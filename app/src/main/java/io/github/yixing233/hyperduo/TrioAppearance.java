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

/**
 * Every decision about <em>what</em> the trio glyph puts on screen, resolved once
 * from a single settings snapshot.
 *
 * <p>Before this existed the same rule was written out by hand in five places:
 * {@code TrioRenderer.drawRingLayout}, {@code TrioRenderer.drawRectLayout},
 * {@code TrioHooks.applyMeterText}, the two battery-style hooks - and, in a
 * different shape again, the settings screen's own row gates. Copies drift, and
 * two of them had already drifted away from what the documentation claimed: the
 * rectangular arrangement silently ignored {@code valueCentred}, and the
 * percentage took the centre whenever Wi-Fi was absent whether or not the user
 * had asked for it.
 *
 * <p>This class is now the one place that rule lives. The renderer asks it what
 * to draw, the hook side asks it what to suppress, and the settings screen asks
 * it which rows mean anything - so a row can no longer be greyed out for a
 * reason the drawing code does not share.
 *
 * <p>Deliberately free of Android types and of static state. The hooked SystemUI
 * process and the settings app both build it, and the settings app builds it
 * again on every recomposition; it is a handful of field reads and copies, cheap
 * enough to build per frame.
 *
 * <p>Immutable.
 */
public final class TrioAppearance {

    /** {@link Prefs#STYLE_RING}, {@link Prefs#STYLE_RECT} or {@link Prefs#STYLE_CAPSULE}. */
    public final int style;
    /** True when {@link #style} is the rectangular arrangement. */
    public final boolean rect;
    /**
     * True when {@link #style} is the capsule arrangement.
     *
     * <p>Its own flag rather than a third case at each use: the two existing
     * arrangements are near enough siblings that one boolean covered both, and
     * every place asking about one of them also means "not the other". The
     * capsule is a different shape with a different meaning - its outline is the
     * gauge - so only the tests that really care are written against it.
     */
    public final boolean capsule;
    /** Interior cell nudges in design units; see Prefs.KEY_CAPSULE_LEFT_DX. */
    public final int capsuleLeftDx;
    public final int capsuleLeftDy;
    public final int capsuleRightDx;
    public final int capsuleRightDy;
    /** Extra straight-run length; see {@link Prefs#KEY_CAPSULE_LENGTH}. */
    public final int capsuleLength;
    /**
     * True when {@link #style} is the ring.
     *
     * <p>Stated explicitly rather than left as {@code !rect}: that expression was
     * correct only while there were two arrangements, and the capsule makes it
     * quietly wrong. Every row and rule that says "the ring" now has a name for
     * it.
     */
    public final boolean ring;
    /**
     * True when this arrangement places pieces in <b>bands</b> - the ring and the capsule.
     *
     * <p>The question every placement row asks before offering a position: the ring has three
     * thirds and the capsule three bands, and both read their contents from the layout. The
     * rectangle has none - its columns and slots are derived from the switches - so a
     * position row there would offer a choice nothing reads.
     *
     * <p>Stated here rather than as {@code !rect} at each call site: the expression was
     * correct only while the capsule had no cells, and this feature exists because it did.
     */
    public final boolean slotted;
    /** The master switch. False hands every slot back to MIUI. */
    public final boolean glyph;
    /** The Wi-Fi arcs are wanted; ink also needs a signal - see {@link #wifiInk}. */
    public final boolean wifi;
    /** The mobile level dots are drawn. */
    public final boolean mobile;
    /**
     * The signal is drawn beside the ring, i.e. handed back to MIUI.
     *
     * <p>The settings screen labels this 环外. While it is set, the glyph draws no level
     * dots whatsoever - not the aggregate meter and not either card's row - and
     * {@link #foldsMobile()} is false so MIUI keeps its own icon. Which third a card
     * would sit in is still stored in the layout; it is simply not consulted.
     */
    public final boolean signalOutOfRing;
    /**
     * While out of ring, the module draws its own stacked reading instead of handing the
     * signal back.
     *
     * <p>Only meaningful with {@link #signalOutOfRing}; see {@link #stackedOut()}, which is
     * the predicate everything should ask.
     */
    public final boolean stackedSignal;
    /**
     * The user wants a <em>second</em> row of level dots while the glyph has
     * neither Wi-Fi ink nor a charger to report - see {@link #dualReading}.
     */
    public final boolean dualSim;

    /**
     * The demo switch: the module treats itself as holding two cards that report
     * {@code 4}/{@code 2} while it is on. Same reading pipeline as the real
     * switch, one explicit toggle, and nothing outside the module's own drawing
     * changes.
     */

    /** Stack both cards inside the ring; see {@link Prefs#KEY_DUAL_INSIDE_RING}. */
    public final boolean dualInsideRing;
    /** Where the stacked pair sits; see {@link Prefs#KEY_DUAL_STACK_POS}. */
    public final int dualStackPos;
    /** Nudges of the stacked pair, in design units. */
    public final int dualStackDy;
    /**
     * SIM 1's own row of four dots is wanted, as a component of its own.
     *
     * <p>Separate from {@link #dualSim}: that switch asks the aggregate meter for
     * a second row, this pair places each card's row independently. Neither
     * implies the other - a user can place SIM 2 in the notch and SIM 1 at the
     * bottom, or read only one card without touching the dual switch.
     */
    public final boolean showSim1;
    /** SIM 2's own row of four dots is wanted; see {@link #showSim1}. */
    public final boolean showSim2;
    /** The user wants only the current data SIM in the out-of-ring reading. */
    public final boolean dataSimOnly;
    /** The battery percentage is drawn. */
    public final boolean value;
    /** The charging bolt toggle; the free ring gives it a slot independent of the percentage. */
    public final boolean boltWanted;
    /**
     * The percentage takes the ring's centre even when the arcs are drawn.
     *
     * <p>Always false for the rectangular arrangement, which has no notch to move
     * the arcs into. Saying so here - rather than leaving the renderer to ignore
     * the switch quietly - is what lets the settings screen gate the row on the
     * style without a second copy of the rule.
     */
    public final boolean centreValue;
    /** {@link Prefs#MOBILE_TYPE_OFF}, {@code IN_RING} or {@code OUT_RING}. */
    public final int typeMode;
    /** The network type is drawn inside the glyph. */
    public final boolean typeInRing;
    /** The network type is drawn as a status-bar view outside the glyph. */
    public final boolean typeOutOfRing;
    /**
     * The charging power is drawn in the out-of-ring strip.
     *
     * <p>Unlike every other flag here this one does not decide the whole view:
     * the power and the temperature share one strip, and either flag alone is
     * enough to bring it back. Use {@link #perfAnywhere} for that question and
     * this one only for the power segment.
     */
    public final boolean perfPower;
    /** The battery temperature is drawn in the same out-of-ring strip. */
    public final boolean perfTemp;
    /** Drops the temperature unless a charger is attached; see {@link Prefs#KEY_PERF_TEMP_CHARGING_ONLY}. */
    public final boolean perfTempChargingOnly;
    /**
     * Overall size of the two readouts, in percent of the adaptive fit.
     *
     * <p>Read by the hooked side only, but kept here with the rest so that every
     * caller that sizes the reading asks one object rather than reaching back
     * into the settings for a number the appearance already resolved.
     */
    public final int perfScale;
    /**
     * How heavy the two readouts are drawn, on the platform's 100..900 scale.
     *
     * <p>A setting of its own rather than a second reader of {@link #typeWeight}:
     * the readout is status-bar text beside MIUI's own figures, and the type
     * weight also drives the label drawn inside the ring. Kept here with the rest
     * so the hooked side asks one object for both the size and the weight.
     */
    public final int perfWeight;
    /**
     * Overall size of the glyph, in percent of the fit the host box implies.
     *
     * <p>Shrink-only, so 100 is the largest value this can hold and the shipped
     * look. The renderer multiplies its own scale by this; see
     * {@code TrioRenderer.drawInto}.
     */
    public final int glyphScale;
    /** The capsule's own size correction; see {@link Prefs#KEY_CAPSULE_SCALE}. */
    public final int capsuleScale;

    /** Horizontal nudge of the whole glyph, in dp; negative moves left. */
    public final int glyphOffsetX;
    /** Vertical nudge of the whole glyph, in dp; negative moves up. */
    public final int glyphOffsetY;
    /** Horizontal shift of the stock icon area itself, in dp; independent of the glyph. */
    public final int iconOffsetX;

    public final boolean roleColors;
    public final int criticalOnDark;
    public final int criticalOnLight;
    public final int chargingOnDark;
    public final int chargingOnLight;
    public final int lowOnDark;
    public final int lowOnLight;
    public final int lowThreshold;

    public final int ringStroke;
    public final int arcStroke;
    public final int trackAlpha;
    public final int valueSize;
    public final int valueWeight;
    /** Independent size and weight of the capsule or free-ring clock. */
    public final int clockSize;
    public final int clockWeight;
    public final int typeSize;
    public final int outTypeSize;
    public final int outSignalSize;
    /**
     * The gap the out-of-ring label keeps on each side, in dp: {@code left} is
     * the side facing away from the battery in LTR and {@code right} the side
     * facing it. Both are physical sides of the label, not reading-order starts
     * and ends - the RTL branch swaps which gap each one supplies.
     */
    public final int outTypeMarginLeft;
    public final int outTypeMarginRight;
    /**
     * Gap between the out-of-ring reading and the battery, in dp. A reserved
     * margin: it is folded into the strip the native icon row gives up, so the
     * reading can never be moved on top of its neighbours.
     */
    public final int outSignalMargin;
    /** Scale of a trailing "A" in the network type, as a percentage. */
    public final int typeSuffixScale;
    /**
     * Size of the in-ring charging bolt, as a percentage of its reference size.
     *
     * <p>Ask {@link #boltScaleFactor()} rather than dividing by 100 at each call site: the
     * renderer multiplies this into three different arrangements' own scales, and one
     * conversion keeps them from rounding differently.
     */
    public final int boltScale;
    /** Wi-Fi arcs' size, in percent; see {@link Prefs#KEY_WIFI_SCALE}. */
    public final int wifiScale;
    /**
     * The network type steps aside while Wi-Fi is up; see {@link Prefs#KEY_TYPE_HIDE_ON_WIFI}.
     *
     * <p>The FLAG only, not the decision: whether Wi-Fi is up this instant is runtime state,
     * and this class is a pure snapshot of the settings - it is compiled against desktop shims
     * by the offline harness, and it is read from the settings process as well as SystemUI.
     * The renderer, which already reads {@code TrioState}, applies the flag to the frame.
     */
    public final boolean typeHideOnWifi;
    /** While charging the ring centre shows the percentage; see {@link Prefs#KEY_CHARGING_SHOWS_VALUE}. */
    public final boolean chargingShowsValue;
    /**
     * Absolute path of the user's font file, or "" for the system face.
     *
     * <p>Resolved to non-null in the constructor, like every other field here, so
     * no consumer has to test for null. The renderer compares it against the face
     * it currently has loaded, which is what makes a font change take effect on
     * the next frame without any invalidation call.
     */
    public final String fontPath;
    /**
     * Size of the ring's level-dot rows, as a percentage of the bead the stroke implies.
     *
     * <p>Ask {@link #dotScaleFactor()} rather than dividing by 100 at each call site, for
     * the same reason as {@link #boltScaleFactor()}.
     *
     * <p>One value for the top and the bottom rows: they are the same ink, and the two rows
     * are what makes the ring read as cut into segments rather than as a closed outline.
     */
    public final int dotScale;
    /**
     * The out-of-ring order, nearest the battery first - see {@link Prefs#KEY_OUT_ORDER}.
     *
     * <p>An order, not a priority: {@link Prefs#OUT_PRIORITY} still decides which piece the
     * strip drops when it does not fit. Kept here with the other out-of-ring geometry
     * because the layout and the reserve pass both read it, and they must agree about where
     * each piece sits.
     */
    public final int[] outOrder;
    public final int typeWeight;

    private TrioAppearance(TrioSettings c) {
        style = c.trioStyle;
        rect = c.trioStyle == Prefs.STYLE_RECT;
        capsule = c.trioStyle == Prefs.STYLE_CAPSULE;
        capsuleLeftDx = c.capsuleLeftDx;
        capsuleLeftDy = c.capsuleLeftDy;
        capsuleRightDx = c.capsuleRightDx;
        capsuleRightDy = c.capsuleRightDy;
        capsuleLength = c.capsuleLength;
        ring = c.trioStyle == Prefs.STYLE_RING;
        slotted = !rect;
        ringZones = TrioSettings.copyZones(c.ringZones);

        glyph = c.enabled;
        wifi = c.showWifi;
        mobile = c.showMobile;
        signalOutOfRing = c.signalMode == Prefs.SIGNAL_OUT_RING;
        stackedSignal = c.stackedSignal;
        dualSim = c.dualSim;

        dualInsideRing = c.dualInsideRing;
        dualStackPos = c.dualStackPos;
        dualStackDy = c.dualStackDy;
        showSim1 = c.showSim1;
        showSim2 = c.showSim2;
        dataSimOnly = c.dataSimOnly;
        value = c.showValue;
        // The bolt is a PLACED piece like every other, not a switch of its own: a charger
        // must show what it is doing, and what it shows is decided by where the bolt sits in
        // the layout. A separate row could contradict the list it was meant to describe.
        boltWanted = true;
        centreValue = c.valueCentred && ring;
        typeMode = c.mobileTypeMode;
        typeInRing = c.mobileTypeMode == Prefs.MOBILE_TYPE_IN_RING;
        typeOutOfRing = c.mobileTypeMode == Prefs.MOBILE_TYPE_OUT_RING;
        typeHideOnWifi = c.typeHideOnWifi;
        chargingShowsValue = c.chargingShowsValue;
        fontPath = c.fontPath == null ? "" : c.fontPath;
        perfPower = c.perfPower;
        perfTemp = c.perfTemp;
        perfTempChargingOnly = c.perfTempChargingOnly;
        perfScale = c.perfScale;
        perfWeight = c.perfWeight;
        glyphScale = c.glyphScale;
        capsuleScale = c.capsuleScale;
        glyphOffsetX = c.glyphOffsetX;
        glyphOffsetY = c.glyphOffsetY;
        iconOffsetX = c.iconOffsetX;

        roleColors = c.roleColors;
        criticalOnDark = c.criticalOnDark;
        criticalOnLight = c.criticalOnLight;
        chargingOnDark = c.chargingOnDark;
        chargingOnLight = c.chargingOnLight;
        lowOnDark = c.lowOnDark;
        lowOnLight = c.lowOnLight;
        lowThreshold = c.lowThreshold;

        ringStroke = c.ringStroke;
        arcStroke = c.arcStroke;
        trackAlpha = c.trackAlpha;
        valueSize = c.valueSize;
        valueWeight = c.valueWeight;
        clockSize = c.clockSize;
        clockWeight = c.clockWeight;
        typeSize = c.typeSize;
        outTypeSize = c.outTypeSize;
        outSignalSize = c.outSignalSize;
        outTypeMarginLeft = c.outTypeMarginLeft;
        outTypeMarginRight = c.outTypeMarginRight;
        outSignalMargin = c.outSignalMargin;
        typeSuffixScale = c.typeSuffixScale;
        boltScale = c.boltScale;
        wifiScale = c.wifiScale;
        dotScale = c.dotScale;
        outOrder = c.outOrder == null
                ? Prefs.OUT_DEFAULT_ORDER.clone() : c.outOrder.clone();
        typeWeight = c.typeWeight;
    }

    /** The appearance of {@code cfg}, or of the shipped defaults when it is null. */
    public static TrioAppearance of(TrioSettings cfg) {
        return new TrioAppearance(cfg == null ? TrioSettings.defaults() : cfg);
    }

    /**
     * Whether the glyph owns the bolt.
     *
     * <p>The two arrangements answer differently, and the difference is real rather
     * than leftover:
     *
     * <ul>
     *   <li>The <b>ring</b> places pieces in three slots, so the bolt appears exactly
     *       where a slot names it ({@code PART_BOLT}). It is no longer tied to the
     *       percentage: with slots, "show the bolt" and "show the number" are two
     *       independent placements, and tying them would make one switch silently
     *       govern the other's slot.
     *   <li>The <b>capsule</b> has no slots - its right-hand reading is arbitrated
     *       (bolt, then arcs, then percentage, then type) - so there the bolt keeps
     *       following the percentage switch, which is what "charging replaces the
     *       number" means in that arrangement.
     * </ul>
     *
     * <p>Not gated on {@link #glyph}, because the preview keeps drawing the chosen
     * arrangement while the master switch is off.
     */
    /**
     * Whether the bolt is PLACED anywhere, ignoring whether a charger is attached.
     *
     * <p>The question the settings rows need: "would a charger show a bolt", asked of the
     * arrangement rather than of this frame. Distinct from {@link #drawsBolt()}, which folds
     * in the current charge state and would grey a row out the moment the cable came out.
     */
    public boolean hasBolt() {
        return hasRingPart(Prefs.PART_BOLT) || hasCapsulePart(Prefs.PART_BOLT);
    }

    public boolean drawsBolt() {
        // Placement alone, in both arrangements. The capsule used to tie the bolt to the
        // percentage switch, which was the only way to ask for "charging replaces the number";
        // now the user says it by placing the bolt, and the empty-layout fallback below still
        // reproduces the old look for an install that has never placed anything.
        return hasRingPart(Prefs.PART_BOLT)
                || (capsule && hasCapsulePart(Prefs.PART_BOLT))
                || (capsule && !anyPlacedInCapsule() && value);
    }
    
    /** Whether the capsule's body holds this piece anywhere, left column included. */
    private boolean hasCapsulePart(int part) {
        return capsuleParts().contains(part);
    }
    
    /** Every piece the capsule's four cells name, in the order they were placed. */
    private java.util.Set<Integer> capsuleParts() {
        final java.util.Set<Integer> out = new java.util.HashSet<>();
        for (int[] chain : ringZones) {
            for (int id : chain) {
                out.add(id);
            }
        }
        return out;
    }
    
    /** True when no capsule cell holds anything, i.e. the shipped look is in force. */
    private boolean anyPlacedInCapsule() {
        for (int[] chain : ringZones) {
            if (chain.length > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * The three ring slots, packed four bits each - the whole layout.
     *
     * <p>There is no second, switch-derived layout: every piece the ring draws is
     * whatever these three slots name.
     */
    public final int[][] ringZones;

    /**
     * Whether {@code part} appears anywhere in the layout, whatever its priority.
     *
     * <p>This is the question the native-icon handoff needs: if a piece is placed at all,
     * the module owns that reading and MIUI's own icon must stay folded - even on a frame
     * where a higher-priority candidate is the one actually drawing that zone. Asking
     * "is it drawn this frame" instead would make the native icon flicker in and out as
     * the fallback chain moved.
     */
    public boolean hasRingPart(int part) {
        for (int z = 0; z < Prefs.ZONE_COUNT; z++) {
            final int[] ids = ringZones[z];
            for (int i = 0; i < ids.length; i++) {
                if (ids[i] == part) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether the module draws the arcs itself, which is also what decides whether MIUI's
     * own Wi-Fi icon is folded away.
     *
     * <p>Only the ring is slot-based; the capsule and the rectangle arbitrate their single
     * reading from the switches, so there the switch alone answers. Asking slots outside
     * the ring would read a layout those arrangements never consult.
     */
    public boolean ownsWifi() {
        return wifi && (!ring || hasRingPart(Prefs.PART_WIFI));
    }

    /**
     * Whether the two cards are stacked inside the ring this frame.
     *
     * <p>Needs three things: the ring is the arrangement being drawn, the user asked for the
     * stacked form, and there really are two cards. One card is a single row and belongs in a
     * mouth like any other reading - stacking it would leave the ring looking half-filled.
     */
    /** Whether the stacked pair is drawn at the bottom rather than the top. */
    public boolean dualStackAtBottom() {
        return dualStackPos == Prefs.DUAL_STACK_BOTTOM;
    }

    public boolean stacksDualRows(int sims) {
        return ring && dualInsideRing && sims >= 2;
    }

    public boolean ownsValue() {
        return value && (!ring || hasRingPart(Prefs.PART_VALUE));
    }

    public boolean ownsType() {
        return typeOutOfRing || (typeInRing && (!ring || hasRingPart(Prefs.PART_TYPE)));
    }

    /** Native percentage follows the user's system style when the free ring releases it. */
    public boolean handsBackPercentage() {
        return glyph && ring && !ownsValue();
    }

    /**
     * Whether MIUI's own charging bolt has to stay out of the way, which is
     * exactly while this module is on <em>and</em> draws one of its own.
     */
    public boolean hidesNativeBolt() {
        return glyph && drawsBolt();
    }

    /** Whether the Wi-Fi arcs carry ink at this signal level. */
    public boolean wifiInk(int wifiLevel) {
        return ownsWifi() && wifiLevel >= 1;
    }

    /**
     * Whether the network type steps aside because Wi-Fi is up - decided per ARRANGEMENT.
     *
     * <p>Three cases, and they are not the same question:
     *
     * <ol>
     *   <li><b>The arcs are handed back to MIUI</b> (Wi-Fi is nowhere in this arrangement).
     *       The system draws its own Wi-Fi icon beside the glyph, so the arcs are not competing
     *       with the label for the same space, and the label stays. Suppressing it here would
     *       hide a reading for a Wi-Fi icon the module is not even drawing.</li>
     *   <li><b>The type is outside the glyph</b> (the ring's own out-of-ring strip, or the
     *       capsule's - see {@link #typeOutOfRing}). It then sits in the same strip the
     *       system's Wi-Fi icon lands in, which is the crowding the option exists to fix, so
     *       it goes.</li>
     *   <li><b>Both are inside.</b> The arcs take priority, so the label yields - but only if
     *       something else can take its band. A band left empty reads as a broken layout, so
     *       when the label is the only candidate left for its zone it stays and the arcs keep
     *       the slot they would have shared.</li>
     * </ol>
     *
     * <p>Case 3 is per BAND, which is why it is answered by the caller: only the zone loop
     * knows what else is queued behind the label. This method answers the first two.
     *
     * @param wifiConnected the live connection state - never the signal level, which is a
     *   strength: 0 is a weak link and -1 also means "no reading"
     */
    public boolean hidesTypeForWifi(boolean wifiConnected) {
        if (!typeHideOnWifi || !wifiConnected) {
            return false;
        }
        // Case 1: Wi-Fi is not ours in this arrangement, so there is nothing to make room for.
        if (!ownsWifi()) {
            return false;
        }
        // Case 2: outside the glyph, where the system icon already sits.
        return typeOutOfRing;
    }

    /**
     * Whether a row of level dots is drawn per SIM - SIM 1 on top, SIM 2 below -
     * rather than the usual single row for the current data SIM.
     *
     * <p>The user asks for this while neither Wi-Fi ink nor a charger is on
     * screen. Both of those own the glyph's slots: the arcs take the notch the
     * bottom row needs, and charging already reports itself through the colour
     * and the bolt. Hiding the second row there keeps one reading per state
     * instead of stacking two.
     *
     * <p>Also needs two SIMs to read: with one - or with the levels not yet
     * sampled - the single row falls back to the current data SIM, which is what
     * the switch degrades to rather than drawing a row of empty dots.
     *
     * @param wifiInk whether the arcs actually carry ink this frame
     * @param charging whether the device reports a charger
     * @param sims how many SIM signal levels are known this frame
     */
    public boolean dualSimRows(boolean wifiInk, boolean charging, int sims) {
        return dualSim && signalDots() && sims >= 2 && !wifiInk && !charging;
    }

    /**
     * Whether the glyph itself draws the mobile level dots.
     *
     * <p>Both arrangements go through here so the two can never disagree about
     * where the signal lives. Out of the ring the answer is no in both: the module
     * hands the reading back to MIUI, and leaving the dots behind would show the
     * same signal twice.
     */
    public boolean signalDots() {
        if (signalOutOfRing) {
            return false;
        }
        // The ring is slot-based, so a card has to be placed there to be drawn. The rectangle
        // derives what it draws from the switch alone - it has no slots - so there the switch
        // IS the answer. The capsule is asked through its resolved cells instead: its rows are
        // placements now, so "the switch is on" is no longer the same question as "an opening
        // holds a row". Capsule answers that itself in its constructor, using this method only
        // for the legacy no-placement fallback.
        return mobile && (!ring || hasRingPart(Prefs.PART_MOBILE));
    }

    /**
     * Whether the glyph draws SIM 1's own row this frame.
     *
     * <p>Placement is the only thing that can turn a per-card row on: the row exists as a
     * <em>slot occupant</em>, and both slot-driven arrangements have slots - the ring's
     * openings and the capsule's bands. The rectangle keeps the aggregate meter it has always
     * drawn, because it has no band to put a second independent piece in, so answering "yes"
     * there would claim the native mobile slot while drawing nothing.
     *
     * <p>The switch is still independent of its twin: it gates the row without the slot (a
     * reserved slot with the switch off draws nothing, as with Wi-Fi).
     *
     * <p>Deliberately not gated on the two-SIM condition {@link #dualSimRows}
     * needs: a row is one card's reading, so a single-SIM device can still place
     * SIM 1's row. SIM 2's row draws nothing there, which is what an absent
     * reading should look like.
     */
    public boolean drawsSim1() {
        return showSim1 && mobile && slotted && !signalOutOfRing
                && hasRingPart(Prefs.PART_SIM_1);
    }

    /** Whether the glyph draws SIM 2's own row this frame; see {@link #drawsSim1()}. */
    public boolean drawsSim2() {
        return showSim2 && mobile && slotted && !signalOutOfRing
                && hasRingPart(Prefs.PART_SIM_2);
    }

    /** Whether either per-card row is drawn, which is what the hooks must ask. */
    public boolean drawsAnySimRow() {
        return drawsSim1() || drawsSim2();
    }

    /**
     * Whether the module is drawing the signal OUTSIDE the glyph as its own stacked
     * reading, rather than leaving MIUI's icon alone.
     *
     * <p>This is the one predicate the hooks should ask before mounting the out-of-ring
     * view: the reading exists exactly while the signal is out of ring AND the user asked
     * for the stacked rendering. Out of ring with it off means "hand it back", and inside
     * the ring the glyph's own rows are the signal - in both cases drawing the stacked view
     * would show the same reading twice.
     */
    public boolean stackedOut() {
        return signalOutOfRing && stackedSignal;
    }

    /**
     * Whether MIUI's own mobile-signal slot has to be handed over, which is
     * exactly while this module draws that signal itself.
     *
     * <p>Stated here rather than inside {@code TrioHooks.foldedSlots()} so the rule can be
     * exercised without the Xposed API. The trap it encodes: folding the slot while drawing
     * nothing in its place would delete a signal indicator from the status bar. That is why
     * this asks "am I drawing the reading" rather than "did the user ask for a reading" -
     * the three in-ring predicates test the card placement, and {@link #stackedOut()}
     * covers the out-of-ring case.
     */
    public boolean foldsMobile() {
        return signalDots() || drawsAnySimRow() || stackedOut();
    }

    /** Whether the network type is drawn at all, in either position. */
    public boolean typeAnywhere() {
        return typeMode != Prefs.MOBILE_TYPE_OFF;
    }

    /**
     * The in-ring bolt's size multiplier, 1.0 for the reference size.
     *
     * <p>Clamped here rather than trusted from storage: a value left over from a build with
     * a wider span would otherwise draw a bolt that spills out of the ring's top gap.
     */
    public float boltScaleFactor() {
        return Prefs.clamp(boltScale, Prefs.MIN_BOLT_SCALE, Prefs.MAX_BOLT_SCALE) / 100f;
    }

    /**
     * The Wi-Fi arcs' size multiplier, 1.0 for the reference size.
     *
     * <p>Clamped here for the same reason as the bolt: this is the one place a value from a
     * broadcast or a stale build reaches the canvas, and an out-of-range arc would cross the
     * notch its own clearance exists to keep it out of.
     */
    public float wifiScaleFactor() {
        return Prefs.clamp(wifiScale, Prefs.MIN_WIFI_SCALE, Prefs.MAX_WIFI_SCALE) / 100f;
    }

    /**
     * The ring's level-dot size multiplier, 1.0 for the bead the stroke implies.
     *
     * <p>Clamped here for the same reason as {@link #boltScaleFactor()}: this is the one
     * place a value from a broadcast or a stale build reaches the canvas, and an
     * out-of-range bead would overlap the arc's caps.
     *
     * <p>A multiplier rather than a radius: the fitted radius already knows the opening's
     * room, and the user is asking to scale what is there, not to overrule the gap.
     */
    public float dotScaleFactor() {
        return Prefs.clamp(dotScale, Prefs.MIN_DOT_SCALE, Prefs.MAX_DOT_SCALE) / 100f;
    }

    /**
     * Whether the performance strip is drawn at all.
     *
     * <p>The two readouts share one view and one reserved strip, so this is the
     * question every caller that mounts or measures that view has to ask - never
     * {@link #perfPower} or {@link #perfTemp} alone. Either switch brings the
     * view back; with both off it is taken away entirely.
     */
    public boolean perfAnywhere() {
        return perfPower || perfTemp;
    }

    /**
     * The fit multiplier the glyph is drawn at, from {@link #glyphScale}.
     *
     * <p>Stated here rather than at the call site so the drawing, the window that
     * shows the same glyph and the settings preview cannot disagree about what the
     * slider means. An out-of-range value is clamped rather than trusted: the
     * stored number is clamped on read, but the broadcast path builds an
     * appearance from extras, and this is the one place a bad value would reach
     * the canvas.
     */
    public float glyphFit() {
        return Prefs.clamp(glyphScale, Prefs.MIN_GLYPH_SCALE, Prefs.MAX_GLYPH_SCALE) / 100f;
    }

    /**
     * The capsule's size multiplier, clamped.
     *
     * <p>Divided by {@link Prefs#CAPSULE_SCALE_UNIT}, not by 100: the number on the slider was
     * rebased so that its lower end renders what the old 100 did. Written here rather than at
     * the call site so the drawing and the preview cannot disagree, and because the broadcast
     * path can hand this class extras that never went through a clamp.
     *
     * <p>The shared {@code glyph_scale} is still the base - one number for what icon size
     * means - and this is the one shape-specific multiplier, because the ring's ink is a full
     * square while the capsule's is a short stadium, so equal percentages are not equal
     * heights on screen.
     */
    public float capsuleFit() {
        return Prefs.clamp(capsuleScale, Prefs.MIN_CAPSULE_SCALE, Prefs.MAX_CAPSULE_SCALE)
                / (float) Prefs.CAPSULE_SCALE_UNIT;
    }

    /**
     * The horizontal nudge in dp, clamped. Stated here for the same reason
     * {@link #glyphFit} is: the broadcast path and the draw path must not be able
     * to disagree about what a stored value means.
     */
    public int glyphOffsetXDp() {
        return Prefs.clamp(glyphOffsetX, Prefs.MIN_GLYPH_OFFSET, Prefs.MAX_GLYPH_OFFSET);
    }

    /** The vertical nudge in dp, clamped; see {@link #glyphOffsetXDp()}. */
    public int glyphOffsetYDp() {
        return Prefs.clamp(glyphOffsetY, Prefs.MIN_GLYPH_OFFSET, Prefs.MAX_GLYPH_OFFSET);
    }

    /** The icon area's own horizontal shift in dp, clamped; see {@link #glyphOffsetXDp()}. */
    public int iconOffsetXDp() {
        return Prefs.clamp(iconOffsetX, Prefs.MIN_GLYPH_OFFSET, Prefs.MAX_GLYPH_OFFSET);
    }

    /** The same, for the reading: {@code perfScale} as a multiplier. */
    public float perfFit() {
        return Prefs.clamp(perfScale, Prefs.MIN_PERF_SCALE, Prefs.MAX_PERF_SCALE) / 100f;
    }

    /**
     * The weight the two readouts are drawn at, from {@link #perfWeight}.
     *
     * <p>Stated here for the same reason {@link #glyphFit} is: the broadcast path
     * builds an appearance from extras, and this is the one place a bad weight
     * would reach the typeface. Not a second reader of {@link #typeWeight} - the
     * readout is status-bar text and the type weight also feeds the in-ring label.
     */
    public int perfTypeWeight() {
        return Prefs.clamp(perfWeight, Prefs.MIN_PERF_WEIGHT, Prefs.MAX_PERF_WEIGHT);
    }

    /** The ring arrangement's slot assignment for one frame. */
    public Ring ring(int level, boolean charging, int wifiLevel, String mobileType, int sims) {
        return ring(level, charging, wifiLevel, mobileType, sims, wifiLevel >= 0);
    }

    /**
     * The same, told outright whether Wi-Fi is connected.
     *
     * <p>The five-argument form infers it from the level, which is all a fixed preview scene can
     * say. The bar knows better: a live link at level 0 is connected, and -1 also covers "no
     * reading yet". See {@link #hidesTypeForWifi}.
     */
    public Ring ring(int level, boolean charging, int wifiLevel, String mobileType, int sims,
                     boolean wifiConnected) {
        return new Ring(this, level, charging, wifiLevel, mobileType, sims, wifiConnected);
    }

    /** The rectangular arrangement's slot assignment for one frame. */
    public Rect rect(int level, boolean charging, int wifiLevel, String mobileType, int sims) {
        return new Rect(this, level, charging, wifiLevel, mobileType, sims);
    }

    /** The capsule arrangement's slot assignment for one frame. */
    public Capsule capsule(int level, boolean charging, int wifiLevel, String mobileType,
                           int sims) {
        return capsule(level, charging, wifiLevel, mobileType, sims, wifiLevel >= 0);
    }

    /** The same, told outright whether Wi-Fi is connected; see {@link #hidesTypeForWifi}. */
    public Capsule capsule(int level, boolean charging, int wifiLevel, String mobileType,
                           int sims, boolean wifiConnected) {
        return new Capsule(this, level, charging, wifiLevel, mobileType, sims, wifiConnected);
    }

    /**
     * Where each piece goes in the ring arrangement.
     *
     * <p>The rule worth reading twice is {@code valueInCentre}: the percentage
     * takes the middle when the user asked for it, and <em>also</em> whenever
     * neither the arcs nor an in-glyph type label is competing for that space.
     * With Wi-Fi off nothing competes, so the switch is not needed to get the
     * centre - which is why its row is gated on Wi-Fi. Stated once here, the
     * renderer, the settings screen and the documentation cannot disagree about
     * it again.
     *
     * <p>Immutable.
     */
    public static final class Ring {
        /** Actual occupants this frame; null selects the original automatic layout. */
        public final int[] parts;
        /** The arcs leave ink this frame. */
        public final boolean wifi;
        /** The arcs are drawn at all. */
        public final boolean drawWifi;
        /** The arcs are shrunk into the notch to make room for the centre. */
        public final boolean wifiInGap;
        /** The bolt takes the notch. */
        public final boolean bolt;
        /** The percentage is drawn at all. */
        public final boolean drawValue;
        public final boolean valueInCentre;
        public final boolean valueInGap;
        /** The in-glyph network type is drawn, and where. */
        public final boolean typeInCentre;
        public final boolean typeInGap;
        /** Something occupies the notch, so the ring has to open for it. */
        public final boolean gapUsed;
        /** The two cards are drawn inside the ring this frame; see stacksDualRows. */
        public final boolean stackedDual;
        /**
         * The level dots are drawn as two rows, one per SIM, instead of one row
         * for the current data SIM.
         */
        public final boolean dual;

        /**
         * The renderer and editor use the same opening around the top component.
         *
         * <p>Only the <em>dual reading</em> widens the mouth: its top row is
         * {@code DOTS} mirrored about the ring centre, so the outer dots sit almost on
         * the arc's own radius and a narrow mouth runs the rounded stroke end into
         * them.
         *
         * <p>The ring's <b>bottom</b> opening is the reference for symmetry, not this
         * mouth - see {@code TrioGeometry.B_SWEEP}, where the arc's own bottom opening
         * was brought down to the top gap's width so the two halves mirror each other.
         */
        public float gapStart() {
            return !gapUsed ? TrioGeometry.GAP_NONE
                    : dual ? TrioGeometry.GAP_START_DUAL
                    : bolt ? TrioGeometry.GAP_START_CHARGE : TrioGeometry.GAP_START_IDLE;
        }

        public float gapEnd() {
            return !gapUsed ? TrioGeometry.GAP_NONE
                    : dual ? TrioGeometry.GAP_END_DUAL
                    : bolt ? TrioGeometry.GAP_END_CHARGE : TrioGeometry.GAP_END_IDLE;
        }

        /**
         * Whether the ring's bottom opening is drawn this frame.
         *
         * <p>The bottom of the ring exists to hold whatever the user put in the bottom
         * zone - normally the signal dots. With that zone empty the opening is a gap with
         * no purpose: a ring with a mouth at the bottom and nothing in it reads as a
         * broken outline rather than as a deliberate notch.
         *
         * <p>The <b>top</b> mouth already worked this way ({@link #gapStart()} collapses to
         * {@link TrioGeometry#GAP_NONE} when nothing is in the top zone), so this is the same
         * rule applied to the other end: a mouth exists while something sits in it.
         */
        public boolean bottomOpen() {
            return parts[2] != Prefs.PART_NONE;
        }

        /** The arc's angular sweep this frame; the full circle minus the bottom opening. */
        public float sweep() {
            return bottomOpen() ? TrioGeometry.B_SWEEP : 360f;
        }

        /**
         * The arc's start angle, holding the bottom opening's midpoint at 6 o'clock.
         *
         * <p>{@code 270 - sweep/2} for the same reason {@code TrioGeometry.B_START} spells
         * it that way: the opening must stay centred on straight-down whatever the sweep
         * becomes. With the bottom closed the sweep is 360 and the subtraction vanishes.
         */
        public float arcStart() {
            return 270f - sweep() / 2f;
        }

        /**
         * Whether one piece would draw ink on this frame, judging only its SWITCH and the
         * frame's readings - never the layout.
         *
         * <p>The layout is what is being decided here, so consulting it would be circular:
         * {@link #drawsBolt()} asks whether the bolt is placed, and asking that while working
         * out what to place would make the answer depend on itself. The switch alone is the
         * right question, because "is this piece wanted" and "is this the zone it goes in" are
         * separate, and this only answers the first.
         */
        private static boolean draws(TrioAppearance a, int part, int level, boolean charging,
                                     int wifiLevel, String mobileType) {
            return hasInk(a, part, level, charging, wifiLevel, mobileType);
        }

        /**
         * Resolves one frame from the three zones.
         *
         * <p>There is no automatic alternative any more: the slots are the layout, so
         * this reads them and decides only whether each piece has anything to draw on
         * this frame (a percentage needs a level, the arcs need a signal, and so on).
         * The old second path - which derived every position from switches and had a
         * priority order for the shared notch - is gone, and with it the case where the
         * same settings produced two different drawings depending on whether the user
         * had ever opened the editor.
         */
        private Ring(TrioAppearance a, int level, boolean charging, int wifiLevel,
                     String mobileType, int sims, boolean wifiConnected) {
            // ZONE_COUNT, not 3: the layout stores five sections now (three ring zones plus
            // the capsule's two), and this array is indexed by Zone.ordinal. The ring only
            // draws the first three; the rest resolve to PART_NONE because a ring install
            // has nothing in them, and a ring never asks for them anyway.
            parts = new int[Prefs.ZONE_COUNT];
            // The label yields to the arcs only when the arcs are OURS in this arrangement,
            // the label is inside the glyph, and Wi-Fi is actually connected. See
            // hidesTypeForWifi for why each of those matters; the per-band part - keeping a
            // band filled - is decided inside firstDrawableInk.
            final boolean yieldType = a.hidesTypeForWifi(wifiConnected);
            for (int z = 0; z < Prefs.ZONE_COUNT; z++) {
                // The zone draws the FIRST candidate that has something to show this frame.
                //
                // That is what makes the list a fallback chain rather than a stack: with
                // [percentage, arcs] the percentage takes the zone while it has a reading,
                // and the arcs step in on a frame where it does not - so a zone never goes
                // blank while any of its pieces could draw. Order is therefore the only
                // arbitration, which is exactly what dragging the list edits.
                //
                // Skipped as well are the placements the ring refuses in this band - a
                // signal row in the middle, see Prefs.zoneAccepts. Decoding a stored layout
                // repairs that already, but this loop is what the hooked process runs and it
                // can be reached from a broadcast that never went through a decode, so the
                // refusal is enforced here rather than trusted to have arrived.
                parts[z] = firstDrawableInk(a, a.ringZones[z], z,
                        level, charging, wifiLevel, mobileType, yieldType);
            }
            // Stacked dual reading: both cards move inside the ring, so neither mouth has to
            // stay open for them. Cleared here rather than at the drawing site because the
            // notch decision is made from these parts - a mouth left open with nothing
            // threaded on it reads as a broken outline, and keeping the pieces in would also
            // keep the type label squeezed out of the glyph, which is the conflict the
            // stacked form exists to solve.
            // The stacking does NOT take the pieces out of the bands. The mouth is part of the
            // shape the user arranged, and closing it was not the request: only the drawing
            // moves. Keeping the parts means the mouth opens exactly as before, and the
            // renderer skips re-drawing the rows in it (they are painted as the nested pair).
            stackedDual = a.stacksDualRows(sims);

            // A charger puts the number in the middle, whatever the layout arranged there.
            //
            // An override of what this FRAME shows, not an edit to what is stored: the
            // layout is the user's arrangement, and rewriting it on plug-in would then have
            // to be undone on unplug - losing whatever had been there if the two ever
            // disagreed. Resolved here, next to the other per-frame answers, so the drawing,
            // the preview window and the settings screen all see the same centre.
            //
            // Only the centre: the openings are what the ring was opened for, and a mouth
            // with something else in it would change the outline's shape as a side effect of
            // a preference about a number.
            if (charging && a.chargingShowsValue && level >= 0
                    && parts[TrioZones.Zone.CENTRE.ordinal()] != Prefs.PART_VALUE) {
                parts[TrioZones.Zone.CENTRE.ordinal()] = Prefs.PART_VALUE;
            }
            wifi = drawWifi = a.wifiInk(wifiLevel);
            bolt = charging && a.drawsBolt();
            drawValue = a.ownsValue() && level >= 0;
            valueInCentre = parts[1] == Prefs.PART_VALUE;
            valueInGap = parts[0] == Prefs.PART_VALUE;
            wifiInGap = parts[0] == Prefs.PART_WIFI;
            typeInCentre = parts[1] == Prefs.PART_TYPE;
            typeInGap = parts[0] == Prefs.PART_TYPE;
            gapUsed = parts[0] != Prefs.PART_NONE;
            // The dual switch does not change how a zone is drawn. It used to mean "add a
            // second row to the meter, and widen the notch to hold it" - but a zone draws one
            // piece, so a second row has nowhere to go. Two cards are expressed by placing
            // PART_SIM_1 and PART_SIM_2 in two different zones. Kept as a field (always false
            // here) so the renderer's parameters do not change shape for the rectangular
            // arrangement, which does still have two columns.
            dual = false;
        }
    }

    /**
     * Where each piece goes in the rectangular arrangement.
     *
     * <p>Note what is <em>not</em> decided here: the centring switch, the notch,
     * and any second thickness setting. The bar takes its thickness from
     * {@code ringStroke} and the arcs keep {@code arcStroke}; the settings screen
     * relabels that one row for this style rather than offering a near-identical
     * twin the renderer would then have to keep in step.
     *
     * <p>The single top slot is filled by the bolt, the arcs or the type label, in
     * that order, so those three are mutually exclusive by construction.
     *
     * <p>Immutable.
     */
    public static final class Rect {
        /** The mobile level dots are drawn. */
        public final boolean dots;
        /** The bolt takes the top slot. */
        public final boolean bolt;
        /** The Wi-Fi arcs take the top slot. */
        public final boolean wifi;
        /** The in-glyph network type takes the top slot. */
        public final boolean type;
        /** The percentage is drawn below. */
        public final boolean value;
        /**
         * The two dot columns carry one SIM each - SIM 1 left, SIM 2 right -
         * instead of both repeating the current data SIM.
         */
        public final boolean dual;

        private Rect(TrioAppearance a, int level, boolean charging, int wifiLevel,
                     String mobileType, int sims) {
            dots = a.signalDots();
            bolt = charging && a.drawsBolt();
            wifi = !bolt && a.wifiInk(wifiLevel);
            type = !bolt && !wifi && a.typeInRing
                    && mobileType != null && !mobileType.isEmpty();
            value = a.value && level >= 0;
            // Same plug-not-bolt rule as the ring: a charger on screen means one
            // reading, and the bar's colour already carries it.
            dual = a.dualSimRows(wifi, charging, sims);
        }
    }

    /**
     * Whether the bar's own clock has to step aside for this appearance.
     *
     * <p>True for the capsule or an assigned free-ring clock while drawing:
     * those arrangements put the time inside the icon, so
     * leaving the bar's clock visible would show the time twice. Turning the
     * module off has to bring it back, which is why this follows the master
     * switch and not just the style.
     *
     * <p>Stated as a rule rather than checked at the two call sites, so the
     * renderer drawing the time and the hook hiding the bar's copy cannot
     * disagree - the failure being either a duplicated clock or none at all.
     */
    /**
     * Whether the capsule's left cell is drawing the CLOCK on this frame.
     *
     * <p>An empty list is the shipped look, where that cell holds the clock. A list with
     * entries resolves through the same priority walk the drawing uses, so a piece that
     * has nothing to show does not count as having taken the cell - which is what keeps
     * the bar's own clock alive when the chosen reading is unavailable.
     */
    private boolean leftCellDrawsClock() {
        final int[] order = ringZones[TrioZones.Zone.LEFT.ordinal()];
        if (order.length == 0) {
            return true;
        }
        // Asked of the FIRST entry, not of a priority walk: this class is compiled on its
        // own by several harnesses and has no TrioState to read live readings from. What
        // it CAN answer is which piece the cell is trying to show, and the renderer -
        // which does have the readings - falls back to the bar's clock when that
        // piece turns out to have no ink.
        final int wanted = order[0];
        return wanted == Prefs.PART_CLOCK;
    }

    public boolean hidesStatusClock() {
        // The capsule hides the bar clock only while it is DRAWING the time. The left
        // cell is the user's choice now, so a capsule that put something else there
        // must leave the bar its clock - otherwise choosing Wi-Fi in that cell would
        // simply delete the time from the phone.
        // An EMPTY list means "nothing placed yet", which is the shipped look: the
        // left cell draws the clock there too. That is why the empty case counts as
        // the time rather than as nothing - reading it as nothing would bring the bar
        // clock back beside a capsule that is still drawing the time, and the time
        // would appear twice.
        final int[] leftOrder = ringZones[TrioZones.Zone.LEFT.ordinal()];
        // Whether the capsule is actually PUTTING INK in the left cell this frame, not
        // whether the list names the clock. A short list can name a reading that has
        // nothing to show - Wi-Fi with no connection, a type with no network - and
        // hiding the bar clock in that case left the phone with no time at all. The
        // rule is: the bar keeps its clock unless the capsule is really drawing one.
        final boolean capsuleDrawsTheTime = capsule && leftCellDrawsClock();
        return glyph && (capsuleDrawsTheTime || (ring && hasRingPart(Prefs.PART_CLOCK)));
    }

    /**
     * Whether {@code part} has ink to draw on this frame, whatever cell it would go in.
     *
     * <p>Deliberately not "is this the right zone": placement is the caller's question, and
     * this only answers "does this piece want to be drawn at all".
     *
     * <p>The three signal entries ask for {@link #ring} only because the per-card rows are
     * ring slot occupants; the capsule draws its dot rows from {@link #dots}, not from a
     * placed piece, so offering them there would name a slot it cannot fill.
     */
    private static boolean hasInk(TrioAppearance a, int part, int level, boolean charging,
                                  int wifiLevel, String mobileType) {
        switch (part) {
            case Prefs.PART_WIFI:
                return a.wifi && a.wifiInk(wifiLevel);
            case Prefs.PART_VALUE:
                return a.value && level >= 0;
            case Prefs.PART_BOLT:
                return a.boltWanted && charging;
            case Prefs.PART_TYPE:
                return a.typeInRing && mobileType != null && !mobileType.isEmpty();
            case Prefs.PART_MOBILE:
                return a.mobile && !a.signalOutOfRing && (a.ring ? a.hasRingPart(Prefs.PART_MOBILE) : true);
            // The per-card rows are placeable in BOTH slot-based arrangements: the ring threads
            // them onto its openings and the capsule lays them across its two. Only the
            // rectangle has no slot for one - it derives its columns from the mobile switch -
            // so `rect` is the exclusion here, not `!ring`.
            //
            // This used to read `a.ring`, which made the capsule's openings impossible to fill
            // from a layout even though the capsule has always drawn dots there: the rows came
            // from the mobile switch alone, so the user saw signal dots they could not move or
            // remove. See the Capsule class comment.
            case Prefs.PART_SIM_1:
                return a.mobile && a.showSim1 && !a.signalOutOfRing && !a.rect;
            case Prefs.PART_SIM_2:
                return a.mobile && a.showSim2 && !a.signalOutOfRing && !a.rect;
            case Prefs.PART_CLOCK:
                // The clock always has something to say; whether the arrangement shows it
                // is the renderer's business.
                return true;
            default:
                return false;
        }
    }

    /**
     * The first entry of {@code order} that has ink on this frame, or {@link
     * Prefs#PART_NONE}.
     *
     * <p>Skipping rather than stopping is the whole behaviour: a candidate whose own
     * switch is off, or which has no reading to show, is simply passed over and the next
     * one is tried. That is why a zone does not go blank when the top of its list happens
     * to be a piece with nothing to draw.
     *
     * <p>Each condition is the same one that piece would test on its own - a percentage
     * needs a level, the arcs need a signal, the bolt only exists while charging, and a
     * card answers to its own switch - so a place's list cannot resurrect something the
     * user switched off.
     *
     * <p>Lives on the outer class rather than in {@link Ring} because the capsule's cells
     * need it too: both arrangements read a priority list per cell, and two copies of this
     * rule would be two places for "what counts as ink" to drift apart.
     */
    private static int firstInk(TrioAppearance a, int[] order, int level,
                                boolean charging, int wifiLevel, String mobileType) {
        if (order == null) {
            return Prefs.PART_NONE;
        }
        for (int i = 0; i < order.length; i++) {
            if (hasInk(a, order[i], level, charging, wifiLevel, mobileType)) {
                return order[i];
            }
        }
        return Prefs.PART_NONE;
    }

    /**
     * The same, for one band of the ring, where a piece can also be refused by
     * {@link Prefs#zoneAccepts(int, int)}.
     *
     * <p>A layout stored before that rule existed can still name a signal row in the middle
     * band, and decoding repairs it - but the drawing has to be right on its own too: it is
     * what the hooked process runs, it is reachable from a broadcast that never went through
     * a decode, and a band's occupant is otherwise decided by this one loop. So a refused
     * candidate is passed over exactly as a switched-off one is, and the fallback behind it
     * takes the band.
     *
     * <p>Asked of {@code Prefs} rather than of {@code TrioZones} on purpose: this class is
     * compiled on its own by several harnesses, and it already has no dependency on the
     * editor's geometry. The rule is the same one either way - {@code TrioZones} defers to
     * the same method.
     *
     * <p>Passing the refused candidate instead would draw the very thing the rule exists to
     * prevent; leaving the band blank would contradict the list the user can see in the
     * priority editor.
     */
    private static int firstDrawableInk(TrioAppearance a, int[] order, int band,
                                        int level, boolean charging, int wifiLevel,
                                        String mobileType) {
        return firstDrawableInk(a, order, band, level, charging, wifiLevel, mobileType, false);
    }

    /**
     * The same, told whether the arcs are up and should displace the type label.
     *
     * <p>When {@code yieldType} is set and the type is the candidate reached, it is skipped -
     * but only if another entry behind it can draw. The band is the user's arrangement made
     * visible; leaving it blank because the piece that would have filled it stepped aside is
     * worse than the crowding the step-aside was for. With nothing behind it the label stays,
     * which is the "keep the band filled" case of the feature.
     */
    private static int firstDrawableInk(TrioAppearance a, int[] order, int band,
                                        int level, boolean charging, int wifiLevel,
                                        String mobileType, boolean yieldType) {
        if (order == null) {
            return Prefs.PART_NONE;
        }
        for (int i = 0; i < order.length; i++) {
            final int id = order[i];
            if (a.refusesInBand(band, id)) {
                continue;
            }
            if (!hasInk(a, id, level, charging, wifiLevel, mobileType)) {
                continue;
            }
            if (yieldType && id == Prefs.PART_TYPE
                    && hasDrawableBehind(a, order, i + 1, band, level, charging, wifiLevel,
                            mobileType)) {
                continue;
            }
            return id;
        }
        return Prefs.PART_NONE;
    }

    /**
     * Whether this arrangement refuses {@code id} in {@code band}.
     *
     * <p>{@link Prefs#zoneAccepts(int, int)} states the rule for the <b>ring's</b> middle
     * band: a row of beads threaded across the closed battery arc reads as a smudge, so the
     * ring's centre belongs to readings. The capsule's interior is a body cell, not a
     * notched opening, and its rows carry their own baseline - {@code TrioZones.accepts}
     * has said so for a long time, which is why the editing page offers a signal row there.
     *
     * <p>Asking the ring's rule of every arrangement is what made that offer a lie: the
     * editor accepted the placement, then this resolve loop skipped the very piece that had
     * just been chosen, and the cell came up empty. The rule is now asked of the arrangement
     * that is actually being drawn.
     */
    private boolean refusesInBand(int band, int id) {
        return !(capsule || rect) && !Prefs.zoneAccepts(band, id);
    }

    /** Whether any entry from {@code from} on can occupy {@code band} this frame. */
    private static boolean hasDrawableBehind(TrioAppearance a, int[] order, int from, int band,
                                             int level, boolean charging, int wifiLevel,
                                             String mobileType) {
        for (int i = from; i < order.length; i++) {
            final int id = order[i];
            if (!a.refusesInBand(band, id)
                    && hasInk(a, id, level, charging, wifiLevel, mobileType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Where each piece goes in the capsule arrangement.
     *
     * <p>The capsule has the same <b>three</b> placeable bands as the ring, mapped onto its own
     * shape: the top opening, the interior to the right of the clock, and the bottom opening.
     * "Top, middle, bottom" is the question the user is asking in every style, so the capsule
     * does not invent a different vocabulary for the same answer.
     *
     * <p>The clock is <b>fixed in the left column and is not a zone</b>: it is drawn
     * unconditionally, and no placement can move it. That is why the capsule has three cells
     * and not four - a fourth would be one whose contents could not be changed.
     *
     * <p>The right column's interior is one cell rather than two halves: splitting it would
     * give each half less than a line of text at the sizes the settings offer, so a piece put
     * there would either overflow or be shrunk below what the user asked for.
     *
     * <p><b>Migration.</b> A capsule install that predates these cells has an empty layout, so
     * every cell would draw nothing and the capsule would go blank on upgrade. When no cell
     * has anything placed, the cells fall back to the pre-cell arbitration (one reading in the
     * interior), which reproduces the shipped look exactly. Once the user places anything the
     * layout is authoritative.
     *
     * <p><b>The two dot rows are a placement like any other.</b> They used to be drawn from
     * {@link TrioAppearance#signalDots()} alone, which made them the one piece of capsule ink
     * the user could see and could not move or remove: the openings held dots whether or not
     * any cell named them, so tapping the top band offered only readings. A {@code PART_SIM_*}
     * row placed in TOP/BOTTOM now decides which opening carries dots, exactly as it does in
     * the ring; {@link #dots} is true when either band holds one, and an install with nothing
     * placed keeps the old always-on look through the same migration branch as the readings.
     *
     * <p>Immutable.
     */
    public static final class Capsule {
        /** The charging bolt is drawn in some cell. */
        public final boolean bolt;
        /** The Wi-Fi arcs are drawn in some cell. */
        public final boolean wifi;
        /** The battery percentage is drawn in some cell. */
        public final boolean value;
        /** The network type is drawn in some cell. */
        public final boolean type;
        /** The time is drawn in some cell. */
        public final boolean clock;
        /**
         * The dot rows are drawn at all - i.e. at least one opening holds one.
         *
         * <p>Ask {@link #dotsIn} for a specific band. This is the question the outline's gap
         * and the charged-fraction pass ask, because the stroke is cut at both openings
         * whether one row or two are there, and a half-drawn outline would read as damage.
         */
        public final boolean dots;
        /** The rows carry one SIM each - SIM 1 on top, SIM 2 below. */
        public final boolean dual;
        /**
         * Which bands carry a dot row, indexed by {@link TrioZones.Zone#ordinal}.
         *
         * <p>False for the interior: it is a reading's band in every arrangement. The two
         * openings are independent, so a user can draw SIM 1 at the top and nothing below.
         */
        public final boolean[] dotBands;
        /**
         * Per-cell occupants, indexed by {@link TrioZones.Zone#ordinal}, or
         * {@code PART_NONE} for a cell that draws nothing.
         */
        public final int[] parts;

        private Capsule(TrioAppearance a, int level, boolean charging, int wifiLevel,
                        String mobileType, int sims, boolean wifiConnected) {
            parts = new int[Prefs.ZONE_COUNT];
            dotBands = new boolean[Prefs.ZONE_COUNT];

            // Every band is a real cell now: the two openings hold either a reading or a dot
            // row, and only the interior is a reading's alone.
            // See hidesTypeForWifi: the label yields only when the arcs are ours here, the
            // label is inside the glyph, and Wi-Fi is really connected. Whether the band then
            // stays filled is decided per zone inside firstDrawableInk.
            final boolean yieldType = a.hidesTypeForWifi(wifiConnected);
            for (TrioZones.Zone band : TrioZones.Zone.values()) {
                // The capsule reads FOUR cells: the three bands plus the left column, which
            // is the layout's LEFT section. It used to be a key of its own, which is
            // why this loop ran over the three bands only and the drawing dispatched the
            // left column separately.
            parts[band.ordinal()] = firstDrawableInk(a, a.ringZones[band.ordinal()],
                        band.ordinal(), level, charging, wifiLevel, mobileType, yieldType);
            }
            for (TrioZones.Zone band : TrioZones.Zone.values()) {
                dotBands[band.ordinal()] = isDotRow(parts[band.ordinal()]);
            }

            // The left column comes from the layout's own section. The legacy
            // capsule_left key is consulted only when that section is empty, so an
            // install that set the column through the old control keeps its choice
            // and the layout becomes the single source once the user picks something.
            // From the layout, by the same priority rule the bands use: the first
            // candidate that has ink this frame wins, the ones behind it are fallbacks.
            //
            // What it must NOT do is fall back to the old stand-alone capsule_left key -
            // which is what it used to do when the list was empty. That key is never
            // updated by the ordering rows, so the cell kept drawing whatever it held
            // (Wi-Fi, in practice) no matter what the list said, and the preview looked
            // stuck.
            parts[TrioZones.Zone.LEFT.ordinal()] = firstInk(
                    a, a.ringZones[TrioZones.Zone.LEFT.ordinal()],
                    level, charging, wifiLevel, mobileType);
            // "Has the user placed anything at all?" - which decides whether the
            // shipped look is used. The LEFT cell counts only when the LAYOUT named
            // something there: a cell filled from the list is a placement, while the
            // clock this class puts there for an untouched install is not. Getting that
            // distinction wrong is what made the cell ignore the list and draw the
            // clock whatever the user picked.
            final boolean anyPlaced = anyPlacedExceptLeft(parts)
                    || a.ringZones[TrioZones.Zone.LEFT.ordinal()].length > 0
                    || dotBands[TrioZones.Zone.TOP.ordinal()]
                    || dotBands[TrioZones.Zone.BOTTOM.ordinal()];
            if (!anyPlaced) {
                // Nothing has ever been placed: behave exactly as before these cells existed,
                // so an upgrading install looks unchanged. The clock is fixed either way.
                final boolean boltWanted = charging && a.drawsBolt();
                final boolean wifiWanted = !boltWanted && a.wifiInk(wifiLevel);
                final boolean valueWanted = !boltWanted && !wifiWanted
                        && a.value && level >= 0;
                final boolean typeWanted = !boltWanted && !wifiWanted && !valueWanted
                        && a.typeInRing && mobileType != null && !mobileType.isEmpty();
                parts[TrioZones.Zone.CENTRE.ordinal()] =
                        boltWanted ? Prefs.PART_BOLT
                        : wifiWanted ? Prefs.PART_WIFI
                        : valueWanted ? Prefs.PART_VALUE
                        : typeWanted ? Prefs.PART_TYPE
                        : Prefs.PART_NONE;
                // And the shipped look's dot rows, which this arrangement always drew. They
                // are the SAME condition signalDots() applies, so an untouched install draws
                // precisely the pixels it used to.
                final boolean legacyDots = a.signalDots();
                dotBands[TrioZones.Zone.TOP.ordinal()] = legacyDots;
                dotBands[TrioZones.Zone.BOTTOM.ordinal()] = legacyDots;
                // The left cell, which used to be a fixed column holding the time.
                // An install that has never placed anything keeps exactly that: the
                // clock, drawn where it always was. Without this the shipped look
                // would come up with an empty left half and the time in the bar
                // instead - a visible change on upgrade.
                parts[TrioZones.Zone.LEFT.ordinal()] = Prefs.PART_CLOCK;
            }

            // A band that holds a dot row draws the row, not a reading: the row IS that band's
            // piece. Clearing `parts` for it keeps the two answers from both claiming the band.
            for (TrioZones.Zone band : TrioZones.Zone.values()) {
                if (dotBands[band.ordinal()]) {
                    parts[band.ordinal()] = Prefs.PART_NONE;
                }
            }

            dots = dotBands[TrioZones.Zone.TOP.ordinal()]
                    || dotBands[TrioZones.Zone.BOTTOM.ordinal()];
            dual = a.dualSimRows(false, charging, sims);
            // The left column counts as a cell here. These four flags answer "does this
            // piece have ink anywhere in the capsule on this frame", and the outline
            // gap, the charged-fraction pass and the drawing all read them: leaving the
            // left column out meant a piece placed there reported no ink, so nothing
            // was drawn - the column looked simply empty (issues 44 and 47).
            final int leftPart = parts[TrioZones.Zone.LEFT.ordinal()];
            final boolean leftWifi = leftPart == Prefs.PART_WIFI;
            final boolean leftValue = leftPart == Prefs.PART_VALUE;
            final boolean leftType = leftPart == Prefs.PART_TYPE;
            final boolean leftBolt = leftPart == Prefs.PART_BOLT;
            bolt = contains(parts, Prefs.PART_BOLT) || leftBolt;
            wifi = contains(parts, Prefs.PART_WIFI) || leftWifi;
            value = contains(parts, Prefs.PART_VALUE) || leftValue;
            type = contains(parts, Prefs.PART_TYPE) || leftType;
            // The clock is not a placement: it is always drawn, in its own column.
            clock = true;
        }

        /** Whether {@code band} carries a row of level dots. */
        public boolean dotsIn(TrioZones.Zone band) {
            return dotBands != null && band != null && dotBands[band.ordinal()];
        }

        /** Whether {@code id} is one of the two rows of level dots. */
        private static boolean isDotRow(int id) {
            return id == Prefs.PART_SIM_1 || id == Prefs.PART_SIM_2
                    || id == Prefs.PART_MOBILE;
        }

        /** As {@link #anyPlaced}, ignoring the capsule's LEFT cell. */
    private static boolean anyPlacedExceptLeft(int[] ids) {
        for (TrioZones.Zone zone : TrioZones.Zone.values()) {
            if (zone != TrioZones.Zone.LEFT && ids[zone.ordinal()] != Prefs.PART_NONE) {
                return true;
            }
        }
        return false;
    }

    private static boolean anyPlaced(int[] ids) {
            for (int id : ids) {
                if (id != Prefs.PART_NONE) {
                    return true;
                }
            }
            return false;
        }

        private static boolean contains(int[] ids, int id) {
            for (int existing : ids) {
                if (existing == id) {
                    return true;
                }
            }
            return false;
        }
    }
}
