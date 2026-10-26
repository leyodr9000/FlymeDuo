/*
 * Copyright (C) 2026 yixing233 and HyperDuo contributors
 *
 * This file is part of HyperDuo, released under the GNU General Public
 * License v3.0. See the LICENSE file at the repository root, or
 * <https://www.gnu.org/licenses/gpl-3.0.html>.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.yixing233.hyperduo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import io.github.yixing233.hyperduo.Prefs
import io.github.yixing233.hyperduo.R
import io.github.yixing233.hyperduo.TrioAppearance
import io.github.yixing233.hyperduo.TrioSettings
import io.github.yixing233.hyperduo.TrioZones
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop

/**
 * One switchable component, paired with the title the settings screen shows for
 * it. The drawing page needs the name as well as the identity, because the dialog
 * it opens is titled with it.
 */
internal enum class TrioSettingsPart(val part: TrioZones.Part?, val labelRes: Int) {
    Appearance(null, R.string.drawing_part_appearance),
    Battery(null, R.string.drawing_part_battery),
    Readouts(null, R.string.perf_pair_title),
    Wifi(TrioZones.Part.WIFI, R.string.show_wifi_title),
    /**
     * The signal card, and the one place a per-card row is chosen.
     *
     * <p>SIM 1 and SIM 2 used to be cards of their own, which meant two cards in the
     * component library whose sheets were one shared set of settings - there was no
     * second thing to set, so the second card could only repeat the first. The two
     * cards are one kind of ink (a row of level dots, from one set of geometry
     * constants); what differs between them is which reading they show. So they are
     * rows inside this card instead: "show mobile dots" is the aggregate meter, and
     * the two per-card rows sit under it.
     */
    Mobile(TrioZones.Part.MOBILE, R.string.show_mobile_title),
    Value(TrioZones.Part.VALUE, R.string.show_value_title),
    Bolt(TrioZones.Part.BOLT, R.string.show_bolt_title),
    Type(TrioZones.Part.TYPE, R.string.show_mobile_type_title),
    Clock(TrioZones.Part.CLOCK, R.string.drawing_occupant_clock),
    ;

    companion object {
        /**
         * The entry for {@code part}, or null when it has no row of its own.
         *
         * <p>SIM 1 and SIM 2 have no card of their own any more, so they resolve to the
         * signal card: tapping either card's ink on the preview opens the one sheet that
         * offers both switches.
         */
        fun of(part: TrioZones.Part?): TrioSettingsPart? = when (part) {
            null -> null
            TrioZones.Part.SIM_1, TrioZones.Part.SIM_2 -> Mobile
            else -> entries.firstOrNull { it.part == part }
        }
    }
}

/**
 * The sections of the size page. The drawing page's per-part dialog reuses the
 * same rows, so the two places that can change a size share one definition or
 * they drift - which is the failure this file exists to prevent.
 */
internal enum class GeometryGroup(val titleRes: Int) {
    Scale(R.string.group_scale),
    Stroke(R.string.group_stroke),
    Text(R.string.group_text),
    Type(R.string.group_type),
    OutSignal(R.string.group_out_signal),
    Track(R.string.group_track),
}

/**
 * Font weights snap in hundreds: 100, 200, ... 900.
 *
 * <p>Lives here rather than on the settings screen because the weight rows do: the
 * three sliders that use it are rows of [geometrySliders], and a step restated at
 * each of them is three copies of one number. It was `WEIGHT_STEP` beside the old
 * hand-written `geometryTab`, which left it dead the moment the rows moved here.
 */
private const val WEIGHT_STEP = 100

/**
 * One size row, described once.
 *
 * <p>Every field is a function of the appearance rather than a captured value,
 * because several rows rename themselves for the rectangular arrangement and
 * every row's gate has to follow the current snapshot.
 *
 * @param part which component this row sizes, or null when it belongs to the
 *   glyph as a whole rather than to one switchable piece. The drawing page's
 *   long-press dialog filters on this.
 */
internal class SliderSpec(
    val group: GeometryGroup,
    val part: TrioZones.Part?,
    val min: Int,
    val max: Int,
    /**
     * The ceiling for the current appearance, when it is not a constant.
     *
     * <p>Only the percentage uses it: a placed reading in the ring is sized by the third it
     * sits in, which can carry far more than the fixed slots of the capsule and rectangle.
     * Defaults to [max], so every other row keeps its constant span.
     */
    val maxFor: (TrioAppearance) -> Int = { max },
    val read: (TrioSettings) -> Int,
    val write: (SettingsRepository, Int) -> Unit,
    val titleRes: (TrioAppearance) -> Int,
    val summaryRes: (TrioAppearance) -> Int,
    val enabled: (TrioAppearance) -> Boolean,
    /** The gates whose titles explain a greyed-out row, in reading order. */
    val hints: (TrioAppearance) -> List<Pair<Boolean, Int>> = { emptyList() },
    val step: Int = 1,
    val suffix: String? = null,
    val editorOwner: TrioSettingsPart = TrioSettingsPart.Appearance,
)

/**
 * Every size row in the module, as one table.
 *
 * <p>Both the size page and the drawing page's part dialog render from this list,
 * so a new slider is added here rather than in two places. The order within a
 * group is the order the size page shows.
 *
 * <p>The gates are the rule the whole settings screen follows: a row may be gated
 * by <em>other</em> switches but never by its own value, or switching it off would
 * disable the row and it could never be turned back on. See the note on the
 * percentage-weight row, which is gated on the percentage switch rather than on
 * the rule that decides whether a percentage is drawn.
 */
internal fun geometrySliders(): List<SliderSpec> {
    val value = TrioZones.Part.VALUE
    val wifi = TrioZones.Part.WIFI
    val mobile = TrioZones.Part.MOBILE
    val type = TrioZones.Part.TYPE
    val bolt = TrioZones.Part.BOLT
    return listOf(
        SliderSpec(
            group = GeometryGroup.Text,
            part = TrioZones.Part.CLOCK,
            min = Prefs.MIN_CLOCK_SIZE,
            max = Prefs.MAX_CLOCK_SIZE,
            maxFor = { if (it.capsule) Prefs.MAX_CAPSULE_CLOCK_SIZE else Prefs.MAX_CLOCK_SIZE },
            read = { it.clockSize },
            write = { r, v -> r.setClockSize(v) },
            titleRes = { R.string.clock_size_title },
            summaryRes = { R.string.clock_size_summary },
            enabled = { it.glyph },
            hints = { listOf(it.glyph to R.string.master_title) },
        ),
        SliderSpec(
            group = GeometryGroup.Text,
            part = TrioZones.Part.CLOCK,
            min = Prefs.MIN_CLOCK_WEIGHT,
            max = Prefs.MAX_CLOCK_WEIGHT,
            step = WEIGHT_STEP,
            read = { it.clockWeight },
            write = { r, v -> r.setClockWeight(v) },
            titleRes = { R.string.clock_weight_title },
            summaryRes = { R.string.clock_weight_summary },
            enabled = { it.glyph },
            hints = { listOf(it.glyph to R.string.master_title) },
        ),
        SliderSpec(
            group = GeometryGroup.Scale,
            part = null,
            min = Prefs.MIN_CAPSULE_NUDGE_X,
            max = Prefs.MAX_CAPSULE_NUDGE_X,
            read = { it.capsuleLeftDx },
            write = { r, v -> r.setCapsuleLeftDx(v) },
            titleRes = { R.string.capsule_nudge_left_dx_title },
            summaryRes = { R.string.capsule_nudge_left_dx_summary },
            enabled = { it.glyph && it.capsule },
        ),
        SliderSpec(
            group = GeometryGroup.Scale,
            part = null,
            min = Prefs.MIN_CAPSULE_NUDGE,
            max = Prefs.MAX_CAPSULE_NUDGE,
            read = { it.capsuleLeftDy },
            write = { r, v -> r.setCapsuleLeftDy(v) },
            titleRes = { R.string.capsule_nudge_left_dy_title },
            summaryRes = { R.string.capsule_nudge_left_dy_summary },
            enabled = { it.glyph && it.capsule },
        ),
        SliderSpec(
            group = GeometryGroup.Scale,
            part = null,
            // Mirrored span: the right cell leans right, so its travel is to the LEFT.
            min = Prefs.MIN_CAPSULE_NUDGE_X_RIGHT,
            max = Prefs.MAX_CAPSULE_NUDGE_X_RIGHT,
            read = { it.capsuleRightDx },
            write = { r, v -> r.setCapsuleRightDx(v) },
            titleRes = { R.string.capsule_nudge_right_dx_title },
            summaryRes = { R.string.capsule_nudge_right_dx_summary },
            enabled = { it.glyph && it.capsule },
        ),
        SliderSpec(
            group = GeometryGroup.Scale,
            part = null,
            min = Prefs.MIN_CAPSULE_NUDGE,
            max = Prefs.MAX_CAPSULE_NUDGE,
            read = { it.capsuleRightDy },
            write = { r, v -> r.setCapsuleRightDy(v) },
            titleRes = { R.string.capsule_nudge_right_dy_title },
            summaryRes = { R.string.capsule_nudge_right_dy_summary },
            enabled = { it.glyph && it.capsule },
        ),
        // How long the capsule is. The straight runs between its two caps are the only part of
        // the shape that can change without changing its form: the caps keep their radius, the
        // height does not move, and every proportion the design states against the height is
        // untouched. This is what "make the capsule wider" means physically.
        SliderSpec(
            group = GeometryGroup.Scale,
            part = null,
            min = Prefs.MIN_CAPSULE_LENGTH,
            max = Prefs.MAX_CAPSULE_LENGTH,
            read = { it.capsuleLength },
            write = { r, v -> r.setCapsuleLength(v) },
            titleRes = { R.string.capsule_length_title },
            summaryRes = { R.string.capsule_length_summary },
            enabled = { it.glyph && it.capsule },
        ),
        // The glyph's overall size lives as a named spec so the home page's master
        // section and the battery editor render one and the same row object.
        glyphScaleSlider,
        glyphOffsetXSlider,
        glyphOffsetYSlider,
        SliderSpec(
            group = GeometryGroup.Scale,
            part = null,
            min = Prefs.MIN_PERF_SCALE,
            max = Prefs.MAX_PERF_SCALE,
            read = { it.perfScale },
            editorOwner = TrioSettingsPart.Readouts,
            write = { r, v -> r.setPerfScale(v) },
            titleRes = { R.string.perf_scale_title },
            summaryRes = { R.string.perf_scale_summary },
            // Gated on the two readout switches rather than on their own values:
            // a reading that is not drawn has no size to set. Gating on
            // perfAnywhere() is safe because that is the OR of two other rows,
            // never this one's own.
            enabled = { it.glyph && it.perfAnywhere() },
            hints = { a ->
                listOf(a.glyph to R.string.master_title, a.perfAnywhere() to R.string.perf_pair_title)
            },
            suffix = "%",
        ),
        SliderSpec(
            group = GeometryGroup.Scale,
            part = null,
            min = Prefs.MIN_PERF_WEIGHT,
            max = Prefs.MAX_PERF_WEIGHT,
            step = WEIGHT_STEP,
            read = { it.perfWeight },
            editorOwner = TrioSettingsPart.Readouts,
            write = { r, v -> r.setPerfWeight(v) },
            titleRes = { R.string.perf_weight_title },
            summaryRes = { R.string.perf_weight_summary },
            enabled = { it.glyph && it.perfAnywhere() },
            hints = { a ->
                listOf(a.glyph to R.string.master_title, a.perfAnywhere() to R.string.perf_pair_title)
            },
        ),
        SliderSpec(
            group = GeometryGroup.Stroke,
            part = null,
            min = Prefs.MIN_RING_STROKE,
            max = Prefs.MAX_RING_STROKE,
            read = { it.ringStroke },
            editorOwner = TrioSettingsPart.Battery,
            write = { r, v -> r.setRingStroke(v) },
            // One stroke setting, two names: the rectangle spends it on the bar's
            // thickness, the ring on the outline's width.
            titleRes = { if (it.rect) R.string.stroke_title_rect else R.string.stroke_title },
            summaryRes = { if (it.rect) R.string.stroke_summary_rect else R.string.stroke_summary },
            enabled = { it.glyph },
        ),
        SliderSpec(
            group = GeometryGroup.Stroke,
            part = wifi,
            min = Prefs.MIN_ARC_STROKE,
            max = Prefs.MAX_ARC_STROKE,
            read = { it.arcStroke },
            write = { r, v -> r.setArcStroke(v) },
            titleRes = { R.string.arc_stroke_title },
            // In the rectangle the arc group hangs off the top slot by its own
            // stroke, so this slider moves the arcs as well as thickening them.
            summaryRes = {
                if (it.rect) R.string.arc_stroke_summary_rect else R.string.arc_stroke_summary
            },
            enabled = { it.glyph },
        ),
        SliderSpec(
            group = GeometryGroup.Stroke,
            part = wifi,
            min = Prefs.MIN_WIFI_SCALE,
            max = Prefs.MAX_WIFI_SCALE,
            read = { it.wifiScale },
            write = { r, v -> r.setWifiScale(v) },
            titleRes = { R.string.wifi_scale_title },
            summaryRes = { R.string.wifi_scale_summary },
            // Multiplies inside the room the arrangement fits the arcs to, so a large value
            // crowded against a thick stroke stops growing instead of crossing the notch.
            enabled = { it.glyph },
            suffix = "%",
        ),
        SliderSpec(
            group = GeometryGroup.Text,
            part = value,
            min = Prefs.MIN_VALUE_SIZE,
            max = Prefs.MAX_VALUE_SIZE,
            // Each arrangement gets the ceiling it can actually draw to, and nothing more:
            // a slider that runs past what the glyph can hold is a slider that does nothing
            // at the top. The ring sizes a piece by the third it sits in; the capsule divides
            // its interior by what its two cells draw; the rectangle has a fixed slot.
            maxFor = {
                when {
                    it.ring -> Prefs.MAX_RING_VALUE_SIZE
                    it.capsule -> Prefs.MAX_CAPSULE_VALUE_SIZE
                    else -> Prefs.MAX_VALUE_SIZE
                }
            },
            read = { it.valueSize },
            write = { r, v -> r.setValueSize(v) },
            titleRes = { R.string.value_size_title },
            summaryRes = { R.string.value_size_summary },
            enabled = { it.glyph },
        ),
        SliderSpec(
            group = GeometryGroup.Text,
            part = value,
            min = Prefs.MIN_VALUE_WEIGHT,
            max = Prefs.MAX_VALUE_WEIGHT,
            step = WEIGHT_STEP,
            read = { it.valueWeight },
            write = { r, v -> r.setValueWeight(v) },
            titleRes = { R.string.value_weight_title },
            summaryRes = { R.string.value_weight_summary },
            // Gated on the percentage switch, deliberately not on drawsBolt() or
            // any rule that folds this row's own value in: a self-gate would lock
            // the row shut the moment it was switched off.
            enabled = { it.glyph && it.value },
            hints = { a -> listOf(a.glyph to R.string.master_title, a.value to R.string.show_value_title) },
        ),
        SliderSpec(
            group = GeometryGroup.Type,
            part = bolt,
            min = Prefs.MIN_BOLT_SCALE,
            max = Prefs.MAX_BOLT_SCALE,
            read = { it.boltScale },
            write = { r, v -> r.setBoltScale(v) },
            titleRes = { R.string.bolt_scale_title },
            summaryRes = { R.string.bolt_scale_summary },
            // Gated on the bolt being PLACED, not on drawsBolt(): that predicate folds in
            // whether charging is happening right now, so gating on it would grey the row out
            // every time the charger was unplugged - which is exactly when nobody is looking
            // at it. There is no switch of its own any more, so the hint names the layout.
            enabled = { it.glyph && it.hasBolt() },
            hints = { a ->
                listOf(a.glyph to R.string.master_title, a.hasBolt() to R.string.bolt_scale_placed_hint)
            },
            suffix = "%",
        ),
        /**
         * The ring's level-dot size - one row for both the top and the bottom row.
         *
         * <p>Owned by [TrioZones.Part.MOBILE] because that is the card the dot ink belongs
         * to, and the two per-card rows share it: the ink is one kind of thing (a row of
         * four beads) and the geometry that sizes it is one set of constants, so a second
         * slider for the cards would be two names for one bead.
         *
         * <p>Gated on the arrangement rather than on a switch. The row sizes ink that only
         * the ring has an opening for; the capsule's rows fit their own outline and the
         * rectangle's columns are square, so on those two the setting would do nothing and
         * a live slider that does nothing is worse than a greyed one that says why. It is
         * not gated on the mobile switches either - a user who has just turned the dots off
         * still needs to set the size before turning them back on, which is the same rule
         * every other size row follows.
         */
        SliderSpec(
            group = GeometryGroup.Type,
            part = mobile,
            min = Prefs.MIN_DOT_SCALE,
            max = Prefs.MAX_DOT_SCALE,
            read = { it.dotScale },
            write = { r, v -> r.setDotScale(v) },
            titleRes = { R.string.dot_scale_title },
            summaryRes = { R.string.dot_scale_summary },
            enabled = { it.glyph && it.ring },
            hints = { a ->
                listOf(a.glyph to R.string.master_title, a.ring to R.string.trio_style_ring)
            },
            suffix = "%",
        ),
        SliderSpec(
            group = GeometryGroup.Type,
            part = type,
            min = Prefs.MIN_TYPE_SIZE,
            max = Prefs.MAX_TYPE_SIZE,
            maxFor = { if (it.capsule) Prefs.MAX_CAPSULE_TYPE_SIZE else Prefs.MAX_TYPE_SIZE },
            read = { it.typeSize },
            write = { r, v -> r.setTypeSize(v) },
            titleRes = { R.string.type_size_title },
            summaryRes = { R.string.type_size_summary },
            enabled = { it.glyph && it.typeInRing },
            hints = { a ->
                listOf(
                    a.glyph to R.string.master_title,
                    a.typeInRing to R.string.show_mobile_type_title,
                )
            },
        ),
        SliderSpec(
            group = GeometryGroup.Type,
            part = type,
            min = Prefs.MIN_OUT_TYPE_SIZE_DP,
            max = Prefs.MAX_OUT_TYPE_SIZE_DP,
            read = { it.outTypeSize },
            write = { r, v -> r.setOutTypeSize(v) },
            titleRes = { R.string.out_type_size_title },
            summaryRes = { R.string.out_type_size_summary },
            enabled = { it.glyph && it.typeOutOfRing },
            hints = { a ->
                listOf(
                    a.glyph to R.string.master_title,
                    a.typeOutOfRing to R.string.show_mobile_type_title,
                )
            },
            suffix = "dp",
        ),
        SliderSpec(
            group = GeometryGroup.Type,
            part = type,
            min = Prefs.MIN_TYPE_SUFFIX_SCALE,
            max = Prefs.MAX_TYPE_SUFFIX_SCALE,
            read = { it.typeSuffixScale },
            write = { r, v -> r.setTypeSuffixScale(v) },
            titleRes = { R.string.type_suffix_scale_title },
            summaryRes = { R.string.type_suffix_scale_summary },
            // A property of the label itself rather than of either position, so
            // "a network type is drawn somewhere" is the right gate.
            enabled = { it.glyph && it.typeAnywhere() },
            hints = { a ->
                listOf(
                    a.glyph to R.string.master_title,
                    a.typeAnywhere() to R.string.show_mobile_type_title,
                )
            },
        ),
        SliderSpec(
            group = GeometryGroup.Type,
            part = type,
            min = Prefs.MIN_OUT_TYPE_MARGIN,
            max = Prefs.MAX_OUT_TYPE_MARGIN,
            read = { it.outTypeMarginLeft },
            write = { r, v -> r.setOutTypeMarginLeft(v) },
            titleRes = { R.string.out_type_margin_left_title },
            summaryRes = { R.string.out_type_margin_left_summary },
            enabled = { it.glyph && it.typeOutOfRing },
            hints = { a ->
                listOf(
                    a.glyph to R.string.master_title,
                    a.typeOutOfRing to R.string.show_mobile_type_title,
                )
            },
            suffix = "dp",
        ),
        SliderSpec(
            group = GeometryGroup.Type,
            part = type,
            min = Prefs.MIN_OUT_TYPE_MARGIN,
            max = Prefs.MAX_OUT_TYPE_MARGIN,
            read = { it.outTypeMarginRight },
            write = { r, v -> r.setOutTypeMarginRight(v) },
            titleRes = { R.string.out_type_margin_right_title },
            summaryRes = { R.string.out_type_margin_right_summary },
            enabled = { it.glyph && it.typeOutOfRing },
            hints = { a ->
                listOf(
                    a.glyph to R.string.master_title,
                    a.typeOutOfRing to R.string.show_mobile_type_title,
                )
            },
            suffix = "dp",
        ),
        SliderSpec(
            group = GeometryGroup.Type,
            part = type,
            min = Prefs.MIN_TYPE_WEIGHT,
            max = Prefs.MAX_TYPE_WEIGHT,
            step = WEIGHT_STEP,
            read = { it.typeWeight },
            write = { r, v -> r.setTypeWeight(v) },
            titleRes = { R.string.type_weight_title },
            summaryRes = { R.string.type_weight_summary },
            enabled = { it.glyph && it.typeAnywhere() },
            hints = { a ->
                listOf(
                    a.glyph to R.string.master_title,
                    a.typeAnywhere() to R.string.show_mobile_type_title,
                )
            },
        ),
        SliderSpec(
            group = GeometryGroup.OutSignal,
            part = mobile,
            min = Prefs.MIN_OUT_SIGNAL_SIZE,
            max = Prefs.MAX_OUT_SIGNAL_SIZE,
            read = { it.outSignalSize },
            write = { r, v -> r.setOutSignalSize(v) },
            titleRes = { R.string.out_signal_size_title },
            summaryRes = { R.string.out_signal_size_summary },
            // The size of the out-of-ring reading only exists while that reading does, so
            // both halves of the choice gate the row: the position and the stacked switch.
            enabled = { it.glyph && it.stackedOut() },
            hints = { a ->
                listOf(
                    a.glyph to R.string.master_title,
                    a.signalOutOfRing to R.string.ring_position_mobile,
                    a.stackedSignal to R.string.stacked_signal_title,
                )
            },
            suffix = "dp",
        ),
        SliderSpec(
            group = GeometryGroup.OutSignal,
            part = mobile,
            min = Prefs.MIN_OUT_SIGNAL_MARGIN,
            max = Prefs.MAX_OUT_SIGNAL_MARGIN,
            read = { it.outSignalMargin },
            write = { r, v -> r.setOutSignalMargin(v) },
            titleRes = { R.string.out_signal_margin_title },
            summaryRes = { R.string.out_signal_margin_summary },
            enabled = { it.glyph && it.stackedOut() },
            hints = { a ->
                listOf(
                    a.glyph to R.string.master_title,
                    a.signalOutOfRing to R.string.ring_position_mobile,
                    a.stackedSignal to R.string.stacked_signal_title,
                )
            },
            suffix = "dp",
        ),
        SliderSpec(
            group = GeometryGroup.Track,
            part = null,
            min = Prefs.MIN_TRACK_ALPHA,
            max = Prefs.MAX_TRACK_ALPHA,
            read = { it.trackAlpha },
            editorOwner = TrioSettingsPart.Battery,
            write = { r, v -> r.setTrackAlpha(v) },
            titleRes = { R.string.track_alpha_title },
            summaryRes = { R.string.track_alpha_summary },
            enabled = { it.glyph },
        ),
    )
}

/** The rows that size one component, for the drawing page's long-press dialog. */
internal fun slidersForPart(part: TrioZones.Part): List<SliderSpec> =
    geometrySliders().filter { it.part == part }

/**
 * The glyph's overall size, as one named row. The battery editor draws it, and
 * the home page draws it again beside the master switch - "how big is the icon"
 * reads as a master-level decision there, not a component detail. One spec
 * object on both surfaces, so the two can never disagree about min, max or gate.
 */
internal val glyphScaleSlider: SliderSpec = SliderSpec(
    group = GeometryGroup.Scale,
    part = null,
    min = Prefs.MIN_GLYPH_SCALE,
    max = Prefs.MAX_GLYPH_SCALE,
    read = { it.glyphScale },
    write = { r, v -> r.setGlyphScale(v) },
    titleRes = { R.string.glyph_scale_title },
    summaryRes = { R.string.glyph_scale_summary },
    // Gated on the master switch and nothing else: it sizes the glyph itself,
    // so with the glyph off there is nothing for it to act on. No row is gated
    // by it, so it can never be the reason another is shut.
    enabled = { it.glyph },
    editorOwner = TrioSettingsPart.Battery,
    suffix = "%",
)

/**
 * The glyph's whole-body horizontal nudge, as a named row beside the size row -
 * same two surfaces, same one-object rule. Negative moves left.
 */
internal val glyphOffsetXSlider: SliderSpec = SliderSpec(
    group = GeometryGroup.Scale,
    part = null,
    min = Prefs.MIN_GLYPH_OFFSET,
    max = Prefs.MAX_GLYPH_OFFSET,
    read = { it.glyphOffsetX },
    write = { r, v -> r.setGlyphOffsetX(v) },
    titleRes = { R.string.glyph_offset_x_title },
    summaryRes = { R.string.glyph_offset_summary },
    enabled = { it.glyph },
    editorOwner = TrioSettingsPart.Battery,
    suffix = "dp",
)

/** The vertical twin of [glyphOffsetXSlider]; negative moves up. */
internal val glyphOffsetYSlider: SliderSpec = SliderSpec(
    group = GeometryGroup.Scale,
    part = null,
    min = Prefs.MIN_GLYPH_OFFSET,
    max = Prefs.MAX_GLYPH_OFFSET,
    read = { it.glyphOffsetY },
    write = { r, v -> r.setGlyphOffsetY(v) },
    titleRes = { R.string.glyph_offset_y_title },
    summaryRes = { R.string.glyph_offset_summary },
    enabled = { it.glyph },
    editorOwner = TrioSettingsPart.Battery,
    suffix = "dp",
)

/** The three rows the merged size-and-position group folds away. */
internal val glyphSizeSpecs: List<SliderSpec> =
    listOf(glyphScaleSlider, glyphOffsetXSlider, glyphOffsetYSlider)

/**
 * The glyph's size and position as ONE row: the collapsed header shows the three
 * live values, and tapping it expands the very same [SliderSpec] objects the old
 * flat list drew - one source of truth for range, gate and wording, drawn on both
 * surfaces (the home master section and the battery sheet) as one widget.
 */
@Composable
internal fun GlyphSizeGroup(
    settings: TrioSettings,
    update: ((SettingsRepository) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val a = TrioAppearance.of(settings)
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ArrowPreference(
            title = stringResource(R.string.glyph_size_group_title),
            summary = stringResource(
                R.string.glyph_size_group_summary,
                settings.glyphScale,
                a.glyphOffsetXDp(),
                a.glyphOffsetYDp(),
            ),
            insideMargin = SettingsItemMargin,
            enabled = a.glyph,
            onClick = { expanded = !expanded },
        )
        if (expanded) {
            glyphSizeSpecs.forEach { spec ->
                val hint = gateHintFor(spec, a)
                if (hint == null) {
                    SliderRow(spec, settings, update)
                } else {
                    TooltipBox(text = hint, enabled = true) {
                        SliderRow(spec, settings, update)
                    }
                }
            }
        }
    }
}

/**
 * That group as the home page's master section draws it.
 */
@Composable
internal fun GlyphScaleHomeRow(
    settings: TrioSettings,
    update: ((SettingsRepository) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlyphSizeGroup(settings, update, modifier)
}

/**
 * The capsule container's own parameters: its length and the two interior cells' offsets.
 *
 * <p>Here rather than in a sheet because these size the CONTAINER, not a component. They
 * used to sit under "Appearance" in the component list, two taps away from the layout that
 * arranges the capsule, which made changing the shape a trip between two sections.
 *
 * <p>The page that draws the layout calls this under its own list, and only while the
 * capsule is the arrangement in force: for a ring these keys mean nothing.
 */
@Composable
internal fun CapsuleContainerRows(
    settings: TrioSettings,
    update: ((SettingsRepository) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val specs = geometrySliders().filter {
        it.part == null && it.editorOwner == TrioSettingsPart.Appearance
    }
    if (specs.isEmpty()) {
        return
    }
    val a = TrioAppearance.of(settings)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        specs.forEach { spec ->
            val hint = gateHintFor(spec, a)
            if (hint == null) {
                SliderRow(spec, settings, update)
            } else {
                TooltipBox(text = hint, enabled = true) {
                    SliderRow(spec, settings, update)
                }
            }
        }
    }
}

internal fun slidersForEditor(part: TrioSettingsPart): List<SliderSpec> = when (part) {
    // "Appearance" holds no size rows any more: the capsule's length and the two cells'
    // offsets size the CONTAINER, and are drawn under the layout that arranges it. What is
    // left in that sheet is the role colours, which are not a size at all.
    TrioSettingsPart.Appearance -> emptyList()
    TrioSettingsPart.Battery, TrioSettingsPart.Readouts ->
        geometrySliders().filter { it.part == null && it.editorOwner == part }
    // Each card gets its own rows. For the signal card those are the out-of-ring readings'
    // two sizes plus the in-ring dot size: the dots themselves have one shared geometry
    // (both rows, both cards, one bead), so that row is shared rather than per-card, while
    // the stacked reading the module draws itself does have a height and a margin.
    else -> part.part?.let(::slidersForPart).orEmpty()
}
/**
 * The role colours a component's ink takes, so the dialog can offer them.
 *
 * <p>Only two pieces are coloured by role: the percentage takes the low and
 * critical colours, and the bolt takes the charging one. The arcs and the level
 * dots follow the status bar's foreground, so they have no colour of their own to
 * offer and say so instead of showing a row that would do nothing.
 */
internal fun roleColorsForPart(part: TrioZones.Part): List<RoleColor> = when (part) {
    TrioZones.Part.VALUE -> listOf(RoleColor.Low, RoleColor.Critical)
    TrioZones.Part.BOLT -> listOf(RoleColor.Charging)
    else -> emptyList()
}

/**
 * The long-press dialog: the rows that size the tapped component, plus the role
 * colours it takes, both editable in place.
 *
 * <p>It renders [SliderSpec]s from the same table the size page uses, so a change
 * to a row's range, gate or wording lands in both places at once. The rows keep
 * their own gates rather than being hidden: a greyed-out row that names what is
 * holding it shut tells the user why the value they came to change has no effect,
 * where a missing row would leave them wondering whether they had opened the wrong
 * dialog. A component with no size and no colour of its own says so outright.
 *
 * <p>The colour rows reuse the page's own picker rather than a second one, which
 * is why [onEditColor] is a callback: the picker is a dialog of its own and two
 * dialogs stacked is a state the page already models, so the parent keeps owning
 * it.
 */
@Composable
internal fun PartSettingsSheet(
    show: Boolean,
    part: TrioSettingsPart,
    settings: TrioSettings,
    update: ((SettingsRepository) -> Unit) -> Unit,
    onEditColor: (RoleColor) -> Unit,
    onResetColors: () -> Unit,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    preview: (@Composable () -> Unit)? = null,
    ringSettings: TrioSettings = settings,
) {
    val a = TrioAppearance.of(settings)
    val rows = slidersForEditor(part).filterNot { it in glyphSizeSpecs }
    val controls = contentControlsFor(part)
    val colors = if (part == TrioSettingsPart.Appearance || part == TrioSettingsPart.Battery) RoleColor.entries
        else part.part?.let(::roleColorsForPart).orEmpty()
    val configuration = LocalConfiguration.current
    val compact = configuration.screenHeightDp < 600 || configuration.fontScale > 1.3f
    val maxHeight = (configuration.screenHeightDp * 0.72f).dp
    // The system's gesture bar (or nav bar) overlays the bottom of the window, so the
    // sheet's last row has to stay above it. Only the bottom inset is taken: the sheet
    // is anchored to the bottom edge, and adding the side/top insets there would shift
    // its full-bleed background away from the screen edge.
    val bottomInset = WindowInsets.navigationBars.asPaddingValues()
        .calculateBottomPadding()
    val sheetBackdrop = rememberLayerBackdrop()

    OverlayBottomSheet(
        show = show,
        // Recorded on the sheet itself, not on the content inside it. `layerBackdrop` captures
        // the subtree the modifier is attached to, and `LayerBackdrop.drawBackdrop` measures
        // the offset between the recorded node and the popup with `positionInWindow` on each.
        // Attaching it to an inner Column made that offset a sheet-internal one while the
        // dropdown is positioned in window space, so the sample landed outside the recorded
        // bitmap and came back blank - the blur ran, over nothing. On the sheet's own
        // modifier the recorded box is the whole panel, which is also what a dropdown opened
        // from inside it should be frosting.
        modifier = Modifier.layerBackdrop(sheetBackdrop),
        title = stringResource(R.string.drawing_settings_title,
            stringResource(if (part == TrioSettingsPart.Battery && a.ring) R.string.drawing_part_ring else part.labelRes)),
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
    ) {
        CompositionLocalProvider(LocalDropdownBackdrop provides sheetBackdrop) {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight)
                    .padding(bottom = bottomInset),
            ) {
                if (!compact) preview?.invoke()
                Column(modifier = Modifier.weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()).padding(horizontal = 4.dp)) {
                    if (compact) preview?.invoke()
                    controls.forEach { control -> ContentControlRow(control, settings, update, ringSettings) }
                if (rows.isEmpty() && colors.isEmpty() && controls.isEmpty()) {
                    Text(
                        text = stringResource(R.string.drawing_settings_none),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }

                if (colors.isNotEmpty()) {
                    SwitchPreference(
                        checked = a.roleColors,
                        title = stringResource(R.string.color_role_title),
                        insideMargin = SettingsItemMargin,
                        enabled = a.glyph,
                        onCheckedChange = { value -> update { it.setRoleColors(value) } },
                    )
                    if (part != TrioSettingsPart.Bolt) {
                        IntSlider(
                            value = settings.lowThreshold,
                            min = Prefs.MIN_LOW_THRESHOLD,
                            max = Prefs.MAX_LOW_THRESHOLD,
                            title = stringResource(R.string.low_threshold_title),
                            summary = stringResource(R.string.low_threshold_summary),
                            enabled = a.glyph && a.roleColors,
                            onValueChange = { value -> update { it.setLowThreshold(value) } },
                        )
                    }
                }

                if (part == TrioSettingsPart.Battery) {
                    // One folded group instead of three flat rows, the same
                    // widget the home page's master section shows.
                    GlyphSizeGroup(settings, update)
                }

                rows.forEach { spec ->
                    // The hint is resolved in a composable scope and passed down, the
                    // same shape the size page uses: a row that is live has nothing to
                    // explain, and a tooltip saying nothing would still eat the press.
                    val hint = gateHintFor(spec, a)
                    if (hint == null) {
                        SliderRow(spec, settings, update)
                    } else {
                        TooltipBox(text = hint, enabled = true) {
                            SliderRow(spec, settings, update)
                        }
                    }
                }

                colors.forEach { role ->
                    ArrowPreference(
                        title = stringResource(role.labelRes),
                        summary = stringResource(R.string.color_field_summary),
                        insideMargin = SettingsItemMargin,
                        startAction = {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Swatch(colorFor(settings, role, onDark = true))
                                Swatch(colorFor(settings, role, onDark = false))
                            }
                        },
                        // Gated on the same two switches the colour page uses: with
                        // per-role colours off these values are not read at all, so
                        // editing them here would change nothing on screen.
                        enabled = a.glyph && a.roleColors,
                        onClick = { onEditColor(role) },
                    )
                }

                    if (part == TrioSettingsPart.Appearance) {
                    ArrowPreference(
                        title = stringResource(R.string.color_reset),
                        summary = stringResource(R.string.color_reset_summary),
                        insideMargin = SettingsItemMargin,
                        enabled = a.glyph && a.roleColors,
                        onClick = onResetColors,
                    )
                    }
                }
            }
        }
    }
}

/** One size row, rendered from its spec. Shared by the page and this dialog. */
@Composable
private fun SliderRow(
    spec: SliderSpec,
    settings: TrioSettings,
    update: ((SettingsRepository) -> Unit) -> Unit,
) {
    val a = TrioAppearance.of(settings)
    IntSlider(
        value = spec.read(settings).coerceAtMost(spec.maxFor(a)),
        min = spec.min,
        max = spec.maxFor(a),
        step = spec.step,
        title = stringResource(spec.titleRes(a)),
        summary = stringResource(spec.summaryRes(a)),
        enabled = spec.enabled(a),
        valueTextSuffix = spec.suffix,
        onValueChange = { v -> update { r -> spec.write(r, v) } },
    )
}

/**
 * The reason a row in the dialog is greyed out, or null when it is live.
 *
 * <p>The first dependency that is switched off wins, so a row needing two names
 * the one the user is likeliest to fix first. Same rule and same wording as the
 * size page's own gate hint.
 */
@Composable
private fun gateHintFor(spec: SliderSpec, a: TrioAppearance): String? {
    val missing = spec.hints(a).firstOrNull { !it.first } ?: return null
    return stringResource(R.string.gate_hint, stringResource(missing.second))
}
