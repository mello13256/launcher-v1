package com.hexa.core.persist

import com.hexa.core.model.BackgroundMode
import com.hexa.core.model.CENTER_HIVE_ID
import com.hexa.core.model.ComboConfig
import com.hexa.core.model.ComboInput
import com.hexa.core.model.Corner
import com.hexa.core.model.HiveVisual
import com.hexa.core.model.ImageConfig
import com.hexa.core.model.ImageFit
import com.hexa.core.model.LauncherAction
import com.hexa.core.model.LauncherConfig
import com.hexa.core.model.LayoutPreset
import com.hexa.core.model.SearchPosition
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PersistenceTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val rich = LauncherConfig()
        .withHive(0) {
            it.copy(
                tapAction = LauncherAction.OpenApp("com.discord/.Main", "Discord"),
                longPressAction = LauncherAction.AppShortcut("com.discord", "dm", 0, "DM"),
                color = 0xFF123456, opacity = 0.4f, visual = HiveVisual.IMAGE_WITH_ICON,
                image = ImageConfig("img_1.webp", ImageFit.CONTAIN, 1.5f, 0.2f, -0.3f),
                iconScale = 0.7f, sizeScale = 0.8f, outlineWidth = 2f, showLabel = true,
            )
        }
        .withHive(CENTER_HIVE_ID) { it.copy(tapAction = LauncherAction.RunCombo("ab"), color = 0xFF000000) }
        .withHive(3) { it.copy(tapAction = LauncherAction.OpenUri("https://example.org", "Example")) }
        .withHive(4) { it.copy(tapAction = LauncherAction.MusicPlayPause) }
        .let { c ->
            c.copy(
                combos = listOf(
                    ComboConfig("ab", listOf(0, 1), ordered = false, action = LauncherAction.OpenApp("com.spotify/.M", "Spotify")),
                    ComboConfig("abc", listOf(0, 1, 2), ordered = true, action = LauncherAction.OpenSettings, name = "Def"),
                ),
                layout = c.layout.copy(preset = LayoutPreset.HONEYCOMB, gap = 0.1f, centerScale = 1.3f),
                comboSettings = c.comboSettings.copy(windowMs = 420, input = ComboInput.SWIPE),
                search = c.search.copy(position = SearchPosition.TOP, autoLaunchSingle = true),
                music = c.music.copy(corner = Corner.BOTTOM_START, preferredPlayerKey = "com.spotify/.M"),
                animation = c.animation.copy(enabled = false, speed = 0.7f),
                interaction = c.interaction.copy(triggerOnPress = true, lockLayout = true),
                background = c.background.copy(mode = BackgroundMode.IMAGE, image = ImageConfig("bg.webp"), dim = 0.3f),
            )
        }

    @Test
    fun jsonRoundTripKeepsEverything() {
        val text = ConfigCodec.encode(rich)
        assertEquals(rich, ConfigCodec.decode(text))
    }

    @Test
    fun unknownFieldsFromNewerVersionsAreIgnored() {
        val text = ConfigCodec.encode(rich).replaceFirst("{", "{\"futureField\":123,")
        assertEquals(rich, ConfigCodec.decode(text))
    }

    @Test
    fun sanitizeRepairsBrokenHivesWithoutLosingValidData() {
        val broken = rich.copy(
            hives = rich.hives.filter { it.id != 7 } +            // missing hive
                rich.hive(0).copy(color = 0xFFFFFFFF) +            // duplicate id
                rich.hive(1).copy(id = 99),                        // unknown id
            combos = rich.combos + ComboConfig("bad", listOf(0, 77), action = LauncherAction.OpenSearch),
        ).withHive(2) { it.copy(slot = rich.hive(1).slot, opacity = 7f) } // slot clash + out of range
        val fixed = ConfigCodec.sanitize(broken)
        assertEquals((0 until 16).toList(), fixed.hives.map { it.id })
        assertEquals(16, fixed.hives.map { it.slot }.toSet().size)
        assertEquals(rich.hive(0), fixed.hive(0))
        assertEquals(1f, fixed.hive(2).opacity, 0f)
        assertEquals(rich.combos, fixed.combos)
    }

    private fun TestScope.state(file: File, debounce: Long = 400) = PersistedState(
        file = AtomicTextFile(file),
        default = LauncherConfig(),
        encode = ConfigCodec::encode,
        decode = ConfigCodec::decode,
        scope = this,
        io = StandardTestDispatcher(testScheduler),
        debounceMs = debounce,
    )

    @Test
    fun missingFileGivesDefaults() = runTest {
        val s = state(File(tmp.root, "config.json"))
        assertFalse(s.load())
        assertEquals(LauncherConfig(), s.value)
        assertTrue(s.loaded.value)
    }

    @Test
    fun burstOfUpdatesWritesOnce() = runTest {
        val file = File(tmp.root, "config.json")
        val s = state(file)
        s.load()
        for (i in 1..50) s.update { c -> c.withHive(0) { it.copy(opacity = i / 50f) } }
        advanceTimeBy(399); runCurrent()
        assertEquals(0, s.writeCount)
        advanceUntilIdle()
        assertEquals(1, s.writeCount)
        assertEquals(1f, ConfigCodec.decode(file.readText()).hive(0).opacity, 0f)
    }

    @Test
    fun unchangedStateIsNotWritten() = runTest {
        val file = File(tmp.root, "config.json")
        val s = state(file)
        s.load()
        s.update { it.withHive(0) { h -> h.copy(opacity = 0.5f) } }
        advanceUntilIdle()
        assertEquals(1, s.writeCount)
        s.update { it } // no-op
        s.update { it.withHive(0) { h -> h.copy(opacity = 0.5f) } } // same value
        advanceUntilIdle()
        s.flush()
        assertEquals(1, s.writeCount)
        // Change and change back before the debounce fires: content equals disk, so no write.
        s.update { it.withHive(0) { h -> h.copy(opacity = 0.9f) } }
        s.update { it.withHive(0) { h -> h.copy(opacity = 0.5f) } }
        advanceUntilIdle()
        assertEquals(1, s.writeCount)
    }

    @Test
    fun restartRestoresConfiguration() = runTest {
        val file = File(tmp.root, "config.json")
        val first = state(file)
        first.load()
        first.set(rich)
        first.flush()

        // "Reboot": a brand new instance reading the same file.
        val second = state(file)
        assertTrue(second.load())
        assertEquals(rich, second.value)
    }

    @Test
    fun corruptFileIsQuarantinedAndDefaultsUsed() = runTest {
        val file = File(tmp.root, "config.json")
        file.writeText("{ this is not json")
        val s = state(file)
        assertFalse(s.load())
        assertEquals(LauncherConfig(), s.value)
        assertFalse(file.exists())
        assertTrue(tmp.root.listFiles()!!.any { it.name.startsWith("config.json.corrupt-") })
    }

    @Test
    fun atomicWriteLeavesNoTempFile() {
        val f = AtomicTextFile(File(tmp.root, "a/b/c.json"))
        f.write("one")
        f.write("two")
        assertEquals("two", f.readOrNull())
        assertEquals(listOf("c.json"), File(tmp.root, "a/b").list()!!.toList())
    }
}
