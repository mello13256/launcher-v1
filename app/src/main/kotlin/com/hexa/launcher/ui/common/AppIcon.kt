package com.hexa.launcher.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import com.hexa.core.model.HiveConfig
import com.hexa.launcher.LauncherGraph
import com.hexa.launcher.apps.IconSize
import com.hexa.launcher.ui.hex.Glyph
import com.hexa.launcher.ui.hex.HexPaths
import com.hexa.launcher.ui.hex.drawGlyph
import kotlin.math.min

/** App icon loaded from the shared cache; shows a placeholder for the few ms of a cold load. */
@Composable
fun AppIcon(graph: LauncherGraph, key: String, size: Dp, modifier: Modifier = Modifier) {
    val icon by produceState<ImageBitmap?>(graph.icons.peek(key, IconSize.SMALL)?.asImageBitmap(), key) {
        if (value == null) value = graph.icons.load(key, IconSize.SMALL)?.asImageBitmap()
    }
    val bmp = icon
    if (bmp != null) {
        Image(bmp, contentDescription = null, modifier = modifier.size(size))
    } else {
        Canvas(modifier.size(size)) { drawCircle(Color.White.copy(alpha = 0.12f)) }
    }
}

/** A small hexagon showing a hive's colour and icon, used to spell out combinations. */
@Composable
fun HiveBadge(hive: HiveConfig, icon: ImageBitmap?, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val r = min(this.size.height / 2f, this.size.width / com.hexa.core.layout.SQRT3)
        val path = Path()
        HexPaths.hexagon(path, center.x, center.y, r, true, 0.16f)
        drawPath(path, Color(hive.color).copy(alpha = hive.opacity.coerceAtLeast(0.6f)))
        if (icon != null) {
            val side = (r * 1.15f).toInt()
            drawImage(
                icon,
                dstOffset = androidx.compose.ui.unit.IntOffset((center.x - side / 2f).toInt(), (center.y - side / 2f).toInt()),
                dstSize = androidx.compose.ui.unit.IntSize(side, side),
            )
        } else {
            drawGlyph(Glyph.PLUS, center, r * 0.7f, Color.White.copy(alpha = 0.5f))
        }
    }
}
