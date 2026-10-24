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

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/**
 * Draws the "trio" glyph (battery ring + Wi-Fi arcs + signal dots + level indicator)
 * into a host view's canvas, in the 120x120 design space of {@link TrioGeometry}.
 */
final class TrioRenderer {

    private TrioRenderer() {
    }

    private static final Paint STROKE = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint FILL = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint TEXT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path BOLT = buildBolt();
    private static final Path WIFI_DOT = buildWifiDot();
    private static final RectF ARC = new RectF();
    /** Scratch for the rectangular battery bar; rewritten every frame, never kept. */
    private static final Path RECT_BAR = new Path();
    /**
     * Scratch for the capsule's outline; rebuilt each pass because the gap the
     * dot rows cut changes with the stroke and with the mobile switch.
     */
    private static final Path CAPSULE = new Path();
    private static final RectF RECT_RECT = new RectF();
    /**
     * Scratch for the ink box {@link #textInkHeight} measures; never kept.
     *
     * <p>An {@link Rect}, not a {@code RectF}: the platform's only
     * {@code getTextBounds} overload that takes a box is
     * {@code getTextBounds(String, int, int, Rect)}, and it reports whole pixels.
     * The desktop shim used to declare a {@code RectF} variant, which made a
     * signature that does not exist on the device compile happily in the harness -
     * it built there and failed only against the real SDK.
     */
    private static final Rect TEXT_INK = new Rect();
    /**
     * Corner radii of the lit bar segment: rounded on the left so it follows the
     * track's capsule, square on the right so the level ends on a clean vertical
     * edge. Order is the {@link Path#addRoundRect} order - TL, TR, BR, BL.
     */
    private static final float[] RECT_RADII = new float[8];

    /**
     * The height of the ink {@code text} actually paints at {@code size}, in the
     * same units.
     *
     * <p>Deliberately not {@code ascent + descent}. Those bounds describe the line
     * box a typesetter reserves - leading above the capitals, room below for
     * descenders - and a digits-only reading draws none of it. At 36pt the metrics
     * are 45 units against 26.3 units of ink, so using them to decide whether a line
     * fits makes every string look about 1.7x taller than it is, and the fit shrinks
     * it accordingly.
     *
     * <p>{@code getTextBounds} reports the real drawn extent, which is what "does
     * this fit in that slot" should be asking.
     */
    private static float textInkHeight(String text, float size) {
        TEXT.setTextSize(size);
        TEXT.getTextBounds(text, 0, text.length(), TEXT_INK);
        return TEXT_INK.bottom - TEXT_INK.top;
    }

    /**
     * A baseline that centres the drawn ink on the slot, given the one the font
     * metrics produce.
     *
     * <p>The metrics version puts the middle of {@code ascent..descent} on the slot
     * centre, but those bounds include the leading a face reserves above its
     * capitals and below its descenders. A digits-only reading draws none of that,
     * so its ink sits high - enough that a line fitted to its third still reached
     * into the arc above it.
     *
     * <p>{@code getTextBounds} reports the real ink box relative to the baseline,
     * which is the same space the font metrics use, so the correction is the
     * difference between the two centres. <b>Both are negative above the
     * baseline</b>: mixing in their absolute values, as an earlier version of this
     * did, adds {@code ascent + descent} instead of subtracting it and pushes the
     * baseline most of a line below the slot.
     */
    private static float inkCentredBaseline(String text, float size, float slotCentre) {
        if (text == null || text.isEmpty() || size <= 0f) {
            return slotCentre;
        }
        TEXT.setTextSize(size);
        TEXT.getTextBounds(text, 0, text.length(), TEXT_INK);
        final Paint.FontMetrics fm = TEXT.getFontMetrics();
        // Both boxes are measured in the same space: y grows downward and the
        // baseline is 0, so `top`/`ascent` are negative. Their centres are therefore
        // comparable directly, and the baseline that centres the ink is the slot
        // centre minus that centre.
        //
        // The previous version mixed the metrics' (ascent + descent) with a baseline
        // built from (ascent - descent), i.e. two different reference points, which
        // pushed every string about 35 units - most of a line - below its slot.
        final float inkCentre = (TEXT_INK.top + TEXT_INK.bottom) * 0.5f;
        return slotCentre - inkCentre;
    }

    /**
     * The weight currently installed on {@link #TEXT}; -1 until first applied.
     *
     * <p>Cached against the installed face rather than per text role: the
     * percentage and the network type can carry different weights and both may
     * be drawn in the same frame, so a role-keyed cache would make the second
     * role skip a swap it still needs. With the shared default (700) this is
     * still zero swaps per frame.
     */
    private static int sTextWeight = -1;

    /**
     * Base face the weight is applied to. {@code Typeface.create(String, int)}
     * only takes a style bitmask, so a numeric weight has to go through the
     * {@code (Typeface, int, boolean)} overload with this as its family.
     */
    private static final Typeface TEXT_BASE = Typeface.create("sans-serif", Typeface.NORMAL);

    /**
     * Path of the font currently installed on {@link #TEXT}, or "" for the system
     * family. The request, not the outcome: see {@link #sFontFailed}.
     */
    private static volatile String sFontPath = "";

    /**
     * The loaded custom face, or null when none is in effect.
     *
     * <p>Held across frames on purpose. Loading a font reads and parses the file,
     * and {@link #applyWeight} runs inside {@code onDraw}; doing that work per
     * frame would be an IO and parse on the render thread, which is exactly what
     * the {@code sTextWeight} cache above exists to avoid. {@link #setFont}
     * refreshes it when - and only when - the setting actually changes.
     */
    private static volatile Typeface sFontFace;

    /**
     * The path whose load was last attempted and rejected, so a missing or
     * unreadable file is not re-read on every frame. Empty means "no failure
     * recorded".
     */
    private static volatile String sFontFailed = "";

    /**
     * Bumped on every font change, and compared by {@link #applyWeight} alongside
     * the weight so a swapped face is never skipped by an unchanged weight.
     */
    private static volatile int sFontGeneration;

    /**
     * Loads the user's font, or falls back to the system family.
     *
     * <p>Called from the settings-change path, never from a draw. Passing the same
     * path twice is free, which matters because the appearance is rebuilt on every
     * frame: the caller can just hand over what the settings say and let this
     * decide whether anything actually moved.
     *
     * <p>A file that cannot be read - deleted, truncated, or refused by the
     * platform - leaves the system face in place and is recorded, so the status
     * bar keeps working and the failure is not retried every frame. The next
     * different path clears that record and tries again.
     */
    static void setFont(String path) {
        final String want = path == null ? "" : path;
        final String current = sFontPath;
        if (want.equals(current)) {
            // Same request. A recorded failure for a path that is still the
            // request is deliberately not retried: the file may be unreadable for
            // a reason that does not change (a permissions mismatch, a directory),
            // and retrying per frame would spin on IO.
            return;
        }
        sFontPath = want;
        sFontFailed = "";
        sFontFace = null;
        sFontGeneration++;
        if (want.length() == 0) {
            return;
        }
        try {
            sFontFace = Typeface.createFromFile(want);
        } catch (Throwable t) {
            sFontFace = null;
            sFontFailed = want;
        }
    }

    /** Whether the current font request failed to load. For diagnostics. */
    static boolean fontFailed() {
        return sFontFailed.length() > 0;
    }

    /** The path {@link #setFont} was last asked for, "" when the system face is in use. */
    static String fontPath() {
        return sFontPath;
    }

    /**
     * Whether the glyph should embolden {@code weight} synthetically.
     *
     * <p>With the system family {@code Typeface.create(base, weight, false)} has
     * nine real weights to choose from and no help is needed. A user-supplied file
     * usually carries one, so asking for 700 and 300 returns the same face and the
     * weight sliders appear dead. Synthetic emboldening restores a visible
     * difference on the same threshold the platform itself uses for "bold", and
     * because every draw path measures its text <em>after</em> this is applied, the
     * wider synthetic face is what the fit math sees - the digits cannot overflow
     * the space they were sized for.
     */
    static boolean fakeBoldFor(int weight) {
        return sFontFace != null && weight >= FAKE_BOLD_FROM;
    }

    /** The weight at or above which a custom face is emboldened synthetically. */
    private static final int FAKE_BOLD_FROM = 600;

    /**
     * The font generation {@link #applyWeight} last installed on {@link #TEXT}.
     *
     * <p>Paired with {@link #sTextWeight} so the cache cannot confuse "the same
     * weight" with "the same face": a font swap leaves the weight alone, and on
     * the weight test alone the swap would be skipped.
     */
    private static int sTextGeneration = -1;

    static {
        STROKE.setStyle(Paint.Style.STROKE);
        STROKE.setStrokeCap(Paint.Cap.ROUND);
        STROKE.setStrokeJoin(Paint.Join.ROUND);
        FILL.setStyle(Paint.Style.FILL);
        TEXT.setStyle(Paint.Style.FILL);
        TEXT.setTextAlign(Paint.Align.CENTER);
        TEXT.setLetterSpacing(TrioGeometry.VALUE_LETTER_SPACING);
    }

    // ------------------------------------------------------------------ public

    static void draw(Canvas canvas, View host, TrioState s) {
        draw(canvas, host, s, TrioConfig.get());
    }

    /**
     * Draws the glyph with the user's current settings. The text size is applied
     * here rather than in the static initialiser so a live settings change is
     * picked up by the very next frame.
     */
    static void draw(Canvas canvas, View host, TrioState s, TrioSettings cfg) {
        drawState(canvas, host.getWidth(), host.getHeight(), s, cfg);
    }

    /**
     * The same frame for a host that paints its own content underneath - Flyme's
     * battery view draws the stock meter first and lets this run on top, so the
     * canvas must not be cleared.
     */
    static void drawFlyme(Canvas canvas, View host, TrioState s, TrioSettings cfg) {
        if (s == null || cfg == null) {
            return;
        }
        drawInto(canvas, host.getWidth(), host.getHeight(), s.level, s.charging, s.quickCharging,
                s.powerSave, s.low, s.wifiPresent ? s.wifiLevel : -1, s.mobileLevel,
                s.slotLevels, s.mobileType, s.foreground(), cfg, false, s.wifiPresent);
    }

    /**
     * The same frame for a caller that has a canvas of its own instead of a host
     * view to measure - {@code GlyphDrawable} hands the icon view's own bounds
     * through, and the slot it paints into can be asked for nothing else.
     */
    static void drawState(Canvas canvas, int w, int h, TrioState s, TrioSettings cfg) {
        if (w <= 0 || h <= 0 || s == null || cfg == null) {
            return;
        }
        drawInto(canvas, w, h, s.level, s.charging, s.quickCharging, s.powerSave, s.low,
                s.wifiPresent ? s.wifiLevel : -1, s.mobileLevel, s.slotLevels, s.mobileType,
                s.foreground(), cfg, true, s.wifiPresent);
    }

    /**
     * Renders the glyph into any canvas of the given size. Kept free of
     * {@link View} so the settings app can preview the exact same drawing code
     * instead of a hand-maintained copy of it.
     */
    static void drawInto(Canvas canvas, int w, int h, int level, boolean charging,
                         boolean powerSave, boolean low, int wifiLevel, int mobileLevel,
                         int fg, TrioSettings cfg) {
        drawInto(canvas, w, h, level, charging, false, powerSave, low, wifiLevel, mobileLevel,
                null, null, fg, cfg, true);
    }

    /**
     * @param quickCharging {@code true} while the host reports a fast charger;
     *   only the bolt's colour changes, never its shape or size.
     * @param slotLevels the per-SIM signal levels by slot - SIM 1 first - or
     *   {@code null} when the caller has none, which keeps the single row.
     * @param clearWhenDone {@code true} when this glyph owns the whole canvas
     *   (the hooked view case); {@code false} when drawing onto a canvas that
     *   already holds the settings app's background.
     */
    static void drawInto(Canvas canvas, int w, int h, int level, boolean charging,
                         boolean quickCharging, boolean powerSave, boolean low,
                         int wifiLevel, int mobileLevel, int[] slotLevels, String mobileType,
                         int fg, TrioSettings cfg, boolean clearWhenDone) {
        // Connected, not merely readable: a live signal of 0 is a weak connection, and -1
        // is "no reading" rather than "no Wi-Fi". Neither answers the question the
        // network-type suppression asks. Inferred here so no caller has to know.
        drawInto(canvas, w, h, level, charging, quickCharging, powerSave, low,
                wifiLevel, mobileLevel, slotLevels, mobileType, fg, cfg, clearWhenDone,
                wifiLevel >= 0);
    }

    /**
     * The same, told outright whether Wi-Fi is CONNECTED.
     *
     * <p>{@code wifiConnected} is separate from {@code wifiLevel} on purpose. The level is
     * a signal strength: 0 means a live but weak link, and -1 means this frame has no
     * reading at all - which is also what an unconnected device reports. Deciding a
     * connection from it conflated three states, and the settings preview (a fixed scene,
     * which always carries a level) then read as "Wi-Fi is up" in every scene but one.
     */
    static void drawInto(Canvas canvas, int w, int h, int level, boolean charging,
                         boolean quickCharging, boolean powerSave, boolean low,
                         int wifiLevel, int mobileLevel, int[] slotLevels, String mobileType,
                         int fg, TrioSettings cfg, boolean clearWhenDone,
                         boolean wifiConnected) {
        if (w <= 0 || h <= 0 || cfg == null) {
            return;
        }
        // Resolve what is on screen once, here, from the single rule source. The
        // layout methods below read positions off it instead of re-deriving the
        // switches themselves, which is what used to let the two arrangements
        // drift apart - and let the documentation drift from both.
        final TrioAppearance appearance = TrioAppearance.of(cfg);
        // The network type is NOT suppressed here any more.
        //
        // It used to be: the whole label was replaced with null at this entry point, which made
        // every arrangement obey at once. That was right while the question was "is Wi-Fi on",
        // and wrong once the answer depends on WHERE the pieces were placed - handing off the
        // arcs, an out-of-ring label, a band that would be left empty. Those are decisions over
        // the layout, and the layout is resolved in TrioAppearance. Suppressing the string here
        // would pre-empt them: the band would be filled from the priority list with the label,
        // and the label would then draw as nothing, leaving exactly the blank zone the
        // per-band rule exists to prevent.
        //
        // See TrioAppearance.hidesTypeForWifi for the arrangement-level half and
        // firstDrawableInk for the per-band half.
        final String shownType = mobileType;
        // The capsule's length belongs to the frame: every horizontal quantity below reads it
        // through TrioGeometry, and setting it here - once, before anything is drawn - is what
        // keeps the outline, the fill, the cells and the hit boxes on one shape.
        TrioGeometry.setCapsuleLength(appearance.capsuleLength);
        // The transform is asked for rather than worked out here, because the
        // settings screen's tappable zones have to land on the very pixels this
        // drawing lands on. Two copies of the fit - one to draw with and one to
        // hit-test with - is precisely how a hot spot drifts off the thing it
        // names. See designTransform.
        final float[] transform = new float[3];
        if (!designTransform(w, h, appearance, transform)) {
            return;
        }
        final float scale = transform[0];
        // Both callers - the hooked view and the settings preview - come through
        // here, so this one line is enough to keep the loaded face in step with
        // the setting. The call is free when nothing changed; the load it may
        // trigger happens only on the frame a new path arrives, never per frame.
        setFont(appearance.fontPath);
        // The two arrangements have different ink extents, so each is fitted to
        // the host view with its own box.
        final boolean rect = appearance.rect;
        final boolean capsule = appearance.capsule;

        if (clearWhenDone) {
            // The native onDraw only clears the canvas on its non-legacy path
            // (MiuiBatteryMeterIconView.onDraw L543-544). On the legacy path it just
            // calls super.onDraw and returns, so the framework's battery drawable
            // would show through underneath the glyph. Clear unconditionally: our
            // glyph is the sole content of this view either way.
            canvas.drawColor(0, PorterDuff.Mode.CLEAR);
        }

        final int role = roleColor(appearance, level, charging, powerSave, low, fg);

        // A slot with no reading counts as absent, so a single-SIM device - or a
        // poll that has not answered yet - is not mistaken for a second SIM.
        final int[] slots = (slotLevels != null && slotLevels.length >= TrioState.SIM_SLOTS)
                ? slotLevels : null;
        final int sims = (slots == null) ? 0
                : (slots[0] >= 0 ? 1 : 0) + (slots[1] >= 0 ? 1 : 0);

        final int save = canvas.save();
        canvas.translate(transform[1], transform[2]);
        canvas.scale(scale, scale);

        if (capsule) {
            drawCapsuleLayout(canvas, level, charging, quickCharging, wifiLevel, mobileLevel, shownType,
                    slots, sims, role, fg, appearance, wifiConnected);
        } else if (rect) {
            drawRectLayout(canvas, level, charging, quickCharging, wifiLevel, mobileLevel,
                    slots, sims, shownType, role, fg, appearance);
        } else {
            drawRingLayout(canvas, level, charging, quickCharging, wifiLevel, mobileLevel,
                    slots, sims, shownType, role, fg, appearance, wifiConnected);
        }

        canvas.restoreToCount(save);
    }

    /**
     * The ring arrangement.
     *
     * <p>The canvas is already scaled into the design space and positioned on the ink
     * box, so everything here is plain design-unit drawing.
     *
     * <p>Which piece goes where is not decided here - {@link TrioAppearance.Ring} has
     * already resolved the three slots. This method only draws.
     */
    private static void drawRingLayout(Canvas canvas, int level, boolean charging,
                                       boolean quickCharging, int wifiLevel, int mobileLevel,
                                       int[] slotLevels, int sims, String mobileType, int role,
                                       int fg, TrioAppearance a, boolean wifiConnected) {
        final TrioAppearance.Ring r = a.ring(level, charging, wifiLevel, mobileType, sims,
                wifiConnected);
        drawFreeRing(canvas, level, quickCharging, wifiLevel, mobileLevel,
                slotLevels, mobileType, role, fg, a, r);
    }

    /** Explicit slots share the editor's thirds and fit ink on BOTH axes. */
    private static void drawFreeRing(Canvas c, int level, boolean quickCharging, int wifiLevel,
                                     int mobileLevel, int[] levels, String type, int role, int fg,
                                     TrioAppearance a, TrioAppearance.Ring r) {
        drawBattery(c, level, role, fg, a, r.gapStart(), r.gapEnd(), r.sweep(), r.arcStart());
        for (int z = 0; z < 3; z++) {
            final int part = r.parts[z];
            if (part == Prefs.PART_NONE) continue;
            final float cx = TrioGeometry.B_CX;
            final float cy = TrioGeometry.ZONE_CY[z];
            final float width = 78f - a.ringStroke;
            final float height = z == 0 ? TrioGeometry.DOT_BOX_TOP
                    : z == 1 ? TrioGeometry.DOT_BOX_CENTRE : TrioGeometry.DOT_BOX_BOTTOM;
            final int save = c.save();
            c.translate(cx, cy);
            if (part == Prefs.PART_WIFI) {
                // The reference bounds include the default stroke. Extra thickness
                // must count before scaling, otherwise the arcs escape their slot.
                final float extra = Math.max(0f, a.arcStroke - TrioGeometry.W_STROKE);
                // The slot fit, then the user's own size inside it.
                final float scale = Math.min(width / (TrioGeometry.W_INK_W + extra),
                        height / (TrioGeometry.W_INK_H + extra)) * a.wifiScaleFactor();
                c.scale(scale, scale);
                c.translate(-TrioGeometry.W_CX, -TrioGeometry.W_INK_CY);
                drawWifiArcs(c, wifiLevel, fg, a);
            } else if (part == Prefs.PART_BOLT) {
                // The bolt is fitted to its slot, then multiplied by the user's size. The
                // product is NOT re-clamped to the slot: a user asking for a bigger bolt
                // wants it bigger, and the slot is a third of the ring rather than a hard
                // edge. Only the ink centre stays pinned, so growing is symmetric.
                final float fit = Math.min(width / 15f, height / TrioGeometry.BOLT_INK_H);
                final float scale = fit * a.boltScaleFactor();
                c.scale(scale, scale);
                c.translate(-TrioGeometry.BOLT_CX_REF, -TrioGeometry.BOLT_INK_CY);
                FILL.setColor(quickCharging ? TrioGeometry.QUICK_CHARGE : fg);
                c.drawPath(BOLT, FILL);
            } else if (part == Prefs.PART_MOBILE
                    || part == Prefs.PART_SIM_1 || part == Prefs.PART_SIM_2) {
                // All three are the same drawing: one row of four level beads. They
                // differ only in which reading feeds them - PART_MOBILE is the aggregate
                // meter, the two SIM parts are the individual cards.
                //
                // The row is drawn by ONE routine regardless of which part it is, and
                // that routine already shapes itself by the third it sits in: beads
                // threaded onto the ring's opening at the top and bottom, a compact
                // horizontal row in the middle. Drawing PART_MOBILE through a separate
                // straight-line path was what made "move the signal to the top" turn
                // into a flat row inside a round ring.
                final int rowLevel = part == Prefs.PART_MOBILE
                        ? mobileLevel
                        : (levels == null ? -1 : levels[part == Prefs.PART_SIM_1 ? 0 : 1]);
                final int active = rowLevel < 0 ? 0 : Math.min(4, rowLevel);
                // The user's size multiplies the reference bead; the opening's room still
                // caps it, so a large setting stops growing rather than overrunning the
                // arc's caps. See TrioGeometry.arcRowDotR's four-argument form.
                drawArcDotRow(c, active, z,
                        TrioGeometry.arcRowDotR(z, a.ringStroke, width, a.dotScaleFactor()),
                        fg, a);
            } else {
                final String text = part == Prefs.PART_VALUE ? String.valueOf(level)
                        : part == Prefs.PART_TYPE ? type : TrioState.sClockText;
                if (text != null && !text.isEmpty()) {
                    final int weight = part == Prefs.PART_VALUE ? a.valueWeight
                            : part == Prefs.PART_TYPE ? a.typeWeight : a.clockWeight;
                    final float requested = part == Prefs.PART_VALUE ? a.valueSize
                            : part == Prefs.PART_TYPE ? a.typeSize : a.clockSize;
                    applyWeight(weight);
                    TEXT.setTextSize(requested);
                    // Type is fitted to the THIRD it sits in, not to the small
                    // placement box that positions a drawn figure (28/44/20 units).
                    //
                    // The height term measures the INK, not ascent + descent. That
                    // distinction is the whole bug the size setting had: the metrics
                    // reserve leading above the capitals and below the descenders that
                    // a reading never draws - at 36pt they are 45 units against 26.3
                    // units of actual ink - so dividing by them capped every line at
                    // about 58% of the room it really had, and the bottom third came
                    // out around a third of the size asked for.
                    final float room = TrioGeometry.zoneTextRoomH(z);
                    final int suffixScale = part == Prefs.PART_TYPE ? a.typeSuffixScale : 100;
                    // The height cap is a straight ratio at the requested size.
                    final float inkAtRequested = Math.max(1f, textInkHeight(text, requested));
                    final float maxByHeight = requested * room / inkAtRequested;
                    // The width cap is NOT independent of the answer: the budget is the
                    // narrowest chord the line spans, which depends on how tall the line
                    // ends up - and that depends on the size being solved for. Iterate
                    // to a fixed point instead of guessing once. (The first attempt at
                    // this multiplied the ink height by fitted/requested as well, which
                    // scaled it twice and never converged.)
                    float fitted = maxByHeight;
                    for (int pass = 0; pass < 4; pass++) {
                        final float inkH = Math.max(1f, textInkHeight(text, fitted));
                        final float maxByWidth = fitSize(text, requested,
                                TrioGeometry.zoneTextRoomW(z, a.ringStroke, inkH), suffixScale);
                        final float next = Math.min(maxByHeight, maxByWidth);
                        if (Math.abs(next - fitted) < 0.05f) {
                            fitted = next;
                            break;
                        }
                        fitted = next;
                    }
                    final float inkH = Math.max(1f, textInkHeight(text, requested));
                    // Centred in the band, and on its INK rather than its metrics: the
                    // metrics reserve leading above the capitals that a digits-only
                    // string never draws, so a metrics-centred line sits high enough to
                    // touch the arc it was fitted to clear.
                    //
                    // The canvas is already translated to the zone's centre by the
                    // caller, so what the baseline helper wants is that local centre -
                    // 0 - not an absolute design-space y.
                    final float slotCentre = TrioGeometry.zoneTextCy(z, a.ringStroke)
                            - TrioGeometry.ZONE_CY[z];
                    final float baseline = inkCentredBaseline(text, fitted, slotCentre);
                    if (part == Prefs.PART_TYPE) drawType(c, text, 0f, baseline, fitted, fg, a);
                    else {
                        TEXT.setTextAlign(Paint.Align.CENTER);
                        TEXT.setTextSize(fitted);
                        TEXT.setColor(fg);
                        c.drawText(text, 0f, baseline, TEXT);
                    }
                }
            }
            c.restoreToCount(save);
        }
    }

    /**
     * One card's four level dots, laid along the ring's own curve.
     *
     * <p>Drawn on the arc that passes through the slot, concentric with the battery
     * ring, rather than on a horizontal line. The shipped level dots already bend
     * ({@link TrioGeometry#DOTS} is 104.2, 111.2, 111.7, 105.8 - lowest in the
     * middle), and a placed card that drew a straight row would read as a foreign
     * shape inside a round glyph.
     *
     * <p>Which way the curve bends depends on where the third sits. A row in the
     * bottom third belongs to the ring's lower opening and bows <em>downward</em>,
     * matching {@code DOTS}; a row in the top third belongs to the 12 o'clock
     * opening and bows the other way, so it stays concentric instead of dipping
     * toward the ring centre. The middle third has no opening of its own, so its row
     * follows the ring's side curvature and bows toward the centre.
     *
     * <p>The sagitta is derived from the arc's radius rather than picked: for a chord
     * of half-width {@code w} on a circle of radius {@code R}, the rise is
     * {@code R - sqrt(R^2 - w^2)}. That way the row is always a genuine arc - widen
     * it and it bends more, shrink it and it flattens - exactly as a real slice of
     * the ring would.
     *
     * @param active   how many of the four dots are lit, 0..4
     * @param zone     {@code 0} top, {@code 1} middle, {@code 2} bottom
     * @param radius   dot radius, already fitted to the slot
     */
    private static void drawArcDotRow(Canvas c, int active, int zone, float radius,
                                      int fg, TrioAppearance a) {
        final int inactive = withAlpha(fg, a.trackAlpha);
        if (zone == 1) {
            // The middle third is not a notch: the ring runs past it on both sides, so
            // there is no bare arc to thread beads onto. It keeps the row it has always
            // had - four dots in a horizontal line, centred in the slot.
            final float step = radius * 3f;
            for (int dot = 0; dot < 4; dot++) {
                FILL.setColor(dot < active ? fg : inactive);
                c.drawCircle((dot - 1.5f) * step, 0f, radius, FILL);
            }
            return;
        }
        // In the top and bottom thirds the dots are threaded onto the arc's own centre
        // line, inside the notch where the ring carries no ink - so a bead as thick as
        // the ring reads as the outline cut into segments. Positioning them inside the
        // glyph instead was the earlier mistake: it made the dots shrink as the stroke
        // grew, which is backwards.
        //
        // Placed in the RING's frame, then converted back into this zone's local space
        // (the caller has already translated to (B_CX, ZONE_CY[z])).
        final float rho = TrioGeometry.B_R;
        // The bare arc this row sits in: the 12 o'clock notch above, the ring's own
        // opening below. Both are GAP_WIDTH_DEG wide.
        final float openingRad = (float) Math.toRadians(TrioGeometry.GAP_WIDTH_DEG);
        // Angular inset for the arc's cap plus the end bead's own radius, and a further
        // unit so the two antialiased edges do not meet.
        final float inset = (radius + a.ringStroke * 0.5f + 1f) / rho;
        final float halfAngle = Math.max(0f, openingRad * 0.5f - inset);
        // The row keeps its own span and does NOT grow with the opening: the notch is
        // deliberately wider than the four beads need, so the arc's ends stand clear of
        // them. Spreading the beads to fill whatever the opening allows would cancel
        // that - the further the ends moved, the further the beads would follow, and the
        // clearance would never change.
        //
        // BEAD_SPAN_DEG is the span the four beads occupy, independent of the notch.
        final float beadHalfAngle = (float) Math.toRadians(TrioGeometry.BEAD_SPAN_DEG * 0.5f);
        final float halfCentreAngle = Math.min(halfAngle, beadHalfAngle);
        for (int dot = 0; dot < 4; dot++) {
            final float t = (dot - 1.5f) / 1.5f;
            final float angle = t * halfCentreAngle;
            // Top third: the notch straddles 12 o'clock, above the ring centre.
            // Bottom third: the opening straddles 6 o'clock, below it.
            final float sign = zone == 2 ? 1f : -1f;
            final float ringX = TrioGeometry.B_CX + rho * (float) Math.sin(angle);
            final float ringY = TrioGeometry.B_CY + sign * rho * (float) Math.cos(angle);
            FILL.setColor(dot < active ? fg : inactive);
            c.drawCircle(ringX - TrioGeometry.B_CX,
                    ringY - TrioGeometry.ZONE_CY[zone], radius, FILL);
        }
    }

    /**
     * The rectangular arrangement: two columns of four level dots, the top slot
     * between them, the enlarged percentage below that, and the battery bar
     * across the bottom.
     *
     * <p>Draw order is back to front and does not depend on which slot is used,
     * because none of the pieces overlap: the bar and the dots are the frame, and
     * the value never reaches either.
     *
     * <p>This arrangement has no notch and no ring centre, so the centring switch
     * means nothing here - and {@link TrioAppearance} already says so, which is
     * what lets the settings screen explain the switch's scope instead of
     * silently ignoring it.
     */
    private static void drawRectLayout(Canvas canvas, int level, boolean charging,
                                       boolean quickCharging, int wifiLevel, int mobileLevel,
                                       int[] slotLevels, int sims, String mobileType, int role,
                                       int fg, TrioAppearance a) {
        final TrioAppearance.Rect r = a.rect(level, charging, wifiLevel, mobileType, sims);
        if (r.dots) {
            drawRectDots(canvas, mobileLevel, slotLevels, r.dual, fg, a);
        }
        drawRectBatteryBar(canvas, level, role, fg, a);

        // One slot at the top, and only one thing in it. The bolt outranks the
        // arcs exactly as it does in the ring arrangement, and the arcs outrank
        // the network type, which shares that slot rather than claiming a second
        // line of its own.
        if (r.bolt) {
            drawRectBolt(canvas, quickCharging ? TrioGeometry.QUICK_CHARGE : fg, a);
        } else if (r.wifi) {
            drawRectWifi(canvas, wifiLevel, fg, a);
        } else if (r.type) {
            drawRectType(canvas, mobileType, fg, a);
        }

        if (r.value) {
            drawRectValue(canvas, level, fg, a);
        }
    }

    // ---------------------------------------------------------- capsule layout

    /**
     * The capsule arrangement: a stadium outline that <em>is</em> the battery
     * gauge, broken at 12 and 6 o'clock by two four-dot rows, with the time on
     * the left inside and the percentage, the arcs or the bolt on the right.
     *
     * <p>The order of the two outline passes is the whole design. The track is
     * drawn first, in full, in the track colour; then the charged part is drawn
     * over it in the role colour, covering the fraction of the outline's length
     * the level asks for. That is what reads as one line filling up rather than as
     * two lines meeting.
     *
     * <p>Both passes cut their gap at the dot rows, so the outline is drawn as the
     * two open runs it actually is and the two passes align exactly.
     *
     * <p>Nothing here competes for space the way the ring's notch does: the
     * interior is a fixed two-slot strip, so {@code valueCentred} has nothing to
     * move and the readings simply sit side by side.
     */
    private static void drawCapsuleLayout(Canvas canvas, int level, boolean charging,
                                          boolean quickCharging, int wifiLevel, int mobileLevel, String mobileType,
                                          int[] slotLevels, int sims, int role, int fg,
                                          TrioAppearance a, boolean wifiConnected) {
        final float stroke = a.ringStroke * TrioGeometry.CAP_STROKE_RATIO;
        final TrioAppearance.Capsule cap =
                a.capsule(level, charging, wifiLevel, mobileType, sims, wifiConnected);

        // The track first, in full, then the charged part over it. `level < 0` is
        // "no reading yet" and fills nothing: painting a whole outline would claim
        // a full battery on a device that had simply not answered.
        STROKE.setStrokeWidth(stroke);
        STROKE.setStyle(Paint.Style.STROKE);
        // One reach per opening, because the two openings no longer necessarily hold the
        // same thing: a dot row is only as wide as its four beads, a reading is as wide as
        // its own ink. The outline is cut back by whichever is there, so a reading sits in a
        // gap the stroke does not run through - which is what makes it legible rather than
        // lost against the line it was drawn over.
        final float topReach = openingReach(cap, TrioZones.Zone.TOP, stroke, level, mobileType, a);
        final float bottomReach =
                openingReach(cap, TrioZones.Zone.BOTTOM, stroke, level, mobileType, a);
        buildCapsuleOutline(CAPSULE, topReach, bottomReach);
        STROKE.setColor(withAlpha(fg, a.trackAlpha));
        canvas.drawPath(CAPSULE, STROKE);

        if (level > 0) {
            STROKE.setColor(role);
            drawCapsuleFill(canvas, stroke, Math.min(1f, level / 100f), topReach, bottomReach);
        }

        if (cap.dots) {
            drawCapsuleDots(canvas, mobileLevel, slotLevels, cap.dual, stroke, fg, a, cap);
        }

        // The left column is a BODY slot - the interior's own height budget, which is -1 in
        // openingRoom's sense - but its contents are the user's choice, so it is dispatched
        // like a cell rather than drawn unconditionally. The clock is still the default, so
        // an install that never opened this setting draws exactly what it used to (issues
        // 44 and 47: the column used to be reserved for the time).


        // Three bands, the same three the ring has: the two openings and the interior.
        // The interior's two cells divide its width by what they actually draw, so a size the
        // user asks for is not capped by width reserved for a string that is not on screen.
        drawCapsuleCell(canvas, stroke, TrioZones.Zone.TOP, cap, level, quickCharging,
                wifiLevel, mobileType, fg, a);
        drawCapsuleCell(canvas, stroke, TrioZones.Zone.CENTRE, cap, level, quickCharging,
                wifiLevel, mobileType, fg, a);
        drawCapsuleCell(canvas, stroke, TrioZones.Zone.BOTTOM, cap, level, quickCharging,
                wifiLevel, mobileType, fg, a);

        // Drawn AFTER the three cells on purpose: the left column and the interior can
        // both hold a reading now, and painting the column last is what keeps a wide
        // piece (the arcs) from being covered by the interior's own ink.
        drawCapsuleLeft(canvas, stroke, cap, level, quickCharging, wifiLevel, mobileLevel,
                slotLevels, mobileType, fg, a);
    }

    /**
     * Draws whatever the capsule's LEFT column holds, at that column's own centre.
     *
     * <p>Deliberately the same vocabulary and the same per-part dispatch as
     * {@link #drawCapsuleCell}: the left column is a body slot like the interior, and a
     * piece dropped there should look the same as the same piece anywhere else. What it
     * does NOT share is the centre - the left column has its own x - and the fact that it
     * can hold a dot row, which a band can and the interior cannot.
     */
    private static void drawCapsuleLeft(Canvas c, float stroke, TrioAppearance.Capsule cap,
                                       int level, boolean quickCharging, int wifiLevel,
                                       int mobileLevel, int[] slotLevels, String type,
                                       int fg, TrioAppearance a) {
        // From the layout's LEFT cell, the same place the three bands read theirs:
        // the left column is a cell of the capsule now, not a setting beside it.
        final int part = cap.parts[TrioZones.Zone.LEFT.ordinal()];

        if (part == Prefs.PART_NONE) {
            return;
        }
        // Left-aligned against the inner edge: the cell reads as its own column
        // starting at the shape, not as ink floating in the middle of the half-slot.
        // The user's own nudge, added to the cell's centre. Design units, so it
        // scales with the glyph like everything else here.
        // The cell's LEFT EDGE, which is the anchor every piece in it hangs from.
        final float anchor = TrioGeometry.capLeftAlignedX(stroke) + a.capsuleLeftDx;
        final float cy = TrioGeometry.capInteriorCy(stroke) + a.capsuleLeftDy;
        // TEXT is drawn from this anchor (left-aligned, so the anchor is where the ink
        // starts), but an ICON is drawn about its centre - and half an icon is a long way.
        // Sharing one coordinate between the two is what made a Wi-Fi glyph or a bolt sit
        // half its own width to the right of the text it replaced, so a cell holding the
        // same nudge put different pieces in visibly different places.
        //
        // So an icon is centred in a half-step from the anchor: close enough to the edge to
        // read as the start of the row, far enough that the icon is not clipped by the
        // stroke. See capLeftIconCx for the figure.
        final float cx = TrioGeometry.capLeftIconCx(stroke, part) + a.capsuleLeftDx;
        // What the two interior cells actually draw, so each gets the width its own string
        // needs instead of the clock-and-number ratio. See TrioGeometry.capReadingSlotWidth.
        // Dot rows are not offered in this column: drawCapsuleDots paints the two OPENINGS,
        // whose y come from the outline geometry. A row here would need its own baseline.
        // LEFT on every branch: this cell reads from its inner edge, nudge or no nudge.
        switch (part) {
            case Prefs.PART_CLOCK:
                drawCapsuleTime(c, stroke, fg, a, cx, cy, -1f, SlotAlign.LEFT);
                break;
            case Prefs.PART_VALUE:
                drawCapsuleValue(c, stroke, level, fg, a, cx, cy, -1f, SlotAlign.LEFT);
                break;
            case Prefs.PART_TYPE:
                drawCapsuleType(c, stroke, type, fg, a, cx, cy, -1f,
                        TrioGeometry.capReadingTrackWidth(stroke),
                        bodyTypeSize(a), SlotAlign.LEFT);
                break;
            case Prefs.PART_WIFI:
                drawCapsuleWifi(c, stroke, wifiLevel, fg, a, cx, cy);
                break;
            case Prefs.PART_BOLT:
                drawCapsuleBolt(c, stroke, quickCharging ? TrioGeometry.QUICK_CHARGE : fg,
                        a, cx, cy);
                break;
            default:
                break;
        }
    }
    /**
     * Draws whatever {@code band} holds, at that band's own centre.
     *
     * <p>The centre comes from the zone, not from the piece: a bolt and a percentage are laid
     * out differently inside the capsule, but they agree about where each band is, which is
     * the whole point of putting them in bands.
     *
     * <p>The capsule's openings are horizontal strips above and below the outline, and its
     * interior is the right-hand part of the body - the left part belongs to the clock.
     */
    private static void drawCapsuleCell(Canvas c, float stroke, TrioZones.Zone band,
                                        TrioAppearance.Capsule cap, int level,
                                        boolean quickCharging, int wifiLevel, String type,
                                        int fg, TrioAppearance a) {
        // A cell that draws nothing. The LEFT cell is deliberately not routed here:
        // it has its own centre and its own dispatcher, because it is the one cell
        // whose x is not the bands' shared centre.
        final int part = cap.parts[band.ordinal()];
        if (part == Prefs.PART_NONE) {
            return;
        }
        // The body cell's anchor is its slot's OUTER edge, because the right cell leans right:
        // its ink grows leftwards from the outline, so the edge is what stays put while the
        // reading gets larger, and the pair frames the interior from both sides. The openings
        // are full-width rows and stay centred on the capsule.
        final boolean opening = band == TrioZones.Zone.TOP || band == TrioZones.Zone.BOTTOM;
        final float cx;
        if (opening) {
            cx = TrioGeometry.CAP_INK_X + TrioGeometry.capInkW() * 0.5f;
        } else {
            // TEXT ends at this edge (right-aligned); an ICON is centred on it and would hang
            // half of itself outside the outline. Same split as the left cell, mirrored - see
            // TrioGeometry.capRightIconCx.
            cx = (part == Prefs.PART_BOLT || part == Prefs.PART_WIFI)
                    ? TrioGeometry.capRightIconCx(stroke, part) + a.capsuleRightDx
                    : TrioGeometry.capReadingEdge(stroke, false) + a.capsuleRightDx;
        }
        final float cy;
        switch (band) {
            case TOP:
                // Centred in the strip ABOVE the outline, not on its centreline: the line is
                // the capsule's edge, and a reading anchored to it loses half its ink outside
                // the shape - clipped by the host and painted over by the stroke. See
                // TrioGeometry.capOpeningCy.
                cy = TrioGeometry.capOpeningCy(true, stroke);
                break;
            case BOTTOM:
                cy = TrioGeometry.capOpeningCy(false, stroke);
                break;
            default:
                cy = TrioGeometry.capInteriorCy(stroke) + a.capsuleRightDy;
                break;
        }
        switch (part) {
            case Prefs.PART_BOLT:
                drawCapsuleBolt(c, stroke, quickCharging ? TrioGeometry.QUICK_CHARGE : fg, a, cx, cy);
                break;
            case Prefs.PART_WIFI:
                drawCapsuleWifi(c, stroke, wifiLevel, fg, a, cx, cy);
                break;
            // CENTRE on every branch: an opening is a full-width row, so it has no edge to
            // sit against. The right cell of the BODY is the one that leans right.
            case Prefs.PART_VALUE:
                drawCapsuleValue(c, stroke, level, fg, a, cx, cy, openingRoom(band, stroke),
                        opening ? SlotAlign.CENTRE : SlotAlign.RIGHT);
                break;
            case Prefs.PART_TYPE:
                drawCapsuleType(c, stroke, type, fg, a, cx, cy, openingRoom(band, stroke),
                        capsuleSlotWidth(band, openingRoom(band, stroke), stroke, false),
                        a.typeSize, opening ? SlotAlign.CENTRE : SlotAlign.RIGHT);
                break;
            case Prefs.PART_CLOCK:
                drawCapsuleTime(c, stroke, fg, a, cx, cy, openingRoom(band, stroke),
                        opening ? SlotAlign.CENTRE : SlotAlign.RIGHT);
                break;
            default:
                break;
        }
    }

    /**
     * The vertical room a cell gives its reading, or {@code -1} for "the interior's own".
     *
     * <p>An opening's strip is the free space beyond the outline, which is shorter than the
     * body the interior owns; telling the text fit which one applies is what stops a reading
     * in an opening from being sized for the body and then overrunning its band.
     */

    /**
     * The width a reading is fitted to: its row's in an opening, its slot's in the body.
     *
     * <p>Two different questions that used to share one answer. The body really is split into
     * slots - the clock and the percentage sit side by side and have to divide the interior -
     * but an opening is a full-width row, so giving it the right-hand slot's width fitted the
     * text to a third of the space it had.
     *
     * <p>Keyed off the height hint because that is how the cell already says which it is:
     * {@code -1} means "the interior's own", anything else is an opening's room.
     */
    private static float capsuleSlotWidth(TrioZones.Zone band, float heightHint, float stroke,
                                          boolean clock) {
        if (heightHint >= 0f) {
            // An opening is a full-width row. See TrioGeometry.capOpeningWidth.
            return TrioGeometry.capOpeningWidth();
        }
        // The whole track. The two body cells are independent components - each is as wide as
        // its own reading and hangs off its own edge - so neither is handed a share of the
        // interior that the other could shrink. See TrioGeometry.capReadingTrackWidth.
        return TrioGeometry.capReadingTrackWidth(stroke);
    }

    private static float openingRoom(TrioZones.Zone band, float stroke) {
        // An EM, not an ink height: everything downstream of this treats it as a font size.
        // See TrioGeometry.capOpeningTextEm.
        return band == TrioZones.Zone.CENTRE
                ? -1f
                : TrioGeometry.capOpeningTextEm(stroke);
    }

    /** {@code hint < 0} means "use the interior's own limit". */
    private static float effectiveHeightLimit(float hint, float stroke) {
        return hint < 0f ? TrioGeometry.capTextMaxSize(stroke) : hint;
    }

    /**
     * The stadium outline, with a gap cut at the top and bottom centre where the
     * dot rows cross it.
     *
     * <p>Two open subpaths, because that is what the shape is once the rows
     * interrupt it: top-centre-right 閳?top-right run 閳?right cap 閳?
     * bottom-right run 閳?bottom-centre-right, and its mirror. Each cap is two
     * quarter-circle cubics, using the usual circle-to-Bezier constant.
     *
     * <p>With {@code reach} at zero the two subpaths meet on the centre line and
     * the outline reads as continuous, which is the case when the rows are off.
     */
    /**
     * How far the outline is cut back at one opening: the half-width of whatever that opening
     * draws.
     *
     * <p>A dot row is fixed - four beads on their own pitch - but a reading is not: "73" and
     * "100" and "5GA" are three different widths, and a gap cut for the widest of them would
     * leave the line interrupted across half the capsule for the narrowest. So the reach is
     * measured per frame from the piece that band actually holds, and zero when it holds
     * nothing - in which case the outline closes, which is what an empty opening is.
     *
     * <p>This is the third term {@link TrioGeometry#capDotReach} documents: the stroke inks a
     * semicircle of radius {@code stroke / 2} past its cut end, so the clearance has to be
     * carried on top of the piece's own half-span.
     */
    private static float openingReach(TrioAppearance.Capsule cap, TrioZones.Zone band,
                                      float stroke, int level, String type, TrioAppearance a) {
        if (cap == null) {
            return 0f;
        }
        if (cap.dotsIn(band)) {
            return TrioGeometry.capDotReach(stroke);
        }
        // A cell that draws nothing. The LEFT cell is deliberately not routed here:
        // it has its own centre and its own dispatcher, because it is the one cell
        // whose x is not the bands' shared centre.
        final int part = cap.parts[band.ordinal()];
        if (part == Prefs.PART_NONE) {
            return 0f;
        }
        return TrioGeometry.capPieceReach(part, level, type, stroke,
                TrioGeometry.capOpeningTextRoomH(stroke))
                + TrioGeometry.capDotClearance() + stroke * 0.5f;
    }

    private static void buildCapsuleOutline(Path p, float topReach, float bottomReach) {
        final float left = TrioGeometry.CAP_LEFT;
        // The DRAWN width, not the reference one: this is the path the whole glyph is made of,
        // and the host is sized from capInkW() which follows it. Reading the constant here is
        // what made a lengthened capsule look stretched - the host grew and the outline did
        // not, so the drawing scale shrank and every part of the shape distorted with it.
        final float right = TrioGeometry.capRight();
        final float top = TrioGeometry.CAP_TOP;
        final float bottom = TrioGeometry.CAP_BOTTOM;
        final float r = TrioGeometry.CAP_RADIUS;
        final float cy = (top + bottom) * 0.5f;
        final float cx = (left + right) * 0.5f;
        final float k = 0.5522847498307936f * r;

        p.reset();
        p.moveTo(cx + topReach, top);
        p.lineTo(right - r, top);
        p.cubicTo(right - r + k, top, right, cy - k, right, cy);
        p.cubicTo(right, cy + k, right - r + k, bottom, right - r, bottom);
        p.lineTo(cx + bottomReach, bottom);
        p.moveTo(cx - bottomReach, bottom);
        p.lineTo(left + r, bottom);
        p.cubicTo(left + r - k, bottom, left, cy + k, left, cy);
        p.cubicTo(left, cy - k, left + r - k, top, left + r, top);
        p.lineTo(cx - topReach, top);
    }

    /**
     * The charged part of the outline, walked segment by segment.
     *
     * <p>Drawn as its own geometry rather than by dashing the track's path: a
     * dash pattern would depend on the platform measuring the path for us, and
     * the fill's end would land wherever that measurement said. Walking the
     * outline explicitly means the fraction drawn is the fraction asked for, and
     * it is the same code path on the device and in the offline harness.
     *
     * <p><b>Where it fills from.</b> The two dot rows divide the outline into
     * exactly two arcs - a left one and a right one - and the level is read off
     * them as two bars: full is entirely lit, and the charge is spent from the
     * <em>bottom of the right bar</em>, travelling counterclockwise. So the grey
     * starts as a stub at the foot of the right bar and eats its way up the right
     * cap, across the top, down the left cap and along the bottom, and what is
     * left lit is the run from the bottom gap's left edge clockwise round to its
     * right edge.
     *
     * <p>That is why the walk below runs <em>clockwise from the bottom gap's left
     * edge</em>: it visits the lit arc in the order the level grows through it, so
     * simply drawing the first {@code fraction} of it puts the fill where the
     * reading is taken. The reverse walk - counterclockwise from the top gap -
     * fills from the other end and was the earlier, wrong, behaviour: it spent the
     * charge from the top left, so a half-full battery lit the top and left while
     * the user was reading the level off the right bar.
     */
    private static void drawCapsuleFill(Canvas c, float stroke, float fraction,
                                        float topReach, float bottomReach) {
        final float left = TrioGeometry.CAP_LEFT;
        final float right = TrioGeometry.capRight();
        final float top = TrioGeometry.CAP_TOP;
        final float bottom = TrioGeometry.CAP_BOTTOM;
        final float r = TrioGeometry.CAP_RADIUS;
        final float cy = (top + bottom) * 0.5f;
        final float cx = (left + right) * 0.5f;
        // The same reach the track's outline was cut back by, from the same stroke:
        // the fill and the track have to agree about where the gap is, or the
        // charged run would stop somewhere other than where the outline resumes.
        final float topGap = topReach;
        final float bottomGap = bottomReach;

        // Four straight runs and two half-circle caps, in travel order. The gaps
        // are not segments: they carry no ink in either pass.
        final float topLeft = (cx - topReach) - (left + r);
        final float bottomLeft = (cx - bottomReach) - (left + r);
        final float bottomRight = (right - r) - (cx + bottomReach);
        final float topRight = (right - r) - (cx + topReach);
        final float capLen = (float) Math.PI * r;
        final float total = topLeft + capLen + bottomLeft + bottomRight + capLen + topRight;
        if (total <= 0f) {
            return;
        }
        float left0 = total * fraction;

        // The six pieces, clockwise from the bottom gap's left edge. Each starts
        // exactly where the previous one ended, so the fill has no seam; the two
        // gaps between the top and bottom runs are not pieces, because they carry
        // ink in neither pass and counting them would make the fraction wrong.
        //
        // Both caps sweep +180 here and not -180: the sweep has to follow the
        // direction of travel, or the fill would run the cap backwards and the two
        // ends would meet in the middle of it.
        left0 = run(c, left0, bottomLeft, cx - bottomReach, bottom, left + r, bottom);
        left0 = cap(c, left0, capLen, left + r, cy, r, 90f, 180f);
        left0 = run(c, left0, topLeft, left + r, top, cx - topReach, top);
        left0 = run(c, left0, topRight, cx + topReach, top, right - r, top);
        left0 = cap(c, left0, capLen, right - r, cy, r, 270f, 180f);
        run(c, left0, bottomRight, right - r, bottom, cx + bottomReach, bottom);
    }

    /**
     * Draws as much of one straight run as {@code remaining} allows, and answers
     * what is left.
     */
    private static float run(Canvas c, float remaining, float length,
                             float x1, float y1, float x2, float y2) {
        if (remaining <= 0f || length <= 0f) {
            return remaining;
        }
        if (remaining >= length) {
            c.drawLine(x1, y1, x2, y2, STROKE);
            return remaining - length;
        }
        final float t = remaining / length;
        c.drawLine(x1, y1, x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, STROKE);
        return 0f;
    }

    /**
     * The same for one half-circle cap.
     *
     * <p>{@code startDeg} is the angle the cap is entered at and {@code sweepDeg}
     * is signed, so a partial cap grows from the end the walk arrives at rather
     * than from the middle - which is what keeps the fill's edge moving smoothly
     * across the cap instead of jumping when it crosses one.
     */
    private static float cap(Canvas c, float remaining, float length,
                             float cx, float cy, float r, float startDeg, float sweepDeg) {
        if (remaining <= 0f || length <= 0f) {
            return remaining;
        }
        ARC.set(cx - r, cy - r, cx + r, cy + r);
        if (remaining >= length) {
            c.drawArc(ARC, startDeg, sweepDeg, false, STROKE);
            return remaining - length;
        }
        c.drawArc(ARC, startDeg, sweepDeg * (remaining / length), false, STROKE);
        return 0f;
    }

    /**
     * The two four-dot rows, riding the outline's own centreline so a dot sits
     * exactly in the gap it interrupts.
     *
     * <p>Unlit steps are drawn too, at {@code trackAlpha}: the outline has a gap
     * there, and leaving it empty would read as a break in the line rather than
     * as an empty step.
     */
    /**
     * The two openings' dot rows, each drawn only if its band holds one.
     *
     * <p>The two rows used to be drawn together, gated by the mobile switch alone. They are
     * separate placements now - {@link TrioAppearance.Capsule#dotsIn} - so a capsule can draw
     * SIM 1 above and nothing below, which is what a user who removed the bottom row asked
     * for. The <b>outline's</b> gap is still cut at both openings while any row exists (see
     * {@code buildCapsuleOutline}'s reach), because a stroke interrupted on one side only
     * reads as damage rather than as a row between two segments.
     */
    private static void drawCapsuleDots(Canvas c, int level, int[] slotLevels, boolean dual,
                                        float stroke, int fg, TrioAppearance a,
                                        TrioAppearance.Capsule cap) {
        final boolean topRow = cap == null || cap.dotsIn(TrioZones.Zone.TOP);
        final boolean bottomRow = cap == null || cap.dotsIn(TrioZones.Zone.BOTTOM);
        final int top = (dual && slotLevels != null) ? slotLevels[0] : level;
        final int bottom = (dual && slotLevels != null) ? slotLevels[1] : level;
        final int inactive = withAlpha(fg, a.trackAlpha);
        final int topActive = top < 0 ? 0 : Math.min(TrioGeometry.RECT_DOT_ROWS, top);
        final int bottomActive = bottom < 0 ? 0 : Math.min(TrioGeometry.RECT_DOT_ROWS, bottom);
        final float topY = TrioGeometry.capDotTopY();
        final float bottomY = TrioGeometry.capDotBottomY();
        final float first = TrioGeometry.capDotCx() - TrioGeometry.CAP_DOT_PITCH * 1.5f;
        FILL.setStyle(Paint.Style.FILL);

        for (int i = 0; i < TrioGeometry.RECT_DOT_ROWS; i++) {
            final float x = first + i * TrioGeometry.CAP_DOT_PITCH;
            if (topRow) {
                FILL.setColor(i < topActive ? fg : inactive);
                c.drawCircle(x, topY, TrioGeometry.CAP_DOT_R, FILL);
            }
            if (bottomRow) {
                FILL.setColor(i < bottomActive ? fg : inactive);
                c.drawCircle(x, bottomY, TrioGeometry.CAP_DOT_R, FILL);
            }
        }
    }

    /** The time of day, in a capsule cell centred on {@code (cx, cy)}. */
    private static void drawCapsuleTime(Canvas c, float stroke, int fg, TrioAppearance a,
                                        float cx, float cy, float heightHint,
                                        SlotAlign align) {
        final String text = TrioState.sClockText;
        if (text == null || text.isEmpty()) {
            return;
        }
        // The clock's own size and weight, not the value's: the two readings sit in
        // two slots and are tuned independently, so moving one must not move the
        // other. Both default to the value's, so an untouched install looks the same.
        applyWeight(a.clockWeight);
        // And its own slot width, which is the wider of the two: "19:41" is half
        // again as long as "100" at the same size, so an even split capped the clock
        // at a setting of about 36 whatever the slider said.
        final float size = capsuleTextSize(text, a.clockSize, stroke,
                capsuleSlotWidth(TrioZones.Zone.CENTRE, heightHint, stroke, true),
                100, effectiveHeightLimit(heightHint, stroke));
        TEXT.setTextSize(size);
        TEXT.setColor(fg);
        alignFor(align);
        c.drawText(text, cx, cy + (size * TrioGeometry.CAP_HEIGHT_RATIO) * 0.5f, TEXT);
    }

    /** The battery percentage, when a capsule cell carries it. */
    private static void drawCapsuleValue(Canvas c, float stroke, int level, int fg,
                                         TrioAppearance a, float cx, float cy,
                                         float heightHint, SlotAlign align) {
        final String text = String.valueOf(level);
        applyWeight(a.valueWeight);
        final float size = capsuleTextSize(text, a.valueSize, stroke,
                capsuleSlotWidth(TrioZones.Zone.CENTRE, heightHint, stroke, false),
                100, effectiveHeightLimit(heightHint, stroke));
        TEXT.setTextSize(size);
        TEXT.setColor(fg);
        alignFor(align);
        c.drawText(text, cx, cy + (size * TrioGeometry.CAP_HEIGHT_RATIO) * 0.5f, TEXT);
    }

    /** The network type, when a capsule cell carries it. */
    private static void drawCapsuleType(Canvas c, float stroke, String type, int fg,
                                        TrioAppearance a, float cx, float cy,
                                        float heightHint, float widthLimit, int sizeSetting,
                                        SlotAlign align) {
        applyWeight(a.typeWeight);
        // Fitted to the width of the cell it is actually in. An opening spans the
        // capsule, a body cell does not, and handing both the opening's width let a
        // reading in the left half lay itself out across the whole body.
        final float size = capsuleTextSize(type, sizeSetting, stroke,
                widthLimit, a.typeSuffixScale,
                effectiveHeightLimit(heightHint, stroke));
        drawType(c, type, cx, cy + (size * TrioGeometry.CAP_HEIGHT_RATIO) * 0.5f,
                size, fg, a, align);
    }

    /**
     * The one place a capsule reading's size is decided, so the time and the
     * percentage cannot disagree about how big they are.
     *
     * <p>Three bounds, in the order they bind: the height the capsule leaves for
     * text, the width of the slot the reading sits in, and the user's own size
     * setting. The other two arrangements need only the second and third - they
     * are tall enough that the width always binds first - so this is the one
     * place the height has to be stated.
     *
     * <p>The slot width is passed in rather than derived here, because the two
     * slots are no longer the same size: the time's is the wider one, since its
     * string is the longer. Taking a shared width - which is what this did while
     * the interior was split evenly - silently capped the clock at whatever fitted
     * half the capsule.
     */

    /**
     * The type size for a cell inside the capsule body.
     *
     * <p>DEF_TYPE_SIZE is deliberately 0.9 of DEF_VALUE_SIZE: the type is a standalone
     * label when it sits in the ring, and the smaller size is what that context wants.
     * A body cell differs: the label stands beside the percentage at the same height,
     * so an untouched setting is scaled up to the reading beside it. A user who moved
     * the type slider keeps exactly what they chose.
     */
    private static int bodyTypeSize(TrioAppearance a) {
        return a.typeSize == Prefs.DEF_TYPE_SIZE ? Prefs.DEF_VALUE_SIZE : a.typeSize;
    }

    private static float capsuleTextSize(String text, int setting, float stroke,
                                         float slotWidth, int suffixPercent) {
        return capsuleTextSize(text, setting, stroke, slotWidth, suffixPercent,
                TrioGeometry.capTextMaxSize(stroke));
    }

    /**
     * The same, with the vertical room stated by the caller instead of taken from the interior.
     *
     * <p>An opening's strip is shorter than the body, so a piece placed there has to be fitted
     * to that strip rather than to the interior's height. Passing the interior's limit - which
     * is what both opening cells used to do - sized the reading for the middle of the capsule
     * and then drew it out in a band half that tall, so it overran the outline on both sides.
     *
     * @param heightLimit the tallest the line's ink may be, in design units
     */
    private static float capsuleTextSize(String text, int setting, float stroke,
                                         float slotWidth, int suffixPercent,
                                         float heightLimit) {
        final float requested = Math.min(setting * TrioGeometry.CAP_TEXT_SCALE, heightLimit);
        return fitSize(text, requested, slotWidth, suffixPercent);
    }

    /** The Wi-Fi group moved and scaled into a capsule cell centred on {@code (cx, cy)}. */
    private static void drawCapsuleWifi(Canvas c, float stroke, int level, int fg,
                                        TrioAppearance a, float cx, float cy) {
        // The user's size multiplies the fit, and the placement below is recomputed from the
        // product: offset = centre - reference * scale only stays centred while the scale it
        // was derived from is the scale being drawn.
        final float scale = TrioGeometry.capWifiScale(stroke) * a.wifiScaleFactor();
        final int save = c.save();
        // The caller hands over the CENTRE this cell wants, so the group is placed by
        // subtracting its own ink centre - not by rewriting the right-hand slot's offset.
        // The old expression anchored on capRightCx and then shifted by (cx - capRightCx),
        // which cancels out but only while cx is a CENTRE; once the left cell started
        // passing its own edge it put the arcs half a glyph off.
        c.translate(cx - TrioGeometry.W_CX * scale,
                cy - TrioGeometry.W_INK_CY * scale);
        c.scale(scale, scale);
        drawWifiArcs(c, level, fg, a);
        c.restoreToCount(save);
    }

    /**
     * The charging bolt, in a capsule cell centred on {@code (cx, cy)}.
     *
     * <p>The cell's own fit is the base; the user's size multiplies inside that room. The
     * offsets are recomputed from the resulting scale rather than added to the existing
     * pair, because {@code offset = centre - reference * scale} only keeps the ink centred
     * while the scale it was derived from is the scale being drawn.
     */
    private static void drawCapsuleBolt(Canvas c, float stroke, int color, TrioAppearance a,
                                        float cx, float cy) {
        final float factor = a.boltScaleFactor();
        final float scale = TrioGeometry.capBoltScale(stroke) * factor;
        final int save = c.save();
        c.translate(cx - TrioGeometry.BOLT_CX_REF * scale,
                cy - TrioGeometry.BOLT_INK_CY * scale);
        c.scale(scale, scale);
        FILL.setStyle(Paint.Style.FILL);
        FILL.setColor(color);
        c.drawPath(BOLT, FILL);
        c.restoreToCount(save);
    }

    // ------------------------------------------------------ rectangular pieces

    /**
     * The two mirrored dot columns. Both carry the same level: MIUI's own meter
     * is one column, and drawing two of them is what makes the arrangement
     * symmetrical around the text rather than a list of items on one side.
     *
     * <p>In the dual-SIM reading that symmetry becomes the point: the columns are
     * already two, so the left one reports SIM 1 and the right one SIM 2 - each
     * column being one SIM's four steps, the same shape MIUI's own meter has.
     *
     * @param slotLevels per-SIM levels, or {@code null} to repeat {@code level}
     */
    private static void drawRectDots(Canvas c, int level, int[] slotLevels, boolean dual,
                                     int fg, TrioAppearance a) {
        final int left = (dual && slotLevels != null) ? slotLevels[0] : level;
        final int right = (dual && slotLevels != null) ? slotLevels[1] : level;
        final int inactive = withAlpha(fg, a.trackAlpha);
        final int leftActive = left < 0 ? 0 : Math.min(TrioGeometry.RECT_DOT_ROWS, left);
        final int rightActive = right < 0 ? 0 : Math.min(TrioGeometry.RECT_DOT_ROWS, right);
        for (int i = 0; i < TrioGeometry.RECT_DOT_ROWS; i++) {
            final float y = TrioGeometry.RECT_DOT_TOP + i * TrioGeometry.RECT_DOT_STEP;
            FILL.setColor(i < leftActive ? fg : inactive);
            c.drawCircle(TrioGeometry.RECT_DOT_LX, y, TrioGeometry.RECT_DOT_R, FILL);
            FILL.setColor(i < rightActive ? fg : inactive);
            c.drawCircle(TrioGeometry.RECT_DOT_RX, y, TrioGeometry.RECT_DOT_R, FILL);
        }
    }

    /**
     * The battery bar: the track as one capsule across the full width, then the
     * lit part on top of it.
     *
     * <p>The lit part is rounded on the left and square on the right, so its head
     * follows the track's cap while its tail stays a vertical edge wherever the
     * level happens to fall - a capsule at both ends would read as a pill that
     * shrinks, not as a bar that fills. The radii are clamped by the platform when
     * the segment is shorter than the corner, so a low level cannot draw a knot.
     *
     * <p>The thickness still comes from {@code ringStroke}: it is the one stroke
     * setting, and the settings screen relabels that row "bar thickness" for this
     * arrangement. A second key would only add a row that is inert in whichever
     * arrangement the user is not looking at.
     */
    private static void drawRectBatteryBar(Canvas c, int level, int role, int fg,
                                          TrioAppearance a) {
        final float left = TrioGeometry.RECT_BAR_LEFT;
        final float right = TrioGeometry.RECT_BAR_RIGHT;
        final float r = a.ringStroke * 0.5f;
        final float top = TrioGeometry.RECT_BAR_CY - r;
        final float bottom = TrioGeometry.RECT_BAR_CY + r;

        FILL.setColor(withAlpha(fg, a.trackAlpha));
        RECT_RECT.set(left, top, right, bottom);
        c.drawRoundRect(RECT_RECT, r, r, FILL);

        if (level <= 0) {
            return;
        }
        final float cut = left + (right - left) * clamp01(level / 100f);
        RECT_RECT.set(left, top, cut, bottom);
        RECT_RADII[0] = r;
        RECT_RADII[1] = r;
        RECT_RADII[2] = 0f;
        RECT_RADII[3] = 0f;
        RECT_RADII[4] = 0f;
        RECT_RADII[5] = 0f;
        RECT_RADII[6] = r;
        RECT_RADII[7] = r;
        RECT_BAR.reset();
        RECT_BAR.addRoundRect(RECT_RECT, RECT_RADII, Path.Direction.CW);
        FILL.setColor(role);
        c.drawPath(RECT_BAR, FILL);
    }

    /** The Wi-Fi group moved into the top slot; the arcs themselves are unchanged. */
    private static void drawRectWifi(Canvas c, int level, int fg, TrioAppearance a) {
        final int save = c.save();
        c.translate(TrioGeometry.rectWifiOffsetX(), TrioGeometry.rectWifiOffsetY(a.arcStroke));
        final float rectScale = TrioGeometry.RECT_WIFI_SCALE * a.wifiScaleFactor();
        c.scale(rectScale, rectScale);
        drawWifiArcs(c, level, fg, a);
        c.restoreToCount(save);
    }

    private static void drawRectBolt(Canvas c, int color, TrioAppearance a) {
        final int save = c.save();
        final float factor = a.boltScaleFactor();
        c.translate(TrioGeometry.rectBoltOffsetX(factor), TrioGeometry.rectBoltOffsetY(factor));
        c.scale(TrioGeometry.BOLT_SCALE * factor, TrioGeometry.BOLT_SCALE * factor);
        FILL.setColor(color);
        c.drawPath(BOLT, FILL);
        c.restoreToCount(save);
    }

    /**
     * The network type in the top slot, between the dot columns.
     *
     * <p>Sized against {@link TrioGeometry#RECT_TYPE_SCALE} like the percentage
     * is against its own, so the two read as a pair, and shrunk by
     * {@link #fitSize} if the slot is too narrow for it.
     */
    private static void drawRectType(Canvas c, String type, int fg, TrioAppearance a) {
        final float requested = a.typeSize * TrioGeometry.RECT_TYPE_SCALE;
        applyWeight(a.typeWeight);
        final float size = fitSize(type, requested, TrioGeometry.RECT_TEXT_CLEAR, a.typeSuffixScale);
        drawType(c, type, TrioGeometry.RECT_VALUE_X, TrioGeometry.RECT_TYPE_BASELINE, size, fg, a);
    }

    /** The percentage, in the middle of the arrangement and at its own scale. */
    private static void drawRectValue(Canvas c, int level, int fg, TrioAppearance a) {
        final String text = String.valueOf(level);
        final float requested = a.valueSize * TrioGeometry.RECT_VALUE_SCALE;
        applyWeight(a.valueWeight);
        final float size = fitSize(text, requested, TrioGeometry.RECT_TEXT_CLEAR);
        TEXT.setTextSize(size);
        TEXT.setColor(fg);
        c.drawText(text, TrioGeometry.RECT_VALUE_X, TrioGeometry.RECT_VALUE_BASELINE, TEXT);
    }

    /**
     * The colour of the lit part of the ring and the value text.
     *
     * <p>Priority: critical battery, then power-save/low battery, then charging,
     * then the framework's own foreground. {@code isDarkBackground(fg)} tells
     * which variant of a role colour to use: a light icon colour means the status
     * bar is dark, so the light-background variant applies.
     */
    static int roleColor(TrioAppearance a, int level, boolean charging,
                         boolean powerSave, boolean low, int fg) {
        if (a == null || !a.roleColors) {
            return fg;
        }
        final boolean dark = isDarkBackground(fg);
        if (level >= 0 && level < TrioGeometry.CRITICAL_LEVEL) {
            return dark ? a.criticalOnDark : a.criticalOnLight;
        }
        if (powerSave || low || (level >= 0 && level <= a.lowThreshold)) {
            return dark ? a.lowOnDark : a.lowOnLight;
        }
        if (charging) {
            return dark ? a.chargingOnDark : a.chargingOnLight;
        }
        return fg;
    }

    // ----------------------------------------------------------------- battery

    private static void drawBattery(Canvas c, int level, int role, int fg,
                                    TrioAppearance a, float gapStart, float gapEnd,
                                    float sweep, float start) {
        STROKE.setStrokeWidth(a.ringStroke);

        STROKE.setColor(withAlpha(fg, a.trackAlpha));
        batteryRing(c, 0f, 1f, gapStart, gapEnd, sweep, start);

        if (level > 0) {
            STROKE.setColor(role);
            batteryRing(c, 0f, Math.min(1f, level / 100f), gapStart, gapEnd, sweep, start);
        }
    }

    /**
     * Draws the open battery arc from progress {@code from} to {@code to},
     * skipping the segment hidden behind the top gap.
     *
     * <p>Both ends are progress of the arc that is <em>actually drawn</em>, not
     * of the full path: the two segments the mouth leaves behind divide the
     * level between them, so 50% ends exactly where the left segment does and
     * the mouth never consumes any of it. Measuring over the whole path instead
     * made the level stand still across the entire width of the mouth - the
     * right half did not start filling until the level had climbed past 67%.
     *
     * <p>{@code sweep}/{@code start} are passed in rather than read from
     * {@link TrioGeometry}, because the arc's angular span depends on whether anything
     * occupies the bottom of the ring this frame - see {@code bottomOpen}.
     */
    private static void batteryRing(Canvas c, float from, float to, float gapStart, float gapEnd,
                                    float sweep, float start) {
        from = clamp01(from);
        to = clamp01(to);
        if (to <= from) {
            return;
        }
        final float mouthStart = clamp01(gapStart);
        final float mouthEnd = clamp01(gapEnd);
        // Path length that actually carries ink; the level is spread over it.
        final float drawn = mouthStart + (1f - mouthEnd);
        if (drawn <= 0f) {
            return;
        }
        final float visibleFrom = from * drawn;
        final float visibleTo = to * drawn;
        // First segment: visible progress [0, mouthStart] is path [0, mouthStart].
        final float firstEnd = Math.min(visibleTo, mouthStart);
        if (firstEnd > visibleFrom) {
            arcByProgress(c, visibleFrom, firstEnd, TrioGeometry.B_CX, TrioGeometry.B_CY,
                    TrioGeometry.B_R, sweep, start);
        }
        // Second segment: visible progress (mouthStart, drawn] is path (mouthEnd, 1].
        final float secondFrom = Math.max(visibleFrom, mouthStart);
        if (visibleTo > secondFrom) {
            arcByProgress(c, mouthEnd + secondFrom - mouthStart, mouthEnd + visibleTo - mouthStart,
                    TrioGeometry.B_CX, TrioGeometry.B_CY, TrioGeometry.B_R, sweep, start);
        }
    }

    private static void arcByProgress(Canvas c, float from, float to, float cx, float cy, float r,
                                      float sweep, float start) {
        arc(c, cx, cy, r, start + from * sweep, (to - from) * sweep);
    }

    private static void arc(Canvas c, float cx, float cy, float r, float startDeg, float sweepDeg) {
        ARC.set(cx - r, cy - r, cx + r, cy + r);
        c.drawArc(ARC, startDeg, sweepDeg, false, STROKE);
    }

    // -------------------------------------------------------------------- wifi


    /**
     * The arcs and the dot at their reference coordinates, with no transform of
     * their own: each caller positions the group first. Both arrangements draw
     * the identical group, which is what keeps the Wi-Fi icon the same in both.
     */
    private static void drawWifiArcs(Canvas c, int level, int fg, TrioAppearance a) {
        STROKE.setStrokeWidth(a.arcStroke);
        STROKE.setColor(fg);
        if (level >= 3) {
            arc(c, TrioGeometry.W_CX, TrioGeometry.W1_CY, TrioGeometry.W1_R,
                    TrioGeometry.W1_START, TrioGeometry.W1_SWEEP);
            arc(c, TrioGeometry.W_CX, TrioGeometry.W2_CY, TrioGeometry.W2_R,
                    TrioGeometry.W2_START, TrioGeometry.W2_SWEEP);
        } else if (level == 2) {
            arc(c, TrioGeometry.W_CX, TrioGeometry.W2_CY, TrioGeometry.W2_R,
                    TrioGeometry.W2_START, TrioGeometry.W2_SWEEP);
        }
        if (level >= 1) {
            FILL.setColor(fg);
            c.drawPath(WIFI_DOT, FILL);
        }
    }

    // ------------------------------------------------------------- level dots


    // ------------------------------------------------- out-of-ring signal

    /**
     * Scratch for one capsule bar; rewritten every draw, never kept.
     *
     * <p>The out-of-ring reading is painted on a view of its own rather than
     * inside the glyph's {@code onDraw}, but never reentrantly: it is one view
     * per battery container, and a container paints its children one at a time.
     */
    private static final RectF OUT_BAR = new RectF();

    /**
     * Resolves the two readings the out-of-ring glyph draws: the capsule row, and
     * the dot row beneath it.
     *
     * <p>Row one is the first SIM that answered and row two the second, which is
     * also why the dot row disappears when only one SIM answered. Four ascending
     * bars is what a lone reading looks like, so a single SIM must not be split
     * across two rows just to fill the space.
     *
     * <p>{@code dataSimOnly} collapses the pair the other way: the reading that
     * matters is the data SIM's, and the other one is dropped whether or not it
     * answered. The data SIM keeps the bars, because that is the shape a lone
     * reading has.
     *
     * <p>When telephony never answered, the aggregate reading the glyph itself
     * uses is the last resort, so a missing subscription list does not leave the
     * out-of-ring glyph blank while the in-ring one still reads a level.
     *
     * @param out receives {@code {barLevel, dotLevel}}; a dot level of {@code -1}
     *            means "no second row", and a bar level of {@code -1} means
     *            "nothing to read"
     */
    static void outSignalLevels(boolean dataSimOnly, int mobileLevel, int[] slotLevels,
                               int dataSlot, int[] out) {
        int slot0 = -1;
        int slot1 = -1;
        if (slotLevels != null && slotLevels.length >= TrioState.SIM_SLOTS) {
            slot0 = slotLevels[0];
            slot1 = slotLevels[1];
        }
        final int firstAnswered = slot0 >= 0 ? slot0 : slot1;
        int bars;
        int dots = -1;
        if (dataSimOnly) {
            bars = firstAnswered;
            if (dataSlot == 0 && slot0 >= 0) {
                bars = slot0;
            } else if (dataSlot == 1 && slot1 >= 0) {
                bars = slot1;
            }
        } else if (slot0 >= 0 && slot1 >= 0) {
            bars = slot0;
            dots = slot1;
        } else {
            bars = firstAnswered;
        }
        if (bars < 0) {
            bars = mobileLevel;
        }
        out[0] = bars;
        out[1] = dots;
    }

    /**
     * How much of the reference box the drawing actually fills, with or without a
     * dot row. Used to centre the ink inside a box that is always
     * {@link TrioGeometry#STACK_INK_H} tall.
     *
     * <p>Deliberately no longer what the scale is divided by. It used to be, and
     * that made a reading without a dot row divide its pixels by a box 209/150
     * times shorter - so the very same four bars came out 1.39x taller beside no
     * dot row than beside one, and the single-SIM reading towered over the
     * dual-SIM one. The box is the reading's frame, not its content.
     */
    static float outSignalInkH(boolean dots) {
        return dots ? TrioGeometry.STACK_INK_H
                : TrioGeometry.STACK_BAR_H[TrioGeometry.STACK_COLUMNS - 1];
    }

    /**
     * The pixel height of the reading's box at the user's {@code sizeDp}, on a
     * display of the given {@code density}.
     *
     * <p>Deliberately independent of any view: the reading's size used to be a
     * share of the row it stood in, so MIUI laying that row out taller - which
     * is exactly what pulling the control centre down does, 88px to 134px on the
     * test device - made the reading grow with it. A dp is fixed.
     *
     * <p>One box for both shapes: a reading with a dot row and one without are
     * given the same box, so switching between them - a second SIM answering, or
     * the data-SIM-only switch - resizes nothing.
     */
    static int outSignalHeight(int sizeDp, float density) {
        if (sizeDp <= 0 || density <= 0f) {
            return 0;
        }
        return Math.max(1, Math.round(sizeDp * density));
    }

    /**
     * The pixel width that gives a box {@code height} pixels tall the reference
     * aspect, dot row or not.
     */
    static int outSignalWidth(int height) {
        if (height <= 0) {
            return 0;
        }
        return Math.max(1,
                Math.round(height * TrioGeometry.STACK_INK_W / TrioGeometry.STACK_INK_H));
    }

    /**
     * Paints the out-of-ring reading into a canvas of its own, in that canvas's
     * pixel space.
     *
     * <p>Laid out from the reference image's own units rather than from the
     * glyph's 120x120 design space: this reading is not inside the ring, and
     * scaling it by the ink box above would tie a status bar height to a number
     * that describes a battery. The reference units are uniform in both axes, so
     * one scale factor keeps the bar steps, the pitch and the round caps in the
     * proportions the reference draws.
     *
     * <p>A reading below four lights its remaining bars in the track colour
     * instead of drawing a partial row: the row length is the reading, and the
     * unlit part of it is what says how much is missing.
     *
     * @return whether anything was painted
     */
    static boolean drawOutSignal(Canvas c, int width, int height, int bars, int dots,
                                 int fg, TrioAppearance a) {
        if (bars < 0 || width <= 0 || height <= 0) {
            return false;
        }
        final boolean dotRow = dots >= 0;
        final float inkW = TrioGeometry.STACK_INK_W;
        // The scale is always taken from the full reference box, never from the
        // shorter ink a reading without a dot row actually fills: that shorter
        // box is only what centres the drawing here, and dividing the pixels by
        // it would make the same bars a third taller whenever the dot row is
        // absent.
        final float inkH = outSignalInkH(dotRow);
        final float scale = Math.min(width / inkW, height / TrioGeometry.STACK_INK_H);
        if (scale <= 0f) {
            return false;
        }
        final int columns = TrioGeometry.STACK_COLUMNS;
        final float x0 = (width - inkW * scale) / 2f;
        // One baseline for all four bars: the steps are in the tops, which is
        // what makes the row read as a level rather than as four separate bars.
        final float baseline = (height - inkH * scale) / 2f
                + TrioGeometry.STACK_BAR_H[columns - 1] * scale;
        final float barW = TrioGeometry.STACK_BAR_W * scale;
        final float radius = barW / 2f;
        final float dotR = TrioGeometry.STACK_DOT_D * scale / 2f;
        final float dotCy = baseline
                + (TrioGeometry.STACK_DOT_GAP + TrioGeometry.STACK_DOT_D / 2f) * scale;
        final int inactive = withAlpha(fg, a.trackAlpha);
        for (int i = 0; i < columns; i++) {
            final float left = x0 + i * TrioGeometry.STACK_PITCH * scale;
            FILL.setColor(i < bars ? fg : inactive);
            OUT_BAR.set(left, baseline - TrioGeometry.STACK_BAR_H[i] * scale,
                    left + barW, baseline);
            c.drawRoundRect(OUT_BAR, radius, radius, FILL);
            if (dotRow) {
                FILL.setColor(i < dots ? fg : inactive);
                c.drawCircle(left + radius, dotCy, dotR, FILL);
            }
        }
        return true;
    }

    // ------------------------------------------------------------ top gap fill

    /**
     * Installs {@code weight} on {@link #TEXT}, skipping the work when that
     * weight is already the installed one. Every draw runs inside onDraw, so the
     * cached comparison keeps a per-frame {@code Typeface} allocation off the hot
     * path. The percentage and the network type share this cache because they
     * share the paint; when they differ both pay one swap per frame, which
     * {@link Typeface#create(Typeface, int, boolean)} answers from its own cache.
     *
     * <p>The font generation is part of the cache key. Without it a weight that
     * did not change would skip the swap even though the face behind it did, and a
     * newly picked font would not appear until something else happened to move
     * the weight.
     */
    private static void applyWeight(int weight) {
        if (weight == sTextWeight && sTextGeneration == sFontGeneration) {
            return;
        }
        TEXT.setTypeface(typefaceFor(weight));
        // Always set, never only when true: a leftover synthetic embolden from a
        // heavier weight would otherwise thicken a face that should be plain.
        TEXT.setFakeBoldText(fakeBoldFor(weight));
        sTextWeight = weight;
        sTextGeneration = sFontGeneration;
    }




    /**
     * Draws the network type at {@code x} on {@code baseline}, shrinking the
     * trailing "A" when the user asked for it.
     *
     * <p>{@code Canvas.drawText(CharSequence, ...)} does <em>not</em> apply
     * spans - only a {@code TextPaint} through {@code StaticLayout} does - so
     * the two sizes are drawn as two runs. The {@link Paint} is left aligned for
     * the duration: {@link #TEXT} is centred, and centring each run separately
     * would stack them on the same spot instead of setting them side by side.
     *
     * <p>The suffix shares the main run's baseline, which is what puts its foot
     * on the same line as the "5G" rather than dropping it into the ring.
     *
     * @param x the centre of the whole label: the single-run path passes it
     *          straight to a centred {@link Paint}, and the two-run path uses it
     *          to centre the block it composes
     */
    /**
     * Sets the shared paint's alignment for one cell's reading.
     *
     * <p>An explicit answer from the caller, never inferred from the coordinate. It WAS
     * inferred, by testing whether the anchor passed in was still the capsule's inner
     * edge - and that test broke the moment the left cell could be nudged: the nudge is
     * added to the anchor before the draw, so dx = 0 read as "left-aligned" while dx = 1
     * read as "centred". Half a label sits between those two answers, so the cell snapped
     * sideways by half its own width as soon as it was nudged off zero, and only then
     * began moving a unit at a time. The alignment is a property of WHICH CELL is drawn,
     * which the caller already knows, so it is passed in rather than guessed.
     */
    private static void alignFor(SlotAlign align) {
        TEXT.setTextAlign(align.paint());
    }

    /**
     * How a capsule reading sits in the space it was given.
     *
     * <p>Three answers, not two, and the third is the point: the capsule's left cell reads
     * from the shape's inner edge and its right cell reads TO it, so the pair frames the
     * interior the way a line of text frames a column. The right cell used to be CENTRED in
     * its own slot, which left a gap of whatever the slot had spare between the reading and
     * the outline - so widening the slot moved the number's left edge instead of its right
     * one, and the pair stopped looking like two columns against the two ends.
     *
     * <p>An opening is a full-width row and stays centred: it spans the whole capsule, so
     * there is no edge for it to sit against.
     */
    enum SlotAlign {
        /** Against the interior's left edge: the capsule's left cell. */
        LEFT(Paint.Align.LEFT),
        /** Centred in its row: an opening, and every arrangement but the capsule. */
        CENTRE(Paint.Align.CENTER),
        /** Against the interior's right edge: the capsule's right cell. */
        RIGHT(Paint.Align.RIGHT);

        final Paint.Align paint;

        SlotAlign(Paint.Align paint) {
            this.paint = paint;
        }

        Paint.Align paint() {
            return paint;
        }

        /** The old boolean's two cases, for the callers that only knew centred or not. */
        static SlotAlign of(boolean startAligned) {
            return startAligned ? LEFT : CENTRE;
        }
    }

    /** Centred label, the form every arrangement but the capsule's left cell wants. */
    private static void drawType(Canvas c, String type, float x, float baseline,
                                 float size, int fg, TrioAppearance a) {
        drawType(c, type, x, baseline, size, fg, a, SlotAlign.CENTRE);
    }

    private static void drawType(Canvas c, String type, float x, float baseline,
                                 float size, int fg, TrioAppearance a,
                                 SlotAlign align) {
        if (!TrioGeometry.hasShrunkSuffix(type, a.typeSuffixScale)) {
            TEXT.setTextAlign(align.paint());
            TEXT.setTextSize(size);
            TEXT.setColor(fg);
            c.drawText(type, x, baseline, TEXT);
            return;
        }
        final String base = TrioGeometry.typeBase(type);
        final String suffix = String.valueOf(TrioGeometry.TYPE_SUFFIX);
        final float suffixSize = size * a.typeSuffixScale / 100f;
        // Measure both runs, then place them as one block. The block is what the alignment
        // applies to - its start, its centre or its end - because the shrunk suffix is part
        // of the label, not a second thing beside it.
        TEXT.setTextAlign(Paint.Align.LEFT);
        TEXT.setColor(fg);
        TEXT.setTextSize(size);
        final float baseWidth = TEXT.measureText(base);
        TEXT.setTextSize(suffixSize);
        final float suffixWidth = TEXT.measureText(suffix);
        final float blockWidth = baseWidth + suffixWidth;
        final float left;
        switch (align) {
            case LEFT:
                left = x;
                break;
            case RIGHT:
                left = x - blockWidth;
                break;
            default:
                left = x - blockWidth * 0.5f;
                break;
        }
        TEXT.setTextSize(size);
        c.drawText(base, left, baseline, TEXT);
        float cursor = left + baseWidth;
        TEXT.setTextSize(suffixSize);
        c.drawText(suffix, cursor, baseline, TEXT);
        // Restore the shared paint's centred default: every other text draw in
        // this renderer relies on it, and this method is not the last per frame.
        TEXT.setTextAlign(Paint.Align.CENTER);
    }

    /**
     * Largest font size at which {@code text} fits in {@code clearWidth} design
     * units, capped by {@code requested}. "100" is far wider than "9", so a
     * long value shrinks instead of overrunning the ring.
     */
    private static float fitSize(String text, float requested, float clearWidth) {
        return fitSize(text, requested, clearWidth, 100);
    }

    /**
     * The same fit, measuring the label the way {@link #drawType} will lay it
     * out: a shrunk trailing "A" carries only {@code scalePercent} of the main
     * size, so it takes less room and the main run is left correspondingly
     * larger. Measuring the whole string at {@code requested} would shrink a
     * label that in fact fits.
     */
    private static float fitSize(String text, float requested, float clearWidth,
                                 int scalePercent) {
        if (clearWidth <= 0f) {
            return requested;
        }
        TEXT.setTextSize(requested);
        final float width = measureType(text, requested, scalePercent);
        if (width <= clearWidth || width <= 0f) {
            return requested;
        }
        return requested * (clearWidth / width);
    }

    /**
     * Width of {@code text} at {@code size}, with the trailing suffix scaled.
     *
     * <p>Leaves {@link #TEXT} at {@code size}, which is what every caller wants
     * next: {@link #fitSize} returns and the draw path re-sets the size anyway.
     */
    private static float measureType(String text, float size, int scalePercent) {
        if (!TrioGeometry.hasShrunkSuffix(text, scalePercent)) {
            TEXT.setTextSize(size);
            return TEXT.measureText(text);
        }
        TEXT.setTextSize(size);
        final float base = TEXT.measureText(TrioGeometry.typeBase(text));
        TEXT.setTextSize(size * scalePercent / 100f);
        final float suffix = TEXT.measureText(String.valueOf(TrioGeometry.TYPE_SUFFIX));
        TEXT.setTextSize(size);
        return base + suffix;
    }

    // ----------------------------------------------------------------- helpers

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    /**
     * Pixels per design unit for a host of this size: the uniform scale that
     * fits the 120x120 design box into the view, i.e. the same factor
     * {@link #drawInto} applies to its canvas. Anything drawn outside the
     * canvas (the out-of-ring network type label) has to convert its design-unit
     * size with this, or it will not match the glyph next to it.
     */
    static float inkScale(int w, int h) {
        return inkScale(w, h, TrioGeometry.INK_W, TrioGeometry.INK_H);
    }

    /**
     * The fit for whichever arrangement is selected. The settings preview draws
     * its own label beside the glyph, so it has to know the same factor
     * {@link #drawInto} will use - and that depends on the style.
     */
    static float inkScale(int w, int h, boolean rect) {
        return rect
                ? inkScale(w, h, TrioGeometry.RECT_INK_W, TrioGeometry.RECT_INK_H)
                : inkScale(w, h);
    }

    /**
     * The same fit, chosen by the stored style.
     *
     * <p>Distinct from the boolean overload above because the capsule is a third
     * ink box rather than a variant of one of the other two - and because the
     * out-of-ring label has to agree with whichever arrangement is in force.
     */
    static float inkScale(int w, int h, int style) {
        if (style == Prefs.STYLE_RECT) {
            return inkScale(w, h, TrioGeometry.RECT_INK_W, TrioGeometry.RECT_INK_H);
        }
        if (style == Prefs.STYLE_CAPSULE) {
            // The banded box: the capsule plus the strip either opening draws in. See
            // TrioGeometry.CAP_INK_TOP - the box grew in HEIGHT only, so the width-driven fit
            // is unchanged and the capsule is the pixels it always was.
            return inkScale(w, h, TrioGeometry.capInkW(),
                    TrioGeometry.CAP_INK_BOTTOM - TrioGeometry.CAP_INK_TOP);
        }
        return inkScale(w, h);
    }

    /**
     * The same fit for an arbitrary ink box. The two arrangements have different
     * ink extents, so the scale that fits one does not fit the other.
     */
    static float inkScale(int w, int h, float inkW, float inkH) {
        if (w <= 0 || h <= 0) {
            return 0f;
        }
        return Math.min(w / inkW, h / inkH);
    }

    /**
     * The concrete mapping from the 120x120 design box onto a canvas of this
     * size, as {@code {scale, translateX, translateY}}.
     *
     * <p>This is the whole point of the method existing separately: the drawing
     * path and the settings screen's tappable zones must agree about where a
     * design-space point lands, or a hot spot ends up beside the thing it names.
     * Writing the fit out a second time at the hit-testing end would be a second
     * source of truth for the same arithmetic, and the two would drift the first
     * time either one changed.
     *
     * <p>Callers apply it in this order, exactly as the canvas does: translate by
     * the returned offsets, then scale by the returned factor. Canonically
     * {@code device = design * scale + translate}.
     *
     * @param out receives {@code {scale, translateX, translateY}}; untouched when
     *            the method returns false
     * @return false when there is nothing to draw into, with {@code out} left
     *         alone so a caller cannot accidentally use a stale transform
     */
    static boolean designTransform(int w, int h, TrioAppearance appearance, float[] out) {
        if (w <= 0 || h <= 0 || appearance == null || out == null || out.length < 3) {
            return false;
        }
        final boolean rect = appearance.rect;
        // The overall-size setting is applied to the fit rather than to the design
        // units: the glyph's whole geometry is expressed in one 120x120 box, so
        // scaling the fit scales every stroke, arc and digit by the same factor
        // and the translate keeps it centred. Doing it anywhere else - a second
        // canvas.scale around the layout calls, say - would scale the stroke
        // widths differently from the radii and the glyph would come apart.
        final float scale = inkScale(w, h, appearance.style) * appearance.glyphFit();
        if (scale <= 0f) {
            return false;
        }
        out[0] = scale;
        if (appearance.capsule) {
            final float boxH = TrioGeometry.CAP_INK_BOTTOM - TrioGeometry.CAP_INK_TOP;
            out[1] = (w - TrioGeometry.capInkW() * scale) / 2f - TrioGeometry.CAP_INK_X * scale;
            out[2] = (h - boxH * scale) / 2f - TrioGeometry.CAP_INK_TOP * scale;
        } else if (rect) {
            out[1] = (w - TrioGeometry.RECT_INK_W * scale) / 2f
                    - TrioGeometry.RECT_INK_X * scale;
            out[2] = (h - TrioGeometry.RECT_INK_H * scale) / 2f
                    - TrioGeometry.RECT_INK_Y * scale;
        } else {
            out[1] = (w - TrioGeometry.INK_W * scale) / 2f - TrioGeometry.INK_X * scale;
            out[2] = (h - TrioGeometry.INK_H * scale) / 2f - TrioGeometry.INK_Y * scale;
        }
        return true;
    }

    /**
     * The shared text face at a numeric weight. The settings app and the hooked
     * side have to agree on it, and so do the in-canvas glyph and the label view
     * drawn beside it.
     *
     * <p>With a custom font loaded this builds on the user's file instead of the
     * system family. The weight overload still runs, because a font that does
     * carry several weights should use them; for the common single-weight file it
     * returns that one face unchanged, and {@link #fakeBoldFor} supplies the
     * visible difference on top.
     */
    static Typeface typefaceFor(int weight) {
        final Typeface base = (sFontFace != null) ? sFontFace : TEXT_BASE;
        return Typeface.create(base, weight, false);
    }

    /**
     * Dresses a real {@code TextView} in the shared face at {@code weight}.
     *
     * <p>The canvas path has {@link #applyWeight} to set the face and the embolden
     * flag together, but a view carries them in two places - the view's own
     * typeface and its paint's fake-bold flag - so the pairing belongs here rather
     * than repeated at every call site. Without the paint half, a single-weight
     * custom file leaves the weight sliders looking dead.
     *
     * <p>Unconditional work, guarded by the caller. Its callers already remember
     * "what did I install last time" as a plain number - see {@code PerfLabel} and
     * {@code OutTypeLabel} - and they add {@link #fontGeneration()} to that test.
     * Comparing the installed {@link Typeface} here instead would be wrong:
     * {@code Typeface.create} is only documented to answer an equal face rather
     * than the same instance, so an identity compare can fail on every pass and,
     * because the embolden flag forces a re-layout, put the view into a layout
     * loop.
     */
    static void styleTextView(android.widget.TextView view, int weight) {
        if (view == null) {
            return;
        }
        view.setTypeface(typefaceFor(weight));
        // Always set, never only when true: a leftover synthetic embolden from a
        // heavier weight would otherwise thicken a face that should be plain.
        view.getPaint().setFakeBoldText(fakeBoldFor(weight));
        // The face and the embolden flag both change the measured width, and a
        // TextView keeps its layout until it is asked to re-measure.
        view.requestLayout();
    }

    /**
     * Bumped whenever the loaded font changes.
     *
     * <p>A view's own "last weight" memory is not enough on its own: picking a new
     * font can leave the weight untouched, and the stored number would then say
     * "nothing to do" while the face behind it had moved. Callers fold this into
     * that test so a font swap reaches them exactly once.
     */
    static int fontGeneration() {
        return sFontGeneration;
    }

    // ----------------------------------------------------------- path builders

    /** The charging bolt outline, in 120x120 design units. */
    private static Path buildBolt() {
        final Path p = new Path();
        p.moveTo(62.1f, 2.2f);
        p.quadTo(62.8f, 2.5f, 62.6f, 3.3f);
        p.lineTo(61.2f, 7.8f);
        p.lineTo(65.9f, 7.8f);
        p.quadTo(66.9f, 7.8f, 67.3f, 8.6f);
        p.quadTo(67.6f, 9.3f, 67.0f, 10.0f);
        p.lineTo(57.0f, 21.3f);
        p.quadTo(56.4f, 22.0f, 55.6f, 21.6f);
        p.quadTo(55.0f, 21.3f, 55.3f, 20.5f);
        p.lineTo(57.4f, 14.1f);
        p.lineTo(52.9f, 14.1f);
        p.quadTo(52.0f, 14.1f, 51.6f, 13.3f);
        p.quadTo(51.3f, 12.6f, 51.9f, 12.0f);
        p.lineTo(61.1f, 2.7f);
        p.quadTo(61.6f, 2.1f, 62.1f, 2.2f);
        p.close();
        return p;
    }

    /** The innermost Wi-Fi dot, in 120x120 design units. */
    private static Path buildWifiDot() {
        final Path p = new Path();
        p.moveTo(59.5f, 69.9f);
        p.cubicTo(61.0f, 69.9f, 65.2f, 70.8f, 66.5f, 73.0f);
        p.cubicTo(66.7f, 73.8f, 66.7f, 74.3f, 66.5f, 75.0f);
        p.cubicTo(63.8f, 78.8f, 61.15f, 80.95f, 59.5f, 80.95f);
        p.cubicTo(57.85f, 80.95f, 55.2f, 78.8f, 52.5f, 75.0f);
        p.cubicTo(52.3f, 74.3f, 52.3f, 73.8f, 52.5f, 73.0f);
        p.cubicTo(53.8f, 70.8f, 58.0f, 69.9f, 59.5f, 69.9f);
        p.close();
        return p;
    }

    /** sRGB luminance test: a light icon colour implies a dark status bar. */
    static boolean isDarkBackground(int color) {
        final float lum = (0.2126f * Color.red(color)
                + 0.7152f * Color.green(color)
                + 0.0722f * Color.blue(color)) / 255f;
        return lum > 0.5f;
    }
}
