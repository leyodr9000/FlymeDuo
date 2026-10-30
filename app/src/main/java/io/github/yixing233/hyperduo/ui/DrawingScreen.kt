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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.yixing233.hyperduo.Prefs
import io.github.yixing233.hyperduo.R
import io.github.yixing233.hyperduo.TrioAppearance
import io.github.yixing233.hyperduo.TrioPreviewView
import io.github.yixing233.hyperduo.TrioSettings
import io.github.yixing233.hyperduo.TrioZones
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.CircleDot
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.X
import top.yukonga.miuix.kmp.basic.IconButton
import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File

/**
 * The drawing page: the glyph rendered large, with its three parts tappable.
 *
 * <p>This is the page that answers "which switch draws the thing I am looking
 * at". Everywhere else in this app the settings are a list of switches named after
 * pieces; here the pieces are the interface, and a switch is what a tap on one
 * does.
 *
 * <p>It is a page of its own rather than a mode of the General tab because it
 * needs room: the targets are thirds of the glyph, so the glyph has to be large
 * enough that a finger can land inside one third on purpose. The General tab's
 * preview strip is about forty dp a cell - a comparison aid, not an input surface.
 *
 * <h2>What a tap does</h2>
 *
 * <p>Tap a third and a list opens of the components that could be drawn there; pick
 * one and it is placed, displacing whatever was there. The list is not a fixed table -
 * it is whatever [TrioZones.choices] reports as placeable in that zone, and choosing
 * an entry writes the switch combination [TrioZones.assign] solved for. Both live in
 * {@code TrioZones} rather than here because they have to agree with where the pieces
 * are actually drawn, and both are checked against real rendered pixels by
 * {@code tests/drawing-zones.ps1}.
 *
 * <p>The first entry is "nothing here", which empties the zone.
 *
 * <p>Long-press on a row opens that component's own size and colour settings, from
 * the same row table the size page renders, so the two places cannot offer
 * different ranges for one setting.
 */

/** Row rhythm inside the editor card, matching the rest of the settings screen. */
private val DrawingItemMargin = PaddingValues(horizontal = 18.dp, vertical = 16.dp)

/**
 * Outline inks for the three zones.
 *
 * <p>Distinct hues rather than one colour, so the guide beside the glyph and the
 * outline over it identify a third by colour as well as by name. Drawn at a
 * fraction of full alpha because the outline sits over the glyph and must not
 * compete with its ink.
 */
private val ZoneInkTop = Color(0x8093C5FD)
private val ZoneInkCentre = Color(0x80A6E3A1)
private val ZoneInkBottom = Color(0x80F9E2AF)

/**
 * The battery and signal states the preview can be put into.
 *
 * <p>This exists because three of the six pieces are only reachable in a state the
 * phone is not usually in: the charging bolt needs a charger, the low and critical
 * colours need a low battery, and the dual rows need two answered SIMs. Without a
 * way to ask for those, those pieces would have no tappable presence at all on
 * this page.
 *
 * <p>Each carries the whole state rather than a flag, because they are mutually
 * exclusive: a frame cannot be both charging and critical, and modelling them as
 * independent toggles would allow combinations the renderer has no look for.
 */
private enum class PreviewScene(
    val labelRes: Int,
    val level: Int,
    val charging: Boolean = false,
    val quickCharging: Boolean = false,
    val powerSave: Boolean = false,
    val low: Boolean = false,
    val wifiLevel: Int = WIFI_ON_LEVEL,
    val slotLevels: IntArray? = null,
) {
    Normal(R.string.drawing_scene_normal, 64),
    Charging(R.string.drawing_scene_charging, 88, charging = true),
    QuickCharging(R.string.drawing_scene_quick, 88, charging = true, quickCharging = true),
    Low(R.string.drawing_scene_low, 24, powerSave = true, low = true),
    Critical(R.string.drawing_scene_critical, 12, low = true),
    NoWifi(R.string.drawing_scene_no_wifi, 79, wifiLevel = NO_WIFI_LEVEL),
}

/** The Wi-Fi level the preview shows: mid-strength, so arcs are drawn. */
private const val WIFI_ON_LEVEL = 3

/** What the renderer reads as "no Wi-Fi ink": the percentage drops to the centre. */
private const val NO_WIFI_LEVEL = -1

/** Mobile level the preview shows: full, so the meter is legible. */
private const val MOBILE_LEVEL = 4

/**
 * Per-SIM levels, so the dual reading draws two rows of different heights.
 *
 * <p>SIM 1 one step down from SIM 2, matching the settings screen's own preview
 * strip. Equal levels would make the dual switch invisible in the rectangular
 * arrangement, where the reading only changes which levels the two columns carry.
 */
private val PREVIEW_SLOTS = intArrayOf(2, 4)

/** The same synthetic SIM readings feed the renderer and the placement solver. */
private val PreviewScene.previewSlots: IntArray
    get() = slotLevels ?: PREVIEW_SLOTS

/**
 * The network-type label the preview stands in for the real one.
 *
 * <p>Deliberately the "5GA" form rather than "5G": the trailing A is what the
 * suffix-scale row changes, so it is the only form in which that setting can be
 * seen at all.
 */
private const val PREVIEW_TYPE = "5GA"

/** Last placement, so a runtime override can be explained beside the preview. */
private data class ZoneSelection(val zone: TrioZones.Zone, val part: TrioZones.Part)

/**
 * The drawing page's body.
 *
 * @param settings the live snapshot, read at composition so a write is visible on
 *   the next frame: the page and the glyph have to agree about what is switched on,
 *   and the glyph is drawn from this same object.
 */
internal fun LazyListScope.drawingTab(
    settings: TrioSettings,
    update: ((SettingsRepository) -> Unit) -> Unit,
    onEditColor: (RoleColor) -> Unit,
    onResetColors: () -> Unit,
    /**
     * The stored snapshot for a given arrangement, used by the style tiles.
     *
     * <p>A plain lambda rather than handing the whole repository down: the page only ever
     * needs to READ one other snapshot, and a read accessor is what keeps that dependency
     * from turning into "the page may write anything".
     */
    snapshotForStyle: (Int) -> TrioSettings,
) {
    item {
        DrawingEditorCard(
            settings = settings,
            update = update,
            onEditColor = onEditColor,
            onResetColors = onResetColors,
            snapshotForStyle = snapshotForStyle,
        )
    }
}

/**
 * The whole editor: what each third holds, the large glyph, and the state selector.
 *
 * <p>[settings] is the live snapshot rather than a local copy - a tap calls
 * straight through to the repository and the parent recomposes with the new value -
 * so there is no second model here to fall out of step with what the status bar
 * will draw.
 */
@Composable
private fun DrawingEditorCard(
    settings: TrioSettings,
    update: ((SettingsRepository) -> Unit) -> Unit,
    onEditColor: (RoleColor) -> Unit,
    onResetColors: () -> Unit,
    snapshotForStyle: (Int) -> TrioSettings,
) {
    // Which component's settings dialog is open, if any. The named enum rather than
    // the raw part, so the dialog can be titled with the row name the rest of the
    // screen uses for it.
    var editing by remember { mutableStateOf<TrioSettingsPart?>(null) }
    var sheetVisible by remember(editing) { mutableStateOf(editing != null) }
    var pendingColor by remember { mutableStateOf<RoleColor?>(null) }
    var pendingReset by remember { mutableStateOf(false) }
    // The zone whose picker is open, if any. One at a time: the picker is a dialog,
    // and two stacked pickers would be a state with no sensible dismissal order.
    var picking by remember { mutableStateOf<TrioZones.Zone?>(null) }
    // The capsule container's own sheet: its five sliders live there, one row away.
    var containerSheet by remember { mutableStateOf(false) }
    // The third under the finger, so the guide can outline what a press would open.
    var pressed by remember { mutableStateOf<TrioZones.Zone?>(null) }
    var scene by rememberSaveable { mutableStateOf(PreviewScene.Normal) }
    var previewTheme by rememberSaveable { mutableStateOf(0) }
    var lastSelection by remember { mutableStateOf<ZoneSelection?>(null) }

    val appearance = TrioAppearance.of(settings)
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val dark = when (previewTheme) { 1 -> false; 2 -> true; else -> systemDark }

    // This page previews synthetic readings, independently of the switch being
    // edited. Counting the switch would make enabling the second row unreachable.
    val sims = scene.previewSlots.count { it >= 0 }
    val ringSettings = TrioZones.editableRingSettings(settings, scene.level, scene.charging,
        scene.wifiLevel, PREVIEW_TYPE, sims)
    // Which cell the page-level tabs show. Kept in the page rather than in a dialog so the
    // glyph above stays visible while the order is edited - which is the point of editing it.
    var openZone by remember { mutableStateOf(TrioZones.Zone.CENTRE) }
    val chooseZone: (TrioZones.Zone) -> Unit = { zone ->
        // One path for every zone now. The capsule's left column used to be special-cased
        // straight to the clock's settings, because the clock was pinned there and could not
        // be moved; with four cells the clock is an ordinary piece, so tapping its cell opens
        // that cell's picker like any other.
        openZone = zone
        pressed = zone
        picking = zone
    }
    /**
     * Edit one zone's priority list.
     *
     * <p>[delta] is {@code -1}/{@code +1} to move an entry up or down, and {@code 0} to drop
     * that single entry. Dropping is deliberately not "clear the zone": when the head is
     * removed the candidate behind it takes over, which is the whole point of a fallback
     * chain.
     */
    val reorderZone: (TrioZones.Zone, Int, Int) -> Unit = { zone, index, delta ->
        // A stale row (the list shrank since it was drawn) resolves to a no-op rather than
        // writing a list built from an index that no longer exists.
        val next = if (delta == 0) {
            TrioZones.withoutAt(settings, zone.ordinal, index)
        } else {
            TrioZones.moveWithin(settings, zone.ordinal, index, delta)
        }
        if (next !== settings) {
            update { r -> applyZoneAssignment(next, r, settings) }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DrawingSection(R.string.drawing_master_title, PaddingValues(0.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ContentControlRow(ContentControl.Enabled, settings, update)
                // Scope and overall size sit at the master's level: both decide
                // where the icon appears and how large it is, which is the same
                // question the switch above answers, not a component detail.
                ContentControlRow(ContentControl.Exclude, settings, update)
                GlyphScaleHomeRow(settings, update)
                // The same lever for the type label. 5G-Advanced coverage is rare enough
                // that a report about it cannot be reproduced on demand, so the label is
                // substitutable: if the simulated one draws correctly, the fault is in what
                // the platform sampled rather than in how the glyph renders it.
                SwitchPreference(
                    checked = settings.demoMobileType,
                    title = stringResource(R.string.demo_mobile_type_title),
                    summary = stringResource(R.string.demo_mobile_type_summary),
                    insideMargin = SettingsItemMargin,
                    onCheckedChange = { value -> update { it.setDemoMobileType(value) } },
                )
                // The stock label cannot say 5GA on builds where config_display_5g_a is
                // false: Flyme never asks for the 5GA resource, so the module would copy
                // plain 5G forever. This reads the radio state instead.
                SwitchPreference(
                    checked = settings.typeFollowNrAdvanced,
                    title = stringResource(R.string.type_follow_nr_advanced_title),
                    summary = stringResource(R.string.type_follow_nr_advanced_summary),
                    insideMargin = SettingsItemMargin,
                    onCheckedChange = { value -> update { it.setTypeFollowNrAdvanced(value) } },
                )
                // The pair and everything that positions it, as one row that opens a sheet:
                // these settings only mean anything together.
                DualStackGroup(settings, update)
            }
        }

        DrawingSection(R.string.drawing_quick_edit_title) {
            DrawingStyleSelector(
                settings = settings,
                scene = scene,
                dark = dark,
                snapshotForStyle = snapshotForStyle,
                onSelect = { style ->
                    lastSelection = null
                    update { it.setTrioStyle(style) }
                },
            )
            Text(
                text = stringResource(R.string.drawing_state_title),
                style = MiuixTheme.textStyles.subtitle,
            )
            SceneSelector(
                selected = scene,
                onSelect = { scene = it },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(listOf(R.string.drawing_theme_system, R.string.drawing_theme_light,
                    R.string.drawing_theme_dark).withIndex().toList(), key = { it.index }) { entry ->
                    SceneChip(stringResource(entry.value), previewTheme == entry.index) {
                        previewTheme = entry.index
                    }
                }
            }

            DrawingGlyph(
                settings = settings,
                scene = scene,
                dark = dark,
                pressed = pressed,
                onPressChange = { pressed = it },
                // A press on the glyph opens that zone's picker: the request is not
                // "flip something" but "put something else here", so pointing at the
                // zone is choosing which slot to fill, and the list follows.
                onPickZone = chooseZone,
                onEditZone = { zone ->
                    val part = TrioZones.occupant(
                        zone, appearance, scene.level, scene.charging,
                        scene.wifiLevel, PREVIEW_TYPE, sims,
                    )
                    if (part == null) {
                        picking = zone
                    } else {
                        editing = TrioSettingsPart.of(part)
                    }
                },
                onEditBattery = { editing = TrioSettingsPart.Battery },
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            )

            // The capsule container's shape, under Quick Edit because that is where the style
            // tiles are: picking the capsule and then sizing it are the same decision, and the
            // component-order section is about where pieces GO, not what holds them. Behind a
            // row rather than inline - five sliders would double this section - and in the same
            // sheet a component uses, since it answers the same kind of question.


            if (!appearance.glyph) {
                Text(
                    text = stringResource(R.string.drawing_master_off),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }

        // The capsule container, in the same shape as the About page's update row: a card of
        // its own holding one ArrowPreference, whose insideMargin is the row's inset. It is
        // NOT inside a DrawingSection, because that card already insets its content by the
        // same 18dp - nesting the two added up and made the row read narrower than any other
        // row on the screen.
        if (appearance.capsule) {
            Card {
                ArrowPreference(
                    title = stringResource(R.string.capsule_container_title),
                    summary = stringResource(R.string.capsule_container_summary),
                    insideMargin = DrawingItemMargin,
                    onClick = { containerSheet = true },
                )
            }
        }

        DrawingSection(R.string.drawing_order_title) {
            // The strip lists the zones this arrangement actually has: three for the ring and
            // the rectangle, four for the capsule (left, upper-right, lower-right, dots).
            // Built from `zonesFor` rather than from Zone.entries so the capsule's extra
            // cells cannot leak into a ring's strip.
            val zones = zonesFor(appearance)
            // Miuix's own outlined tab row, with nothing overridden. The page used to
            // wrap this in a hand-drawn glass capsule (backdrop, blur, lens, contour
            // border) to match the shell's floating bar, but that made the tabs a
            // second material next to the cards they sit inside: the component's own
            // contour already IS the outline, and re-drawing it by hand is one more
            // copy of a shape the library ships.
            TabRowWithContour(
                tabs = zones.map { zone -> stringResource(zoneLabelRes(zone, appearance)) },
                selectedTabIndex = zones.indexOf(openZone).coerceAtLeast(0),
                onTabSelected = { index ->
                    openZone = zones[index]
                    pressed = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            )

            val chain = appearance.ringZones[openZone.ordinal]
            // Shown whenever the signal is out of ring, not only when a signal row happens to
            // be in this zone's list. Two different things go wrong without it, and both read
            // as "the module stopped working":
            //
            //   1. a signal row IS listed here but draws nothing, because the reading moved
            //      to the status bar - the list claims it is on screen when it is not;
            //   2. nothing is listed here yet, and the user is about to add a signal row that
            //      will never appear - which is worse, because there is no evidence at all.
            //
            // One sentence covers both: it says where the signal is, so "nothing here draws
            // dots" stops being a mystery in either case.
            if (appearance.signalOutOfRing) {
                SignalOutOfRingNotice()
            }
            val actual = TrioZones.occupant(
                openZone, appearance, scene.level, scene.charging,
                scene.wifiLevel, PREVIEW_TYPE, sims,
            )
            Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                if (chain.isEmpty()) {
                    Text(
                        text = stringResource(R.string.drawing_priority_empty_summary),
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    )
                }
                chain.forEachIndexed { index, id ->
                    val part = TrioZones.decodePart(id) ?: return@forEachIndexed
                    PriorityPreferenceRow(
                        index = index,
                        part = part,
                        lastIndex = chain.size - 1,
                        isDrawing = part == actual,
                        onMove = { i, delta -> reorderZone(openZone, i, delta) },
                        onRemove = { reorderZone(openZone, index, REMOVE) },
                        // The same sheet the library opens, so the two entrances cannot
                        // disagree about what a piece's settings are.
                        onEdit = { TrioSettingsPart.of(part)?.let { editing = it } },
                    )
                }
            }

            lastSelection?.let { selection ->
                PlacementFeedback(selection, appearance, scene, sims)
            }

            Button(
                onClick = { picking = openZone },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.drawing_priority_add))
            }


        }

        DrawingSection(R.string.drawing_outside_title) {
            DrawingOutsideParts(settings, scene, dark,
                onEdit = { editing = it },
                onReorder = { order -> update { it.setOutOrder(order) } },
            )
        }

    }


    // The container sheet. Same surface as a component's, because it answers the same kind
    // of question - and it needs the same inset handling, or the last slider sits under the
    // gesture bar.
    if (containerSheet) {
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        OverlayBottomSheet(
            show = true,
            title = stringResource(R.string.capsule_container_title),
            onDismissRequest = { containerSheet = false },
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = bottomInset)) {
                CapsuleContainerRows(settings, update, Modifier.padding(horizontal = 4.dp))
            }
        }
    }

    val pickZone = picking
    if (pickZone != null) {
        ZonePickerDialog(
            zone = pickZone,
            settings = settings,
            appearance = appearance,
            scene = scene,
            sims = sims,
            onChoose = { part ->
                // Choosing a piece puts it at the FRONT of this zone's priority list; the
                // pieces already there stay behind it as fallbacks. Choosing "empty" removes
                // that one entry, so the next candidate is revealed rather than the zone
                // being wiped.
                val next = TrioZones.withPiece(settings, pickZone.ordinal, part)
                update { r -> applyZoneAssignment(next, r, settings) }
                lastSelection = part?.let { ZoneSelection(pickZone, it) }
                picking = null
            },
            onEdit = { part ->
                picking = null
                editing = TrioSettingsPart.of(part)
            },
            onDismiss = { picking = null },
        )
    }

    val current = editing
    if (current != null) {
        PartSettingsSheet(
            show = sheetVisible,
            part = current,
            settings = settings,
            ringSettings = ringSettings,
            update = update,
            // The colour picker is the page's own, opened over this dialog: the
            // dialog closes and the picker takes its place, which is the stacking
            // the colour page already uses. Two dialogs at once would be a state
            // the rest of the screen has no way to present.
            onEditColor = { role ->
                pendingColor = role
                sheetVisible = false
            },
            onResetColors = {
                pendingReset = true
                sheetVisible = false
            },
            preview = {
                Column {
                    DrawingSettingsPreview(current, settings, scene, dark)
                    SceneSelector(scene, { scene = it }, Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 16.dp))
                }
            },
            onDismiss = { sheetVisible = false },
            onDismissFinished = {
                editing = null
                pendingColor?.let { role ->
                    pendingColor = null
                    onEditColor(role)
                }
                if (pendingReset) {
                    pendingReset = false
                    onResetColors()
                }
            },
        )
    }
}

/** A labelled card using the same section rhythm as the settings page. */
@Composable
private fun DrawingSection(
    titleRes: Int,
    contentPadding: PaddingValues = DrawingItemMargin,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column {
        SmallTitle(
            text = stringResource(titleRes),
            modifier = Modifier.padding(top = 4.dp),
            insideMargin = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        )
        Card {
            Column(modifier = Modifier.fillMaxWidth().padding(contentPadding), content = content)
        }
    }
}

/**
 * The components page: one row per component.
 *
 * <p>Emitted into the host list as a single item, like the drawing page, so the list that
 * scrolls it is the same one that scrolls every other page.
 */
@Composable
internal fun PartsPage(
    settings: TrioSettings,
    onEdit: (TrioSettingsPart) -> Unit,
    installingFont: Boolean,
    onChooseFont: () -> Unit,
    onClearFont: (String) -> Unit,
) {
    val a = TrioAppearance.of(settings)
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        partsForPage(settings).forEach { part ->
            PartRow(part, settings, onClick = { onEdit(part) })
        }

        // The face the glyph's text is drawn in. Here rather than on the About page: it is a
        // property of the ink, and About answers questions about the BUILD (version, framework,
        // scope) rather than about how the glyph looks.
        SmallTitle(
            text = stringResource(R.string.group_font),
            modifier = Modifier.padding(top = 4.dp),
            insideMargin = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        )
        Card {
            val chosen = settings.fontPath
            ArrowPreference(
                title = stringResource(R.string.font_title),
                // The file name, not a bare "chosen": it is how a user tells two fonts
                // apart when comparing them, and the full path is far too long to show.
                summary = when {
                    installingFont -> stringResource(R.string.font_installing)
                    chosen.isEmpty() -> stringResource(R.string.font_summary_system)
                    else -> File(chosen).name
                },
                insideMargin = SettingsItemMargin,
                // Disabled while a copy is in flight: a second pick would race the first
                // one's file out from under it.
                enabled = !installingFont,
                onClick = onChooseFont,
            )
            // Always present, greyed out when there is nothing to clear.
            val nothingToClear = chosen.isEmpty()
            ArrowPreference(
                title = stringResource(R.string.font_clear_title),
                summary = if (nothingToClear) {
                    stringResource(R.string.font_clear_summary_idle)
                } else {
                    stringResource(R.string.font_clear_summary)
                },
                insideMargin = SettingsItemMargin,
                enabled = !nothingToClear && !installingFont,
                onClick = { onClearFont(chosen) },
            )
        }

    }
}

/** The components this arrangement can hold, in the order the page lists them. */
/**
 * The components this arrangement can hold, in page order.
 *
 * <p>Takes the SETTINGS, not an appearance: it has to be callable from a LazyListScope, which
 * is not a composable scope, and the only thing it needs is which arrangement is in force.
 */
internal fun partsForPage(settings: TrioSettings): List<TrioSettingsPart> {
    // Only the clock depends on the arrangement: the ring and the capsule both draw one, the
    // rectangle has no slot for it. Read from the raw style rather than from TrioAppearance -
    // that class is resolved by a composable, and this runs inside a LazyListScope.
    val style = Prefs.normalizeTrioStyle(settings.trioStyle)
    val slotted = style == Prefs.STYLE_RING || style == Prefs.STYLE_CAPSULE
    return listOf(TrioSettingsPart.Appearance, TrioSettingsPart.Battery, TrioSettingsPart.Wifi,
        TrioSettingsPart.Mobile, TrioSettingsPart.Value, TrioSettingsPart.Bolt,
        TrioSettingsPart.Type, TrioSettingsPart.Readouts) +
        if (slotted) listOf(TrioSettingsPart.Clock) else emptyList()
}

@Composable
private fun PartRow(
    part: TrioSettingsPart,
    settings: TrioSettings,
    onClick: () -> Unit,
) {
    val a = TrioAppearance.of(settings)
    // The row is its NAME and nothing else. A summary was tried - the first size row, then a
    // placeholder for the cards that only hold colours - and both were noise: the number is
    // one tap away in the sheet the row opens, and "this one has no size" is a sentence about
    // the absence of a feature, which is not what a list of components is for.
    Card {
        ArrowPreference(
            title = stringResource(if (part == TrioSettingsPart.Battery && a.ring)
                R.string.drawing_part_ring else drawingPartLabel(part)),
            summary = null,
            insideMargin = SettingsItemMargin,
            onClick = onClick,
        )
    }
}

private fun drawingPartLabel(part: TrioSettingsPart): Int = when (part) {
    TrioSettingsPart.Appearance -> R.string.drawing_part_appearance
    TrioSettingsPart.Readouts -> R.string.drawing_part_readouts
    else -> part.part?.let(::occupantLabelRes) ?: part.labelRes
}

@Composable
private fun DrawingStyleSelector(
    settings: TrioSettings, scene: PreviewScene, dark: Boolean,
    snapshotForStyle: (Int) -> TrioSettings, onSelect: (Int) -> Unit,
) {
    val styles = listOf(Prefs.STYLE_RING, Prefs.STYLE_CAPSULE)
    val labels = listOf(R.string.trio_style_ring, R.string.trio_style_capsule)
    Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        styles.forEachIndexed { index, style ->
            val chosen = settings.trioStyle == style
            // Each arrangement keeps its own components and layout now, so the tile that is
            // NOT selected has to draw THAT arrangement's pieces. Copying the current snapshot
            // and only relabelling its shape would preview a look clicking it does not
            // produce - and for the capsule that is a visible lie, since its openings and
            // body are filled by a different set of pieces.
            val sample = remember(style, settings.trioStyle) {
                settings.withComponentsFrom(snapshotForStyle(style), style)
            }
            Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                .background(if (chosen) MiuixTheme.colorScheme.primaryContainer else MiuixTheme.colorScheme.surfaceContainerHigh)
                .border(if (chosen) 2.dp else 0.dp, if (chosen) MiuixTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(12.dp))
                .semantics { selected = chosen }
                .clickable(enabled = settings.enabled, role = Role.RadioButton) { onSelect(style) }
                .padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                DrawingSmallPreview(sample, scene, dark, Modifier.fillMaxWidth().height(64.dp))
                Text(stringResource(labels[index]), style = MiuixTheme.textStyles.footnote1,
                    color = if (chosen) MiuixTheme.colorScheme.onPrimaryContainer else MiuixTheme.colorScheme.onSurface)
            }
        }
    }
}

/**
 * Puts one [TrioPreviewView] into the state a preview wants.
 *
 * <p>Called from BOTH the factory and the update block, which is the point. A Compose
 * {@code AndroidView} runs {@code factory} once and {@code update} only on a
 * recomposition of that node; a LazyColumn item whose parent does not recompose can
 * therefore live its whole life with the state its factory gave it. The factory above
 * used to construct the view and leave it at its defaults - an empty ring - so the
 * preview showed the shipped look instead of the user's, and every settings tile drew
 * the same thing whatever they configured. One function, called from both places, is
 * what makes "the preview shows what you picked" true rather than likely.
 */
private fun applyPreviewState(
    view: TrioPreviewView,
    settings: TrioSettings,
    scene: PreviewScene,
    dark: Boolean,
    /**
     * Ink for the press highlight, or 0 to leave the view's own default alone.
     *
     * <p>Passed in rather than fixed in the view: the preview follows the system theme, and
     * the highlight was a fixed white - invisible on the light surface it spends most of
     * its life on. The callers pass the theme's own primary, so the region under the
     * finger is the same colour as every other "this is selected" in the app.
     */
    accent: Int = 0,
    outType: Boolean = false,
    outSignal: Boolean = false,
    highlight: TrioZones.Zone? = null,
) {
    if (accent != 0) {
        view.setHighlightColors(accent)
    }
    view.setSettings(settings)
    view.setOutTypeOnly(outType)
    view.setOutSignalOnly(outSignal)
    view.setHighlight(highlight)
    view.setPreviewBackground(if (dark) 0xFF1C1B1F.toInt() else 0xFFF2F2F7.toInt())
    view.setForeground(if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt())
    view.setState(scene.level, scene.charging, scene.quickCharging, scene.powerSave,
        scene.low, scene.wifiLevel, MOBILE_LEVEL, scene.previewSlots, PREVIEW_TYPE)
}

@Composable
private fun DrawingSmallPreview(
    settings: TrioSettings, scene: PreviewScene, dark: Boolean, modifier: Modifier,
    outType: Boolean = false, outSignal: Boolean = false,
) {
    AndroidView(
        modifier = modifier,
        // The state is applied here as well as in update: see applyPreviewState.
        factory = { context ->
            TrioPreviewView(context).also {
                applyPreviewState(it, settings, scene, dark, outType = outType,
                    outSignal = outSignal)
            }
        },
        update = { view ->
            applyPreviewState(view, settings, scene, dark, outType = outType,
                outSignal = outSignal)
        },
    )
}

/**
 * The sample at the top of a component's settings sheet.
 *
 * <p>Both branches clip to the same shape as the cards this sheet is made of: the preview
 * is an {@code AndroidView} that paints its own background, and a Compose {@code clip}
 * alone does not reach inside it - the view rounds its own corners, and this keeps the
 * Compose layer from showing a square edge underneath the 12dp it rounds to.
 */
@Composable
private fun DrawingSettingsPreview(
    part: TrioSettingsPart, settings: TrioSettings, scene: PreviewScene, dark: Boolean,
) {
    val a = TrioAppearance.of(settings)
    val shape = RoundedCornerShape(12.dp)
    if (part == TrioSettingsPart.Readouts) {
        DrawingReadoutSample(settings, dark, Modifier.fillMaxWidth().height(92.dp))
        Text(stringResource(R.string.drawing_readout_sample_summary),
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(vertical = 4.dp))
    } else {
        DrawingSmallPreview(settings, scene, dark,
            Modifier.fillMaxWidth().height(92.dp).clip(shape),
            outType = part == TrioSettingsPart.Type && a.typeOutOfRing,
            // The cell follows what the signal actually is this frame: the stacked reading
            // only exists out of ring with its switch on. In every other case the glyph
            // itself is the right sample, so this stays false.
            outSignal = part == TrioSettingsPart.Mobile && a.stackedOut(),
        )
    }
}

/**
 * A live sample of the two performance readouts.
 *
 * <p>The sample always shows a value for every readout that is switched on, even when the
 * current preview scene has nothing to report - the power reading only exists while
 * charging, and the default scene is not charging, so keying the sample to the scene left
 * this cell permanently blank.
 *
 * <p>The numbers (`9.6W`, `32°C`) are fixed stand-ins: a sample of the type, not a claim
 * about the device.
 *
 * <p><b>One fixed size, and the text is always whole.</b> The sample is not a measurement of
 * the status bar - the status bar is live, so a size change is visible there the moment the
 * slider moves. What this cell has to do is show <em>which pieces</em> are in the strip and
 * <em>roughly how they sit together</em>, so it draws one legible size and lets the longest
 * line decide it. Deriving the size from `outTypeSize` was tried and rejected: it made this
 * cell disagree with the network-type cell next to it, and at the sizes that fit the height
 * the text ran past the width and was silently clipped - "9.6W" showed as "9.6".
 *
 * <p>The weight is the reading's own, because that one is worth previewing: it is the only
 * property that changes the shape of the text rather than just its size.
 */
@Composable
private fun DrawingReadoutSample(settings: TrioSettings, dark: Boolean, modifier: Modifier) {
    val a = TrioAppearance.of(settings)
    // With neither switch on there is nothing to report, but the cell still has to show
    // the type it sizes: one line, because the single-reading size is what the
    // "overall reading size" row acts on when only one reading exists.
    val readings = listOfNotNull(
        if (settings.perfPower) "9.6W" else null,
        if (settings.perfTemp) "32°C" else null,
    ).ifEmpty { listOf("32°C") }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier.clip(RoundedCornerShape(12.dp))
        .background(if (dark) Color(0xFF1C1B1F) else Color(0xFFF2F2F7)), contentAlignment = Alignment.Center) {
        // Fit the cell on both axes, measured rather than estimated: the widest line at a
        // trial size gives the width factor, and the number of lines gives the height one.
        // Whatever the longer constraint says wins, so nothing is ever clipped.
        val longest = readings.maxByOrNull { it.length }.orEmpty()
        val probe = if (longest.isEmpty()) {
            null
        } else {
            textMeasurer.measure(
                text = longest,
                style = TextStyle(
                    fontSize = 100.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight(a.perfTypeWeight()),
                ),
                maxLines = 1,
            )
        }
        // 100sp was the trial size, so a single glyph-run's width per unit of font size is
        // its measured width over 100. Margins keep the text clear of the rounded corners.
        val widthPerUnit = probe?.let { with(density) { it.size.width.toDp().value } / 100f } ?: 0f
        val byWidth = if (widthPerUnit > 0f) maxWidth.value * 0.86f / widthPerUnit else 0f
        val byHeight = maxHeight.value * 0.82f / (readings.size * 1.35f)
        val size = minOf(
            if (byWidth > 0f) byWidth else byHeight,
            byHeight,
        )
        Text(readings.joinToString("\n"), color = if (dark) Color.White else Color.Black,
            fontSize = with(density) { size.dp.toSp() },
            lineHeight = with(density) { (size * 1.3f).dp.toSp() },
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight(a.perfTypeWeight()), textAlign = TextAlign.Center)
    }
}

/**
 * The three out-of-ring pieces, each as a live sample.
 *
 * <p>A cell shows "hidden" only when the piece <em>cannot</em> be previewed at all - the
 * network type when it is set to draw inside the ring. A switch that is merely off is not
 * a reason to blank the cell: these samples exist so the size and weight rows have
 * something to act on, and a user who has just switched a reading off still needs to see
 * what it will look like before switching it back on. The two readouts already say so
 * themselves, in [DrawingReadoutSample]'s own empty state.
 */
/**
 * The three out-of-ring pieces, positioned the way the status bar actually lays them out.
 *
 * <p><b>The row runs left to right and the RIGHTmost tile is the one next to the battery.</b>
 * That is not a cosmetic choice: {@code layoutOutRingStrip} measures every slot from the
 * battery outwards, so the stored order's first entry is the piece nearest the battery and
 * therefore the rightmost one on screen in LTR. Drawing the list left to right in stored
 * order - which is what this card first did - showed the arrangement mirrored, so the arrows
 * moved a piece the opposite way from the tile the user pressed.
 *
 * <p>The stored array stays in "nearest the battery first" order, because that is what the
 * layout consumes. Only the <em>rendering</em> is reversed, and only here: making the storage
 * order screen order would push the mirror image into every other reader.
 *
 * <p><b>The tiles are ordered, and that is the control.</b> Showing the samples in the shipped
 * order would make the setting invisible: the user would move a piece, read "first", and still
 * be looking at the same three tiles.
 *
 * <p>Which piece survives a narrow row is separate and deliberately not offered here: that is
 * a judgement about what a reading is worth ([Prefs.OUT_PRIORITY]), not a preference.
 */
@Composable
private fun DrawingOutsideParts(
    settings: TrioSettings, scene: PreviewScene, dark: Boolean,
    onEdit: (TrioSettingsPart) -> Unit, onReorder: (IntArray) -> Unit,
) {
    val a = TrioAppearance.of(settings)
    val order = settings.outOrder
    // Screen order: outermost (furthest from the battery) on the left, nearest on the right.
    val onScreen = order.reversedArray()
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            onScreen.forEachIndexed { screenIndex, slot ->
                val part = trioSettingsPartFor(slot) ?: return@forEachIndexed
                // The stored index this tile occupies. Moving "towards the battery" means a
                // LOWER stored index, which is one step to the RIGHT on screen.
                val stored = order.size - 1 - screenIndex
                Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                    .clickable(role = Role.Button) { onEdit(part) }.padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    val previewable = when (part) {
                        // The type has exactly one reason to be unpaintable here: it is set to
                        // draw inside the ring, so there is no out-of-ring label to show.
                        TrioSettingsPart.Type -> a.typeOutOfRing
                        // Both of these can always be drawn as a sample.
                        else -> true
                    }
                    if (!previewable) {
                        Box(Modifier.fillMaxWidth().height(58.dp), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.drawing_preview_hidden), style = MiuixTheme.textStyles.footnote2,
                                textAlign = TextAlign.Center)
                        }
                    } else if (part == TrioSettingsPart.Readouts) {
                        DrawingReadoutSample(settings, dark, Modifier.fillMaxWidth().height(58.dp))
                    } else {
                        DrawingSmallPreview(settings, scene, dark, Modifier.fillMaxWidth().height(58.dp),
                            outType = part == TrioSettingsPart.Type, outSignal = part == TrioSettingsPart.Mobile)
                    }
                    Text(stringResource(drawingPartLabel(part)), style = MiuixTheme.textStyles.footnote2,
                        textAlign = TextAlign.Center)
                    OutOrderArrows(
                        // Right = towards the battery, left = outwards, matching the tiles.
                        canMoveRight = stored > 0,
                        canMoveLeft = stored < order.size - 1,
                        onMoveRight = { onReorder(swapped(order, stored, stored - 1)) },
                        onMoveLeft = { onReorder(swapped(order, stored, stored + 1)) },
                    )
                }
            }
        }
        // Which end is the battery is the one thing a horizontal row cannot say by itself.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.out_order_outwards),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Text(
                text = stringResource(R.string.out_order_battery),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** One tile's left/right pair, disabled at the ends rather than hidden. */
@Composable
private fun OutOrderArrows(
    canMoveRight: Boolean,
    canMoveLeft: Boolean,
    onMoveRight: () -> Unit,
    onMoveLeft: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OrderButton(
            icon = Lucide.ChevronLeft,
            description = R.string.out_order_move_out,
            enabled = canMoveLeft,
        ) { onMoveLeft() }
        OrderButton(
            icon = Lucide.ChevronRight,
            description = R.string.out_order_move_in,
            enabled = canMoveRight,
        ) { onMoveRight() }
    }
}

/** {@code order} with two entries exchanged; a copy, so the snapshot stays immutable. */
private fun swapped(order: IntArray, from: Int, to: Int): IntArray {
    val next = order.copyOf()
    if (from !in next.indices || to !in next.indices) return next
    val held = next[from]
    next[from] = next[to]
    next[to] = held
    return next
}

/** The tile a stored {@code OUT_SLOT_*} id stands for. */
private fun trioSettingsPartFor(slot: Int): TrioSettingsPart? = when (slot) {
    Prefs.OUT_SLOT_SIGNAL -> TrioSettingsPart.Mobile
    Prefs.OUT_SLOT_READOUTS -> TrioSettingsPart.Readouts
    Prefs.OUT_SLOT_TYPE -> TrioSettingsPart.Type
    else -> null
}

/** Explain the saved component using the same runtime frame as the visible glyph. */
@Composable
private fun PlacementFeedback(
    selection: ZoneSelection,
    appearance: TrioAppearance,
    scene: PreviewScene,
    sims: Int,
) {
    val actual = TrioZones.occupant(
        selection.zone, appearance, scene.level, scene.charging,
        scene.wifiLevel, PREVIEW_TYPE, sims,
    )
    if (actual == selection.part) return

    val messageRes = when {

        scene.charging && selection.part != TrioZones.Part.BOLT -> R.string.drawing_feedback_charging
        selection.part == TrioZones.Part.BOLT && !scene.charging -> R.string.drawing_feedback_bolt
        selection.part == TrioZones.Part.WIFI && scene.wifiLevel < 0 -> R.string.drawing_feedback_wifi
        else -> R.string.drawing_feedback_other
    }
    Text(
        text = stringResource(
            messageRes,
            stringResource(occupantLabelRes(selection.part)),
            stringResource(zoneLabelRes(selection.zone, appearance)),
            stringResource(occupantLabelRes(actual)),
        ),
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(top = 10.dp),
    )
}

/**
 * Writes the switch combination already solved for the selected zone and component.
 *
 * <p>The combination is solved by [TrioZones.assign] against the real arbitration
 * rule, so nothing here decides which switches to move. That is deliberate: this
 * page asks for an outcome ("put the number in the middle") and the renderer decides
 * what that costs, which is the only arrangement that cannot drift.
 *
 * <p>Only the switches that differ are written, and they are written through the same
 * repository setters the rest of the screen uses - so the local commit, the push to
 * the hooked process and the broadcast all happen exactly once per changed switch.
 */
private fun applyZoneAssignment(
    next: TrioSettings,
    repository: SettingsRepository,
    settings: TrioSettings,
) {
    // The slot-driven arrangements write the whole layout in one transaction. The capsule
    // joined the ring here once its two dot rows became placements: its bands now come from
    // ringZones too, so pushing only the switches would leave the rows un-moveable - which is
    // the defect this path used to have, where the openings showed dots no setting could
    // change.
    if (TrioZones.hasSlots(next)) {
        repository.applyRingAssignment(next)
        return
    }
    if (next.showWifi != settings.showWifi) repository.setShowWifi(next.showWifi)
    if (next.showMobile != settings.showMobile) repository.setShowMobile(next.showMobile)
    if (next.showValue != settings.showValue) repository.setShowValue(next.showValue)
    if (next.showBolt != settings.showBolt) repository.setShowBolt(next.showBolt)
    if (next.dualSim != settings.dualSim) repository.setDualSim(next.dualSim)
    if (next.valueCentred != settings.valueCentred) {
        repository.setValueCentred(next.valueCentred)
    }
    if (next.mobileTypeMode != settings.mobileTypeMode) {
        repository.setMobileTypeMode(next.mobileTypeMode)
    }
}

/**
 * The list of pieces that can be put in one zone.
 *
 * <p>Every entry comes from [TrioZones.choices], which answers by asking the solver
 * whether each piece is placeable - so the menu and the solver cannot disagree. A
 * piece the zone cannot hold at all is still listed, greyed out with the reason,
 * rather than hidden: a missing entry leaves the user wondering whether they opened
 * the wrong list, where a disabled one tells them why.
 *
 * <p>"Nothing here" is the first entry, because emptying a zone is a normal thing to
 * want and burying it at the bottom would push it out of sight on a long list.
 */
@Composable
private fun ZonePickerDialog(
    zone: TrioZones.Zone,
    settings: TrioSettings,
    appearance: TrioAppearance,
    scene: PreviewScene,
    sims: Int,
    onChoose: (TrioZones.Part?) -> Unit,
    onEdit: (TrioZones.Part) -> Unit,
    onDismiss: () -> Unit,
) {
    // Asked of the frame the user is looking at, not of a fixed one: the title says
    // what is in the zone now, and the bolt only exists while charging - so a
    // hard-coded frame would have the title name a piece the user cannot see.
    val current = TrioZones.occupant(
        zone, appearance, scene.level, scene.charging,
        scene.wifiLevel, PREVIEW_TYPE, sims,
    )
    val solvable = TrioZones.placementChoices(zone, settings, sims)
    // This third's saved list, which is what "already here" and "elsewhere" are judged
    // against. Read from the appearance rather than from settings so it is the same list
    // the preview is drawing from.
    val chain = appearance.ringZones[zone.ordinal]
    val canEmpty = TrioZones.place(zone, null, settings, sims) != null

    // Taking a piece out of another third is the one tap here that is not reversible by
    // tapping again: the row it came from is left short, and the user may not have been
    // looking at that third at all. So that tap asks first, and the question names both
    // thirds. Everything else - placing a piece that is nowhere, promoting one already
    // in this list, emptying this list - stays a single tap, because it either changes
    // nothing elsewhere or is the thing the user opened the sheet to do.
    var confirmingMove by remember { mutableStateOf<ZoneMove?>(null) }

    // A bottom sheet, the same container the component settings open in. Picking what goes
    // in a zone and sizing it are two answers about one component, and they used to arrive
    // in two different shapes - a centred dialog for the first, a sheet for the second - which
    // made the long-press shortcut on a picker row feel like it left the screen it was on.
    // The title carries the zone and what is in it now, as before.
    // The gesture bar overlays the bottom of the window, so the last row has to clear it.
    // The component sheet measures the same inset the same way; forgetting it here put the
    // list flush against the screen edge, under the home indicator.
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    OverlayBottomSheet(
        show = true,
        title = stringResource(
            R.string.drawing_pick_title,
            stringResource(zoneLabelRes(zone, appearance)),
            stringResource(occupantLabelRes(current)),
        ),
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = bottomInset)) {
            // Same bounce as the component sheet: a bare verticalScroll has none, so the list
            // stopped dead at its ends instead of springing back.
            Column(modifier = Modifier.weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 4.dp)) {
                // Emptying first: it is a normal request, and putting it last would push
                // it off the bottom of a long list.
                PickerRow(
                    title = stringResource(R.string.drawing_occupant_none),
                    summary = stringResource(if (canEmpty) R.string.drawing_pick_none_summary else R.string.drawing_clock_fixed),
                    selected = current == null,
                    enabled = canEmpty,
                    onClick = { if (canEmpty) onChoose(null) },
                    onLongClick = null,
                )

                // placeableParts, not Part.entries: the aggregate meter is retired, so
                // listing it here would offer a piece that would immediately be reported
                // as un-placeable.
                TrioZones.placeableParts().forEach { part ->
                    val canPlace = solvable.contains(part)
                    val id = TrioZones.encodePart(part)
                    // Three questions, drawn differently:
                    //
                    //   current   - being drawn in this third right now;
                    //   inZone    - already in THIS third's list, just not the one winning;
                    //   elsewhere - placed in a DIFFERENT third. Tapping moves it here and
                    //               leaves that other third short, so the row says so first
                    //               and the tap is confirmed before it happens.
                    val inZone = chain.contains(id)
                    val elsewhere = if (inZone) null else TrioZones.Zone.entries.firstOrNull { z ->
                        z != zone && appearance.ringZones[z.ordinal].contains(id)
                    }
                    // A refusal has two shapes here, and they are different sentences. The
                    // ring's middle does not hold a signal row at all - a rule about the
                    // band, which no switch can change (see TrioZones.ringAccepts) - while
                    // anything else the solver cannot place is a frame that does not exist
                    // yet. Saying "the middle does not fit this component" for the first
                    // would leave the user hunting for a setting that would fix it.
                    val reason = when {
                        canPlace -> null
                        !TrioZones.accepts(zone, part, settings) -> stringResource(
                            R.string.drawing_pick_signal_in_centre,
                            stringResource(occupantLabelRes(part)),
                        )
                        else -> stringResource(
                            R.string.drawing_pick_impossible,
                            stringResource(zoneLabelRes(zone, appearance)),
                        )
                    }
                    // Only STATE, never what the component does. It used to fall through to
                    // the component description, so one column carried four different kinds
                    // of sentence - "showing now", "promote to first", "moving from below",
                    // and a paragraph about what Wi-Fi arcs are - and the reader had to work
                    // out which kind each line was before it meant anything.
                    // Only a REFUSAL gets a sentence. The rest of the states are already said
                    // twice - a word beside the title, a mark at the end - and adding a line
                    // under them made the list read as four kinds of message stacked.
                    val summary = reason
                    PickerRow(
                        title = stringResource(occupantLabelRes(part)),
                        summary = summary,
                        selected = current == part,
                        inZone = inZone,
                        elsewhere = elsewhere != null,
                        enabled = canPlace,
                        onClick = {
                            if (canPlace) {
                                if (elsewhere != null) {
                                    confirmingMove = ZoneMove(part, elsewhere)
                                } else {
                                    onChoose(part)
                                }
                            }
                        },
                        onLongClick = if (canPlace) {
                            { onEdit(part) }
                        } else {
                            null
                        },
                    )
                }

            }
            // No close button. This is a bottom sheet: it dismisses by swipe and by tapping
            // the scrim, and a full-width primary-coloured "Close" under a list of choices
            // reads as the thing to do rather than as the way out. The component sheet has
            // never had one either, and the two are the same kind of surface now.
        }
    }

    confirmingMove?.let { move ->
        OverlayDialog(
            show = true,
            title = stringResource(R.string.drawing_move_title),
            summary = stringResource(
                R.string.drawing_move_summary,
                stringResource(occupantLabelRes(move.part)),
                stringResource(zoneLabelRes(move.from, appearance)),
            ),
            onDismissRequest = { confirmingMove = null },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = { confirmingMove = null },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        confirmingMove = null
                        onChoose(move.part)
                    },
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.drawing_move_confirm))
                }
            }
        }
    }
}

/** A pending "take this piece out of another third" request, named well enough to ask about. */
private class ZoneMove(val part: TrioZones.Part, val from: TrioZones.Zone)

/** One entry in the picker. */
@Composable
private fun PickerRow(
    title: String,
    summary: String?,
    selected: Boolean,
    inZone: Boolean = false,
    elsewhere: Boolean = false,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val content = if (enabled) {
        MiuixTheme.colorScheme.onSurface
    } else {
        MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    // Four states, four looks, because each answers a different question:
                    //
                    //   selected  - being drawn here now       (strong tint + check)
                    //   inZone    - already in this list       (quiet tint + tag)
                    //   elsewhere - placed in ANOTHER third    (faint tint + tag)
                    //   none      - not placed anywhere        (plain row)
                    //
                    // One highlight for "present" and "drawn" would claim every listed
                    // piece is on screen; no mark for "elsewhere" would hide that tapping
                    // it takes the piece away from another third.
                    selected -> MiuixTheme.colorScheme.primaryContainer
                    inZone -> MiuixTheme.colorScheme.surfaceContainerHigh
                    elsewhere -> MiuixTheme.colorScheme.surfaceContainer
                    else -> Color.Transparent
                },
            )
            .semantics { this.selected = selected }
            .combinedClickable(enabled = enabled, role = Role.RadioButton,
                onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.body1,
                    color = if (selected) MiuixTheme.colorScheme.onPrimaryContainer else content,
                )
                // A word rather than only a tint: this state has to survive a colour the
                // user cannot distinguish, and it decides whether a tap adds a second
                // entry, promotes the existing one, or MOVES the piece out of another
                // third. "已在环内" and "在别处" are deliberately different words.
                when {
                    inZone && !selected -> Text(
                        text = stringResource(R.string.drawing_pick_in_zone),
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    elsewhere -> Text(
                        text = stringResource(R.string.drawing_pick_elsewhere),
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            // No second line. The state is already written beside the title as a word and
            // marked at the end of the row; a subtitle repeating it made one fact appear
            // three times, and when it repeated a REFUSAL it said the same sentence twice
            // in two different places.
            //
            // What is left is the one case a tag cannot carry: a frame that cannot hold the
            // piece at all, where the reason is not a state but an obstacle.
            if (summary != null) {
                Text(
                    text = summary,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        when {
            // Three marks, not three tints: the end of the row has to say which kind of
            // "present" this is even for a reader who cannot tell the tints apart, and it
            // is the only cue for why tapping takes the piece AWAY from another third.
            //
            //   ✓  drawn here right now  - a check, the universal "this is it";
            //   lock  held by another third - the piece is taken, and tapping steals it;
            //   dot   in this third's list, just not the one winning - present, not taken.
            selected -> Icon(
                imageVector = Lucide.Check,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp),
            )
            inZone -> Icon(
                imageVector = Lucide.CircleDot,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            elsewhere -> Icon(
                imageVector = Lucide.Lock,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** The page-tab handler's "remove this entry" delta; anything else moves by it. */
private const val REMOVE = 0

/**
 * One entry of a zone's priority list: a standard radio preference with reorder controls.
 *
 * <p>The first row is marked selected because it is the one that WINS the third; [isDrawing]
 * marks whether it is the one actually on screen this frame. Those differ when the head has
 * nothing to draw - the fallback behind it is drawn while the head stays the chosen one.
 *
 * <p>Order moves with the two arrows rather than by dragging. That is not a compromise: the
 * library has no sortable list (checked across `miuix-ui`, `miuix-preference` and
 * `miuix-icons`), and arrows sit exactly where a thumb already is, work one-handed, and stay
 * reachable for anyone who cannot drag.
 */
@Composable
private fun PriorityPreferenceRow(
    index: Int,
    part: TrioZones.Part,
    lastIndex: Int,
    isDrawing: Boolean,
    onMove: (Int, Int) -> Unit,
    onRemove: () -> Unit,
    onEdit: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .weight(1f)
                // The library row offers onClick and nothing else, so the long press goes on
                // this wrapper instead of changing the library.
                //
                // detectTapGestures would not do: the row IS a clickable, so it consumes the
                // press before an ancestor sees it, and that helper only watches unconsumed
                // input. Waiting on the raw stream instead is what makes the gesture fire, and
                // since nothing here consumes anything the row keeps every event it had - its
                // own tap still lands, and the arrow buttons beside it are outside this Box.
                .pointerInput(part, onEdit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // The block returns normally when the gesture ENDS (finger up, or a
                        // drag past touch slop) and is cancelled by the timeout when it does
                        // not. So only a null result means "held long enough" - returning from
                        // the block is the opposite answer. Getting that backwards would open
                        // the sheet on every ordinary tap.
                        val endedEarly = withTimeoutOrNull(
                                viewConfiguration.longPressTimeoutMillis) {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: continue
                                if (!change.pressed ||
                                    (change.position - down.position).getDistance() >
                                    viewConfiguration.touchSlop) {
                                    break
                                }
                            }
                            true
                        }
                        if (endedEarly == null) onEdit()
                    }
                },
        ) {
            RadioButtonPreference(
                title = stringResource(occupantLabelRes(part)),
                // Where this row sits in the chain, and - when it is the head - whether it
                // is the one actually visible. Those differ when the head has nothing to
                // draw on this frame and the fallback behind it takes over.
                summary = when {
                    index == 0 && isDrawing -> stringResource(R.string.drawing_priority_showing)
                    index == 0 -> stringResource(R.string.drawing_priority_first, index + 1)
                    else -> stringResource(R.string.drawing_priority_fallback, index + 1)
                },
                selected = index == 0,
                insideMargin = SettingsItemMargin,
                onClick = {},
            )
        }
        Row(
            modifier = Modifier.padding(end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OrderButton(
                icon = Lucide.ChevronUp,
                description = R.string.drawing_priority_move_up,
                enabled = index > 0,
            ) { onMove(index, -1) }
            OrderButton(
                icon = Lucide.ChevronDown,
                description = R.string.drawing_priority_move_down,
                enabled = index < lastIndex,
            ) { onMove(index, 1) }
            OrderButton(
                icon = Lucide.X,
                description = R.string.drawing_priority_remove,
                enabled = true,
            ) { onRemove() }
        }
    }
}

/**
 * One of the three small controls beside a priority row.
 *
 * <p>Disabled rather than hidden at the ends of the list: a row that loses its up arrow at the
 * top would look like a different row, and the user is clicking by position.
 */
@Composable
private fun OrderButton(
    icon: ImageVector,
    description: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(36.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(description),
            tint = if (enabled) MiuixTheme.colorScheme.onSurfaceVariantSummary
                else MiuixTheme.colorScheme.outline,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** The large glyph, with the three zones as touch targets and guides.
 *
 * <p>The zones are resolved by the <em>view</em>, because it is what knows the
 * transform the glyph is drawn with: the gestures here ask it for the third under
 * the finger rather than working one out from the touch coordinates. That is the
 * whole design of this composable - a second copy of the design-to-view mapping on
 * the Compose side is exactly how a hot spot ends up beside the piece it names.
 *
 * <p>The gestures are Compose's rather than a touch listener on the view, and that
 * is a deliberate correction: a listener returning true for `ACTION_DOWN` swallows
 * the event, so the page could not be scrolled by dragging over a glyph that fills
 * most of the screen. [detectTapGestures] instead gives up the moment the finger
 * moves past the touch slop, so a drag scrolls the list and a press that stays put
 * is a tap.
 *
 * <p>The handler reads its callbacks through [rememberUpdatedState] so the detector,
 * which is keyed on the settings rather than rebuilt per frame, always calls the
 * current lambda; one captured at composition would close over an older snapshot
 * and flip a switch based on what was on screen several edits ago.
 */
@Composable
private fun DrawingGlyph(
    settings: TrioSettings,
    scene: PreviewScene,
    dark: Boolean,
    pressed: TrioZones.Zone?,
    onPressChange: (TrioZones.Zone?) -> Unit,
    onPickZone: (TrioZones.Zone) -> Unit,
    onEditZone: (TrioZones.Zone) -> Unit,
    onEditBattery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The theme's own accent, so the press highlight is the same colour as the rest of the
    // app's "selected" affordances and stays visible on both themes.
    val accent = MiuixTheme.colorScheme.primary.toArgb()
    val currentPress = rememberUpdatedState(onPressChange)
    val currentPick = rememberUpdatedState(onPickZone)
    val currentEdit = rememberUpdatedState(onEditZone)
    val currentBattery = rememberUpdatedState(onEditBattery)

    // The view the gestures interrogate. A plain holder rather than a MutableState:
    // it is written once, from `factory`, and read only later - when a finger
    // arrives - so there is nothing here that ought to trigger recomposition, and
    // writing state during the layout that creates the view would.
    val holder = remember { arrayOfNulls<TrioPreviewView>(1) }
    // Resolved against the view that is actually laid out, so a gesture arriving
    // before the first layout finds no zone instead of guessing one.
    val zoneAt: (Offset) -> TrioZones.Zone? = { offset ->
        holder[0]?.zoneAt(offset.x, offset.y)
    }

    Box(
        modifier = modifier
            .fillMaxWidth(0.94f)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .pointerInput(settings, scene) {
                detectTapGestures(
                    onPress = { offset ->
                        currentPress.value(if (holder[0]?.isBatteryRingAt(offset.x, offset.y) == true)
                            null else zoneAt(offset))
                        // Waits for the press to resolve, which is also what lets a
                        // drag cancel this detector and be handled as a scroll.
                        tryAwaitRelease()
                        currentPress.value(null)
                    },
                    onTap = { offset ->
                        if (holder[0]?.isBatteryRingAt(offset.x, offset.y) == true) currentBattery.value()
                        else zoneAt(offset)?.let { currentPick.value(it) }
                    },
                    onLongPress = { offset ->
                        if (holder[0]?.isBatteryRingAt(offset.x, offset.y) == true) currentBattery.value()
                        else zoneAt(offset)?.let { currentEdit.value(it) }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            // Fills the square Box above; the aspect ratio belongs to the Box so the
            // gesture detector covers exactly the area the glyph is drawn in.
            modifier = Modifier.fillMaxSize(),
            // Applied in the factory too: an AndroidView whose node never recomposes
            // keeps whatever its factory gave it, and a view left at its defaults draws
            // the shipped look rather than the user's. See applyPreviewState.
            factory = { context ->
                TrioPreviewView(context).also { view ->
                    holder[0] = view
                    applyPreviewState(view, settings, scene, dark, accent = accent,
                        highlight = pressed)
                }
            },
            update = { view ->
                applyPreviewState(view, settings, scene, dark, accent = accent,
                    highlight = pressed)
            },
        )
    }
}

/** The short name for a piece, for the zone rows and the picker. */
private fun occupantLabelRes(part: TrioZones.Part?): Int = when (part) {
    TrioZones.Part.WIFI -> R.string.drawing_occupant_wifi
    TrioZones.Part.MOBILE -> R.string.drawing_occupant_mobile
    TrioZones.Part.VALUE -> R.string.drawing_occupant_value
    TrioZones.Part.BOLT -> R.string.drawing_occupant_bolt
    TrioZones.Part.TYPE -> R.string.drawing_occupant_type
    TrioZones.Part.SIM_1 -> R.string.drawing_occupant_sim_1
    TrioZones.Part.SIM_2 -> R.string.drawing_occupant_sim_2
    TrioZones.Part.CLOCK -> R.string.drawing_occupant_clock
    null -> R.string.drawing_occupant_none
}

/**
 * The zone's own name, for the picker's title and its refusals.
 *
 * <p>One name set for every style: top, middle, bottom. The three arrangements differ in
 * their shape, not in the question the user is asking - "where on the icon does this go" -
 * so giving the capsule its own vocabulary ("top opening", "inside") said the same thing
 * less directly and implied a difference that does not exist.
 *
 * <p>The `appearance` parameter is kept because callers already have it to hand and a future
 * style that genuinely needs a different word should change it here rather than at six call
 * sites; nothing in this function reads it today.
 */
private fun zoneLabelRes(zone: TrioZones.Zone, a: TrioAppearance): Int = when (zone) {
    TrioZones.Zone.TOP -> R.string.drawing_hint_top
    // In the capsule the interior is two cells side by side, so its right half is
    // the RIGHT cell rather than a centre. The ring and the rectangle keep 涓儴: for
    // them the interior really is the middle, and a single cell.
    TrioZones.Zone.CENTRE -> if (a.capsule) R.string.drawing_hint_right
        else R.string.drawing_hint_centre
    TrioZones.Zone.BOTTOM -> R.string.drawing_hint_bottom
    TrioZones.Zone.LEFT -> R.string.drawing_hint_left
}

/** The zones this arrangement shows, in tab order. All three styles have the same three. */
/**
 * The zones this arrangement shows, in tab order.
 *
 * <p>The capsule has four cells - the two openings, its right-hand interior and its
 * left-hand interior - so it shows four. The ring and the rectangle have three, and
 * LEFT is not one of them: their interiors are single cells.
 */
private fun zonesFor(a: TrioAppearance): List<TrioZones.Zone> =
    if (a.capsule) {
        // Four cells, clockwise from the top: the two openings and the interior
        // split into a left and a right half. Spatially ordered rather than
        // enum-ordered, so the strip reads the way the glyph is laid out.
        listOf(TrioZones.Zone.TOP, TrioZones.Zone.LEFT, TrioZones.Zone.CENTRE,
                TrioZones.Zone.BOTTOM)
    } else {
        listOf(TrioZones.Zone.TOP, TrioZones.Zone.CENTRE, TrioZones.Zone.BOTTOM)
    }

/**
 * Says that the signal rows are drawn outside the ring, whatever this list holds.
 *
 * <p>Shown inside the order card rather than as a dialog: it is a standing explanation of why
 * nothing here is producing dots, not an event to acknowledge. A user who has just switched
 * the signal to 环外 (or who upgraded into that state) sees this instead of concluding the
 * module broke.
 */
@Composable
private fun SignalOutOfRingNotice() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Lucide.Info,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = stringResource(R.string.drawing_signal_out_of_ring_notice),
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

/** One line saying what a piece is, for the picker. */
private fun partSummaryRes(part: TrioZones.Part): Int = when (part) {
    TrioZones.Part.WIFI -> R.string.show_wifi_summary
    TrioZones.Part.MOBILE -> R.string.show_mobile_summary
    TrioZones.Part.VALUE -> R.string.show_value_summary
    TrioZones.Part.BOLT -> R.string.show_bolt_summary
    TrioZones.Part.TYPE -> R.string.show_mobile_type_summary
    TrioZones.Part.SIM_1 -> R.string.show_sim_1_summary
    TrioZones.Part.SIM_2 -> R.string.show_sim_2_summary
    TrioZones.Part.CLOCK -> R.string.clock_size_summary
}

/**
 * The preview-state chips.
 *
 * <p>A horizontal strip rather than a dropdown: there are six, their labels are
 * short, and seeing the whole set is what tells the user that a charging frame is
 * reachable at all - which is the only way the bolt becomes tappable.
 */
@Composable
private fun SceneSelector(
    selected: PreviewScene,
    onSelect: (PreviewScene) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(PreviewScene.entries, key = { it.name }) { entry ->
            SceneChip(
                label = stringResource(entry.labelRes),
                selected = entry == selected,
                onClick = { onSelect(entry) },
            )
        }
    }
}

/** One state chip. */
@Composable
private fun SceneChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (selected) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.surfaceContainerHigh
                },
            )
            .semantics { this.selected = selected }
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.button,
            color = if (selected) {
                MiuixTheme.colorScheme.onPrimary
            } else {
                MiuixTheme.colorScheme.onSurfaceContainerHigh
            },
            textAlign = TextAlign.Center,
        )
    }
}
