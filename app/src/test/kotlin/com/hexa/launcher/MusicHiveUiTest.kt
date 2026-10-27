package com.hexa.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hexa.core.home.HomeOverlays
import com.hexa.core.music.MusicAccess
import com.hexa.launcher.media.MusicState
import com.hexa.launcher.ui.home.MusicHive
import com.hexa.launcher.ui.theme.HexaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Expanding the music hive into the widget and closing it back to the compact hexagon. */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class) // no launcher graph needed for this UI test
class MusicHiveUiTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun expandsAndCollapses() {
        val overlays = HomeOverlays()
        var playPause = 0
        compose.setContent {
            var expanded by remember { mutableStateOf(false) }
            HexaTheme {
                Box(Modifier.fillMaxSize()) {
                    MusicHive(
                        state = MusicState(access = MusicAccess.MEDIA_KEYS_ONLY),
                        appLabel = null,
                        expanded = expanded,
                        // Robolectric's default screen is 320x470 px (mdpi): keep both rects on screen.
                        compact = Rect(250f, 10f, 295f, 62f),
                        expandedRect = Rect(10f, 10f, 310f, 220f),
                        color = Color.DarkGray,
                        onTap = { overlays.toggleMusic(); expanded = overlays.isMusicExpanded },
                        onLongPress = {},
                        onCollapse = { overlays.collapseMusic(); expanded = overlays.isMusicExpanded },
                        onPlayPause = { playPause++ },
                        onNext = {},
                        onPrevious = {},
                        onSeek = {},
                        onOpenPlayer = {},
                        onRequestAccess = {},
                    )
                }
            }
        }
        compose.onNodeWithContentDescription("Música").performClick()
        compose.waitForIdle()
        assertTrue(overlays.isMusicExpanded)
        compose.onNodeWithContentDescription("Fechar widget de música").assertIsDisplayed()

        // Controls work without notification access (media keys).
        compose.onNodeWithContentDescription("Reproduzir").performClick()
        assertEquals(1, playPause)

        compose.onNodeWithContentDescription("Fechar widget de música").performClick()
        compose.waitForIdle()
        assertFalse(overlays.isMusicExpanded)
        compose.onNodeWithContentDescription("Música").assertIsDisplayed()
    }
}
