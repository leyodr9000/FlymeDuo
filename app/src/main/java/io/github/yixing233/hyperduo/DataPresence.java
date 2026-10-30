package io.github.yixing233.hyperduo;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

/**
 * Watches the mobile-data switch and repaints when it moves.
 *
 * <p><b>Why this class has to exist.</b> The reading used to be sampled from inside the draw
 * path, which meant it was only ever as fresh as the last repaint. That is fine while the
 * status bar is being redrawn anyway, and completely broken in the one place the user is most
 * likely to flip the switch: pull down the shade, turn data off, stay there. Nothing redraws
 * the glyph while the shade sits still, so the old reading stayed on screen until the shade was
 * dismissed - the label looked like it ignored the switch entirely.
 *
 * <p>The fix is to stop waiting for a draw. A {@link ContentObserver} on the data keys turns
 * the switch into an event, and a slow recheck behind it covers the case where the observer
 * never fires - the same two-layer approach {@link WifiPresence} already uses, and for the same
 * reason: delivery on these ROMs is not something to bet on.
 *
 * <p>Only a CHANGE repaints. Polling at a low rate costs a settings read; repainting on every
 * poll would cost a frame per second forever.
 */
final class DataPresence {

    /** Key names tried in order; the first one present answers. See {@link TrioState}. */
    private static final String[] KEYS = {
            "mobile_data", "mobile_data1", "mobile_data2"
    };

    /** Backstop interval. Fast enough to feel immediate, slow enough to be free. */
    private static final long RECHECK_MS = 1_500L;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static boolean started;
    private static Context sContext;
    private static boolean sObserverReady;
    private static Boolean sLast;

    private DataPresence() {}

    /** Idempotent: the first caller installs the observer and starts the backstop. */
    static synchronized void start(final Context context) {
        if (started) {
            return;
        }
        started = true;
        final Context app = context.getApplicationContext();
        sContext = (app != null) ? app : context;

        // Read once immediately: the very first frame should not have to guess.
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                publish(read(sContext), "start");
                MAIN.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        poll();
                        MAIN.postDelayed(this, RECHECK_MS);
                    }
                }, RECHECK_MS);
            }
        });
    }

    /**
     * Installs the settings observer.
     *
     * <p>Deliberately separate from {@link #start}: registration can fail for reasons that have
     * nothing to do with the feature (a stripped-down resolver, a ROM that rejects the uri), and
     * the backstop alone is enough to keep the reading correct. A failure here must therefore
     * not stop the polling.
     */
    private static synchronized void observe(Context context) {
        if (sObserverReady) {
            return;
        }
        sObserverReady = true;
        try {
            final ContentResolver cr = context.getContentResolver();
            final ContentObserver observer = new ContentObserver(MAIN) {
                @Override
                public void onChange(boolean selfChange) {
                    // Posted through MAIN already; still cheap enough to re-read here.
                    poll();
                }
            };
            for (int i = 0; i < KEYS.length; i++) {
                cr.registerContentObserver(
                        Settings.Global.getUriFor(KEYS[i]), false, observer);
            }
        } catch (Throwable ignored) {
            // The backstop carries the feature on its own.
        }
    }

    /** One cheap read; repaints only when the answer actually moved. */
    private static void poll() {
        if (sContext != null) {
            observe(sContext);
            publish(read(sContext), null);
        }
    }

    /**
     * Reads the switch through {@link TrioState}, so the event path and the draw path can
     * never disagree about what "on" means - one implementation, two callers.
     */
    private static boolean read(Context context) {
        return TrioState.readDataEnabled();
    }

    /** Records the value and schedules a repaint when it changed. */
    private static void publish(boolean next, String reason) {
        final Boolean last = sLast;
        sLast = Boolean.valueOf(next);
        final boolean changed = TrioState.setDataEnabled(next);
        if (!changed) {
            return;
        }
        // The reason is only ever "start" or null; keeping the parameter makes the log line
        // self-describing if another caller appears later.
        TrioHooks.log(TrioHooks.LOG_INFO,
                "mobile data enabled=" + next + (reason == null ? "" : " (" + reason + ")"));
        // This is the whole point of the class: the shade is not being redrawn, so ask for one.
        //
        // BOTH registries, and that is not belt-and-braces for its own sake: they are two
        // different lists. TrioHooks.HOSTS is the upstream MIUI path and is empty on Flyme,
        // while FlymeHooks.STATES is the one the adapter actually fills - so asking only the
        // first, as this class did at first, repainted nothing on the ROM it was written for.
        FlymeHooks.invalidateHosts();
        TrioHooks.invalidateHosts();
    }
}
