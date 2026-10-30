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

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.yixing233.hyperduo.R
import io.github.yixing233.hyperduo.TrioAppearance
import io.github.yixing233.hyperduo.TrioSettings
import io.github.yixing233.hyperduo.TrioZones
import io.github.yixing233.hyperduo.Prefs
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.preference.SwitchPreference

/**
 * Content controls shared by General and the visual editor.
 *
 * <p><b>Declaration order is the order they appear in a component's sheet</b>, and it
 * follows one rule: <em>where a thing goes, before how it looks</em>. A row that decides
 * placement comes first, because it answers the question the user opened the sheet with
 * ("where is this drawn?"). Size, weight and colour rows live in [geometrySliders] and
 * come after these, in their own groups.
 *
 * <p>Only one placement choice is left, and only one piece has it: the network type's
 * "show it beside the ring". Every other piece is placed or released - add it to a third
 * and it is drawn there, do not and MIUI keeps its own icon - so the old "signal in ring /
 * out of ring" switch, and the "stacked signal" style that went with it, were asking the
 * user to choose between states the layout already decides.
 */
internal enum class ContentControl(val owner: TrioSettingsPart?) {
    // --- placement: where the piece lives -------------------------------------
    // Enabled / Exclude are drawn on the home master card and the style by the home style
    // cards. Owner null keeps them out of every component sheet, so each has one home.
    /** Master switch, then layout: both decide what exists at all. */
    Enabled(null),
    /** Which regions keep Flyme's own battery; sits next to the master switch. */
    Exclude(null),
    Style(null),
    // --- per component: its own switch ----------------------------------------
    Wifi(TrioSettingsPart.Wifi),
    /**
     * The signal card: one switch for the aggregate meter, then one per card.
     *
     * <p>The two per-card rows are rows of THIS card rather than cards of their own.
     * They were separate cards, and both sheets resolved to the same set of settings -
     * two entries in the library that could only ever repeat each other. The ink is
     * one kind of thing (a row of level dots); which reading it shows is a property of
     * the row, not a second component.
     */
    Mobile(TrioSettingsPart.Mobile),
    DataSim(TrioSettingsPart.Mobile),
    /**
     * The out-of-ring rendering: the module's own stacked reading, or MIUI's icon.
     *
     * <p>Owned by the signal card rather than being a piece of its own, because it is a
     * property of where the signal is drawn, not a third reading. It sits directly under
     * the position row it depends on.
     */
    Stacked(TrioSettingsPart.Mobile),
    Sim1(TrioSettingsPart.Mobile),
    Sim2(TrioSettingsPart.Mobile),
    Value(TrioSettingsPart.Value),
    /** A charger puts the number in the ring's middle; see Prefs.KEY_CHARGING_SHOWS_VALUE. */
    ChargingShowsValue(TrioSettingsPart.Value),
    Bolt(TrioSettingsPart.Bolt),
    /**
     * Network type, and the ONE piece that has a position choice of its own.
     *
     * <p>Every other piece is placed or released: add it to a third and it is drawn there,
     * do not and MIUI keeps its own icon. There is no third option, which is why the old
     * "signal in ring / out of ring" switch is gone. The type is different - it is text with
     * no native counterpart to hand back - so "show it beside the ring" is a real choice
     * rather than a way of expressing the same thing twice.
     */
    Type(TrioSettingsPart.Type),
    /** Drops the label while the arcs are on; see Prefs.KEY_TYPE_HIDE_ON_WIFI. */
    TypeHideOnWifi(TrioSettingsPart.Type),
    /** The twin of the row above, for the mobile-data switch. */
    TypeHideOnDataOff(TrioSettingsPart.Type),

    // --- readouts and fixed pieces --------------------------------------------
    Power(TrioSettingsPart.Readouts),
    Temperature(TrioSettingsPart.Readouts),
    ChargingTemp(TrioSettingsPart.Readouts),
    Clock(TrioSettingsPart.Clock),
}

/** Rows that only configure the module's own out-of-ring pieces; see Prefs.OUT_RING_FEATURES. */
private val OUT_RING_CONTROLS = setOf(
    ContentControl.Stacked, ContentControl.DataSim,
    ContentControl.Power, ContentControl.Temperature, ContentControl.ChargingTemp,
)

internal fun contentControlsFor(part: TrioSettingsPart): List<ContentControl> =
    ContentControl.entries.filter {
        // One entry per card, so a card's sheet shows that card's rows and nothing
        // else. The signal card is the one card that owns several rows, because its
        // three switches are three readings of the same ink.
        it.owner == part && (Prefs.OUT_RING_FEATURES || it !in OUT_RING_CONTROLS)
    }

@Composable
internal fun ContentControlRow(
    control: ContentControl,
    settings: TrioSettings,
    update: ((SettingsRepository) -> Unit) -> Unit,
    ringSettings: TrioSettings = settings,
) {
    val a = TrioAppearance.of(settings)
    val master = listOf(a.glyph to R.string.master_title)
    val positioned = when (control) {
        ContentControl.Value -> TrioZones.Part.VALUE
        // Not a position row: it is a lens over the centre, not a place for the number.
        ContentControl.ChargingShowsValue -> null
        ContentControl.Bolt -> TrioZones.Part.BOLT
        ContentControl.Type -> TrioZones.Part.TYPE
        ContentControl.Clock -> TrioZones.Part.CLOCK
        else -> null
    }
    // Wi-Fi is a position row rather than a switch plus a position row: "off" and "nowhere in
    // the ring" are the same intention, so offering both made the user answer the same
    // question twice and made the two controls able to disagree (switch on, nothing placed,
    // no arcs). The row writes both facts in one go - see WifiPositionControl.
    if (control == ContentControl.Wifi) {
        WifiPositionControl(settings, ringSettings, update)
        return
    }
    if (a.ring && positioned != null) {
        RingPositionControl(positioned, ringSettings, update)
        if (control == ContentControl.Type || control == ContentControl.Clock) return
    }
    // The signal's position applies to every arrangement, not just the ring. It used to be
    // gated on `a.ring` along with the slot rows, which left the capsule and the rectangle
    // with no way to move the signal out of the glyph at all - the switch simply was not
    // drawn, so "outside" was unreachable in those two styles.
    if (control == ContentControl.Mobile) {
        // The signal is the one piece with a two-way position of its own. Drawn above the
        // switches, so "where does it go" is answered before "is it on".
        SignalPositionControl(settings, update)
    }
    // The per-card slot rows exist wherever the arrangement has bands to put a row in: the
    // ring's two openings and the capsule's. Both draw the row as beads/lines in a band, so
    // both can move it. The rectangle cannot - its columns are derived from the mobile switch
    // - so a slot row there would offer a choice that does not exist.
    if (control == ContentControl.Sim1 && a.slotted) {
        SignalCardPositionControl(TrioZones.Part.SIM_1, settings, update, enabled = signalInsideRing(a))
    }
    if (control == ContentControl.Sim2 && a.slotted) {
        SignalCardPositionControl(TrioZones.Part.SIM_2, settings, update, enabled = signalInsideRing(a))
    }
    when (control) {
        ContentControl.Enabled -> ContentToggle(settings.enabled, R.string.master_title,
            null, emptyList()) { value -> update { it.setEnabled(value) } }
        ContentControl.Exclude -> {
            val regions = listOf(Prefs.REGION_ALL, Prefs.REGION_NO_CONTROL_CENTRE,
                Prefs.REGION_NO_NOTIFICATION, Prefs.REGION_NO_BOTH)
            ContentChoice(regions.indexOf(settings.excludeRegions).coerceAtLeast(0),
                // No description on the home page row: the options name themselves, and the
                // three-line explanation made the master card twice as tall.
                R.string.exclude_regions_title, null,
                listOf(R.string.exclude_regions_all, R.string.exclude_regions_no_control_centre,
                    R.string.exclude_regions_no_notification, R.string.exclude_regions_no_both),
                master) { index -> update { it.setExcludeRegions(regions[index]) } }
        }
        ContentControl.Style -> {
            val styles = listOf(Prefs.STYLE_RING, Prefs.STYLE_CAPSULE)
            ContentChoice(styles.indexOf(a.style).coerceAtLeast(0), R.string.trio_style_title,
                null,
                listOf(R.string.trio_style_ring, R.string.trio_style_capsule),
                master) { index -> update { it.setTrioStyle(styles[index]) } }
        }
        ContentControl.Wifi -> Unit // Handled above by WifiPositionControl, which returns.
        // The aggregate meter's own switch is gone with the aggregate meter: the signal
        // is now always the two cards' rows, so "show mobile dots" would be a second
        // name for something the cards already decide.
        ContentControl.Mobile -> Unit
        // Draw the module's own reading beside the battery instead of handing the icon
        // back. Only meaningful out of ring, and gated on the position row rather than on
        // its own value - a row that switches itself off could never be switched back on.
        ContentControl.Stacked -> ContentToggle(
            checked = a.stackedSignal,
            title = R.string.stacked_signal_title,
            summaryRes = R.string.stacked_signal_summary,
            dependencies = master + (a.signalOutOfRing to R.string.ring_position_mobile),
        ) { value -> update { it.setStackedSignal(value) } }
        ContentControl.Sim1 -> ContentToggle(
            checked = a.showSim1,
            title = R.string.show_sim_1_title,
            summaryRes = null,
            dependencies = master + (signalInsideRing(a) to R.string.ring_position_mobile),
        ) { value -> update { if (value) it.setShowMobile(true); it.setShowSim1(value) } }
        ContentControl.Sim2 -> ContentToggle(
            checked = a.showSim2,
            title = R.string.show_sim_2_title,
            summaryRes = null,
            dependencies = master + (signalInsideRing(a) to R.string.ring_position_mobile),
        ) { value -> update { if (value) it.setShowMobile(true); it.setShowSim2(value) } }
        ContentControl.Value -> ContentToggle(a.value, R.string.show_value_title,
            null,
            master) { value -> update { it.setShowValue(value) } }
        // A charger puts the number in the ring's middle, whatever the layout arranged
        // there; the centre returns to its own content on unplug. Ring style only - the
        // capsule and the rectangle have no single "middle" to lend.
        ContentControl.ChargingShowsValue -> ContentToggle(a.chargingShowsValue,
            R.string.charging_shows_value_title,
            R.string.charging_shows_value_summary, master) { value ->
                update { it.setChargingShowsValue(value) }
            }
        // The signal's position is no longer a switch: a card placed in a third is drawn
        // there, and a card that is not placed hands the reading back to MIUI. There is no
        // third state to choose between, so the old row is gone.
        ContentControl.DataSim -> ContentToggle(a.dataSimOnly, R.string.data_sim_only_title,
            null, master) { value -> update { it.setDataSimOnly(value) } }
        // No bolt switch: a charger always reports itself, and where it does so is decided by
        // placing the bolt in the layout like any other piece. A row here would be a second
        // answer to the same question, able to contradict the list it describes.
        ContentControl.Bolt -> Unit
        // a.typeMode is already coerced: a stored OUT_RING reads as OFF while it is disabled.
        ContentControl.Type -> ContentChoice(a.typeMode, R.string.show_mobile_type_title,
            null,
            listOf(R.string.mobile_type_off, R.string.mobile_type_inside) +
                if (Prefs.OUT_RING_FEATURES) listOf(R.string.mobile_type_outside) else emptyList(),
            master) { value -> update { it.setMobileTypeMode(value) } }
        // Only meaningful when the label is drawn somewhere: with the type off there is
        // nothing to step aside.
        ContentControl.TypeHideOnWifi -> ContentToggle(settings.typeHideOnWifi,
            R.string.type_hide_on_wifi_title,
            R.string.type_hide_on_wifi_summary, master) { value ->
                update { it.setTypeHideOnWifi(value) }
            }
        // The same question for the data switch, and the one this module was missing: with
        // no Wi-Fi and no data the label still claimed a network, so the reading could not
        // tell the user whether their data was even on.
        ContentControl.TypeHideOnDataOff -> ContentToggle(settings.typeHideOnDataOff,
            R.string.type_hide_on_data_off_title,
            R.string.type_hide_on_data_off_summary, master) { value ->
                update { it.setTypeHideOnDataOff(value) }
            }
        ContentControl.Power -> ContentToggle(settings.perfPower, R.string.perf_power_title,
            null, master) { value -> update { it.setPerfPower(value) } }
        ContentControl.Temperature -> ContentToggle(settings.perfTemp, R.string.perf_temp_title,
            null, master) { value -> update { it.setPerfTemp(value) } }
        ContentControl.ChargingTemp -> ContentToggle(settings.perfTempChargingOnly,
            R.string.perf_temp_charging_only_title,
            R.string.perf_temp_charging_only_summary, master) { value ->
                update { it.setPerfTempChargingOnly(value) }
            }
        ContentControl.Clock -> Unit // Capsule time has a fixed slot.
    }
}

/**
 * The signal's own two-way position: in the ring, or back to the system.
 *
 * <p>This is the one position row that is not a slot list, because the signal is the one
 * piece drawn from per-card rows rather than from a single slot. "Outside the ring" here
 * means exactly what "return it to the system" means on every other row - the glyph draws
 * nothing and MIUI keeps its icon - so it is labelled 环外 to match where the reading then
 * appears.
 */
@Composable
private fun SignalPositionControl(
    settings: TrioSettings,
    update: ((SettingsRepository) -> Unit) -> Unit,
) {
    val outside = settings.signalMode == Prefs.SIGNAL_OUT_RING
    ContentChoice(
        selected = if (outside) 1 else 0,
        title = R.string.ring_position_mobile,
        summaryRes = null,
        options = listOf(R.string.signal_mode_in_ring, R.string.signal_mode_out_ring),
        dependencies = listOf(settings.enabled to R.string.master_title),
        onChange = { value ->
            update { it.setSignalMode(if (value == 1) Prefs.SIGNAL_OUT_RING else Prefs.SIGNAL_IN_RING) }
        },
    )
}

/**
 * Whether the signal is drawn inside the glyph, which is what enables the per-card rows.
 *
 * <p>Covers the capsule as well as the ring: both draw the rows in a band, so both can place
 * them. The rectangle cannot, and [TrioAppearance.slotted] is what the callers gate on, so
 * this only has to answer the shared part.
 */
// No `a.mobile` here: its switch was removed from the UI, so an install that once stored
// show_mobile=false had both card rows greyed out with no way to turn it back on. Switching
// a card on now writes show_mobile=true as well (see the Sim1/Sim2 rows).
private fun signalInsideRing(a: TrioAppearance): Boolean =
    a.slotted && !a.signalOutOfRing

/**
 * One card's third, with the thirds the other card holds disabled.
 *
 * <p>The two cards cannot share a third: two rows of four dots in one slot would overlap
 * and the layout would have to pick one of them, so the second card's choice is expressed
 * by disabling the option rather than by silently moving the first card. A disabled row
 * that cannot be picked is also the honest presentation of "one of these is taken".
 *
 * <p>The middle is disabled for every card, always, because the ring does not hold a signal
 * row there - see [TrioZones.isSignalRow] and the reason in `Prefs.zoneAccepts`. That is not
 * the other card taking it: it is a band the row has no business in, so it is blocked
 * whatever the twin is doing.
 */
@Composable
private fun SignalCardPositionControl(
    part: TrioZones.Part,
    settings: TrioSettings,
    update: ((SettingsRepository) -> Unit) -> Unit,
    enabled: Boolean,
) {
    val id = TrioZones.encodePart(part)
    val twin = if (part == TrioZones.Part.SIM_1) TrioZones.Part.SIM_2 else TrioZones.Part.SIM_1
    val twinId = TrioZones.encodePart(twin)
    val zones = settings.ringZones
    val placedAt = zones.indexOfFirst { it.contains(id) } + 1
    val takenByTwin = zones.indexOfFirst { it.contains(twinId) }
    // Option 0 is "not placed" and is always allowed; 1..3 mirror the zones. The middle is
    // refused for the row itself, and the one the twin holds is refused because it is taken.
    val blocked = buildSet {
        add(TrioZones.Zone.CENTRE.ordinal + 1)
        if (takenByTwin >= 0) add(takenByTwin + 1)
    }
    ContentChoice(
        selected = placedAt,
        title = if (part == TrioZones.Part.SIM_1) R.string.ring_position_sim_1
        else R.string.ring_position_sim_2,
        summaryRes = null,
        options = listOf(R.string.ring_position_native, R.string.drawing_hint_top,
            R.string.drawing_hint_centre, R.string.drawing_hint_bottom),
        dependencies = listOf(settings.enabled to R.string.master_title),
        enabled = enabled,
        blockedIndices = blocked,
        onChange = { value -> update { it.setRingPartPosition(part, value) } },
    )
}

/**
 * Wi-Fi's position, with "off" as one of the choices.
 *
 * <p>Wi-Fi is the one piece whose switch and position always changed together: switching it
 * off and removing it from the ring produced the same picture, and switching it on with
 * nothing placed produced none. Two rows for one decision invited the pair to disagree, so
 * they are one row here - index 0 means off-and-unplaced, 1..3 mean on-and-in-that-third.
 *
 * <p>Both facts are written together through [SettingsRepository.setWifiPosition], because
 * writing them separately would leave a frame in between where the switch and the layout
 * disagree.
 */
@Composable
private fun WifiPositionControl(
    settings: TrioSettings,
    ringSettings: TrioSettings,
    update: ((SettingsRepository) -> Unit) -> Unit,
) {
    val id = TrioZones.encodePart(TrioZones.Part.WIFI)
    // The capsule has FOUR cells and the ring three, so the list of choices - and the
    // mapping from a stored zone back to a row in it - is per-arrangement, exactly as
    // RingPositionControl does it. This row used to take the array INDEX as the answer
    // and pair it with a fixed three-band list, which in the capsule named the wrong
    // cell: Zone order is TOP, CENTRE, BOTTOM, LEFT, so a piece in the left cell sits at
    // index 3 - read as "fourth choice", which in a four-item list is the BOTTOM label.
    // The list had no Left entry at all, so there was no way to name that cell either.
    val capsule = ringSettings.trioStyle == Prefs.STYLE_CAPSULE
    val slots = if (capsule) {
        listOf(TrioZones.Zone.TOP, TrioZones.Zone.LEFT, TrioZones.Zone.CENTRE,
            TrioZones.Zone.BOTTOM)
    } else {
        listOf(TrioZones.Zone.TOP, TrioZones.Zone.CENTRE, TrioZones.Zone.BOTTOM)
    }
    val placedAt = ringSettings.ringZones.indexOfFirst { it.contains(id) }
    val slotIndex = if (placedAt < 0) 0 else slots.indexOf(TrioZones.Zone.entries[placedAt]) + 1
    // Off wins over "placed somewhere": a switch that is off draws no arcs whatever the
    // layout says, so reporting a slot here would name a place that produces nothing.
    val selected = if (!settings.showWifi) 0 else slotIndex
    ContentChoice(selected, R.string.ring_position_wifi, null,
        listOf(R.string.ring_position_native) + slots.map { zone ->
            when (zone) {
                TrioZones.Zone.TOP -> R.string.drawing_hint_top
                TrioZones.Zone.LEFT -> R.string.drawing_hint_left
                TrioZones.Zone.CENTRE -> if (capsule) R.string.drawing_hint_right
                    else R.string.drawing_hint_centre
                else -> R.string.drawing_hint_bottom
            }
        },
        listOf(settings.enabled to R.string.master_title)) { value ->
        update { it.setWifiPosition(value) }
    }
}

/**
 * The one option every position dropdown has to be able to refuse: the ring's middle does
 * not hold a signal row.
 *
 * <p>Written as a set of option indices rather than as a boolean so the caller can add the
 * options it has its own reason to disable - the third the other card already holds - and
 * hand one set to [ContentChoice]. The index is the zone's ordinal plus one: zero is "not
 * placed" in every one of these dropdowns, and 1..3 mirror the three bands.
 *
 * <p>Only meaningful where the row is actually a ring row. The per-card rows are ring-only,
 * and the Wi-Fi row never names a signal row, so in practice this is the card rows' own
 * constant - kept a function so the arithmetic cannot be written slightly differently in
 * two places.
 */
private fun middleBlocked(part: TrioZones.Part): Set<Int> =
    if (TrioZones.isSignalRow(part)) setOf(TrioZones.Zone.CENTRE.ordinal + 1) else emptySet()

@Composable
private fun RingPositionControl(
    part: TrioZones.Part,
    settings: TrioSettings,
    update: ((SettingsRepository) -> Unit) -> Unit,
) {
    val id = TrioZones.encodePart(part)
    // Which zone's list currently holds this piece, or 0 for "not placed".
    //
    // The dropdown is a shortcut into the priority lists, not a second source of truth: it
    // reads where the piece sits and writes the same layout the editor's list edits. A piece
    // in more than one zone cannot happen (placing moves it), so one answer is enough.
    val zones = settings.ringZones
    // The capsule has FOUR cells (top, left, right, bottom) and the ring has three, so
    // the list of choices is per-arrangement. It used to be a fixed [native, top,
    // centre, bottom]: in the capsule the cell at index 3 is the LEFT one, and that
    // list labelled it 涓嬮儴 - a piece sitting on the left reported itself as being at
    // the bottom. The order here is also the order the reader sees, so it has to match
    // the tab strip.
    val slots = if (settings.trioStyle == Prefs.STYLE_CAPSULE) {
        listOf(TrioZones.Zone.TOP, TrioZones.Zone.LEFT, TrioZones.Zone.CENTRE,
            TrioZones.Zone.BOTTOM)
    } else {
        listOf(TrioZones.Zone.TOP, TrioZones.Zone.CENTRE, TrioZones.Zone.BOTTOM)
    }
    // The stored index is a ZONE ordinal, and the choices are listed in [slots] order.
    // Translate one into the other rather than assuming they agree - that assumption
    // is what produced the wrong label.
    val placedAt = zones.indexOfFirst { it.contains(id) }
    val slotIndex = if (placedAt < 0) 0 else slots.indexOf(TrioZones.Zone.entries[placedAt]) + 1
    val outOfRing = Prefs.OUT_RING_FEATURES && part == TrioZones.Part.TYPE
            && settings.mobileTypeMode == Prefs.MOBILE_TYPE_OUT_RING
    val selected = if (outOfRing) slots.size + 1 else slotIndex
    // The row's title is the SLOT it picks, never the component's own switch name.
    // Reusing the switch title put "显示移动信号点" above "显示移动信号点" - two rows
    // with the same name and different meanings, which reads as a duplicate bug rather
    // than as "choose the slot, then switch it on".
    val title = when (part) {
        TrioZones.Part.WIFI -> R.string.ring_position_wifi
        TrioZones.Part.MOBILE -> R.string.ring_position_mobile
        TrioZones.Part.SIM_1 -> R.string.ring_position_sim_1
        TrioZones.Part.SIM_2 -> R.string.ring_position_sim_2
        TrioZones.Part.VALUE -> R.string.ring_position_value
        TrioZones.Part.BOLT -> R.string.ring_position_bolt
        TrioZones.Part.TYPE -> R.string.ring_position_type
        TrioZones.Part.CLOCK -> R.string.ring_position_clock
    }
    ContentChoice(selected, title,
        null,
        listOf(R.string.ring_position_native) + slots.map { zone ->
            when (zone) {
                TrioZones.Zone.TOP -> R.string.drawing_hint_top
                TrioZones.Zone.LEFT -> R.string.drawing_hint_left
                TrioZones.Zone.CENTRE -> if (settings.trioStyle == Prefs.STYLE_CAPSULE)
                    R.string.drawing_hint_right else R.string.drawing_hint_centre
                else -> R.string.drawing_hint_bottom
            }
        } +
            if (Prefs.OUT_RING_FEATURES && part == TrioZones.Part.TYPE)
                listOf(R.string.mobile_type_outside) else emptyList(),
        listOf(settings.enabled to R.string.master_title),
        // A signal row cannot be put in the middle band - see [middleBlocked]. The row is
        // mostly used by pieces that have no such restriction, so the set is empty for them
        // and [ContentChoice] takes its plain path.
        blockedIndices = middleBlocked(part),
    ) { value ->
        update { it.setRingPartPosition(part, value) }
    }
}

/**
 * A switch row, optionally with a description under its title.
 *
 * <p>[summaryRes] is null for the rows that explain themselves: the master switch
 * (whose own title already says what it turns on) and the ring-position dropdown
 * (whose options name the slots). Miuix drops the description line entirely when
 * the summary is null, so the row is not left with an empty second line.
 */
@Composable
private fun ContentToggle(
    checked: Boolean,
    title: Int,
    summaryRes: Int?,
    dependencies: List<Pair<Boolean, Int>>,
    onChange: (Boolean) -> Unit,
) {
    ContentGate(dependencies) {
        SwitchPreference(
            checked = checked,
            title = stringResource(title),
            summary = summaryRes?.let { stringResource(it) },
            insideMargin = SettingsItemMargin,
            enabled = dependencies.all { it.first },
            onCheckedChange = onChange,
        )
    }
}

/**
 * A dropdown row, optionally with a description and with individual options disabled.
 *
 * <p>[blockedIndices] is for the options that exist but may not be chosen right now - the
 * third already held by the other card. Disabling the entry rather than dropping it keeps
 * the list the same length as the other cards' lists, so the numeric order the row writes
 * stays the same everywhere.
 *
 * @param enabled a gate independent of [dependencies]: the row can be live while depending
 *   on nothing, which is what the per-card rows need when the signal is outside the ring.
 */
@Composable
private fun ContentChoice(
    selected: Int,
    title: Int,
    summaryRes: Int?,
    options: List<Int>,
    dependencies: List<Pair<Boolean, Int>>,
    enabled: Boolean = true,
    blockedIndices: Set<Int> = emptySet(),
    onChange: (Int) -> Unit,
) {
    ContentGate(dependencies) {
        if (blockedIndices.isEmpty()) {
            GlassDropdownPreference(
                items = options.map { stringResource(it) },
                selectedIndex = selected,
                title = stringResource(title),
                summary = summaryRes?.let { stringResource(it) },
                insideMargin = SettingsItemMargin,
                enabled = enabled && dependencies.all { it.first },
                onSelectedIndexChange = onChange,
            )
        } else {
            GlassDropdownPreference(
                entry = DropdownEntry(
                    items = options.mapIndexed { index, res ->
                        DropdownItem(
                            text = stringResource(res),
                            enabled = index !in blockedIndices,
                            selected = index == selected,
                            // The list carries its own per-item state, so each entry has
                            // to report its own choice back.
                            onClick = { onChange(index) },
                        )
                    },
                    enabled = enabled && dependencies.all { it.first },
                ),
                title = stringResource(title),
                summary = summaryRes?.let { stringResource(it) },
                insideMargin = SettingsItemMargin,
                enabled = enabled && dependencies.all { it.first },
            )
        }
    }
}

@Composable
private fun ContentGate(dependencies: List<Pair<Boolean, Int>>, content: @Composable () -> Unit) {
    val missing = dependencies.firstOrNull { !it.first }
    val hint = missing?.let { stringResource(R.string.gate_hint, stringResource(it.second)) }
    TooltipBox(text = hint.orEmpty(), enabled = hint != null, content = content)
}
