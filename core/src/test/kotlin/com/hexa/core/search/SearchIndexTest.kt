package com.hexa.core.search

import com.hexa.core.model.ComboConfig
import com.hexa.core.model.LauncherAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SearchIndexTest {

    private val apps = listOf(
        "Google Maps" to "com.google.android.apps.maps",
        "Gmail" to "com.google.android.gm",
        "Câmara" to "com.android.camera",
        "WhatsApp" to "com.whatsapp",
        "WhatsApp Business" to "com.whatsapp.w4b",
        "Spotify" to "com.spotify.music",
        "Discord" to "com.discord",
        "Definições" to "com.android.settings",
        "Calendário" to "com.google.android.calendar",
        "Calculadora" to "com.google.android.calculator",
        "Telefone" to "com.google.android.dialer",
        "YouTube Music" to "com.google.android.apps.youtube.music",
    ).map { (label, pkg) -> SearchItem("$pkg/.Main", label, pkg) }

    private val index = SearchIndex(apps)

    private fun labels(q: String, matchPackage: Boolean = false) =
        SearchIndex(apps).search(q, limit = 20, matchPackage = matchPackage).map { it.item.label }

    @Test
    fun emptyQueryReturnsNothing() {
        assertTrue(index.search("   ").isEmpty())
    }

    @Test
    fun partialAndCaseInsensitive() {
        assertEquals("Spotify", labels("SPO").first())
        assertEquals("Spotify", labels("spot").first())
        assertTrue("Discord" in labels("cord"))
    }

    @Test
    fun accentsAreIgnored() {
        assertEquals("Câmara", labels("camara").first())
        assertEquals("Definições", labels("definicoes").first())
        assertEquals("Câmara", labels("CÂM").first())
    }

    @Test
    fun prefixRanksAboveContains() {
        val r = labels("ca")
        assertEquals(setOf("Câmara", "Calendário", "Calculadora"), r.take(3).toSet())
        // "ca" inside a word comes after every prefix match.
        val r2 = labels("ma")
        assertTrue(r2.indexOf("Google Maps") < r2.indexOf("Câmara"))
    }

    @Test
    fun exactMatchWins() {
        assertEquals("WhatsApp", labels("whatsapp").first())
        assertEquals("Gmail", labels("gmail").first())
    }

    @Test
    fun wordAndInitialsMatches() {
        assertEquals("YouTube Music", labels("music").first())
        assertEquals("Google Maps", labels("gm").first { it != "Gmail" })
        assertTrue("Google Maps" in labels("gm").take(2))
        assertEquals("YouTube Music", labels("ytm").first())
    }

    @Test
    fun camelCaseWordsMatch() {
        assertTrue("WhatsApp" in labels("app"))
    }

    @Test
    fun fuzzySubsequenceIsLowerRanked() {
        val r = labels("sptfy")
        assertEquals("Spotify", r.first())
    }

    @Test
    fun packageNameOnlyWhenEnabled() {
        assertTrue("Telefone" !in labels("dialer"))
        assertTrue("Telefone" in labels("dialer", matchPackage = true))
    }

    @Test
    fun usageBoostReordersButNeverAddsNonMatches() {
        val idx = SearchIndex(apps)
        val boosted = idx.search("ca", usageBoost = { if (it.startsWith("com.google.android.calculator")) 500f else 0f })
        assertEquals("Calculadora", boosted.first().item.label)
        val none = SearchIndex(apps).search("zzzz", usageBoost = { 999f })
        assertTrue(none.isEmpty())
    }

    @Test
    fun incrementalSearchMatchesFullSearch() {
        val rnd = Random(42)
        val big = (0 until 600).map { i ->
            val words = (0 until rnd.nextInt(1, 4)).joinToString(" ") { randomWord(rnd) }
            SearchItem("pkg$i/.A", words, "pkg.$i")
        }
        val incremental = SearchIndex(big)
        repeat(200) {
            val base = big[rnd.nextInt(big.size)].label
            val target = base.substring(0, rnd.nextInt(1, base.length + 1))
            for (n in 1..target.length) {
                val q = target.substring(0, n)
                val a = incremental.search(q, limit = 50).map { it.item.key to it.score }
                val b = SearchIndex(big).search(q, limit = 50).map { it.item.key to it.score }
                assertEquals("query '$q'", b, a)
            }
            incremental.reset()
        }
    }

    @Test
    fun staysFastWithManyApps() {
        val rnd = Random(7)
        val big = (0 until 5_000).map { i -> SearchItem("p$i/.A", randomWord(rnd) + " " + randomWord(rnd), "p.$i") }
        val idx = SearchIndex(big)
        val queries = (0 until 500).map { randomWord(rnd).take(rnd.nextInt(1, 5)) }
        idx.search("warmup")
        val start = System.nanoTime()
        for (q in queries) {
            idx.reset()
            idx.search(q)
        }
        val perQueryMs = (System.nanoTime() - start) / 1e6 / queries.size
        println("SearchIndex: 5000 apps, cold query avg = %.3f ms".format(perQueryMs))
        assertTrue("cold query took $perQueryMs ms", perQueryMs < 20.0)
    }

    @Test
    fun normalizeCollapsesSeparators() {
        assertEquals("google maps", normalizeForSearch("  Google   Maps! "))
        assertEquals("acao", normalizeForSearch("Ação"))
    }

    @Test
    fun combosFoundByTargetAppAndByResults() {
        val combos = listOf(
            ComboConfig("c1", listOf(0, 1), action = LauncherAction.OpenApp("com.discord/.Main", "Discord")),
            ComboConfig("c2", listOf(2, 3), action = LauncherAction.OpenApp("com.spotify.music/.Main", "Spotify")),
            ComboConfig("c3", listOf(4, 5), action = LauncherAction.OpenSearch, name = "Procurar"),
        )
        val label: (LauncherAction) -> String = {
            when (it) {
                is LauncherAction.OpenApp -> it.label
                LauncherAction.OpenSearch -> "Abrir pesquisa"
                else -> ""
            }
        }
        val hive: (Int) -> String = { if (it == 4) "Telefone" else "" }
        assertEquals(listOf("c1"), ComboSearch.find("disc", combos, label, hive).map { it.combo.id })
        assertEquals(listOf("c2"), ComboSearch.find("spo", combos, label, hive).map { it.combo.id })
        assertEquals("c3", ComboSearch.find("proc", combos, label, hive).first().combo.id)
        assertEquals("c3", ComboSearch.find("tele", combos, label, hive).first().combo.id)
        assertEquals(
            listOf("c2"),
            ComboSearch.find("xyz", combos, label, hive, resultKeys = setOf("com.spotify.music/.Main")).map { it.combo.id },
        )
    }

    @Test
    fun usageStatsRecordAndBoost() {
        var s = UsageStats()
        val now = 1_000_000_000L
        repeat(5) { s = s.record("a", now) }
        assertEquals(5, s.entries.getValue("a").count)
        assertTrue(s.boost("a", now) > s.boost("b", now))
        assertTrue(s.boost("a", now) > s.boost("a", now + 30L * 24 * 3_600_000))
    }

    private fun randomWord(rnd: Random): String {
        val letters = "abcdefghijklmnopqrstuvwxyzáéíóúç"
        return (0 until rnd.nextInt(3, 10)).map { letters[rnd.nextInt(letters.length)] }.joinToString("")
            .replaceFirstChar { it.uppercase() }
    }
}
