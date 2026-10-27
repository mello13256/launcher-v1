package com.hexa.launcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.hexa.core.model.AnimationSettings

object HexaColors {
    val Accent = Color(0xFFB388FF)
    val AccentStrong = Color(0xFF7C4DFF)
    val Surface = Color(0xFF181A26)
    val SurfaceHigh = Color(0xFF222536)
    val Background = Color(0xFF101118)
    val Error = Color(0xFFFF5370)
    val Warning = Color(0xFFFFC266)
    val Scrim = Color(0xC00A0B12)
}

private val scheme = darkColorScheme(
    primary = HexaColors.Accent,
    onPrimary = Color(0xFF1B0A3A),
    primaryContainer = HexaColors.AccentStrong,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF80D8FF),
    background = HexaColors.Background,
    surface = HexaColors.Surface,
    surfaceVariant = HexaColors.SurfaceHigh,
    surfaceContainer = HexaColors.Surface,
    surfaceContainerHigh = HexaColors.SurfaceHigh,
    surfaceContainerLow = HexaColors.Background,
    error = HexaColors.Error,
)

/** Animation preferences resolved for the UI: durations scaled, or zero when disabled. */
@Immutable
data class AnimPrefs(val enabled: Boolean, val speed: Float) {
    fun duration(baseMs: Int): Int = if (!enabled) 0 else (baseMs * speed).toInt().coerceAtLeast(1)

    companion object {
        fun from(s: AnimationSettings) = AnimPrefs(s.enabled, s.speed.coerceIn(0.5f, 1.5f))
    }
}

val LocalAnim = staticCompositionLocalOf { AnimPrefs(enabled = true, speed = 1f) }

@Composable
fun HexaTheme(anim: AnimPrefs = AnimPrefs(true, 1f), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAnim provides anim) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
