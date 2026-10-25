/*
 * Copyright (C) 2026 yixing233 and HyperDuo contributors
 *
 * This file is part of HyperDuo, released under the GNU General Public
 * License v3.0. See the LICENSE file at the repository root, or
 * <https://www.gnu.org/licenses/gpl-3.0.html>.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.yixing233.hyperduo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * Installs every hook HyperDuo v1 needs and keeps the per-host {@link TrioState}
 * bookkeeping.
 *
 * <h3>Why the trio glyph is drawn from the battery icon view</h3>
 * {@code MiuiBatteryMeterIconView} is measured 28dp x 20dp
 * ({@code battery_meter_width} x {@code status_bar_icon_height}) and its native
 * {@code onDraw} clears the canvas itself, so drawing after {@code proceed()}
 * takes the icon over completely without any view-tree surgery.
 *
 * <p>That 28dp is the width of <em>MIUI's</em> battery, which is a narrow vertical
 * shape. The ring and the rectangle fit it well enough - they are roughly square -
 * but the capsule is wide, and in the stock box its fit is decided by the width and
 * it comes out short. So {@link #hookBatteryIconView} also answers that view's
 * {@code onMeasure} with the width the capsule's own aspect ratio needs for the
 * height the bar offered, and nothing else has to be told: the meter and the
 * container both add up their child's measured width, so the slot widens and the
 * bar makes room. The other two arrangements are passed straight through.
 *
 * <h3>Why {@code ignoredSlots} alone leaves a ghost, and what fixes it</h3>
 * {@code ModernStatusBarView.isIconVisible()} is driven by the binding and the anim
 * helper, never by {@code View.getVisibility()}, so hiding the native Wi-Fi / mobile
 * icons has to go through {@code ignoredSlots}: {@code MiuiStatusIconContainer.onMeasure}
 * skips every child whose slot is in that list. But the <em>layout</em> pass still
 * touches them: its first pass unconditionally runs
 * {@code childAt.layout(0, y, measuredWidth, ...)} for every child, and the later
 * right-to-left pass only repositions children that are visible, unblocked and not
 * ignored. So an ignored child keeps the left edge 0 of the pass. The status bar
 * hands its icon container exactly half the screen width (see
 * {@code MiuiNotificationStatusContainer.onMeasure}), which parks the orphaned
 * native mobile view - and the "5G" label inside it - on the screen centre line.
 *
 * <p>The fix therefore has to pair the slot suppression with a hard
 * {@code View.GONE} on the same children, re-applied on every layout pass so it
 * survives MIUI re-showing them. Hiding is restricted to containers that really
 * render the trio glyph (one that owns a live host), because a container whose
 * battery view never draws would otherwise lose its signal icons entirely.
 *
 * <h3>Why {@code addIgnoredSlots} itself is not hooked</h3>
 * The callers pass MIUI's shared static block lists
 * ({@code MiuiIconManagerUtils.RIGHT_BLOCK_LIST}); mutating the argument would
 * corrupt those lists process-wide. Instead the container's own
 * {@code addIgnoredSlots} is invoked with a private list, and only when one of our
 * slots is actually missing - {@code addIgnoredSlots} unconditionally ends in
 * {@code requestLayout()}, so calling it on every pass would loop forever.
 */
final class TrioHooks {

    private static final String TAG = "HyperDuo";
    private static final Map<View, Boolean> KEYGUARD_ROWS = new WeakHashMap<>();
    private static final Map<Object, List<String>> KEYGUARD_ADDED = new WeakHashMap<>();
    static final int LOG_INFO = 4;
    static final int LOG_WARN = 5;
    static final int LOG_ERROR = 6;

    /**
     * Enables the view-tree dumps used to pin down which container holds a stray
     * native icon. Normally driven by the user's debug-log setting; force it on
     * here while matching a container that has not been identified yet.
     */
    static final boolean DEBUG_DUMP = false;

    /** True when the user asked for verbose logging. */
    static boolean debugLog() {
        return DEBUG_DUMP || TrioConfig.debugLog();
    }

    /**
     * {@code MiuiStatusBatteryContainer} 鈥?matched by name while walking the view
     * tree, because resolving it through the host's class loader never worked.
     */
    private static final String BATTERY_CONTAINER_CLASS =
            "com.android.systemui.statusbar.views.MiuiStatusBatteryContainer";

    /** Module handle for logging from helpers that have no callback argument. */
    private static volatile XposedModule sModule;

    /**
     * Every slot the module knows how to fold, one group per sub-toggle.
     *
     * <p>"mobile" and "wifi" are the classic {@code ModernStatusBarMobileView} /
     * {@code ModernStatusBarWifiView} slots. HyperOS 4 replaced the visible mobile
     * indicator with a Compose one whose slot is "stacked_mobile"
     * ({@code SingleBindableStatusBarComposeIconView}); on a real device it is the
     * only mobile child left visible and the classic ones stay {@code a=0.0 v=8}.
     * Both mobile slots are managed together so whichever the build uses follows
     * the same sub-toggle.
     */
    private static final List<String> MANAGED_SLOTS =
            Collections.unmodifiableList(
                    Arrays.asList("wifi", "mobile", "stacked_mobile"));

    /** The "mobile" slot group, folded and restored as one. */
    private static final List<String> MOBILE_SLOTS =
            Collections.unmodifiableList(Arrays.asList("mobile", "stacked_mobile"));

    /** The Wi-Fi slot group; a single element list, kept for symmetry. */
    private static final List<String> WIFI_SLOTS =
            Collections.unmodifiableList(Arrays.asList("wifi"));

    /**
     * The battery style that makes MIUI measure, show and lay out its own
     * charging bolt. Read by {@code MiuiBatteryMeterView.onMeasure},
     * {@code updateChargeAndText} and {@code onLayout} - never by
     * {@code onBatteryStyleChanged}, which is the only method whose style-1 path
     * also hides {@code mBatteryIconView}, the view the glyph is drawn on.
     */
    private static final int BOLT_STYLE = 1;

    /**
     * The slots to fold for the settings in force right now.
     *
     * <p>This is what makes a sub-toggle mean something: while the module is on,
     * a slot is folded exactly when the glyph draws that element, so switching
     * one off hands its native icon back instead of leaving it stranded at the
     * container's left edge. The whole list is empty while the module is off,
     * which is what {@link #restoreNative} relies on to undo everything.
     *
     * <p>The bolt is not here: it is not a status-bar icon slot but a child of
     * the battery view, handed back through {@link #BOLT_STYLE}.
     */
    private static List<String> foldedSlots() {
        final TrioAppearance a = TrioConfig.appearance();
        if (!a.glyph) {
            return Collections.emptyList();
        }
        final List<String> slots = new ArrayList<>(MANAGED_SLOTS.size());
        if (a.ownsWifi()) {
            // The island is the one state that hands Wi-Fi back while the module
            // draws it: the battery is hidden, so the glyph cannot carry the
            // arcs and the user asked for the native icon to take over.
            if (!islandHideBattery()) {
                slots.addAll(WIFI_SLOTS);
            }
        }
        // The mobile slot is folded exactly while the module draws the signal
        // itself: as the glyph's dots, or as the out-of-ring reading. Leaving it
        // in place out of ring with the stacked switch off is what keeps MIUI's
        // own icon on screen, which is the whole difference the switch makes.
        // The island does not change this: what it changes is *where* the
        // module draws the signal (out of ring, since the glyph is gone), not
        // whether the user asked for it - so the fold condition is untouched and
        // the reading takes over in syncOutSignal.
        if (a.foldsMobile()) {
            slots.addAll(MOBILE_SLOTS);
        }
        return slots;
    }

    /**
     * Whether the module should be drawing the out-of-ring signal reading.
     *
     * <p>Two ways in, and they are different questions:
     *
     * <ol>
     *   <li>the user put the signal out of ring AND asked for the stacked rendering. That
     *       is the explicit choice, and {@link TrioAppearance#stackedOut()} is the
     *       predicate for it;
     *   <li>the charging island took the glyph away while cards are still placed in the
     *       ring. There is nowhere left to draw them, so the reading moves beside the
     *       battery instead of vanishing - that is the fallback, and it must not require
     *       the switch, because the user's intent was "in the ring" and the island is what
     *       changed.
     * </ol>
     *
     * <p>A user in neither case has handed the reading back to MIUI, so the native icon
     * stays and this draws nothing.
     */
    private static boolean outSignalWanted() {
        final TrioAppearance a = TrioConfig.appearance();
        return a.stackedOut() || (islandHideBattery() && a.foldsMobile());
    }

    /**
     * Whether the charging super island currently has the battery hidden.
     *
     * <p>Sourced from {@link TrioState#sIslandHideBattery}, which the hook on
     * {@code MiuiStatusBatteryContainer.setIsHideBattery} keeps current; this
     * reads the flag rather than walking for the container, because the ask
     * happens in contexts ({@code foldedSlots} during a layout, the draw gate)
     * where no container is at hand - and the flag is global by nature: MIUI
     * hides the battery of every container at once.
     */
    static boolean islandHideBattery() {
        return TrioState.sIslandHideBattery;
    }

    /** Hosts currently drawing the trio glyph. */
    private static final List<TrioState> HOSTS =
            Collections.synchronizedList(new ArrayList<TrioState>());

    /** The status bar's icon container, kept for {@link #resources()}. */
    private static volatile Object sStatusIconContainer;
    /**
     * The status bar view itself ({@code MiuiPhoneStatusBarView}), the row the
     * glyph shares with the clock, the notification icons and the system icons.
     * Kept so {@link #invalidateHosts()} can ask that one row to lay out again
     * instead of the keyguard's and the control centre's copies of it.
     */
    private static volatile View sStatusBarView;

    /** {@code MiuiStatusBatteryContainer}, resolved once. */
    private static volatile Class<?> sBatteryContainerClass;
    /** {@code MiuiStatusBatteryContainer.mStatusIcon}, resolved once. */
    private static volatile Field sStatusIconField;

    /** {@code MiuiBatteryMeterView.mStoreRealStyle}, resolved once. */
    private static volatile Field sStoreRealStyleField;
    /** {@code MiuiBatteryMeterView.updateChargeAndText}, resolved once. */
    private static volatile Method sUpdateChargeAndTextField;
    /** {@code MiuiBatteryMeterView.onBatteryStyleChanged(int)}, resolved once. */
    private static volatile Method sStyleChangedField;
    /** {@code MiuiBatteryMeterView.mBatteryChargingView}, resolved once. */
    private static volatile Field sChargingViewField;
    /** {@code MiuiBatteryMeterView.mBatteryPercentContainer}, resolved once. */
    private static volatile Field sPercentContainerField;
    /**
     * {@code MiuiBatteryMeterView.mBatteryStyle}, resolved once.
     *
     * <p>Written directly, never through {@code onBatteryStyleChanged}: that is
     * the only path that hides {@code mBatteryIconView}, and it only does so when
     * the style really changes, so passing the same value leaves the glyph host
     * alone. See {@link #BOLT_STYLE}.
     */
    private static volatile Field sBatteryStyleField;
    /** {@code MiuiBatteryMeterView.mBatteryIconView}, resolved once. */
    private static volatile Field sBatteryIconViewField;
    /** {@code MiuiBatteryMeterView.mHollowBatteryIconView}, resolved once. */
    private static volatile Field sHollowBatteryIconViewField;

    /**
     * Live {@code MiuiBatteryMeterView} instances, so a settings change can ask
     * them to re-apply the style MIUI last requested.
     */
    private static final List<WeakReference<View>> METERS = new ArrayList<>();

    /** {@code MiuiStatusIconContainer}, resolved once. */
    private static volatile Class<?> sIconContainerClass;
    /** {@code MiuiStatusIconContainer.ignoredSlots}, resolved once. */
    private static volatile Field sIgnoredSlotsField;

    /**
     * Icon containers that belong to a view actually drawing the trio glyph.
     * Native Wi-Fi / mobile children are only forced GONE in these, so a
     * container whose battery view stays hidden keeps its own signal icons.
     */
    private static final Map<Object, Boolean> OWNED =
            Collections.synchronizedMap(new WeakHashMap<Object, Boolean>());

    /** Containers already dumped to the log, to keep one diagnostic each. */
    private static final Map<Object, Boolean> DIAGNOSED =
            Collections.synchronizedMap(new WeakHashMap<Object, Boolean>());

    /**
     * Last {@code owned} value logged per container.
     *
     * <p>The first layout of a container normally runs before any glyph host has
     * drawn, so the first header legitimately reports {@code owned=false}; a
     * one-shot header would then keep reporting a stale value for the rest of
     * the session. Re-logging on a change keeps the log honest for two extra
     * lines at most per container.
     */
    private static final Map<Object, Boolean> DIAG_OWNED =
            Collections.synchronizedMap(new WeakHashMap<Object, Boolean>());

    /**
     * Folded children already given a hard {@code GONE}. Kept so a firmware pass
     * that re-shows one of them is not answered with a new visibility flip on
     * every layout - the zero-size {@code layout()} in {@link #settle} already
     * keeps the child undrawn.
     */
    private static final Map<View, Boolean> COLLAPSED =
            Collections.synchronizedMap(new WeakHashMap<View, Boolean>());

    /** Last child dump per container, so a steady layout logs at most once. */
    private static final Map<Object, String> DIAG_SIG =
            Collections.synchronizedMap(new WeakHashMap<Object, String>());

    /**
     * Original {@code left}/{@code right} padding of an icon container, recorded
     * the first time the out-of-ring label reserves room in it.
     *
     * <p>Keyed on the <em>icon</em> container the padding is applied to; each value
     * is a bare {@code int[]}, so - unlike a {@code WeakHashMap<View, TextView>} -
     * it cannot reach back and keep its own key alive.
     */
    private static final Map<View, int[]> OUT_PAD_SAVED =
            Collections.synchronizedMap(new WeakHashMap<View, int[]>());

    /** Hard cap on total child dumps, so a layout loop cannot flood the log. */
    private static volatile int sDiagDumps;
    /** Hard cap on container headers, including {@code owned} flips. */
    private static volatile int sDiagHeaders;

    /**
     * The status bar's own clock, once the bar has been inflated.
     *
     * <p>Held as a strong reference on purpose, unlike the host and container
     * maps: there is one status bar and one clock for the life of the process, so
     * this cannot pin a discarded view - and the visibility has to be re-asserted
     * on a layout path that must not go looking for the clock every frame.
     */
    private static volatile View sStatusClock;
    /** {@code MiuiClock.setMultiTaskVisibility(int)}, resolved once. */
    private static volatile Method sClockSetter;
    /** Whether the clock currently carries the module's hiding. */
    private static volatile boolean sClockHidden;
    /** One diagnostic line for the capsule's widened host, so a loop cannot flood. */
    private static volatile boolean sLoggedCapsuleMeasure;
    /**
     * {@code View.setMeasuredDimension}, resolved once.
     *
     * <p>Via {@code View.class} rather than the icon class: the method is declared
     * on View and merely inherited, and {@code Refl.method} uses
     * {@code getDeclaredMethod}, which does not walk up the hierarchy - asking the
     * icon class for it would silently return null and the widening would quietly
     * never happen.
     */
    private static volatile Method sSetMeasuredDimension;

    private TrioHooks() {
    }

    // ------------------------------------------------------------------ install

    static void install(XposedModule module, ClassLoader cl) {
        sModule = module;
        // Read the user's settings before anything is hooked: the very first hook
        // callback already needs to know whether the module is switched on.
        TrioConfig.install(module);
        final boolean flyme = FlymeHooks.isFlyme(cl);
        TrioConfig.addListener(new TrioConfig.Listener() {
            @Override
            public void onConfigChanged() {
                if (flyme) {
                    FlymeHooks.onConfigChanged();
                } else {
                    applyConfigChange();
                }
            }
        });
        if (flyme) {
            // Flyme's SystemUI shares almost nothing with MIUI's: the whole Miui*
            // chain is absent, and the battery, Wi-Fi and icon container live in
            // its own classes. Installing the MIUI hooks there yields three
            // generic hits that draw into containers nobody owns, so the adapter
            // replaces them outright.
            int hooked = FlymeHooks.install(module, cl);
            log(module, "HyperDuo installed, flyme adapter build "
                    + FlymeHooks.ADAPTER_BUILD + ", hooks=" + hooked
                    + " enabled=" + TrioConfig.get().enabled);
            return;
        }
        int hooked = 0;
        hooked += group(module, cl, 1);
        hooked += group(module, cl, 2);
        hooked += group(module, cl, 3);
        hooked += group(module, cl, 4);
        hooked += group(module, cl, 5);
        hooked += group(module, cl, 6);
        hooked += group(module, cl, 7);
        hooked += group(module, cl, 8);
        hooked += group(module, cl, 9);
        hooked += group(module, cl, 10);
        hooked += group(module, cl, 11);
        hooked += group(module, cl, 12);
        hooked += group(module, cl, 13);
        hooked += group(module, cl, 14);
        log(module, "HyperDuo installed, hooks=" + hooked
                + " enabled=" + TrioConfig.get().enabled);
    }

    /**
     * Reacts to the settings app changing a value.
     *
     * <p>Runs on whatever thread delivered the change - the framework's Binder
     * thread, or the main looper for the settings app's reload broadcast - so
     * everything that touches the view tree is posted to the main looper.
     *
     * <p>A plain repaint is only enough when nothing about the suppression moved.
     * The transitions that need real work are:
     *
     * <p>Turning the module <em>off</em> has to undo the slot suppression and the
     * forced {@code GONE}, hand each meter back the battery style MIUI really
     * wants, and re-run {@code updateChargeAndText} - the native charging /
     * percentage views were hidden by our hook and MIUI only re-shows them from
     * that method, which it does not call on a settings change.
     *
     * <p>Turning it <em>on</em> has to redo the suppression (turning it off
     * dropped it), re-pin the battery style, re-hide the native charging and
     * percentage views (turning it off handed them back to MIUI), and collapse
     * the folded children again - see {@link #refoldContainers}, which applies
     * that state directly rather than waiting for the layout pass that
     * {@code invalidate()} would not trigger on its own.
     *
     * <p>The same work is needed for the sub-toggles, which is why this does not
     * branch on the master switch alone: {@code show_wifi} and {@code show_mobile}
     * decide which slots are suppressed, and {@code show_bolt} / {@code show_value}
     * decide whether the native charging bolt is ours to suppress. Turning a
     * sub-toggle off has to hand its native element back - see
     * {@link #foldedSlots} and {@link #applyMeterText} - and turning it on has to
     * take it away again.
     */
    private static void applyConfigChange() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            // The framework delivers remote-preference changes on a Binder
            // thread, and the work below calls requestLayout on live views.
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    applyConfigChange();
                }
            });
            return;
        }
        final TrioAppearance a = TrioAppearance.of(TrioConfig.get());
        final boolean enabled = a.glyph;
        final List<TrioState> hosts;
        synchronized (HOSTS) {
            hosts = new ArrayList<TrioState>(HOSTS);
        }
        // Snapshot the meters before anything can drop them: the switch-off path
        // both re-applies their style and re-runs their text refresh, and the
        // weak list must not be emptied out from under the second step.
        final List<View> meters = liveMeters();
        // The font is read here rather than left to the first draw that notices the
        // path changed. This runs on the settings-change path, off the render
        // thread's draw, and it is the one moment the file is known to be new. The
        // draw-time call in the renderer stays as a safety net for an appearance
        // that arrives by a route this callback does not cover; it finds the work
        // already done and returns.
        TrioRenderer.setFont(a.fontPath);
        if (TrioRenderer.fontFailed()) {
            log(LOG_WARN, "font could not be loaded: " + a.fontPath);
        }
        if (debugLog()) {
            log(LOG_INFO, "config changed, hosts=" + hosts.size()
                    + " enabled=" + enabled
                    + " ring=" + a.ringStroke + " arc=" + a.arcStroke
                    + " size=" + a.valueSize);
        }
        for (int i = 0; i < hosts.size(); i++) {
            final View v = hosts.get(i).host;
            if (v == null) {
                continue;
            }
            v.post(new Runnable() {
                @Override
                public void run() {
                    if (enabled) {
                        foldHostContainer(v);
                        // Insurance on top of refoldContainers(), which applies
                        // the folded state directly: a layout pass is what MIUI
                        // itself uses to re-lay the icon container, and letting it
                        // happen keeps our result consistent with anything MIUI
                        // does in the same pass.
                        v.requestLayout();
                    }
                    // Either mounts or removes it, so this covers switching the
                    // network type to Out of ring, away from it, and off. Posted
                    // rather than called inline because it may add a view, which
                    // must never happen inside a layout pass - and this runnable
                    // is already the deferred slot the rest of the re-fold uses.
                    syncOutSignal(v);
                    // Between the two on purpose: the label chains through the
                    // performance readout, which chains through the reading, so
                    // each is measured before the one that stands outside it.
                    syncPerfLabel(v);
                    syncOutTypeLabel(v);
                    v.invalidate();
                }
            });
        }
        // The styles are re-applied either way, but for different reasons, and it
        // has to happen before the meter text below: re-applying the style runs
        // onBatteryStyleChanged, which is what puts the battery views back the way
        // MIUI wants them - and the style hook re-forces the native bolt onto the
        // meter afterwards.
        restyleMeters(meters);
        // The slot suppression follows the sub-toggles, not just the master switch,
        // so this runs on every change while the module is on: turning show_wifi or
        // show_mobile off has to drop that slot from ignoredSlots and let MIUI lay
        // the native icon out again.
        if (enabled) {
            refoldContainers();
        } else {
            restoreNative();
        }
        // Always, whichever toggles moved: the native charging bolt has to follow
        // show_bolt / show_value, and the percentage container follows the master
        // switch.
        applyMeterText(meters);
        syncKeyguardRows();
        // A meter that was mid-island when the module switched off must still
        // receive MIUI's original replacement request - the replay re-drives
        // every declined island with its real values. Under the new enable the
        // same replay re-declines them, which is the state the hooks want.
        replayBatteryIslandRequests();
        // Last, because it is the only step that can add ink the user has not
        // seen yet: switching to the capsule mid-minute would otherwise leave its
        // left slot empty until the next minute boundary, which reads as the
        // style being broken rather than as the clock not having ticked.
        ClockTicker.refreshNow();
        // The bar's own clock follows the same switch the capsule's does, and both
        // directions matter: leaving the capsule has to give the time back.
        applyClockVisibility();
    }

    /**
     * Re-applies the slot suppression to every container we had claimed, and
     * both collapses the slots the sub-toggles still want folded and hands the
     * ones they no longer want back to MIUI.
     *
     * <p>{@link #restoreNative} drops both the suppression and the forced
     * {@code GONE}, but keeps the ownership record, so this is what makes a later
     * re-enable take effect without waiting for each container to be inflated
     * again.
     *
     * <p>Goes through {@link #syncSlots}, not the layout pass's
     * {@link #ensureFolded}: this runs once per settings change, never per layout,
     * so the suppression must be applied even when the slot list cannot be read
     * back, and {@code syncSlots} is idempotent whenever it can be.
     *
     * <p>{@link #settle} runs here as well, immediately after the suppression.
     * Normally {@code settle} is driven by the icon container's {@code onLayout},
     * and the re-enable path does request a layout so that it gets there - but
     * that makes correctness depend on the layout pass actually happening and on
     * MIUI not skipping it. Calling it directly applies the same state now; the
     * later layout pass is then a no-op, because
     * {@link #markCollapsed} has already recorded the children.
     */
    private static void refoldContainers() {
        final List<Object> containers;
        synchronized (OWNED) {
            containers = new ArrayList<Object>(OWNED.keySet());
        }
        for (int i = 0; i < containers.size(); i++) {
            foldAndSettle(containers.get(i));
        }
        foldAndSettle(sStatusIconContainer);
    }

    private static void foldAndSettle(Object container) {
        syncSlots(container);
        if (container instanceof ViewGroup) {
            settle((ViewGroup) container);
        }
    }

    /** Live meter views, dropping the ones the GC already took. */
    private static List<View> liveMeters() {
        final List<View> meters = new ArrayList<>();
        synchronized (METERS) {
            for (int i = METERS.size() - 1; i >= 0; i--) {
                final View v = METERS.get(i).get();
                if (v == null) {
                    METERS.remove(i);
                } else {
                    meters.add(v);
                }
            }
        }
        return meters;
    }

    /**
     * Brings MIUI's own charging bolt and percentage views in line with the
     * sub-toggles.
     *
     * <p>The charging bolt is the one native element this module draws itself, so
     * it is the one element with a rule: the stock bolt stays out of the way only
     * while the glyph actually draws its own, which
     * {@link TrioAppearance#hidesNativeBolt} states once for every caller. Turn
     * either toggle off and the stock bolt belongs to MIUI again.
     *
     * <p>Handing it back cannot just mean un-hiding it. {@code updateChargeAndText}
     * is MIUI's authority on whether the bolt belongs on screen - it hides it when
     * the battery is not charging - and it re-runs on every battery state change.
     * So the hand-back is: make the view visible, then let MIUI decide. That way a
     * battery change cannot leave the bolt in a state this module invented.
     *
     * <p>The percentage container has no such rule: MIUI only measures and shows
     * it at battery style 3, and the module never asks for style 3, so while the
     * module is on it can only ever be hidden. It is shown again whenever the
     * native text is wanted, so switching the module off leaves MIUI in charge.
     */
    private static void applyMeterText(List<View> meters) {
        for (int i = 0; i < meters.size(); i++) {
            final View meter = meters.get(i);
            meter.post(new Runnable() {
                @Override
                public void run() {
                    final TrioAppearance a = TrioConfig.appearance();
                    final Object charging = Refl.get(sChargingViewField, meter);
                    // The PR's repair, kept under main's predicate. Both sides ask
                    // hidesNativeBolt(); the difference is only how the hand-back is
                    // performed - a bare showIfHidden leaves the bolt dark whenever MIUI
                    // has not happened to re-run its own style pass, because
                    // updateChargeAndText only reveals the charging view while
                    // mBatteryStyle is 1 or 2 and the module forces it to 0.
                    if (!a.hidesNativeBolt()) {
                        Refl.set(sBatteryStyleField, meter, Integer.valueOf(BOLT_STYLE));
                        showIfHidden(charging);
                        // Asking for the bolt is what performs MIUI's view swap: the
                        // style-1 branch hides mBatteryIconView - the view the trio is
                        // painted on - and shows the hollow outline. Put the pair back.
                        showIfHidden(Refl.get(sBatteryIconViewField, meter));
                        hide(Refl.get(sHollowBatteryIconViewField, meter));
                    } else {
                        hide(charging);
                    }
                    // The percentage text is ours to hide the whole time the
                    // module is on, whichever sub-toggles are set.
                    final Object percent = Refl.get(sPercentContainerField, meter);
                    if (a.glyph && !a.handsBackPercentage()) {
                        hide(percent);
                    } else {
                        showIfHidden(percent);
                    }
                    // Let MIUI re-derive the bolt from the battery state. It only
                    // runs the text refresh on its own battery callbacks, and this
                    // is a settings change, so without the call the hand-back
                    // would wait for the next charge event.
                    Refl.invoke(sUpdateChargeAndTextField, meter);
                }
            });
        }
    }

    private static void showIfHidden(Object view) {
        if (view instanceof View) {
            final View v = (View) view;
            if (v.getVisibility() != View.VISIBLE) {
                v.setVisibility(View.VISIBLE);
            }
        }
    }

    /** Asks every known {@code MiuiBatteryMeterView} to re-apply its real style. */
    private static void restyleMeters(List<View> meters) {
        for (int i = 0; i < meters.size(); i++) {
            final View meter = meters.get(i);
            meter.post(new Runnable() {
                @Override
                public void run() {
                    reapplyStyle(meter);
                }
            });
        }
    }

    private static void reapplyStyle(View meter) {
        final Object style = Refl.get(sStoreRealStyleField, meter);
        if (style instanceof Number) {
            Refl.invokeArgs(sStyleChangedField, meter,
                    new Object[]{Integer.valueOf(((Number) style).intValue())});
        }
    }

    /**
     * Puts the native icons back: drops our slots from {@code ignoredSlots} and
     * clears the forced {@code GONE}, so switching the module off leaves the
     * status bar exactly as MIUI would have drawn it.
     *
     * <p>Only the children recorded in {@code COLLAPSED} are made visible again.
     * Those are the ones this module hid; a child MIUI itself decided to hide -
     * no SIM, wifi off, airplane mode - was never recorded and is deliberately
     * left alone, because resurrecting it would show an icon the system state
     * does not call for.
     */
    private static void restoreNative() {
        List<Object> containers;
        synchronized (OWNED) {
            containers = new ArrayList<Object>(OWNED.keySet());
        }
        for (int i = 0; i < containers.size(); i++) {
            final Object container = containers.get(i);
            unfoldSlots(container);
            if (!(container instanceof ViewGroup)) {
                continue;
            }
            final ViewGroup group = (ViewGroup) container;
            final int count = group.getChildCount();
            for (int c = 0; c < count; c++) {
                final View child;
                try {
                    child = group.getChildAt(c);
                } catch (Throwable t) {
                    continue;
                }
                if (child == null) {
                    continue;
                }
                final String slot = slotOf(child);
                if (slot == null || !MANAGED_SLOTS.contains(slot)) {
                    continue;
                }
                if (!unmarkCollapsed(child)) {
                    continue;
                }
                try {
                    if (child.getVisibility() != View.VISIBLE) {
                        child.setVisibility(View.VISIBLE);
                    }
                } catch (Throwable ignored) {
                    // never let one child abort the pass
                }
            }
        }
        // The out-of-ring label and the performance readout are ours alone, so
        // taking them back out needs no state check - unlike the native slots
        // above, MIUI has no opinion about whether they should exist.
        for (int i = 0; i < containers.size(); i++) {
            final Object container = containers.get(i);
            if (container instanceof View) {
                final View battery = batteryContainerOf((View) container);
                removePerfLabel(battery);
                removeOutTypeLabel(battery);
            }
        }
        for (int i = 0; i < containers.size(); i++) {
            final Object container = containers.get(i);
            if (container instanceof View) {
                // While folded, the children were laid out at 0x0 and hidden, so
                // the container may still hold the measurement taken then.
                // Making them visible again is not enough on its own: MIUI needs
                // one more pass before they reappear where they belong.
                ((View) container).requestLayout();
            }
        }
        for (int i = 0; i < containers.size(); i++) {
            final Object container = containers.get(i);
            if (container instanceof ViewGroup) {
                // The native type views were suppressed independently of the
                // slots, so they are handed back independently too. Runs before
                // COLLAPSED.clear() because the hand-back reads the same
                // once-only records the suppression wrote.
                suppressNativeTypeViews((ViewGroup) container);
            }
        }
        synchronized (COLLAPSED) {
            COLLAPSED.clear();
        }
        // METERS is deliberately kept: it holds only weak references (pruned by
        // liveMeters), and dropping it here would leave a later re-enable with no
        // meter to re-pin the battery style on.
    }

    /**
     * Runs one hook group, isolating any failure. Hook groups are independent:
     * a method that a given firmware build renamed must not take the others
     * down with it.
     */
    private static int group(XposedModule module, ClassLoader cl, int which) {
        try {
            switch (which) {
                case 1: return hookStatusBarViewCapture(module, cl);
                case 2: return hookBatteryIconView(module, cl);
                case 3: return hookBatteryMeterView(module, cl);
                case 4: return hookStatusBarView(module, cl);
                case 5: return hookSignalIcons(module, cl);
                case 6: return hookIconContainerLayout(module, cl);
                case 7: return hookMobileType(module, cl);
                case 8: return hookMobileTypeVisibility(module, cl);
                case 9: return hookMeterTint(module, cl);
                case 10: return hookIslandHide(module, cl);
                case 11: return hookIconTint(module, cl);
                case 12: return hookKeyguardBar(module, cl);
                case 13: return hookIconAdded(module, cl);
                case 14: return hookStatusClock(module, cl);
                default: return 0;
            }
        } catch (Throwable t) {
            log(module, "hook group " + which + " failed: " + t);
            return 0;
        }
    }

    /**
     * Hooks one executable, tolerating a missing method (null) or a rejected
     * hook. Returns 1 when the hook was installed, 0 otherwise.
     */
    private static int hook(XposedModule module, Method m, String id,
                            XposedInterface.Hooker hooker) {
        if (m == null) {
            log(module, "skip " + id + ": method not found");
            return 0;
        }
        try {
            module.hook(m).setId(id).intercept(hooker);
            return 1;
        } catch (Throwable t) {
            log(module, "skip " + id + ": " + t);
            return 0;
        }
    }

    /**
     * Captures the status bar's icon container.
     *
     * <p>This deliberately hooks {@code MiuiPhoneStatusBarView}, not
     * {@code MiuiStatusBatteryContainer}: {@code system_icons.xml} is included by
     * seven layouts (status bar, keyguard, control center, both QS headers, ...),
     * so a container-level hook fires for every one of those instances and the
     * last to inflate would win, leaving us suppressing slots on the keyguard or
     * control-center container instead of the status bar's. Only
     * {@code status_bar.xml}'s root is a {@code MiuiPhoneStatusBarView}, and it
     * holds the authoritative {@code mStatusBarStatusIcons}.
     */
    private static int hookStatusBarViewCapture(XposedModule module, ClassLoader cl) {
        final Class<?> bar = Refl.cls(
                "com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView", cl);
        if (bar == null) {
            log(module, "MiuiPhoneStatusBarView missing (capture)");
            return 0;
        }
        final Field iconsField = Refl.field(bar, "mStatusBarStatusIcons");
        return hook(module, Refl.method(bar, "onFinishInflate"),
                "hyperduo-container", new XposedInterface.Hooker() {
                    @Override
                    public Object intercept(XposedInterface.Chain chain) throws Throwable {
                        final Object result = chain.proceed();
                        // mStatusBarStatusIcons is assigned inside proceed(), so it
                        // is only readable now.
                        final Object self = chain.getThisObject();
                        final Object icons = Refl.get(iconsField, self);
                        if (icons instanceof View) {
                            sStatusIconContainer = icons;
                            syncSlots(icons);
                        }
                        if (self instanceof View) {
                            sStatusBarView = (View) self;
                            // The only hook callback that runs early enough on a
                            // real SystemUI context. TrioConfig ignores repeat
                            // calls, so this stays a one-shot.
                            TrioConfig.installReceiver(((View) self).getContext());
                        }
                        return result;
                    }
                });
    }

    private static int hookBatteryIconView(XposedModule module, ClassLoader cl) {
        final Class<?> icon = Refl.cls(
                "com.android.systemui.statusbar.views.MiuiBatteryMeterIconView", cl);
        if (icon == null) {
            log(module, "MiuiBatteryMeterIconView missing");
            return 0;
        }
        // Resolved through the class rather than through the instance, because
        // View.setMeasuredDimension is protected and only a subclass may call it
        // directly - this hook is an outside writer, so it goes through reflection.
        sSetMeasuredDimension = Refl.method(View.class, "setMeasuredDimension",
                int.class, int.class);
        final int a = hook(module, Refl.method(icon, "onDraw", Canvas.class),
                "hyperduo-draw", new XposedInterface.Hooker() {
                    @Override
                    public Object intercept(XposedInterface.Chain chain) throws Throwable {
                        final Object self = chain.getThisObject();
                        final Object canvasArg = chain.getArg(0);
                        // Return the slot before native onDraw can use it. Doing
                        // this afterwards leaves a stale glyph in the first off
                        // or island frame, and clearTint alone loses native tint.
                        final boolean paint = TrioConfig.get().enabled && !islandHideBattery();
                        if (self instanceof View && !paint) {
                            GlyphDrawable.uninstall((View) self);
                        }
                        final Object result = chain.proceed();
                        if (self instanceof View) {
                            // Idempotent, and the only chance to pick up a real
                            // SystemUI context when this generation was loaded
                            // after the view tree had already been inflated.
                            TrioConfig.installReceiver(((View) self).getContext());
                            // The per-SIM signal sampler needs the same context to
                            // reach telephony; it is the drawing path that needs it,
                            // so it is handed over here rather than at hook install
                            // time, when there may be no context at all.
                            TrioState.attachContext(((View) self).getContext());
                        }
                        // Just the master switch: whether to paint at all. The
                        // renderer resolves the rest of the switches itself -
                        // except the island: with the battery hidden there is no
                        // ring to draw into, and painting on would leave the
                        // glyph floating over the island's clearance.
                        if (self instanceof View && canvasArg instanceof Canvas
                                && paint) {
                            final View host = (View) self;
                            TrioState state = stateFor(host);
                            if (state == null) {
                                state = registerHost(host);
                            }
                            state.refresh();
                            // MIUI's dark-mode pass ends at this view's own
                            // invalidate() and nowhere else, so a tint change is
                            // visible here first - and the out-of-ring views are
                            // siblings that nothing repaints on their own.
                            final int ink = state.foreground();
                            if (state.outRingInk != ink) {
                                recolourOutRing(state, ink);
                            }
                            final Canvas canvas = (Canvas) canvasArg;
                            // The glyph is painted by the icon view itself, from
                            // the drawable this puts into MIUI's own tint slot -
                            // see GlyphDrawable. Nothing is left of the window
                            // that used to be opened here: the canvas, its
                            // bounds and its clip are the platform's again, so
                            // there is nothing to keep in step with the bar and
                            // nothing for the platform to read as an overlay.
                            if (GlyphDrawable.install(host, state)) {
                                // The frame that took the slot still has MIUI's
                                // own battery on it - the drawable only starts
                                // being asked from the next one. Paint the
                                // glyph here as well, so the two never trade
                                // places on screen.
                                TrioRenderer.draw(canvas, host, state);
                            }
                        }
                        return result;
                    }
                });
        final int b = hook(module, Refl.method(icon, "onDetachedFromWindow"),
                "hyperduo-detach", new XposedInterface.Hooker() {
                    @Override
                    public Object intercept(XposedInterface.Chain chain) throws Throwable {
                        final Object result = chain.proceed();
                        final Object self = chain.getThisObject();
                        if (self instanceof View) {
                            unregisterHost((View) self);
                        }
                        return result;
                    }
                });
        // The host is MIUI's own battery icon, and MIUI measures it to
        // `battery_meter_width` - 28dp, the width of the narrow vertical battery
        // MIUI draws. The capsule is a wide shape, so inside that box the fit is
        // decided by the width and the glyph comes out short: measured on the
        // reference device at 84x46px inside a 60px-tall bar, with fourteen pixels
        // of the bar's height going unused and the shape reading as a squashed
        // pill.
        //
        // Answering with the width the capsule's own aspect ratio needs for the
        // height MIUI just chose is the whole fix, and nothing has to be told
        // about it: MiuiBatteryMeterView.onMeasure does not measure its icon child
        // at all, it adds up mBatteryDigitalView.getMeasuredWidth(), and
        // MiuiStatusBatteryContainer.onMeasure adds up the meter's - so widening
        // this one view widens the battery slot and the bar makes room for it on
        // its own.
        //
        // Only for the capsule. The ring and the rectangle are as tall as they are
        // wide and are content in the stock box, so they are left exactly as MIUI
        // measured them - a pass-through, not a re-measure, so nothing about the
        // other two arrangements can shift.
        final int c = hook(module, Refl.method(icon, "onMeasure", int.class, int.class),
                "hyperduo-icon-measure", new XposedInterface.Hooker() {
                    @Override
                    public Object intercept(XposedInterface.Chain chain) throws Throwable {
                        final Object result = chain.proceed();
                        final Object self = chain.getThisObject();
                        if (!(self instanceof View)) {
                            return result;
                        }
                        final View view = (View) self;
                        try {
                            final TrioAppearance a = TrioConfig.appearance();
                            if (!a.glyph || !a.capsule) {
                                return result;
                            }
                            // Taken from the height MIUI just settled on rather
                            // than from the measure spec: that is the height the
                            // glyph is about to be drawn into, so the request and
                            // the drawing cannot be computed from different
                            // numbers, and no spec mode has to be interpreted.
                            final int height = view.getMeasuredHeight();
                            // The length has to be in force BEFORE the width is asked for: the
                            // host is sized from the shape, so a measurement taken against
                            // last frame's length would reserve the old width and then draw
                            // a longer capsule inside it, clipped at the ends.
                            TrioGeometry.setCapsuleLength(a.capsuleLength);
                            final int width = TrioGeometry.capHostWidthPx(height);
                            // The two opening bands are added to the HEIGHT, after the width
                            // has been asked for the bare capsule. Width decides the drawing
                            // scale, so leaving it alone is what keeps the capsule the same
                            // size; the band is purely extra room above and below it.
                            final int tall = TrioGeometry.capHostHeightPx(height);
                            final Method setter = sSetMeasuredDimension;
                            if (width > 0 && tall > 0
                                    && (width != view.getMeasuredWidth()
                                        || tall != view.getMeasuredHeight())
                                    && setter != null) {
                                // Reflectively, because View.setMeasuredDimension is
                                // protected: only a subclass may call it on itself,
                                // and this is an outside writer. It is part of the
                                // SDK stubs, so it is not a hidden API and no
                                // restriction applies.
                                Refl.invokeArgs(setter, view, new Object[] {
                                        Integer.valueOf(width), Integer.valueOf(tall) });
                                if (debugLog() && !sLoggedCapsuleMeasure) {
                                    sLoggedCapsuleMeasure = true;
                                    log(LOG_INFO, "capsule host widened to "
                                            + width + "x" + tall);
                                }
                            }
                        } catch (Throwable t) {
                            // A throw out of onMeasure takes the whole layout pass
                            // down, so a failure here leaves MIUI's own measurement
                            // standing instead of propagating.
                            log(LOG_WARN, "capsule measure failed: " + t);
                        }
                        return result;
                    }
                });
        return a + b + c;
    }

    private static int hookBatteryMeterView(XposedModule module, ClassLoader cl) {
        final Class<?> meter = Refl.cls(
                "com.android.systemui.statusbar.views.MiuiBatteryMeterView", cl);
        if (meter == null) {
            log(module, "MiuiBatteryMeterView missing");
            return 0;
        }
        // Pin the style to 0: MIUI then shows the digital battery view and hides
        // its hollow variant, and never measures its own charging icon or
        // percentage container. While the module is off the request is passed
        // through untouched, so MIUI is fully in charge again; while it is on the
        // style is always 0 and the native bolt is handed back on the side - see
        // the hook below.
        final Field storeRealStyle = Refl.field(meter, "mStoreRealStyle");
        sStoreRealStyleField = storeRealStyle;
        // Resolved through getDeclaredMethod rather than looked up by name on
        // every call: Refl.callByName uses getMethod, which cannot see a
        // non-public member, and the visibility of MIUI's private fields and
        // methods is not part of any contract we can rely on.
        final Method updateChargeAndText = Refl.method(meter, "updateChargeAndText");
        sUpdateChargeAndTextField = updateChargeAndText;
        sChargingViewField = Refl.field(meter, "mBatteryChargingView");
        sPercentContainerField = Refl.field(meter, "mBatteryPercentContainer");
        // Needed to hand MIUI its own charging bolt back: mBatteryStyle is the
        // switch its onMeasure, onLayout and updateChargeAndText all read, and
        // the two battery views have to be left the way a style-1 meter would
        // look, which is the opposite of the swap onBatteryStyleChanged performs.
        sBatteryStyleField = Refl.field(meter, "mBatteryStyle");
        sBatteryIconViewField = Refl.field(meter, "mBatteryIconView");
        sHollowBatteryIconViewField = Refl.field(meter, "mHollowBatteryIconView");
        final Method styleChanged = Refl.method(meter, "onBatteryStyleChanged", int.class);
        sStyleChangedField = styleChanged;
        // Runs after the meter's own layout, which is the pass that both decides
        // the host's frame and performs the bolt-style view swap. Resolved with
        // methodDeep because onLayout is inherited from LinearLayout, and
        // getDeclaredMethod would miss it - which is how this repair silently
        // failed to install once already.
        final Method meterLayout = Refl.methodDeep(meter, "onLayout",
                boolean.class, int.class, int.class, int.class, int.class);
        final int layoutHook = hook(module, meterLayout,
                "hyperduo-meter-layout", new XposedInterface.Hooker() {
                    @Override
                    public Object intercept(XposedInterface.Chain chain) throws Throwable {
                        final Object result = chain.proceed();
                        keepHostFrame(chain.getThisObject());
                        return result;
                    }
                });
        final int a = hook(module, styleChanged,
                "hyperduo-style", new XposedInterface.Hooker() {
                    @Override
                    public Object intercept(XposedInterface.Chain chain) throws Throwable {
                        final Object self = chain.getThisObject();
                        final Object requested = chain.getArg(0);
                        if (self instanceof View) {
                            rememberMeter((View) self);
                        }
                        final TrioAppearance appearance = TrioConfig.appearance();
                        // The stock bolt is ours to suppress exactly while the
                        // glyph draws its own, which is one rule and lives in
                        // one place now.
                        final boolean glyphBolt = appearance.hidesNativeBolt();
                        if (!appearance.glyph) {
                            // Nothing of ours is involved: pass the real style
                            // through and leave MIUI's own bolt alone.
                            //
                            // The field is cleared first, though. While the module
                            // was on and the glyph was drawing no bolt of its own,
                            // handBackBolt wrote BOLT_STYLE into mBatteryStyle -
                            // but onBatteryStyleChanged leaves the two battery
                            // views the way *it* wants them, which for BOLT_STYLE
                            // is the hollow outline on and the normal icon off.
                            // MIUI guards that work on "the style really changed"
                            // and would therefore skip it, leaving the normal icon
                            // in place of the hollow one. The constructor's own
                            // value is the one state that makes the guard pass.
                            Refl.set(sBatteryStyleField, self, Integer.valueOf(-1));
                            return chain.proceed();
                        }
                        // Always ask for 0, even when MIUI's bolt is wanted. The
                        // style-1 branch of onBatteryStyleChanged hides
                        // mBatteryIconView - the view the glyph is drawn on - so
                        // MIUI must never be allowed to take that branch while the
                        // module is on. The bolt is handed back by writing the
                        // field below, which no other code path reads.
                        final Object result = chain.proceed(new Object[]{Integer.valueOf(0)});
                        // L574 assigns mStoreRealStyle = i *before* the style guard,
                        // so a forced style clobbers the real one. Keyguard reads it
                        // (KeyguardStatusBarViewControllerInject: "mStoreRealStyle != 3")
                        // to choose between its icon-container and battery alpha
                        // animations, so restore the caller's value.
                        Refl.set(storeRealStyle, self, requested);
                        if (appearance.handsBackPercentage() && Integer.valueOf(3).equals(requested)) {
                            // Style 3 measures and lays out native percentage text.
                            // Keep our normal battery host visible for the glyph.
                            Refl.set(sBatteryStyleField, self, Integer.valueOf(3));
                            showIfHidden(Refl.get(sBatteryIconViewField, self));
                            hide(Refl.get(sHollowBatteryIconViewField, self));
                            Refl.invoke(sUpdateChargeAndTextField, self);
                        } else if (!glyphBolt) {
                            handBackBolt(self);
                        }
                        return result;
                    }
                });
        // updateChargeAndText() re-shows those two views on every state change.
        final int b = hook(module, updateChargeAndText,
                "hyperduo-charge-text", new XposedInterface.Hooker() {
                    @Override
                    public Object intercept(XposedInterface.Chain chain) throws Throwable {
                        final Object result = chain.proceed();
                        final Object self = chain.getThisObject();
                        if (self instanceof View) {
                            rememberMeter((View) self);
                        }
                        final TrioAppearance appearance = TrioConfig.appearance();
                        if (appearance.hidesNativeBolt()) {
                            // The glyph draws the bolt, so MIUI's own copy of it
                            // must not show up next to the ring.
                            hide(Refl.get(sChargingViewField, self));
                        }
                        // Otherwise MIUI's own decision stands: handBackBolt and
                        // the module's own hand-back leave the visibility to this
                        // method, and re-hiding here would undo them.
                        if (appearance.glyph && !appearance.handsBackPercentage()) {
                            // The percentage container is only measured and shown
                            // at battery style 3, which the module never asks for,
                            // so it is ours to hide for as long as we are on.
                            hide(Refl.get(sPercentContainerField, self));
                        }
                        return result;
                    }
                });
        return layoutHook + a + b;
    }

    /**
     * Gives MIUI's own charging bolt back to the meter it belongs to, and undoes
     * the view swap that asking for it performs.
     *
     * <p>The bolt cannot be handed back by visibility alone. In the firmware's
     * own {@code updateChargeAndText}, the charging view is only revealed while
     * {@code mBatteryStyle} is 1 or 2 - both constants are tested there - so a
     * field left at the module's forced 0 keeps MIUI's bolt hidden. The field
     * write below is therefore what actually gives the bolt back.
     *
     * <p>That same value is what {@code onBatteryStyleChanged} keys its view swap
     * on: at style 1 it hides {@code mBatteryIconView} - the view the trio glyph
     * is painted on - and shows the hollow outline instead. The style hook avoids
     * that by forcing its own {@code proceed(0)} and writing the field only after
     * the call returns, but the two callbacks run in an order MIUI picks, so the
     * host is re-asserted here as well, and again after every layout pass by
     * {@link #keepHostFrame}. Neither point alone is enough.
     */
    private static void handBackBolt(Object meter) {
        Refl.set(sBatteryStyleField, meter, Integer.valueOf(BOLT_STYLE));
        showIfHidden(Refl.get(sBatteryIconViewField, meter));
        hide(Refl.get(sHollowBatteryIconViewField, meter));
        Refl.invoke(sUpdateChargeAndTextField, meter);
    }

    /**
     * Keeps the glyph host visible and laid out at a real size.
     *
     * <p>The glyph lives on {@code mBatteryIconView}, and MIUI hides that view -
     * and gives it a zero frame - whenever the meter runs in its bolt style,
     * because in that style the hollow outline is the battery and the normal
     * icon is the spare. A VISIBLE view at 0x0 is skipped by the draw pass
     * outright, so the trio disappears with nothing looking wrong in the view
     * tree: on device the host still reported VISIBLE at the size it had, while
     * the row was blank.
     *
     * <p>Run after the meter's own layout, which is the pass that decides the
     * frame, so the correction is the last word rather than something the very
     * next pass overwrites. Only containers the module actually draws in are
     * touched, and only while the module is on.
     */
    private static void keepHostFrame(Object meter) {
        if (!(meter instanceof View) || !TrioConfig.get().enabled
                || !TrioConfig.appearance().glyph) {
            return;
        }
        try {
            final Object iconV = Refl.get(sBatteryIconViewField, meter);
            if (!(iconV instanceof View)) {
                return;
            }
            final View icon = (View) iconV;
            if (icon.getVisibility() != View.VISIBLE) {
                icon.setVisibility(View.VISIBLE);
            }
            // A hidden wrapper is enough to keep the glyph off screen no matter
            // how healthy the host looks, and MIUI hides exactly that wrapper in
            // the swap above, so the chain is repaired as well - never past the
            // meter, which is MIUI's own view.
            for (ViewParent p = icon.getParent(); p != null && p != meter;
                 p = (p instanceof View) ? ((View) p).getParent() : null) {
                if (!(p instanceof View)) {
                    break;
                }
                final View wrapper = (View) p;
                if (wrapper.getVisibility() != View.VISIBLE) {
                    wrapper.setVisibility(View.VISIBLE);
                }
            }
            if (icon.getWidth() > 0 && icon.getHeight() > 0) {
                HOST_FRAME.put(icon, new int[]{
                        icon.getLeft(), icon.getTop(), icon.getWidth(), icon.getHeight()});
                return;
            }
            final int[] good = HOST_FRAME.get(icon);
            if (good != null) {
                icon.layout(good[0], good[1], good[0] + good[2], good[1] + good[3]);
            }
        } catch (Throwable ignored) {
            // A stale frame is worse than none: leave MIUI's alone.
        }
    }

    /** Last good rect per glyph host, so a collapsed layout can be repaired. */
    private static final Map<View, int[]> HOST_FRAME =
            Collections.synchronizedMap(new WeakHashMap<View, int[]>());

    /** Records a meter view so a later settings change can re-apply its style. */
    private static void rememberMeter(View meter) {
        synchronized (METERS) {
            for (int i = 0; i < METERS.size(); i++) {
                if (METERS.get(i).get() == meter) {
                    return;
                }
            }
            METERS.add(new WeakReference<>(meter));
        }
    }

    /**
     * Re-applies our slots after the one call that clears them:
     * {@code MiuiPhoneStatusBarView.updateCutoutLocation} invokes
     * {@code setIgnoredSlots(RIGHT_BLOCK_LIST)}, which clears before adding.
     */
    private static int hookStatusBarView(XposedModule module, ClassLoader cl) {
        final Class<?> bar = Refl.cls(
                "com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView", cl);
        if (bar == null) {
            log(module, "MiuiPhoneStatusBarView missing");
            return 0;
        }
        return hook(module, Refl.method(bar, "updateCutoutLocation"),
                "hyperduo-cutout", new XposedInterface.Hooker() {
                    @Override
                    public Object intercept(XposedInterface.Chain chain) throws Throwable {
                        final Object result = chain.proceed();
                        syncSlots(sStatusIconContainer);
                        return result;
                    }
                });
    }

    /**
     * Learns the current Wi-Fi / mobile signal levels. Both binders call
     * {@code imageView.setTag(rawResId)} immediately before passing that same raw
     * id to {@code transformResId}, so the first argument is the signal icon being
     * applied right now.
     */
    private static int hookSignalIcons(XposedModule module, ClassLoader cl) {
        final Class<?> helper = Refl.cls(
                "com.android.systemui.statusbar.MiuiStatusBarIconViewHelper", cl);
        if (helper == null) {
            log(module, "MiuiStatusBarIconViewHelper missing");
            return 0;
        }
        return hook(module, Refl.method(helper, "transformResId",
                        int.class, boolean.class, boolean.class),
                "hyperduo-signal", new XposedInterface.Hooker() {
                    @Override
                    public Object intercept(XposedInterface.Chain chain) throws Throwable {
                        final Object raw = chain.getArg(0);
                        final int beforeWifi = TrioState.sWifiLevel;
                        final int beforeMobile = TrioState.sMobileLevel;
                        final Object result = chain.proceed();
                        if (raw instanceof Number) {
                            TrioState.noteSignalIcon(
                                    resources(), ((Number) raw).intValue());
                            // MIUI only re-applies an icon when the reading behind
                            // it changed, so this is the event that says the
                            // per-SIM levels are stale. The icons name no SIM, so
                            // telephony is asked directly - and only when a
                            // per-SIM reading is actually wanted: the glyph's
                            // second row, or the out-of-ring stacked reading.
                            final boolean simsMoved = (TrioConfig.get().dualSim
                                    || TrioConfig.appearance().drawsAnySimRow())
                                    && TrioState.pollSimsNow();
                            if (beforeWifi != TrioState.sWifiLevel
                                    || beforeMobile != TrioState.sMobileLevel
                                    || simsMoved) {
                                invalidateHosts();
                            }
                        }
                        return result;
                    }
                });
    }

    /**
     * Samples the mobile network type label ("5G", "5GA", "4G") that MIUI is
     * about to draw.
     *
     * <p>{@code MobileTypeDrawable.mMobileType} is owned by the
     * {@code R.id.mobile_type} image view, which has no stable global entry
     * point, so the string is sampled where it is used instead:
     * {@code measure()} runs in lockstep with MIUI's own refresh and is called
     * only when the label actually changed
     * ({@code MiuiMobileIconBinder} L1366-1373).
     *
     * <p>The read happens after {@code chain.proceed()} because {@code measure()}
     * is what normalises the label: it rewrites {@code "5G++"} into {@code "5G"}
     * plus a separate double-plus flag, so reading beforehand would surface a
     * string MIUI never paints.
     */
    private static int hookMobileType(XposedModule module, ClassLoader cl) {
        final Class<?> drawable = Refl.cls(
                "com.miui.systemui.statusbar.views.MobileTypeDrawable", cl);
        if (drawable == null) {
            log(module, "MobileTypeDrawable missing");
            return 0;
        }
        final Field type = Refl.field(drawable, "mMobileType");
        if (type == null) {
            log(module, "MobileTypeDrawable.mMobileType missing");
            return 0;
        }
        final int sampled = hook(module, Refl.method(drawable, "measure"),
                "hyperduo-mobile-type", new XposedInterface.Hooker() {
                    @Override
                    public Object intercept(XposedInterface.Chain chain) throws Throwable {
                        final String before = TrioState.sMobileType;
                        final Object result = chain.proceed();
                        final Object raw = Refl.get(type, chain.getThisObject());
                        if (raw instanceof String
                                && TrioState.setMobileType((String) raw)
                                && !before.equals(TrioState.sMobileType)) {
                            invalidateHosts();
                        }
                        return result;
                    }
                });
        // Draw-level suppression. The classic mobile_type ImageView is re-shown
        // by its binder's own flow, which does not go through setChildVisible -
        // and its drawable paints text without consulting the view's bounds, so
        // even the folded slot's 0x0 frame does not stop the ink (MIUI sets
        // clipChildren=false up the chain). Gate only this dedicated drawable's
        // draw, never View.draw: the sampling above stays intact either way, and
        // the module's replacement label reads the same string from it.
        final int drawn = hook(module, Refl.method(drawable, "draw", Canvas.class),
                "hyperduo-mobile-type-draw", new XposedInterface.Hooker() {
                    @Override
                    public Object intercept(XposedInterface.Chain chain) throws Throwable {
                        final TrioAppearance a = TrioConfig.appearance();
                        if (a.glyph && a.ownsType()) {
                            return null;
                        }
                        return chain.proceed();
                    }
                });
        return sampled + drawn;
    }

    /**
     * Turns MIUI's own "show the network type" requests into "hide it" while the
     * module draws the type itself.
     *
     * <p>{@code MobileSignalAnimatorContainer.setChildVisible(View, boolean)} is
     * the one door every native type-show walks through:
     * {@code MiuiMobileIconBinder} collects a {@code mobileTypeSingleVisible}
     * flow and calls it for both the HyperOS {@code mobile_type_single} TextView
     * and the classic {@code mobile_signal_container} group. Flipping the
     * boolean here kills the show at the source - the appear animation never
     * starts, so there is no one-frame flash the way there would be if the
     * module only re-hid the view after every layout.
     *
     * <p>The same container also serves volte / vowifi / roaming, whose shows
     * must pass untouched: the flip is gated on the view actually being one of
     * the native type views, resolved once per view by id and remembered in
     * {@link #COLLAPSED}-adjacent fashion through the view's own tag slot is
     * deliberately avoided - a tag write could collide with MIUI's
     * {@code status_bar_view_state_tag}. The id check is cheap enough per call:
     * one memoised map look-up.
     *
     * <p>Only the {@code true} requests are touched, and only while the module
     * is drawing the type itself (out of ring). Every other call proceeds
     * unchanged, so a firmware rename or an unexpected caller costs nothing.
     */
    private static int hookMobileTypeVisibility(XposedModule module, ClassLoader cl) {
        final Class<?> container = Refl.cls(
                "com.android.systemui.statusbar.views.MobileSignalAnimatorContainer", cl);
        if (container == null) {
            log(module, "MobileSignalAnimatorContainer missing");
            return 0;
        }
        return hook(module, Refl.method(container, "setChildVisible",
                        View.class, boolean.class),
                "hyperduo-mobile-type-visible", new XposedInterface.Hooker() {
                    @Override
                    public Object intercept(XposedInterface.Chain chain) throws Throwable {
                        final TrioAppearance a = TrioConfig.appearance();
                        if (a.glyph && a.ownsType()) {
                            final Object arg0 = chain.getArg(0);
                            final Object arg1 = chain.getArg(1);
                            if (arg0 instanceof View && isNativeTypeView((View) arg0)) {
                                // Both directions are suppressed, not just the
                                // show: a false request on a visible target
                                // makes the animator clone it into a transient
                                // disappearance view, which paints the type
                                // even after the slot has been folded away.
                                // Marking it INVISIBLE first still lets the
                                // call proceed, so MIUI cleans up any clone
                                // bookkeeping it already holds.
                                if (!Boolean.TRUE.equals(arg1)) {
                                    prepareNativeTypeHide((View) arg0);
                                }
                                return chain.proceed(new Object[]{arg0, Boolean.FALSE});
                            }
                        }
                        return chain.proceed();
                    }
                });
    }

    /**
     * Drops a native type view to INVISIBLE before MIUI animates it away, so
     * the disappearance clone starts from an invisible original. Recorded in
     * the collapse book, which is what the module's own restore path reads -
     * these are views this module hid.
     */
    private static void prepareNativeTypeHide(View view) {
        if (view.getVisibility() == View.VISIBLE && markCollapsed(view)) {
            try {
                view.setVisibility(View.INVISIBLE);
            } catch (Throwable ignored) {
                unmarkCollapsed(view);
            }
        }
    }

    /**
     * Keeps the trio glyph on the bar while the charging super island is up.
     *
     * <p>{@code MiuiBatteryMeterView.updateIslandChanged(boolean, boolean)} is
     * the one callee MIUI drives when the island appears or disappears; it
     * normally lands in {@code setIsHideBattery(true)}, which stops the battery
     * container reserving the meter's width - the native icon row slides right,
     * and the glyph the module paints into that meter would be left floating in
     * the vacated space. The module instead declines the replacement: the meter
     * keeps its place, the row never moves, and the glyph - plus the arcs and
     * dots - simply stay where they were while the island draws beside them.
     * On-device verification for this shape is PR #10's.
     *
     * <p>The real request is remembered per meter in
     * {@link #BATTERY_ISLAND_REQUESTS}, and {@link #replayBatteryIslandRequests}
     * re-drives every remembered meter when a settings change flips
     * {@code enabled}: a meter mid-island when the module switches off must get
     * the replacement MIUI asked for, or the bar would sit batteryless until the
     * next island event. {@code mStoreIsAddBatteryIsland} is restored after the
     * call for the same reason - it is the value other code reads back.
     *
     * <p>The {@code setIsHideBattery} hook stays as the fallback path: if this
     * meter hook is unavailable on some firmware, the old handover (hide the
     * glyph, hand Wi-Fi back, read the signal out of ring) still applies, driven
     * by the flag the setter records.
     */
    private static int hookIslandHide(XposedModule module, ClassLoader cl) {
        final Class<?> container = Refl.cls(
                "com.android.systemui.statusbar.views.MiuiStatusBatteryContainer", cl);
        if (container == null) {
            log(module, "MiuiStatusBatteryContainer missing");
            return 0;
        }
        final Class<?> meter = Refl.cls(
                "com.android.systemui.statusbar.views.MiuiBatteryMeterView", cl);
        int n = 0;
        if (meter != null) {
            n += hook(module, Refl.method(meter, "updateIslandChanged",
                            boolean.class, boolean.class),
                    "hyperduo-keep-island-battery", new XposedInterface.Hooker() {
                        @Override
                        public Object intercept(XposedInterface.Chain chain) throws Throwable {
                            return keepBatteryDuringIsland(chain);
                        }
                    });
        }
        n += hook(module, Refl.method(container, "setIsHideBattery", Boolean.class),
                "hyperduo-island-hide", new XposedInterface.Hooker() {
                    @Override
                    public Object intercept(XposedInterface.Chain chain) throws Throwable {
                        final Object arg = chain.getArg(0);
                        final boolean hide = Boolean.TRUE.equals(arg);
                        final boolean changed = TrioState.sIslandHideBattery != hide;
                        final Object result = chain.proceed();
                        if (changed) {
                            TrioState.sIslandHideBattery = hide;
                            reactToIslandChange();
                        }
                        return result;
                    }
                });
        return n;
    }

    /** Real island requests per meter, so a mid-island disable can replay them. */
    private static final Map<Object, boolean[]> BATTERY_ISLAND_REQUESTS =
            new WeakHashMap<Object, boolean[]>();

    private static Object keepBatteryDuringIsland(XposedInterface.Chain chain) throws Throwable {
        final boolean requested = Boolean.TRUE.equals(chain.getArg(0));
        final boolean trigger = Boolean.TRUE.equals(chain.getArg(1));
        synchronized (BATTERY_ISLAND_REQUESTS) {
            BATTERY_ISLAND_REQUESTS.put(chain.getThisObject(),
                    new boolean[] {requested, trigger});
        }
        // Declining the replacement while the module owns the glyph: the flag
        // that stops the container from reserving the meter's width stays down,
        // so the bar keeps the glyph exactly where it was. The island window
        // and its clearance stay MIUI's - only the meter's participation moves.
        final Object result = chain.proceed(new Object[] {
                Boolean.valueOf(requested && !TrioConfig.get().enabled),
                Boolean.valueOf(trigger)});
        final Object meter = chain.getThisObject();
        Refl.set(Refl.field(meter.getClass(), "mStoreIsAddBatteryIsland"), meter,
                Boolean.valueOf(requested));
        return result;
    }

    /**
     * Re-drives every meter whose island request the module declined, with the
     * real values. Run from a settings change: a meter that was mid-island when
     * the module switched off must still receive MIUI's original request, or it
     * would keep painting a battery the island had replaced until the next
     * island event.
     */
    private static void replayBatteryIslandRequests() {
        final Map<Object, boolean[]> requests;
        synchronized (BATTERY_ISLAND_REQUESTS) {
            requests = new HashMap<Object, boolean[]>(BATTERY_ISLAND_REQUESTS);
        }
        for (Map.Entry<Object, boolean[]> entry : requests.entrySet()) {
            final boolean[] args = entry.getValue();
            Refl.callArgs(entry.getKey(), "updateIslandChanged",
                    new Class<?>[] {boolean.class, boolean.class},
                    new Object[] {Boolean.valueOf(args[0]), Boolean.valueOf(args[1])});
        }
    }

    /**
     * One re-fold and one resync after the island flag moved, posted to the
     * main looper - the same shape {@code applyConfigChange} gives a settings
     * change, minus the meter restyling the island does not touch.
     */
    private static void reactToIslandChange() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    reactToIslandChange();
                }
            });
            return;
        }
        refoldContainers();
        syncKeyguardRows();
        final List<TrioState> hosts;
        synchronized (HOSTS) {
            hosts = new ArrayList<TrioState>(HOSTS);
        }
        for (int i = 0; i < hosts.size(); i++) {
            final View v = hosts.get(i).host;
            if (v == null) {
                continue;
            }
            v.post(new Runnable() {
                @Override
                public void run() {
                    foldHostContainer(v);
                    v.requestLayout();
                    syncOutSignal(v);
                    // Between the two on purpose: the label chains through the
                    // performance readout, which chains through the reading, so
                    // each is measured before the one that stands outside it.
                    syncPerfLabel(v);
                    syncOutTypeLabel(v);
                    v.invalidate();
                }
            });
        }
    }

    /** Memoised view-id to "is a native network-type view" answers. */
    private static final Map<Integer, Boolean> NATIVE_TYPE_VIEW_CACHE =
            new HashMap<Integer, Boolean>();

    /**
     * Whether {@code view} is one of the native views that draw the network
     * type, matched by the resource name of its id. The two candidates are the
     * classic {@code mobile_type} ImageView and the HyperOS
     * {@code mobile_type_single} TextView; anything else - the signal image,
     * the volte and vowifi views, the dot - is not.
     */
    private static boolean isNativeTypeView(View view) {
        final int id = view.getId();
        if (id <= 0) {
            return false;
        }
        synchronized (NATIVE_TYPE_VIEW_CACHE) {
            final Boolean cached = NATIVE_TYPE_VIEW_CACHE.get(id);
            if (cached != null) {
                return cached.booleanValue();
            }
        }
        String name = null;
        try {
            final android.content.res.Resources res = view.getResources();
            if (res != null) {
                name = res.getResourceEntryName(id);
            }
        } catch (Throwable ignored) {
            // a foreign id is simply not one of ours
        }
        final boolean match = "mobile_type".equals(name)
                || "mobile_type_single".equals(name);
        synchronized (NATIVE_TYPE_VIEW_CACHE) {
            NATIVE_TYPE_VIEW_CACHE.put(Integer.valueOf(id), Boolean.valueOf(match));
        }
        return match;
    }

    /**
     * Recolours the out-of-ring views in the same beat MIUI tints the meter.
     *
     * <p>The tint pipeline reaches this module's two sibling views only
     * indirectly: the tint animation runs once per animation frame through
     * {@code MiuiBatteryMeterView.updateLightDarkTint}, which ends in the
     * battery icon's own {@code invalidate()} - and the icon's next draw is
     * where the draw-pass comparison picks the new ink up. That indirection is
     * the "delayed transition" of issue #2: the native icons ride MIUI's
     * animation, while the label and the reading wait for the glyph's next
     * frame and then flip in one step.
     *
     * <p>Hooking {@code updateLightDarkTint} (the single door both the animated
     * and the instant path walk through - see {@code onLightDarkTintChanged}
     * and the value animator inside the meter) puts the two views on MIUI's own
     * schedule: after the meter has applied this frame's tint, the frame's
     * final tint colour is handed to {@link #applyOutRingInk} directly, no post
     * and no draw needed, so label and reading blend at the same pace as every
     * native icon.
     *
     * <p>The hook fires per animation frame (a transition is a handful of
     * frames), and the body is two field reads and a colour compare per frame
     * - the same weight MIUI itself pays per frame. No book is needed here:
     * {@code applyOutRingInk} compares before writing, and the draw-pass
     * comparison stays as the safety net for tint changes that arrive without
     * this method (a config change, a restored view tree).
     */
    private static int hookMeterTint(XposedModule module, ClassLoader cl) {
        final Class<?> meter = Refl.cls(
                "com.android.systemui.statusbar.views.MiuiBatteryMeterView", cl);
        if (meter == null) {
            log(module, "MiuiBatteryMeterView missing");
            return 0;
        }
        return hook(module, Refl.method(meter, "updateLightDarkTint",
                        ArrayList.class, float.class, int.class, int.class,
                        int.class, boolean.class),
                "hyperduo-meter-tint", new XposedInterface.Hooker() {
                    @Override
                    public Object intercept(XposedInterface.Chain chain) throws Throwable {
                        final Object result = chain.proceed();
                        final Object self = chain.getThisObject();
                        if (self instanceof View) {
                            // Signature: (areas, intensity, tintColor, light, dark,
                            // useTint). Resolve the ink exactly the way
                            // TrioState.foreground() resolves it from the very
                            // fields this call installs, so the tint-path colour
                            // and the draw-path colour are one number by
                            // construction.
                            final boolean useTint = Boolean.TRUE.equals(chain.getArg(5));
                            final float intensity =
                                    (chain.getArg(1) instanceof Float)
                                            ? ((Float) chain.getArg(1)).floatValue() : 0f;
                            final Object tintObj = chain.getArg(2);
                            final Object lightObj = chain.getArg(3);
                            final Object darkObj = chain.getArg(4);
                            final int tint = (tintObj instanceof Integer)
                                    ? (Integer) tintObj : 0;
                            final int light = (lightObj instanceof Integer)
                                    ? (Integer) lightObj : 0;
                            final int dark = (darkObj instanceof Integer)
                                    ? (Integer) darkObj : 0;
                            final View v = (View) self;
                            final Object owner = batteryContainerOf(v);
                            if (owner instanceof ViewGroup) {
                                final ViewGroup row = (ViewGroup) owner;
                                // Hidden native icons can stop reporting tint. The
                                // meter still receives each frame, so archive it
                                // against its own row before any glyph reads ink.
                                final int previous = rowInk(row);
                                final TrioTint.Inputs inputs = new TrioTint.Inputs(
                                        chain.getArg(0), intensity, tint, light, dark, useTint);
                                final View host = hostIn(row);
                                final int ink = inputs.colour(host != null ? host : v);
                                ROW_TINTS.put(row, inputs);
                                ROW_INK.put(row, ink);
                                applyOutRingInk(row, ink);
                                if (previous != ink) {
                                    if (host != null) {
                                        host.invalidate();
                                    }
                                }
                            }
                        }
                        return result;
                    }
                });
    }

    /**
     * The colour SystemUI last painted the icons of one row in, keyed by that
     * row's battery container.
     *
     * <p>Keyed per row because several rows are on screen at once and they are
     * <em>not</em> tinted alike: the status bar sits on the launcher's light
     * surface while the control centre beside it is dark, and both are tinted in
     * the same frame. One shared slot therefore lets whichever row was tinted
     * last decide for all of them, and every glyph draws in the opposite colour
     * to the icons beside it - the inversion this replaced. The values are bare
     * {@code Integer}s, so - unlike a {@code WeakHashMap<View, TextView>} - they
     * cannot reach back and keep their own keys alive.
     */
    private static final Map<View, Integer> ROW_INK =
            Collections.synchronizedMap(new WeakHashMap<View, Integer>());

    /**
     * The colour SystemUI last painted the icons of {@code row}'s own row in, or
     * {@code 0} when that row has not been tinted yet.
     */
    static int rowInk(View row) {
        if (row == null) {
            return 0;
        }
        final TrioTint.Inputs inputs = ROW_TINTS.get(row);
        if (inputs != null && row instanceof ViewGroup) {
            final View host = hostIn((ViewGroup) row);
            if (host != null) {
                // Area membership can change on layout without a new colour
                // dispatch, e.g. when switching to the wider capsule host.
                return inputs.colour(host);
            }
        }
        final Integer ink = ROW_INK.get(row);
        return ink == null ? 0 : ink;
    }

    /** The row (battery container) {@code host} belongs to, or null. */
    static View rowOf(View host) {
        return batteryContainerOf(host);
    }

    private static final Map<View, TrioTint.Inputs> ROW_TINTS =
            Collections.synchronizedMap(new WeakHashMap<View, TrioTint.Inputs>());

    /**
     * Hears the colour SystemUI paints its own icons in.
     *
     * <p>The tint fields the colour rule reads are set when the bar is built and
     * lag behind a change of background; the call SystemUI makes to repaint the
     * icons does not - it happens as the change lands. Both spellings are hooked
     * because both are in the tree: {@code onDarkChanged} is the AOSP one, and
     * {@code setIconColor} is the one MIUI added around it. Only the colour
     * argument is read; the rest of the signature is left alone.
     */
    private static int hookIconTint(XposedModule module, ClassLoader cl) {
        final Class<?> icon = Refl.cls("com.android.systemui.statusbar.StatusBarIconView", cl);
        if (icon == null) {
            log(module, "StatusBarIconView missing");
            return 0;
        }
        final XposedInterface.Hooker hooker = new XposedInterface.Hooker() {
            @Override
            public Object intercept(XposedInterface.Chain chain) throws Throwable {
                final Object result = chain.proceed();
                final List<Object> args = chain.getArgs();
                if (args == null) {
                    return result;
                }
                int colour = 0;
                for (int i = 0; i < args.size(); i++) {
                    final Object arg = args.get(i);
                    if (arg instanceof Integer) {
                        colour = (Integer) arg;
                        break;
                    }
                }
                if (colour == 0) {
                    return result;
                }
                // Filed against the row this icon belongs to, not one global
                // cell: the status bar and the control centre are tinted in the
                // same frame and to opposite colours, so a shared cell hands
                // whichever was tinted last to both and inverts one of them.
                final Object self = chain.getThisObject();
                if (self instanceof View) {
                    final View row = rowOf((View) self);
                    if (row != null) {
                        // A neighbouring icon may lie in a different tint area.
                        // Its raw tint must never overwrite the battery's own
                        // area-aware dispatch, regardless of callback order.
                        if (ROW_TINTS.containsKey(row)) {
                            return result;
                        }
                        if (!args.isEmpty() && args.get(0) instanceof ArrayList<?>) {
                            final View host = row instanceof ViewGroup
                                    ? hostIn((ViewGroup) row) : null;
                            colour = new TrioTint.Inputs(args.get(0), 0f, colour,
                                    0xFFFFFFFF, 0xFF000000, true)
                                    .colour(host != null ? host : (View) self);
                        }
                        ROW_INK.put(row, colour);
                    }
                }
                return result;
            }
        };
        int n = 0;
        n += hook(module, Refl.method(icon, "onDarkChanged",
                        java.util.ArrayList.class, float.class, int.class),
                "hyperduo-tint-dark", hooker);
        n += hook(module, Refl.method(icon, "setIconColor", int.class, boolean.class),
                "hyperduo-tint-set", hooker);
        if (n == 0) {
            log(module, "no icon tint hook");
        }
        return n;
    }

    /**
     * Hides the status bar's own clock while the capsule is drawing the time.
     *
     * <p>Only the capsule needs this, and only because it puts the time inside
     * the icon: every other arrangement leaves the bar's clock as the single
     * source of the time, and hiding it there would simply remove the time from
     * the phone.
     *
     * <p><b>Why {@code setMultiTaskVisibility} and not {@code ignoredSlots}.</b>
     * {@code MiuiClock} is a bare {@code TextView} - it has no {@code getSlot()}
     * and is not a child of {@code MiuiStatusIconContainer}, so the slot
     * suppression this module uses for the Wi-Fi and mobile icons cannot reach it.
     * MIUI's own lever is {@code setMultiTaskVisibility(int)}, and it is the right
     * one for two reasons: it is public, and it survives the {@code
     * setPolicyVisibility(0)} that MIUI runs when it re-shows the clock, so the
     * hiding is not undone by the next policy pass.
     *
     * <p><b>Why {@code GONE} and not {@code INVISIBLE}.</b> {@code INVISIBLE} hides
     * the digits but keeps the view measured, so the bar reserves a clock-sized
     * space and paints nothing in it - a hole where the time used to be, which
     * reads as a layout bug rather than as a bar without a clock. {@code GONE} lets
     * {@code phone_status_bar_left_container} collapse and the bar close the gap.
     *
     * <p>The width that {@code GONE} gives up is read in
     * {@code HoverViewManager.calculateIsInClockRect}, which is why the safer
     * looking {@code INVISIBLE} was the first choice. It turns out not to matter,
     * and that was checked against this ROM rather than reasoned about: the method
     * has exactly two call sites, both inside {@code MiuiStatusBarHoverHelper}, and
     * that helper is only constructed under {@code MiuiConfigs.IS_PAD}
     * ({@code MiuiPhoneStatusBarView.java:418}). On a phone {@code setDependence}
     * takes the other branch and sets {@code mHoverHelper = null} (:472), so the
     * rectangle is never consulted - and this device reports
     * {@code ro.build.characteristics=nosdcard}. Even where it is consulted, the
     * affected paths are hover highlighting and a mouse button release; the finger
     * gesture that opens the shade is decided in
     * {@code ControlCenterEventHandlerImpl} from {@code getRawX()} against half the
     * screen width and never reads this rectangle.
     *
     * <p>Setting {@code mMultiTaskVisibility} rather than {@code mPolicyVisibility}
     * is the other half of surviving MIUI: the policy field is MIUI's own state
     * machine, written by its {@code hideClock}/{@code showClock} paths, so a value
     * put there is reverted by the next {@code showClock}. The multi-task field is
     * referenced nowhere outside {@code MiuiClock}, so nothing races us for it, and
     * {@code updateClockVisibility} gives it priority over the policy value.
     *
     * <p>The other clocks on the phone - the keyguard's, the shade header's, the
     * control centre's, the always-on display's - are separate instances of a
     * different view and are deliberately left alone. Hiding the bar's clock must
     * not blank the time on the lock screen.
     */
    private static int hookStatusClock(XposedModule module, ClassLoader cl) {
        final Class<?> clockClass = Refl.cls("com.android.systemui.statusbar.views.MiuiClock", cl);
        if (clockClass == null) {
            log(module, "MiuiClock missing");
            return 0;
        }
        final Method setMultiTaskVisibility =
                Refl.method(clockClass, "setMultiTaskVisibility", int.class);
        if (setMultiTaskVisibility == null) {
            log(module, "MiuiClock.setMultiTaskVisibility missing");
            return 0;
        }
        sClockSetter = setMultiTaskVisibility;
        final Class<?> barView = Refl.cls(
                "com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView", cl);
        if (barView == null) {
            log(module, "MiuiPhoneStatusBarView missing");
            return 0;
        }
        final Method onFinishInflate = Refl.method(barView, "onFinishInflate");
        if (onFinishInflate == null) {
            log(module, "MiuiPhoneStatusBarView.onFinishInflate missing");
            return 0;
        }
        final XposedInterface.Hooker hooker = new XposedInterface.Hooker() {
            @Override
            public Object intercept(XposedInterface.Chain chain) throws Throwable {
                final Object result = chain.proceed();
                final Object self = chain.getThisObject();
                if (!(self instanceof View)) {
                    return result;
                }
                final View bar = (View) self;
                // Posted: the bar's children are inflated after this returns on
                // some builds, so a clock found here may not be the last one.
                bar.post(new Runnable() {
                    @Override
                    public void run() {
                        final View clock = findClock(bar, clockClass);
                        if (clock == null) {
                            return;
                        }
                        if (sStatusClock != clock) {
                            sStatusClock = clock;
                            sClockHidden = false;
                        }
                        applyClockVisibility();
                    }
                });
                return result;
            }
        };
        return hook(module, onFinishInflate, "hyperduo-status-clock", hooker);
    }

    /**
     * The first {@code MiuiClock} at or under {@code bar}, or null.
     *
     * <p>A search rather than a field read: the bar keeps its clock in a private
     * field whose name has changed between builds, and the keyguard's clock lives
     * under a different root - so walking this one subtree cannot reach it.
     */
    private static View findClock(View bar, Class<?> clockClass) {
        if (clockClass.isInstance(bar)) {
            return bar;
        }
        if (!(bar instanceof ViewGroup)) {
            return null;
        }
        final ViewGroup group = (ViewGroup) bar;
        for (int i = 0; i < group.getChildCount(); i++) {
            final View found = findClock(group.getChildAt(i), clockClass);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * Applies the clock's visibility for the current appearance.
     *
     * <p>Idempotent and cheap, so it can be called from the layout path as well as
     * from a settings change - which is what it takes, because MIUI re-shows the
     * clock on its own schedule and every re-show has to be answered.
     */
    static void applyClockVisibility() {
        final View clock = sStatusClock;
        final Method setter = sClockSetter;
        if (clock == null || setter == null) {
            return;
        }
        // The bar keeps its own clock unless the capsule is REALLY drawing the time.
        // Two things can make that false even with the clock placed: the capsule may be
        // off, and the piece that took the left cell may have nothing to show this
        // frame - Wi-Fi with no connection, a type with no network. In both cases the
        // cell would otherwise be empty AND the bar's clock hidden, so the phone
        // would simply have no time. Handing the cell back to the system is the
        // fallback, and this is where it is decided because this is the only place that
        // both owns the clock view and can read the live readings.
        final boolean hide = TrioConfig.appearance().hidesStatusClock();
        if (hide == sClockHidden) {
            return;
        }
        // GONE, not INVISIBLE. INVISIBLE hides the digits but leaves the view
        // measured, so the clock's width stays reserved and the bar keeps a
        // clock-sized hole where nothing is painted - which is a worse result than
        // either drawing the clock or removing it. GONE lets the left container
        // collapse and the bar close the gap.
        //
        // The reason INVISIBLE looked necessary is that the clock's width is read
        // in HoverViewManager.calculateIsInClockRect, which decides whether a
        // pointer in the clock's rectangle counts as being on the clock. Two things
        // make that harmless here, and both were checked against this ROM rather
        // than assumed:
        //
        //  1. On a phone the rectangle is never consulted. calculateIsInClockRect
        //     has exactly two call sites, both in MiuiStatusBarHoverHelper, and
        //     that helper is constructed only under MiuiConfigs.IS_PAD
        //     (MiuiPhoneStatusBarView.java:418); on a phone setDependence takes
        //     the other branch and sets mHoverHelper = null (:472). This device
        //     reports ro.build.characteristics=nosdcard, so IS_PAD is false.
        //  2. Even on a tablet, the affected paths are hover highlighting and a
        //     mouse's ACTION_BUTTON_RELEASE. The finger gesture that opens the
        //     shade is decided in ControlCenterEventHandlerImpl from
        //     motionEvent.getRawX() against half the screen width, and never reads
        //     this rectangle at all.
        //
        // MiuiClock's own multi-task parameter, rather than setPolicyVisibility:
        // mPolicyVisibility is MIUI's own state machine, read and written by its
        // hideClock/showClock paths, so anything written there is reverted by the
        // next showClock. mMultiTaskVisibility is referenced nowhere outside
        // MiuiClock itself, so nothing races us for it - and updateClockVisibility
        // gives it priority over the policy value, which is what makes the hiding
        // survive MIUI's own re-show.
        final int goneReleasingTheSpace = 8;
        try {
            setter.invoke(clock, Integer.valueOf(hide ? goneReleasingTheSpace : 0));
            sClockHidden = hide;
        } catch (Throwable t) {
            log(sModule, "clock visibility failed: " + t);
        }
    }

    /**
     * Folds the keyguard row's icons when that row attaches.
     *
     * <p>{@link #refoldContainers()} runs on a config change and on an island
     * change, and the keyguard row's container reaches {@link #settle} down the
     * layout path only while it is still laying out. A row that attaches after
     * that - which is what a SystemUI restart with the lock screen already up
     * produces - keeps its native icons until something else happens to fold it,
     * and nothing else necessarily does. That is the shape of the report where
     * restarting SystemUI a few times makes the lock screen come good.
     *
     * <p>Attaching is the one moment that always happens for the row, so the fold
     * is asked for there. The layout path and the draw pass keep their own
     * attempts; all three end in the same idempotent call.
     */
    private static int hookKeyguardBar(XposedModule module, ClassLoader cl) {
        final Class<?> bar = Refl.cls(
                "com.android.systemui.statusbar.phone.MiuiKeyguardStatusBarView", cl);
        if (bar == null) {
            log(module, "MiuiKeyguardStatusBarView missing");
            return 0;
        }
        final XposedInterface.Hooker hooker = new XposedInterface.Hooker() {
            @Override
            public Object intercept(XposedInterface.Chain chain) throws Throwable {
                final Object result = chain.proceed();
                final Object self = chain.getThisObject();
                if (!(self instanceof View)) {
                    return result;
                }
                final View row = (View) self;
                synchronized (KEYGUARD_ROWS) { KEYGUARD_ROWS.put(row, Boolean.TRUE); }
                // Posted, not run here: the row's icons are inflated as part of
                // attaching, and naming slots before that would name them to a
                // controller that has nothing to refresh yet.
                row.post(new Runnable() {
                    @Override
                    public void run() {
                        // MIUI re-lays the row's icons out after it attaches, and a
                        // GONE plus a zero-size layout does not survive that: the log
                        // shows the mobile views folded (v=8, w=0) and then back
                        // (v=0, w=56) a moment later. The row's own ignore list is
                        // what MIUI consults when it lays them out, so the slots this
                        // module draws itself are named there.
                        blockIcons(row);
                        foldContainers(row);
                    }
                });
                return result;
            }
        };
        // initCallback is the one that matters, and it is later than it looks:
        // onAttachedToWindow only calls super and then hands the real work to
        // initCallback, which is also reached from setDependency. It is the
        // method that finishes configuring the row - setNeedLimitIcon(true),
        // setAnimatorController - and that configuration is what undoes a fold
        // made while attaching, which is how the mobile views came back at
        // v=0 w=56 after being folded to v=8 w=0. Folding after initCallback
        // is what survives.
        //
        // The View entry points are kept as well: on some builds initCallback
        // is absent and attaching is the only moment the row is complete.
        int n = hook(module, Refl.method(bar, "initCallback", String.class),
                "hyperduo-keyguard-init", hooker);
        n += hook(module, Refl.method(bar, "miuiOnAttachedToWindow"),
                "hyperduo-keyguard-bar", hooker);
        n += hook(module, Refl.method(bar, "onAttachedToWindow"),
                "hyperduo-keyguard-attach", hooker);
        if (n == 0) {
            log(module, "no keyguard row attach hook");
        }
        return n;
    }

    /**
     * Names the slots this module draws itself in MIUI's own ignore list for the
     * keyguard row, and asks the row to re-run its layout.
     *
     * <p>This is the part that survives: MIUI lays the row's icons out again after
     * the row attaches, and it skips exactly the slots on this list. Folding them
     * after the fact is what the log shows being undone.
     */
    private static void blockIcons(Object bar) {
        try {
            final Object manager = Refl.get(Refl.field(bar.getClass(), "mTintedIconManager"), bar);
            if (manager == null) {
                log(sModule, "keyguard: no tinted icon manager");
                return;
            }
            final List<String> wanted = new ArrayList<>();
            final Object current = Refl.get(Refl.field(manager.getClass(), "mBlockList"), manager);
            if (current instanceof List<?>) {
                for (Object slot : (List<?>) current) if (slot instanceof String) wanted.add((String) slot);
            }
            synchronized (KEYGUARD_ADDED) {
                final List<String> previous = KEYGUARD_ADDED.get(manager);
                if (previous != null) wanted.removeAll(previous);
                final List<String> added = new ArrayList<>();
                for (String slot : foldedSlots()) if (!wanted.contains(slot)) {
                    wanted.add(slot);
                    added.add(slot);
                }
                KEYGUARD_ADDED.put(manager, added);
            }
            // IconManager.setBlockList(List) clears the list, fills it and calls
            // refreshIconGroup in one go, and refreshIconGroup is what makes MIUI
            // lay the row's icons out again without the named slots. Writing the
            // list by hand and asking for the refresh separately does not work:
            // both the list and the controller that would be asked are on
            // IconManager, one level up from the manager the row holds.
            final Method[] methods = manager.getClass().getMethods();
            for (int i = 0; i < methods.length; i++) {
                final Method m = methods[i];
                if ("setBlockList".equals(m.getName()) && m.getParameterTypes().length == 1) {
                    m.invoke(manager, wanted);
                    if (debugLog()) {
                        log(sModule, "keyguard block list set: " + wanted);
                    }
                    return;
                }
            }
            log(sModule, "keyguard: setBlockList missing");
        } catch (Throwable t) {
            log(sModule, "keyguard block list failed: " + t);
        }
    }

    /** Refresh the lockscreen's own suppression whenever component ownership changes. */
    private static void syncKeyguardRows() {
        synchronized (KEYGUARD_ROWS) {
            for (View row : new ArrayList<>(KEYGUARD_ROWS.keySet())) {
                row.post(() -> { blockIcons(row); foldContainers(row); });
            }
        }
    }

    /**
     * Hides a replaced icon the moment MIUI adds it, wherever it is added.
     *
     * <p>{@code IconManager.addHolder} is the one place every status icon is
     * mounted, old pipeline and new: it builds the view and adds it to the group
     * in the same call. That makes it the only point where hiding an icon cannot
     * be undone - MIUI is not restoring anything, it is adding it again, and this
     * runs on every addition.
     *
     * <p>It is also where the module's own block list falls short on this build:
     * the blocked flag {@code addHolder} computes is passed to the old-pipeline
     * view and to the network-speed view, but the modern mobile and wifi views are
     * constructed without it, which is why naming the slots in MIUI's list left
     * the lock screen's signal icons on screen.
     */
    private static int hookIconAdded(XposedModule module, ClassLoader cl) {
        final Class<?> manager = Refl.cls(
                "com.android.systemui.statusbar.phone.ui.IconManager", cl);
        if (manager == null) {
            log(module, "IconManager missing");
            return 0;
        }
        final Method[] methods = manager.getDeclaredMethods();
        int n = 0;
        for (int i = 0; i < methods.length; i++) {
            final Method m = methods[i];
            if (!"addHolder".equals(m.getName()) || m.getParameterTypes().length != 4) {
                continue;
            }
            n += hook(module, m, "hyperduo-icon-added", new XposedInterface.Hooker() {
                @Override
                public Object intercept(XposedInterface.Chain chain) throws Throwable {
                    final Object result = chain.proceed();
                    final Object slot = chain.getArg(1);
                    if (result instanceof View && slot instanceof String
                            && foldedSlots().contains(slot)) {
                        hideIcon((View) result);
                    }
                    return result;
                }
            });
        }
        if (n == 0) {
            log(module, "no addHolder hook");
        }
        return n;
    }

    /**
     * Takes an icon out of the layout and out of drawing.
     *
     * <p>Two ways, because there are two pipelines. The old one has already been
     * laid out by the time this runs, so it needs the zero-size layout and the
     * GONE - the same pair settle() uses. The new one asks
     * {@code ModernStatusBarView.isIconVisible()}, which is driven by the binding
     * and by {@code setVisibleState}, and never by {@code View.getVisibility()}:
     * a GONE there changes nothing, and MIUI restores the state on its own
     * schedule, which is what kept putting the lock screen's signal icons back.
     */
    private static void hideIcon(final View icon) {
        try {
            icon.post(new Runnable() {
                @Override
                public void run() {
                    // Ownership can change between addHolder and this posted work.
                    if (!foldedSlots().contains(slotOf(icon))) return;
                    if (icon.getVisibility() == View.VISIBLE) markCollapsed(icon);
                    try {
                        if (icon.getWidth() != 0 || icon.getHeight() != 0) {
                            icon.layout(0, 0, 0, 0);
                        }
                        icon.setVisibility(View.GONE);
                    } catch (Throwable ignored) {
                        // never let one icon abort the pass
                    }
                    try {
                        final Method state = icon.getClass()
                                .getMethod("setVisibleState", int.class, boolean.class);
                        state.invoke(icon, Integer.valueOf(0), Boolean.TRUE);
                    } catch (Throwable ignored) {
                        // the old pipeline views have no such method
                    }
                }
            });
        } catch (Throwable ignored) {
            // posting is best-effort; the fold path still runs on layout
        }
    }

    /** Folds every icon container inside {@code view}. */
    private static void foldContainers(View view) {
        if (view instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) view;
            if (sIconContainerClass != null && sIconContainerClass.isInstance(group)) {
                foldAndSettle(group);
            }
            for (int i = 0; i < group.getChildCount(); i++) {
                foldContainers(group.getChildAt(i));
            }
        }
    }

    /** True when {@code view} sits inside the keyguard's own status bar row. */
    private static boolean isKeyguardRow(View view) {
        for (ViewParent p = view.getParent(); p != null;
             p = (p instanceof View) ? ((View) p).getParent() : null) {
            if (p.getClass().getSimpleName().contains("Keyguard")) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------- registrations

    /**
     * Hides the native Wi-Fi / mobile views inside every icon container that
     * renders the trio glyph.
     *
     * <p>{@code ignoredSlots} alone only removes a child from measurement; the
     * layout pass still parks it at the container's left edge, which - in the
     * status bar - is the middle of the screen, so the orphaned "5G" label stays
     * visible next to the clock. Forcing the child {@code GONE} removes it from
     * the pass (and from drawing) entirely. Re-applying on every layout keeps it
     * gone when MIUI re-shows it.
     *
     * <p>Hiding is limited to containers that own a live host: a container whose
     * battery view is hidden (island, minimalism, control-center collapse) never
     * draws the glyph, and stripping its signal icons would leave it empty.
     */
    private static int hookIconContainerLayout(XposedModule module, ClassLoader cl) {
        final Class<?> container = Refl.cls(
                "com.android.systemui.statusbar.views.MiuiStatusIconContainer", cl);
        if (container == null) {
            log(module, "MiuiStatusIconContainer missing");
            return 0;
        }
        sIconContainerClass = container;
        sIgnoredSlotsField = Refl.field(container, "ignoredSlots");
        return hook(module, Refl.method(container, "onLayout",
                        boolean.class, int.class, int.class, int.class, int.class),
                "hyperduo-icon-layout", new XposedInterface.Hooker() {
                    @Override
                    public Object intercept(XposedInterface.Chain chain) throws Throwable {
                        final Object result = chain.proceed();
                        final Object self = chain.getThisObject();
                        if (self instanceof ViewGroup) {
                            settle((ViewGroup) self);
                        }
                        return result;
                    }
                });
    }

    /**
     * Brings one icon container into its steady state: our slots suppressed, the
     * folded native children collapsed out of the layout, and - just as
     * important - the children whose sub-toggle went off handed back to MIUI.
     * Runs inside {@code onLayout}, after the container has finished positioning
     * children.
     */
    /** Last container reported as skipped, so the same one is not logged twice. */
    private static volatile String sLastSkipped;

    /**
     * Records a container whose icons are left alone.
     *
     * <p>Hiding is limited to containers that own a live host, and a container
     * that is skipped keeps drawing its own signal icons. On the lock screen
     * that is what puts the native signal icons next to the glyph, so the chain
     * is written down: it says which container it was and where it sits.
     */
    private static void noteSkipped(View container) {
        final StringBuilder chain = new StringBuilder(container.getClass().getSimpleName());
        for (ViewParent p = container.getParent(); p != null && chain.length() < 200;
             p = (p instanceof View) ? ((View) p).getParent() : null) {
            chain.append('<').append(p.getClass().getSimpleName());
        }
        final String note = "skip container: " + chain;
        if (!note.equals(sLastSkipped)) {
            sLastSkipped = note;
            // Diagnostic, not an event: one line per container shape is still
            // a dozen lines on a normal boot, and they all fire from the layout
            // path (issue #40).
            if (debugLog()) {
                log(sModule, note);
            }
        }
    }

    private static void settle(ViewGroup container) {
        if (!TrioConfig.get().enabled) {
            return;
        }
        ensureFolded(container);
        sampleSignalPresence(container);
        final boolean owned = isOwned(container);
        diagnose(container, owned);
        if (!owned) {
            noteSkipped(container);
            return;
        }
        // Refresh-only: this runs inside onLayout, so it may reposition the
        // label but must never add or remove one.
        // The reading first: the label stands against it when it is mounted, so
        // placing the label before it would place it against last frame's frame.
        refreshOutSignal(container);
        refreshPerfLabel(container);
        refreshOutTypeLabel(container);
        final List<String> wanted = foldedSlots();
        final int count = container.getChildCount();
        boolean relayout = false;
        for (int i = 0; i < count; i++) {
            final View child;
            try {
                child = container.getChildAt(i);
            } catch (Throwable t) {
                continue;
            }
            if (child == null) {
                continue;
            }
            final String slot = slotOf(child);
            if (slot == null || !MANAGED_SLOTS.contains(slot)) {
                continue;
            }
            if (!wanted.contains(slot)) {
                // The sub-toggle for this slot is off, so the native icon belongs
                // to MIUI again. Undo only what we did: a child MIUI itself hid
                // stays hidden, and the zero-size layout below heals on its own,
                // because MIUI lays out every slot that is not ignored.
                if (unmarkCollapsed(child)) {
                    try {
                        if (child.getVisibility() != View.VISIBLE) {
                            child.setVisibility(View.VISIBLE);
                        }
                    } catch (Throwable ignored) {
                        // never let one child abort the pass
                    }
                    // The slot was ignored a moment ago, so the container still
                    // holds the measurement taken with this child collapsed.
                    relayout = true;
                }
                continue;
            }
            // Collapse the child out of the layout. MIUI's own onLayout lays every
            // child out at container-local x=0 in its first pass and its later
            // passes only move the ones it does *not* ignore, so an ignored
            // "mobile" stays stranded at the container's left edge - which for the
            // status bar is the middle of the screen. layout() does not schedule
            // another pass, so this cannot loop.
            try {
                if (child.getWidth() != 0 || child.getHeight() != 0) {
                    child.layout(0, 0, 0, 0);
                }
            } catch (Throwable ignored) {
                // never let one child abort the pass
            }
            // Also take it out of drawing. This has to be re-applied on every
            // pass, not once: gating it on markCollapsed meant the GONE was
            // applied the first time only, while MIUI keeps putting the child
            // back on its own schedule - the log shows a mobile view at v=8
            // and then at v=0 w=56 a few passes later, which is a signal
            // indicator that came back and stayed. The mark is bookkeeping for
            // restoreNative, so it is recorded here rather than used as a
            // condition; setVisibility is already a no-op once the child is
            // GONE, so re-applying it cannot schedule extra layouts.
            if (child.getVisibility() != View.GONE) {
                markCollapsed(child);
                try {
                    child.setVisibility(View.GONE);
                } catch (Throwable ignored) {
                    // never let one child abort the pass
                }
            }
        }
        if (relayout) {
            container.requestLayout();
        }
        suppressNativeTypeViews(container);
    }

    /** Resource names of the native views that draw the network-type text. */
    private static final String[] NATIVE_TYPE_IDS = {"mobile_type_single", "mobile_type"};

    /** Memoised {@code R.id} look-ups; both ids are stable for the process. */
    private static final Map<String, Integer> NATIVE_TYPE_VIEW_IDS =
            new HashMap<String, Integer>();

    /**
     * Hides or restores the native network-type text views inside an owned
     * container, the ones no slot fold reaches.
     *
     * <p>The type text lives <em>inside</em> the mobile slot group: the classic
     * {@code mobile_type} ImageView (a {@code MobileTypeDrawable}) in the signal
     * container, and the HyperOS {@code mobile_type_single} TextView beside it.
     * Folding the "mobile" slot GONEs the whole group, which should be enough -
     * except MIUI's own binder drives those children directly:
     * {@code MiuiMobileIconBinder} collects a {@code mobileTypeSingleVisible}
     * flow and calls {@code MobileSignalAnimatorContainer.setChildVisible},
     * which sets the child VISIBLE and plays an appear animation. Worse, the
     * disappear path clones the child and hangs the copy off the container root
     * as a transient view, drawing it outside the GONE ancestor entirely. Either
     * way the native "4G"/"5G" reappears - and since the first pass of
     * {@code MiuiStatusIconContainer.onLayout} parks every child at the
     * container's left edge, in the status bar that means the middle of the
     * screen.
     *
     * <p>Suppression is wanted exactly while the module draws the type itself,
     * out of ring: in-ring or off, the module draws no type and the native one
     * is the user's requested reading, so it is handed back. The hand-back only
     * touches views this module hid ({@link #unmarkCollapsed}), the same
     * principle {@code restoreNative} applies to the slots; the appear flow
     * re-runs on the next binder emit and re-shows the text on its own.
     *
     * <p>Callable from the layout pass: {@code setVisibility} on a view whose
     * visibility already agrees is a framework no-op, and the once-only
     * {@link #markCollapsed} guard keeps the binder's re-shows from scheduling a
     * layout per pass.
     */
    private static void suppressNativeTypeViews(ViewGroup container) {
        final TrioAppearance a = TrioConfig.appearance();
        final boolean suppress = a.glyph && a.ownsType();
        final android.content.res.Resources res = resources();
        if (res == null) {
            return;
        }
        final String pkg = "com.android.systemui";
        for (int i = 0; i < NATIVE_TYPE_IDS.length; i++) {
            final Integer id = nativeTypeId(res, pkg, NATIVE_TYPE_IDS[i]);
            if (id == null) {
                continue;
            }
            View target;
            try {
                target = container.findViewById(id.intValue());
            } catch (Throwable t) {
                continue;
            }
            if (target == null) {
                continue;
            }
            if (suppress) {
                if (target.getVisibility() == View.VISIBLE && markCollapsed(target)) {
                    try {
                        target.setVisibility(View.GONE);
                    } catch (Throwable ignored) {
                        unmarkCollapsed(target);
                    }
                }
            } else if (unmarkCollapsed(target)) {
                try {
                    if (target.getVisibility() != View.VISIBLE) {
                        target.setVisibility(View.VISIBLE);
                    }
                } catch (Throwable ignored) {
                    // never let one child abort the pass
                }
            }
        }
    }

    /** Cached {@code getIdentifier} for one of the native type-view names. */
    private static Integer nativeTypeId(android.content.res.Resources res,
                                        String pkg, String name) {
        synchronized (NATIVE_TYPE_VIEW_IDS) {
            final Integer cached = NATIVE_TYPE_VIEW_IDS.get(name);
            if (cached != null) {
                return cached.intValue() != 0 ? cached : null;
            }
            final int id;
            try {
                id = res.getIdentifier(name, "id", pkg);
            } catch (Throwable t) {
                NATIVE_TYPE_VIEW_IDS.put(name, Integer.valueOf(0));
                return null;
            }
            NATIVE_TYPE_VIEW_IDS.put(name, Integer.valueOf(id));
            return id != 0 ? Integer.valueOf(id) : null;
        }
    }

    /**
     * Records {@code child} as already hidden. Returns false when it was recorded
     * before, so the caller skips a repeat {@code setVisibility} - the redundant
     * call is a no-op for the framework but would re-enter layout if MIUI keeps
     * restoring the child.
     */
    private static boolean markCollapsed(View child) {
        synchronized (COLLAPSED) {
            return COLLAPSED.put(child, Boolean.TRUE) == null;
        }
    }

    /**
     * Forgets that {@code child} was hidden by us, returning true only for the
     * call that did so - the same once-only guard as {@link #markCollapsed}, for
     * the opposite direction. Without it, re-showing a child on every pass would
     * schedule a new layout each time MIUI hid it again.
     */
    private static boolean unmarkCollapsed(View child) {
        synchronized (COLLAPSED) {
            return COLLAPSED.remove(child) != null;
        }
    }

    /**
     * Samples which signal indicators the status bar is currently showing, from
     * the live view tree.
     *
     * <p>This is the only reliable source for the Wi-Fi indicator going away.
     * MIUI stops calling {@code MiuiStatusBarIconViewHelper.transformResId} as
     * soon as an indicator is not visible, so the level learned there goes stale:
     * switching Wi-Fi off left the last level behind and the arcs stayed on
     * screen forever.
     *
     * <p>Note what "present" means here. When Wi-Fi is switched off MIUI does
     * <em>not</em> remove the {@code slot=wifi} child 鈥?it keeps the view and
     * merely stops binding it, so the child count stays at one. The live answer
     * is {@code ModernStatusBarView.isIconVisible()}, which reads the binding.
     * Counting children instead would never see the indicator leave.
     *
     * <p>Only the Wi-Fi indicator is sampled. {@code isIconVisible()} stays true
     * on a mobile view that has already been faded out, so it cannot tell mobile
     * off from mobile on; the level dots keep being driven by the last signal
     * resource seen instead.
     *
     * <p>Only the authoritative status-bar container is sampled. The control
     * centre, the QS headers and the keyguard each inflate their own container
     * from the same layout, and those must never drive the shared state.
     */
    private static void sampleSignalPresence(ViewGroup container) {
        if (container == null || !isStatusBarContainer(container)) {
            return;
        }
        boolean wifiVisible = false;
        int count;
        try {
            count = container.getChildCount();
            for (int i = 0; i < count; i++) {
                final View child = container.getChildAt(i);
                if (child == null || !"wifi".equals(slotOf(child))) {
                    continue;
                }
                // Binding visibility is only a fallback when framework
                // connectivity is unavailable. Never treat historical signal
                // levels as proof of a current connection (issues #13/#14).
                final Object visible = Refl.callByName(child, "isIconVisible");
                if (!(visible instanceof Boolean) || ((Boolean) visible).booleanValue()) {
                    wifiVisible = true;
                    break;
                }
            }
        } catch (Throwable ignored) {
            // keep the previous state
        }
        // Live framework connectivity wins over a stale sampled icon level.
        wifiVisible = WifiPresence.resolve(wifiVisible, TrioState.sWifiLevel);
        if (TrioState.setWifiPresent(wifiVisible)) {
            invalidateHosts();
        }
    }

    /**
     * True for the status bar's own icon container, as opposed to the ones the
     * control centre, its fake header and the QS headers inflate from the same
     * {@code system_icons.xml}.
     *
     * <p>Matched either against the container captured from
     * {@code MiuiPhoneStatusBarView.mStatusBarStatusIcons}, or - in case that
     * capture has not run yet - by looking for the {@code MiuiPhoneStatusBarView}
     * ancestor that only the status bar (and the keyguard sharing it) has.
     */
    private static boolean isStatusBarContainer(View container) {
        if (container == sStatusIconContainer) {
            return true;
        }
        for (ViewParent p = container.getParent(); p != null;
             p = (p instanceof View) ? ((View) p).getParent() : null) {
            if (p.getClass().getSimpleName().equals("MiuiPhoneStatusBarView")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Makes sure the container's ignored slots match the sub-toggles, as seen
     * from the layout pass.
     *
     * <p>Returns without doing anything once they agree, which is what keeps this
     * from looping: {@code addIgnoredSlots} ends in an unconditional
     * {@code requestLayout()}, and re-appending on every pass would schedule a
     * new layout forever. When the slot list cannot be read there is nothing to
     * compare against, and assuming "already in sync" is the only choice that
     * cannot loop. Callers that must apply the state regardless - a fresh
     * container, or a settings change - use {@link #syncSlots}, which appends
     * even when it cannot verify.
     */
    private static void ensureFolded(Object container) {
        final Field f = sIgnoredSlotsField;
        if (f == null || container == null) {
            return;
        }
        final Object value = Refl.get(f, container);
        if (!(value instanceof List)) {
            return;
        }
        final List<?> slots = (List<?>) value;
        final List<String> wanted = foldedSlots();
        for (int i = 0; i < wanted.size(); i++) {
            if (!slots.contains(wanted.get(i))) {
                syncSlots(container);
                return;
            }
        }
        for (int i = 0; i < MANAGED_SLOTS.size(); i++) {
            final String slot = MANAGED_SLOTS.get(i);
            if (!wanted.contains(slot) && slots.contains(slot)) {
                syncSlots(container);
                return;
            }
        }
    }

    /**
     * Reads {@code ModernStatusBarView.getSlot()} (or the displayable one).
     */
    private static String slotOf(View child) {
        final Object slot = Refl.callByName(child, "getSlot");
        return (slot instanceof String) ? (String) slot : null;
    }

    /**
     * True when {@code container} belongs to a {@code MiuiStatusBatteryContainer}
     * that also holds a battery host currently drawing the trio glyph.
     *
     * <p>The icon container is a <em>sibling</em> of the battery view inside
     * {@code MiuiStatusBatteryContainer} (see {@code system_icons.xml}), so
     * walking up from the host never reaches it; the two are bound by the
     * battery container's own {@code mStatusIcon} field plus the registered
     * drawing hosts instead.
     *
     * <p>Every look-up walks the live view tree, so a container that exists
     * before any host has drawn simply stays native until the next layout pass:
     * {@code onDraw} and {@code onLayout} legitimately race on the first frame.
     */
    private static boolean isOwned(Object container) {
        synchronized (OWNED) {
            if (OWNED.containsKey(container)) {
                return true;
            }
        }
        if (!(container instanceof View)) {
            return false;
        }
        final View owner = batteryContainerOf((View) container);
        if (owner == null) {
            return false;
        }
        // Guard against a mis-walk: the container must really be this parent's.
        final Object declared = Refl.get(sStatusIconField, owner);
        if (declared != null && declared != container) {
            return false;
        }
        if (!holdsLiveHost(owner)) {
            // The lock screen's row is a second instance of the same layout -
            // keyguard_status_bar.xml includes system_icons - and it has no live
            // host, because the host match is scoped to the status bar's own row.
            // Its icons still have to be folded: the trio is painted into that
            // meter by the draw path, and without this the native signal icons
            // sit right beside it.
            if (!isKeyguardRow((View) container)) {
                return false;
            }
        }
        own(container);
        return true;
    }

    /** True when some registered glyph host lives under {@code container}. */
    private static boolean holdsLiveHost(View container) {
        synchronized (HOSTS) {
            for (int i = 0; i < HOSTS.size(); i++) {
                if (isDescendant(HOSTS.get(i).host, container)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isDescendant(View child, View ancestor) {
        for (ViewParent p = child.getParent(); p != null;
             p = (p instanceof View) ? ((View) p).getParent() : null) {
            if (p == ancestor) {
                return true;
            }
        }
        return false;
    }

    /**
     * The {@code MiuiStatusBatteryContainer} enclosing {@code v}, or null.
     *
     * <p>Matched by class name rather than by {@code Class.forName} on the host's
     * class loader: the host is inflated through a Compose/Factory wrapper whose
     * loader chain does not expose SystemUI classes, which made every earlier
     * look-up fail ({@code owned=false} for all four containers on device).
     * The concrete class of the parent view is always available directly.
     */
    private static View batteryContainerOf(View v) {
        Class<?> known = sBatteryContainerClass;
        for (ViewParent p = v.getParent(); p != null;
             p = (p instanceof View) ? ((View) p).getParent() : null) {
            if (!(p instanceof View)) {
                return null;
            }
            final View parent = (View) p;
            final Class<?> cls = parent.getClass();
            final boolean match = (known != null)
                    ? known.isInstance(parent)
                    : BATTERY_CONTAINER_CLASS.equals(cls.getName());
            if (match) {
                if (known == null) {
                    sBatteryContainerClass = cls;
                    sStatusIconField = Refl.field(cls, "mStatusIcon");
                }
                return parent;
            }
        }
        return null;
    }

    private static void own(Object container) {
        synchronized (OWNED) {
            OWNED.put(container, Boolean.TRUE);
        }
    }

    /**
     * Logs a container's geometry and children once, so the on-device report
     * pins down which container a stray icon belongs to.
     */
    private static void diagnose(ViewGroup container, boolean owned) {
        if (!debugLog()) {
            return;
        }
        final int[] location = new int[2];
        try {
            container.getLocationOnScreen(location);
        } catch (Throwable ignored) {
            // keep the relative values
        }
        final String children = childrenOf(container);
        final boolean log;
        synchronized (DIAGNOSED) {
            final boolean first = !DIAGNOSED.containsKey(container);
            // Record every container seen, not just the first dozen: the map is
            // weak, so this bounds nothing but the log. Without the record a
            // container past the log cap would look "first" on every pass.
            if (first) {
                DIAGNOSED.put(container, Boolean.TRUE);
            }
            final Boolean previous = DIAG_OWNED.get(container);
            // Log the first sighting and any later flip of `owned`. The first
            // layout normally runs before any glyph host has drawn, so that
            // first header legitimately reads owned=false; a one-shot header
            // would keep reporting that stale value for the whole session.
            final boolean changed = (previous != null) && previous.booleanValue() != owned;
            log = (first || changed) && sDiagHeaders < 48;
            if (log) {
                DIAG_OWNED.put(container, Boolean.valueOf(owned));
                sDiagHeaders++;
            }
        }
        if (log) {
            // The first layout usually runs before MIUI populates the container,
            // so the header alone is not enough: the children are dumped below by
            // signature as soon as they exist.
            final XposedModule module = sModule;
            if (module != null) {
                log(module, "container " + container.getClass().getSimpleName()
                        + " owned=" + owned
                        + " screen=" + location[0] + "," + location[1]
                        + " size=" + container.getWidth() + "x" + container.getHeight()
                        + " measured=" + container.getMeasuredWidth()
                        + " left=" + container.getLeft()
                        + " padStart=" + container.getPaddingStart()
                        + " padEnd=" + container.getPaddingEnd()
                        + " parent=" + (container.getParent() == null ? "null"
                        : container.getParent().getClass().getSimpleName())
                        + " path=" + pathOf(container));
            }
        }
        // Children are dumped until one dump shows the container populated; the
        // signature check keeps a repeated layout from logging the same content.
        if (children.length() == 0 || sDiagDumps >= 24) {
            return;
        }
        synchronized (DIAG_SIG) {
            if (children.equals(DIAG_SIG.get(container))) {
                return;
            }
            DIAG_SIG.put(container, children);
            sDiagDumps++;
        }
        final XposedModule module = sModule;
        if (module != null) {
            log(module, "children of " + container.getClass().getSimpleName()
                    + " at " + location[0] + "," + location[1] + ":" + children);
        }
    }

    /** One compact token per child, used both for logging and for change detection. */
    private static String childrenOf(ViewGroup container) {
        final StringBuilder sb = new StringBuilder();
        final int count;
        try {
            count = container.getChildCount();
        } catch (Throwable t) {
            return sb.toString();
        }
        for (int i = 0; i < count; i++) {
            final View child;
            try {
                child = container.getChildAt(i);
            } catch (Throwable t) {
                continue;
            }
            if (child == null) {
                continue;
            }
            sb.append(" | ").append(i)
                    .append(' ').append(child.getClass().getSimpleName())
                    .append(" slot=").append(slotOf(child))
                    .append(" l=").append(child.getLeft())
                    .append(" r=").append(child.getRight())
                    .append(" w=").append(child.getWidth())
                    .append(" mw=").append(child.getMeasuredWidth())
                    .append(" tx=").append(child.getTranslationX())
                    .append(" a=").append(child.getAlpha())
                    .append(" v=").append(child.getVisibility());
        }
        return sb.toString();
    }

    private static String pathOf(View v) {
        final StringBuilder sb = new StringBuilder();
        for (ViewParent p = v.getParent(); p != null;
             p = (p instanceof View) ? ((View) p).getParent() : null) {
            sb.append(p.getClass().getSimpleName()).append('<');
        }
        return sb.toString();
    }


    /** The capsule's stroke width for the current settings, in design units. */
    private static float strokeForCapsule() {
        return TrioConfig.appearance().ringStroke * TrioGeometry.CAP_STROKE_RATIO;
    }

    private static TrioState registerHost(View host) {
        synchronized (HOSTS) {
            for (int i = 0; i < HOSTS.size(); i++) {
                if (HOSTS.get(i).host == host) {
                    return HOSTS.get(i);
                }
            }
            final TrioState s = new TrioState(host);
            HOSTS.add(s);
            // Each host hides the native signal icons of the container it lives in
            // (status bar, keyguard, control center, QS headers each have their own).
            // registerHost runs from onDraw, and addIgnoredSlots ends in
            // requestLayout(), so defer it out of the draw pass.
            host.post(new Runnable() {
                @Override
                public void run() {
                    foldHostContainer(host);
                    // The reading is mounted and measured before the label, since
                    // the label stands outside it whenever it is present; the
                    // performance readout sits between the two for the same
                    // reason.
                    syncOutSignal(host);
                    syncPerfLabel(host);
                    syncOutTypeLabel(host);
                }
            });
            return s;
        }
    }

    private static TrioState stateFor(View host) {
        synchronized (HOSTS) {
            for (int i = 0; i < HOSTS.size(); i++) {
                final TrioState s = HOSTS.get(i);
                if (s.host == host) {
                    return s;
                }
            }
        }
        return null;
    }

    /** The nearest {@code MiuiStatusIconContainer} ancestor of {@code host}, or null. */
    private static Object iconContainerOf(View host) {
        final Class<?> cls = sIconContainerClass;
        if (cls == null) {
            return null;
        }
        for (ViewParent p = host.getParent(); p != null;
             p = (p instanceof View) ? ((View) p).getParent() : null) {
            if (cls.isInstance(p)) {
                return p;
            }
        }
        return null;
    }

    /**
     * Asks every known host to draw again.
     *
     * <p>Not the full {@link #invalidateHosts()}: that one also asks the bar to
     * lay out again, which is right after a real change and far too much twice
     * a second. This only repaints the hosts, and it is what turns "the bar is
     * drawing" into a question that can be asked at all.
     */
    static void nudgeHosts() {
        synchronized (HOSTS) {
            for (int i = 0; i < HOSTS.size(); i++) {
                HOSTS.get(i).host.invalidate();
            }
        }
    }

    private static void unregisterHost(View host) {
        synchronized (HOSTS) {
            for (int i = HOSTS.size() - 1; i >= 0; i--) {
                if (HOSTS.get(i).host == host) {
                    HOSTS.remove(i);
                }
            }
        }
    }

    /** Repaints every trio host. Signal updates arrive off the UI thread. */
    static void invalidateHosts() {
        // The bar itself is asked to lay out again as well: the host can have
        // left the tree since the last pass, and MIUI re-derives the width it
        // gives the row, so the readings have to be measured against the new
        // one rather than the one they were placed in.
        final View bar = sStatusBarView;
        if (bar != null) {
            bar.post(new Runnable() {
                @Override
                public void run() {
                    bar.requestLayout();
                    bar.invalidate();
                }
            });
        }
        final List<TrioState> copy;
        synchronized (HOSTS) {
            if (HOSTS.isEmpty()) {
                return;
            }
            copy = new ArrayList<TrioState>(HOSTS);
        }
        for (int i = 0; i < copy.size(); i++) {
            final View v = copy.get(i).host;
            v.post(new Runnable() {
                @Override
                public void run() {
                    // The type string is sampled from a drawable's measure(), so
                    // this is the only path that can carry a changed "5G"/"5GA"
                    // through to a label the glyph canvas does not own. Readings
                    // arrive here too: a SIM returning or the data SIM changing
                    // moves the bars without any draw pass of the glyph.
                    syncOutSignal(v);
                    // Between the two on purpose: the label chains through the
                    // performance readout, which chains through the reading, so
                    // each is measured before the one that stands outside it.
                    syncPerfLabel(v);
                    syncOutTypeLabel(v);
                    v.invalidate();
                }
            });
        }
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Makes {@code container}'s ignored-slot list match the sub-toggles: the
     * slots the glyph draws are added, the ones it no longer draws are removed.
     *
     * <p>Both directions matter. Adding folds a native icon into the glyph;
     * <em>removing</em> is what hands it back, because MIUI lays out - and so
     * positions - every slot that is not ignored. Only slots this class manages
     * are ever removed: {@code ignoredSlots} also carries MIUI's own entries
     * (the control-centre {@code RIGHT_BLOCK_LIST}), and dropping one of those
     * would move icons the user never asked about.
     *
     * <p>The layout request is deliberately conditional. {@code addIgnoredSlots}
     * ends in an unconditional {@code requestLayout()}, so appending on every
     * pass would schedule a new layout forever - which is why this is idempotent
     * while {@code ignoredSlots} is readable, and why it does not add a second
     * request of its own when it appended. When the field cannot be read there is
     * nothing to compare against, so the append happens blind: a MIUI rename must
     * not silently stop the module from folding. Removal is impossible then, and
     * the sub-toggle degrades to the old always-folded behaviour.
     */
    private static void syncSlots(Object container) {
        if (container == null) {
            return;
        }
        final List<String> wanted = foldedSlots();
        final Field f = sIgnoredSlotsField;
        final Object value = (f == null) ? null : Refl.get(f, container);
        if (!(value instanceof List)) {
            if (!wanted.isEmpty()) {
                addSlots(container, wanted);
            }
            return;
        }
        final List<?> slots = (List<?>) value;
        boolean missing = false;
        for (int i = 0; i < wanted.size(); i++) {
            if (!slots.contains(wanted.get(i))) {
                missing = true;
                break;
            }
        }
        boolean removed = false;
        try {
            for (int i = 0; i < MANAGED_SLOTS.size(); i++) {
                final String slot = MANAGED_SLOTS.get(i);
                if (!wanted.contains(slot) && slots.remove(slot)) {
                    removed = true;
                }
            }
        } catch (Throwable ignored) {
            // an immutable list is MIUI's problem, not ours
        }
        if (missing) {
            addSlots(container, wanted);
        } else if (removed) {
            // Nothing else will ask for a pass, and MIUI has to re-measure and
            // re-lay the slot before the handed-back icon reappears in place.
            requestLayout(container);
        }
    }

    /** Appends {@code slots} to the container's ignored list. */
    private static void addSlots(Object container, List<String> slots) {
        Refl.callArgs(container, "addIgnoredSlots",
                new Class<?>[]{List.class},
                new Object[]{new ArrayList<String>(slots)});
    }

    private static void requestLayout(Object container) {
        Refl.callArgs(container, "requestLayout", new Class<?>[0], new Object[0]);
    }

    /**
     * Removes every managed slot again, so MIUI measures and lays them all out.
     * Used when the module is switched off wholesale.
     */
    private static void unfoldSlots(Object container) {
        if (container == null) {
            return;
        }
        final Field f = sIgnoredSlotsField;
        if (f == null) {
            return;
        }
        final Object value = Refl.get(f, container);
        if (!(value instanceof List)) {
            return;
        }
        try {
            ((List<?>) value).removeAll(MANAGED_SLOTS);
        } catch (Throwable ignored) {
            // an immutable list is MIUI's problem, not ours
        }
        requestLayout(container);
    }

    /**
     * Suppresses the native Wi-Fi / mobile icons in the
     * {@code MiuiStatusBatteryContainer} that owns {@code host}.
     *
     * <p>There are seven containers (status bar, keyguard, control center, both
     * QS headers, ...) and each one that renders the trio glyph must hide its own
     * native signal icons. Walking up from the drawing host is the only way to
     * bind the two together without a hard-coded per-host lookup.
     */
    private static void foldHostContainer(View host) {
        final Object container = batteryContainerOf(host);
        if (container == null) {
            return;
        }
        syncSlots(Refl.get(sStatusIconField, container));
    }

    // ------------------------------------------------- out-of-ring type label

    /**
     * The out-of-ring network type label.
     *
     * <p>A {@link TextView} of our own rather than MIUI's {@code mobile_type}
     * view. That one is a child of the mobile slot group, so folding the mobile
     * icon takes it away with it - and the whole point of this mode is to keep
     * showing the type while the glyph carries the signal. Being our own class
     * also gives {@link #findOutTypeLabel} a marker no MIUI view can match by
     * accident, which a tag or a {@code WeakHashMap} keyed on the container
     * would not: {@code mParent} is a strong reference from the label back to
     * the container, so such a map's values would keep their own keys alive.
     */
    private static final class OutTypeLabel extends TextView {
        /** The glyph host supplying the ink scale and the icon colour. */
        View host;
        /**
         * The suffix scale the current text was built with; -1 until first
         * applied.
         *
         * <p>Remembered because a scale change leaves the <em>characters</em>
         * identical - "5GA" is still "5GA" - so the content comparison alone
         * would skip the rebuild and the slider would appear inert.
         */
        int suffixScale = -1;
        /**
         * Weight the current typeface was built at, and the font generation it was
         * built from. Paired so that a font swap re-dresses the label even when the
         * weight is unchanged - see {@link TrioRenderer#fontGeneration()}.
         */
        int appliedWeight = -1;
        int fontGeneration = -1;

        OutTypeLabel(Context context) {
            super(context);
        }
    }

    /**
     * Mounts, updates or removes the out-of-ring label for one glyph host.
     *
     * <p>Called only from posted runnables: it may {@code addView}, which must
     * never happen inside a layout pass. The mode check comes first so that the
     * ordinary path - in-ring or off - pays for one child scan and nothing else.
     */
    private static void syncOutTypeLabel(View host) {
        if (host == null) {
            return;
        }
        final Object owner = batteryContainerOf(host);
        if (!(owner instanceof ViewGroup)) {
            return;
        }
        final ViewGroup container = (ViewGroup) owner;
        final TrioAppearance a = TrioConfig.appearance();
        final boolean wanted = a.glyph && a.typeOutOfRing;
        OutTypeLabel label = findOutTypeLabel(container);
        if (!wanted) {
            removeOutTypeLabel(container);
            return;
        }
        final View anchor = meterIn(host, container);
        if (anchor == null) {
            removeOutTypeLabel(container);
            return;
        }
        // Only a guard: inkScale() is 0 exactly when the host has not been
        // measured yet, and an unmeasured meter has no frame to place against.
        // The next posted sync mounts the label.
        if (TrioRenderer.inkScale(host.getWidth(), host.getHeight()) <= 0f) {
            removeOutTypeLabel(container);
            return;
        }
        if (label == null) {
            label = createOutTypeLabel(container);
        }
        label.host = host;
        updateOutTypeLabel(container, label, host, anchor);
    }

    /**
     * Repositions an already-mounted label. Runs inside
     * {@code MiuiStatusIconContainer.onLayout}, so it may relayout the label but
     * must never add or remove a view.
     *
     * <p>The label lives in the {@code MiuiStatusBatteryContainer}, which is the
     * icon container's <em>parent</em>, so this starts by walking up - and the
     * label was placed relative to the battery meter, whose position the same
     * traversal may only just have settled. A wrong frame here is therefore
     * normal on the first pass and heals on the next one.
     */
    private static void refreshOutTypeLabel(ViewGroup iconContainer) {
        final Object owner = batteryContainerOf(iconContainer);
        if (!(owner instanceof ViewGroup)) {
            return;
        }
        final ViewGroup container = (ViewGroup) owner;
        final OutTypeLabel label = findOutTypeLabel(container);
        if (label == null) {
            // Mounting needs a host for the ink scale and must not run in a
            // layout pass, so ask for a posted sync instead - but only once the
            // host can actually be measured, or every pass would queue another.
            requestOutTypeSync(container);
            return;
        }
        final View host = label.host;
        if (host == null || container.getWidth() <= 0 || container.getHeight() <= 0) {
            return;
        }
        final View anchor = meterIn(host, container);
        if (anchor == null) {
            return;
        }
        // Only a guard: inkScale() is 0 exactly when the host has no measured
        // size, and the label cannot be positioned against an unmeasured meter.
        // The text size no longer comes from the ink scale at all - it is the
        // user's own Out-of-ring size, already in screen pixels.
        if (TrioRenderer.inkScale(host.getWidth(), host.getHeight()) <= 0f) {
            return;
        }
        // Text and size both re-measure the view, and a re-measure schedules a
        // layout - which is exactly what must not happen inside onLayout. Hand
        // those over to the posted path and only ever reposition from here.
        final String text = typeText();
        if (text.length() == 0) {
            // The mode change and every type update already arrive through a
            // posted sync, so the label is parked at GONE before this can see an
            // empty type. Nothing to do, and nothing to lay out.
            return;
        }
        if (label.getVisibility() != View.VISIBLE
                || !text.contentEquals(label.getText())
                || label.suffixScale != TrioConfig.appearance().typeSuffixScale
                || label.getTextSize() != outTypeSizePx(container)
                // A new font moves none of the three values above, so without this
                // term the pass would find nothing to do and the label would keep
                // the previous face until the text or the size happened to change.
                || label.fontGeneration != TrioRenderer.fontGeneration()) {
            requestOutTypeSync(container);
            return;
        }
        // The strip belongs to the icon container and is taken with setPadding,
        // which schedules a layout - illegal from in here. A container rebuilt by
        // MIUI comes back with its own padding and an empty book, so hand that
        // over to the posted path too.
        final View icons = iconContainerIn(container);
        if (icons != null && !OUT_PAD_SAVED.containsKey(icons)) {
            requestOutTypeSync(container);
            return;
        }
        final TrioState state = stateFor(host);
        if (state != null) {
            final int colour = state.foreground();
            if (label.getCurrentTextColor() != colour) {
                label.setTextColor(colour);
            }
        }
        // Against the reading, not against the battery meter. The label and the
        // reading are placed by the same rule - the anchor's left edge minus the
        // gap and the width - so anchoring both at the meter stacks them on top
        // of each other, and the reading is the wider one. The posted path
        // already chains through labelAnchorIn; this pass runs on every layout
        // and would otherwise keep overwriting where that one got it right.
        placeOutTypeLabel(container, label, labelAnchorIn(container, anchor));
    }

    /**
     * Hands a new ink to the out-of-ring views under {@code host}, on the one
     * frame where the tint changed.
     *
     * <p>MIUI's dark-mode pass ends at the battery icon's own
     * {@code invalidate()}: it tints what it owns and nothing else. Both of the
     * views here are siblings of the glyph host - mounted in the battery
     * container, not drawn by it - so that pass never reaches them. The reading
     * is a plain {@code View} whose {@code onDraw} reads the colour live, so it
     * only needs the repaint; the label is a {@code TextView} that nothing
     * re-measures on a tint change, so it needs the colour handed to it
     * outright.
     *
     * <p>The comparison happens in the caller and the book is closed here,
     * before the post: a frame storm must not queue one recolour per frame, and
     * the value written is the one the frame was painted with, so the two never
     * disagree about what is on screen.
     *
     * <p>Everything else is posted. This runs inside the glyph's draw pass, and
     * the lookup walks the parent chain and reads classes - cheap, but the
     * whole point of the book above is that it runs once per tint change rather
     * than once per frame.
     */
    private static void recolourOutRing(TrioState state, int ink) {
        state.outRingInk = ink;
        final View host = state.host;
        host.post(new Runnable() {
            @Override
            public void run() {
                final Object owner = batteryContainerOf(host);
                if (owner instanceof ViewGroup) {
                    applyOutRingInk((ViewGroup) owner, ink);
                }
            }
        });
    }

    /**
     * Hands one ink to the out-of-ring views under {@code container} - the two
     * text views outright, the reading as a repaint request. The one-line body
     * both tint paths share, so they cannot drift apart.
     *
     * <p>Safe to call directly (no post) from the tint hook, which runs outside
     * any layout or draw pass; the draw-pass path above keeps its post because
     * it runs inside the glyph's onDraw.
     */
    private static void applyOutRingInk(ViewGroup container, int ink) {
        final OutTypeLabel label = findOutTypeLabel(container);
        if (label != null && label.getCurrentTextColor() != ink) {
            label.setTextColor(ink);
        }
        // The performance readout is a TextView like the label and needs the
        // same treatment: setting the colour repaints it on the next frame, so
        // unlike the reading below it wants no invalidate of its own.
        final PerfLabel perf = findPerfLabel(container);
        if (perf != null && perf.getCurrentTextColor() != ink) {
            perf.setTextColor(ink);
        }
        final OutSignalView signal = findOutSignal(container);
        if (signal != null) {
            // It would redraw with the new colour on its own, but only if
            // something asked it to - and nothing did.
            signal.invalidate();
        }
    }

    /**
     * Queues one {@link #syncOutTypeLabel} for the host under {@code container},
     * without ever queuing an empty one: the runnable is a no-op unless the mode
     * is Out of ring, a host is registered and that host has been measured.
     */
    private static void requestOutTypeSync(final ViewGroup container) {
        if (!TrioConfig.appearance().typeOutOfRing) {
            return;
        }
        final View host = hostIn(container);
        if (host == null
                || TrioRenderer.inkScale(host.getWidth(), host.getHeight()) <= 0f) {
            return;
        }
        final View target = host;
        container.post(new Runnable() {
            @Override
            public void run() {
                syncOutTypeLabel(target);
            }
        });
    }

    /** The type string to show, normalised to empty when there is none. */
    private static String typeText() {
        final String text = TrioState.sMobileType;
        return text == null ? "" : text;
    }

    /** The label already mounted on {@code container}, or null. */
    private static OutTypeLabel findOutTypeLabel(ViewGroup container) {
        final int count = container.getChildCount();
        for (int i = 0; i < count; i++) {
            final View child;
            try {
                child = container.getChildAt(i);
            } catch (Throwable t) {
                continue;
            }
            if (child instanceof OutTypeLabel) {
                return (OutTypeLabel) child;
            }
        }
        return null;
    }

    private static OutTypeLabel createOutTypeLabel(ViewGroup container) {
        final OutTypeLabel label = new OutTypeLabel(container.getContext());
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setIncludeFontPadding(false);
        label.setLetterSpacing(TrioGeometry.VALUE_LETTER_SPACING);
        label.setVisibility(View.GONE);
        // WRAP_CONTENT on both axes: the size is set explicitly further down by
        // measuring against an unlimited spec. The container never measures this
        // child on its own - MiuiStatusBatteryContainer.onMeasure only touches
        // its three named fields - so whatever we measure stands.
        container.addView(label, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return label;
    }

    private static void removeOutTypeLabel(View container) {
        if (!(container instanceof ViewGroup)) {
            return;
        }
        final ViewGroup group = (ViewGroup) container;
        final OutTypeLabel label = findOutTypeLabel(group);
        if (label != null) {
            try {
                group.removeView(label);
            } catch (Throwable ignored) {
                // a container torn down under us is not worth crashing over
            }
        }
        // Recompute rather than release first. The strip is shared with the
        // performance readout, and this method runs on every sync - including
        // the common case where there is no label at all because the out-of-ring
        // type is switched off. Releasing here and returning early handed the
        // padding back and left the readout's strip unrestored: the icon row was
        // dropped and re-padded once per power update, about once a second while
        // charging, which slid every native icon on that side of the bar.
        // reserveOutRingStrip is authoritative - it totals whatever is actually
        // on screen and hands the strip back itself when that total is zero - so
        // there is nothing for a pre-emptive release to fix and everything for it
        // to break.
        reserveOutRingStrip(group);
    }

    private static void updateOutTypeLabel(ViewGroup container, OutTypeLabel label,
                                           View host, View anchor) {
        final TrioAppearance a = TrioConfig.appearance();
        final TrioState state = stateFor(host);
        if (state != null) {
            // Pull the status values in before reading them: this runs from a
            // posted runnable, but the state snapshot is only as fresh as the
            // last call to refresh().
            state.refresh();
        }
        final String text = typeText();
        if (text.length() == 0) {
            // Hidden rather than removed: the text comes and goes as the modem
            // reports a type, and add/remove on every layout pass would churn
            // the container for nothing. The reserved strip goes back, though -
            // holding it open with nothing in it would just leave a hole in the
            // status bar.
            if (label.getTranslationX() != 0f) {
                label.setTranslationX(0f);
            }
            if (label.getVisibility() != View.GONE) {
                label.setVisibility(View.GONE);
            }
            // The reading may still be mounted and still need its strip; the
            // bookkeeping above cannot tell, so it is asked.
            reserveOutRingStrip(container);
            return;
        }
        if (!text.contentEquals(label.getText())
                || label.suffixScale != a.typeSuffixScale) {
            applyOutTypeText(label, text, a.typeSuffixScale);
        }
        // Its own setting, not the in-ring type_size: that one is authored for
        // the ring canvas' 120x120 design space and comes out far too small
        // once the label stands in the status bar's real pixel space.
        final float size = outTypeSizePx(container);
        if (label.getTextSize() != size) {
            // PX, not the SP that the one-argument overload would use: the
            // status bar lays out in raw pixels, so the value is applied as-is
            // rather than scaled by the user's font-size setting.
            label.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
        }
        // The generation is part of the test so a font swap reaches this label even
        // when the weight it wears did not move.
        final int generation = TrioRenderer.fontGeneration();
        if (label.fontGeneration != generation
                || label.appliedWeight != a.typeWeight) {
            TrioRenderer.styleTextView(label, a.typeWeight);
            label.fontGeneration = generation;
            label.appliedWeight = a.typeWeight;
        }
        final int colour = (state != null) ? state.foreground() : 0xFFFFFFFF;
        if (label.getCurrentTextColor() != colour) {
            label.setTextColor(colour);
        }
        if (label.getVisibility() != View.VISIBLE) {
            label.setVisibility(View.VISIBLE);
        }
        label.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        // Measured first, because the strip the native row has to give up is the
        // label's own width plus the gap on either side of it. Without it the row
        // lays its icons all the way to the end and paints over the label.
        // reserveOutRingStrip counts the reading too, when one is mounted.
        reserveOutRingStrip(container);
        placeOutTypeLabel(container, label, labelAnchorIn(container, anchor));
        if (debugLog()) {
            log(LOG_INFO, "out type: \"" + text + "\" size=" + size
                    + " label=" + label.getWidth() + "x" + label.getHeight()
                    + " at " + label.getLeft() + "," + label.getTop()
                    + " anchor=" + anchor.getLeft() + ".." + anchor.getRight()
                    + " container=" + container.getWidth() + "x" + container.getHeight());
        }
    }

    /**
     * Sets the label's text, shrinking a trailing "A" when the user asked for
     * it.
     *
     * <p>A {@code SpannableStringBuilder} with a {@link RelativeSizeSpan} rather
     * than two draws: this side is a real {@code TextView}, so the platform lays
     * the runs out and keeps them on one baseline for free. That is exactly what
     * {@code Canvas.drawText} cannot do - it ignores spans - which is why the
     * ring canvas takes the two-run path in {@code TrioRenderer.drawType} and
     * this one takes the span.
     *
     * <p>The span covers only the last character and only when there is a label
     * left after it: a suffix span over a bare "A" would just draw the label
     * smaller, which is not the reference's shape. {@code
     * setLetterSpacing} is left as it is - the tracking applies to the whole
     * label exactly as it did when the type was one size.
     */
    private static void applyOutTypeText(OutTypeLabel label, String text, int scalePercent) {
        if (!TrioGeometry.hasShrunkSuffix(text, scalePercent)) {
            // Plain text, not a spannable: an install that never shrinks the
            // suffix should not pay for span bookkeeping on every type change.
            label.setText(text);
        } else {
            final SpannableStringBuilder span = new SpannableStringBuilder(text);
            final int at = text.length() - 1;
            span.setSpan(new RelativeSizeSpan(scalePercent / 100f), at, text.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            label.setText(span);
        }
        label.suffixScale = scalePercent;
    }

    // ------------------------------------------------- performance readout

    /**
     * The break between the two readings in the one view.
     *
     * <p>A newline and not a space. The power and the temperature are separate
     * quantities with separate switches, and stacking them keeps a pair of
     * readings from crowding the native icons whose strip they were carved out
     * of: the view grows taller and stays narrow instead of growing sideways.
     * One reading is one line, the pair is two, and the view is the same
     * either way.
     */
    private static final char PERF_BREAK = '\n';

    /**
     * The floor on the line-height multiplier used to fit two readings into
     * the status bar.
     *
     * <p>The digits, the degree sign and the C are all cap-height with no
     * descenders, so most of each line box is empty and can be given back;
     * below this the two lines would touch instead. A pair that slightly
     * overflows a pathologically short row is better than two that overlap.
     */
    private static final float PERF_MIN_LINE_SPACING = 0.72f;

    /**
     * The floor on how far the stacked pair may be shrunk to fit one icon.
     *
     * <p>The fit is derived from the row, so a row that is short for reasons
     * of its own - an unusual density, a host measured before it settled -
     * would otherwise be able to shrink the pair past legibility. Below this
     * the pair is allowed to be taller than the icon instead.
     */
    private static final float PERF_MIN_SIZE_RATIO = 0.6f;

    /**
     * Scratch face for measuring what one line of the readout costs at a given
     * size, so the size can be decided before any view is given it.
     *
     * <p>One shared instance and main-thread only: every caller is a posted
     * runnable or a layout callback, and a {@code Paint} is not something to
     * hand out per frame.
     */
    private static final Paint PERF_PROBE = new Paint();

    /**
     * The out-of-ring performance readout: charging power and battery
     * temperature, in one view.
     *
     * <p>One view rather than two because the two readings are the same kind of
     * thing standing in the same place. They share the strip the native icon row
     * gives up, and with a view each they would have to be chained and reserved
     * against one another to keep from being drawn through each other. With one
     * view there is one width, one gap and one anchor, and the two switches only
     * decide which segments the text is built from.
     *
     * <p>Being our own class gives {@link #findPerfLabel} a marker no MIUI view
     * can match by accident, for the same reason {@link OutTypeLabel} is its own
     * class rather than a tag on somebody else's.
     */
    private static final class PerfLabel extends TextView {
        /** The glyph host the ink colour comes from. */
        View host;
        /**
         * The weight the current typeface was built from, or -1 before the first
         * pass.
         *
         * <p>Remembered as the number rather than compared as a {@code Typeface},
         * because {@code Typeface.create} is only documented to answer an equal
         * face and two weights that map to the same style would compare equal
         * while still needing no work. It is the fifth thing the re-sync test
         * asks about: the weight reaches the canvas through the typeface and
         * through the line height, and on a row where the size is pinned at
         * {@link #PERF_MIN_SIZE_RATIO} the size term cannot see a weight change
         * at all - without this the slider would do nothing there.
         */
        int weight = -1;

        /**
         * Font generation the current face was built from, or -1 before the first
         * pass. A font swap leaves the weight alone, so the weight test above
         * cannot see it; this is what makes the picked file appear.
         */
        int fontGeneration = -1;

        PerfLabel(Context context) {
            super(context);
        }
    }

    /**
     * Mounts, updates or removes the performance readout for one glyph host.
     *
     * <p>Posted only, for the same reason as {@link #syncOutTypeLabel}: it may
     * {@code addView}, which must never happen inside a layout pass. The switch
     * check comes first so the ordinary path - both readouts off - pays for one
     * child scan and nothing else.
     */
    private static void syncPerfLabel(View host) {
        if (host == null) {
            return;
        }
        final Object owner = batteryContainerOf(host);
        if (!(owner instanceof ViewGroup)) {
            return;
        }
        final ViewGroup container = (ViewGroup) owner;
        final TrioAppearance a = TrioConfig.appearance();
        final boolean wanted = a.glyph && a.perfAnywhere();
        PerfLabel perf = findPerfLabel(container);
        if (!wanted) {
            removePerfLabel(container);
            return;
        }
        final View anchor = meterIn(host, container);
        if (anchor == null) {
            removePerfLabel(container);
            return;
        }
        // Only a guard: inkScale() is 0 exactly when the host has not been
        // measured yet, and an unmeasured meter has no frame to place against.
        // The next posted sync mounts the readout.
        if (TrioRenderer.inkScale(host.getWidth(), host.getHeight()) <= 0f) {
            removePerfLabel(container);
            return;
        }
        if (perf == null) {
            perf = createPerfLabel(container);
        }
        perf.host = host;
        updatePerfLabel(container, perf, host, anchor);
    }

    /**
     * Repositions an already-mounted readout. Runs inside
     * {@code MiuiStatusIconContainer.onLayout}, so it may relayout the view but
     * must never add or remove one.
     *
     * <p>Mirrors {@link #refreshOutTypeLabel}, including the reason each
     * condition is handed back to the posted path instead of being done here:
     * setting text, changing the text size, adjusting the letter spacing and
     * taking the strip with {@code setPadding} all schedule a layout, and none of
     * those may run from inside one.
     */
    private static void refreshPerfLabel(ViewGroup iconContainer) {
        final Object owner = batteryContainerOf(iconContainer);
        if (!(owner instanceof ViewGroup)) {
            return;
        }
        final ViewGroup container = (ViewGroup) owner;
        final PerfLabel perf = findPerfLabel(container);
        if (perf == null) {
            requestPerfSync(container);
            return;
        }
        final View host = perf.host;
        if (host == null || container.getWidth() <= 0 || container.getHeight() <= 0) {
            return;
        }
        final View anchor = meterIn(host, container);
        if (anchor == null) {
            return;
        }
        if (TrioRenderer.inkScale(host.getWidth(), host.getHeight()) <= 0f) {
            return;
        }
        final String text = perfText(TrioConfig.appearance());
        if (text.length() == 0) {
            // Every switch change and every reading change arrives through a
            // posted sync, so the view is parked at GONE before this can see an
            // empty string. Nothing to do, and nothing to lay out.
            return;
        }
        // The line spacing belongs in this test as much as the text and the
        // size do. perfLineSpacing reads the container's height, and that row
        // is not a fixed height - MIUI lays it out at 88px with the shade
        // closed and 134px with it open (dumpsys window displays) - while the
        // host, and with it perfTextSize, is unchanged. On such a pass the text
        // and the size both compare equal, so without this term nothing would be
        // posted and the leading computed for the other row height would stay.
        // refreshOutTypeLabel compares its own scale term for the same reason.
        // Reading it here is safe from inside a layout pass: perfLineSpacing
        // only reads the view's paint metrics and the container's height and
        // padding.
        //
        // The weight is the fourth term for a sharper version of the same
        // reason. It reaches the screen through the typeface and through the
        // line height, and on the rows where the size is pinned at
        // PERF_MIN_SIZE_RATIO - which is where this device sits by default - a
        // weight change leaves the size identical, so neither the text nor the
        // size term would notice it and the slider would appear dead. The
        // stored number is also what updatePerfLabel gates its setTypeface on,
        // so the two must be asking about the same value.
        // The font generation is a fifth term for the same reason the weight is the
        // fourth: picking a new font changes the face without touching any of the
        // values above, so without this the gate would find nothing to do and the
        // new font would not reach the row until something unrelated moved.
        final int weight = TrioConfig.appearance().perfTypeWeight();
        if (perf.getVisibility() != View.VISIBLE
                || !text.contentEquals(perf.getText())
                || perf.getTextSize() != perfTextSize(text, container, host)
                || perf.getLineSpacingMultiplier()
                        != perfLineSpacing(perf, text, container)
                || perf.weight != weight
                || perf.fontGeneration != TrioRenderer.fontGeneration()) {
            requestPerfSync(container);
            return;
        }
        // The strip belongs to the icon container and is taken with setPadding,
        // which schedules a layout - illegal from in here. A container rebuilt by
        // MIUI comes back with its own padding and an empty book, so hand that
        // over to the posted path too.
        final View icons = iconContainerIn(container);
        if (icons != null && !OUT_PAD_SAVED.containsKey(icons)) {
            requestPerfSync(container);
            return;
        }
        final TrioState state = stateFor(host);
        if (state != null) {
            final int colour = state.foreground();
            if (perf.getCurrentTextColor() != colour) {
                perf.setTextColor(colour);
            }
        }
        // Against the reading rather than against the meter, and in particular
        // not through labelAnchorIn: that one answers "what is outermost" and
        // would hand this view its own frame as its anchor.
        placeOutTypeLabel(container, perf, readingAnchorIn(container, anchor));
    }

    /**
     * Queues one {@link #syncPerfLabel} for the host under {@code container},
     * without ever queuing an empty one: the runnable is a no-op unless a
     * readout is switched on, a host is registered and that host has been
     * measured.
     */
    private static void requestPerfSync(final ViewGroup container) {
        if (!TrioConfig.appearance().perfAnywhere()) {
            return;
        }
        final View host = hostIn(container);
        if (host == null
                || TrioRenderer.inkScale(host.getWidth(), host.getHeight()) <= 0f) {
            return;
        }
        final View target = host;
        container.post(new Runnable() {
            @Override
            public void run() {
                syncPerfLabel(target);
            }
        });
    }

    /**
     * The performance string to show, normalised to empty when no segment
     * applies.
     *
     * <p>Built from segments rather than from one format because the power is a
     * property of the charger and the temperature is a property of the battery.
     * Power is dropped the moment no source is attached - an unplugged phone has
     * no charging power, and "0.0W" standing next to a temperature would read as
     * a broken reading rather than as an idle one - while the temperature stays
     * whatever the charger is doing. That asymmetry is why the two are separate
     * switches over one view.
     */
    private static String perfText(TrioAppearance a) {
        if (!a.perfAnywhere()) {
            return "";
        }
        final StringBuilder text = new StringBuilder();
        // Both halves of the question, because they say different things: a
        // source being attached is not the same as the platform being able to
        // say how much power is going in. Attached with no reading - the shape
        // this had before the sentinel existed - renders a confident "0.0W"
        // that is not a reading at all.
        //
        // isNaN and not != POWER_UNKNOWN: the sentinel is a NaN, so != answers
        // true for it and the gate would let the very value it exists to
        // exclude through. Math.round then turns that NaN into 0 and the row
        // reads "0.0W" - the exact claim this test is here to prevent.
        if (a.perfPower && TrioState.sChargingAttached
                && !Float.isNaN(TrioState.sChargePowerW)) {
            text.append(powerText(TrioState.sChargePowerW));
        }
        // The charging-only switch is a filter on the temperature, not a second reading:
        // with it on, both halves of the strip appear and disappear together, which is the
        // block a user watching a charge asked for (issue 45). Off - the default and the
        // behaviour this shipped with - the temperature stands on its own, because it is a
        // property of the battery rather than of the charger.
        final boolean tempWanted = a.perfTemp
                && TrioState.sBatteryTempC != TrioState.TEMP_UNKNOWN
                && (!a.perfTempChargingOnly || TrioState.sChargingAttached);
        if (tempWanted) {
            if (text.length() > 0) {
                // One reading per line: the pair stacks instead of running on,
                // so the view never grows a second reading wide. With only the
                // temperature on there is nothing to break from and the view
                // stays one line tall.
                text.append(PERF_BREAK);
            }
            // Written as a unicode escape so this file stays ASCII: the degree
            // sign is the one character in the readout that no keyboard on the
            // build machine is guaranteed to produce.
            text.append(TrioState.sBatteryTempC).append("\u00B0C");
        }
        return text.toString();
    }

    /** How many readings the string holds, one per {@link #PERF_BREAK}. */
    private static int perfLines(String text) {
        int lines = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == PERF_BREAK) {
                lines++;
            }
        }
        return lines;
    }

    /**
     * The height one line of this face occupies at this size.
     *
     * <p>Measured on a probe rather than on the view: the size this feeds back
     * is the one being decided, so taking the metrics from the view would make
     * the answer depend on whether the view had already been given it.
     */
    private static float perfLineHeight(float size, int weight) {
        PERF_PROBE.setTypeface(TrioRenderer.typefaceFor(weight));
        // The label itself is emboldened synthetically when a custom single-weight
        // font is in use, and synthetic bold changes the metrics. The probe has to
        // match, or the size it decides is fitted to a face the label is not
        // wearing and the two lines of a stacked readout come out mis-measured.
        PERF_PROBE.setFakeBoldText(TrioRenderer.fakeBoldFor(weight));
        PERF_PROBE.setTextSize(size);
        final Paint.FontMetrics metrics = PERF_PROBE.getFontMetrics();
        return metrics.descent - metrics.ascent;
    }

    /**
     * The text size for the readout, adapted to the row it stands in.
     *
     * <p>One reading keeps the out-of-ring type's own size: it is a single
     * line and already fits. A pair does not - two lines at that size are
     * taller than the icon beside them - so the size is scaled down until the
     * two of them together come to one icon height. That is what keeps the
     * stack reading as one item next to the battery instead of as an overflow
     * of the row, and it is why the pair is smaller than the lone reading
     * rather than the same size drawn twice.
     *
     * <p>The icon's height is the <em>drawn</em> height, taken from the same
     * fit the glyph is rendered with, so it follows the arrangement and the
     * ring stroke the way the drawing does. It also carries the glyph's own
     * overall-size setting: that slider shrinks what is painted inside MIUI's
     * reserved box without moving the box, so measuring the box alone would
     * leave the pair sized to an icon that is no longer there - at 60% the
     * glyph is 36px tall while the stack beside it still laid out for 60px.
     * The scaling stops at {@link #PERF_MIN_SIZE_RATIO}: a host measured
     * before MIUI has settled it must not be able to shrink the reading past
     * legibility.
     *
     * <p>The user's overall-size setting multiplies whatever the rules above
     * settle on, and it is applied to every answer this method can give - the
     * lone reading's base size and the unmeasurable-host fallbacks included. If
     * it only scaled the pair, then with one switch on the slider would move
     * nothing, and turning the second switch on would make the reading jump;
     * applying it last also keeps it a correction on top of the layout rather
     * than a second opinion about it. The lone reading is deliberately left out
     * of the glyph coupling above: it has no icon-height identity to keep, so
     * following the glyph there would only resize text that already fits.
     *
     * <p>The weight comes in through {@link #perfLineHeight} rather than being
     * a separate step, because the weight is what decides the line height: a
     * lighter face is shorter, so a heavier setting would overflow the very
     * identity this method exists to keep. It is the readout's own weight, not
     * the type label's - see {@link Prefs#KEY_PERF_WEIGHT}.
     */
    private static float perfTextSize(String text, View container, View host) {
        final float base = outTypeSizePx(container);
        final TrioAppearance a = TrioConfig.appearance();
        final float factor = a.perfFit();
        final int lines = perfLines(text);
        if (lines < 2 || host == null) {
            return base * factor;
        }
        final float fit = TrioRenderer.inkScale(host.getWidth(), host.getHeight(), a.style);
        if (fit <= 0f) {
            return base * factor;
        }
        final float inkH = a.rect ? TrioGeometry.RECT_INK_H
                : (a.capsule ? TrioGeometry.CAP_INK_H : TrioGeometry.INK_H);
        final float icon = inkH * fit * a.glyphFit();
        final float lineHeight = perfLineHeight(base, a.perfTypeWeight());
        if (lineHeight <= 0f) {
            return base * factor;
        }
        final float size = base * (icon / lines) / lineHeight;
        return Math.max(base * PERF_MIN_SIZE_RATIO, Math.min(base, size)) * factor;
    }

    /**
     * The width the readout is laid out at - deliberately not the width of the
     * text currently in it.
     *
     * <p>Roboto's figures are proportional, so a reading's width follows its
     * digits: {@code 0.0W} is wider than {@code 6.1W}, and on this device that
     * was 102px against 92px. The strip the native icons give up is measured
     * from this view, and MIUI takes it with {@code setPadding}, so a live width
     * meant every power update - about once a second while charging - re-padded
     * the icon row and shoved every native icon on that side of the bar. The
     * strip has to be a function of what the readout <em>is</em>, not of what it
     * currently says.
     *
     * <p>So the width is taken from the widest form the current text could take:
     * every digit replaced by the widest digit this face has. Only the number of
     * digits is left - {@code 9.9W} to {@code 10W} still changes the reserve
     * once, which is a step and not a flicker.
     *
     * <p>Measuring through the paint rather than by laying the view out with a
     * probe string is what keeps this off the layout path, at the cost of
     * leaving the letter spacing out. That makes the answer a few pixels wide of
     * the drawn text at the shipped -0.04em, which is the safe direction: the
     * reserve may be a hair generous, and it must never be short.
     */
    private static int perfWidth(PerfLabel perf, String text) {
        final Paint paint = perf.getPaint();
        if (paint == null || text.length() == 0) {
            return perf.getMeasuredWidth();
        }
        // Asked of the face rather than assumed: the widest digit is not the
        // same at every weight, and a wrong guess would leave the reserve short.
        char widest = '0';
        float best = -1f;
        for (char c = '0'; c <= '9'; c++) {
            final float w = paint.measureText(String.valueOf(c));
            if (w > best) {
                best = w;
                widest = c;
            }
        }
        // Per line, and the widest one wins. Measuring the whole string in one
        // call would add the two lines together - the break is just a character
        // to the paint - and reserve the reading at twice its real width.
        final StringBuilder probe = new StringBuilder(text.length());
        float width = 0f;
        for (int i = 0; i <= text.length(); i++) {
            if (i == text.length() || text.charAt(i) == PERF_BREAK) {
                width = Math.max(width, paint.measureText(probe.toString()));
                probe.setLength(0);
                continue;
            }
            final char c = text.charAt(i);
            probe.append(c >= '0' && c <= '9' ? widest : c);
        }
        return (int) Math.ceil(width);
    }

    /**
     * The line-height multiplier that squeezes a stack of readings into the
     * row the status bar actually has.
     *
     * <p>One line always gets the normal multiplier back: there is nothing to
     * fit and shrinking a single reading would only make it look like a
     * different setting. Two lines share the container's height, and the
     * multiplier is whatever makes them do so, floored so they cannot be driven
     * into each other by a container that is too short even for that.
     *
     * <p>Ordinarily this returns 1f: {@link #perfTextSize} has already sized
     * the pair to one icon height, and the container is at least that tall. It
     * still has work when that is not true - the row is shorter than the stack
     * it holds, which is what the size floor leaves behind once it is biting -
     * and there the leading is what gives, after the size already has.
     */
    private static float perfLineSpacing(TextView view, String text,
                                         View container) {
        final int lines = perfLines(text);
        if (lines < 2) {
            return 1f;
        }
        final int room = container.getHeight()
                - container.getPaddingTop() - container.getPaddingBottom();
        if (room <= 0) {
            return 1f;
        }
        // The view's own paint, read after the text size was set on it: the
        // line height is whatever this typeface and this size actually give,
        // not what a fresh default Paint would guess.
        final Paint.FontMetrics metrics = view.getPaint().getFontMetrics();
        final float lineHeight = metrics.descent - metrics.ascent;
        if (lineHeight <= 0f) {
            return 1f;
        }
        // setLineSpacing scales every line after the first, so the first line
        // keeps its full height and what has to fit is one whole line plus the
        // compressed ones below it. Dividing the room by the line count instead
        // would spread that full height over all of them and leave the stack
        // taller than the row.
        final float aboveLast = room - lineHeight;
        if (aboveLast <= 0f) {
            return PERF_MIN_LINE_SPACING;
        }
        final float multiplier = aboveLast / (float) (lines - 1) / lineHeight;
        if (multiplier >= 1f) {
            return 1f;
        }
        return Math.max(PERF_MIN_LINE_SPACING, multiplier);
    }

    /**
     * Whole watts from ten up, one decimal below.
     *
     * <p>Assembled from integers rather than through {@code String.format} so no
     * {@link java.util.Locale} is needed at all: a status bar reading is five
     * characters wide at most, and a comma decimal separator would be wider than
     * the tenths are informative.
     */
    private static String powerText(float watts) {
        final int tenths = Math.round(watts * 10f);
        if (tenths >= 100) {
            return (tenths / 10) + "W";
        }
        return (tenths / 10) + "." + (tenths % 10) + "W";
    }

    /** The performance readout already mounted on {@code container}, or null. */
    private static PerfLabel findPerfLabel(ViewGroup container) {
        final int count = container.getChildCount();
        for (int i = 0; i < count; i++) {
            final View child;
            try {
                child = container.getChildAt(i);
            } catch (Throwable t) {
                continue;
            }
            if (child instanceof PerfLabel) {
                return (PerfLabel) child;
            }
        }
        return null;
    }

    private static PerfLabel createPerfLabel(ViewGroup container) {
        final PerfLabel perf = new PerfLabel(container.getContext());
        perf.setGravity(Gravity.CENTER);
        // Two lines, not the one setSingleLine(true) would pin it to: with both
        // readings on the text carries a break, and a single-line view would
        // draw the second reading off the end of its own box. The explicit
        // measure below is what actually decides the height - this only stops
        // the framework from doing something else with the break.
        perf.setMaxLines(2);
        perf.setIncludeFontPadding(false);
        perf.setLetterSpacing(TrioGeometry.VALUE_LETTER_SPACING);
        perf.setVisibility(View.GONE);
        // WRAP_CONTENT on both axes: the size comes from the explicit measure
        // below. The container never measures this child on its own -
        // MiuiStatusBatteryContainer.onMeasure only touches its three named
        // fields - so whatever we measure stands.
        container.addView(perf, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return perf;
    }

    private static void removePerfLabel(View container) {
        if (!(container instanceof ViewGroup)) {
            return;
        }
        final ViewGroup group = (ViewGroup) container;
        final PerfLabel perf = findPerfLabel(group);
        if (perf != null) {
            try {
                group.removeView(perf);
            } catch (Throwable ignored) {
                // a container torn down under us is not worth crashing over
            }
        }
        // Recompute rather than release first, for the same reason
        // removeOutTypeLabel does: the strip is shared, this runs on every sync
        // including the ones where no readout is mounted, and a release here
        // would hand the icons' padding back only to be re-reserved for whatever
        // else is still on screen. reserveOutRingStrip is the authority - it
        // totals what is actually mounted and releases the strip itself when
        // that total is zero.
        reserveOutRingStrip(group);
    }

    private static void updatePerfLabel(ViewGroup container, PerfLabel perf,
                                        View host, View anchor) {
        final TrioAppearance a = TrioConfig.appearance();
        final TrioState state = stateFor(host);
        if (state != null) {
            // Pull the status values in before reading them: this runs from a
            // posted runnable, but the state snapshot is only as fresh as the
            // last call to refresh().
            state.refresh();
        }
        final String text = perfText(a);
        if (text.length() == 0) {
            // Hidden rather than removed, and the strip handed back: with the
            // reading gone there is nothing to reserve for, and holding the
            // space open would leave a hole in the status bar.
            if (perf.getTranslationX() != 0f) {
                perf.setTranslationX(0f);
            }
            if (perf.getVisibility() != View.GONE) {
                perf.setVisibility(View.GONE);
            }
            // The type label and the reading may still be mounted and still need
            // their strip; the bookkeeping above cannot tell, so it is asked.
            reserveOutRingStrip(container);
            return;
        }
        if (!text.contentEquals(perf.getText())) {
            perf.setText(text);
        }
        // The out-of-ring type's own size is the base, not a setting of its
        // own: the readings stand in the same strip as the out-of-ring label
        // and have to match it, so a separate absolute size could only ever
        // make them disagree. A pair is scaled down from that base to fit one
        // icon; see perfTextSize. The one thing layered on top is the user's
        // overall-size multiplier, which scales whatever the base and the fit
        // settle on and so cannot break the match either.
        final float size = perfTextSize(text, container, host);
        if (perf.getTextSize() != size) {
            // PX, not the SP that the one-argument overload would use: the
            // status bar lays out in raw pixels.
            perf.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
        }
        // The readout's own weight, not the type label's: this view is status-bar
        // text standing beside MIUI's figures, while the type weight also feeds
        // the label drawn inside the ring. Recorded as the number so the re-sync
        // test can ask the same question, and compared that way rather than by
        // Typeface identity - Typeface.create is only documented to answer an
        // equal face, so an identity compare could either miss a real change or
        // re-set the same face on every pass.
        final int weight = a.perfTypeWeight();
        final int generation = TrioRenderer.fontGeneration();
        if (perf.weight != weight || perf.fontGeneration != generation) {
            TrioRenderer.styleTextView(perf, weight);
            perf.weight = weight;
            perf.fontGeneration = generation;
        }
        // A stack of two readings has to fit the row the bar actually gives it,
        // and it does not at the full line height: the status bar is 88px tall
        // here and two lines of the shared type size are taller than that. The
        // glyphs are all digits with no descenders, so the leading is what
        // gives, down to a floor that still keeps the two lines from touching.
        final float spacing = perfLineSpacing(perf, text, container);
        if (perf.getLineSpacingMultiplier() != spacing) {
            perf.setLineSpacing(0f, spacing);
        }
        final int colour = (state != null) ? state.foreground() : 0xFFFFFFFF;
        if (perf.getCurrentTextColor() != colour) {
            perf.setTextColor(colour);
        }
        if (perf.getVisibility() != View.VISIBLE) {
            perf.setVisibility(View.VISIBLE);
        }
        perf.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        // Measured first: the strip the native row has to give up is this view's
        // own width plus its gap, and reserveOutRingStrip counts the other
        // out-of-ring views as well.
        reserveOutRingStrip(container);
        placeOutTypeLabel(container, perf, readingAnchorIn(container, anchor));
        if (debugLog()) {
            // The break is printed as a slash rather than literally: a raw
            // newline in the message would split one reading over two logcat
            // lines and make the numbers hard to line up with the frame they
            // describe.
            log(LOG_INFO, "performance: \"" + text.replace(PERF_BREAK, '/') + "\" size=" + size
                    + " view=" + perf.getWidth() + "x" + perf.getHeight()
                    + " at " + perf.getLeft() + "," + perf.getTop()
                    + " anchor=" + anchor.getLeft() + ".." + anchor.getRight()
                    + " container=" + container.getWidth() + "x" + container.getHeight());
        }
    }

    /**
     * Puts the label just outside the battery meter, on the side the container
     * reads away from: left of it in LTR, right of it in RTL. This is the gap
     * MIUI's own {@code mobile_type} label used to fill.
     */
    private static void placeOutTypeLabel(ViewGroup container, View view, View anchor) {
        placeOutTypeLabel(container, view, anchor, outLabelAnchorGap(container));
    }

    /**
     * The same placement, with the gap toward the anchor given explicitly.
     *
     * <p>Every out-of-ring view is positioned by a <em>gap</em>, never by a
     * position. That is the whole point: {@link #reserveOutRingStrip} reserves
     * this same number plus the view's width out of the native icon row, so a
     * larger gap moves the view away from its neighbour <em>and</em> pushes the
     * icons over by the same amount. A free position offset - which this
     * replaced - moved the view without reserving anything, so a large enough
     * value painted the reading over the icons it should have displaced.
     *
     * <p>Which physical gap each margin supplies flips with the reading
     * direction, and getting it backwards is invisible in LTR and wrong in RTL:
     *
     * <pre>
     *   LTR:  [label] --label right-- [reading] --signal margin-- [battery]
     *   RTL:  [battery] --signal margin-- [reading] --label left-- [label]
     * </pre>
     *
     * <p>So {@code gap} is always "the space between this view and its anchor",
     * and this method only decides which side that is: the view goes at
     * {@code anchor.getLeft() - gap - width} in LTR and
     * {@code anchor.getRight() + gap} in RTL.
     *
     * <p>The view is centred on its anchor vertically and is never offset on
     * that axis. A status-bar row has no vertical room to reserve, so a vertical
     * nudge could only ever overlap the icons - there is nothing for it to
     * displace.
     */
    private static void placeOutTypeLabel(ViewGroup container, View view, View anchor, int gap) {
        layoutOutRingStrip(container, anchor);
    }

    /** The same stable slot width feeds reservation and placement. */
    private static int outRingSlotWidth(View view) {
        if (view == null || view.getVisibility() != View.VISIBLE) {
            return 0;
        }
        if (view instanceof PerfLabel) {
            final PerfLabel perf = (PerfLabel) view;
            return Math.max(perf.getMeasuredWidth(), perfWidth(perf, perf.getText().toString()));
        }
        return view.getMeasuredWidth();
    }

    /**
     * The three out-of-ring views in the user's chosen arrangement, nearest the battery
     * first.
     *
     * <p>This is the single place the {@code out_ring_order} setting is turned into views,
     * and it exists because the arrays handed to {@link OutRingLayout#solve} index the strip
     * from the battery outwards: their order <em>is</em> the placement. Reading the setting
     * at both call sites would be two chances to get the mapping wrong, and the two callers
     * - the measure pass and the layout pass - disagreeing about which view is slot 0 is
     * exactly a piece drawn over its neighbour.
     *
     * <p>{@link #outRingDropOrder} is <b>not</b> derived from this: the sacrificed piece is
     * chosen by what the reading is worth, not by where the user put it.
     */
    private static View[] outRingViews(ViewGroup container) {
        final int[] order = slotOrder(TrioConfig.appearance());
        final View signal = findOutSignal(container);
        final View perf = findPerfLabel(container);
        final View type = findOutTypeLabel(container);
        final View[] views = new View[order.length];
        for (int i = 0; i < order.length; i++) {
            views[i] = slotView(order[i], signal, perf, type);
        }
        return views;
    }

    /** The stored arrangement, normalised: a settings read can never yield a short array. */
    private static int[] slotOrder(TrioAppearance a) {
        final int[] stored = a == null ? null : a.outOrder;
        return stored == null ? Prefs.OUT_DEFAULT_ORDER : stored;
    }

    /** The view for one {@code OUT_SLOT_*} id. */
    private static View slotView(int slot, View signal, View perf, View type) {
        switch (slot) {
            case Prefs.OUT_SLOT_SIGNAL: return signal;
            case Prefs.OUT_SLOT_READOUTS: return perf;
            case Prefs.OUT_SLOT_TYPE: return type;
            default: return null;
        }
    }

    /**
     * The slot indices to sacrifice when the strip overflows, least important first.
     *
     * <p>Translated from {@link Prefs#OUT_PRIORITY} through the <em>current</em> arrangement,
     * so priority survives the user reordering: the type label is the cheapest to lose
     * because it is one unchanging string, wherever the user chose to put it.
     */
    private static int[] outRingDropOrder(TrioAppearance a) {
        final int[] order = slotOrder(a);
        final int[] drop = new int[Prefs.OUT_PRIORITY.length];
        int n = 0;
        // The solver drops the LAST entry of the list first, so the array is filled from the
        // back: index 0 here must be the most important, which is how OUT_PRIORITY reads.
        for (int i = 0; i < Prefs.OUT_PRIORITY.length; i++) {
            final int at = Prefs.outOrderIndex(order, Prefs.OUT_PRIORITY[i]);
            if (at >= 0 && at < order.length) {
                drop[drop.length - 1 - n] = at;
                n++;
            }
        }
        // Anything not named (a repaired or partial order) is left out, and the solver falls
        // back to the positional rule for it.
        if (n == drop.length) {
            return drop;
        }
        final int[] trimmed = new int[n];
        System.arraycopy(drop, drop.length - n, trimmed, 0, n);
        return trimmed;
    }

    /** Reflow all siblings together; a changed member cannot leave stale neighbours. */
    private static OutRingLayout.Plan outRingPlan(ViewGroup container, View icons) {
        final View[] views = outRingViews(container);
        final int[] widths = new int[views.length];
        final int[] heights = new int[views.length];
        for (int i = 0; i < views.length; i++) {
            widths[i] = outRingSlotWidth(views[i]);
            heights[i] = widths[i] > 0 ? views[i].getMeasuredHeight() : 0;
        }
        final TrioAppearance appearance = TrioConfig.appearance();
        final int[] order = slotOrder(appearance);
        final boolean rtl = container.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        final int[] margins = outLabelMargins(container);
        final int inward = rtl ? margins[0] : margins[1];
        final int[] gaps = new int[views.length];
        for (int i = 0; i < views.length; i++) {
            // Each slot keeps its OWN gap, so a piece carries its spacing with it when the
            // arrangement changes: the reading keeps the battery margin, the type label keeps
            // its label margins. One shared gap would silently reset a tuned value the moment
            // two pieces swapped places.
            gaps[i] = outGapFor(order[i], container, inward);
        }
        // The outward gap belongs to the piece actually furthest out, whichever that is.
        final int outermost = order.length == 0 ? Prefs.OUT_SLOT_TYPE
                : order[order.length - 1];
        final int outward = widths[views.length - 1] > 0 && outermost == Prefs.OUT_SLOT_TYPE
                ? (rtl ? margins[1] : margins[0]) : 0;
        final int[] saved = icons != null ? OUT_PAD_SAVED.get(icons) : null;
        // Current padding already includes our reserve; deduct only the original.
        final int baseLeft = saved != null ? saved[0] : (icons != null ? icons.getPaddingLeft() : 0);
        final int baseRight = saved != null ? saved[1] : (icons != null ? icons.getPaddingRight() : 0);
        final int height = Math.max(0, container.getHeight()
                - container.getPaddingTop() - container.getPaddingBottom());
        return OutRingLayout.solve(icons != null ? icons.getWidth() : 0,
                rtl ? baseRight : baseLeft, rtl ? baseLeft : baseRight,
                islandBoundaryPx(icons), height, widths, heights, gaps, outward,
                outRingDropOrder(appearance));
    }

    /** The gap one {@code OUT_SLOT_*} keeps towards its inner neighbour, in pixels. */
    private static int outGapFor(int slot, ViewGroup container, int labelInward) {
        if (slot == Prefs.OUT_SLOT_SIGNAL) {
            return outSignalMargin(container);
        }
        if (slot == Prefs.OUT_SLOT_TYPE) {
            return labelInward;
        }
        // The performance readout is text like the label and shares its inward margin; it
        // has never had a margin setting of its own.
        return labelInward;
    }

    private static final Map<View, Boolean> OUT_RESERVE_PENDING =
            new WeakHashMap<View, Boolean>();

    private static void requestOutRingReserve(final ViewGroup container) {
        if (OUT_RESERVE_PENDING.put(container, Boolean.TRUE) != null) {
            return;
        }
        container.post(new Runnable() {
            @Override
            public void run() {
                OUT_RESERVE_PENDING.remove(container);
                reserveOutRingStrip(container);
                layoutOutRingStrip(container, hostIn(container));
            }
        });
    }

    /** Reflow all siblings together; a changed member cannot leave stale neighbours. */
    private static void layoutOutRingStrip(ViewGroup container, View anchor) {
        final View icons = iconContainerIn(container);
        final View host = hostIn(container);
        final View meter = host != null ? meterIn(host, container) : null;
        final View centre = meter != null ? meter : anchor;
        final View[] views = outRingViews(container);
        final OutRingLayout.Plan plan = outRingPlan(container, icons);
        final boolean rtl = container.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        final boolean ready = icons != null && icons.getWidth() > 0 && centre != null;
        final int[] saved = icons != null ? OUT_PAD_SAVED.get(icons) : null;
        if (ready && container.getHeight() > 0) {
            final int currentReserve = saved == null ? 0 : (rtl
                    ? icons.getPaddingLeft() - saved[0] : icons.getPaddingRight() - saved[1]);
            if (currentReserve != plan.reserve) {
                // Layout may change available width/height without new readings.
                // Apply the matching padding later, never inside onLayout.
                requestOutRingReserve(container);
            }
        }
        for (int i = 0; i < views.length; i++) {
            final View child = views[i];
            if (child == null || plan.widths[i] <= 0) {
                // Two different reasons a slot has no width, and they need different
                // handling: nothing to draw (widths[i] <= 0 from the caller) needs no
                // touch, but a slot the solver DROPPED has to be hidden or it would keep
                // painting at its old frame.
                if (child != null && plan.dropped[i]) {
                    child.setAlpha(0f);
                }
                continue;
            }
            final float scale = ready ? plan.scale : 0f;
            // Alpha affects painting only. Keep the requested VISIBLE state so
            // a temporarily covered strip is still reserved and can recover.
            child.setAlpha(scale > 0f ? 1f : 0f);
            child.setScaleX(scale);
            child.setScaleY(scale);
            final int w = child.getMeasuredWidth();
            final int h = child.getMeasuredHeight();
            child.setPivotX(rtl ? 0f : w);
            child.setPivotY(h / 2f);
            if (!ready) {
                child.setTranslationX(0f);
                continue;
            }
            final float edge = rtl ? icons.getRight() - plan.near[i]
                    : icons.getLeft() + plan.near[i];
            final float left = rtl ? edge : edge - w;
            final int frameLeft = (int) Math.floor(left);
            final float centreY = Math.max(container.getPaddingTop() + h * scale / 2f,
                    Math.min(container.getHeight() - container.getPaddingBottom() - h * scale / 2f,
                            centre.getTop() + centre.getHeight() / 2f));
            final int top = Math.round(centreY - h / 2f);
            child.layout(frameLeft, top, frameLeft + w, top + h);
            // Fractional placement only. Island width is an exclusion boundary,
            // not a translation to subtract from every sibling.
            child.setTranslationX(left - frameLeft);
        }
    }

    /** The label's margin on the side that faces its anchor, in pixels. */
    private static int outLabelAnchorGap(ViewGroup container) {
        final boolean rtl =
                container.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        final int[] margins = outLabelMargins(container);
        return rtl ? margins[0] : margins[1];
    }

    /**
     * The two gaps the out-of-ring label keeps, in pixels, in the label's own
     * reading order: {@code [0]} is the side the label reads <em>from</em> and
     * {@code [1]} the side it reads <em>to</em>.
     *
     * <p>Not named left/right on purpose. The two settings are physical sides of
     * the label ({@code out_type_margin_left}/{@code _right}), but which physical
     * side a run leads with flips under RTL, so keeping the reading-order pair
     * here and letting each caller pick the physical one is what stops the RTL
     * branch from silently swapping the two.
     *
     * <p>The dp鈫抪x conversion follows {@link TrioRenderer#outSignalHeight}: the
     * display's density, never the status bar row's height, which MIUI changes
     * when the control centre is pulled down.
     */
    private static int[] outLabelMargins(View container) {
        final float density = container.getResources().getDisplayMetrics().density;
        final TrioAppearance a = TrioConfig.appearance();
        return new int[] {
                Math.round(a.outTypeMarginLeft * density),
                Math.round(a.outTypeMarginRight * density)};
    }

    /** The gap the out-of-ring reading keeps from the battery, in pixels. */
    private static int outSignalMargin(View container) {
        final float density = container.getResources().getDisplayMetrics().density;
        return Math.round(TrioConfig.appearance().outSignalMargin * density);
    }

    /** The out-of-ring label's font size in pixels, after the dp setting. */
    private static float outTypeSizePx(View container) {
        final float density = container.getResources().getDisplayMetrics().density;
        return TrioConfig.appearance().outTypeSize * density;
    }

    /**
     * The {@code MiuiStatusIconContainer} nested in a battery container, i.e. the
     * view whose end padding decides where the native icon row starts.
     */
    private static View iconContainerIn(View batteryContainer) {
        final Object icons = Refl.get(sStatusIconField, batteryContainer);
        return (icons instanceof View) ? (View) icons : null;
    }

    /** Logical start-side occlusion boundary reported by this icon row's island monitor. */
    private static int islandBoundaryPx(View iconContainer) {
        if (iconContainer == null) {
            return 0;
        }
        try {
            final Class<?> cls = iconContainer.getClass();
            final Field monitorField = Refl.field(cls, "_islandMonitor");
            if (monitorField == null) {
                return 0;
            }
            final Object monitor = Refl.get(monitorField, iconContainer);
            if (monitor == null) {
                return 0;
            }
            final Method blocked = Refl.method(monitor.getClass(), "getBlocked");
            if (blocked != null && Boolean.TRUE.equals(Refl.invoke(blocked, monitor))) {
                return 0;
            }
            final Method width = Refl.method(monitor.getClass(), "getIslandWidth");
            if (width == null) {
                return 0;
            }
            final Object value = Refl.invoke(width, monitor);
            final int px = (value instanceof Integer) ? (Integer) value : 0;
            return (px > 0) ? px : 0;
        } catch (Throwable ignored) {
            // Retain normal strip placement when this ROM exposes no monitor.
            return 0;
        }
    }

    /**
     * Makes the native icon row give up {@code reserve} pixels at its end, so the
     * out-of-ring label has room to stand in rather than being painted over.
     *
     * <p>{@code MiuiStatusIconContainer.onLayout} walks its children right to left
     * starting from {@code getWidth() - getPaddingEnd()} and positions each one by
     * {@code translationX}. Growing that end padding is therefore enough to slide
     * the whole row inward - no view has to be added to the container, which is
     * just as well because every one of its children is cast to
     * {@code StatusIconDisplayable} and a foreign view would crash the pass.
     *
     * <p>The original values are remembered against the icon container, so
     * repeated calls cannot accumulate: {@code setPadding} is always called with
     * the original plus the current reserve, never with the current value plus
     * the reserve.
     *
     * <p>Callers must be on a posted path - {@code setPadding} schedules a layout,
     * which is illegal inside one.
     */
    private static void reserveOutTypeSpace(ViewGroup batteryContainer, int reserve) {
        if (reserve <= 0) {
            return;
        }
        final View icons = iconContainerIn(batteryContainer);
        if (icons == null) {
            return;
        }
        int[] saved = OUT_PAD_SAVED.get(icons);
        if (saved == null) {
            saved = new int[] {icons.getPaddingLeft(), icons.getPaddingRight()};
            OUT_PAD_SAVED.put(icons, saved);
        }
        // getPaddingEnd() resolves to the right in LTR and to the left in RTL, and
        // that is the side the icon row is laid out from - so that is the side the
        // room has to come out of.
        final boolean rtl = icons.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        final int left = saved[0] + (rtl ? reserve : 0);
        final int right = saved[1] + (rtl ? 0 : reserve);
        if (icons.getPaddingLeft() == left && icons.getPaddingRight() == right) {
            return;
        }
        icons.setPadding(left, icons.getPaddingTop(), right, icons.getPaddingBottom());
        if (debugLog()) {
            log(LOG_INFO, "out type: reserved " + reserve + "px, icon padding "
                    + saved[0] + "/" + saved[1] + " -> " + left + "/" + right
                    + ", icons w=" + icons.getWidth());
        }
    }

    /** Hands the reserved strip back to the native icon row. Idempotent. */
    private static void releaseOutTypeSpace(View batteryContainer) {
        final View icons = iconContainerIn(batteryContainer);
        if (icons == null) {
            return;
        }
        final int[] saved = OUT_PAD_SAVED.remove(icons);
        if (saved == null) {
            return;
        }
        if (icons.getPaddingLeft() == saved[0] && icons.getPaddingRight() == saved[1]) {
            return;
        }
        icons.setPadding(saved[0], icons.getPaddingTop(), saved[1], icons.getPaddingBottom());
    }

    // ---------------------------------------------------- out-of-ring signal

    /**
     * The out-of-ring SIM reading: four ascending capsule bars, and the dot row
     * beneath them when both SIMs have a reading.
     *
     * <p>A {@link View} of our own rather than MIUI's signal icon. That icon is
     * one icon for one reading, while this mode draws a reading per SIM and has
     * to place them itself; and being our own class gives {@link #findOutSignal}
     * a marker no MIUI view can match by accident.
     *
     * <p>It is mounted in the battery container, never in the icon container:
     * {@code MiuiStatusIconContainer.onLayout} casts each of its children to
     * {@code StatusIconDisplayable}, and a foreign child would crash the pass.
     */
    private static final class OutSignalView extends View {
        /** The glyph host the reading and the icon colour come from. */
        View host;
        /** Bars to light, {@code 0..}{@link TrioGeometry#STACK_COLUMNS}. */
        int bars;
        /** Dots to light, or {@code -1} when there is no second reading. */
        int dots = -1;

        OutSignalView(Context context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final View source = host;
            if (source == null) {
                return;
            }
            // Read on every frame rather than cached. Nothing invalidates this
            // view when the tint changes - it is a sibling of the glyph host,
            // outside the chain MIUI's dark-mode pass walks - so the frame has
            // to be asked for, and it is asked for by recolourOutRing().
            final TrioState state = stateFor(source);
            final int fg = (state != null) ? state.foreground() : 0xFFFFFFFF;
            TrioRenderer.drawOutSignal(canvas, getWidth(), getHeight(), bars, dots, fg,
                    TrioConfig.appearance());
        }
    }

    /**
     * Mounts, updates or removes the out-of-ring reading for one glyph host.
     *
     * <p>Posted only, for the same reason as {@link #syncOutTypeLabel}: it may
     * add a view, and this runnable runs from a layout pass otherwise.
     */
    private static void syncOutSignal(View host) {
        if (host == null) {
            return;
        }
        final Object owner = batteryContainerOf(host);
        if (!(owner instanceof ViewGroup)) {
            return;
        }
        final ViewGroup container = (ViewGroup) owner;
        // Two ways in: the user asked for the reading, or the island took the
        // glyph away while the user still wants the signal drawn in-ring - the
        // reading is then the only place left for it.
        final boolean wanted = outSignalWanted();
        final OutSignalView view = findOutSignal(container);
        if (!wanted) {
            removeOutSignal(container);
            return;
        }
        final View anchor = meterIn(host, container);
        if (anchor == null) {
            removeOutSignal(container);
            return;
        }
        // Only a guard: inkScale() is 0 exactly when the host has not been
        // measured yet, and an unmeasured meter has no frame to place against.
        if (TrioRenderer.inkScale(host.getWidth(), host.getHeight()) <= 0f) {
            removeOutSignal(container);
            return;
        }
        final OutSignalView target = (view != null) ? view : createOutSignal(container);
        target.host = host;
        updateOutSignal(container, target, host, anchor);
    }

    /**
     * Repositions an already-mounted reading. Runs inside the icon container's
     * layout pass, so it may relayout the view but must never measure it: a
     * re-measure schedules a layout, which is illegal from in there. Anything
     * that would change its size is handed to the posted path instead.
     */
    private static void refreshOutSignal(ViewGroup iconContainer) {
        final Object owner = batteryContainerOf(iconContainer);
        if (!(owner instanceof ViewGroup)) {
            return;
        }
        final ViewGroup container = (ViewGroup) owner;
        final OutSignalView view = findOutSignal(container);
        if (view == null) {
            requestOutSignalSync(container);
            return;
        }
        final View host = view.host;
        if (host == null || container.getWidth() <= 0 || container.getHeight() <= 0) {
            return;
        }
        final View anchor = meterIn(host, container);
        if (anchor == null) {
            return;
        }
        if (TrioRenderer.inkScale(host.getWidth(), host.getHeight()) <= 0f) {
            return;
        }
        if (anchor.getHeight() <= 0) {
            return;
        }
        final int[] reading = new int[2];
        outSignalReading(stateFor(host), reading);
        if (reading[0] < 0) {
            reading[0] = 0;
        }
        // Size changed - a SIM appearing or disappearing, the row resizing, or
        // the size slider moving - is a re-measure, so it goes back through the
        // posted path.
        if (view.bars != reading[0] || view.dots != reading[1]
                || view.getMeasuredHeight() != outSignalHeight(host)) {
            requestOutSignalSync(container);
            return;
        }
        // Shares the label's slot geometry: both stand outside the battery
        // meter, one against the other, so they are placed by one routine. Each
        // is placed by its own reserved margin - the reading by the signal
        // margin, the label by its two - which is why neither can be nudged onto
        // a neighbour.
        placeOutTypeLabel(container, view, anchor, outSignalMargin(container));
    }

    /**
     * Queues one {@link #syncOutSignal} for the host under {@code container},
     * without ever queuing an empty one: the runnable is a no-op unless the
     * module is drawing the reading itself and that host has been measured.
     */
    private static void requestOutSignalSync(final ViewGroup container) {
        // Same condition syncOutSignal mounts the reading under, so a layout
        // pass can never queue a sync the posted path would immediately undo -
        // which matters under the island, where the reading is wanted with the
        // stacked switch off as long as the user keeps the signal on.
        if (!outSignalWanted()) {
            return;
        }
        final View host = hostIn(container);
        if (host == null
                || TrioRenderer.inkScale(host.getWidth(), host.getHeight()) <= 0f) {
            return;
        }
        final View target = host;
        container.post(new Runnable() {
            @Override
            public void run() {
                syncOutSignal(target);
            }
        });
    }

    /**
     * The bars and dots a host's current readings resolve to.
     *
     * <p>One place asks the renderer, so the posted pass that measures the view
     * and the layout pass that compares against it can never disagree about
     * what the reading is.
     */
    private static void outSignalReading(TrioState state, int[] out) {
        final int[] slots = (state != null) ? state.slotLevels : null;
        final int mobile = (state != null) ? state.mobileLevel : -1;
        TrioRenderer.outSignalLevels(TrioConfig.appearance().dataSimOnly, mobile, slots,
                TrioState.sDataSlot, out);
    }

    private static OutSignalView findOutSignal(ViewGroup container) {
        final int count = container.getChildCount();
        for (int i = 0; i < count; i++) {
            final View child;
            try {
                child = container.getChildAt(i);
            } catch (Throwable ignored) {
                continue;
            }
            if (child instanceof OutSignalView) {
                return (OutSignalView) child;
            }
        }
        return null;
    }

    private static OutSignalView createOutSignal(ViewGroup container) {
        final OutSignalView view = new OutSignalView(container.getContext());
        view.setVisibility(View.GONE);
        // WRAP_CONTENT on both axes: the size is set explicitly by measuring
        // against exact specs further down. MiuiStatusBatteryContainer.onMeasure
        // only touches its three named fields, so whatever we measure stands.
        container.addView(view, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return view;
    }

    private static void removeOutSignal(View container) {
        if (!(container instanceof ViewGroup)) {
            return;
        }
        final ViewGroup group = (ViewGroup) container;
        final OutSignalView view = findOutSignal(group);
        if (view == null) {
            return;
        }
        try {
            group.removeView(view);
        } catch (Throwable ignored) {
            // a container torn down under us is not worth crashing over
        }
        // The strip the reading held has to go back the moment it does, or the
        // native icon row would keep laying out short of a view that is gone.
        reserveOutRingStrip(group);
    }

    private static void updateOutSignal(ViewGroup container, OutSignalView view,
                                        View host, View anchor) {
        final TrioState state = stateFor(host);
        if (state != null) {
            // The snapshot is only as fresh as the last refresh(), and this runs
            // from a posted runnable rather than from the draw path.
            state.refresh();
        }
        final int[] reading = new int[2];
        outSignalReading(state, reading);
        // A reading telephony has not answered for yet is drawn unlit rather
        // than absent. Folding the mobile slot has already happened by the time
        // this runs, so hiding the view would leave a hole in the status bar
        // until the modem answers - and four dark bars is also what MIUI itself
        // draws for "no service".
        if (reading[0] < 0) {
            reading[0] = 0;
        }
        final int height = outSignalHeight(host);
        if (height <= 0) {
            return;
        }
        final int width = TrioRenderer.outSignalWidth(height);
        if (width <= 0) {
            return;
        }
        view.bars = reading[0];
        view.dots = reading[1];
        if (view.getVisibility() != View.VISIBLE) {
            view.setVisibility(View.VISIBLE);
        }
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        // Measured first, then reserved: the strip the native row gives up is
        // this view's width plus the label's, and the gap around both.
        reserveOutRingStrip(container);
        placeOutTypeLabel(container, view, anchor, outSignalMargin(container));
        // The reading is not part of this view's geometry: the height comes from
        // the battery meter and the width from that height alone, so switching
        // between the one-row and two-row reading - or any SIM falling off the
        // network - repaints nothing on its own. setVisibility() is skipped
        // because the view is already VISIBLE, and the invalidate() the rest of
        // the module does lands on the glyph host, which is a sibling: it never
        // redraws this view. Without this line the new reading only appears when
        // something unrelated re-measures this view, which in practice means the
        // out-of-ring size slider.
        view.invalidate();
        if (debugLog()) {
            log(LOG_INFO, "out signal: bars=" + reading[0] + " dots=" + reading[1]
                    + " view=" + view.getWidth() + "x" + view.getHeight()
                    + " at " + view.getLeft() + "," + view.getTop()
                    + " anchor=" + anchor.getLeft() + ".." + anchor.getRight()
                    + " container=" + container.getWidth() + "x" + container.getHeight());
        }
    }

    /**
     * The height the out-of-ring reading wants, after the size setting. Both the
     * measure and the change check above go through here so a slider move and a
     * re-measure can never disagree about the target.
     *
     * <p>Only the display's density is taken from {@code host}; its measured
     * height deliberately is not. MIUI lays the battery container out at one
     * height at rest and at the full {@code statusBars} inset once the control
     * centre is pulled down, so a size derived from the row grew with the shade.
     */
    private static int outSignalHeight(View host) {
        final float density = host.getResources().getDisplayMetrics().density;
        return TrioRenderer.outSignalHeight(TrioConfig.appearance().outSignalSize, density);
    }

    /**
     * The view the out-of-ring label stands next to: the performance readout
     * when it is mounted, the SIM reading when that is, the battery meter
     * otherwise.
     *
     * <p>That is MIUI's own reading order - network type, then signal, then
     * battery - so each view is always the outermost of the ones below it and no
     * two ever claim the same strip. The performance readout joined the chain
     * between the reading and the label, which is why this asks for it first:
     * the label has to clear the readout as well as the reading.
     */
    private static View labelAnchorIn(ViewGroup container, View meter) {
        final PerfLabel perf = findPerfLabel(container);
        if (perf != null && perf.getVisibility() == View.VISIBLE) {
            return perf;
        }
        return readingAnchorIn(container, meter);
    }

    /**
     * The view the performance readout stands next to: the SIM reading when it
     * is mounted, the battery meter otherwise.
     *
     * <p>Split out of {@link #labelAnchorIn} rather than written inline because
     * the two are different questions. The label chains through whatever is
     * outermost, while the readout has to ignore itself - asking
     * {@code labelAnchorIn} from inside {@code updatePerfLabel} would hand the
     * view its own frame as its anchor and it would place itself against itself.
     */
    private static View readingAnchorIn(ViewGroup container, View meter) {
        final OutSignalView signal = findOutSignal(container);
        if (signal != null && signal.getVisibility() == View.VISIBLE) {
            return signal;
        }
        return meter;
    }

    /**
     * Gives the native icon row exactly the room the out-of-ring views need: the
     * reading, the performance readout and the label, plus the gap that
     * separates each from the next thing. With none mounted the strip is handed
     * back entirely.
     *
     * <p>All three callers use this rather than reserving for themselves, because
     * the strip is one number: whichever view is updated last would otherwise
     * decide the padding for all of them.
     */
    private static void reserveOutRingStrip(ViewGroup container) {
        final View icons = iconContainerIn(container);
        final OutRingLayout.Plan plan = outRingPlan(container, icons);
        // Before the first layout request the desired width, so the native row
        // can measure it. Subsequent passes reserve the actual scaled span.
        final int total = icons != null && icons.getWidth() > 0 && container.getHeight() > 0
                ? plan.reserve : plan.total;
        if (total <= 0) {
            releaseOutTypeSpace(container);
        } else {
            reserveOutTypeSpace(container, total);
        }
    }

    /**
     * The direct child of {@code container} that holds {@code host}, i.e. the
     * {@code MiuiBatteryMeterView} the glyph is drawn in.
     *
     * <p>Found by walking the live parent chain rather than by class name, for
     * the same reason {@link #batteryContainerOf} matches by name: the host is
     * inflated through a wrapper whose loader chain does not expose SystemUI
     * classes.
     */
    private static View meterIn(View host, View container) {
        View child = host;
        for (ViewParent p = host.getParent(); p != null;
             p = (p instanceof View) ? ((View) p).getParent() : null) {
            if (p == container) {
                return child;
            }
            if (!(p instanceof View)) {
                return null;
            }
            child = (View) p;
        }
        return null;
    }

    /** A registered glyph host living under {@code container}, or null. */
    private static View hostIn(View container) {
        synchronized (HOSTS) {
            for (int i = 0; i < HOSTS.size(); i++) {
                if (isDescendant(HOSTS.get(i).host, container)) {
                    return HOSTS.get(i).host;
                }
            }
        }
        return null;
    }

    private static void hide(Object view) {
        if (view instanceof View) {
            ((View) view).setVisibility(View.GONE);
        }
    }

    /** Resources handle for resolving signal icon entry names. */
    private static android.content.res.Resources resources() {
        final Object container = sStatusIconContainer;
        if (container instanceof View) {
            try {
                return ((View) container).getResources();
            } catch (Throwable ignored) {
                // fall through
            }
        }
        synchronized (HOSTS) {
            if (!HOSTS.isEmpty()) {
                try {
                    return HOSTS.get(0).host.getResources();
                } catch (Throwable ignored) {
                    // fall through
                }
            }
        }
        return null;
    }

    private static void log(XposedModule module, String msg) {
        try {
            module.log(LOG_INFO, TAG, msg);
        } catch (Throwable ignored) {
            // logging must never break hook installation
        }
    }

    /**
     * Logs through the handle captured at install time. Used by callers that run
     * outside the hook callbacks (the settings channel) and so cannot pass the
     * module instance along.
     */
    static void log(int priority, String msg) {
        final XposedModule module = sModule;
        if (module == null) {
            return;
        }
        try {
            module.log(priority, TAG, msg);
        } catch (Throwable ignored) {
            // logging must never break a config change
        }
    }
}
