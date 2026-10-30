package io.github.yixing233.hyperduo;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyDisplayInfo;
import android.telephony.TelephonyManager;

/**
 * Watches whether the radio is on 5G-Advanced.
 *
 * <p><b>Why this cannot be read off the icon.</b> The stock Flyme SystemUI ships
 * {@code bool/config_display_5g_a = false}, so it never asks for {@code ic_5g_a_mobiledata}
 * and the label arrives as plain 5G no matter how good the cell is. Whatever the module draws
 * from that resource can only ever say 5G. The radio state, though, is already in the process:
 * it is what the platform derives the display info from.
 *
 * <p><b>Why a hook alone is not enough.</b> {@link FlymeHooks#hookDisplayInfo} reads the same
 * value, but only fires when something calls {@code getOverrideNetworkType()} - and on a build
 * whose config hides 5G-A, the stock code may never call it. So this class subscribes instead:
 * a {@link TelephonyCallback.DisplayInfoListener} is delivered whether or not anyone on the
 * other side cares about the answer.
 *
 * <p>The reference module for this ROM (FlymeBarSizer) hooks the same getter, which is what
 * suggested the shape; the subscription is what makes it independent of whether the stock
 * label code paths run at all.
 */
final class NrAdvancedPresence {

    /**
     * The ROM value of {@code OVERRIDE_NETWORK_TYPE_NR_ADVANCED}, or -1 when this build has no
     * such constant. Resolved by name: its ordinal is not something to hard-code across ROMs.
     */
    private static int sAdvancedValue = -1;

    private static boolean started;
    private static Context sContext;
    private static TelephonyCallback sCallback;
    private static TelephonyManager sRegisteredOn;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private NrAdvancedPresence() {}

    /** The constant, resolved once; -1 when unavailable. Shared with the hook. */
    static int advancedValue() {
        if (sAdvancedValue == -1) {
            try {
                final java.lang.reflect.Field f = TelephonyDisplayInfo.class
                        .getField("OVERRIDE_NETWORK_TYPE_NR_ADVANCED");
                sAdvancedValue = f.getInt(null);
            } catch (Throwable ignored) {
                sAdvancedValue = -1;
            }
        }
        return sAdvancedValue;
    }

    /** Idempotent: the first caller subscribes, later ones do nothing. */
    static synchronized void start(final Context context) {
        if (started) {
            return;
        }
        started = true;
        final Context app = context.getApplicationContext();
        sContext = (app != null) ? app : context;
        // TelephonyCallback arrived in API 31. Below that the listener class must not even be
        // loaded, so the guard comes before anything that mentions it.
        if (android.os.Build.VERSION.SDK_INT < 31) {
            TrioHooks.log(TrioHooks.LOG_INFO,
                    "nr advanced: API " + android.os.Build.VERSION.SDK_INT + " has no display-info callback");
            return;
        }
        if (advancedValue() == -1) {
            TrioHooks.log(TrioHooks.LOG_INFO,
                    "nr advanced: constant absent, 5GA follows the stock label only");
            return;
        }
        // Off the main thread: the subscription is a binder call and this runs from the
        // battery view's attach, which is already on the main thread.
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                subscribe(sContext);
            }
        });
        // Follow the data card. Without this the subscription stayed on whatever card was
        // the default at boot (or on nothing, if the SIM was not up yet) for the whole life
        // of the process.
        try {
            final android.content.IntentFilter filter = new android.content.IntentFilter(
                    // TelephonyIntents.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED (hidden constant)
                    "android.intent.action.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED");
            filter.addAction("android.intent.action.SIM_STATE_CHANGED");
            final android.content.BroadcastReceiver receiver = new android.content.BroadcastReceiver() {
                @Override
                public void onReceive(Context c, android.content.Intent intent) {
                    // The new card's callback reports its own state on registration.
                    if (TrioState.setNrAdvanced(false)) {
                        FlymeHooks.invalidateHosts();
                    }
                    subscribe(sContext);
                }
            };
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                sContext.registerReceiver(receiver, filter, null, MAIN, Context.RECEIVER_EXPORTED);
            } else {
                sContext.registerReceiver(receiver, filter, null, MAIN);
            }
        } catch (Throwable ignored) {
            // Keeps the boot-time subscription; nothing worse than before.
        }
    }

    /**
     * Subscribes for the default data subscription.
     *
     * <p>That subscription rather than every subscription: the reading on screen is the one
     * the glyph describes, and the glyph describes the data card. Re-subscribing when the
     * default moves is handled by the caller re-running this on a config change.
     */
    private static synchronized void subscribe(Context context) {
        try {
            final TelephonyManager tm = context.getSystemService(TelephonyManager.class);
            if (tm == null) {
                return;
            }
            final int subId = SubscriptionManager.getDefaultDataSubscriptionId();
            final TelephonyManager forSub = (subId >= 0)
                    ? tm.createForSubscriptionId(subId) : tm;
            if (sRegisteredOn != null && sCallback != null) {
                try {
                    sRegisteredOn.unregisterTelephonyCallback(sCallback);
                } catch (Throwable ignored) {
                    // already gone with the process
                }
                sCallback = null;
            }
            final TelephonyCallback cb = new NrCallback();
            forSub.registerTelephonyCallback(context.getMainExecutor(), cb);
            sCallback = cb;
            sRegisteredOn = forSub;
        } catch (Throwable ignored) {
            // No subscription yet (no SIM, or telephony still coming up). The getter hook and
            // the stock label remain, and a later attach tries again.
        }
    }

    /** Records one answer and repaints when it moved. */
    private static void publish(int overrideType) {
        final boolean advanced = overrideType == advancedValue();
        if (TrioState.setNrAdvanced(advanced)) {
            TrioHooks.log(TrioHooks.LOG_INFO, "nr advanced=" + advanced + " (callback)");
            FlymeHooks.invalidateHosts();
        }
    }

    /**
     * A named subclass rather than an anonymous one: {@link TelephonyCallback} is a class and
     * the listener is a separate interface, which an anonymous class cannot add.
     */
    private static final class NrCallback extends TelephonyCallback
            implements TelephonyCallback.DisplayInfoListener {
        @Override
        public void onDisplayInfoChanged(TelephonyDisplayInfo info) {
            try {
                publish(info.getOverrideNetworkType());
            } catch (Throwable ignored) {
                // A display info that will not answer is not a reason to take the drawing down.
            }
        }
    }
}
