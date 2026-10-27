package com.hexa.launcher.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hexa.core.apps.TargetStatus
import com.hexa.core.layout.LayoutGeometry
import com.hexa.core.layout.SQRT3
import com.hexa.core.model.CENTER_HIVE_ID
import com.hexa.core.model.LauncherConfig
import com.hexa.launcher.ui.hex.HexPaths
import com.hexa.launcher.ui.hex.HiveDecor
import com.hexa.launcher.ui.hex.drawHive
import com.hexa.launcher.ui.theme.LocalAnim
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Touch feedback state, read only in the draw phase so it never recomposes the field. */
@Stable
private class FieldTouchState {
    var pressedSlot by mutableIntStateOf(-1)
    val press = Animatable(0f)
    var dragHive by mutableStateOf<Int?>(null)
    var dragOffset by mutableStateOf(Offset.Zero)
    var dropSlot by mutableStateOf<Int?>(null)
    var flashHives by mutableStateOf<List<Int>>(emptyList())
    val flash = Animatable(0f)
}

/**
 * The hive composition: one canvas for all hexagons plus one gesture handler covering the whole
 * home screen (so long-press and swipe-up work on empty space too).
 *
 * The field is drawn in [geometry] coordinates offset by [origin]. Paths are rebuilt only when
 * the layout or the hive list changes; touches only invalidate drawing.
 */
@Composable
fun HiveField(
    vm: HomeViewModel,
    config: LauncherConfig,
    geometry: LayoutGeometry,
    origin: Offset,
    editing: Boolean,
    modifier: Modifier = Modifier,
) {
    val touch = remember { FieldTouchState() }
    val scope = rememberCoroutineScope()
    val anim = LocalAnim.current
    val density = LocalDensity.current

    val cfg by rememberUpdatedState(config)
    val geo by rememberUpdatedState(geometry)
    val org by rememberUpdatedState(origin)
    val isEditing by rememberUpdatedState(editing)
    val swipeThreshold = with(density) { 56.dp.toPx() }
    val releaseMs = anim.duration(140)

    // Hexagon outlines per slot, at full slot radius and per-hive size.
    val paths = remember(geometry, config.hives, config.layout.cornerRadius) {
        val bySlot = config.hives.associateBy { it.slot }
        geometry.slots.map { s ->
            val hive = bySlot[s.slot]
            Path().also { HexPaths.hexagon(it, s.cx, s.cy, s.radius * (hive?.sizeScale ?: 1f), geometry.pointyTop, config.layout.cornerRadius) }
        }
    }
    val ghostPaths = remember(geometry, config.layout.cornerRadius) {
        geometry.slots.map { s -> Path().also { HexPaths.hexagon(it, s.cx, s.cy, s.radius * 0.92f, geometry.pointyTop, config.layout.cornerRadius) } }
    }

    LaunchedEffect(vm) {
        vm.flash.collect { hives ->
            touch.flashHives = hives
            if (anim.enabled) {
                touch.flash.snapTo(1f)
                touch.flash.animateTo(0f, tween(anim.duration(380)))
            } else {
                touch.flash.snapTo(1f)
                kotlinx.coroutines.delay(200)
                touch.flash.snapTo(0f)
            }
        }
    }

    fun pressSlot(slot: Int) {
        touch.pressedSlot = slot
        scope.launch { touch.press.snapTo(1f) }
    }

    fun release() {
        scope.launch {
            if (releaseMs > 0) touch.press.animateTo(0f, tween(releaseMs)) else touch.press.snapTo(0f)
            touch.pressedSlot = -1
        }
    }

    val dash = remember { PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f) }
    val pending by vm.pending.collectAsState()

    Box(
        modifier
            .fillMaxSize()
            .pointerInput(vm) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    val g = geo
                    val c = cfg
                    val o = org
                    val slot = g.slotAt(down.position.x - o.x, down.position.y - o.y)
                    val hive = slot?.let { c.hiveAtSlot(it) }

                    if (isEditing) {
                        if (hive == null) {
                            trackUntilUp(down.id)
                            return@awaitEachGesture
                        }
                        val start = down.position
                        var dragging = false
                        val up = trackUntilUp(down.id) { change ->
                            val d = change.position - start
                            if (!dragging && d.getDistance() > viewConfiguration.touchSlop && hive.id != CENTER_HIVE_ID) {
                                dragging = true
                                touch.dragHive = hive.id
                            }
                            if (dragging) {
                                touch.dragOffset = d
                                touch.dropSlot = g.slotAt(change.position.x - o.x, change.position.y - o.y)
                                change.consume()
                            }
                        }
                        if (dragging) {
                            val target = touch.dropSlot
                            touch.dragHive = null
                            touch.dragOffset = Offset.Zero
                            touch.dropSlot = null
                            if (up != null && target != null) vm.moveHive(hive.id, target)
                        } else if (up != null) {
                            vm.selectHive(hive.id)
                        }
                        return@awaitEachGesture
                    }

                    if (hive == null) {
                        // Empty space: swipe up opens search, long press enters edit mode.
                        val start = down.position
                        var moved = false
                        var swiped = false
                        val finished = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            trackUntilUp(down.id) { change ->
                                val d = change.position - start
                                if (d.getDistance() > viewConfiguration.touchSlop) moved = true
                                if (!swiped && d.y < -swipeThreshold && abs(d.y) > abs(d.x) * 1.3f) {
                                    swiped = true
                                    if (cfg.search.swipeUpOpensSearch) vm.openSearch()
                                }
                            }
                            true
                        }
                        if (finished == null) {
                            if (!moved && !swiped) vm.onBackgroundLongPress()
                            trackUntilUp(down.id)
                        }
                        return@awaitEachGesture
                    }

                    val hitSlot: Int = slot ?: return@awaitEachGesture
                    pressSlot(hitSlot)
                    if (vm.triggersOnPress(hive.id)) {
                        // Fastest path: act on finger-down; nothing can follow from this hive.
                        vm.onHiveTap(hive.id, down.uptimeMillis)
                        trackUntilUp(down.id)
                        release()
                        return@awaitEachGesture
                    }

                    val path = arrayListOf(hive.id)
                    var lastSlot: Int = hitSlot
                    var moved = false
                    val start = down.position
                    val onMove: (PointerInputChange) -> Unit = { change ->
                        if ((change.position - start).getDistance() > viewConfiguration.touchSlop) moved = true
                        val s = g.slotAt(change.position.x - o.x, change.position.y - o.y)
                        if (s != null && s != lastSlot) {
                            val h = c.hiveAtSlot(s)
                            if (h != null) {
                                path += h.id
                                lastSlot = s
                                touch.pressedSlot = s
                            }
                        }
                    }
                    var up: PointerInputChange? = null
                    var ended = false
                    val inTime = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        up = trackUntilUp(down.id, onMove)
                        ended = true
                    }
                    if (inTime == null && !ended) {
                        if (path.size == 1 && !moved) {
                            release()
                            vm.onHiveLongPress(hive.id)
                            trackUntilUp(down.id)
                            return@awaitEachGesture
                        }
                        up = trackUntilUp(down.id, onMove)
                    }
                    release()
                    val u = up ?: return@awaitEachGesture // cancelled (system gesture, etc.)
                    if (path.size == 1) vm.onHiveTap(hive.id, u.uptimeMillis) else vm.onHiveSwipe(path, u.uptimeMillis)
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val icons = vm.editor.visuals.icons
            val images = vm.editor.visuals.images
            val status = vm.editor.visuals.status
            val pressedSlot = touch.pressedSlot
            val press = touch.press.value
            val flash = touch.flash.value
            val flashHives = touch.flashHives
            val dragHive = touch.dragHive
            val bySlot = config.hives.associateBy { it.slot }

            translate(origin.x, origin.y) {
                if (editing) {
                    for (s in geometry.slots) {
                        if (bySlot[s.slot] == null) {
                            val target = touch.dropSlot == s.slot
                            drawPath(
                                ghostPaths[s.slot],
                                Color.White.copy(alpha = if (target) 0.9f else 0.35f),
                                style = Stroke(if (target) 3f * this.density else 1.5f * this.density, pathEffect = dash),
                            )
                        }
                    }
                }
                for (s in geometry.slots) {
                    val hive = bySlot[s.slot] ?: continue
                    if (hive.id == dragHive) continue
                    val r = s.radius * hive.sizeScale
                    drawHive(
                        path = paths[s.slot],
                        cx = s.cx,
                        cy = s.cy,
                        r = r,
                        pointyTop = geometry.pointyTop,
                        hive = hive,
                        icon = icons[hive.id],
                        image = hive.image?.let { images[it.path] },
                        status = status[hive.id] ?: TargetStatus.READY,
                        decor = HiveDecor(
                            press = if (s.slot == pressedSlot) press else 0f,
                            flash = if (hive.id in flashHives) flash else 0f,
                            pending = cfg.comboSettings.showPending && hive.id in pending,
                            editing = editing,
                            lifted = editing && touch.dropSlot == s.slot && dragHive != null,
                        ),
                    )
                }
                if (dragHive != null) {
                    val hive = config.hives.firstOrNull { it.id == dragHive }
                    if (hive != null) {
                        val s = geometry.slot(hive.slot)
                        translate(touch.dragOffset.x, touch.dragOffset.y) {
                            drawHive(
                                path = paths[s.slot],
                                cx = s.cx, cy = s.cy, r = s.radius * hive.sizeScale,
                                pointyTop = geometry.pointyTop,
                                hive = hive,
                                icon = icons[hive.id],
                                image = hive.image?.let { images[it.path] },
                                status = status[hive.id] ?: TargetStatus.READY,
                                decor = HiveDecor(lifted = true),
                            )
                        }
                    }
                }
            }
        }

        // Accessibility (and optional captions): one node per hive at its position.
        val labelOf = { id: Int -> vm.editor.hiveLabel(id) }
        for (hive in config.hives) {
            if (hive.slot !in geometry.slots.indices) continue
            val s = geometry.slot(hive.slot)
            val r = s.radius * hive.sizeScale
            val w = if (geometry.pointyTop) r * SQRT3 else 2f * r
            val h = if (geometry.pointyTop) 2f * r else r * SQRT3
            val label = labelOf(hive.id).ifEmpty { if (hive.id == CENTER_HIVE_ID) "Colmeia central vazia" else "Colmeia vazia" }
            Box(
                Modifier
                    .offset { IntOffset((origin.x + s.cx - w / 2f).roundToInt(), (origin.y + s.cy - h / 2f).roundToInt()) }
                    .size(with(density) { w.toDp() }, with(density) { h.toDp() })
                    .semantics {
                        contentDescription = label
                        role = Role.Button
                        onClick(label = "Abrir") {
                            vm.onHiveTap(hive.id, android.os.SystemClock.uptimeMillis())
                            true
                        }
                        onLongClick(label = "Editar") {
                            vm.openHiveEditor(hive.id)
                            true
                        }
                    },
                contentAlignment = Alignment.BottomCenter,
            ) {
                if (hive.showLabel && label.isNotEmpty()) {
                    Text(
                        text = label,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = with(density) { (h * 0.12f).toDp() }, start = 6.dp, end = 6.dp),
                    )
                }
            }
        }
    }
}

/**
 * Follows pointer [id] until it is lifted. Returns the up change, or null when the gesture was
 * cancelled (another component or the system took the pointer). [onMove] sees every move.
 */
private suspend fun AwaitPointerEventScope.trackUntilUp(
    id: PointerId,
    onMove: (PointerInputChange) -> Unit = {},
): PointerInputChange? {
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == id } ?: return null
        if (!change.pressed) {
            // A cancel arrives as an "up" that is already consumed.
            return if (change.isConsumed) null else change
        }
        if (change.positionChanged()) onMove(change)
    }
}

private fun PointerInputChange.positionChanged() = position != previousPosition
