package com.hexa.launcher.ui.edit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hexa.launcher.ui.common.LabeledSlider

private val SWATCHES: List<Long> = listOf(
    0xFF0E0F17, 0xFF2B2F45, 0xFF5C6079, 0xFFFFFFFF,
    0xFF3D5AFE, 0xFF2962FF, 0xFF00B8D4, 0xFF00BFA5,
    0xFF00C853, 0xFF64DD17, 0xFFFFD600, 0xFFFFAB00,
    0xFFFF6E40, 0xFFDD2C00, 0xFFFF4081, 0xFFC51162,
    0xFFD500F9, 0xFF7C4DFF, 0xFF6200EA, 0xFF8D6E63,
)

/** Swatches plus hue/saturation/brightness sliders; reports ARGB as Long with full alpha. */
@Composable
fun ColorPicker(color: Long, onChange: (Long) -> Unit) {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toInt(), hsv)
    Column(Modifier.fillMaxWidth()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (c in SWATCHES) {
                val selected = (c and 0xFFFFFFFF) == (color and 0xFFFFFFFF)
                Canvas(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(c))
                        .border(if (selected) 3.dp else 1.dp, if (selected) Color.White else Color.White.copy(alpha = 0.25f), CircleShape)
                        .clickable { onChange(c) }
                        .semantics { contentDescription = "Cor #" + java.lang.Long.toHexString(c and 0xFFFFFF).padStart(6, '0') },
                ) {}
            }
        }
        LabeledSlider("Tom", hsv[0], 0f..360f, "${hsv[0].toInt()}°", { h -> onChange(hsvToLong(h, hsv[1], hsv[2])) })
        LabeledSlider("Saturação", hsv[1], 0f..1f, "${(hsv[1] * 100).toInt()}%", { s -> onChange(hsvToLong(hsv[0], s, hsv[2])) })
        LabeledSlider("Brilho", hsv[2], 0f..1f, "${(hsv[2] * 100).toInt()}%", { v -> onChange(hsvToLong(hsv[0], hsv[1], v)) })
    }
}

private fun hsvToLong(h: Float, s: Float, v: Float): Long =
    android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)).toLong() and 0xFFFFFFFFL
