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

import android.content.SharedPreferences;
import android.os.Bundle;

/**
 * One coherent set of user settings, read from a {@link SharedPreferences}
 * file.
 *
 * <p>This type deliberately has no dependency on the Xposed API: the settings
 * app process reuses it to drive the live preview, and pulling in
 * {@code io.github.libxposed.api} there would mean shipping classes the app's
 * class loader cannot resolve.
 *
 * <p>Every field is public and mutable so the renderer can be handed a
 * snapshot cheaply; instances are published as a whole and never mutated after
 * publication.
 */
public final class TrioSettings {

    public boolean enabled;
    public boolean showWifi;
    public boolean showMobile;
    public boolean showValue;
    public boolean showBolt;
    /**
     * Where the mobile network type is drawn: {@link Prefs#MOBILE_TYPE_OFF},
     * {@link Prefs#MOBILE_TYPE_IN_RING} or {@link Prefs#MOBILE_TYPE_OUT_RING}.
     * Clamped to that range wherever it is read.
     */
    public int mobileTypeMode;
    /** See Prefs.KEY_EXCLUDE_REGIONS: which regions keep Flyme's own battery. */
    public int excludeRegions;
    /**
     * Draws the battery percentage enlarged in the middle of the ring, with the
     * Wi-Fi arcs shrunk into the 12 o'clock notch. While charging the bolt owns
     * the middle and the arcs stay in the notch.
     *
     * <p>Stored under the frozen key {@link Prefs#KEY_VALUE_CENTRED}.
     */
    public boolean valueCentred;
    /**
     * Which three-in-one layout is drawn: {@link Prefs#STYLE_RING},
     * {@link Prefs#STYLE_RECT} or {@link Prefs#STYLE_CAPSULE}. Clamped to that
     * range wherever it is read.
     *
     * <p>All three read the same keys, but they are not interchangeable: the
     * rectangular one ignores {@link #valueCentred} (its value has a fixed place)
     * and reads {@link #arcStroke} as the Wi-Fi arcs' stroke width, which also
     * decides their vertical position; the capsule ignores {@link #valueCentred}
     * too, draws the time inside the glyph - which is what makes it hide the
     * bar's own clock, see {@link TrioAppearance#hidesStatusClock()} - and reads
     * {@link #ringStroke} as the outline's thickness. {@link TrioAppearance} is
     * where those differences are resolved, so nothing else has to know them.
     */
    /** Bottom-bar material; see {@link Prefs#KEY_NAV_STYLE}. */
    public int navStyle;
    /** Bottom-bar label mode; see {@link Prefs#KEY_NAV_LABELS}. */
    public int navLabels;
    public int trioStyle;
    /**
     * The ring layout: for each of the three zones, an ordered list of candidate pieces.
     *
     * <p>Priority order, most wanted first. A zone draws the first entry that has something
     * to show on this frame, so a list is a fallback chain rather than a stack of overlapping
     * ink: with nothing but Wi-Fi and the percentage, [percentage, arcs] draws the percentage
     * while it has a reading and the arcs when it does not.
     *
     * <p>Empty by default - a fresh install starts with every zone empty, because with
     * priority lists there is no single arrangement to ship. The first three indices are the
     * ring's thirds (top, middle, bottom, matching {@code TrioGeometry.ZONE_CY}); the last
     * two are the capsule's right column and its lower half, which the ring simply never
     * reads.
     *
     * <p>Stored as the string {@link Prefs#KEY_RING_LAYOUT}; this is its decoded form.
     * Never null: every entry is a non-null array, possibly empty.
     */
    public int[][] ringZones = Prefs.decodeLayout(Prefs.EMPTY_LAYOUT);

    /**
     * Draws one row of level dots per SIM - SIM 1 on top, SIM 2 below - instead
     * of a single row for the current data SIM.
     *
     * <p>Only ever honoured while no Wi-Fi ink is on screen and no charger is
     * plugged in, because a plugged-in phone already says so with the ring's
     * colour, and one state should read as one reading - see
     * {@link TrioAppearance#dualSimRows(boolean, boolean, int)}.
     */
    public boolean dualSim;
    /**
     * Where the mobile signal reading goes: the glyph's own thirds, or back to MIUI.
     *
     * <p>An int holding one of the {@code Prefs.SIGNAL_*} constants. Out of the ring the
     * module draws no level dots at all and the native icon keeps its place - so this is
     * the same "return it to the system" option the other pieces' position rows offer,
     * which is why the settings screen calls it 环外.
     *
     * <p>It does <em>not</em> say which third a card sits in: that is the ring layout's
     * job, as {@code Prefs.PART_SIM_1} / {@code PART_SIM_2}. Keeping the two apart is what
     * lets "in the ring" stay a single choice while the two cards still each pick a slot.
     */
    public int signalMode;
    /**
     * Draws the module's own stacked reading while the signal is out of the ring.
     *
     * <p>Out of the ring there are two outcomes and the user picks between them: leave the
     * native icon in place (this off), or draw the stacked bars-and-dots reading beside the
     * battery (this on). Inside the ring it is not consulted - the glyph's own rows are the
     * signal there - which is why the settings screen only enables the row out of ring.
     */
    public boolean stackedSignal;
    /**
     * Draws SIM 1's own row of four dots, as a placed component.
     *
     * <p>Independent of {@link #dualSim} and of {@link #showSim2}: the dual
     * switch describes the aggregate meter's two-row shape, while this pair
     * describes the cards themselves, and those are arranged by the user rather
     * than by a rule - see {@link Prefs#KEY_SHOW_SIM_1}.
     */
    public boolean showSim1;
    /** Draws SIM 2's own row of four dots; see {@link #showSim1}. */
    public boolean showSim2;
    /**
     * Draws only the current data SIM in the out-of-ring reading, leaving the other out.
     */
    public boolean dataSimOnly;

    public boolean roleColors;
    public int criticalOnDark;
    public int criticalOnLight;
    public int chargingOnDark;
    public int chargingOnLight;
    public int lowOnDark;
    public int lowOnLight;
    public int lowThreshold;

    public int ringStroke;
    public int arcStroke;
    public int valueSize;
    /** Numeric font weight of the value/type text, 100..900. */
    public int valueWeight;
    /**
     * Size and weight of the time in the capsule's left-hand slot. Independent of
     * {@link #valueSize}/{@link #valueWeight}: the two slots hold different
     * strings and are read differently, so a change made for one must not move the
     * other. Defaults equal the value's, so nothing changes until it is touched.
     */
    public int clockSize;
    public int clockWeight;
    /**
     * Size and weight of the centred network type ("5G", "5GA"). Independent of
     * {@link #valueSize}/{@link #valueWeight}: the type is a label of its own,
     * tuned separately from the digits it shares the ring with.
     */
    public int typeSize;
    /**
     * Font size of the network type when it is drawn outside the ring. A field
     * of its own rather than a reuse of {@link #typeSize}: the two labels live
     * in different spaces (ring design space vs. real status-bar pixels) and are
     * tuned separately.
     */
    public int outTypeSize;
    /**
     * Height of the stacked out-of-ring signal, in dp, resolved against the
     * display's density. A field of its own rather than a reuse of
     * {@link #outTypeSize}: that one describes a glyph drawn on a canvas
     * this module owns, whereas this one is a view whose box has to be measured
     * and reserved in the status bar's layout, so it is tuned on its own scale.
     */
    public int outSignalSize;
    /**
     * The gap the out-of-ring label keeps on each side, in dp. Two fields rather
     * than one because the label's two neighbours differ - the native icon row
     * outside it, the out-of-ring reading (or the battery) inside it - and the
     * shipped spacing tunes the two independently.
     */
    public int outTypeMarginLeft;
    public int outTypeMarginRight;
    /**
     * The gap between the out-of-ring reading and the battery, in dp. A reserved
     * margin, not a position: widening it also pushes the native icon row over
     * by the same amount, so the reading can never end up on top of the icons.
     */
    public int outSignalMargin;
    /**
     * Scale of a trailing "A" in the network type, as a percentage of the main
     * size. 100 means no shrink; the reference "5GA" is 65.
     */
    public int typeSuffixScale;
    /**
     * Size of the in-ring charging bolt, as a percentage of its reference size.
     *
     * <p>Percent rather than dp because the bolt is drawn in the glyph's 120x120 design
     * space and scaled by the arrangement, not by the density: a dp value would have to
     * be converted back into a design-unit factor everywhere it is used. 100 is the size
     * the reference path implies, which is what every install had before this key existed.
     */
    public int boltScale;
    /** Wi-Fi arcs' size, in percent; see {@link Prefs#KEY_WIFI_SCALE}. */
    public int wifiScale;
    /** Drop the network type while Wi-Fi is up; see {@link Prefs#KEY_TYPE_HIDE_ON_WIFI}. */
    public boolean typeHideOnWifi;
    /** While charging the ring centre shows the percentage; see {@link Prefs#KEY_CHARGING_SHOWS_VALUE}. */
    public boolean chargingShowsValue;
    /**
     * Absolute path of the user's font file, or "" for the system face.
     *
     * <p>Never null: the renderer treats null and "" the same, but a null here
     * would also have to be defended in the equals/bundle paths, and emptiness
     * already means "no custom font" without any of that.
     */
    public String fontPath;
    /**
     * Size of the ring's level-dot rows, as a percentage of the bead the stroke implies.
     *
     * <p>Percent for the same reason as {@link #boltScale}: the beads live in the glyph's
     * 120x120 design space, so a dp value would need converting back into design units at
     * every use. One field for the top and the bottom rows - they are the same ink, and the
     * reasoning is on {@link Prefs#KEY_DOT_SCALE}.
     */
    public int dotScale;
    /**
     * The out-of-ring order, nearest the battery first - see {@link Prefs#KEY_OUT_ORDER}.
     *
     * <p>Always {@link Prefs#OUT_SLOT_COUNT} entries: {@link Prefs#decodeOutOrder(String)}
     * normalises whatever was stored, so nothing downstream has to cope with a short array.
     * Order only - which piece survives an overflow is {@link Prefs#OUT_PRIORITY}, a fixed
     * judgement that this field deliberately does not carry.
     */
    public int[] outOrder = Prefs.OUT_DEFAULT_ORDER.clone();
    public int typeWeight;
    public int trackAlpha;

    /**
     * Draws the battery's charging power beside the out-of-ring reading.
     *
     * <p>Follows {@link #outSignalSize}/{@link #outTypeSize} rather than the ring:
     * like them it is a status-bar element whose box must be laid out and
     * reserved, not a glyph drawn inside a canvas this module owns. It has no
     * size setting of its own - the text matches the out-of-ring type, so it
     * never needs a second slider to keep the two readings looking alike.
     *
     * <p>Shown only while a charger is attached; see {@link Prefs#KEY_PERF_POWER}.
     */
    public boolean perfPower;
    /**
     * Draws the battery's temperature beside the out-of-ring reading.
     *
     * <p>The two readouts share one view and one reserved strip, which is why
     * they are two flags rather than a two-entry list: turning both off takes the
     * view away entirely, and turning either on brings back the same view with
     * only the requested segment drawn.
     */
    public boolean perfTemp;
    /** Drops the temperature unless charging; see {@link Prefs#KEY_PERF_TEMP_CHARGING_ONLY}. */
    public boolean perfTempChargingOnly;

    /**
     * Overall size of the two readouts, as a percentage of the size the adaptive
     * fit settles on. 100 leaves that answer alone, which is the shipped state.
     *
     * <p>Applied after the fit and after the {@code PERF_MIN_SIZE_RATIO} floor, so
     * the slider scales whatever the rules produced rather than replacing them:
     * the pair still tracks the icon it stands beside and still tracks
     * {@link #outTypeSize}.
     */
    public int perfScale;
    /**
     * How heavy the two readouts are drawn, on the 100..900 scale the platform's
     * own typeface weights use. 700 is the shipped bold.
     *
     * <p>Not a second reader of {@code typeWeight}: the readout is status-bar text
     * standing next to MIUI's own figures, while {@code typeWeight} also drives
     * the label drawn inside the ring, and the two want different answers. It is
     * also a layout input - the line height is measured from the weight - so
     * changing it re-runs the pair's fit.
     */
    public int perfWeight;
    /**
     * Overall size of the trio glyph, as a percentage of the fit the host box
     * implies. Shrink-only; see {@link Prefs#KEY_GLYPH_SCALE}.
     */
    public int glyphScale;
    /** The capsule's own size correction; see {@link Prefs#KEY_CAPSULE_SCALE}. */
    public int capsuleScale;
    /**
     * Horizontal / vertical nudge of the whole glyph, in density pixels units of
     * dp, sign as screen axes: negative X moves left, negative Y moves up. See
     * {@link Prefs#KEY_GLYPH_OFFSET_X} / {@link Prefs#KEY_GLYPH_OFFSET_Y}.
     */
    public int glyphOffsetX;
    public int glyphOffsetY;
    /** Horizontal shift of the stock icon area itself; see {@link Prefs#KEY_ICON_OFFSET_X}. */
    public int iconOffsetX;
    /**
     * Demo two pretend cards, for a device with one SIM; see
     * {@link Prefs#KEY_DEMO_DUAL_SIM}. Display-only: the levels exist inside the
     * module's own draw path and nowhere else.
     */
    public boolean demoDualSim;
    /** Interior cell nudges, in design units; see Prefs.KEY_CAPSULE_LEFT_DX. */
    public int capsuleLeftDx;
    public int capsuleLeftDy;
    public int capsuleRightDx;
    public int capsuleRightDy;
    /** Extra straight-run length; see {@link Prefs#KEY_CAPSULE_LENGTH}. */
    public int capsuleLength;

    /** Only used by the settings app: the hooked side reads this via {@code TrioConfig}. */
    public boolean debugLog;

    public static TrioSettings defaults() {
        TrioSettings s = new TrioSettings();
        s.enabled = Prefs.DEF_ENABLED;
        s.showWifi = Prefs.DEF_SHOW_WIFI;
        s.showMobile = Prefs.DEF_SHOW_MOBILE;
        s.showValue = Prefs.DEF_SHOW_VALUE;
        s.showBolt = Prefs.DEF_SHOW_BOLT;
        s.mobileTypeMode = Prefs.DEF_MOBILE_TYPE_MODE;
        s.excludeRegions = Prefs.DEF_EXCLUDE_REGIONS;
        s.valueCentred = Prefs.DEF_VALUE_CENTRED;
        s.navStyle = Prefs.DEF_NAV_STYLE;
        s.navLabels = Prefs.DEF_NAV_LABELS;
        s.trioStyle = Prefs.DEF_TRIO_STYLE;
        s.dualSim = Prefs.DEF_DUAL_SIM;
        s.signalMode = Prefs.DEF_SIGNAL_MODE;
        s.stackedSignal = Prefs.DEF_STACKED_SIGNAL;
        s.showSim1 = Prefs.DEF_SHOW_SIM_1;
        s.showSim2 = Prefs.DEF_SHOW_SIM_2;
        s.dataSimOnly = Prefs.DEF_DATA_SIM_ONLY;
        s.perfPower = Prefs.DEF_PERF_POWER;
        s.perfTemp = Prefs.DEF_PERF_TEMP;
        s.perfTempChargingOnly = Prefs.DEF_PERF_TEMP_CHARGING_ONLY;
        s.perfScale = Prefs.DEF_PERF_SCALE;
        s.perfWeight = Prefs.DEF_PERF_WEIGHT;
        s.glyphScale = Prefs.DEF_GLYPH_SCALE;
        s.capsuleScale = Prefs.DEF_CAPSULE_SCALE;
        s.glyphOffsetX = Prefs.DEF_GLYPH_OFFSET;
        s.glyphOffsetY = Prefs.DEF_GLYPH_OFFSET;
        s.iconOffsetX = Prefs.DEF_ICON_OFFSET;
        s.demoDualSim = Prefs.DEF_DEMO_DUAL_SIM;
        s.capsuleLeftDx = Prefs.DEF_CAPSULE_NUDGE;
        s.capsuleLeftDy = Prefs.DEF_CAPSULE_NUDGE;
        s.capsuleRightDx = Prefs.DEF_CAPSULE_NUDGE;
        s.capsuleRightDy = Prefs.DEF_CAPSULE_NUDGE;
        s.capsuleLength = Prefs.DEF_CAPSULE_LENGTH;

        s.roleColors = Prefs.DEF_ROLE_COLORS;
        s.criticalOnDark = Prefs.DEF_COLOR_CRITICAL_ON_DARK;
        s.criticalOnLight = Prefs.DEF_COLOR_CRITICAL_ON_LIGHT;
        s.chargingOnDark = Prefs.DEF_COLOR_CHARGING_ON_DARK;
        s.chargingOnLight = Prefs.DEF_COLOR_CHARGING_ON_LIGHT;
        s.lowOnDark = Prefs.DEF_COLOR_LOW_ON_DARK;
        s.lowOnLight = Prefs.DEF_COLOR_LOW_ON_LIGHT;
        s.lowThreshold = Prefs.DEF_LOW_THRESHOLD;

        s.ringStroke = Prefs.DEF_RING_STROKE;
        s.arcStroke = Prefs.DEF_ARC_STROKE;
        s.valueSize = Prefs.DEF_VALUE_SIZE;
        s.valueWeight = Prefs.DEF_VALUE_WEIGHT;
        s.clockSize = Prefs.DEF_CLOCK_SIZE;
        s.clockWeight = Prefs.DEF_CLOCK_WEIGHT;
        s.typeSize = Prefs.DEF_TYPE_SIZE;
        s.outTypeSize = Prefs.DEF_OUT_TYPE_SIZE_DP;
        s.outSignalSize = Prefs.DEF_OUT_SIGNAL_SIZE;
        s.outTypeMarginLeft = Prefs.DEF_OUT_TYPE_MARGIN_LEFT;
        s.outTypeMarginRight = Prefs.DEF_OUT_TYPE_MARGIN_RIGHT;
        s.outSignalMargin = Prefs.DEF_OUT_SIGNAL_MARGIN;
        s.typeSuffixScale = Prefs.DEF_TYPE_SUFFIX_SCALE;
        s.boltScale = Prefs.DEF_BOLT_SCALE;
        s.wifiScale = Prefs.DEF_WIFI_SCALE;
        s.typeHideOnWifi = Prefs.DEF_TYPE_HIDE_ON_WIFI;
        s.chargingShowsValue = Prefs.DEF_CHARGING_SHOWS_VALUE;
        s.fontPath = Prefs.DEF_FONT_PATH;
        s.dotScale = Prefs.DEF_DOT_SCALE;
        s.outOrder = Prefs.OUT_DEFAULT_ORDER.clone();
        s.typeWeight = Prefs.DEF_TYPE_WEIGHT;
        s.trackAlpha = Prefs.DEF_TRACK_ALPHA;
        s.debugLog = Prefs.DEF_DEBUG_LOG;
        return s;
    }

    /**
     * Reads every key, falling back to the shared defaults. Numeric keys are
     * clamped here rather than at draw time so the renderer can trust them.
     *
     * <p>Same as {@link #from(SharedPreferences, float)} at the density the
     * retired raw-pixel keys were authored on. Kept for callers that have no
     * display to ask - the workbench stubs - which never hold a migrated value
     * anyway.
     */
    public static TrioSettings from(SharedPreferences p) {
        return from(p, Prefs.AUTHORED_DENSITY);
    }

    /**
     * Reads every key, falling back to the shared defaults. Numeric keys are
     * clamped here rather than at draw time so the renderer can trust them.
     *
     * @param density the display's density, needed only to convert a value left
     *        in the retired raw-pixel {@link Prefs#KEY_OUT_TYPE_SIZE} by an
     *        install that predates {@link Prefs#KEY_OUT_TYPE_SIZE_DP}; ignored
     *        once the new key exists
     */
    public static TrioSettings from(SharedPreferences p, float density) {
        return from(p, density, -1);
    }

    /**
     * The same read, for a stated style rather than the stored one.
     *
     * <p>{@code styleOverride} resolves the per-arrangement keys ({@link Prefs#STYLED_KEYS})
     * without changing the stored setting, which is what the settings screen's style tiles
     * need: they preview the OTHER arrangement, and a read that first had to write the stored
     * style would push a bogus transient value at the hooked process.
     *
     * @param styleOverride the arrangement to resolve components for, or a negative value to
     *   use whatever {@link Prefs#KEY_TRIO_STYLE} holds
     */
    public static TrioSettings from(SharedPreferences p, float density, int styleOverride) {
        TrioSettings s = new TrioSettings();
        s.enabled = p.getBoolean(Prefs.KEY_ENABLED, Prefs.DEF_ENABLED);
        // The style first: the component switches below are stored per arrangement, so
        // which key to read depends on it.
        s.navStyle = Prefs.normalizeNavStyle(p.getInt(Prefs.KEY_NAV_STYLE, Prefs.DEF_NAV_STYLE));
        s.navLabels = Prefs.normalizeNavLabels(p.getInt(Prefs.KEY_NAV_LABELS, Prefs.DEF_NAV_LABELS));

        s.trioStyle = styleOverride >= 0
                ? Prefs.normalizeTrioStyle(styleOverride)
                : Prefs.normalizeTrioStyle(
                        p.getInt(Prefs.KEY_TRIO_STYLE, Prefs.DEF_TRIO_STYLE));

        // Every one of these is per-arrangement now: the ring's own key is the historical,
        // unsuffixed name, and the capsule reads its own with a fallback to the ring's value -
        // so an install that upgrades inherits the look it had instead of getting defaults.
        s.showWifi = readStyledBoolean(p, Prefs.KEY_SHOW_WIFI, Prefs.DEF_SHOW_WIFI, s.trioStyle);
        s.showMobile = readStyledBoolean(p, Prefs.KEY_SHOW_MOBILE, Prefs.DEF_SHOW_MOBILE,
                s.trioStyle);
        s.showValue = readStyledBoolean(p, Prefs.KEY_SHOW_VALUE, Prefs.DEF_SHOW_VALUE,
                s.trioStyle);
        s.showBolt = readStyledBoolean(p, Prefs.KEY_SHOW_BOLT, Prefs.DEF_SHOW_BOLT, s.trioStyle);
        s.mobileTypeMode = readMobileTypeMode(p, s.trioStyle);
        s.excludeRegions = Prefs.clamp(p.getInt(Prefs.KEY_EXCLUDE_REGIONS, Prefs.DEF_EXCLUDE_REGIONS), 0, Prefs.MAX_EXCLUDE_REGIONS);
        s.valueCentred = readStyledBoolean(p, Prefs.KEY_VALUE_CENTRED, Prefs.DEF_VALUE_CENTRED,
                s.trioStyle);
        s.ringZones = Prefs.decodeLayout(readRingLayout(p, s.trioStyle));
        s.dualSim = readStyledBoolean(p, Prefs.KEY_DUAL_SIM, Prefs.DEF_DUAL_SIM, s.trioStyle);
        s.signalMode = Prefs.clamp(
                readStyledInt(p, Prefs.KEY_SIGNAL_MODE, Prefs.DEF_SIGNAL_MODE, s.trioStyle),
                Prefs.MIN_SIGNAL_MODE, Prefs.MAX_SIGNAL_MODE);
        s.stackedSignal = readStyledBoolean(p, Prefs.KEY_STACKED_SIGNAL,
                Prefs.DEF_STACKED_SIGNAL, s.trioStyle);
        s.showSim1 = readStyledBoolean(p, Prefs.KEY_SHOW_SIM_1, Prefs.DEF_SHOW_SIM_1,
                s.trioStyle);
        s.showSim2 = readStyledBoolean(p, Prefs.KEY_SHOW_SIM_2, Prefs.DEF_SHOW_SIM_2,
                s.trioStyle);
        // The stored layout may predate the retirement of the aggregate meter. Prefs
        // migrates the id to SIM 1's row when it decodes, but that row also needs its
        // switch - and the switch's default is off - so an upgrading install would see a
        // zone that reserves room and draws nothing. Asking the RAW string (not the decoded
        // layout, which can no longer contain the old id) keeps this to exactly those
        // installs, and never re-enables a switch the user turned off afterwards.
        if (Prefs.mentionsRetiredMeter(readRingLayout(p, s.trioStyle))) {
            s.showSim1 = true;
        }
        s.dataSimOnly = p.getBoolean(Prefs.KEY_DATA_SIM_ONLY, Prefs.DEF_DATA_SIM_ONLY);
        s.perfPower = p.getBoolean(Prefs.KEY_PERF_POWER, Prefs.DEF_PERF_POWER);
        s.perfTemp = p.getBoolean(Prefs.KEY_PERF_TEMP, Prefs.DEF_PERF_TEMP);
        s.perfTempChargingOnly = p.getBoolean(Prefs.KEY_PERF_TEMP_CHARGING_ONLY,
                Prefs.DEF_PERF_TEMP_CHARGING_ONLY);
        s.perfScale = Prefs.clamp(
                p.getInt(Prefs.KEY_PERF_SCALE, Prefs.DEF_PERF_SCALE),
                Prefs.MIN_PERF_SCALE, Prefs.MAX_PERF_SCALE);
        s.perfWeight = Prefs.clamp(
                p.getInt(Prefs.KEY_PERF_WEIGHT, Prefs.DEF_PERF_WEIGHT),
                Prefs.MIN_PERF_WEIGHT, Prefs.MAX_PERF_WEIGHT);
        s.glyphScale = Prefs.clamp(
                p.getInt(Prefs.KEY_GLYPH_SCALE, Prefs.DEF_GLYPH_SCALE),
                Prefs.MIN_GLYPH_SCALE, Prefs.MAX_GLYPH_SCALE);
        s.capsuleScale = Prefs.clamp(
                p.getInt(Prefs.KEY_CAPSULE_SCALE, Prefs.DEF_CAPSULE_SCALE),
                Prefs.MIN_CAPSULE_SCALE, Prefs.MAX_CAPSULE_SCALE);
        s.glyphOffsetX = Prefs.clamp(
                p.getInt(Prefs.KEY_GLYPH_OFFSET_X, Prefs.DEF_GLYPH_OFFSET),
                Prefs.MIN_GLYPH_OFFSET, Prefs.MAX_GLYPH_OFFSET);
        s.iconOffsetX = Prefs.clamp(
                p.getInt(Prefs.KEY_ICON_OFFSET_X, Prefs.DEF_ICON_OFFSET),
                Prefs.MIN_GLYPH_OFFSET, Prefs.MAX_GLYPH_OFFSET);
        s.glyphOffsetY = Prefs.clamp(
                p.getInt(Prefs.KEY_GLYPH_OFFSET_Y, Prefs.DEF_GLYPH_OFFSET),
                Prefs.MIN_GLYPH_OFFSET, Prefs.MAX_GLYPH_OFFSET);
        s.demoDualSim = p.getBoolean(Prefs.KEY_DEMO_DUAL_SIM, Prefs.DEF_DEMO_DUAL_SIM);
        s.capsuleLeftDx = Prefs.clamp(p.getInt(Prefs.KEY_CAPSULE_LEFT_DX,
                Prefs.DEF_CAPSULE_NUDGE), Prefs.MIN_CAPSULE_NUDGE_X,
                Prefs.MAX_CAPSULE_NUDGE_X);
        s.capsuleLeftDy = Prefs.clamp(p.getInt(Prefs.KEY_CAPSULE_LEFT_DY,
                Prefs.DEF_CAPSULE_NUDGE), Prefs.MIN_CAPSULE_NUDGE, Prefs.MAX_CAPSULE_NUDGE);
        // Its own span: the right cell leans right, so its travel is leftwards. Sharing the
        // left cell's range would silently trim every value the slider allows.
        s.capsuleRightDx = Prefs.clamp(p.getInt(Prefs.KEY_CAPSULE_RIGHT_DX,
                Prefs.DEF_CAPSULE_NUDGE), Prefs.MIN_CAPSULE_NUDGE_X_RIGHT,
                Prefs.MAX_CAPSULE_NUDGE_X_RIGHT);
        s.capsuleRightDy = Prefs.clamp(p.getInt(Prefs.KEY_CAPSULE_RIGHT_DY,
                Prefs.DEF_CAPSULE_NUDGE), Prefs.MIN_CAPSULE_NUDGE, Prefs.MAX_CAPSULE_NUDGE);
        s.capsuleLength = Prefs.clamp(p.getInt(Prefs.KEY_CAPSULE_LENGTH,
                Prefs.DEF_CAPSULE_LENGTH), Prefs.MIN_CAPSULE_LENGTH,
                Prefs.MAX_CAPSULE_LENGTH);
        s.roleColors = p.getBoolean(Prefs.KEY_ROLE_COLORS, Prefs.DEF_ROLE_COLORS);
        s.criticalOnDark = p.getInt(Prefs.KEY_COLOR_CRITICAL_ON_DARK, Prefs.DEF_COLOR_CRITICAL_ON_DARK);
        s.criticalOnLight = p.getInt(Prefs.KEY_COLOR_CRITICAL_ON_LIGHT, Prefs.DEF_COLOR_CRITICAL_ON_LIGHT);
        s.chargingOnDark = p.getInt(Prefs.KEY_COLOR_CHARGING_ON_DARK, Prefs.DEF_COLOR_CHARGING_ON_DARK);
        s.chargingOnLight = p.getInt(Prefs.KEY_COLOR_CHARGING_ON_LIGHT, Prefs.DEF_COLOR_CHARGING_ON_LIGHT);
        s.lowOnDark = p.getInt(Prefs.KEY_COLOR_LOW_ON_DARK, Prefs.DEF_COLOR_LOW_ON_DARK);
        s.lowOnLight = p.getInt(Prefs.KEY_COLOR_LOW_ON_LIGHT, Prefs.DEF_COLOR_LOW_ON_LIGHT);
        s.lowThreshold = Prefs.clamp(
                p.getInt(Prefs.KEY_LOW_THRESHOLD, Prefs.DEF_LOW_THRESHOLD),
                Prefs.MIN_LOW_THRESHOLD, Prefs.MAX_LOW_THRESHOLD);

        s.ringStroke = Prefs.clamp(
                p.getInt(Prefs.KEY_RING_STROKE, Prefs.DEF_RING_STROKE),
                Prefs.MIN_RING_STROKE, Prefs.MAX_RING_STROKE);
        s.arcStroke = Prefs.clamp(
                p.getInt(Prefs.KEY_ARC_STROKE, Prefs.DEF_ARC_STROKE),
                Prefs.MIN_ARC_STROKE, Prefs.MAX_ARC_STROKE);
        s.valueSize = Prefs.clamp(
                p.getInt(Prefs.KEY_VALUE_SIZE, Prefs.DEF_VALUE_SIZE),
                Prefs.MIN_VALUE_SIZE, Prefs.maxValueSize(s.trioStyle));
        s.valueWeight = Prefs.clamp(
                p.getInt(Prefs.KEY_VALUE_WEIGHT, Prefs.DEF_VALUE_WEIGHT),
                Prefs.MIN_VALUE_WEIGHT, Prefs.MAX_VALUE_WEIGHT);
        // The clock falls back to the value's OWN size and weight, not to the
        // constants: before these two keys existed the time was drawn at exactly
        // the value's size, so an install that had already moved that slider to 40
        // must keep a 40-sized clock rather than snap back to the 36 default. The
        // constants are the same numbers, but only the fallback makes the upgrade
        // invisible for a user who had changed the old setting.
        s.clockSize = Prefs.clamp(
                p.getInt(Prefs.KEY_CLOCK_SIZE, s.valueSize),
                Prefs.MIN_CLOCK_SIZE, Prefs.maxClockSize(s.trioStyle));
        s.clockWeight = Prefs.clamp(
                p.getInt(Prefs.KEY_CLOCK_WEIGHT, s.valueWeight),
                Prefs.MIN_CLOCK_WEIGHT, Prefs.MAX_CLOCK_WEIGHT);
        s.typeSize = Prefs.clamp(
                p.getInt(Prefs.KEY_TYPE_SIZE, Prefs.DEF_TYPE_SIZE),
                Prefs.MIN_TYPE_SIZE, Prefs.maxTypeSize(s.trioStyle));
        s.outTypeSize = readOutTypeSize(p, density);
        s.outSignalSize = Prefs.clamp(
                p.getInt(Prefs.KEY_OUT_SIGNAL_SIZE, Prefs.DEF_OUT_SIGNAL_SIZE),
                Prefs.MIN_OUT_SIGNAL_SIZE, Prefs.MAX_OUT_SIGNAL_SIZE);
        s.outTypeMarginLeft = Prefs.clamp(
                p.getInt(Prefs.KEY_OUT_TYPE_MARGIN_LEFT, Prefs.DEF_OUT_TYPE_MARGIN_LEFT),
                Prefs.MIN_OUT_TYPE_MARGIN, Prefs.MAX_OUT_TYPE_MARGIN);
        s.outTypeMarginRight = Prefs.clamp(
                p.getInt(Prefs.KEY_OUT_TYPE_MARGIN_RIGHT, Prefs.DEF_OUT_TYPE_MARGIN_RIGHT),
                Prefs.MIN_OUT_TYPE_MARGIN, Prefs.MAX_OUT_TYPE_MARGIN);
        s.outSignalMargin = Prefs.clamp(
                p.getInt(Prefs.KEY_OUT_SIGNAL_MARGIN, Prefs.DEF_OUT_SIGNAL_MARGIN),
                Prefs.MIN_OUT_SIGNAL_MARGIN, Prefs.MAX_OUT_SIGNAL_MARGIN);
        s.typeSuffixScale = Prefs.clamp(
                p.getInt(Prefs.KEY_TYPE_SUFFIX_SCALE, Prefs.DEF_TYPE_SUFFIX_SCALE),
                Prefs.MIN_TYPE_SUFFIX_SCALE, Prefs.MAX_TYPE_SUFFIX_SCALE);
        s.boltScale = Prefs.clamp(
                p.getInt(Prefs.KEY_BOLT_SCALE, Prefs.DEF_BOLT_SCALE),
                Prefs.MIN_BOLT_SCALE, Prefs.MAX_BOLT_SCALE);
        s.wifiScale = Prefs.clamp(
                p.getInt(Prefs.KEY_WIFI_SCALE, Prefs.DEF_WIFI_SCALE),
                Prefs.MIN_WIFI_SCALE, Prefs.MAX_WIFI_SCALE);
        s.typeHideOnWifi = p.getBoolean(Prefs.KEY_TYPE_HIDE_ON_WIFI,
                Prefs.DEF_TYPE_HIDE_ON_WIFI);
        s.chargingShowsValue = p.getBoolean(Prefs.KEY_CHARGING_SHOWS_VALUE,
                Prefs.DEF_CHARGING_SHOWS_VALUE);
        s.fontPath = readFontPath(p.getString(Prefs.KEY_FONT_PATH, Prefs.DEF_FONT_PATH));
        s.dotScale = Prefs.clamp(
                p.getInt(Prefs.KEY_DOT_SCALE, Prefs.DEF_DOT_SCALE),
                Prefs.MIN_DOT_SCALE, Prefs.MAX_DOT_SCALE);
        s.outOrder = Prefs.decodeOutOrder(
                p.getString(Prefs.KEY_OUT_ORDER, Prefs.OUT_DEFAULT_ORDER_STRING));
        s.typeWeight = Prefs.clamp(
                p.getInt(Prefs.KEY_TYPE_WEIGHT, Prefs.DEF_TYPE_WEIGHT),
                Prefs.MIN_TYPE_WEIGHT, Prefs.MAX_TYPE_WEIGHT);
        s.trackAlpha = Prefs.clamp(
                p.getInt(Prefs.KEY_TRACK_ALPHA, Prefs.DEF_TRACK_ALPHA),
                Prefs.MIN_TRACK_ALPHA, Prefs.MAX_TRACK_ALPHA);
        s.debugLog = p.getBoolean(Prefs.KEY_DEBUG_LOG, Prefs.DEF_DEBUG_LOG);
        return s;
    }

    /**
     * Reads the three-way network-type placement, migrating the legacy boolean.
     *
     * <p>The new int key wins whenever it is present. Only while it is absent -
     * i.e. on an install that predates it - is the old
     * {@link Prefs#KEY_SHOW_MOBILE_TYPE} boolean consulted, and an existing
     * "on" maps to {@link Prefs#MOBILE_TYPE_IN_RING} so that user keeps the
     * placement they had. The legacy key is never written again, so this runs at
     * most until the user's first write of the new key.
     */
    private static int readMobileTypeMode(SharedPreferences p, int style) {
        final String own = Prefs.styledKey(Prefs.KEY_MOBILE_TYPE_MODE, style);
        if (p.contains(own)) {
            return Prefs.clamp(
                    p.getInt(own, Prefs.DEF_MOBILE_TYPE_MODE),
                    Prefs.MIN_MOBILE_TYPE_MODE, Prefs.MAX_MOBILE_TYPE_MODE);
        }
        // Not set for this arrangement: inherit the ring's, which may itself still need the
        // legacy boolean consulted below.
        if (style == Prefs.STYLE_CAPSULE && p.contains(Prefs.KEY_MOBILE_TYPE_MODE)) {
            return Prefs.clamp(
                    p.getInt(Prefs.KEY_MOBILE_TYPE_MODE, Prefs.DEF_MOBILE_TYPE_MODE),
                    Prefs.MIN_MOBILE_TYPE_MODE, Prefs.MAX_MOBILE_TYPE_MODE);
        }
        return p.getBoolean(Prefs.KEY_SHOW_MOBILE_TYPE, Prefs.DEF_SHOW_MOBILE_TYPE)
                ? Prefs.MOBILE_TYPE_IN_RING
                : Prefs.MOBILE_TYPE_OFF;
    }

    /**
     * Reads the out-of-ring type size, migrating the retired raw-pixel key.
     *
     * <p>The dp key wins whenever it is present. Only an install that predates
     * it - one that may hold a raw-pixel value in {@link Prefs#KEY_OUT_TYPE_SIZE}
     * - has that value converted once, by dividing by the display density that
     * gives the pixels their physical size. An install that never touched the
     * old slider has no old key at all and simply gets the dp default. The old
     * key is never written again, so like
     * {@link #readMobileTypeMode(SharedPreferences)} this runs at most until the
     * user's first write of the new key.
     *
     * <p>The conversion rounds before clamping so a user's tuned value lands as
     * close as the new range allows: 32px at density 3 reads as 11dp, which the
     * 8..26dp range holds unchanged.
     */
    private static int readOutTypeSize(SharedPreferences p, float density) {
        if (p.contains(Prefs.KEY_OUT_TYPE_SIZE_DP)) {
            return Prefs.clamp(
                    p.getInt(Prefs.KEY_OUT_TYPE_SIZE_DP, Prefs.DEF_OUT_TYPE_SIZE_DP),
                    Prefs.MIN_OUT_TYPE_SIZE_DP, Prefs.MAX_OUT_TYPE_SIZE_DP);
        }
        if (p.contains(Prefs.KEY_OUT_TYPE_SIZE)) {
            final float safeDensity = density > 0f ? density : Prefs.AUTHORED_DENSITY;
            return Prefs.clamp(
                    Math.round(p.getInt(Prefs.KEY_OUT_TYPE_SIZE, Prefs.DEF_OUT_TYPE_SIZE)
                            / safeDensity),
                    Prefs.MIN_OUT_TYPE_SIZE_DP, Prefs.MAX_OUT_TYPE_SIZE_DP);
        }
        // Neither key present: the user never touched this slider. Fall back
        // to the 14dp default.
        return Prefs.clamp(Prefs.DEF_OUT_TYPE_SIZE_DP,
                Prefs.MIN_OUT_TYPE_SIZE_DP, Prefs.MAX_OUT_TYPE_SIZE_DP);
    }

    /**
     * Reads the ring layout, migrating the retired packed encodings.
     *
     * <p>The string key wins whenever it is present. Only an install that predates it has
     * its packed int converted - and the conversion is delegated to
     * {@link Prefs#migrateLayout(String, String)}, which knows the two widths, because
     * reading a three-bit value with four-bit shifts would scatter the user's pieces
     * across the wrong zones.
     *
     * <p>Each stored piece becomes the single entry of its zone's list, so an existing
     * install keeps its arrangement and gains the ability to add candidates behind it.
     */
    private static String readRingLayout(SharedPreferences p, int style) {
        final String own = Prefs.styledKey(Prefs.KEY_RING_LAYOUT, style);
        if (p.contains(own)) {
            return p.getString(own, Prefs.EMPTY_LAYOUT);
        }
        // The capsule has no layout of its own yet: adopt the ring's, so an upgrading install
        // opens on the arrangement it was already showing rather than on a blank capsule.
        if (style == Prefs.STYLE_CAPSULE && p.contains(Prefs.KEY_RING_LAYOUT)) {
            return p.getString(Prefs.KEY_RING_LAYOUT, Prefs.EMPTY_LAYOUT);
        }
        return Prefs.migrateLayout(
                packedAsString(p, Prefs.KEY_RING_SLOTS_V2),
                packedAsString(p, Prefs.KEY_RING_SLOTS));
    }

    /** The broadcast-extras mirror of {@link #readRingLayout(SharedPreferences, int)}. */
    private static String readRingLayout(Bundle bundle, int style) {
        final String own = Prefs.styledKey(Prefs.KEY_RING_LAYOUT, style);
        if (bundle.containsKey(own)) {
            return bundle.getString(own, Prefs.EMPTY_LAYOUT);
        }
        if (style == Prefs.STYLE_CAPSULE && bundle.containsKey(Prefs.KEY_RING_LAYOUT)) {
            return bundle.getString(Prefs.KEY_RING_LAYOUT, Prefs.EMPTY_LAYOUT);
        }
        return Prefs.migrateLayout(
                packedAsString(bundle, Prefs.KEY_RING_SLOTS_V2),
                packedAsString(bundle, Prefs.KEY_RING_SLOTS));
    }

    // ------------------------------------------- per-arrangement component state
    //
    // The switches these read decide WHAT the glyph draws and WHERE. They are stored once per
    // arrangement (see Prefs.STYLED_KEYS) so that setting up the ring cannot empty the capsule
    // or move a piece the other arrangement placed.
    //
    // Each of them falls back to the ring's value when the capsule's key is absent, and only
    // then. That fallback is the whole migration: an install from before the split keeps its
    // values under the ring's historical keys, so the capsule inherits exactly the look the
    // user had, and starts diverging only once they change something in capsule mode.

    /** One per-arrangement boolean, with the ring's value as the capsule's fallback. */
    private static boolean readStyledBoolean(SharedPreferences p, String base, boolean def,
                                             int style) {
        return readStyled(p, base, style, new Reader<Boolean>() {
            @Override
            public Boolean read(String key) {
                return p.getBoolean(key, def);
            }
        });
    }

    /** One per-arrangement int, with the ring's value as the capsule's fallback. */
    private static int readStyledInt(SharedPreferences p, String base, int def, int style) {
        return readStyled(p, base, style, new Reader<Integer>() {
            @Override
            public Integer read(String key) {
                return p.getInt(key, def);
            }
        });
    }

    /** A value read from one storage key - the small seam the two typed readers share. */
    private interface Reader<T> {
        T read(String key);
    }

    /**
     * Reads {@code base} under {@code style}, falling back to the ring's key.
     *
     * <p>Reads through {@link SharedPreferences#contains} rather than substituting a default,
     * because "absent" and "present but equal to the default" are different answers here: an
     * absent capsule key means "inherit", while a present one means "this is what the user
     * chose", even if that happens to be the default.
     */
    private static <T> T readStyled(SharedPreferences p, String base, int style,
                                    Reader<T> reader) {
        final String own = Prefs.styledKey(base, style);
        if (p.contains(own)) {
            return reader.read(own);
        }
        // Ring, or a capsule that has not been edited yet: the historical key is the answer.
        return reader.read(base);
    }

    /**
     * One retired packed key as the string {@link Prefs#migrateLayout} expects, or null.
     *
     * <p>The migration works on strings because its live input is a string; the packed keys
     * are ints, so they are stringified here rather than duplicating the width rules in a
     * second int-typed overload.
     */
    private static String packedAsString(SharedPreferences p, String key) {
        return p.contains(key) ? String.valueOf(p.getInt(key, 0)) : null;
    }

    private static String packedAsString(Bundle bundle, String key) {
        return bundle.containsKey(key) ? String.valueOf(bundle.getInt(key, 0)) : null;
    }

    /** A deep copy of the zone lists; a snapshot is never mutated after publication. */
    public static int[][] copyZones(int[][] zones) {
        final int[][] out = new int[Prefs.ZONE_COUNT][];
        for (int z = 0; z < Prefs.ZONE_COUNT; z++) {
            final int[] src = zones != null && z < zones.length ? zones[z] : null;
            out[z] = src == null ? new int[0] : src.clone();
        }
        return out;
    }

    /**
     * This snapshot's <b>component state</b> for the other arrangement.
     *
     * <p>Every field except the switches and the layout is carried over unchanged, because the
     * rest are properties of the components themselves rather than of the shape - a 40pt digit
     * is 40pt in both. What changes is exactly what {@link Prefs#STYLED_KEYS} lists.
     *
     * <p>Exists for the settings screen's own preview. Now that each arrangement keeps its own
     * pieces, a style tile that drew the CURRENT arrangement's values under the other shape's
     * name would advertise a look that clicking it does not produce: the user would see the
     * capsule's placement on the ring tile and then watch it change when they picked it.
     *
     * @param other the snapshot to take the component state from
     * @param style the arrangement this result is for
     */
    public TrioSettings withComponentsFrom(TrioSettings other, int style) {
        if (other == null) {
            return this;
        }
        final TrioSettings s = copy();
        s.trioStyle = Prefs.normalizeTrioStyle(style);
        s.showWifi = other.showWifi;
        s.showMobile = other.showMobile;
        s.showValue = other.showValue;
        s.showBolt = other.showBolt;
        s.showSim1 = other.showSim1;
        s.showSim2 = other.showSim2;
        s.mobileTypeMode = other.mobileTypeMode;
        s.excludeRegions = other.excludeRegions;
        s.valueCentred = other.valueCentred;
        s.dualSim = other.dualSim;
        s.signalMode = other.signalMode;
        s.stackedSignal = other.stackedSignal;
        s.ringZones = copyZones(other.ringZones);
        return s;
    }

    /**
     * The broadcast-extras mirror of {@link #readMobileTypeMode(SharedPreferences)}.
     *
     * <p>The legacy fallback cannot actually fire here - the settings app only
     * ever puts the new int key into the bundle - but it is kept for symmetry
     * with the stored read, so a bundle built by an older writer still migrates
     * the same way instead of silently resetting to off.
     */
    private static int readMobileTypeMode(Bundle bundle, int style) {
        final String own = Prefs.styledKey(Prefs.KEY_MOBILE_TYPE_MODE, style);
        if (bundle.containsKey(own)) {
            return Prefs.clamp(
                    bundle.getInt(own, Prefs.DEF_MOBILE_TYPE_MODE),
                    Prefs.MIN_MOBILE_TYPE_MODE, Prefs.MAX_MOBILE_TYPE_MODE);
        }
        if (style == Prefs.STYLE_CAPSULE && bundle.containsKey(Prefs.KEY_MOBILE_TYPE_MODE)) {
            return Prefs.clamp(
                    bundle.getInt(Prefs.KEY_MOBILE_TYPE_MODE, Prefs.DEF_MOBILE_TYPE_MODE),
                    Prefs.MIN_MOBILE_TYPE_MODE, Prefs.MAX_MOBILE_TYPE_MODE);
        }
        return bundle.getBoolean(Prefs.KEY_SHOW_MOBILE_TYPE, Prefs.DEF_SHOW_MOBILE_TYPE)
                ? Prefs.MOBILE_TYPE_IN_RING
                : Prefs.MOBILE_TYPE_OFF;
    }

    /** One per-arrangement boolean out of the broadcast extras. */
    private static boolean readStyledBoolean(Bundle bundle, String base, boolean def,
                                             int style) {
        final String own = Prefs.styledKey(base, style);
        if (bundle.containsKey(own)) {
            return bundle.getBoolean(own, def);
        }
        return bundle.getBoolean(base, def);
    }

    /** One per-arrangement int out of the broadcast extras. */
    private static int readStyledInt(Bundle bundle, String base, int def, int style) {
        final String own = Prefs.styledKey(base, style);
        if (bundle.containsKey(own)) {
            return bundle.getInt(own, def);
        }
        return bundle.getInt(base, def);
    }

    /**
     * An independent copy. A snapshot is published as a whole and never mutated
     * afterwards, so updating a single field means copying and republishing.
     */
    public TrioSettings copy() {
        TrioSettings s = new TrioSettings();
        s.enabled = enabled;
        s.showWifi = showWifi;
        s.showMobile = showMobile;
        s.showValue = showValue;
        s.showBolt = showBolt;
        s.mobileTypeMode = mobileTypeMode;
        s.valueCentred = valueCentred;
        s.navStyle = navStyle;
        s.navLabels = navLabels;
        s.trioStyle = trioStyle;
        s.ringZones = copyZones(ringZones);
        s.dualSim = dualSim;
        s.signalMode = signalMode;
        s.stackedSignal = stackedSignal;
        s.showSim1 = showSim1;
        s.showSim2 = showSim2;
        s.dataSimOnly = dataSimOnly;
        s.perfPower = perfPower;
        s.perfTemp = perfTemp;
        s.perfTempChargingOnly = perfTempChargingOnly;
        s.perfScale = perfScale;
        s.perfWeight = perfWeight;
        s.glyphScale = glyphScale;
        s.capsuleScale = capsuleScale;
        s.glyphOffsetX = glyphOffsetX;
        s.glyphOffsetY = glyphOffsetY;
        s.iconOffsetX = iconOffsetX;
        s.demoDualSim = demoDualSim;
        s.capsuleLeftDx = capsuleLeftDx;
        s.capsuleLeftDy = capsuleLeftDy;
        s.capsuleRightDx = capsuleRightDx;
        s.capsuleRightDy = capsuleRightDy;
        s.capsuleLength = capsuleLength;

        s.roleColors = roleColors;
        s.criticalOnDark = criticalOnDark;
        s.criticalOnLight = criticalOnLight;
        s.chargingOnDark = chargingOnDark;
        s.chargingOnLight = chargingOnLight;
        s.lowOnDark = lowOnDark;
        s.lowOnLight = lowOnLight;
        s.lowThreshold = lowThreshold;

        s.ringStroke = ringStroke;
        s.arcStroke = arcStroke;
        s.valueSize = valueSize;
        s.valueWeight = valueWeight;
        s.clockSize = clockSize;
        s.clockWeight = clockWeight;
        s.typeSize = typeSize;
        s.outTypeSize = outTypeSize;
        s.outSignalSize = outSignalSize;
        s.outTypeMarginLeft = outTypeMarginLeft;
        s.outTypeMarginRight = outTypeMarginRight;
        s.outSignalMargin = outSignalMargin;
        s.typeSuffixScale = typeSuffixScale;
        s.boltScale = boltScale;
        s.wifiScale = wifiScale;
        s.typeHideOnWifi = typeHideOnWifi;
        s.chargingShowsValue = chargingShowsValue;
        s.fontPath = fontPath;
        s.dotScale = dotScale;
        s.outOrder = outOrder == null ? Prefs.OUT_DEFAULT_ORDER.clone() : outOrder.clone();
        s.typeWeight = typeWeight;
        s.trackAlpha = trackAlpha;

        s.debugLog = debugLog;
        return s;
    }

    /**
     * Copies the one field named by {@code key} out of {@code src}.
     *
     * <p>The framework's remote map is only guaranteed to be fresh for the key it
     * just reported: the daemon merges a diff into the map and then fires one
     * change callback per key in that diff, so a full re-read here would take
     * stale values for every key whose diff never arrived. That is exactly how a
     * value published by another path can be silently reverted by the next
     * unrelated setting change, which is why only the reported key is applied and
     * the rest of an already-correct snapshot is left alone.
     *
     * @return true when the key maps to a field of this type
     */
    public boolean applyKeyFrom(TrioSettings src, String key) {
        if (src == null || key == null) {
            return false;
        }
        // The per-arrangement keys arrive suffixed when a capsule value changed, and the field
        // they write is the same one the unsuffixed key writes: the snapshot holds the CURRENT
        // arrangement's values, so "show_wifi@capsule changed" and "show_wifi changed" are the
        // same edit to whichever style is loaded.
        if (Prefs.isCapsuleKey(key)) {
            key = Prefs.styledBase(key);
        }
        switch (key) {
            case Prefs.KEY_ENABLED: enabled = src.enabled; return true;
            case Prefs.KEY_EXCLUDE_REGIONS: excludeRegions = src.excludeRegions; return true;
            case Prefs.KEY_SHOW_WIFI: showWifi = src.showWifi; return true;
            case Prefs.KEY_SHOW_MOBILE: showMobile = src.showMobile; return true;
            case Prefs.KEY_SHOW_VALUE: showValue = src.showValue; return true;
            case Prefs.KEY_SHOW_BOLT: showBolt = src.showBolt; return true;
            case Prefs.KEY_MOBILE_TYPE_MODE: mobileTypeMode = src.mobileTypeMode; return true;
            case Prefs.KEY_VALUE_CENTRED: valueCentred = src.valueCentred; return true;
            case Prefs.KEY_NAV_STYLE: navStyle = Prefs.normalizeNavStyle(src.navStyle); return true;
            case Prefs.KEY_NAV_LABELS: navLabels = Prefs.normalizeNavLabels(src.navLabels); return true;
            case Prefs.KEY_TRIO_STYLE: trioStyle = Prefs.normalizeTrioStyle(src.trioStyle); return true;
            case Prefs.KEY_RING_LAYOUT: ringZones = copyZones(src.ringZones); return true;
            case Prefs.KEY_DUAL_SIM: dualSim = src.dualSim; return true;
            case Prefs.KEY_SIGNAL_MODE: signalMode = src.signalMode; return true;
            case Prefs.KEY_STACKED_SIGNAL: stackedSignal = src.stackedSignal; return true;
            case Prefs.KEY_SHOW_SIM_1: showSim1 = src.showSim1; return true;
            case Prefs.KEY_SHOW_SIM_2: showSim2 = src.showSim2; return true;
            case Prefs.KEY_DATA_SIM_ONLY: dataSimOnly = src.dataSimOnly; return true;
            case Prefs.KEY_PERF_POWER: perfPower = src.perfPower; return true;
            case Prefs.KEY_PERF_TEMP: perfTemp = src.perfTemp; return true;
            case Prefs.KEY_PERF_TEMP_CHARGING_ONLY: perfTempChargingOnly = src.perfTempChargingOnly; return true;
            case Prefs.KEY_PERF_SCALE: perfScale = src.perfScale; return true;
        case Prefs.KEY_PERF_WEIGHT: perfWeight = src.perfWeight; return true;
            case Prefs.KEY_GLYPH_SCALE: glyphScale = src.glyphScale; return true;
            case Prefs.KEY_CAPSULE_SCALE: capsuleScale = src.capsuleScale; return true;
            case Prefs.KEY_GLYPH_OFFSET_X: glyphOffsetX = src.glyphOffsetX; return true;
            case Prefs.KEY_ICON_OFFSET_X: iconOffsetX = src.iconOffsetX; return true;
            case Prefs.KEY_GLYPH_OFFSET_Y: glyphOffsetY = src.glyphOffsetY; return true;
            case Prefs.KEY_DEMO_DUAL_SIM: demoDualSim = src.demoDualSim; return true;
            case Prefs.KEY_CAPSULE_LEFT_DX: capsuleLeftDx = src.capsuleLeftDx; return true;
            case Prefs.KEY_CAPSULE_LEFT_DY: capsuleLeftDy = src.capsuleLeftDy; return true;
            case Prefs.KEY_CAPSULE_RIGHT_DX: capsuleRightDx = src.capsuleRightDx; return true;
            case Prefs.KEY_CAPSULE_RIGHT_DY: capsuleRightDy = src.capsuleRightDy; return true;
            case Prefs.KEY_CAPSULE_LENGTH: capsuleLength = src.capsuleLength; return true;
            case Prefs.KEY_ROLE_COLORS: roleColors = src.roleColors; return true;
            case Prefs.KEY_COLOR_CRITICAL_ON_DARK: criticalOnDark = src.criticalOnDark; return true;
            case Prefs.KEY_COLOR_CRITICAL_ON_LIGHT: criticalOnLight = src.criticalOnLight; return true;
            case Prefs.KEY_COLOR_CHARGING_ON_DARK: chargingOnDark = src.chargingOnDark; return true;
            case Prefs.KEY_COLOR_CHARGING_ON_LIGHT: chargingOnLight = src.chargingOnLight; return true;
            case Prefs.KEY_COLOR_LOW_ON_DARK: lowOnDark = src.lowOnDark; return true;
            case Prefs.KEY_COLOR_LOW_ON_LIGHT: lowOnLight = src.lowOnLight; return true;
            case Prefs.KEY_LOW_THRESHOLD: lowThreshold = src.lowThreshold; return true;
            case Prefs.KEY_RING_STROKE: ringStroke = src.ringStroke; return true;
            case Prefs.KEY_ARC_STROKE: arcStroke = src.arcStroke; return true;
            case Prefs.KEY_VALUE_SIZE: valueSize = src.valueSize; return true;
            case Prefs.KEY_VALUE_WEIGHT: valueWeight = src.valueWeight; return true;
            case Prefs.KEY_CLOCK_SIZE: clockSize = src.clockSize; return true;
            case Prefs.KEY_CLOCK_WEIGHT: clockWeight = src.clockWeight; return true;
            case Prefs.KEY_TYPE_SIZE: typeSize = src.typeSize; return true;
            case Prefs.KEY_OUT_TYPE_SIZE_DP: outTypeSize = src.outTypeSize; return true;
            case Prefs.KEY_OUT_SIGNAL_SIZE: outSignalSize = src.outSignalSize; return true;
            case Prefs.KEY_OUT_TYPE_MARGIN_LEFT: outTypeMarginLeft = src.outTypeMarginLeft; return true;
            case Prefs.KEY_OUT_TYPE_MARGIN_RIGHT: outTypeMarginRight = src.outTypeMarginRight; return true;
            case Prefs.KEY_OUT_SIGNAL_MARGIN: outSignalMargin = src.outSignalMargin; return true;
            case Prefs.KEY_TYPE_SUFFIX_SCALE: typeSuffixScale = src.typeSuffixScale; return true;
            case Prefs.KEY_BOLT_SCALE: boltScale = src.boltScale; return true;
            case Prefs.KEY_WIFI_SCALE: wifiScale = src.wifiScale; return true;
            case Prefs.KEY_TYPE_HIDE_ON_WIFI: typeHideOnWifi = src.typeHideOnWifi; return true;
            case Prefs.KEY_CHARGING_SHOWS_VALUE: chargingShowsValue = src.chargingShowsValue; return true;
            case Prefs.KEY_FONT_PATH: fontPath = src.fontPath; return true;
            case Prefs.KEY_DOT_SCALE: dotScale = src.dotScale; return true;
            case Prefs.KEY_OUT_ORDER:
                outOrder = src.outOrder == null
                        ? Prefs.OUT_DEFAULT_ORDER.clone() : src.outOrder.clone();
                return true;
            case Prefs.KEY_TYPE_WEIGHT: typeWeight = src.typeWeight; return true;
            case Prefs.KEY_TRACK_ALPHA: trackAlpha = src.trackAlpha; return true;
            case Prefs.KEY_DEBUG_LOG: debugLog = src.debugLog; return true;
            default: return false;
        }
    }

    /**
     * Reads a snapshot delivered as broadcast extras, falling back to the same
     * defaults as {@link #from(SharedPreferences)}. Numeric values are clamped
     * identically, because the extras come from another process and are just as
     * untrusted as the stored file.
     *
     * <p>{@code bundle} may be null or partial; every key falls back on its own.
     */
    public static TrioSettings fromBundle(Bundle bundle) {
        TrioSettings s = new TrioSettings();
        if (bundle == null) {
            return defaults();
        }
        s.enabled = bundle.getBoolean(Prefs.KEY_ENABLED, Prefs.DEF_ENABLED);
        // The scope switch travels with the snapshot like every other setting. It
        // was missing here while the settings screen already wrote it, which made
        // the row look live and change nothing until the next reboot.
        s.excludeRegions = Prefs.clamp(
                bundle.getInt(Prefs.KEY_EXCLUDE_REGIONS, Prefs.DEF_EXCLUDE_REGIONS),
                0, Prefs.MAX_EXCLUDE_REGIONS);
        // The style first, for the same reason as the stored read: the component switches below
        // are per arrangement.
        s.navStyle = Prefs.normalizeNavStyle(bundle.getInt(Prefs.KEY_NAV_STYLE, Prefs.DEF_NAV_STYLE));
        s.navLabels = Prefs.normalizeNavLabels(bundle.getInt(Prefs.KEY_NAV_LABELS, Prefs.DEF_NAV_LABELS));
        s.trioStyle = Prefs.normalizeTrioStyle(bundle.getInt(Prefs.KEY_TRIO_STYLE, Prefs.DEF_TRIO_STYLE));

        s.showWifi = readStyledBoolean(bundle, Prefs.KEY_SHOW_WIFI, Prefs.DEF_SHOW_WIFI,
                s.trioStyle);
        s.showMobile = readStyledBoolean(bundle, Prefs.KEY_SHOW_MOBILE, Prefs.DEF_SHOW_MOBILE,
                s.trioStyle);
        s.showValue = readStyledBoolean(bundle, Prefs.KEY_SHOW_VALUE, Prefs.DEF_SHOW_VALUE,
                s.trioStyle);
        s.showBolt = readStyledBoolean(bundle, Prefs.KEY_SHOW_BOLT, Prefs.DEF_SHOW_BOLT,
                s.trioStyle);
        s.mobileTypeMode = readMobileTypeMode(bundle, s.trioStyle);
        s.valueCentred = readStyledBoolean(bundle, Prefs.KEY_VALUE_CENTRED,
                Prefs.DEF_VALUE_CENTRED, s.trioStyle);
        s.ringZones = Prefs.decodeLayout(readRingLayout(bundle, s.trioStyle));
        s.dualSim = readStyledBoolean(bundle, Prefs.KEY_DUAL_SIM, Prefs.DEF_DUAL_SIM,
                s.trioStyle);
        s.signalMode = Prefs.clamp(
                readStyledInt(bundle, Prefs.KEY_SIGNAL_MODE, Prefs.DEF_SIGNAL_MODE, s.trioStyle),
                Prefs.MIN_SIGNAL_MODE, Prefs.MAX_SIGNAL_MODE);
        s.stackedSignal = readStyledBoolean(bundle, Prefs.KEY_STACKED_SIGNAL,
                Prefs.DEF_STACKED_SIGNAL, s.trioStyle);
        s.showSim1 = readStyledBoolean(bundle, Prefs.KEY_SHOW_SIM_1, Prefs.DEF_SHOW_SIM_1,
                s.trioStyle);
        s.showSim2 = readStyledBoolean(bundle, Prefs.KEY_SHOW_SIM_2, Prefs.DEF_SHOW_SIM_2,
                s.trioStyle);
        s.dataSimOnly = bundle.getBoolean(Prefs.KEY_DATA_SIM_ONLY, Prefs.DEF_DATA_SIM_ONLY);
        s.perfPower = bundle.getBoolean(Prefs.KEY_PERF_POWER, Prefs.DEF_PERF_POWER);
        s.perfTemp = bundle.getBoolean(Prefs.KEY_PERF_TEMP, Prefs.DEF_PERF_TEMP);
        s.perfTempChargingOnly = bundle.getBoolean(
                Prefs.KEY_PERF_TEMP_CHARGING_ONLY, Prefs.DEF_PERF_TEMP_CHARGING_ONLY);
        s.perfScale = Prefs.clamp(
                bundle.getInt(Prefs.KEY_PERF_SCALE, Prefs.DEF_PERF_SCALE),
                Prefs.MIN_PERF_SCALE, Prefs.MAX_PERF_SCALE);
        s.perfWeight = Prefs.clamp(
                bundle.getInt(Prefs.KEY_PERF_WEIGHT, Prefs.DEF_PERF_WEIGHT),
                Prefs.MIN_PERF_WEIGHT, Prefs.MAX_PERF_WEIGHT);
        s.glyphScale = Prefs.clamp(
                bundle.getInt(Prefs.KEY_GLYPH_SCALE, Prefs.DEF_GLYPH_SCALE),
                Prefs.MIN_GLYPH_SCALE, Prefs.MAX_GLYPH_SCALE);
        s.capsuleScale = Prefs.clamp(
                bundle.getInt(Prefs.KEY_CAPSULE_SCALE, Prefs.DEF_CAPSULE_SCALE),
                Prefs.MIN_CAPSULE_SCALE, Prefs.MAX_CAPSULE_SCALE);
        s.glyphOffsetX = Prefs.clamp(
                bundle.getInt(Prefs.KEY_GLYPH_OFFSET_X, Prefs.DEF_GLYPH_OFFSET),
                Prefs.MIN_GLYPH_OFFSET, Prefs.MAX_GLYPH_OFFSET);
        s.iconOffsetX = Prefs.clamp(
                bundle.getInt(Prefs.KEY_ICON_OFFSET_X, Prefs.DEF_ICON_OFFSET),
                Prefs.MIN_GLYPH_OFFSET, Prefs.MAX_GLYPH_OFFSET);
        s.glyphOffsetY = Prefs.clamp(
                bundle.getInt(Prefs.KEY_GLYPH_OFFSET_Y, Prefs.DEF_GLYPH_OFFSET),
                Prefs.MIN_GLYPH_OFFSET, Prefs.MAX_GLYPH_OFFSET);
        s.demoDualSim = bundle.getBoolean(Prefs.KEY_DEMO_DUAL_SIM, Prefs.DEF_DEMO_DUAL_SIM);
        s.capsuleLeftDx = Prefs.clamp(bundle.getInt(Prefs.KEY_CAPSULE_LEFT_DX,
                Prefs.DEF_CAPSULE_NUDGE), Prefs.MIN_CAPSULE_NUDGE_X,
                Prefs.MAX_CAPSULE_NUDGE_X);
        s.capsuleLeftDy = Prefs.clamp(bundle.getInt(Prefs.KEY_CAPSULE_LEFT_DY,
                Prefs.DEF_CAPSULE_NUDGE), Prefs.MIN_CAPSULE_NUDGE, Prefs.MAX_CAPSULE_NUDGE);
        s.capsuleRightDx = Prefs.clamp(bundle.getInt(Prefs.KEY_CAPSULE_RIGHT_DX,
                Prefs.DEF_CAPSULE_NUDGE), Prefs.MIN_CAPSULE_NUDGE_X_RIGHT,
                Prefs.MAX_CAPSULE_NUDGE_X_RIGHT);
        s.capsuleRightDy = Prefs.clamp(bundle.getInt(Prefs.KEY_CAPSULE_RIGHT_DY,
                Prefs.DEF_CAPSULE_NUDGE), Prefs.MIN_CAPSULE_NUDGE, Prefs.MAX_CAPSULE_NUDGE);
        s.capsuleLength = Prefs.clamp(bundle.getInt(Prefs.KEY_CAPSULE_LENGTH,
                Prefs.DEF_CAPSULE_LENGTH), Prefs.MIN_CAPSULE_LENGTH,
                Prefs.MAX_CAPSULE_LENGTH);
        s.roleColors = bundle.getBoolean(Prefs.KEY_ROLE_COLORS, Prefs.DEF_ROLE_COLORS);
        s.criticalOnDark = bundle.getInt(Prefs.KEY_COLOR_CRITICAL_ON_DARK, Prefs.DEF_COLOR_CRITICAL_ON_DARK);
        s.criticalOnLight = bundle.getInt(Prefs.KEY_COLOR_CRITICAL_ON_LIGHT, Prefs.DEF_COLOR_CRITICAL_ON_LIGHT);
        s.chargingOnDark = bundle.getInt(Prefs.KEY_COLOR_CHARGING_ON_DARK, Prefs.DEF_COLOR_CHARGING_ON_DARK);
        s.chargingOnLight = bundle.getInt(Prefs.KEY_COLOR_CHARGING_ON_LIGHT, Prefs.DEF_COLOR_CHARGING_ON_LIGHT);
        s.lowOnDark = bundle.getInt(Prefs.KEY_COLOR_LOW_ON_DARK, Prefs.DEF_COLOR_LOW_ON_DARK);
        s.lowOnLight = bundle.getInt(Prefs.KEY_COLOR_LOW_ON_LIGHT, Prefs.DEF_COLOR_LOW_ON_LIGHT);
        s.lowThreshold = Prefs.clamp(
                bundle.getInt(Prefs.KEY_LOW_THRESHOLD, Prefs.DEF_LOW_THRESHOLD),
                Prefs.MIN_LOW_THRESHOLD, Prefs.MAX_LOW_THRESHOLD);

        s.ringStroke = Prefs.clamp(
                bundle.getInt(Prefs.KEY_RING_STROKE, Prefs.DEF_RING_STROKE),
                Prefs.MIN_RING_STROKE, Prefs.MAX_RING_STROKE);
        s.arcStroke = Prefs.clamp(
                bundle.getInt(Prefs.KEY_ARC_STROKE, Prefs.DEF_ARC_STROKE),
                Prefs.MIN_ARC_STROKE, Prefs.MAX_ARC_STROKE);
        s.valueSize = Prefs.clamp(
                bundle.getInt(Prefs.KEY_VALUE_SIZE, Prefs.DEF_VALUE_SIZE),
                Prefs.MIN_VALUE_SIZE, Prefs.maxValueSize(s.trioStyle));
        s.valueWeight = Prefs.clamp(
                bundle.getInt(Prefs.KEY_VALUE_WEIGHT, Prefs.DEF_VALUE_WEIGHT),
                Prefs.MIN_VALUE_WEIGHT, Prefs.MAX_VALUE_WEIGHT);
        // Falls back to the value's own, as in from(SharedPreferences): a bundle
        // that predates these keys - or a sender that simply omits them - keeps the
        // shared-size look rather than snapping the time to a constant.
        s.clockSize = Prefs.clamp(
                bundle.getInt(Prefs.KEY_CLOCK_SIZE, s.valueSize),
                Prefs.MIN_CLOCK_SIZE, Prefs.maxClockSize(s.trioStyle));
        s.clockWeight = Prefs.clamp(
                bundle.getInt(Prefs.KEY_CLOCK_WEIGHT, s.valueWeight),
                Prefs.MIN_CLOCK_WEIGHT, Prefs.MAX_CLOCK_WEIGHT);
        s.typeSize = Prefs.clamp(
                bundle.getInt(Prefs.KEY_TYPE_SIZE, Prefs.DEF_TYPE_SIZE),
                Prefs.MIN_TYPE_SIZE, Prefs.maxTypeSize(s.trioStyle));
        s.outTypeSize = Prefs.clamp(
                bundle.getInt(Prefs.KEY_OUT_TYPE_SIZE_DP, Prefs.DEF_OUT_TYPE_SIZE_DP),
                Prefs.MIN_OUT_TYPE_SIZE_DP, Prefs.MAX_OUT_TYPE_SIZE_DP);
        s.outSignalSize = Prefs.clamp(
                bundle.getInt(Prefs.KEY_OUT_SIGNAL_SIZE, Prefs.DEF_OUT_SIGNAL_SIZE),
                Prefs.MIN_OUT_SIGNAL_SIZE, Prefs.MAX_OUT_SIGNAL_SIZE);
        s.outTypeMarginLeft = Prefs.clamp(
                bundle.getInt(Prefs.KEY_OUT_TYPE_MARGIN_LEFT, Prefs.DEF_OUT_TYPE_MARGIN_LEFT),
                Prefs.MIN_OUT_TYPE_MARGIN, Prefs.MAX_OUT_TYPE_MARGIN);
        s.outTypeMarginRight = Prefs.clamp(
                bundle.getInt(Prefs.KEY_OUT_TYPE_MARGIN_RIGHT, Prefs.DEF_OUT_TYPE_MARGIN_RIGHT),
                Prefs.MIN_OUT_TYPE_MARGIN, Prefs.MAX_OUT_TYPE_MARGIN);
        s.outSignalMargin = Prefs.clamp(
                bundle.getInt(Prefs.KEY_OUT_SIGNAL_MARGIN, Prefs.DEF_OUT_SIGNAL_MARGIN),
                Prefs.MIN_OUT_SIGNAL_MARGIN, Prefs.MAX_OUT_SIGNAL_MARGIN);
        s.typeSuffixScale = Prefs.clamp(
                bundle.getInt(Prefs.KEY_TYPE_SUFFIX_SCALE, Prefs.DEF_TYPE_SUFFIX_SCALE),
                Prefs.MIN_TYPE_SUFFIX_SCALE, Prefs.MAX_TYPE_SUFFIX_SCALE);
        s.boltScale = Prefs.clamp(
                bundle.getInt(Prefs.KEY_BOLT_SCALE, Prefs.DEF_BOLT_SCALE),
                Prefs.MIN_BOLT_SCALE, Prefs.MAX_BOLT_SCALE);
        s.wifiScale = Prefs.clamp(
                bundle.getInt(Prefs.KEY_WIFI_SCALE, Prefs.DEF_WIFI_SCALE),
                Prefs.MIN_WIFI_SCALE, Prefs.MAX_WIFI_SCALE);
        s.typeHideOnWifi = bundle.getBoolean(Prefs.KEY_TYPE_HIDE_ON_WIFI,
                Prefs.DEF_TYPE_HIDE_ON_WIFI);
        s.chargingShowsValue = bundle.getBoolean(Prefs.KEY_CHARGING_SHOWS_VALUE,
                Prefs.DEF_CHARGING_SHOWS_VALUE);
        s.fontPath = readFontPath(bundle.getString(Prefs.KEY_FONT_PATH, Prefs.DEF_FONT_PATH));
        s.dotScale = Prefs.clamp(
                bundle.getInt(Prefs.KEY_DOT_SCALE, Prefs.DEF_DOT_SCALE),
                Prefs.MIN_DOT_SCALE, Prefs.MAX_DOT_SCALE);
        s.outOrder = Prefs.decodeOutOrder(
                bundle.getString(Prefs.KEY_OUT_ORDER, Prefs.OUT_DEFAULT_ORDER_STRING));
        s.typeWeight = Prefs.clamp(
                bundle.getInt(Prefs.KEY_TYPE_WEIGHT, Prefs.DEF_TYPE_WEIGHT),
                Prefs.MIN_TYPE_WEIGHT, Prefs.MAX_TYPE_WEIGHT);
        s.trackAlpha = Prefs.clamp(
                bundle.getInt(Prefs.KEY_TRACK_ALPHA, Prefs.DEF_TRACK_ALPHA),
                Prefs.MIN_TRACK_ALPHA, Prefs.MAX_TRACK_ALPHA);
        s.debugLog = bundle.getBoolean(Prefs.KEY_DEBUG_LOG, Prefs.DEF_DEBUG_LOG);
        return s;
    }

    /**
     * The same read on top of an existing snapshot: keys the bundle carries are
     * taken from it, keys it leaves out keep the value {@code base} already had.
     *
     * <p>Seeding matters because the bundle is untrusted input. If an absent key
     * fell back to the shared default instead, a single missing extra would wipe
     * the user's configuration - including {@code enabled}, which would flip back
     * to true and draw the trio glyph over an otherwise native status bar.
     */
    public static TrioSettings fromBundle(Bundle bundle, TrioSettings base) {
        TrioSettings s = (base == null) ? defaults() : base.copy();
        if (bundle == null) {
            return s;
        }
        TrioSettings delivered = fromBundle(bundle);
        for (String key : bundle.keySet()) {
            s.applyKeyFrom(delivered, key);
        }
        return s;
    }

    /**
     * The inverse of {@link #fromBundle(Bundle)}, using the same pref key names
     * so the two sides cannot drift apart. The settings app sends this to the
     * hooked process after every write.
     */
    public Bundle toBundle() {
        Bundle b = new Bundle();
        // The style first: the component switches below are written under the CURRENT
        // arrangement's key, so the hooked process has to be able to pick them out.
        b.putInt(Prefs.KEY_NAV_STYLE, Prefs.normalizeNavStyle(navStyle));
        b.putInt(Prefs.KEY_NAV_LABELS, Prefs.normalizeNavLabels(navLabels));
        b.putInt(Prefs.KEY_TRIO_STYLE, Prefs.normalizeTrioStyle(trioStyle));
        final int style = Prefs.normalizeTrioStyle(trioStyle);
        b.putBoolean(Prefs.KEY_ENABLED, enabled);
        b.putInt(Prefs.KEY_EXCLUDE_REGIONS, excludeRegions);
        b.putBoolean(Prefs.styledKey(Prefs.KEY_SHOW_WIFI, style), showWifi);
        b.putBoolean(Prefs.styledKey(Prefs.KEY_SHOW_MOBILE, style), showMobile);
        b.putBoolean(Prefs.styledKey(Prefs.KEY_SHOW_VALUE, style), showValue);
        b.putBoolean(Prefs.styledKey(Prefs.KEY_SHOW_BOLT, style), showBolt);
        b.putInt(Prefs.styledKey(Prefs.KEY_MOBILE_TYPE_MODE, style), mobileTypeMode);
        b.putBoolean(Prefs.styledKey(Prefs.KEY_VALUE_CENTRED, style), valueCentred);
        b.putString(Prefs.styledKey(Prefs.KEY_RING_LAYOUT, style),
                Prefs.encodeLayout(ringZones));
        b.putBoolean(Prefs.styledKey(Prefs.KEY_DUAL_SIM, style), dualSim);
        b.putInt(Prefs.styledKey(Prefs.KEY_SIGNAL_MODE, style), Prefs.clamp(signalMode,
                Prefs.MIN_SIGNAL_MODE, Prefs.MAX_SIGNAL_MODE));
        b.putBoolean(Prefs.styledKey(Prefs.KEY_STACKED_SIGNAL, style), stackedSignal);
        b.putBoolean(Prefs.styledKey(Prefs.KEY_SHOW_SIM_1, style), showSim1);
        b.putBoolean(Prefs.styledKey(Prefs.KEY_SHOW_SIM_2, style), showSim2);
        b.putBoolean(Prefs.KEY_DATA_SIM_ONLY, dataSimOnly);
        b.putBoolean(Prefs.KEY_PERF_POWER, perfPower);
        b.putBoolean(Prefs.KEY_PERF_TEMP, perfTemp);
        b.putBoolean(Prefs.KEY_PERF_TEMP_CHARGING_ONLY, perfTempChargingOnly);
        b.putInt(Prefs.KEY_PERF_SCALE, perfScale);
        b.putInt(Prefs.KEY_PERF_WEIGHT, perfWeight);
        b.putInt(Prefs.KEY_GLYPH_SCALE, glyphScale);
        b.putInt(Prefs.KEY_CAPSULE_SCALE, capsuleScale);
        b.putInt(Prefs.KEY_GLYPH_OFFSET_X, glyphOffsetX);
        b.putInt(Prefs.KEY_ICON_OFFSET_X, iconOffsetX);
        b.putInt(Prefs.KEY_GLYPH_OFFSET_Y, glyphOffsetY);
        b.putBoolean(Prefs.KEY_DEMO_DUAL_SIM, demoDualSim);
        b.putInt(Prefs.KEY_CAPSULE_LEFT_DX, capsuleLeftDx);
        b.putInt(Prefs.KEY_CAPSULE_LEFT_DY, capsuleLeftDy);
        b.putInt(Prefs.KEY_CAPSULE_RIGHT_DX, capsuleRightDx);
        b.putInt(Prefs.KEY_CAPSULE_RIGHT_DY, capsuleRightDy);
        b.putInt(Prefs.KEY_CAPSULE_LENGTH, capsuleLength);

        b.putBoolean(Prefs.KEY_ROLE_COLORS, roleColors);
        b.putInt(Prefs.KEY_COLOR_CRITICAL_ON_DARK, criticalOnDark);
        b.putInt(Prefs.KEY_COLOR_CRITICAL_ON_LIGHT, criticalOnLight);
        b.putInt(Prefs.KEY_COLOR_CHARGING_ON_DARK, chargingOnDark);
        b.putInt(Prefs.KEY_COLOR_CHARGING_ON_LIGHT, chargingOnLight);
        b.putInt(Prefs.KEY_COLOR_LOW_ON_DARK, lowOnDark);
        b.putInt(Prefs.KEY_COLOR_LOW_ON_LIGHT, lowOnLight);
        b.putInt(Prefs.KEY_LOW_THRESHOLD, lowThreshold);

        b.putInt(Prefs.KEY_RING_STROKE, ringStroke);
        b.putInt(Prefs.KEY_ARC_STROKE, arcStroke);
        b.putInt(Prefs.KEY_VALUE_SIZE, valueSize);
        b.putInt(Prefs.KEY_VALUE_WEIGHT, valueWeight);
        b.putInt(Prefs.KEY_CLOCK_SIZE, clockSize);
        b.putInt(Prefs.KEY_CLOCK_WEIGHT, clockWeight);
        b.putInt(Prefs.KEY_TYPE_SIZE, typeSize);
        b.putInt(Prefs.KEY_OUT_TYPE_SIZE_DP, outTypeSize);
        b.putInt(Prefs.KEY_OUT_SIGNAL_SIZE, outSignalSize);
        b.putInt(Prefs.KEY_OUT_TYPE_MARGIN_LEFT, outTypeMarginLeft);
        b.putInt(Prefs.KEY_OUT_TYPE_MARGIN_RIGHT, outTypeMarginRight);
        b.putInt(Prefs.KEY_OUT_SIGNAL_MARGIN, outSignalMargin);
        b.putInt(Prefs.KEY_TYPE_SUFFIX_SCALE, typeSuffixScale);
        b.putInt(Prefs.KEY_BOLT_SCALE, boltScale);
        b.putInt(Prefs.KEY_WIFI_SCALE, wifiScale);
        b.putBoolean(Prefs.KEY_TYPE_HIDE_ON_WIFI, typeHideOnWifi);
        b.putBoolean(Prefs.KEY_CHARGING_SHOWS_VALUE, chargingShowsValue);
        b.putString(Prefs.KEY_FONT_PATH, fontPath);
        b.putInt(Prefs.KEY_DOT_SCALE, dotScale);
        b.putString(Prefs.KEY_OUT_ORDER, Prefs.encodeOutOrder(outOrder));
        b.putInt(Prefs.KEY_TYPE_WEIGHT, typeWeight);
        b.putInt(Prefs.KEY_TRACK_ALPHA, trackAlpha);

        b.putBoolean(Prefs.KEY_DEBUG_LOG, debugLog);
        return b;
    }

    /**
     * Field-wise equality. Exists for one caller - the boot-time re-read in
     * {@code TrioConfig}, which must not notify the listeners (and re-fold every
     * container) when the delayed read saw the same values the first one did -
     * but a full comparison is also what a snapshot type ought to answer.
     */
    /**
     * Normalises a stored font path to a non-null string.
     *
     * <p>Null is folded to the empty string here rather than at every reader,
     * because the value crosses a process boundary: the hooked side can be handed
     * a null from a bundle built by an older app, and a null path reaching the
     * renderer would mean a null check in the draw path.
     */
    private static String readFontPath(String stored) {
        return stored == null ? Prefs.DEF_FONT_PATH : stored;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TrioSettings)) {
            return false;
        }
        final TrioSettings s = (TrioSettings) o;
        return enabled == s.enabled
                // The scope switch decides per region whether the stock battery is
                // left alone. Left out, the settings row could write it and never
                // see its own value come back, because the screen holds the
                // snapshot in a mutableStateOf compared with this method.
                && excludeRegions == s.excludeRegions
                && showWifi == s.showWifi
                && showMobile == s.showMobile
                && showValue == s.showValue
                && showBolt == s.showBolt
                && mobileTypeMode == s.mobileTypeMode
                && valueCentred == s.valueCentred
                && navStyle == s.navStyle
                && navLabels == s.navLabels
                && trioStyle == s.trioStyle
                && java.util.Arrays.deepEquals(ringZones, s.ringZones)
                && dualSim == s.dualSim
                && signalMode == s.signalMode
                && stackedSignal == s.stackedSignal
                // Two switches of their own, and issue 25 applies: left out, a tap
                // on either row would write a value the screen cannot see change.
                && showSim1 == s.showSim1
                && showSim2 == s.showSim2
                && dataSimOnly == s.dataSimOnly
                // The two performance readouts are stored settings like any
                // other, and the same issue 25 trap applies: the settings screen
                // holds the snapshot in a mutableStateOf whose default policy is
                // equality, so omitting a field here means a write that changed
                // only that field schedules no recomposition and the switch goes
                // on showing its old state.
                && perfPower == s.perfPower
                && perfTemp == s.perfTemp
                // issue 45's own flag. It spent one release outside this list, and the
                // symptom was exactly the one described above: the switch could be
                // turned on, the value reached storage and SystemUI, and the row went on
                // showing it off until some unrelated write forced a recomposition.
                && perfTempChargingOnly == s.perfTempChargingOnly
                // The two size sliders are stored settings like any other, and
                // the issue 25 trap above applies unchanged: with either one left
                // out, moving that slider would change nothing the screen can
                // see, no recomposition would be scheduled, and the row would go
                // on showing the value it had.
                && perfScale == s.perfScale
                // The readout's weight is a stored setting too, and issue 25
                // applies to it exactly as it does to the size above: the slider
                // has to be able to see its own write, or the row snaps back to
                // the old number on the next recomposition.
                && perfWeight == s.perfWeight
                && glyphScale == s.glyphScale
                && capsuleScale == s.capsuleScale
                && glyphOffsetX == s.glyphOffsetX
                && glyphOffsetY == s.glyphOffsetY
                && iconOffsetX == s.iconOffsetX
                && demoDualSim == s.demoDualSim
                // The four capsule nudges, and the reason this defect was noticed at all:
                // they were added to defaults(), from(), copy(), applyKeyFrom() and both
                // bundle directions - the whole pipeline except this list - so the only
                // row in the module that could not move was the one they belong to. The
                // readout row next to them is in the list, which is why dragging THAT one
                // refreshed the whole page and made the nudges catch up.
                && capsuleLeftDx == s.capsuleLeftDx
                && capsuleLeftDy == s.capsuleLeftDy
                && capsuleRightDx == s.capsuleRightDx
                && capsuleRightDy == s.capsuleRightDy
                && capsuleLength == s.capsuleLength
                && roleColors == s.roleColors
                && criticalOnDark == s.criticalOnDark
                && criticalOnLight == s.criticalOnLight
                && chargingOnDark == s.chargingOnDark
                && chargingOnLight == s.chargingOnLight
                && lowOnDark == s.lowOnDark
                && lowOnLight == s.lowOnLight
                && lowThreshold == s.lowThreshold
                && ringStroke == s.ringStroke
                && arcStroke == s.arcStroke
                && valueSize == s.valueSize
                && valueWeight == s.valueWeight
                && clockSize == s.clockSize
                && clockWeight == s.clockWeight
                && typeSize == s.typeSize
                && outTypeSize == s.outTypeSize
                && outSignalSize == s.outSignalSize
                && outTypeMarginLeft == s.outTypeMarginLeft
                && outTypeMarginRight == s.outTypeMarginRight
                && outSignalMargin == s.outSignalMargin
                && typeSuffixScale == s.typeSuffixScale
                && boltScale == s.boltScale
                && wifiScale == s.wifiScale
                && typeHideOnWifi == s.typeHideOnWifi
                && chargingShowsValue == s.chargingShowsValue
                // Objects.equals, not ==: the font path is the one reference-typed
                // field here, and == would compare identities. Two snapshots built
                // from the same stored path hold different String instances, so a
                // reference compare would report "changed" on every read and make
                // the settings row rebuild endlessly.
                && java.util.Objects.equals(fontPath, s.fontPath)
                && dotScale == s.dotScale
                && java.util.Arrays.equals(outOrder, s.outOrder)
                && typeWeight == s.typeWeight
                && trackAlpha == s.trackAlpha
                && debugLog == s.debugLog;
    }

    @Override
    public int hashCode() {
        // Fields are small ints and booleans; a cheap fold is enough. The class
        // is used as a value only through equals() - nothing hashes it - but the
        // two performance switches are folded in anyway so that the contract
        // holds if anything ever does.
        int h = (enabled ? 1 : 0) ^ (mobileTypeMode << 1) ^ trioStyle;
        h = h * 31 + (perfPower ? 1 : 0);
        h = h * 31 + (perfTemp ? 1 : 0);
        h = h * 31 + perfScale;
        h = h * 31 + perfWeight;
        h = h * 31 + glyphScale;
        h = h * 31 + capsuleScale;
        h = h * 31 + glyphOffsetX;
        h = h * 31 + glyphOffsetY;
        h = h * 31 + iconOffsetX;
        h = h * 31 + (demoDualSim ? 1 : 0);
        h = h * 31 + (perfTempChargingOnly ? 1 : 0);
        h = h * 31 + capsuleLeftDx;
        h = h * 31 + capsuleLeftDy;
        h = h * 31 + capsuleRightDx;
        h = h * 31 + capsuleRightDy;
        h = h * 31 + excludeRegions;
        return h != 0 ? h : 1;
    }
}
