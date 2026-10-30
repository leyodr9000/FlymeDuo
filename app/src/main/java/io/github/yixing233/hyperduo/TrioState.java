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
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.SystemClock;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.view.View;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Snapshot of everything the trio glyph needs: battery level / charge state, the
 * Wi-Fi and mobile signal levels, and the tint colours MIUI is currently using
 * for this status bar.
 *
 * <p>All battery/tint values live on {@code MiuiBatteryMeterIconView} as public
 * fields, so they are read reflectively; every read degrades to a sane default.
 *
 * <p>Signal levels are learned from the signal icons the Wi-Fi and mobile binders
 * apply (see {@link #noteSignalIcon}). Those arrive as raw resource ids, which are
 * resolved to entry names once a host view 鈥?and therefore a {@code Resources} 鈥?
 * is available.
 *
 * <p>That path carries exactly one mobile level: {@code transformResId} is static
 * and its arguments never name a SIM, so it can only ever describe whichever SIM
 * MIUI is drawing. Reading one level <em>per</em> SIM therefore has to come from
 * telephony directly 鈥?see {@link #sampleSimsIfDue()}.
 */
final class TrioState {

    /** Latest resolved signal levels; shared by every trio host. */
    static volatile int sWifiLevel = -1;
    static volatile int sMobileLevel = -1;

    /**
     * Whether the status bar is currently showing a Wi-Fi indicator at all.
     *
     * <p>The level alone is not enough: MIUI only calls
     * {@code MiuiStatusBarIconViewHelper.transformResId} while it is binding a
     * signal icon, so when Wi-Fi is switched off the last level simply stays
     * behind and the arcs would never disappear. Presence is sampled from the
     * live view tree instead (see {@code TrioHooks.settle}), which is the only
     * source that tracks the indicator going away.
     */
    static volatile boolean sWifiPresent = true;

    /**
     * Whether the charging super island currently has the battery hidden.
     *
     * <p>MIUI reports the island through
     * {@code MiuiBatteryMeterView.updateIslandChanged}, which lands in
     * {@code MiuiStatusBatteryContainer.setIsHideBattery(true)}: the battery
     * keeps its view but loses its measured space, so the native icon row slides
     * right to clear the island. Sampled by the hook on that setter (see
     * TrioHooks) and consumed as one rule everywhere - the glyph hides, the
     * Wi-Fi slot is handed back, the signal reads out of ring - so all three
     * answers come from one flag and cannot disagree mid-island.
     */
    static volatile boolean sIslandHideBattery = false;

    /**
     * Mobile network type label as MIUI itself renders it: "5G", "4G", "5GA",
     * "3G"... Empty when there is nothing to show.
     *
     * <p>Sampled from {@code MobileTypeDrawable.mMobileType} after MIUI's own
     * normalisation has run, so this is exactly the string the stock status bar
     * would have painted. {@code "5G++"} has already been rewritten to "5G" with
     * a separate double-plus flag by the time the hook reads it, which is why the
     * module never has to know about that special case.
     */
    static volatile String sMobileType = "";

    /**
     * The clock string the capsule arrangement draws: "19:41", or "9:41" where
     * the locale's own time format says so.
     *
     * <p>Kept here rather than sampled inside the renderer because the renderer
     * also serves the settings preview, which must not tick - and because
     * something has to repaint the bar when the minute changes, which is the
     * hooked side's job. {@link ClockTicker} owns that.
     *
     * <p>Empty until the first tick, so the capsule's left slot stays blank for
     * the one frame before the clock has ever been formatted rather than drawing
     * a placeholder that would then flicker into the real time.
     */
    static volatile String sClockText = "";

    /**
     * Whether an external power source is attached to the phone at all.
     *
     * <p>Not the same question as {@code status == CHARGING}: once the battery is
     * full MIUI flips the status to {@code FULL} and stops pushing current. The
     * power readout is gated on <em>this</em> flag rather than on the current
     * being positive, so the segment does not blink out of the status bar at the
     * moment the battery tops out.
     */
    static volatile boolean sChargingAttached = false;

    /**
     * The power currently going into the battery, in watts, or {@code 0} when
     * nothing is.
     *
     * <p>The battery's own current times its own voltage, not the charger's
     * negotiated limit - see {@code BatteryPresence.wattsOf}. Never negative: a
     * phone held at 100 % draws a small backward current, and a negative wattage
     * in the status bar reads as a defect rather than as "full".
     *
     * <p>{@link #POWER_UNKNOWN} is a third answer, and it is not the same as
     * zero: zero means the battery is genuinely taking no power, while the
     * sentinel means the platform could not be asked. The readout drops the
     * segment in both cases rather than printing a confident "0.0W".
     */
    static volatile float sChargePowerW = 0f;

    /** No temperature reading has arrived yet; draw nothing rather than "0掳". */
    static final int TEMP_UNKNOWN = Integer.MIN_VALUE;

    /**
     * The platform would not say how much power is going in; draw nothing
     * rather than "0.0W".
     *
     * <p>Deliberately {@code NaN} so that a comparison against it is written
     * {@code !=} and reads as "is a reading". A sentinel of {@code 0} could not
     * be told apart from an honest zero, which is why the power path needed its
     * own constant instead of reusing the temperature one's meaning.
     */
    static final float POWER_UNKNOWN = Float.NaN;

    /** The battery temperature in whole degrees Celsius, or {@link #TEMP_UNKNOWN}. */
    static volatile int sBatteryTempC = TEMP_UNKNOWN;

    /** How many SIM slots the reading covers; SIM 1 and SIM 2. */
    static final int SIM_SLOTS = 2;

    /**
     * Signal level per SIM <em>slot</em> 鈥?index 0 is SIM 1, index 1 is SIM 2 鈥?
     * or {@code -1} where that slot is empty or its level is unknown.
     *
     * <p>Only used by the dual-SIM reading; the single row keeps using the
     * {@code transformResId} level above, which tracks the current data SIM.
     *
     * <p>Length is always {@link #SIM_SLOTS}, so the renderer can read it without
     * a length check.
     *
     * <p>Starts at {@code -1} rather than the zero-filled default: {@code 0} is a
     * real reading (a SIM with no service), and a fresh array must not look like
     * "two SIMs, both readable" before telephony has answered once. Getting that
     * wrong draws two grey rows instead of falling back to the single row.
     */
    static volatile int[] sSlotLevels = { -1, -1 };
    /** The label the 5GA demo substitutes; see {@link Prefs#KEY_DEMO_MOBILE_TYPE}. */
    private static final String DEMO_MOBILE_TYPE = "5GA";

    /** How often the mobile-data toggle may be re-read; the same budget as the SIM poll. */
    private static final long DATA_INTERVAL_MS = 2000L;
    /** The demo's two card levels - one strong, one mid, so the rows read apart. */
    private static final int DEMO_SIM1_LEVEL = 4;
    private static final int DEMO_SIM2_LEVEL = 2;
    private static volatile long sDataDueAt;
    /**
     * Whether mobile data is switched on.
     *
     * <p>Starts true so nothing hides before the first sample: an unknown state must not
     * blank the label, and a module that silently drops a reading is worse than one that
     * shows a stale one for the two seconds it takes to ask.
     */
    private static volatile boolean sDataEnabled = true;

    /** The last mobile-data reading, for the Flyme adapter's stock-icon rules. */
    static boolean sDataEnabledPublic() {
        return sDataEnabled;
    }
    /**
     * Whether the radio currently reports 5G-Advanced.
     *
     * <p>Not taken from the icon Flyme hands us: the stock SystemUI ships
     * {@code bool/config_display_5g_a = false}, so on some builds the 5G-A resource is never
     * requested even when the network is 5G-A. The display info is the value the platform
     * itself derives from the radio, so reading it directly is what lets the module draw
     * 5GA on a phone whose stock label would say 5G.
     */
    static volatile boolean sNrAdvanced;

    /** The current default-data SIM's slot, or {@code -1} when it is unknown. */
    static volatile int sDataSlot = -1;

    /** Telephony app context, captured from the first hooked view. */
    private static volatile Context sContext;
    /** Last tint line logged, so a per-frame call site logs only when it changes. */
    private static volatile String sLastTint;
    /** Next {@link SystemClock#elapsedRealtime} at which telephony may be polled. */
    private static volatile long sSimsDueAt;
    /** How often telephony is polled. Signal strength is not a per-frame value. */
    private static final long SIMS_INTERVAL_MS = 2000L;

    /** Raw signal icon ids seen before a host existed, newest last. */
    private static final List<Integer> PENDING = new ArrayList<>();
    private static final int PENDING_MAX = 8;

    /** Signal icon id -> classification code. Ids are process-stable. */
    private static final java.util.concurrent.ConcurrentHashMap<Integer, Integer> CLASSIFIED =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Not a signal-level icon: leave the last level alone. */
    private static final int C_IGNORE = -1;
    /** Genuine "no signal" marker: clear the dots. */
    private static final int C_CLEAR = -2;
    /** Wi-Fi levels are stored as this base plus 0..3, to share one int channel. */
    private static final int C_WIFI_BASE = 100;

    private static final int DEFAULT_FOREGROUND = 0xFFFFFFFF;

    private static Field fLevel;
    private static Field fCharging;
    private static Field fQuick;
    private static Field fLow;
    private static Field fPowerSave;
    private static Field fPerformance;
    private static Field fUseTint;
    private static Field fTint;
    private static Field fLight;
    private static Field fDark;
    private static Field fIntensity;
    private static boolean fieldsReady;

    // Flyme's battery view keeps the level in a private field the adapter only
    // learns about through onBatteryLevelChanged, so the last reported value is
    // kept here as a fallback for hosts whose own field reads -1.
    private static volatile int sFlymeBatteryLevel = -1;
    private static volatile boolean sFlymeCharging;
    private static volatile boolean sFlymePluggedIn;

    final View host;

    // Battery state of this host.
    int level = -1;
    boolean charging;
    boolean quickCharging;
    boolean low;
    boolean powerSave;
    boolean performanceMode;

    // Tint state of this host.
    boolean useTint;
    int tintColor;
    int lightColor;
    int darkColor;
    float darkIntensity;
    /**
     * Ink last handed to the out-of-ring views under this host, or {@code 0}
     * before the first hand-over.
     *
     * <p>Kept per host rather than in one static: the status bar and the
     * control centre each own a battery container, and the ink is the same for
     * both, so a shared cell would let whichever drew first swallow the change
     * for the other. {@link #foreground()} never returns {@code 0} - it
     * substitutes a default - so the first comparison always fires.
     */
    int outRingInk;

    // Signal levels (shared).
    int wifiLevel = -1;
    int mobileLevel = -1;
    boolean wifiPresent = true;
    boolean islandHideBattery;
    String mobileType = "";
    /** Per-SIM levels by slot; a fresh array each refresh, never mutated in place. */
    int[] slotLevels = { -1, -1 };
    /** How many of {@link #slotLevels} carry a reading. */
    int sims;

    TrioState(View host) {
        this.host = host;
    }

    // ------------------------------------------------------------------ fields

    private static synchronized void ensureFields(Class<?> c) {
        if (fieldsReady) {
            return;
        }
        fLevel = Refl.field(c, "mLevel");
        fCharging = Refl.field(c, "mCharging");
        fQuick = Refl.field(c, "mQuickCharging");
        fLow = Refl.field(c, "mLow");
        fPowerSave = Refl.field(c, "mPowerSave");
        if (fPowerSave == null) {
            // FlymeBatteryMeterView keeps it as mLowPowerMode (set in onPowerSaveChanged).
            fPowerSave = Refl.field(c, "mLowPowerMode");
        }
        fPerformance = Refl.field(c, "mPerformanceMode");
        fUseTint = Refl.field(c, "mUseTint");
        fTint = Refl.field(c, "mTintColor");
        fLight = Refl.field(c, "mLightColor");
        fDark = Refl.field(c, "mDarkColor");
        fIntensity = Refl.field(c, "mDarkIntensity");
        fieldsReady = fLevel != null;
    }

    /** Flyme's battery view reports its state through a callback, not a field. */
    static void noteFlymeBatteryState(int level, boolean pluggedIn, boolean charging) {
        if (level >= 0 && level <= 100) {
            sFlymeBatteryLevel = level;
        }
        sFlymePluggedIn = pluggedIn;
        sFlymeCharging = charging;
    }

    private static boolean isFlymeBatteryView(View host) {
        return host != null
                && "com.flyme.statusbar.battery.FlymeBatteryMeterView".equals(host.getClass().getName());
    }

    private static int systemBatteryLevel(View host) {
        try {
            android.os.BatteryManager battery =
                    (android.os.BatteryManager) host.getContext().getSystemService("batterymanager");
            if (battery == null) {
                return -1;
            }
            int level = battery.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY);
            return (level < 0 || level > 100) ? -1 : level;
        } catch (Throwable t) {
            return -1;
        }
    }

    private static boolean systemIsCharging(View host) {
        try {
            android.os.BatteryManager battery =
                    (android.os.BatteryManager) host.getContext().getSystemService("batterymanager");
            return battery != null && battery.isCharging();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Re-reads every status value from the host. Cheap enough to call in onDraw. */
    void refresh() {
        ensureFields(host.getClass());
        level = Refl.getInt(fLevel, host, level);
        if (isFlymeBatteryView(host) && sFlymeBatteryLevel >= 0) {
            level = sFlymeBatteryLevel;
        }
        if (level < 0) {
            level = systemBatteryLevel(host);
        }
        charging = Refl.getBool(fCharging, host, charging);
        if (!charging && isFlymeBatteryView(host)) {
            // Flyme's view exposes charging as a drawable swap rather than a
            // field, so trust the reported state and the system as well.
            charging = sFlymeCharging || (sFlymePluggedIn && level < 100) || systemIsCharging(host);
        }
        quickCharging = Refl.getBool(fQuick, host, quickCharging);
        low = Refl.getBool(fLow, host, low);
        powerSave = Refl.getBool(fPowerSave, host, powerSave);
        performanceMode = Refl.getBool(fPerformance, host, performanceMode);
        useTint = Refl.getBool(fUseTint, host, useTint);
        tintColor = Refl.getInt(fTint, host, tintColor);
        lightColor = Refl.getInt(fLight, host, lightColor);
        darkColor = Refl.getInt(fDark, host, darkColor);
        darkIntensity = Refl.getFloat(fIntensity, host, darkIntensity);

        resolvePending(resourcesOf(host));

        // One telephony poll is enough for every host, so it is rate-limited here
        // rather than expressed as a background subscription no host owns. It runs
        // before the levels are read so the frame that discovers a change is also
        // the frame that draws it - no extra invalidation round is needed. Polled
        // when the second row is wanted, when the out-of-ring reading needs a
        // level per SIM, or when the icon chain has never answered and the data
        // SIM's own reading is the only thing left to fall back on.
        if (TrioConfig.get().dualSim || TrioConfig.appearance().drawsAnySimRow()
                || sMobileLevel < 0) {
            sampleSimsIfDue();
        }

        wifiLevel = sWifiLevel;
        mobileLevel = sMobileLevel;
        wifiPresent = sWifiPresent;
        islandHideBattery = sIslandHideBattery;
        mobileType = sMobileType;
        // Ask the data toggle first, so the demo below can still override it: the demo exists
        // to inspect the drawing, and burying it behind a switch about the radio would make it
        // untestable on a phone whose data happens to be off.
        sampleDataIfDue();
        if (!sDataEnabled && io.github.yixing233.hyperduo.TrioConfig.get().typeHideOnDataOff) {
            // An empty type is exactly "no ink": the ring's notch closes on its own, the
            // capsule cell falls through to its next candidate, and the out-of-ring label
            // hides with it. One rule, three surfaces - nothing else has to know.
            mobileType = "";
        }
        // The platform knows before the label does: several Flyme builds ship
        // bool/config_display_5g_a = false, so the 5G-A resource is never requested and the
        // icon arrives as plain 5G even on a 5G-A cell. When the display info says the radio
        // is 5G-Advanced and the label we were handed is a plain 5G, draw 5GA - the module
        // already owns that reading, so no stock resource is needed.
        if (sNrAdvanced && "5G".equals(mobileType)
                && io.github.yixing233.hyperduo.TrioConfig.get().typeFollowNrAdvanced) {
            mobileType = DEMO_MOBILE_TYPE;
        }
        // Manual 5GA: show a real 5G label as 5GA. Only a "5G" is upgraded, AFTER the data-off
        // rule above, so the label still appears and disappears exactly like the stock 5G one
        // (Wi-Fi only, 4G, data off -> untouched). Needed because Flyme ports and stock Meizu
        // builds never report OVERRIDE_NETWORK_TYPE_NR_ADVANCED (carrier config has no NR
        // advanced threshold, the RIL reports no bandwidth), so the automatic path cannot fire.
        if (io.github.yixing233.hyperduo.TrioConfig.get().demoMobileType
                && "5G".equals(mobileType)) {
            mobileType = DEMO_MOBILE_TYPE;
        }
        // The three battery values are read straight from their statics by the
        // readout's own refresh path, which runs on the layout pass rather than
        // on refresh(), so there is nothing to mirror them into here.

        int[] slots = sSlotLevels;
        // Demo: pretend a second card answered, so a one-SIM install can check the stacked
        // reading. Display only - the level never reaches telephony, and the real levels
        // return the moment the switch goes off. A card that really is present wins: the
        // demo is a stand-in for a missing one, never a replacement for a real one.
        if (io.github.yixing233.hyperduo.TrioConfig.get().demoDualSim) {
            final int[] faked = new int[slots.length];
            System.arraycopy(slots, 0, faked, 0, slots.length);
            if (faked[0] < 0) {
                faked[0] = DEMO_SIM1_LEVEL;
            }
            if (faked.length > 1 && faked[1] < 0) {
                faked[1] = DEMO_SIM2_LEVEL;
            }
            slots = faked;
        }
        slotLevels = new int[] { slots[0], slots[1] };
        sims = (slots[0] >= 0 ? 1 : 0) + (slots[1] >= 0 ? 1 : 0);

        // Single row, no answer from the icon chain: the row still means "the SIM
        // that carries data", and the sampler resolved exactly which one that is.
        if (mobileLevel < 0 && sDataSlot >= 0 && sDataSlot < SIM_SLOTS
                && slots[sDataSlot] >= 0) {
            mobileLevel = slots[sDataSlot];
        }
    }

    // ------------------------------------------------------------- per-SIM level

    /**
     * Captures the context telephony needs, from a view the module already has.
     *
     * <p>SystemUI is {@code android.uid.systemui} with
     * {@code READ_PRIVILEGED_PHONE_STATE}, and this module runs inside it, so the
     * framework answers these calls without the module's own manifest asking for
     * anything.
     */
    static void attachContext(Context context) {
        if (context == null || sContext != null) {
            return;
        }
        try {
            final Context app = context.getApplicationContext();
            sContext = (app != null) ? app : context;
            WifiPresence.start(sContext);
            // Same context, same reason: the battery broadcast is not routed
            // through the hooked views, so it needs a receiver of its own.
            BatteryPresence.start(sContext);
            // The one reading with no event behind it: the clock is correct until
            // the minute rolls over, so it has to wake itself up.
            ClockTicker.start(sContext);
            // The data switch has no view of its own: it is a setting, and the shade that
            // flips it does not redraw the glyph. Watch it, do not wait to be asked.
            DataPresence.start(sContext);
            // The radio's own answer, independent of whether the stock label code ever asks
            // for it: on builds that hide 5G-A, the icon alone can only ever say 5G.
            NrAdvancedPresence.start(sContext);
        } catch (Throwable ignored) {
            // leave it null; the reading stays on the single-row fallback
        }
    }

    /**
     * Re-reads the per-SIM levels at most once every {@link #SIMS_INTERVAL_MS}.
     *
     * <p>Called from {@code refresh()}, which runs in {@code onDraw}: the guard
     * is what keeps a per-frame call from becoming a per-frame IPC.
     */
    private static void sampleSimsIfDue() {
        final long now = SystemClock.elapsedRealtime();
        if (now < sSimsDueAt) {
            return;
        }
        sSimsDueAt = now + SIMS_INTERVAL_MS;
        sampleSims(sContext);
    }

    /**
     * Re-reads the mobile-data toggle at most once per {@link #DATA_INTERVAL_MS}.
     *
     * <p>{@code mobile_data} is the user-facing master switch, which is the exact question
     * the label was failing to answer: not "is a network attached" but "did I turn data on".
     * Reading the switch rather than the connection keeps the glyph from flickering while the
     * radio is still attaching, and needs no privileged telephony call - the setting is
     * readable by the uid SystemUI already runs as.
     *
     * <p>Failure is silent and keeps the last value: a SecurityException here must not take
     * the drawing down, and an unknown state must not blank the label.
     */
    /**
     * Records the data switch and reports whether it moved.
     *
     * <p>Called from {@link DataPresence}, which watches the settings keys so the reading
     * stays current while nothing is being drawn - the shade case, where the old value used
     * to survive until the next unrelated repaint.
     */
    /** Records the 5G-A flag and reports whether it moved. */
    static boolean setNrAdvanced(boolean advanced) {
        if (sNrAdvanced == advanced) {
            return false;
        }
        sNrAdvanced = advanced;
        return true;
    }

    static boolean setDataEnabled(boolean enabled) {
        if (sDataEnabled == enabled) {
            return false;
        }
        sDataEnabled = enabled;
        return true;
    }

    private static void sampleDataIfDue() {
        final long now = SystemClock.elapsedRealtime();
        if (now < sDataDueAt || sContext == null) {
            return;
        }
        sDataDueAt = now + DATA_INTERVAL_MS;
        // DataPresence owns this value: it watches the settings keys so the answer stays
        // current while nothing is being drawn. This draw-path read is only a safety net in
        // case both the observer and its backstop missed, which keeps the two in step.
        if (setDataEnabled(readDataEnabled())) {
            io.github.yixing233.hyperduo.TrioHooks.log(
                    io.github.yixing233.hyperduo.TrioHooks.LOG_INFO,
                    "mobile data enabled=" + sDataEnabled + " (draw)");
        }
    }

    /**
     * Whether the user's mobile-data switch is on.
     *
     * <p><b>Per-card keys first, the plain one only as a fallback.</b> Measured on this Flyme
     * build by watching every candidate while the switch was flipped by hand: the global
     * {@code mobile_data} key stayed put the whole time, while {@code mobile_data1} /
     * {@code mobile_data2} and the telephony registry's data-enabled flag followed the switch.
     * AOSP writes the plain key, MIUI-style firmwares write the per-card ones, so reading both
     * - per-card when present - is what makes the answer portable.
     *
     * <p>"Any card enabled" rather than "the data card enabled": the switch the user is asking
     * about is the one in the pull-down, which these keys record, and on a dual-SIM phone
     * turning data off turns it off for both.
     */
    static boolean readDataEnabled() {
        if (sContext == null) {
            return sDataEnabled;
        }
        // TelephonyManager first: this is the path Flyme itself takes. Dumping the stock
        // SystemUI found isDataEnabled three times and mobile_data1/2 not once, and the
        // manager answers about the DEFAULT data card, which is the switch in the pull-down.
        try {
            final android.telephony.TelephonyManager tm =
                    sContext.getSystemService(android.telephony.TelephonyManager.class);
            if (tm != null) {
                return tm.isDataEnabled();
            }
        } catch (java.lang.Throwable ignored) {
            // fall through to the keys
        }
        // Keys as the fallback: ALL cards off, never ANY. Measured on a dual-SIM Flyme,
        // turning mobile data off clears only the data card key while the second card keeps
        // its 1. OR-ing them - which this did at first - therefore answered "on" forever and
        // the label never hid. A single-SIM phone cannot show the bug, which is why it
        // survived until a two-card report arrived.
        try {
            final android.content.ContentResolver cr = sContext.getContentResolver();
            final int[] slots = sSlotLevels;
            boolean saw = false;
            boolean allOff = true;
            for (int i = 0; i < SIM_SLOTS; i++) {
                if (slots != null && slots[i] < 0) {
                    continue;
                }
                final int v = android.provider.Settings.Global.getInt(
                        cr, "mobile_data" + (i + 1), -1);
                if (v < 0) {
                    continue;
                }
                saw = true;
                if (v != 0) {
                    allOff = false;
                }
            }
            if (saw) {
                return !allOff;
            }
            return android.provider.Settings.Global.getInt(cr, "mobile_data", 1) != 0;
        } catch (java.lang.Throwable ignored) {
            return sDataEnabled;
        }
    }

    /**
     * Re-reads the per-SIM levels right away, bypassing the frame-rate guard, and
     * reports whether anything changed.
     *
     * <p>Called from the signal-icon hook, which is an event-driven refresh rather
     * than a per-frame one, so the guard is not what protects that path. Repainting
     * only on a real change is what keeps MIUI's own icon churn from becoming a
     * repaint loop.
     */
    static boolean pollSimsNow() {
        if (sContext == null) {
            return false;
        }
        final int[] before = sSlotLevels;
        final int beforeData = sDataSlot;
        sampleSims(sContext);
        final int[] after = sSlotLevels;
        return beforeData != sDataSlot || before[0] != after[0] || before[1] != after[1];
    }

    /**
     * Reads one signal level per SIM slot straight from telephony.
     *
     * <p>Why not the signal icons: {@code transformResId} is static and its
     * arguments name neither a SIM nor a slot, so the icons cannot say which SIM
     * a level belongs to. The framework can, and {@link SubscriptionInfo} is the
     * one place that maps a subscription to its slot 鈥?resolved at run time
     * rather than assumed, because the ids differ per device.
     *
     * <p>Every step is defensive: any missing permission, dead radio or absent
     * subscription leaves the corresponding slot at {@code -1}, and the caller
     * then falls back to a single row rather than drawing empty dots.
     */
    private static void sampleSims(Context context) {
        if (context == null) {
            return;
        }
        final int[] levels = { -1, -1 };
        int dataSlot = -1;
        try {
            final SubscriptionManager subs =
                    (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
            final TelephonyManager tel =
                    (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (subs == null || tel == null) {
                return;
            }
            final int dataSubId = SubscriptionManager.getDefaultDataSubscriptionId();
            final List<SubscriptionInfo> infos = subs.getActiveSubscriptionInfoList();
            if (infos != null) {
                for (int i = 0; i < infos.size(); i++) {
                    final SubscriptionInfo info = infos.get(i);
                    if (info == null) {
                        continue;
                    }
                    final int subId = info.getSubscriptionId();
                    final int slot = info.getSimSlotIndex();
                    if (subId == dataSubId) {
                        dataSlot = slot;
                    }
                    if (slot < 0 || slot >= SIM_SLOTS) {
                        continue;
                    }
                    levels[slot] = levelOf(tel, subId);
                }
            }
        } catch (Throwable ignored) {
            // keep whatever the previous poll found
        }
        // Publish only once the whole reading is consistent, so a frame never
        // pairs SIM 1's level with SIM 2's from a different poll.
        sSlotLevels = levels;
        sDataSlot = dataSlot;
    }

    /**
     * The 0..4 level MIUI draws for one subscription, or {@code -1}.
     *
     * <p>Deliberately <em>not</em> AOSP's {@code SignalStrength.getLevel()}: MIUI
     * labels its bars from its own level and the two disagree. A live Xiaomi 14 on
     * 5G NR reported {@code level = 3} with {@code miuiLevel = 4} on both SIMs
     * while the stock bar drew a full four bars, so a dual row fed by
     * {@code getLevel()} sat one notch below the icon the single row matches.
     */
    private static int levelOf(TelephonyManager tel, int subId) {
        try {
            final TelephonyManager perSim = tel.createForSubscriptionId(subId);
            if (perSim == null) {
                return -1;
            }
            final SignalStrength strength = perSim.getSignalStrength();
            if (strength == null) {
                return -1;
            }
            final int level = miuiLevel(strength);
            return (level < 0 || level > 4) ? -1 : level;
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * MIUI's own signal level, the one its status bar icon is chosen from, or
     * AOSP's where MIUI does not provide it.
     *
     * <p>{@code SignalStrength.getMiuiLevel()} is a MIUI addition absent from the
     * public SDK - even the SDK 37 {@code android.jar} has only
     * {@code getLevel()} - so it is reached reflectively. MIUI's own
     * {@code MobileSignalController.updateTelephony()} makes exactly this call to
     * pick {@code stat_sys_signal_N}, which is why bare {@code getLevel()} can sit
     * a notch below the icon on screen.
     */
    private static int miuiLevel(SignalStrength strength) {
        final Object v = Refl.callByName(strength, "getMiuiLevel");
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        return strength.getLevel();
    }

    private static Resources resourcesOf(View v) {
        try {
            return v.getResources();
        } catch (Throwable t) {
            return null;
        }
    }

    // ------------------------------------------------------------------ colours

    /** The plain icon colour: what MIUI would paint the battery icon in. */
    int foreground() {
        // The style follows the bar, not the system theme: MIUI's own fields say
        // what this bar is drawing on, and they are the same pair its icons use.
        // They are the first and only real answer here; the system's night mode is
        // kept as the last resort for a host whose fields never arrived (a host
        // inflated a moment ago, a bar whose tint never came through).
        // Two sources, and neither can be trusted on its own: the fields lag
        // behind a change of background (they still say "white" while every icon
        // has gone black), and the icons keep their last colour once they are
        // hidden by this module (they still say "black" on a dark launcher).
        // Whichever changed more recently is the one that saw the change.
        // The colour SystemUI last painted its own icons in comes first: it is
        // the colour the user is looking at, and it arrives as the change lands
        // rather than after it. The fields stay as the fallback.
        // This host's own row, not one global cell. Several rows are on screen
        // and they are tinted to opposite colours in the same frame, so a shared
        // cell hands whichever was tinted last to all of them and every glyph
        // comes out the opposite colour to the icons beside it.
        int c = TrioHooks.rowInk(TrioHooks.rowOf(host));
        if (c == 0) {
            c = useTint ? tintColor : (darkIntensity > 0f ? darkColor : lightColor);
        }
        if (c == 0) {
            c = nightMode() ? 0xFFFFFFFF : DEFAULT_FOREGROUND;
        }
        noteTint(c);
        return c;
    }

    /**
     * Records the tint inputs once per change, so the values MIUI is handing over
     * can be read off a device instead of guessed at.
     */
    private void noteTint(int resolved) {
        final String note = "tint: useTint=" + useTint
                + " tint=" + Integer.toHexString(tintColor)
                + " light=" + Integer.toHexString(lightColor)
                + " dark=" + Integer.toHexString(darkColor)
                + " intensity=" + darkIntensity
                + " -> " + Integer.toHexString(resolved);
        // A diagnostic, not a state change: it rides on every foreground() call,
        // and MIUI's intensity arrives with per-frame float jitter, so an
        // unconditional INFO here floods logcat on the draw path (issue #40).
        // Same rule as every other dump in TrioHooks: only when the user asked
        // for verbose logging.
        if (TrioHooks.debugLog() && !note.equals(sLastTint)) {
            sLastTint = note;
            TrioHooks.log(TrioHooks.LOG_INFO, note);
        }
    }

    /** The system's night mode, as the configuration currently reports it. */
    private boolean nightMode() {
        final Context context = sContext;
        if (context == null) {
            return false;
        }
        try {
            return (context.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------ signal levels

    /**
     * Records a signal icon resource id 鈥?the {@code rawResId} the Wi-Fi and
     * mobile binders pass to
     * {@code MiuiStatusBarIconViewHelper.transformResId(int,boolean,boolean)}.
     *
     * <p>Names look like {@code stat_sys_wifi_signal_2} or
     * {@code stat_sys_signal_3}; decorated variants
     * ({@code stat_sys_signal_0_no_voice_darkmode}) still carry the level as the
     * token right after {@code signal}. Ids seen before a {@code Resources} handle
     * exists are buffered and resolved on the next draw.
     */
    static void noteSignalIcon(Resources res, int resId) {
        if (resId == 0) {
            return;
        }
        if (res == null) {
            synchronized (PENDING) {
                if (PENDING.size() >= PENDING_MAX) {
                    PENDING.remove(0);
                }
                PENDING.add(Integer.valueOf(resId));
            }
            return;
        }
        apply(res, resId);
    }

    /**
     * Records Wi-Fi indicator presence and reports whether it changed.
     *
     * <p>Called from the icon container's layout, from the live children: a
     * {@code slot=wifi} child means the indicator is on screen. Reporting the
     * change lets the caller redraw only on the edge, not on every layout pass.
     */
    static boolean setWifiPresent(boolean present) {
        present = WifiPresence.resolve(present, -1);
        if (sWifiPresent == present) {
            return false;
        }
        sWifiPresent = present;
        return true;
    }

    /**
     * Records the mobile network type label and reports whether it changed.
     *
     * <p>Fed from {@code MobileTypeDrawable.measure()} after MIUI has already
     * normalised the string, so whatever the stock status bar would show ("5G",
     * "5GA", "4G") is what lands here. A {@code null} is treated as "no type",
     * which is what the view model holds while the radio is still settling.
     */
    static boolean setMobileType(String type) {
        final String next = type == null ? "" : type;
        if (next.equals(sMobileType)) {
            return false;
        }
        sMobileType = next;
        return true;
    }

    private static void resolvePending(Resources res) {
        if (res == null) {
            return;
        }
        synchronized (PENDING) {
            if (PENDING.isEmpty()) {
                return;
            }
            for (int i = 0; i < PENDING.size(); i++) {
                apply(res, PENDING.get(i).intValue());
            }
            PENDING.clear();
        }
    }

    private static void apply(Resources res, int resId) {
        // Classification is a pure function of the resource id, and this runs on
        // the hot transformResId path (20+ call sites, every signal update), so
        // cache it: one getResourceEntryName per distinct id per process.
        final Integer hit = CLASSIFIED.get(Integer.valueOf(resId));
        final int code = hit != null ? hit.intValue() : classify(res, resId);
        if (code == C_IGNORE) {
            return;
        }
        if (code == C_CLEAR) {
            // Not a level icon but a genuine "no signal" marker: clear the dots
            // rather than leaving the last level lit forever.
            sMobileLevel = 0;
            return;
        }
        if (code >= C_WIFI_BASE) {
            sWifiLevel = code - C_WIFI_BASE;
        } else {
            sMobileLevel = code;
        }
    }

    /**
     * Resolves a signal icon id to a classification code, caching the result.
     * Never returns null.
     */
    private static int classify(Resources res, int resId) {
        final int code = computeCode(res, resId);
        CLASSIFIED.put(Integer.valueOf(resId), Integer.valueOf(code));
        return code;
    }

    private static int computeCode(Resources res, int resId) {
        final String name;
        try {
            name = res.getResourceEntryName(resId);
        } catch (Throwable t) {
            return C_IGNORE;
        }
        if (name == null) {
            return C_IGNORE;
        }
        // Match the primary Wi-Fi entry only. A loose "wifi_signal" substring
        // would also swallow stat_sys_slave_wifi_signal_* and
        // ic_no_internet_wifi_signal_*, which are different indicators.
        final boolean wifi = name.startsWith("stat_sys_wifi_signal");
        if (!wifi && !name.startsWith("stat_sys_signal")) {
            return C_IGNORE;
        }
        final int value = parseLevel(name);
        if (value < 0) {
            // Not a level icon (volte, vonr, data_left, satellite_null, ...):
            // decoration rather than a level change, so keep the last level.
            // The two genuine "no signal" markers are matched exactly, because
            // stat_sys_signal_satellite_null also ends in _null and belongs to
            // the separate satellite indicator.
            if ("stat_sys_signal_null".equals(name)
                    || "stat_sys_signal_flightmode".equals(name)) {
                return C_CLEAR;
            }
            return C_IGNORE;
        }
        return wifi ? C_WIFI_BASE + value : value;
    }

    /** {@code stat_sys_wifi_signal_2} 鈫?2, {@code stat_sys_signal_0_no_voice} 鈫?0. */
    private static int parseLevel(String name) {
        final String[] parts = name.split("_");
        for (int i = 0; i < parts.length - 1; i++) {
            if ("signal".equals(parts[i])) {
                final String n = parts[i + 1];
                if (n.length() == 1 && n.charAt(0) >= '0' && n.charAt(0) <= '9') {
                    return n.charAt(0) - '0';
                }
                return -1;
            }
        }
        return -1;
    }
}
