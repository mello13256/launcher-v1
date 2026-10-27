package com.hexa.launcher.ui.hex

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.hexa.core.apps.TargetStatus
import com.hexa.core.layout.SQRT3
import com.hexa.core.model.HiveConfig
import com.hexa.core.model.HiveVisual
import com.hexa.core.model.ImageConfig
import com.hexa.core.model.ImageFit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Transient per-frame decorations of a hive. */
class HiveDecor(
    val press: Float = 0f,
    val flash: Float = 0f,
    val pending: Boolean = false,
    val editing: Boolean = false,
    val lifted: Boolean = false,
)

private val FLASH = Color(0xFFFF1744)
private val PENDING = Color(0xFFFFFFFF)
private val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)

/**
 * Draws one hive: colour fill, optional image (clipped to the hexagon), optional app icon,
 * status badges and interaction feedback. [path] must already describe the hexagon at (cx, cy)
 * with radius [r]; it is built once per layout and reused every frame.
 */
fun DrawScope.drawHive(
    path: Path,
    cx: Float,
    cy: Float,
    r: Float,
    pointyTop: Boolean,
    hive: HiveConfig,
    icon: ImageBitmap?,
    image: ImageBitmap?,
    status: TargetStatus,
    decor: HiveDecor,
) {
    val s = (1f - 0.07f * decor.press) * (if (decor.lifted) 1.08f else 1f)
    scale(s, Offset(cx, cy)) {
        val opacity = hive.opacity.coerceIn(0f, 1f)
        drawPath(path, Color(hive.color).copy(alpha = opacity))

        val showImage = hive.visual == HiveVisual.IMAGE || hive.visual == HiveVisual.IMAGE_WITH_ICON
        val img = hive.image
        if (showImage && image != null && img != null) {
            clipPath(path) { drawFittedImage(image, img, cx, cy, r, pointyTop, opacity) }
        }

        val showIcon = hive.visual == HiveVisual.ICON || hive.visual == HiveVisual.IMAGE_WITH_ICON ||
            (showImage && image == null) // image not loaded/missing: fall back to the icon
        val iconSide = r * SQRT3 * hive.iconScale
        if (showIcon && icon != null) {
            val side = iconSide.roundToInt()
            drawImage(
                image = icon,
                dstOffset = IntOffset((cx - side / 2f).roundToInt(), (cy - side / 2f).roundToInt()),
                dstSize = IntSize(side, side),
                alpha = if (status == TargetStatus.MISSING) 0.35f else 1f,
                filterQuality = FilterQuality.Medium,
            )
        }
        if (status == TargetStatus.EMPTY && (hive.visual != HiveVisual.COLOR || decor.editing)) {
            drawGlyph(Glyph.PLUS, Offset(cx, cy), iconSide * 0.55f, Color.White.copy(alpha = 0.55f))
        }
        if (status == TargetStatus.MISSING) {
            val b = r * 0.36f
            drawGlyph(Glyph.ALERT, Offset(cx + r * 0.42f, cy + r * 0.42f), b, Color(0xFFFFC266))
        }
        if (hive.outlineWidth > 0f) {
            drawPath(path, Color(hive.outlineColor), style = Stroke(hive.outlineWidth * density))
        }
        if (decor.pending) drawPath(path, PENDING, style = Stroke(3f * density))
        if (decor.press > 0f) drawPath(path, Color.White.copy(alpha = 0.22f * decor.press))
        if (decor.flash > 0f) drawPath(path, FLASH.copy(alpha = 0.55f * decor.flash))
        if (decor.editing) drawPath(path, Color.White.copy(alpha = 0.75f), style = Stroke(1.5f * density, pathEffect = dash))
    }
}

/** Places [image] inside the hexagon's bounding box according to fit, zoom and focus point. */
private fun DrawScope.drawFittedImage(image: ImageBitmap, cfg: ImageConfig, cx: Float, cy: Float, r: Float, pointyTop: Boolean, alpha: Float) {
    val bw = if (pointyTop) r * SQRT3 else 2f * r
    val bh = if (pointyTop) 2f * r else r * SQRT3
    val iw = image.width.toFloat()
    val ih = image.height.toFloat()
    if (iw <= 0f || ih <= 0f) return
    val zoom = cfg.zoom.coerceIn(1f, 4f)
    val (dw, dh) = when (cfg.fit) {
        ImageFit.COVER -> max(bw / iw, bh / ih).let { iw * it * zoom to ih * it * zoom }
        ImageFit.CONTAIN -> min(bw / iw, bh / ih).let { iw * it * zoom to ih * it * zoom }
        ImageFit.STRETCH -> bw * zoom to bh * zoom
    }
    // Overflowing images pan towards the focus point; smaller ones slide inside the free space.
    val dx = if (dw > bw) -cfg.offsetX * (dw - bw) / 2f else cfg.offsetX * (bw - dw) / 2f
    val dy = if (dh > bh) -cfg.offsetY * (dh - bh) / 2f else cfg.offsetY * (bh - dh) / 2f
    drawImage(
        image = image,
        dstOffset = IntOffset((cx - dw / 2f + dx).roundToInt(), (cy - dh / 2f + dy).roundToInt()),
        dstSize = IntSize(dw.roundToInt().coerceAtLeast(1), dh.roundToInt().coerceAtLeast(1)),
        alpha = alpha,
        filterQuality = FilterQuality.Medium,
    )
}

/** Draws [image] covering the whole [w]x[h] area (home background). */
fun DrawScope.drawBackgroundImage(image: ImageBitmap, cfg: ImageConfig) {
    val bw = size.width
    val bh = size.height
    val iw = image.width.toFloat()
    val ih = image.height.toFloat()
    if (iw <= 0f || ih <= 0f) return
    val zoom = cfg.zoom.coerceIn(1f, 4f)
    val (dw, dh) = when (cfg.fit) {
        ImageFit.COVER -> max(bw / iw, bh / ih).let { iw * it * zoom to ih * it * zoom }
        ImageFit.CONTAIN -> min(bw / iw, bh / ih).let { iw * it * zoom to ih * it * zoom }
        ImageFit.STRETCH -> bw * zoom to bh * zoom
    }
    val dx = if (dw > bw) -cfg.offsetX * (dw - bw) / 2f else cfg.offsetX * (bw - dw) / 2f
    val dy = if (dh > bh) -cfg.offsetY * (dh - bh) / 2f else cfg.offsetY * (bh - dh) / 2f
    drawImage(
        image = image,
        dstOffset = IntOffset((bw / 2f - dw / 2f + dx).roundToInt(), (bh / 2f - dh / 2f + dy).roundToInt()),
        dstSize = IntSize(dw.roundToInt().coerceAtLeast(1), dh.roundToInt().coerceAtLeast(1)),
        filterQuality = FilterQuality.Medium,
    )
}
