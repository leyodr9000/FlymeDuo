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
 * Geometry of the "trio" glyph in a 120x120 design space. All units are design
 * units.
 */
final class TrioGeometry {

    private TrioGeometry() {
    }

    /** The SVG viewBox. Everything is drawn inside 0..120 on both axes. */
    static final float BOX = 120f;

    // ---------------------------------------------------------------- battery
    /** Arc centre: the two endpoints (15.5,88.25) and (103.5,88.25) with r=51.5. */
    static final float B_CX = 59.5f;
    static final float B_CY = 61.48715261785473f;
    static final float B_R = 51.5f;

    // ---------------------------------------------- stacked dual reading
    //
    // Two rows of beads inside the ring, curved along its own wall. Every figure below is a
    // ratio of the ring stroke so the reading scales with the stroke the user chose: the bead
    // is 0.30 of it (a little under a third, which reads as secondary to the ring without
    // turning into a dotted line) and the breathing room is the same 0.30, so the two rows and
    // the ring are separated by one even gap rather than by two guesses.
    //
    // The inner row keeps the SAME angular span and shrinks its beads in proportion to its
    // radius. That is the rule the Wi-Fi arcs already follow - nesting arcs shrink as they go
    // in - and it is what makes two concentric rows read as parallel instead of leaving the
    // inner row looking cramped against the outer one.
    /** Total arc the four beads of one row span, centred on 6 o'clock. */
    static final float STACK_SPAN_DEG = 46f;
    /** Bead radius of the outer row. */
    static float stackBeadR(float stroke) {
        return 0.30f * stroke;
    }
    /** Centreline radius of the outer row: one bead and one gap in from the ring's inner edge. */
    static float stackOuterRadius(float stroke) {
        return B_R - stroke * 0.5f - 0.30f * stroke - stackBeadR(stroke);
    }
    /** Centreline radius of the inner row: two beads and one gap in from the outer one. */
    static float stackInnerRadius(float stroke) {
        return stackOuterRadius(stroke) - (2f * stackBeadR(stroke) + 0.30f * stroke);
    }
    /** The inner row's bead, scaled by the ratio of the two radii; see the note above. */
    static float stackInnerBeadR(float stroke) {
        return stackBeadR(stroke) * (stackInnerRadius(stroke) / stackOuterRadius(stroke));
    }
    /** Default ring stroke; the user's {@code ring_stroke} setting overrides it. */
    static final float B_STROKE = 8f;

    /**
     * The width of the ring's 12 o'clock opening, in degrees.
     *
     * <p>This is the <em>reference</em> opening: the arc's own bottom gap is set to
     * match it (see {@link #B_SWEEP}), so a ring with content at both ends reads as
     * two mirrored halves rather than as a long bottom and a short top.
     *
     * <p>Stated in degrees rather than as a fraction of the sweep, so that changing the
     * sweep cannot silently change how wide the notch is - it used to be
     * {@code 0.35 * B_SWEEP}, which meant widening the arc by 32 degrees also widened
     * the notch by 11.
     */
    static final float GAP_WIDTH_DEG = 96f;

    /**
     * The angular span the four card beads occupy, in degrees.
     *
     * <p>Independent of {@link #GAP_WIDTH_DEG} on purpose. The notch is wider than the
     * beads need, so the arc's ends keep their distance from them; if the beads were
     * spread to fill whatever the opening allows, widening the notch would drag the
     * beads outward with it and the clearance would never change.
     *
     * <p>60.4 degrees is the span the beads already have at the default stroke, where
     * the spacing looked right: four beads, one diameter apart at the ends.
     */
    static final float BEAD_SPAN_DEG = 60.4f;

    /**
     * Clockwise sweep (deg). The missing part is at the BOTTOM.
     *
     * <p>Derived from {@link #GAP_WIDTH_DEG}: the bottom opening is
     * {@code 360 - B_SWEEP}, and making it equal the top gap is what centres the glyph
     * vertically. It used to be 242.62, leaving 117.38 degrees bare at the bottom
     * against 84.92 at the top - 16.2 degrees short on each side of the notch, which
     * reads as "the lower half of the ring is shorter".
     */
    static final float B_SWEEP = 360f - GAP_WIDTH_DEG;

    /**
     * Start angle (deg), placed so the arc's bottom opening is centred on 6 o'clock.
     *
     * <p>{@code 270 - sweep/2} puts the midpoint of the missing part at 90 degrees
     * (straight down). Spelling it out from the sweep rather than keeping the old
     * absolute 148.69 is what keeps the opening centred if the sweep changes again.
     */
    static final float B_START = 270f - B_SWEEP / 2f;

    /**
     * The 12 o'clock opening, as path progress in {@code 0..1} of the sweep.
     *
     * <p>Converted from {@link #GAP_WIDTH_DEG} so the notch keeps its width in
     * <em>degrees</em> whatever the sweep is. Centred on 0.5, which is 12 o'clock.
     */
    static final float GAP_START_IDLE = 0.5f - GAP_WIDTH_DEG / 2f / B_SWEEP;
    static final float GAP_END_IDLE = 0.5f + GAP_WIDTH_DEG / 2f / B_SWEEP;

    /** The charging mouth: the same width, as it always was for a bolt or two digits. */
    static final float GAP_START_CHARGE = GAP_START_IDLE;
    static final float GAP_END_CHARGE = GAP_END_IDLE;

    /**
     * The 12 o'clock gap used by the dual-SIM reading: exactly as wide as the
     * opening the arc already leaves bare at the bottom, so the two rows of dots
     * sit in matching mouths.
     *
     * <p>{@link #GAP_START_IDLE} is sized for a bolt or two digits. Measured
     * against the dots it is too narrow: the top row is {@link #DOTS} mirrored
     * about {@link #B_CY}, so its outer dots reach the arc's own radius, and the
     * rounded stroke ends are then only ~10.4 units from the outer dot centres
     * while a cap (stroke/2) plus a dot ({@link #DUAL_DOT_R}) is 14. The cap and
     * the dot merge into one blob and the top row reads as if the ring had cut it
     * off. The bottom row never had this problem because it sits in the arc's
     * natural opening, which is 32.5 degrees wider.
     *
     * <p>Opening the notch to that natural width is what "the rows are symmetric"
     * means geometrically, and it hands the top row the same clearance the bottom
     * row has (~10 units instead of an overlap of ~3.6).
     *
     * <p>Both bounds are derived from {@link #B_SWEEP} rather than typed in: the
     * bare part of the circle is {@code 360 - B_SWEEP}, so its half is
     * {@code 180 / B_SWEEP - 0.5} of the path - measured either side of t=0.5,
     * which is 12 o'clock.
     */
    static final float GAP_START_DUAL = 1f - 180f / B_SWEEP;
    static final float GAP_END_DUAL = 180f / B_SWEEP;

    /**
     * Gap used when nothing is drawn in the 12 o'clock slot.
     *
     * <p>A zero-width gap at position 0 makes the ring draw as one unbroken arc:
     * with {@code gapStart == gapEnd == 0} the two halves of
     * {@code batteryRing}'s split collapse into a single 0..to sweep. An empty
     * mouth is not a feature, so the ring simply closes.
     */
    static final float GAP_NONE = 0f;

    /** Path progress t (0..1) -> angle in degrees. */
    static float bAngle(float t) {
        return B_START + t * B_SWEEP;
    }

    // ----------------------------------------------------------------- charging
    /**
     * The charging bolt, scaled up from the reference path.
     *
     * <p>The reference path only spans x 51.3..67.6 and y 2.2..21.6 鈥?a pinched
     * sliver next to a 103-unit ring. {@link #BOLT_SCALE} grows it about
     * {@link #BOLT_CX} and pins its top edge to {@link #BOLT_TOP}, so it fills
     * the opened 12 o'clock gap without leaving the 120x120 box.
     *
     * <p>The renderer draws the path with
     * {@code translate(boltOffsetX(), boltOffsetY())} then
     * {@code scale(BOLT_SCALE)}: a reference point {@code (x, y)} lands on
     * {@code (x * BOLT_SCALE + boltOffsetX(), y * BOLT_SCALE + boltOffsetY())}.
     */
    static final float BOLT_CX = 59.45f;
    static final float BOLT_TOP = 1.5f;
    /** Top edge and horizontal centre of the reference path, before scaling. */
    static final float BOLT_TOP_REF = 2.2f;
    static final float BOLT_CX_REF = 59.45f;
    /**
     * Chosen against the default ring stroke: the reference bolt is sized for a
     * stroke of 8 design units, and the shipping default is 14, so a much larger
     * scale is needed for the two to read as one icon.
     */
    static final float BOLT_SCALE = 1.6f;

    static float boltOffsetX() {
        return BOLT_CX - BOLT_CX_REF * BOLT_SCALE;
    }

    static float boltOffsetY() {
        return BOLT_TOP - BOLT_TOP_REF * BOLT_SCALE;
    }

    /**
     * The same offset for a bolt scaled by {@code factor} about its ink centre.
     *
     * <p>{@code offset = target - reference * scale}, the shape every bolt offset here
     * uses. Multiplying the existing offset instead would translate the bolt rather than
     * grow it around {@link #BOLT_CX}/{@link #BOLT_TOP}, so a larger setting would drift up
     * and to the left out of the notch.
     */
    static float boltOffsetX(float factor) {
        return BOLT_CX - BOLT_CX_REF * (BOLT_SCALE * factor);
    }

    static float boltOffsetY(float factor) {
        return BOLT_TOP - BOLT_TOP_REF * (BOLT_SCALE * factor);
    }

    // ------------------------------------------------------------------- value
    static final float VALUE_X = 59.5f;
    static final float VALUE_BASELINE = 24f;
    static final float VALUE_SIZE = 32f;
    /** SVG letter-spacing="-0.04em". */
    static final float VALUE_LETTER_SPACING = -0.04f;

    /**
     * The character the reference draws smaller than the rest of the network
     * type: the "A" of "5GA".
     *
     * <p>Kept here rather than in either drawing path so the ring canvas and the
     * out-of-ring label cannot end up disagreeing about which glyph is the
     * suffix. MIUI reports the type as one string ("5G", "5GA"), so the only
     * signal available is the trailing character.
     */
    static final char TYPE_SUFFIX = 'A';

    /**
     * Whether {@code type} ends in a suffix that should be drawn smaller.
     *
     * <p>Both conditions matter: {@code scalePercent} of 100 is "no shrink", and
     * a bare "A" is the whole label rather than a suffix of one - shrinking it
     * would just draw the label smaller, which is what the size slider is for.
     */
    static boolean hasShrunkSuffix(String type, int scalePercent) {
        return scalePercent < 100
                && type != null
                && type.length() > 1
                && type.charAt(type.length() - 1) == TYPE_SUFFIX;
    }

    /** {@code type} without its trailing {@link #TYPE_SUFFIX}. */
    static String typeBase(String type) {
        return type.substring(0, type.length() - 1);
    }

    /**
     * Vertical centre of the ring interior. The level is drawn here once the
     * Wi-Fi area is empty, so the hole left by a missing Wi-Fi glyph carries the
     * battery percentage instead of staying blank.
     */
    static final float CENTER_CY = 64f;
    /** Cap height of the digits, in em. Roboto's is ~0.71em. */
    static final float CAP_HEIGHT_RATIO = 0.71f;
    /**
     * Height of a digit's visual centre above its baseline, in em. Digits fill
     * the cap, so their centre sits half of the cap height above the baseline.
     */
    static final float CAP_CENTRE_RATIO = CAP_HEIGHT_RATIO / 2f;

    /**
     * Smallest y a value's cap may start at when it is drawn in the top gap.
     * Anything above this leaves the 120x120 box and gets clipped by the host.
     *
     * <p>Not 0: the cap sits on a curve, and font metrics differ by a hair
     * between Roboto and the measurement used here, so a cap exactly on the ink
     * edge loses its top row to antialiasing. Two and a half units of headroom
     * costs nothing visually and keeps every digit whole.
     */
    static final float GAP_TOP = 2.5f;

    /**
     * Baseline for a value drawn in the 12 o'clock gap. {@link #VALUE_BASELINE}
     * is the design baseline, but a larger font would push the cap above
     * {@link #GAP_TOP}, so the line is dropped just enough to keep it inside.
     */
    static float gapBaseline(float size) {
        return Math.max(VALUE_BASELINE, GAP_TOP + size * CAP_HEIGHT_RATIO);
    }

    /**
     * The centre of the ring gives the value far more room than the narrow top
     * gap it normally lives in, so a centred value is drawn this much larger.
     *
     * <p>1.5 crowded the ring: the digits ran into the stroke on both sides once
     * the ring was thickened. 1.3 still reads as "the big number" while keeping
     * a visible margin against the inner edge.
     */
    static final float CENTRE_SIZE_RATIO = 1.4f;
    /** Upper bound for {@link #CENTRE_SIZE_RATIO}: "100" must stay inside the ring. */
    static final float CENTRE_MAX_SIZE = 52f;

    /** Font size actually used for a centred value of configured {@code size}. */
    static float centreSize(float size) {
        return Math.min(size * CENTRE_SIZE_RATIO, CENTRE_MAX_SIZE);
    }

    /** Baseline that centres digits of {@code size} on {@link #CENTER_CY}. */
    static float centerBaseline(float size) {
        return CENTER_CY + size * CAP_CENTRE_RATIO;
    }

    /**
     * Clear horizontal room for text drawn inside the ring. The ring's inner
     * edge is one stroke width inside its centre line; the text's cap corners
     * must stay within that circle, so the room narrows as it gets taller.
     *
     * <p>Both centre slots go through here: the percentage when Wi-Fi is off,
     * and the mobile network type ({@code Prefs.KEY_TYPE_SIZE}) when it replaces
     * the percentage. Sizing them independently is safe because neither can
     * overrun the ring.
     */
    static float centreClearWidth(float size, float stroke) {
        final float inner = B_R - stroke * 0.5f - 1f;
        final float dy = size * CAP_HEIGHT_RATIO * 0.5f;
        final float dx = (float) Math.sqrt(Math.max(0f, inner * inner - dy * dy));
        return dx * 2f;
    }

    /**
     * Clear horizontal room for a value drawn in the 12 o'clock gap: the chord
     * between the two rounded arc ends, less one stroke width for those caps.
     */
    static float gapClearWidth(float stroke) {
        final double half = Math.toRadians((GAP_END_IDLE - GAP_START_IDLE) * B_SWEEP * 0.5f);
        return (float) (2.0 * B_R * Math.sin(half)) - stroke;
    }

    // -------------------------------------------------- free-ring slot thirds
    //
    // The three tappable thirds of the ring, in design units. The renderer places a
    // piece in one and sizes any text by the room the third gives, and TrioZones
    // hit-tests the same cuts - so the numbers live here once and both read them.
    // Two copies of a cut line is how a hot spot ends up beside the piece it names.

    /**
     * Cut between the top third and the middle one.
     *
     * <p><b>38, unchanged.</b> Moving this was tried and reverted: the cut is not
     * only a size hint, it is the line {@code TrioZones} hit-tests and the line
     * {@code tests/drawing-zones.ps1} attributes every painted pixel to. Nudging it
     * to 40 re-attributed the top of the Wi-Fi arcs to the top third and the harness
     * failed with "WIFI loses ink only in the thirds that name it: got TOP".
     *
     * <p>The type's size problem is therefore solved on the type's own terms - see
     * {@link #zoneTextRoomH} - rather than by redividing a box whose division other
     * things depend on.
     */
    static final float ZONE_CUT_TOP = 38f;
    /**
     * Cut between the middle third and the bottom one.
     *
     * <p><b>86, raised from 92.</b> This is the one cut that had to move, and the
     * reason is measured rather than aesthetic: a placed reading is sized by the
     * room its third gives, and the bottom third used to get
     * {@code 2 * (104 - 92) = 24} units - about 15pt for a reading the user asked to
     * be 36. Moving the cut up 6 units gives 36, which is the whole difference
     * between "half the size you asked for" and "what the slider says".
     *
     * <p>What stops it going further is the middle third's tallest drawn piece: a
     * bolt fitted to the slot box is 44 units tall about a centre line at 64, so it
     * reaches y = 86. At 86 the two just touch, which is why the harness is what
     * decides this number rather than a round figure.
     *
     * <p>The top cut stays at 38 on purpose: it is also the line the editor
     * hit-tests, and moving it re-attributed the top of the Wi-Fi arcs to another
     * third.
     */
    static final float ZONE_CUT_BOTTOM = 86f;

    /**
     * The vertical centre each zone places its piece on: {@code 0} top,
     * {@code 1} middle, {@code 2} bottom.
     *
     * <p>Fixed numbers rather than the middles of the thirds, and that is
     * deliberate: the drawn figures - the arcs, the bolt, the dot rows - have their
     * own sizes and were tuned against these centres, so re-centring them whenever
     * a cut line moves would shift drawings that nobody asked to move. The cuts
     * divide the <em>hot zones</em>; these say where a piece is painted.
     *
     * <p>Text is the one piece that is also bounded by the third it sits in (a
     * reading has to fit the band it belongs to), which is why
     * {@link #zoneTextRoomH} measures around these centres rather than around the
     * cuts' middles - a mismatch there would fit the type to a band it is not drawn
     * in.
     */
    static final float[] ZONE_CY = { 19f, 64f, 104f };

    /**
     * The vertical room a placed text piece may use in zone {@code z}, in design
     * units.
     *
     * <p>Two different rules, by design:
     *
     * <ul>
     *   <li><b>Top and bottom</b> are limited by the <em>signal-dot box</em> - the
     *       28 and 20 units the level dots were designed into. Those two slots exist
     *       to hold a row of dots on the ring's own openings, so a reading placed
     *       there is a guest in the dots' space and takes the dots' measure.
     *   <li><b>Middle</b> is limited by the ring's interior, which is the largest
     *       plainly available area in the glyph and the place a reading is actually
     *       meant to sit.
     * </ul>
     *
     * <p>Stating it this way is what keeps the three slots from having to share one
     * compromised number: the middle gets the room it visibly has, while the two end
     * slots stay in proportion with the dots they stand in for.
     */
    static float zoneTextRoomH(int z) {
        if (z == 1) {
            // The ring's interior, less a margin so the ink does not touch the arc.
            // The interior is measured on the arc's inside edge; the extra two units
            // clear the antialiased rim on both horizontal edges of the line, which is
            // what turned "fits by measurement" into a two-pixel overlap.
            return 2f * (B_R - Prefs.DEF_RING_STROKE) - 2f * ZONE_TEXT_INSET
                    - 2f * ZONE_TYPE_FRINGE;
        }
        // A signal-dot box, less the same margin.
        return signalDotBoxH(z) - 2f * ZONE_TEXT_INSET;
    }

    /**
     * The height of the level-dot box a slot stands in for: {@code 28} in the top
     * third and {@code 20} in the bottom.
     *
     * <p>These are the two figures {@code TrioRenderer.drawFreeRing} fits the dot
     * rows into ({@code height = z == 0 ? 28 : z == 1 ? 44 : 20}). They are named
     * here because the type cap now uses them too - see {@link #zoneTextRoomH} - and
     * a second copy of "28" in the renderer is how the two would drift.
     */
    static float signalDotBoxH(int z) {
        return z == 0 ? DOT_BOX_TOP : DOT_BOX_BOTTOM;
    }

    /** The level-dot placement box in the top third, in design units. */
    static final float DOT_BOX_TOP = 28f;
    /** The level-dot placement box in the bottom third, in design units. */
    static final float DOT_BOX_BOTTOM = 20f;
    /**
     * The level-dot placement box in the middle third, in design units. */
    static final float DOT_BOX_CENTRE = 44f;

    /**
     * The largest bead radius the ink box can hold, in design units.
     *
     * <p>The end thirds thread their beads onto the ring's own arc, so the outermost one
     * sits at radius {@code B_R} and its outer edge reaches {@code B_R + r} from the ring
     * centre - straight up at 12 o'clock, or straight down at 6. The ink box is the square
     * that scales the glyph, so {@code INK_W / 2} is the furthest any ink may reach; this is
     * that limit solved for the bead.
     *
     * <p>It equals {@code MAX_RING_STROKE / 2}, which is not a coincidence: the box was
     * sized so the shipped bead - whose diameter is the ring's thickness - just fits at the
     * thickest stroke. That is what makes this a real ceiling rather than a spare margin,
     * and why the dot-size setting has to stop here instead of growing the box: enlarging
     * the box would shrink the glyph for every install that never touched the slider.
     */
    static float maxBeadR() {
        return INK_W / 2f - B_R;
    }

    /**
     * The dot radius for a placed card row in the top or bottom third, at the reference size.
     *
     * <p>Exactly half the ring's stroke, so each dot's <b>diameter equals the ring's
     * thickness</b> and a row of four reads as four beads threaded onto the open arc -
     * the ring cut into segments, drawn in the same ink. That is the whole point of
     * putting these rows in the notches: the notch is where the ring has no arc, so
     * there is nothing to collide with.
     *
     * <p>The earlier version treated the row as something drawn <em>inside</em> the
     * ring and capped its radius by the clear chord at the slot's centre line. That was
     * the wrong model and produced the absurd result that a thicker ring forced smaller
     * dots: the notch is 84.9 degrees, so its arc is {@code R * 1.482 = 76.3} design
     * units long, and four stroke-diameter beads plus their gaps need only 56-60 of
     * that even at the thickest setting.
     *
     * <p>The result is scaled down only if the four beads would not fit the opening,
     * which the numbers above say cannot happen for the shipped stroke range - but the
     * guard stays so a future change to the notch width degrades into smaller dots
     * rather than into overlap.
     *
     * <p>This is the scale-1 case of {@link #arcRowDotR(int, int, float, float)}; ask that
     * one when the user's {@code dot_scale} applies.
     */
    static float arcRowDotR(int z, int ringStroke, float slotWidth) {
        return arcRowDotR(z, ringStroke, slotWidth, 1f);
    }

    /**
     * The same, with the user's dot-size multiplier applied.
     *
     * <p>The multiplier scales the <em>reference</em> radius and then the opening's room
     * still caps the result. That order is the whole contract: {@link #arcRowBeadRoomR} is
     * a mechanical limit - four beads and the arc's caps have to fit the gap - while the
     * percentage is a look, so the look is applied first and the limit is allowed to
     * refuse it. Capping before scaling would let a large percentage push the beads past
     * the caps; scaling before capping means a large value simply stops growing.
     *
     * <p>{@link #maxBeadR()} is the second ceiling, and it is the one the ink box imposes:
     * the bead rides the arc, so scaling it pushes ink outward toward the edge of the box
     * that fits the glyph to its view. This is exactly the {@code bolt_scale} precedent -
     * a large value crowded against a thick stroke stops growing rather than spilling over
     * - and it is why the size setting never has to shrink the glyph.
     *
     * <p>1.0 is exactly the old behaviour, so the default and every caller that does not
     * pass a scale go on drawing the bead whose diameter equals the ring's thickness.
     *
     * @param scale {@code TrioAppearance.dotScaleFactor()}: 1.0 for the reference bead
     */
    static float arcRowDotR(int z, int ringStroke, float slotWidth, float scale) {
        final float fit = arcRowDotRFitted(z, ringStroke, slotWidth);
        final float scaled = Math.max(0.5f, fit * Math.max(0.01f, scale));
        // The middle third is not an opening: the row there is fitted to the interior
        // rather than threaded onto a bare arc, so there is no cap to overrun and the
        // user's size is the only limit. The end thirds have the arc's caps to clear.
        if (z == 1) {
            return scaled;
        }
        return Math.max(0.5f, Math.min(scaled, Math.min(arcRowBeadRoomR(z, ringStroke),
                maxBeadR())));
    }

    /** The reference bead radius for {@code z}, before any user multiplier. */
    private static float arcRowDotRFitted(int z, int ringStroke, float slotWidth) {
        // The middle third is not a notch: the ring runs past it on both sides, so a row
        // there has to fit the space between the arcs rather than being threaded onto
        // one. It keeps the compact proportions it has always had - the dot box divided
        // into four - which is also what the aggregate meter uses for a single row.
        if (z == 1) {
            return Math.max(0.5f, Math.min(DOT_BOX_CENTRE / 3f / 2f, slotWidth / 18f));
        }
        // The end thirds sit in the ring's own openings, where a bead as thick as the
        // stroke is what makes the row read as outline segments. Four beads plus the
        // arc's caps have to fit that opening, which the numbers say they always do for
        // the shipped stroke range.
        final float preferred = Math.max(1f, ringStroke * 0.5f);
        return Math.max(0.5f, Math.min(preferred, arcRowBeadRoomR(z, ringStroke)));
    }

    /**
     * Clearance kept between the end bead and the arc's own round cap, in design units.
     *
     * <p>Both are antialiased, so "touching exactly" still interleaves pixels; one unit
     * is the fringe.
     */
    private static final float ARC_ROW_CLEARANCE = 1f;

    /**
     * The largest bead radius that still lets four of them sit inside the opening.
     *
     * <p>Four beads at diameter {@code 2r}, spaced at most one diameter apart, span
     * {@code 3 * 2r + 2r = 8r} of arc, and the caps of the arc itself eat a
     * {@code stroke/2} at each end.
     */
    private static float arcRowBeadRoomR(int z, int ringStroke) {
        final float opening = arcOpeningLength(z);
        // Both ends of that opening are the arc's own round caps, each reaching stroke/2
        // in, plus a unit of clearance so the end bead's antialiased edge does not meet
        // the cap's.
        final float usable = Math.max(0f, opening - ringStroke - 2f * ARC_ROW_CLEARANCE);
        return Math.max(0.5f, usable / 8f);
    }

    /**
     * The length of the bare arc a zone's row is threaded onto, in design units.
     *
     * <p>The top third uses the 12 o'clock notch; the bottom third uses the arc's own
     * opening. Both are {@link #GAP_WIDTH_DEG} wide by construction, so they measure
     * the same.
     */
    static float arcOpeningLength(int z) {
        if (z == 2) {
            return (float) (B_R * Math.toRadians(360f - B_SWEEP));
        }
        return (float) (B_R * Math.toRadians(GAP_WIDTH_DEG));
    }


    /**
     * The horizontal room a placed text piece may use in zone {@code z}, in design
     * units.
     *
     * <p>A single width budget cannot be right, because the ring is round: the room
     * left at a given height is that height's <em>chord</em>, and it shrinks toward
     * the top and bottom of the glyph. Fitting to the chord is what stops a wide
     * reading from clipping the arc at its corners - which is what a budget measured
     * only at the centre line did.
     */
    static float zoneTextRoomW(int z, int ringStroke, float inkH) {
        if (z != 1) {
            // The end thirds sit in the ring's own openings, where there is no arc across
            // the slot at all - the ink there is bounded by the arc's END CAPS, not by a
            // chord that the text's own height would narrow.
            //
            // Measuring the chord there was wrong twice over: it made the budget depend on
            // the line's height (so the fit oscillated between two values instead of
            // converging), and at the top it collapsed the budget to about 20 units the
            // moment the text's upper edge passed the circle's apex - a cliff that no
            // amount of iterating could settle on.
            //
            // The room in an opening is its chord: the straight distance between the two
            // caps, which is fixed by the opening's width, not by the text.
            return Math.max(0f, openingChord(z, ringStroke));
        }
        final float centre = zoneTextCy(z, ringStroke);
        final float half = Math.max(0f, inkH) * 0.5f;
        // The middle third is inside the ring, so its budget really is a chord - and one
        // that narrows as the line grows, which is why the caller iterates.
        final float rowA = centre - half;
        final float rowB = centre + half;
        final float binding = Math.abs(rowA - B_CY) > Math.abs(rowB - B_CY) ? rowA : rowB;
        final double dy = binding - B_CY;
        final double inside = (double) B_R * B_R - dy * dy;
        if (inside <= 0) {
            return Math.max(0f, gapClearWidth(ringStroke) - 2f * ZONE_TYPE_FRINGE);
        }
        final float chord = (float) (2.0 * Math.sqrt(inside));
        // One stroke of ink lies inside the arc (half at each side), plus the
        // antialiased rim of both the glyph and the arc.
        return Math.max(0f, chord - ringStroke - 4f * ZONE_TYPE_FRINGE);
    }

    /**
     * The straight distance between the two arc caps of a zone's opening, in design
     * units.
     *
     * <p>That is the width a piece standing in the opening has: the opening is a sector
     * bounded by the arc's round ends, so its usable width is the chord across it - a
     * fixed number, unlike the interior chord which shrinks towards the top of the
     * circle. Insets a stroke's worth so the ink clears both caps.
     */
    static float openingChord(int z, int ringStroke) {
        final double halfAngle = Math.toRadians(GAP_WIDTH_DEG * 0.5);
        final float chord = (float) (2.0 * B_R * Math.sin(halfAngle));
        return Math.max(0f, chord - ringStroke - 4f * ZONE_TYPE_FRINGE);
    }

    /** The width budget at the centre line alone, for a line whose height is unknown. */
    static float zoneTextRoomW(int z, int ringStroke) {
        return zoneTextRoomW(z, ringStroke, 0f);
    }

    /**
     * Clearance kept between a fitted line's edge and the arc, in design units.
     *
     * <p>Both are antialiased, so "touching exactly" still interleaves pixels. Small
     * enough to cost a fraction of a point at the default sizes.
     */
    private static final float ZONE_TYPE_FRINGE = 1f;

    /**
     * The centre a placed piece of <em>text</em> is drawn on, per zone.
     *
     * <p>The same centres the drawn figures use, deliberately: text that shifted
     * whenever a cut line moved would sit off-centre from the arcs, the bolt and the
     * dot rows it shares a third with, and every fit above is measured about this
     * same point so the two always agree.
     */
    static float zoneTextCy(int z, int ringStroke) {
        return ZONE_CY[z];
    }

    /**
     * Slack kept between a fitted line and its third's boundary, in design units.
     *
     * <p>Not a tuning knob. It matters because this cap is the one the draw path
     * trusts: when the width budget binds, the size lands exactly on the cap, and
     * "exactly" is where the antialiased fringe of the glyph meets the antialiased
     * edge of the ink above it. Two units is enough for a fringe at the largest
     * sizes the sliders reach, and small enough that it costs a fifth of a point at
     * the default size.
     */
    private static final float ZONE_TEXT_INSET = 2.5f;

    /**
     * Clearance kept from the arc's antialiased fringe, in design units.
     *
     * <p>The arc's nominal inner edge is {@code B_CY - B_R - stroke / 2}, but the
     * stroke is drawn antialiased and its round cap adds a rim of partial pixels
     * outside that. Reserving one unit keeps a fitted line off that rim rather than
     * exactly on the mathematical edge, which is where "it should fit" and "the ink
     * overlaps" differ by a handful of pixels.
     */
    private static final float ZONE_ARC_FRINGE = 1f;

    // ------------------------------------------------------------------- wi-fi

    static final float W_CX = 59.5f;
    static final float W_STROKE = 7f;
    /** Outer arc: chord (38.5,55.5)-(80.5,55.5), r=31 -> centre below the chord. */
    static final float W1_CY = 78.3035085f;
    static final float W1_R = 31f;
    static final float W1_START = 227.3423f;
    static final float W1_SWEEP = 85.3154f;
    /** Middle arc: chord (47,65.25)-(72,65.25), r=18.5. */
    static final float W2_CY = 78.8881817f;
    static final float W2_R = 18.5f;
    static final float W2_START = 227.5048f;
    static final float W2_SWEEP = 84.9904f;

    /** Topmost ink of the outer arc, at its apex. */
    static final float W_TOP = W1_CY - W1_R;
    /**
     * Lowest ink of the group: the underside of the dot beneath the middle arc.
     * Read off {@code TrioRenderer.buildWifiDot}, whose path bottoms out at
     * y=80.95.
     */
    static final float W_BOTTOM = 80.95f;
    /**
     * Scale of the Wi-Fi group when the percentage is centred
     * ({@code Prefs.KEY_VALUE_CENTRED}) and the group is moved into the 12
     * o'clock notch. The notch is 69.6 units of chord at the mouth and the group
     * is 49 wide at full size, so it has to shrink to read as a notch ornament
     * rather than spill over the arc ends.
     */
    static final float GAP_WIFI_SCALE = 0.8f;

    /** Keeps the group horizontally centred on the icon axis. */
    static float gapWifiOffsetX() {
        return W_CX * (1f - GAP_WIFI_SCALE);
    }

    /**
     * Puts the topmost ink - the outer arc's apex, half a stroke above its
     * centre line - at {@link #GAP_TOP}, the same clearance the digits get when
     * they sit in the notch.
     */
    static float gapWifiOffsetY(float stroke) {
        return GAP_TOP - (W_TOP - stroke * 0.5f) * GAP_WIFI_SCALE;
    }

    // ------------------------------------------------------- four level dots
    static final float DOT_R = 5.5f;
    static final float[][] DOTS = {
            {33f, 104.2f},
            {50.5f, 111.2f},
            {68.5f, 111.7f},
            {86f, 105.8f},
    };

    /**
     * Radius of a level dot in the dual-SIM reading.
     *
     * <p>Larger than {@link #DOT_R}: with two rows of four the dots are the
     * glyph's dominant feature and the reference draws them at 50px across a
     * 451px-wide image, i.e. 14.56 design units - a 7.28 radius. Rounded down to
     * 7.0 so the top row keeps a hair of clearance inside the ink box at the
     * largest reachable stroke.
     *
     * <p>Only used for the two rows of the dual reading; the single-row layout
     * keeps {@link #DOT_R} untouched, because that is the shipped look.
     */
    static final float DUAL_DOT_R = 7.0f;

    /**
     * The dual-SIM dot rows, eight entries: indices 0..3 are SIM 1 (slot 0) in
     * the top half of the ring, indices 4..7 are SIM 2 (slot 1) at
     * {@link #DOTS}.
     *
     * <p>The top row is {@link #DOTS} mirrored about {@link #B_CY}, which is what
     * makes the two rows sit symmetrically inside the ring - the bottom row
     * follows the ring's lower curve, and the mirror puts the top row on the
     * matching upper curve. The mirror line is the ring centre rather than the
     * middle of the design box: the ring itself is centred at {@code B_CY}, and
     * mirroring about anything else would leave the rows visibly off-centre
     * inside it.
     *
     * <p>Both rows also fall inside the ring's two mouths - the bottom row in the
     * arc's natural opening, the top row in the 12 o'clock notch - which is why
     * the dual reading forces a gap even though it draws no bolt.
     */
    static final float[][] DOTS_DUAL = new float[8][2];

    static {
        for (int i = 0; i < DOTS.length; i++) {
            DOTS_DUAL[i][0] = DOTS[i][0];
            DOTS_DUAL[i][1] = 2f * B_CY - DOTS[i][1];
            DOTS_DUAL[i + DOTS.length][0] = DOTS[i][0];
            DOTS_DUAL[i + DOTS.length][1] = DOTS[i][1];
        }
    }

    /** Opacity of the battery track / inactive level dots. */
    static final int TRACK_ALPHA = 56; // 0.22 * 255

    /** Below this battery level the ring turns the "critical" colour. */
    static final int CRITICAL_LEVEL = 20;

    // ------------------------------------------- stacked out-of-ring signal
    //
    // The out-of-ring reading stands in the status bar, not in the glyph, so
    // none of the ink box above applies to it. Its numbers are quoted in the
    // 350x350 reference image and kept in that image's own units; the view
    // measures itself against them and scales to whatever height the status bar
    // row gives it. Working in the reference's units rather than in dp is what
    // keeps the two rows, the bar steps and the round caps in the proportions
    // the reference draws, at every density.

    /** Columns in one reading: one capsule bar, and one dot beneath it. */
    static final int STACK_COLUMNS = 4;

    /** Bar width in reference pixels. Also the dot row's diameter. */
    static final float STACK_BAR_W = 50f;

    /** Column pitch: {@link #STACK_BAR_W} plus the 17px gap the reference leaves. */
    static final float STACK_PITCH = 67f;

    /**
     * Bar heights, ascending left to right, in reference pixels.
     *
     * <p>An even 25px step - 75, 100, 125, 150 - on a shared baseline, which is
     * what makes the row read as a level at a glance. The baseline is the
     * <em>bottom</em>: the bars grow upwards, so index {@code 3} is both the
     * tallest bar and the height of the whole row.
     */
    static final float[] STACK_BAR_H = {75f, 100f, 125f, 150f};

    /** Dot diameter in reference pixels. Equal to the bar width, as drawn. */
    static final float STACK_DOT_D = 50f;

    /**
     * Gap between the bars' baseline and the top of the dot row, both measured
     * as edges.
     *
     * <p>The reference has no antialiasing, so its edges land on whole pixels
     * and the gap is exactly determinate: the bars' last ink row is 237, which
     * puts their bottom edge at 238, and the dots' first ink row is 247, so the
     * blank band is {@code 238..246} - nine units. Measuring the gap by
     * subtracting those two ink-row indices instead gives ten and a reading one
     * unit too tall, which is exactly the error the reference comparison in
     * {@code work/outringcheck/compare.py} exists to catch.
     */
    static final float STACK_DOT_GAP = 9f;

    /** Width of the two-row reading: {@code 59..309} in the reference. */
    static final float STACK_INK_W =
            STACK_PITCH * (STACK_COLUMNS - 1) + STACK_BAR_W;

    /** Height of the two-row reading: {@code 88..297} in the reference. */
    static final float STACK_INK_H =
            STACK_BAR_H[STACK_COLUMNS - 1] + STACK_DOT_GAP + STACK_DOT_D;

    // ------------------------------------------------- rectangular arrangement
    //
    // Everything below lays the same pieces out the other way: two columns of
    // four dots carry the mobile signal, the top slot between them carries the
    // Wi-Fi arcs, the network type or the charging bolt, the percentage sits
    // enlarged below that, and a full-width bar along the bottom is the battery.
    // There is no ring at all.
    //
    // The coordinates still live in the same 120x120 design space, so the two
    // arrangements differ only in where the pieces go and in the ink box used to
    // fit them into the host view.

    /** Radius of a level dot, and the centre x of each of the two columns. */
    static final float RECT_DOT_R = 8.6f;
    static final float RECT_DOT_LX = 9.6f;
    static final float RECT_DOT_RX = 110.4f;
    /** Centre y of the first dot; each following dot drops by {@link #RECT_DOT_STEP}. */
    static final float RECT_DOT_TOP = 15.6f;
    static final float RECT_DOT_STEP = 22.5f;
    /** Four dots per column, the number of steps a phone's signal meter has. */
    static final int RECT_DOT_ROWS = 4;

    /**
     * The battery bar: a capsule spanning the full width of the columns' outer
     * edges, so its ends line up with the dots above it.
     *
     * <p>Its thickness is {@code TrioSettings#ringStroke} - the ring the slider
     * would otherwise draw is absent here, and the bar is the one heavy line the
     * arrangement keeps, so the slider stays meaningful.
     */
    static final float RECT_BAR_LEFT = 1.0f;
    static final float RECT_BAR_RIGHT = 119.0f;
    static final float RECT_BAR_CY = 107.3f;

    /**
     * The inner edges of the two dot columns: the box the centre column occupies.
     *
     * <p>Stated as its own pair of numbers because <em>two</em> things have to agree
     * about it, and they were two independent constants before:
     *
     * <ul>
     *   <li>{@link #RECT_TEXT_CLEAR} - the width the renderer fits the percentage
     *       and the network type into, so their ink is bounded by these edges;
     *   <li>the tappable centre column in {@code TrioZones} - which decides whether
     *       a tap on that ink means "the percentage" or "the mobile meter".
     * </ul>
     *
     * <p>The failure when they disagree is silent and wrong: a three-digit
     * percentage at a large size reaches these edges, so a seam placed even one
     * design unit inside them puts the right-hand stroke of "100" in the mobile
     * column, and tapping the number turns the signal meter off instead. Measured
     * at 960px: a seam 1 unit in misattributes 334 ink pixels, 3 units in
     * misattributes 3324, and every one of them is a three-digit reading.
     */
    static final float RECT_TEXT_LEFT = RECT_DOT_LX + RECT_DOT_R;
    static final float RECT_TEXT_RIGHT = RECT_DOT_RX - RECT_DOT_R;

    /**
     * Horizontal room for text between the two dot columns. Unlike the ring's
     * clear width this does not depend on the font size: the columns are
     * vertical, so a taller glyph eats into the same gap.
     */
    static final float RECT_TEXT_CLEAR = RECT_TEXT_RIGHT - RECT_TEXT_LEFT;

    /** Baseline and scale of the network type in the top slot. */
    static final float RECT_TYPE_BASELINE = 42.3f;
    static final float RECT_TYPE_SCALE = 1.26f;

    /**
     * Baseline, x and scale of the percentage. The reference draws the digits
     * noticeably larger than the ring arrangement does, which is the point of
     * the arrangement: the columns leave the middle free to be read at a glance.
     */
    static final float RECT_VALUE_X = 60f;
    static final float RECT_VALUE_BASELINE = 92.7f;
    static final float RECT_VALUE_SCALE = 1.38f;

    /**
     * Scale of the Wi-Fi group in the top slot and the y of its topmost ink.
     *
     * <p>Only a translation and a uniform scale: the arcs themselves are the
     * same paths the ring arrangement uses, so both arrangements stay visually
     * identical in shape.
     *
     * <p>{@code 0.95} is measured against the reference: its outer arc's ink is
     * 3.05 dot diameters wide, and the untouched group at scale 1 is 3.20, so
     * the arcs are very nearly full size in this arrangement. It is not exactly
     * 1: the reference's own arcs are a little flatter than this module's
     * (wider ink, thinner stroke). That difference is deliberately not copied -
     * the arcs here are the existing icon, only repositioned.
     */
    static final float RECT_WIFI_SCALE = 0.95f;
    /**
     * The outer arc's apex, half a stroke above its centre line, sits here in
     * design units. The reference puts that ink two of its own pixels below the
     * top of the dot columns, which is about 1.5 design units, so this is a
     * little lower than the dots' ink top at {@link #RECT_DOT_TOP} - {@link
     * #RECT_DOT_R}.
     */
    static final float RECT_WIFI_TOP = 8.5f;

    /** Keeps the Wi-Fi group horizontally centred on the icon axis. */
    static float rectWifiOffsetX() {
        return 60f - W_CX * RECT_WIFI_SCALE;
    }

    /** Puts the outer arc's apex, half a stroke above its centre line, at {@link #RECT_WIFI_TOP}. */
    static float rectWifiOffsetY(float stroke) {
        return RECT_WIFI_TOP - (W_TOP - stroke * 0.5f) * RECT_WIFI_SCALE;
    }

    /**
     * The charging bolt sits in the same top slot. The ring arrangement puts its
     * ink centre at {@link #BOLT_CX} / y=17.26; the arrangement's axis is 60 and
     * its top slot centre slightly lower, so both are nudged by a hair.
     */
    static final float RECT_BOLT_DX = 0.55f;
    static final float RECT_BOLT_DY = 0.24f;

    static float rectBoltOffsetX() {
        return boltOffsetX() + RECT_BOLT_DX;
    }

    static float rectBoltOffsetY() {
        return boltOffsetY() + RECT_BOLT_DY;
    }

    /** The rectangle's bolt offset for a bolt scaled by {@code factor}; see {@link #boltOffsetX(float)}. */
    static float rectBoltOffsetX(float factor) {
        return boltOffsetX(factor) + RECT_BOLT_DX;
    }

    static float rectBoltOffsetY(float factor) {
        return boltOffsetY(factor) + RECT_BOLT_DY;
    }

    // ------------------------------------------------------------------- frame
    /**
     * Tight ink box of the whole glyph (battery arc + stroke, Wi-Fi arcs + dot, level
     * dots, top indicator), in design units. Used to fit the 120x120 artwork into the
     * host view without non-uniform scaling.
     *
     * <p>Derived from the arc rather than typed in. It used to be
     * {@code 1.0, 0.5, 117.0, 117.5} - the box of the old 242.6-degree sweep, whose ends
     * sat at y = 88.25 and whose round caps reached the bottom of the design box. The
     * sweep is now 264 degrees with the bottom opening centred on 6 o'clock, so those
     * ends moved down to y ~ 96 and the old box no longer described the ink: the glyph
     * was scaled to a frame larger than itself and came out visibly smaller in the
     * status bar.
     *
     * <p>The extents are taken from the arc's own endpoints plus half a stroke, which is
     * what the round caps add. The top of the box is the arc's apex (also half a stroke
     * clear), and the bottom is the lower of the two endpoints.
     */
    // The values are literals because the offline harness reads them out of this file with
    // a regex and measures the render against them; a static initialiser computing them
    // would make the number invisible to that check. They are still DERIVED, and
    // {@link #checkInkBox()} recomputes them at class load so a change to the arc or the
    // beads that is not mirrored here fails loudly instead of silently scaling the glyph
    // wrong.
    //
    // Derivation: the box is SQUARE and centred on the RING'S CENTRE (B_CX, B_CY), not on the
    // extent of whatever happens to be drawn. That distinction is the whole point:
    //
    //   - Centred on the ring, the glyph never moves or resizes as pieces come and go. The
    //     status bar draws the same ring every frame; only its contents change.
    //   - Centred on the drawn ink, the ring would slide up and down as a percentage or a dot
    //     row appeared, because the arc has an opening at the bottom and its ink sits above
    //     the ring's centre.
    //
    // Half-extent 59.5 is the reach of the outermost ink from the ring's centre: the cap of
    // the thickest stroke above (61.487 - 1.99 = 59.50) and the bottom bead row below
    // (120.99 - 61.487 = 59.50). Those two are equal, which is what makes the box symmetric
    // and the ring exactly centred.
    //
    // The bead rows ARE included, at the thickest stroke the slider allows. Excluding them
    // was tried and is wrong: a placed bottom row reaches y 118 while a ring-only box stopped
    // at 104, so at 100% size the host clipped the row - the visible symptom being a bottom
    // edge that "does not show completely".
    static final float INK_X = B_CX - 59.5f;
    static final float INK_Y = B_CY - 59.5f;
    static final float INK_W = 119.0f;
    static final float INK_H = 119.0f;

    static {
        checkInkBox();
    }

    /**
     * Verifies the box is the ring's own square, centred on the ring.
     *
     * <p>The check is not "does this match the drawn ink" - the two differ on any frame that
     * holds a dot row - but the two properties the box exists for:
     *
     * <ol>
     *   <li>it is centred on {@code (B_CX, B_CY)}, so the ring never slides as contents
     *       change;
     *   <li>it is big enough for the outermost ink the sliders can ask for, so nothing is
     *       clipped at 100%.
     * </ol>
     *
     * <p>A comment saying "keep these in step" is not a check, and neither is comparing
     * against one arbitrary frame: this recomputes both properties and throws at class load,
     * which means at the first frame in the harness or on the device rather than as a subtly
     * mis-scaled glyph.
     */
    private static void checkInkBox() {
        // 1. Centred on the ring, and square.
        if (Math.abs(INK_X + INK_W / 2f - B_CX) > 0.01f
                || Math.abs(INK_Y + INK_H / 2f - B_CY) > 0.01f) {
            throw new AssertionError("ink box is not centred on the ring: box centre "
                    + (INK_X + INK_W / 2f) + "," + (INK_Y + INK_H / 2f)
                    + " vs ring " + B_CX + "," + B_CY);
        }
        if (Math.abs(INK_W - INK_H) > 0.01f) {
            throw new AssertionError("ink box is not square: " + INK_W + "x" + INK_H
                    + " - a non-square box scales the ring differently on each axis");
        }
        // 2. Big enough for the outermost ink, recomputed the way the drawing places it.
        final float capPad = Prefs.MAX_RING_STROKE * 0.5f;
        float reach = 0f;
        for (int i = 0; i <= 360; i++) {
            final double angle = Math.toRadians(B_START + (i / 360.0) * B_SWEEP);
            final float dx = Math.abs(B_R * (float) Math.cos(angle)) + capPad;
            final float dy = Math.abs(B_R * (float) Math.sin(angle)) + capPad;
            reach = Math.max(reach, Math.max(dx, dy));
        }
        final int stroke = Prefs.MAX_RING_STROKE;
        final float halfOpening = (float) Math.toRadians(GAP_WIDTH_DEG * 0.5);
        // Both ends of the dot-size slider, because the beads are the one piece whose ink
        // the user moves directly: at MAX_DOT_SCALE they are what reaches furthest, and at
        // MIN_DOT_SCALE they shrink away and the arc's own caps become the outer limit.
        // Checking only the reference size is exactly what let a larger bead overrun the
        // box before maxBeadR() existed.
        for (int scalePct : new int[] { Prefs.MIN_DOT_SCALE, Prefs.DEF_DOT_SCALE,
                Prefs.MAX_DOT_SCALE }) {
            final float beadR = arcRowDotR(0, stroke, 78f - stroke, scalePct / 100f);
            final float inset = (beadR + stroke * 0.5f + 1f) / B_R;
            final float beadHalfAngle = Math.min(halfOpening - inset,
                    (float) Math.toRadians(BEAD_SPAN_DEG * 0.5));
            for (int row = 0; row < 2; row++) {
                for (int i = -1; i <= 1; i++) {
                    final float angle = beadHalfAngle * i;
                    final float dx = Math.abs(B_R * (float) Math.sin(angle)) + beadR;
                    final float dy = Math.abs(B_R * (float) Math.cos(angle)) + beadR;
                    reach = Math.max(reach, Math.max(dx, dy));
                }
            }
            // The ceiling itself has to be a real one: a bead radius past maxBeadR() would
            // put the outer edge outside the box, whatever the opening allows.
            if (beadR > maxBeadR() + 0.01f) {
                throw new AssertionError("dot radius " + beadR + " at " + scalePct
                        + "% exceeds what the ink box holds: " + maxBeadR());
            }
        }
        final float half = INK_W / 2f;
        if (reach > half + 0.01f) {
            throw new AssertionError("ink box too small: outermost ink reaches " + reach
                    + " from the ring centre, the box allows " + half);
        }
    }

    /**
     * The same box for the rectangular arrangement.
     *
     * <p>Wider and slightly shorter, and - unlike {@link #INK_W}/{@link #INK_H} -
     * sized against the <em>extremes</em> every slider can reach rather than
     * against the default look: the bolt's tip at {@code showValue} with the
     * thickest arc strokes, and the bar's underside at
     * {@code Prefs#MAX_RING_STROKE}. Fitting the extremes means no setting can
     * push ink outside the host view, where it would be clipped; the cost is
     * that the default glyph ends up a few percent smaller than it could be.
     */
    static final float RECT_INK_X = 1.0f;
    static final float RECT_INK_Y = 1.0f;
    static final float RECT_INK_W = 118.0f;
    static final float RECT_INK_H = 114.4f;

    // ------------------------------------------------------- capsule arrangement
    //
    // The outline itself is the battery gauge: its length is the full track and
    // the role-coloured part of it is the level, so the shape fills up instead of
    // containing a gauge. Two four-dot rows interrupt the stroke at 12 and 6
    // o'clock, and the time and the right-hand reading share the interior.
    //
    // The shape is quoted against the reference the design was drawn from: a
    // capsule 2.34 times as wide as it is tall, a stroke 12.6% of that height,
    // dots as thick as the stroke with a pitch of 18.5%, and text 26.5% of the
    // height. All four of those are kept, and the reason they can be is the host
    // width - see CAP_RIGHT.

    /** Outer bounds of the capsule outline, in design units. */
    static final float CAP_LEFT = 1.0f;
    /**
     * Right edge of the outline at the reference's own 2.34:1 width.
     *
     * <p>The width the DESIGN is quoted at. The drawn width is {@link #capRight}, which is
     * this plus the user's {@code capsule_length} - so everything below that needs a
     * horizontal position asks that function rather than this constant. Kept as a constant
     * because it is the number the reference figure was measured at, and several derived
     * quantities are stated against it.
     */
    static final float CAP_RIGHT = 139.0f;
    /**
     * The straight run between the two caps, at reference length.
     *
     * <p>The stadium's caps are half the height each, so this is whatever is left of the
     * design width - 79 units of the 138. It is the one part of the shape that can change
     * length without changing its form.
     */
    static float capStraightRef() {
        return (CAP_RIGHT - CAP_LEFT) - (CAP_BOTTOM - CAP_TOP);
    }

    /**
     * Right edge of the outline as drawn: the reference width plus the user's extra length.
     *
     * <p>Every horizontal quantity of the capsule derives from this - the caps' centres, the
     * dot row's centre, the interior's left and right margins, the ink box the host is sized
     * from. Adding to it lengthens BOTH straight runs by that much and moves nothing else:
     * the caps keep their radius, the height does not move, and the proportions the design
     * states against the height are untouched.
     */
    /**
     * The extra length the caller is drawing with, clamped.
     *
     * <p>A parameter rather than a read of the live settings. This class is pure geometry: it
     * is compiled against desktop shims by the offline harness and it is called from the
     * settings process as well as from SystemUI, so reaching for a global snapshot here would
     * (a) make the harness unable to run it and (b) let the preview and the bar disagree about
     * the shape - the same class of bug as reading the live readings for the split.
     */
    static float capRight(int extraLength) {
        return CAP_RIGHT + Prefs.clamp(extraLength,
                Prefs.MIN_CAPSULE_LENGTH, Prefs.MAX_CAPSULE_LENGTH);
    }

    /**
     * The extra length in force for the frame being drawn, or 0 for the reference shape.
     *
     * <p>A single static rather than a parameter on six functions. The width is read by the
     * outline builder, the fill walk, the dot rows, the interior margins, the hit-test boxes
     * and the host measurement - threading one int through all of them would touch every
     * capsule call site and every one of their callers, for a value that is constant within a
     * frame. The renderer sets this at the top of a draw and the preview sets it from its own
     * snapshot, so the two processes never share it.
     *
     * <p>Not read from the live settings here: this class stays pure geometry, which is what
     * lets the offline harness compile and run it.
     */
    private static volatile int sLength;

    /** Sets the extra straight-run length for the frame about to be drawn. */
    static void setCapsuleLength(int extra) {
        sLength = Prefs.clamp(extra, Prefs.MIN_CAPSULE_LENGTH, Prefs.MAX_CAPSULE_LENGTH);
    }

    /** The extra length in force, for a caller that needs to mirror it (the host measure). */
    static int capsuleLength() {
        return sLength;
    }

    /** The reference width, for callers with no appearance to hand (tests, the harness). */
    static float capRight() {
        return CAP_RIGHT + sLength;
    }


    static final float CAP_TOP = 1.0f;
    /**
     * Bottom edge of the outline.
     *
     * <p>Quoted against the height the status bar offers its icons -
     * {@code status_bar_icon_height}, 20dp, 60px at density 3 - less the two
     * units of the design space that keep the ink off the host's edge. The
     * reference's 26.5% text rule then lands at 15.6 units of cap height, which
     * is what the stock battery percentage is drawn at, so the readings are the
     * size they would be anywhere else on the bar.
     *
     * <p>Deliberately the number that does <em>not</em> move when the shape gets
     * wider: the bar's height is fixed, and the capsule grows sideways into it
     * rather than up. See {@link #CAP_RIGHT}.
     */
    static final float CAP_BOTTOM = 60.0f;

    /**
     * Corner radius: half the height, which is what makes the outline a stadium
     * rather than a rounded rectangle.
     */
    static final float CAP_RADIUS = (CAP_BOTTOM - CAP_TOP) * 0.5f;



    /**
     * The outline's thickness, as a fraction of {@code ringStroke}.
     *
     * <p>The reference draws it at 12.6% of the capsule's height, which is 7.4
     * units here; {@code ringStroke} defaults to 14, so this is the factor that
     * lands there. Kept as a ratio rather than a constant so the stroke slider
     * still means something in this arrangement - it is the one row that adjusts
     * the glyph's weight in every style.
     */
    static final float CAP_STROKE_RATIO = 0.53f;

    /**
     * Radius of one signal dot, and the pitch between their centres.
     *
     * <p>From the reference: a dot is as thick as the stroke (12.6% of the
     * height) and the pitch is 18.5% of it. Dots thinner than the line they
     * interrupt read as beads dropped on top of it, which is the one thing the
     * reference is careful not to look like.
     */
    static final float CAP_DOT_R = 3.7f;
    static final float CAP_DOT_PITCH = 10.9f;

    /** Centre x of the four-dot row: the capsule's own centre. */
    static float capDotCx() {
        return (CAP_LEFT + capRight()) * 0.5f;
    }

    /**
     * The clear space the stroke leaves on each side of a dot row, as a fraction
     * of the capsule's <em>path</em> height - the outline's own height, excluding
     * the stroke, which is the measure the reference's own figures are stated
     * against.
     *
     * <p><b>9%, against the reference's 6.67%, at the user's request.</b> The
     * reference leaves 9px out of a 135px path height. Measured on the device at
     * that value the gap is 3px, which is faithful to the mockup but was reported
     * as still too tight - and on a 60px-tall glyph 6.67% is under four pixels, so
     * there is not much of it to see. 9% is about a third more than the reference
     * and stays well inside the point where the outline would read as broken
     * rather than as a row sitting in it.
     *
     * <p>A ratio rather than a constant: the stroke is a slider, and the clearance
     * has to hold at every width. It is measured against the PATH rather than the
     * ink height because the ink height depends on the stroke - a ratio against
     * that would move the clearance whenever the user changed the line's weight.
     *
     * <p>The first cut of this used 3.3%, which was a measurement error and a bad
     * one to have shipped: it came from counting the antialiased pixels between the
     * dot and the stroke as track, which made the gap look half its real size.
     *
     * <p>Without any clearance the row and the line run together, which is what the
     * very first cut did: its {@link #capDotReach} was written as "the dots' own
     * span" and nothing else, so the gap was exactly as wide as the row it held and
     * the outer two merged into the stroke ends. The row then reads as a line with
     * two lumps on it rather than as four dots interrupting a line.
     */
    static final float CAP_DOT_CLEARANCE_RATIO = 0.09f;

    /** The clear space on one side of a dot row, in design units. */
    static float capDotClearance() {
        return CAP_DOT_CLEARANCE_RATIO * (CAP_BOTTOM - CAP_TOP);
    }

    /**
     * Half the horizontal span of one dot row: how far the outline's path must be
     * cut back at 12 and 6 o'clock for the row to sit in a real gap.
     *
     * <p><b>Three terms, and the third is the one that was missing.</b> The path
     * has to be cut back by the dots' own half-span, by the clearance wanted
     * between them and the line, and by half the stroke - because {@code STROKE}
     * is drawn with {@link android.graphics.Paint.Cap#ROUND}, so the cut end inks
     * a semicircle of radius {@code stroke / 2} <em>past</em> its endpoint. Cutting
     * the path back by only the first two terms therefore spends the whole
     * clearance on that semicircle.
     *
     * <p>Measured, at the two ends of the stroke slider, with the first two terms
     * alone and {@link #CAP_DOT_CLEARANCE_RATIO} at the reference's own 6.67%:
     * the visible gap is +0.23 units at {@code ringStroke} 14 and -0.31 at 16. It
     * is negative at the top of the range - the round cap overlaps the outer dot -
     * and near enough to zero at the default to be invisible. That is what "the
     * dots still have no room" was, and it is why raising the ratio alone did not
     * fix it: the clearance was being eaten, not miscalculated.
     *
     * <p>The stroke is a slider, so the third term belongs in the geometry rather
     * than in a constant: at the default it is 3.71 units and at the maximum 4.24.
     */
    static float capDotReach(float stroke) {
        return CAP_DOT_PITCH * 1.5f + CAP_DOT_R + capDotClearance() + stroke * 0.5f;
    }

    /**
     * The same at the default stroke, for the two places that have no stroke to
     * hand: the offline harness's own reporting and {@code buildCapsuleOutline}'s
     * caller, which reads the stroke from the settings it was given.
     */
    static float capDotReach() {
        return capDotReach(Prefs.DEF_RING_STROKE * CAP_STROKE_RATIO);
    }

    /**
     * Centre y of the top and bottom dot rows.
     *
     * <p>Exactly the outline's path coordinates, and deliberately not
     * {@code CAP_TOP + stroke / 2}: the stroke straddles the path, so the path's
     * own y is the line's centre. Putting the dots on the stroke's inner edge
     * instead - which the first cut of this did - hangs them below the line they
     * are supposed to be set into.
     */
    static float capDotTopY() {
        return CAP_TOP;
    }

    static float capDotBottomY() {
        return CAP_BOTTOM;
    }

    /** Where the two end caps stop and the flat runs begin. */
    static float capFlatLeft() {
        return CAP_LEFT + CAP_RADIUS;
    }

    static float capFlatRight() {
        return capRight() - CAP_RADIUS;
    }

    /**
     * The interior, and the two reading slots inside it.
     *
     * <p>Not the flat run between the caps: the caps curve in only near the top
     * and bottom edges, so at the height the readings are drawn the interior runs
     * almost the full width. The reference relies on exactly that - its text
     * spans well past the point where the flat run ends - so insetting by the
     * radius would shrink the type for no reason.
     *
     * <p>Everything is derived from the stroke rather than fixed, because the
     * stroke is a slider: a heavier line eats into the room the readings have, and
     * they have to give way rather than overrun.
     */
    static float capInteriorLeft(float stroke) {
        return CAP_LEFT + stroke * 0.5f;
    }

    static float capInteriorRight(float stroke) {
        return capRight() - stroke * 0.5f;
    }

    /**
     * The interior's vertical extent - and this is the bound that matters.
     *
     * <p>The two dot rows ride the outline's own centreline, so the top row's ink
     * reaches {@code CAP_TOP + stroke / 2 + CAP_DOT_R} and the bottom row's
     * mirrors it. Anything drawn inside the capsule has to live between them;
     * measuring against the outline's outer bounds instead - which is what the
     * first cut of this did - lets the Wi-Fi arcs and the digits run straight
     * through the dot rows, which is exactly what the offline render showed.
     */
    static float capInteriorTop(float stroke) {
        return CAP_TOP + stroke * 0.5f + CAP_DOT_R;
    }

    static float capInteriorBottom(float stroke) {
        return CAP_BOTTOM - stroke * 0.5f - CAP_DOT_R;
    }

    static float capInteriorHeight(float stroke) {
        return capInteriorBottom(stroke) - capInteriorTop(stroke);
    }

    static float capInteriorCy(float stroke) {
        return (capInteriorTop(stroke) + capInteriorBottom(stroke)) * 0.5f;
    }

    /**
     * The vertical centre a piece placed in one of the two <b>openings</b> is drawn on.
     *
     * <p>The two opening bands are not the interior: they are the strips at 12 and 6 o'clock
     * where the outline is cut, which is what lets a row of beads read as the ring segmented.
     * Drawing a cell at {@code CAP_TOP}/{@code CAP_BOTTOM} - which is what this used to do, on
     * the reasoning that a band's centre is its anchor - put the reading exactly ON the
     * outline's centreline: half of it stuck out beyond the capsule, where the host clipped it
     * and the stroke painted over it. That is the "component shows only partially" defect.
     *
     * <p><b>The piece lives inside the capsule, in the lane the opening leaves free.</b> Not
     * outside it: the strip beyond the stroke is the ink box's own margin, about one stroke
     * wide, which is enough for a bead and nowhere near enough for a line of digits. So an
     * opening's reading is centred in the free lane just <em>inside</em> the outline, between
     * the stroke's inner face and the first thing that would collide with it - which for the
     * top is nothing but the interior's own readings, and that is why the lane is measured from
     * the interior's edge.
     *
     * @param top true for the 12 o'clock opening, false for the 6 o'clock one
     */
    static float capOpeningCy(boolean top, float stroke) {
        return top ? CAP_TOP + openingLaneCy(stroke) : CAP_BOTTOM - openingLaneCy(stroke);
    }

    /**
     * How far OUTSIDE the outline an opening's piece sits, measured from the path line.
     *
     * <p><b>Astride the gap, not tucked inside it.</b> The two openings are where the outline is
     * cut for the dot rows, so a reading placed there belongs in that gap, centred on the path
     * line: it then reads as the row that opened the outline rather than as a second line of
     * digits crammed under the top edge. Putting it in the lane just <em>inside</em> the
     * outline - which is what this used to do - stacked it on the body's own readings (the clock
     * shares the left column) and asked a 7.4-unit strip to do the work of a text line.
     *
     * <p>That lane existed only because the ink box left about one stroke of room beyond the
     * outline and a taller line would have been clipped by the host. The box is not the limit
     * any more: {@link #CAP_BAND_H} is added above and below it, and {@link #capHostHeightPx}
     * grows the host's <em>height</em> by the same two bands, so the drawing scale - and with it
     * every pixel of the capsule - is unchanged.
     */
    private static float openingLaneCy(float stroke) {
        // Dead on the path line, which is what the reference does: the label straddles the
        // outline, half of it above and half below, so the gap the dot row opened in the
        // stroke is exactly where the label sits. The band above the ink box is therefore
        // only half a label tall, and the other half lives in the opening itself.
        return 0f;
    }

    /**
     * The band added above and below the outline's ink box to hold the two openings' pieces.
     *
     * <p><b>Why a band on the host and not a taller ink box.</b> The capsule is fitted to the
     * host by whichever axis binds, and {@link #capHostWidthPx} asks the status bar to reserve a
     * width derived from the ink box's aspect ratio. Making the ink box itself taller therefore
     * makes the capsule <em>narrower</em>: the glyph would shrink to make room for the very band
     * it was meant to gain.
     *
     * <p>So the band is added to the <em>host</em> instead. {@link #capHostWidthPx} keeps the
     * width it always asked for, {@link #capHostHeightPx} grows the height by one band at each
     * end, and the drawing scale is still decided by the width alone. Every pixel of the capsule
     * - outline, stroke, clock, percentage, dot rows - stays exactly where it was; only the host
     * got taller, and the space it gained is the space these readings now use.
     *
     * <p>Quoted against the reference the design was drawn from rather than against the stroke,
     * because it is a text band: the mockup puts an opening's label at a cap height of about a
     * quarter of the path height. The <b>inward</b> half of such a label is the half that binds
     * - it runs down to the interior's own top, where the clock is, and that clearance is
     * fixed by the dot rows rather than by this constant - so the band is sized to at least
     * match that inward half and stop being the limit. Anything less and the label is fitted to
     * the band instead of to the row it is in, which is what makes it look shrunken.
     */
    static final float CAP_BAND_H = 9f;


    /**
     * The height a reading in an opening may use, in design units.
     *
     * <p>The band, less what the stroke and the clearance take out of it. A line fitted to this
     * fits the band wherever the piece is drawn, which is why the same number is both the fit's
     * bound and the band's usable height.
     *
     * <p>Measured from the band's outer edge rather than from the path line: a piece centred on
     * the path line reaches into the opening as well as out of it, and the outline's stroke is
     * drawn across the middle of that. The band is what is left beyond the stroke.
     */
    static float capOpeningTextRoomH(float stroke) {
        // The budget is measured from the PATH LINE outwards on both sides, because that is
        // where the reading is centred. Its inward half runs down to the interior's own top -
        // the first thing it could collide with, which is the clock - and its outward half runs
        // up to the box's edge. The two are not equal: the band is one stroke's worth taller
        // than the interior's clearance, so the INWARD half is what binds.
        //
        // Note what is NOT subtracted again: the path line is the stroke's centre, so a piece
        // centred on it has already cleared half the stroke by construction. Taking the
        // stroke's half out a second time - which this did - charged the same clearance twice.
        final float inward = capInteriorTop(stroke) - CAP_TOP;
        final float outward = CAP_TOP - CAP_INK_TOP;
        final float half = Math.min(inward, outward) - CAP_OPENING_GAP;
        // Ink on both sides of the line, so the ink budget is twice the smaller half.
        final float ink = 2f * half;
        // No extra reduction. The clearance to the interior already leaves this at about 0.81
        // of the body's readings, which is the reference's own 0.82 - applying a ratio on top
        // of that shrank the label twice for the same reason, which is what made the network
        // type look wrong in the top opening. The proportion is a CONSEQUENCE of the geometry,
        // not a second knob. This is an INK height - see {@link #capOpeningTextEm}, which is
        // what the text fit actually wants.
        // Never smaller than a dot: a band that cannot draw a bead is not a band.
        return Math.max(CAP_DOT_R * 2f, ink);
    }

    /**
     * The same budget as an <b>em</b>, which is the unit the text fit measures in.
     *
     * <p>Two different quantities, and feeding one where the other was wanted is what made an
     * opening's label come out at a fifth of the body's readings: the size was clamped to an
     * INK height, then multiplied by {@link #CAP_HEIGHT_RATIO} again to get its own ink - so the
     * cap height came out as the square of the ratio's effect rather than the once it should be.
     * {@link #capOpeningTextRoomH} answers the ink a cell has; this answers the font size that
     * produces it.
     */
    static float capOpeningTextEm(float stroke) {
        return capOpeningTextRoomH(stroke) / CAP_HEIGHT_RATIO;
    }


    /**
     * Clearance kept between the outline and a reading sitting across its opening.
     *
     * <p>One unit is the antialiasing fringe: both the line and the glyph are antialiased, so
     * touching exactly still interleaves pixels.
     */
    private static final float CAP_OPENING_GAP = 1f;

    /**
     * Half the width a piece placed in an opening inks, in design units.
     *
     * <p>Used to cut the outline back by exactly what that opening holds, so a reading sits in
     * a gap rather than on top of the line. The dot rows have their own measured reach
     * ({@link #capDotReach}); this is the same question for a piece made of text or an icon.
     *
     * <p>Measured rather than estimated: the three candidates for an opening - the percentage,
     * the network type and the clock - differ by more than 2:1 in width, so a single constant
     * would either cut the outline for the widest and leave it broken under "73", or fit "73"
     * and bury "100" under the stroke. Worst-case on purpose where a piece can grow: the outline
     * must be wide enough at every reading, not only at the one on screen.
     *
     * @param part      the piece that opening holds
     * @param level     battery level, for the percentage this frame
     * @param type      network type, for the label this frame
     * @param stroke    the capsule's stroke, which sets the text's own height budget
     * @param roomH     the height that opening's reading is fitted to
     */
    static float capPieceReach(int part, int level, String type, float stroke, float roomH) {
        switch (part) {
            case Prefs.PART_WIFI:
                // The arcs are drawn at capWifiScale about their own centre.
                return W_INK_W * capWifiScale(stroke) * 0.5f;
            case Prefs.PART_BOLT:
                // The bolt path is 12.5 units wide against its 19.9 of height; the reach is
                // half of that, scaled the way the bolt itself is.
                return 6.25f * capBoltScale(stroke);
            case Prefs.PART_VALUE:
                // Three digits is the widest this can get.
                return capTextReach("100", stroke, roomH);
            case Prefs.PART_TYPE:
                return capTextReach("5GA", stroke, roomH);
            case Prefs.PART_CLOCK:
                return capTextReach("19:41", stroke, roomH);
            default:
                return 0f;
        }
    }

    /**
     * Half the width {@code text} inks at the size an opening gives it.
     *
     * <p>The same two bounds the renderer applies - the user's size setting and the opening's
     * height - so the gap is cut for the glyph that will actually be drawn rather than for a
     * nominal one. Estimated from the character count instead of measured on a Paint: geometry
     * has no font, and the harness renders without one either, so an estimate that is
     * deliberately a little generous is the honest answer here. The clearance term the caller
     * adds covers the remainder.
     */
    private static float capTextReach(String text, float stroke, float roomH) {
        // The em the renderer will actually draw at: the user's own size, clamped by the row's
        // budget. Both are ems, so they compare directly. Clamping the reach and clamping the
        // drawn size have to use the same number or the gap gets cut for a glyph that is not
        // there - too narrow and the stroke runs through the label, too wide and the outline is
        // interrupted for nothing.
        final float em = Math.min(Prefs.DEF_VALUE_SIZE * CAP_TEXT_SCALE, roomH);
        // Average advance width of a digit in the condensed face the readings use, in ems.
        final float half = Math.max(0, text.length()) * em * 0.31f;
        // Never eat more than a quarter of the capsule: the outline still has to read as an
        // outline, and a gap wider than this stops being an interruption of the stroke and
        // becomes a shape with two horns.
        return Math.min(half, capFlatRight() * 0.5f);
    }

    /**
     * The width a piece placed in an <b>opening</b> may use, in design units.
     *
     * <p>A piece in an opening is not in a slot. The two interior slots exist because the clock
     * and the percentage have to share the body side by side, which is why the right-hand one is
     * only {@link #capValueSlotWidth} wide. An opening spans the whole capsule instead - it is a
     * row the outline was cut for - so fitting its text to the right-hand slot crushed it to a
     * third of the size the same string gets anywhere else. That was the "the network type looks
     * far too small" report.
     *
     * <p>Bounded by the flat run between the end caps rather than by the full outline width:
     * past the flat run the shape curves away, and the text would hang its first and last glyph
     * out over the curve. The run is the straight edge the opening actually has.
     */
    static float capOpeningWidth() {
        return capFlatRight() - capFlatLeft();
    }

    /** The gap the two readings keep between each other. */
    static final float CAP_TEXT_GAP = 6f;

    /**
     * The margin a reading keeps from the outline's inner edge, as a fraction of
     * the capsule's height.
     *
     * <p>Read off the reference at about 8.6% of the height. It matters most for
     * the time, which sits in the left slot right where the end cap curves back in
     * underneath it: with the slot starting at the interior's own edge the digits
     * begin immediately at the stroke's inner face, and at the reference device's
     * text size the "1" of "19:41" ends up touching the line.
     *
     * <p>A ratio rather than a constant for the same reason as the dot clearance -
     * the stroke is a slider, and the margin has to grow with it.
     */
    static final float CAP_TEXT_INSET_RATIO = 0.086f;

    /** The margin on one side of the readings, in design units. */
    static float capTextInset() {
        return CAP_TEXT_INSET_RATIO * (CAP_BOTTOM - CAP_TOP);
    }

    /** Where the readings' usable width starts, inside the outline's inner face. */
    static float capTextLeft(float stroke) {
        return capInteriorLeft(stroke) + capTextInset();
    }

    static float capTextRight(float stroke) {
        return capInteriorRight(stroke) - capTextInset();
    }

    /**
     * Natural width of each reading per unit of font size, at the default face.
     *
     * <p>Measured off the platform's own metrics, not guessed: "19:41" is 2.52
     * units wide per unit of size and "100" is 1.68. Their ratio is what the two
     * slots are divided by, and it is the whole reason a half each was wrong.
     */
    static final float CAP_TIME_TEXT_PER_UNIT = 2.52f;
    static final float CAP_VALUE_TEXT_PER_UNIT = 1.68f;

    /**
     * The share of the readings' width the time takes, the rest going to the
     * right-hand slot.
     *
     * <p><b>Not a half, and the even split was the bug behind "the clock's maximum
     * is too small".</b> The two slots held equal halves of the interior, but the
     * strings in them are not equal: at one unit of font size "19:41" is 2.52 units
     * wide and "100" is 1.68, so the time ran out of width long before the number
     * did. The clock's slider stopped doing anything above a setting of about 36 -
     * its documented maximum of 44 was unreachable, and raising that maximum would
     * have changed nothing. Measured before this fix: setting 44 drew the time at
     * 23.03 units instead of the 27.37 it asks for, and settings of 44, 60 and 120
     * all drew it at the same size. The slot was the binding constraint, not the
     * ceiling.
     *
     * <p>2.52 / (2.52 + 1.68) = 0.60, the two strings' natural widths at a common
     * size, so each reading gets the room its own string needs and both reach their
     * documented maximum together. The reference agrees: its time spans 156px
     * against its number's 92px, a share of 0.63.
     *
     * <p><b>A fixed ratio, deliberately, and the choice is a real one.</b> Dividing
     * instead in proportion to the two <em>size settings</em> would let the clock go
     * larger - up to about setting 62 with the number at its smallest, where this
     * caps it at 44 - but it would also make the time drift sideways every time the
     * number's slider moved, because its slot would be re-divided underneath it. Two
     * settings that the user is told are independent cannot behave that way, and the
     * fixed ratio costs only the ceiling that a 60px capsule cannot honestly hold
     * anyway. Following the current strings rather than the settings was rejected
     * for a second reason: the number is "9" at one level and "100" at another, and
     * is replaced entirely by the arcs, the bolt or the type while charging, so the
     * clock would resize itself as the battery moved.
     */
    static final float CAP_TIME_WIDTH_SHARE =
            CAP_TIME_TEXT_PER_UNIT / (CAP_TIME_TEXT_PER_UNIT + CAP_VALUE_TEXT_PER_UNIT);

    /** The width the two readings have to share, once the gap between them is taken out. */
    static float capReadingsWidth(float stroke) {
        return capTextRight(stroke) - capTextLeft(stroke) - CAP_TEXT_GAP;
    }

    /** Usable width of the left-hand slot, where the time is drawn. */
    static float capTimeSlotWidth(float stroke) {
        return capReadingsWidth(stroke) * CAP_TIME_WIDTH_SHARE;
    }

    /** Usable width of the right-hand slot: the number, the arcs, the bolt or the type. */
    static float capValueSlotWidth(float stroke) {
        return capReadingsWidth(stroke) * (1f - CAP_TIME_WIDTH_SHARE);
    }

    /**
     * How wide a capsule reading is allowed to be, from its OWN size.
     *
     * <p>The two interior cells are independent components sitting on one track, not a width
     * divided between them. Each is as wide as the reading it draws, capped by the track it
     * has to stay on:
     *
     * <ul>
     *   <li>the LEFT cell starts at the interior's left edge and grows rightwards;</li>
     *   <li>the RIGHT cell ends at the interior's right edge and grows leftwards;</li>
     *   <li>neither can pass the other, so the track is what limits them.</li>
     * </ul>
     *
     * <p>An earlier version divided the interior between the two cells in proportion to their
     * strings. That makes each cell's width depend on the other's CONTENT - place a clock on
     * the left and the number on the right shrinks - which is what "the two are squeezing each
     * other" describes, and it produced a press highlight that changed width with whatever the
     * cell happened to be showing. A component's size is its own business.
     *
     * <p>So the answer is the whole track: a reading is fitted to the room it could use if it
     * were alone, and the other cell does not shrink it. What keeps them apart is the drawing
     * order and the alignment - each cell hangs off its own edge and grows inwards - so a
     * reading that would overrun is fitted down by {@code fitSize} against the same number,
     * not by a width handed out in advance.
     *
     * @param stroke       the capsule's stroke, which sets the interior
     * @param forLeft      which edge to measure from; the track is the same either way
     */
    static float capReadingTrackWidth(float stroke) {
        return capReadingsWidth(stroke);
    }

    /**
     * The x where a cell's reading starts, given its own drawn width.
     *
     * <p>The left cell is left-aligned and the right cell is right-aligned, so this is the
     * reading's near edge: the interior's left margin for the left cell, its right margin for
     * the right one. The width is not consulted - an aligned reading does not need its centre
     * computed from its width - which is the point: nothing about one cell moves because of
     * the other.
     */
    static float capReadingEdge(float stroke, boolean forLeft) {
        return forLeft ? capTextLeft(stroke) : capTextRight(stroke);
    }

    /**
     * Where an ICON sits in the capsule's left cell, given the piece it is.
     *
     * <p>Text and icons do not share an anchor. A left-aligned reading is drawn FROM the
     * cell's inner edge, so its anchor is where the ink begins; an icon is drawn about its
     * centre, so feeding it that same number puts half of it past the edge - a Wi-Fi glyph or
     * a bolt then sits visibly to the right of the text it replaced, at the same nudge.
     *
     * <p>An icon is therefore centred half a step in from the edge, using the widest thing
     * that cell can hold as the step. That keeps every icon inside the stroke without
     * shifting it far enough to look like a different alignment from the text beside it.
     */
    static float capLeftIconCx(float stroke, int part) {
        final float edge = capTextLeft(stroke);
        // The bolt is the narrowest of the three and the arcs the widest, so one half-step
        // from the same edge would leave the arcs touching the stroke. Sized per piece, from
        // the same references the drawing uses.
        final float half;
        if (part == Prefs.PART_WIFI) {
            half = (W_INK_W * capWifiScale(stroke)) * 0.5f;
        } else if (part == Prefs.PART_BOLT) {
            // The bolt is fitted by HEIGHT alone (capBoltScale), so its ink width follows from
            // the path's own aspect rather than from a separate constant.
            half = (BOLT_INK_W * capBoltScale(stroke)) * 0.5f;
        } else {
            half = 0f;
        }
        return edge + half;
    }

    /**
     * Where an ICON sits in the capsule's RIGHT cell, given the piece it is.
     *
     * <p>The mirror of {@link #capLeftIconCx}, and needed for the same reason: the right cell
     * hangs from the interior's right edge, which is where a right-aligned READING ends - but
     * an icon is drawn about its CENTRE. Handing an icon that edge puts half of it outside the
     * outline, over the stroke and past the host's own bounds, so a bolt sitting where a number
     * would sit looks a long way right of the cell it belongs to.
     *
     * <p>Half a step back from the edge, sized per piece from the same references the drawing
     * uses - exactly as the left cell does at its own edge.
     */
    static float capRightIconCx(float stroke, int part) {
        final float edge = capTextRight(stroke);
        final float half;
        if (part == Prefs.PART_WIFI) {
            half = (W_INK_W * capWifiScale(stroke)) * 0.5f;
        } else if (part == Prefs.PART_BOLT) {
            half = (BOLT_INK_W * capBoltScale(stroke)) * 0.5f;
        } else {
            half = 0f;
        }
        return edge - half;
    }



    /** Centre of the left-hand slot: the time's own place. */
    static float capLeftCx(float stroke) {
        return capTextLeft(stroke) + capTimeSlotWidth(stroke) * 0.5f;
    }

    /**
     * The LEFT cell's anchor: its inner-left edge, not the slot's centre.
     *
     * <p>The capsule's left cell reads left-aligned, like a sentence starting at the
     * shape's edge. Centring it in the half-slot left a gap on the left and pushed the
     * ink toward the middle, so a piece there looked dragged rightwards instead of
     * sitting in its own cell.
     */
    static float capLeftAlignedX(float stroke) {
        return capTextLeft(stroke);
    }

    /** Centre of the right-hand slot: the percentage, the arcs or the bolt. */
    static float capRightCx(float stroke) {
        return capTextRight(stroke) - capValueSlotWidth(stroke) * 0.5f;
    }


    /**
     * Where a line of digits sits, so its cap height is centred in the interior:
     * half the cap above and half below the interior's own middle.
     */
    static float capTextBaseline(float stroke, float size) {
        return capInteriorCy(stroke) + size * CAP_HEIGHT_RATIO * 0.5f;
    }

    /**
     * How tall a reading may be, as a fraction of the interior.
     *
     * <p>Not a fraction of the capsule's outer height: the dot rows are inside
     * that, and a reading has to clear them. The other two arrangements never
     * needed this - they are 117 and 114 units tall, so a size that fits their
     * width always fits their height too.
     *
     * <p><b>A backstop, not the binding bound.</b> Measured rather than assumed:
     * with the size slider at its own ceiling the width fit is what decides the
     * type, and the height only takes over for a single narrow glyph at a
     * {@code valueSize} the slider cannot reach. A negative control that made
     * this limit a hundred times larger still passed every pixel assertion in
     * {@code tests/capsule.ps1}. It is kept because the two bounds are computed
     * from different things and the height is the one that would fail silently if
     * a future size range grew past it - but it is not what keeps the digits
     * inside the outline today, and saying otherwise would be a comment that
     * lies about which line is doing the work.
     */
    static final float CAP_TEXT_HEIGHT_RATIO = 0.82f;

    /**
     * The requested reading size, as a multiple of {@code valueSize}.
     *
     * <p>Two conversions, and leaving either out is what makes the type come out
     * the wrong size: the reference draws text whose <em>cap height</em> is 26.5%
     * of the capsule's height, and a font's em size is that cap height divided by
     * {@link #CAP_HEIGHT_RATIO}. Quoted against {@code valueSize} so the existing
     * size slider keeps control of it exactly as it does in the other two
     * arrangements.
     */
    static final float CAP_TEXT_SCALE = 0.265f * CAP_BOTTOM / (CAP_HEIGHT_RATIO * 36f);

    /**
     * Largest size the capsule's interior allows.
     *
     * <p>A backstop for a size the slider cannot currently reach - see
     * {@link #CAP_TEXT_HEIGHT_RATIO} for why it is stated as one.
     */
    static float capTextMaxSize(float stroke) {
        return capInteriorHeight(stroke) * CAP_TEXT_HEIGHT_RATIO / CAP_HEIGHT_RATIO;
    }

    /**
     * The Wi-Fi group's ink extents, in design units.
     *
     * <p>Measured off the geometry rather than read off a constant, because the
     * obvious constants are the wrong ones: {@code W1_R} is the arc's centre-line
     * radius, and the ink reaches half a stroke beyond it, while the arc's
     * <em>endpoints</em> are what set the width - not its apex. Taking the chord
     * for the width and the centre-line span for the height - which the first cut
     * of this did - understates both, and the group then overruns the slot it was
     * fitted to.
     *
     * <p>Quoted at {@code DEF_ARC_STROKE}, the default the arcs are drawn at; the
     * fit below is against this height, and the offset compensates for the stroke
     * actually in force the way the ring's own Wi-Fi offsets do.
     */
    static final float W_INK_W = 54.0f;
    static final float W_INK_H = 39.65f;
    /**
     * The group's ink centre. Centring on this - rather than aligning its top
     * edge - is what keeps the arcs optically centred in a slot whose height
     * comes from the dot rows rather than from the group itself.
     */
    static final float W_INK_CY = 61.13f;

    /** The bolt's ink extremes, from {@code buildBolt}'s own coordinates. */
    static final float BOLT_INK_H = 19.9f;
    static final float BOLT_INK_CY = 12.05f;

    /**
     * The bolt path's width, from the path's own bounding box.
     *
     * <p>The bolt is fitted by HEIGHT alone ({@code capBoltScale}), so its ink width follows
     * from the path's aspect rather than from a second constant the drawing would have to
     * keep in step. Taken from {@code buildBolt}'s coordinates: the path spans 13.6 units
     * across the 19.9 it is tall.
     */
    static final float BOLT_INK_W = 13.6f;

    /**
     * The height every reading and icon in the capsule is drawn at, in design
     * units: the reference's 26.5% of the capsule's height, as a cap height.
     *
     * <p>The same number {@link #CAP_TEXT_SCALE} produces, restated as the height
     * the icons are matched against so the two cannot drift apart. Measured off
     * the reference: its arcs and its digits are both 41px in a 151px capsule -
     * the same height to the pixel - and its percentage matches them.
     */
    static final float CAP_ICON_H = 0.265f * CAP_BOTTOM;

    /**
     * The arcs' and the bolt's scales, each sized so its own ink is
     * {@link #CAP_ICON_H} tall.
     *
     * <p>Two scales, not one shared, because the two icons have different ink
     * heights - the group is 39.65 units tall and the bolt 19.9 - so a single
     * factor would draw the bolt at half the height of the arcs. The ring
     * arrangement keeps them separate for the same reason.
     *
     * <p>Each is also capped by the interior, so an extreme stroke setting makes
     * the icons give way rather than cross the dot rows.
     */
    static float capWifiScale(float stroke) {
        return Math.min(CAP_ICON_H / W_INK_H, capInteriorHeight(stroke) / W_INK_H);
    }

    static float capBoltScale(float stroke) {
        return Math.min(CAP_ICON_H / BOLT_INK_H, capInteriorHeight(stroke) / BOLT_INK_H);
    }

    /** Places the arcs' ink centre on the right-hand slot's own centre. */
    static float capWifiOffsetX(float stroke) {
        return capRightCx(stroke) - W_CX * capWifiScale(stroke);
    }

    static float capWifiOffsetY(float stroke) {
        return capInteriorCy(stroke) - W_INK_CY * capWifiScale(stroke);
    }

    /**
     * The bolt's offset. Written against {@code BOLT_CX_REF} and
     * {@link #BOLT_INK_CY} - which are the bolt path's own ink centre - and never
     * against {@code BOLT_CX}/{@code BOLT_TOP}: those two are the ring's
     * <em>targets</em>, so treating them as path coordinates a second time puts
     * the bolt down and to the right of where it belongs.
     */
    static float capBoltOffsetX(float stroke) {
        return capRightCx(stroke) - BOLT_CX_REF * capBoltScale(stroke);
    }

    static float capBoltOffsetY(float stroke) {
        return capInteriorCy(stroke) - BOLT_INK_CY * capBoltScale(stroke);
    }

    /**
     * The capsule's ink box: the outline plus its stroke, which is what has to
     * fit inside the host view.
     *
     * <p>Sized against the largest stroke the slider offers rather than the
     * default, the way {@link #RECT_INK_W} is: fitting the extremes means no
     * setting can push ink outside the host, where the platform clips it.
     */
    static final float CAP_INK_X = CAP_LEFT - Prefs.MAX_RING_STROKE * CAP_STROKE_RATIO * 0.5f;
    /**
     * The top of the capsule's ink: the outline plus its stroke.
     *
     * <p><b>Deliberately unchanged by the opening bands.</b> A reading placed in the top or
     * bottom opening is fitted to the room that exists <em>inside</em> this box (see
     * {@link #capOpeningCy}), rather than the box being widened to hold it. Growing the box was
     * tried and reverted: {@link #capHostWidthPx} divides {@code capInkW()} by this height to
     * ask the status bar for a width, so a taller box makes the capsule NARROWER - the glyph
     * would have shrunk to make room for the very band it was meant to fit.
     */
    static final float CAP_INK_Y = CAP_TOP - Prefs.MAX_RING_STROKE * CAP_STROKE_RATIO * 0.5f;
    /**
     * The ink box's width, which follows the drawn width rather than the reference one.
     *
     * <p>A function, not a constant, because the user can lengthen the capsule and the host
     * is sized from this box: a constant here would have the bar reserve the reference width
     * while the glyph drew a longer capsule inside it.
     */
    static float capInkW() {
        return (capRight() + Prefs.MAX_RING_STROKE * CAP_STROKE_RATIO * 0.5f) - CAP_INK_X;
    }
    static final float CAP_INK_H =
            (CAP_BOTTOM + Prefs.MAX_RING_STROKE * CAP_STROKE_RATIO * 0.5f) - CAP_INK_Y;
     /**
     * The top of what the capsule draws, band included.
     *
     * <p>The outline plus its stroke is where the <em>capsule</em> starts; the band above that
     * is what the 12 o'clock piece sits in. Declared here rather than beside
     * {@link #CAP_BAND_H} because it is derived from {@link #CAP_INK_Y}: Java does not allow
     * reading a static field before its declaration.
     *
     * <p>This box is what the renderer fits the drawing to, and it is <b>taller without being
     * wider</b>. That is the whole point: the fit is width-driven for this shape, so the
     * capsule's own pixels are decided by {@code capInkW()} alone and are unchanged, while the
     * two bands give the opening readings somewhere to live.
     */
    static final float CAP_INK_TOP = CAP_INK_Y - CAP_BAND_H;

    /** The bottom of what the capsule draws, band included - the mirror of {@link #CAP_INK_TOP}. */
    static final float CAP_INK_BOTTOM = CAP_INK_Y + CAP_INK_H + CAP_BAND_H;

    /**
     * The width the capsule's host view has to ask the status bar for, so that the
     * shape can be as tall as the bar allows.
     *
     * <p><b>Why this method exists at all.</b> The host is not a square like the
     * other two arrangements' - it is MIUI's battery icon, and
     * {@code MiuiBatteryMeterIconView.onMeasure} pins its width to
     * {@code R.dimen.battery_meter_width}, which is 28dp. That number is a
     * property of the <em>stock</em> battery glyph, which is a narrow vertical
     * battery; the capsule is a wide shape, so being handed a tall narrow box
     * means the fit is decided by the width and the shape ends up short. On the
     * reference device that is 84x46px in a 60px-tall bar - fourteen pixels of
     * the bar's height go unused, and the glyph looks like a squashed pill.
     *
     * <p>The fix is to answer {@code onMeasure} with the width this aspect ratio
     * actually needs for the height being offered, rather than the stock width.
     * Nothing has to be told about it: {@code MiuiBatteryMeterView.onMeasure} does
     * not measure its icon child at all, it just adds up
     * {@code mBatteryDigitalView.getMeasuredWidth()}, and
     * {@code MiuiStatusBatteryContainer.onMeasure} adds up the meter's - so
     * widening this one view widens the battery slot and the status bar makes room
     * for it on its own.
     *
     * <p>Deliberately <em>not</em> scaled by {@code glyphFit}: that multiplier is
     * already applied to the drawing scale, and folding it in here as well would
     * apply it twice - the ink would shrink by the square of the setting. The
     * consequence is that the host keeps its full width when the user shrinks the
     * glyph, which is what the ring arrangement already does with its square host.
     *
     * @param heightPx the height the bar is offering, in pixels
     * @return the width to measure at, or 0 when there is no height to work from
     */



    static int capHostWidthPx(int heightPx) {
        if (heightPx <= 0) {
            return 0;
        }
        return Math.round(heightPx * (capInkW() / CAP_INK_H));
    }


    static int capHostHeightPx(int heightPx) {
        if (heightPx <= 0) {
            return 0;
        }
        // The scale the width above was asked for, so the bands cost the same number of pixels
        // the rest of the design is drawn at rather than a fixed pixel count.
        final float scale = heightPx / CAP_INK_H;
        final int grown = Math.round(heightPx + 2f * CAP_BAND_H * scale);
        return Math.max(heightPx, grown);
    }

    // ------------------------------------------------------------------ colours
    static final int CRITICAL = 0xFFFF3B30;
    static final int CHARGING_LIGHT = 0xFF34C759;
    static final int CHARGING_DARK = 0xFF1F8F3D;
    static final int LOW_POWER_LIGHT = 0xFFF2B900;
    static final int LOW_POWER_DARK = 0xFFC99700;

    /**
     * Amber used for the charging bolt while the battery reports quick charge.
     * At the real 60px status-bar size colour is the only channel that stays
     * instantly legible, so quick charge is told apart by hue rather than by
     * extra glyphs (two bolts merge into an unreadable blob at that size).
     */
    static final int QUICK_CHARGE = 0xFFFFBA28;
}
