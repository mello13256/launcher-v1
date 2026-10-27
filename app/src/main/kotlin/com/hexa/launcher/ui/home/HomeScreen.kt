package com.hexa.launcher.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hexa.core.home.Overlay
import com.hexa.core.layout.HexLayoutEngine
import com.hexa.core.layout.LayoutPresets
import com.hexa.core.layout.SQRT3
import com.hexa.core.model.BackgroundConfig
import com.hexa.core.model.BackgroundMode
import com.hexa.core.model.Corner
import com.hexa.core.model.SearchPosition
import com.hexa.launcher.ui.edit.ComboEditorDialog
import com.hexa.launcher.ui.edit.ComboListDialog
import com.hexa.launcher.ui.edit.HiveEditorSheet
import com.hexa.launcher.ui.hex.drawBackgroundImage
import com.hexa.launcher.ui.theme.HexaColors
import kotlin.math.min
import kotlin.math.roundToInt

/** Home screen: background, hive composition, search bar, music hive and the edit layers. */
@Composable
fun HomeScreen(vm: HomeViewModel) {
    val loaded by vm.loaded.collectAsState()
    // The config file is tiny and read in a few ms; drawing defaults first would flash wrong colours.
    if (!loaded) return
    val config by vm.config.collectAsStateWithLifecycle()
    val music by vm.graph.music.state.collectAsStateWithLifecycle()
    val apps by vm.graph.apps.snapshot.collectAsStateWithLifecycle()
    val overlay = vm.overlay
    val editing = overlay is Overlay.Edit

    // Back closes the top layer; on the plain composition it does nothing (a launcher never finishes).
    BackHandler { vm.back() }

    // Positions below are computed in physical pixels; lay out left-to-right and mirror only the
    // music corner for right-to-left locales.
    val dir = LocalLayoutDirection.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        HomeContent(vm, config, music, apps, overlay, editing, dir)
    }
}

@Composable
private fun HomeContent(
    vm: HomeViewModel,
    config: com.hexa.core.model.LauncherConfig,
    music: com.hexa.launcher.media.MusicState,
    apps: com.hexa.launcher.apps.AppsSnapshot,
    overlay: Overlay,
    editing: Boolean,
    dir: LayoutDirection,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
        val px = { dp: Float -> dp * density.density }
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val insetTop = insets.getTop(density).toFloat()
        val insetBottom = insets.getBottom(density).toFloat()
        val insetLeft = insets.getLeft(density, dir).toFloat()
        val insetRight = insets.getRight(density, dir).toFloat()
        val margin = px(12f)

        // Compact music hive: pointy-top hexagon of circumradius 26dp in the chosen corner.
        val musicR = px(26f)
        val musicW = musicR * SQRT3
        val musicH = musicR * 2f
        val corner = if (dir == LayoutDirection.Rtl) mirror(config.music.corner) else config.music.corner
        val musicTop = corner == Corner.TOP_START || corner == Corner.TOP_END
        val musicStart = corner == Corner.TOP_START || corner == Corner.BOTTOM_START
        val musicOn = config.music.enabled && !editing

        // The search bar row (also used by the edit bar while editing). When the music hive is in a
        // corner of the same edge, they share the row.
        val searchBottom = config.search.position == SearchPosition.BOTTOM
        val shareRow = config.music.enabled && musicTop != searchBottom
        val pillH = px(48f)
        val rowH = if (shareRow) maxOf(pillH, musicH) else pillH
        val rowTop = if (searchBottom) height - insetBottom - margin - rowH else insetTop + margin

        val musicLeft = if (musicStart) insetLeft + margin else width - insetRight - margin - musicW
        val musicTopPx = when {
            shareRow -> rowTop + (rowH - musicH) / 2f
            musicTop -> insetTop + margin
            else -> height - insetBottom - margin - musicH
        }
        val compact = Rect(musicLeft, musicTopPx, musicLeft + musicW, musicTopPx + musicH)
        val expW = min(width - insetLeft - insetRight - 2 * margin, px(440f))
        // Taller with large fonts so the controls never overflow the card.
        val expH = px(196f) * density.fontScale.coerceIn(1f, 1.6f)
        val expLeft = if (musicStart) insetLeft + margin else width - insetRight - margin - expW
        val expTop = if (musicTop) insetTop + margin else height - insetBottom - margin - expH
        val expanded = Rect(expLeft, expTop, expLeft + expW, expTop + expH)

        // Field area: the safe area minus the search row and, if separate, the music hive row. It
        // does not change in edit mode, so the composition never jumps when editing starts.
        var top = insetTop + margin
        var bottom = height - insetBottom - margin
        if (searchBottom) bottom -= rowH + margin else top += rowH + margin
        if (config.music.enabled && !shareRow) {
            if (musicTop) top += musicH + margin * 0.5f else bottom -= musicH + margin * 0.5f
        }
        val fieldLeft = insetLeft + margin
        val fieldWidth = width - insetLeft - insetRight - 2 * margin
        val fieldHeight = (bottom - top).coerceAtLeast(px(100f))

        val geometry = remember(config.layout, fieldWidth, fieldHeight, density.density) {
            HexLayoutEngine.compute(LayoutPresets.of(config.layout.preset), config.layout, fieldWidth, fieldHeight, density.density)
        }
        val origin = Offset(fieldLeft, top)
        SideEffect { vm.placement = FieldPlacement(geometry, origin.x, origin.y) }

        Background(config.background, vm)

        HiveField(vm = vm, config = config, geometry = geometry, origin = origin, editing = editing)

        val barStart = insetLeft + margin + if (shareRow && musicStart) musicW + margin else 0f
        val barEnd = insetRight + margin + if (shareRow && !musicStart) musicW + margin else 0f
        val barTop = rowTop + (rowH - pillH) / 2f
        val barModifier = with(density) {
            Modifier
                .offset { IntOffset(barStart.roundToInt(), barTop.roundToInt()) }
                .width((width - barStart - barEnd).toDp())
        }
        if (!editing) SearchPill(onClick = vm::openSearch, modifier = barModifier)

        if (musicOn) {
            MusicHive(
                state = music,
                appLabel = music.sessionPackage?.let { pkg -> apps.apps.firstOrNull { it.packageName == pkg }?.label },
                expanded = overlay == Overlay.MusicExpanded,
                compact = compact,
                expandedRect = expanded,
                color = Color(config.music.color).copy(alpha = config.music.opacity),
                onTap = vm::onMusicHiveTap,
                onLongPress = vm::onMusicHiveLongPress,
                onCollapse = vm::collapseMusic,
                onPlayPause = { vm.graph.music.playPause() },
                onNext = { vm.graph.music.next() },
                onPrevious = { vm.graph.music.previous() },
                onSeek = { vm.graph.music.seekToFraction(it) },
                onOpenPlayer = vm::openMusicPlayer,
                onRequestAccess = vm::requestMusicAccess,
            )
        }

        if (overlay == Overlay.Search) {
            SearchOverlay(vm, config.search.position, config.search.autoKeyboard)
        }

        if (editing) {
            EditBar(vm, Modifier.offset { IntOffset((insetLeft + margin).roundToInt(), barTop.roundToInt()) }.width(with(density) { (width - insetLeft - insetRight - 2 * margin).toDp() }))
            val selected = (overlay as Overlay.Edit).selectedHive
            if (selected != null) {
                HiveEditorSheet(
                    editor = vm.editor,
                    hiveId = selected,
                    onDismiss = { vm.selectHive(null) },
                    onEditCombo = { vm.editCombo(it) },
                    onNewCombo = { vm.newComboStartingWith(it) },
                    toast = { vm.host?.toast(it) },
                )
            }
            if (vm.showComboList) {
                ComboListDialog(vm.editor, onEdit = { vm.editCombo(it) }, onDismiss = vm::closeComboList)
            }
            vm.comboEditor?.let { combo ->
                ComboEditorDialog(vm.editor, combo, onDismiss = vm::closeComboEditor)
            }
        }
    }
}

private fun mirror(c: Corner) = when (c) {
    Corner.TOP_START -> Corner.TOP_END
    Corner.TOP_END -> Corner.TOP_START
    Corner.BOTTOM_START -> Corner.BOTTOM_END
    Corner.BOTTOM_END -> Corner.BOTTOM_START
}

@Composable
private fun Background(bg: BackgroundConfig, vm: HomeViewModel) {
    when (bg.mode) {
        BackgroundMode.WALLPAPER -> Unit // the window shows the system wallpaper
        BackgroundMode.COLOR -> Box(Modifier.fillMaxSize().background(Color(bg.color)))
        BackgroundMode.IMAGE -> {
            val img = bg.image?.let { vm.editor.visuals.images[it.path] }
            val cfg = bg.image
            if (img != null && cfg != null) {
                Canvas(Modifier.fillMaxSize()) { drawBackgroundImage(img, cfg) }
            } else {
                Box(Modifier.fillMaxSize().background(Color(bg.color)))
            }
        }
    }
    if (bg.dim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = bg.dim)))
}

/**
 * Edit mode bar, placed where the search bar normally is (search is hidden while editing), so the
 * composition keeps exactly the same geometry. Accent colour + dashed outlines make the mode obvious.
 */
@Composable
private fun EditBar(vm: HomeViewModel, modifier: Modifier) {
    Row(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(HexaColors.AccentStrong.copy(alpha = 0.95f))
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(onClick = vm::openComboList) { Text("Combinações", color = Color.White, fontSize = 13.sp) }
        TextButton(onClick = { vm.host?.openSettings() }) { Text("Definições", color = Color.White, fontSize = 13.sp) }
        TextButton(onClick = vm::exitEdit) { Text("Concluir", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
    }
}
