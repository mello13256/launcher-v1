package com.hexa.launcher.ui.home

import android.os.SystemClock
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hexa.core.music.MusicDisplay
import com.hexa.launcher.media.MusicState
import com.hexa.launcher.ui.hex.Glyph
import com.hexa.launcher.ui.hex.GlyphIcon
import com.hexa.launcher.ui.hex.MorphShape
import com.hexa.launcher.ui.hex.drawGlyph
import com.hexa.launcher.ui.theme.HexaColors
import com.hexa.launcher.ui.theme.LocalAnim
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * The music hive. Compact: a small hexagon in a screen corner. Expanded: the same surface grows
 * from that corner while its outline morphs from hexagon to rounded rectangle, then the player
 * content fades in. Collapsing runs the same transformation backwards.
 *
 * [compact] and [expanded] are rectangles in px in the home screen's coordinate space.
 */
@Composable
fun MusicHive(
    state: MusicState,
    appLabel: String?,
    expanded: Boolean,
    compact: Rect,
    expandedRect: Rect,
    color: Color,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onCollapse: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    onOpenPlayer: () -> Unit,
    onRequestAccess: () -> Unit,
) {
    val anim = LocalAnim.current
    val t by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(anim.duration(240), easing = FastOutSlowInEasing),
        label = "musicMorph",
    )
    val density = LocalDensity.current
    val rect = Rect(
        left = compact.left + (expandedRect.left - compact.left) * t,
        top = compact.top + (expandedRect.top - compact.top) * t,
        right = compact.right + (expandedRect.right - compact.right) * t,
        bottom = compact.bottom + (expandedRect.bottom - compact.bottom) * t,
    )
    val corner = with(density) { 26.dp.toPx() }
    val shape = remember(t, corner) { MorphShape(t, 0.16f, corner) }
    val display = MusicDisplay.from(state.snapshot, state.access, appLabel)

    // Full-screen container: while the widget is open it catches taps outside the card (closing
    // it). The card is its child, so the card's own controls always receive their touches first.
    Box(
        Modifier
            .fillMaxSize()
            .then(
                if (t > 0f) {
                    Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onCollapse)
                } else {
                    Modifier
                },
            ),
    ) {
        Box(
            Modifier
                .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
                .graphicsLayer {
                    this.shape = shape
                    clip = true
                }
                .background(color)
                .then(
                    if (t < 0.5f) {
                        Modifier
                            .combinedClickable(onClick = onTap, onLongClick = onLongPress)
                            .semantics { contentDescription = if (display.isPlaying) "Música: ${display.title}" else "Música" }
                    } else {
                        Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                    },
                ),
        ) {
            if (t < 0.5f) {
                val a = 1f - t * 2f
                Canvas(Modifier.fillMaxSize().alpha(a)) {
                    drawGlyph(Glyph.NOTE, center, size.minDimension * 0.46f, Color.White)
                    if (display.isPlaying) {
                        // Small "live" dot when something is playing.
                        drawCircle(HexaColors.Accent, size.minDimension * 0.07f, Offset(size.width * 0.5f, size.height * 0.86f))
                    }
                }
            }
            if (t > 0.5f) {
                ExpandedPlayer(
                    state = state,
                    display = display,
                    visible = expanded,
                    modifier = Modifier.fillMaxSize().alpha((t - 0.5f) * 2f),
                    onClose = onCollapse,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onSeek = onSeek,
                    onOpenPlayer = onOpenPlayer,
                    onRequestAccess = onRequestAccess,
                )
            }
        }
    }
}

@Composable
private fun ExpandedPlayer(
    state: MusicState,
    display: MusicDisplay,
    visible: Boolean,
    modifier: Modifier,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    onOpenPlayer: () -> Unit,
    onRequestAccess: () -> Unit,
) {
    val snapshot = state.snapshot
    var progress by remember { mutableFloatStateOf(0f) }
    // Tick the progress bar only while visible and playing; nothing runs otherwise.
    LaunchedEffect(snapshot, visible) {
        if (snapshot == null || !snapshot.hasProgress) return@LaunchedEffect
        progress = snapshot.progressAt(SystemClock.elapsedRealtime())
        while (visible && snapshot.isPlaying) {
            delay(500)
            progress = snapshot.progressAt(SystemClock.elapsedRealtime())
        }
    }
    val art = remember(state.art) { state.art?.asImageBitmap() }

    Row(modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .fillMaxHeight()
                .width(112.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .clickable(onClick = onOpenPlayer),
            contentAlignment = Alignment.Center,
        ) {
            if (art != null) {
                Image(art, contentDescription = "Capa do álbum", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                GlyphIcon(Glyph.NOTE, Color.White.copy(alpha = 0.6f), size = 40.dp)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(display.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(display.subtitle, color = Color.White.copy(alpha = 0.72f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onClose)
                        .semantics { contentDescription = "Fechar widget de música" },
                    contentAlignment = Alignment.Center,
                ) { GlyphIcon(Glyph.CLOSE, Color.White.copy(alpha = 0.8f), size = 16.dp) }
            }
            if (display.showAccessHint) {
                Text(
                    "Permitir acesso ›",
                    color = HexaColors.Accent,
                    fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onRequestAccess).padding(vertical = 2.dp),
                )
            } else {
                Text(display.statusLabel, color = HexaColors.Accent, fontSize = 12.sp, maxLines = 1)
            }
            if (display.showProgress) {
                LinearProgressIndicator(
                    progress = { progress },
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape)
                        .then(
                            if (snapshot?.canSeek == true) {
                                Modifier.pointerInput(Unit) {
                                    detectTapGestures { pos -> onSeek(pos.x / size.width.toFloat()) }
                                }
                            } else {
                                Modifier
                            },
                        ),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                ControlButton(Glyph.PREVIOUS, "Faixa anterior", enabled = display.controlsEnabled && display.canSkipPrevious, size = 40, onClick = onPrevious)
                ControlButton(
                    if (display.isPlaying) Glyph.PAUSE else Glyph.PLAY,
                    if (display.isPlaying) "Pausa" else "Reproduzir",
                    enabled = display.controlsEnabled,
                    size = 52,
                    filled = true,
                    onClick = onPlayPause,
                )
                ControlButton(Glyph.NEXT, "Faixa seguinte", enabled = display.controlsEnabled && display.canSkipNext, size = 40, onClick = onNext)
            }
        }
    }
}

@Composable
private fun ControlButton(glyph: Glyph, label: String, enabled: Boolean, size: Int, filled: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(if (filled) Color.White else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        val c = if (filled) Color(0xFF14151F) else Color.White
        GlyphIcon(glyph, if (enabled) c else c.copy(alpha = 0.3f), size = (size * 0.46f).dp)
    }
}
