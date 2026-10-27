package com.hexa.core

import com.hexa.core.apps.AppResolution
import com.hexa.core.apps.MapAppCatalog
import com.hexa.core.apps.TargetStatus
import com.hexa.core.home.HomeOverlays
import com.hexa.core.home.Overlay
import com.hexa.core.metrics.Metric
import com.hexa.core.metrics.PerfStats
import com.hexa.core.model.AppKey
import com.hexa.core.model.LauncherAction
import com.hexa.core.model.LauncherConfig
import com.hexa.core.music.MusicAccess
import com.hexa.core.music.MusicDisplay
import com.hexa.core.music.MusicSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppResolutionTest {

    private val discord = AppKey.of("com.discord", "com.discord.Main")
    private val installed = MapAppCatalog(mapOf(discord to "Discord", AppKey.of("com.maps", "com.maps.Main") to "Maps"))

    @Test
    fun keysEncodeUserProfiles() {
        val work = AppKey.of("com.x", "com.x.A", 10)
        assertEquals("com.x", AppKey.packageOf(work))
        assertEquals("com.x.A", AppKey.classOf(work))
        assertEquals(10L, AppKey.userSerialOf(work))
        assertEquals(0L, AppKey.userSerialOf(discord))
    }

    @Test
    fun uninstalledAppIsMissingButRecoverable() {
        var config = LauncherConfig()
            .withHive(0) { it.copy(tapAction = LauncherAction.OpenApp(AppKey.of("com.gone", "com.gone.A"), "Gone"), color = 0xFF00FF00) }
            .withHive(1) { it.copy(tapAction = LauncherAction.OpenApp(discord, "Discord")) }
        assertEquals(TargetStatus.MISSING, AppResolution.status(config.hive(0).tapAction, installed))
        assertEquals(TargetStatus.READY, AppResolution.status(config.hive(1).tapAction, installed))
        assertEquals(TargetStatus.EMPTY, AppResolution.status(config.hive(2).tapAction, installed))

        // Reassigning only touches that hive's action; colour, slot and other hives are kept.
        val before = config
        config = config.withHive(0) { it.copy(tapAction = LauncherAction.OpenApp(discord, "Discord")) }
        assertEquals(before.hive(0).copy(tapAction = config.hive(0).tapAction), config.hive(0))
        assertEquals(before.hives.drop(1), config.hives.drop(1))
        assertEquals(TargetStatus.READY, AppResolution.status(config.hive(0).tapAction, installed))
    }

    @Test
    fun reinstalledAppWorksAgain() {
        val action = LauncherAction.OpenApp(AppKey.of("com.gone", "com.gone.A"), "Gone")
        assertEquals(TargetStatus.MISSING, AppResolution.status(action, installed))
        val again = MapAppCatalog(mapOf(AppKey.of("com.gone", "com.gone.A") to "Gone"))
        assertEquals(TargetStatus.READY, AppResolution.status(action, again))
    }

    @Test
    fun renamedLauncherActivityFallsBackToPackage() {
        val old = AppKey.of("com.discord", "com.discord.OldMain")
        assertEquals(discord, AppResolution.resolve(old, installed))
        assertNull(AppResolution.resolve(AppKey.of("com.discord", "com.discord.Main", 10), installed))
    }
}

class HomeOverlaysTest {

    @Test
    fun musicWidgetExpandsAndCollapses() {
        val o = HomeOverlays()
        o.toggleMusic()
        assertTrue(o.isMusicExpanded)
        o.toggleMusic()
        assertFalse(o.isMusicExpanded)
        o.expandMusic()
        assertTrue(o.back())
        assertEquals(Overlay.None, o.current)
        assertFalse(o.back())
    }

    @Test
    fun onlyOneOverlayAtATime() {
        val o = HomeOverlays()
        o.expandMusic()
        o.openSearch()
        assertTrue(o.isSearchOpen)
        assertFalse(o.isMusicExpanded)
        o.toggleMusic()
        assertTrue(o.isMusicExpanded)
    }

    @Test
    fun editModeRespectsLockAndBackClosesEditorFirst() {
        val o = HomeOverlays()
        assertFalse(o.enterEdit(locked = true))
        assertEquals(Overlay.None, o.current)
        assertTrue(o.enterEdit(locked = false))
        o.selectHive(3)
        assertEquals(Overlay.Edit(3), o.current)
        o.back()
        assertEquals(Overlay.Edit(null), o.current)
        o.openSearch() // ignored while editing
        assertTrue(o.isEditing)
        o.home()
        assertEquals(Overlay.None, o.current)
    }
}

class MusicModelTest {

    private fun snap(playing: Boolean, position: Long = 10_000, duration: Long = 200_000) = MusicSnapshot(
        packageName = "com.spotify", title = "Song", artist = "Artist", album = "Album",
        isPlaying = playing, position = position, positionUpdatedAt = 1_000, speed = 1f, duration = duration,
        canPlayPause = true, canSkipNext = true, canSkipPrevious = false, canSeek = true,
    )

    @Test
    fun positionAdvancesOnlyWhilePlaying() {
        assertEquals(15_000, snap(true).positionAt(6_000))
        assertEquals(10_000, snap(false).positionAt(6_000))
        assertEquals(200_000, snap(true).positionAt(10_000_000))
        assertEquals(0.05f, snap(false).progressAt(0), 0.0001f)
    }

    @Test
    fun displayHandlesMissingData() {
        val noAccess = MusicDisplay.from(null, MusicAccess.MEDIA_KEYS_ONLY, null)
        assertTrue(noAccess.showAccessHint)
        assertFalse(noAccess.showProgress)
        val idle = MusicDisplay.from(null, MusicAccess.GRANTED, null)
        assertEquals("Nada a reproduzir", idle.title)
        val live = MusicDisplay.from(snap(true, duration = 0).copy(title = null, artist = null), MusicAccess.GRANTED, "Rádio")
        assertEquals("Rádio", live.title)
        assertFalse(live.showProgress)
        val playing = MusicDisplay.from(snap(true), MusicAccess.GRANTED, "Spotify")
        assertEquals("Song", playing.title)
        assertEquals("Artist • Album", playing.subtitle)
        assertTrue(playing.showProgress)
        assertFalse(playing.canSkipPrevious)
    }
}

class PerfStatsTest {
    @Test
    fun summaries() {
        val p = PerfStats(capacity = 4)
        assertNull(p.summary(Metric.LAUNCH_DISPATCH))
        listOf(1.0, 2.0, 3.0, 4.0, 100.0).forEach { p.record(Metric.LAUNCH_DISPATCH, it) }
        val s = p.summary(Metric.LAUNCH_DISPATCH)!!
        assertEquals(4, s.count) // ring keeps the last 4
        assertEquals(100.0, s.max, 0.0)
        assertEquals(100.0, s.last, 0.0)
        assertEquals((2 + 3 + 4 + 100) / 4.0, s.mean, 1e-9)
        p.clear()
        assertTrue(p.all().isEmpty())
    }
}
