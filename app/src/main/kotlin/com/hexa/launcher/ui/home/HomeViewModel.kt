package com.hexa.launcher.ui.home

import android.app.Application
import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hexa.core.apps.AppResolution
import com.hexa.core.apps.TargetStatus
import com.hexa.core.combo.ComboEngine
import com.hexa.core.combo.Resolution
import com.hexa.core.home.HomeOverlays
import com.hexa.core.home.Overlay
import com.hexa.core.layout.LayoutGeometry
import com.hexa.core.layout.SQRT3
import com.hexa.core.metrics.Metric
import com.hexa.core.model.ComboConfig
import com.hexa.core.model.LauncherAction
import com.hexa.core.model.LauncherConfig
import com.hexa.core.model.MusicTapAction
import com.hexa.core.search.ComboSearch
import com.hexa.core.search.SearchHit
import com.hexa.launcher.InternalKeys
import com.hexa.launcher.graph
import com.hexa.launcher.launch.ActionExecutor
import com.hexa.launcher.launch.LaunchOutcome
import com.hexa.launcher.ui.common.EditorModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/** Implemented by the activity: things that need an Activity/View. */
interface LaunchHost {
    val executor: ActionExecutor
    val rootView: View
    fun haptic(kind: Haptic)
    fun toast(message: String)
    fun openSettings()
    fun startIntent(intent: Intent): Boolean
}

enum class Haptic { TAP, CONFIRM, REJECT, LONG_PRESS }

/** Where the hive field is on screen, so launches can animate from the touched hive. */
class FieldPlacement(val geometry: LayoutGeometry, val originX: Float, val originY: Float)

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    val graph = app.graph
    val editor = EditorModel(graph, viewModelScope)
    val config: StateFlow<LauncherConfig> = graph.config.state
    val loaded: StateFlow<Boolean> = graph.config.loaded

    var host: LaunchHost? = null
    var placement: FieldPlacement? = null

    // ---- overlays -----------------------------------------------------------------------------

    private val overlays = HomeOverlays()
    var overlay by mutableStateOf<Overlay>(Overlay.None)
        private set

    /** Combination list / editor dialogs opened from edit mode. */
    var comboEditor by mutableStateOf<ComboConfig?>(null)
        private set
    var showComboList by mutableStateOf(false)
        private set

    private fun overlays(block: HomeOverlays.() -> Unit) {
        overlays.block()
        overlay = overlays.current
    }

    // ---- combination engine -------------------------------------------------------------------

    val engine = ComboEngine(viewModelScope, SystemClock::uptimeMillis, ::onResolved)
    val pending get() = engine.pending

    private val _flash = MutableSharedFlow<List<Int>>(extraBufferCapacity = 4)

    /** Hives to flash red: an invalid or incomplete sequence. */
    val flash: SharedFlow<List<Int>> = _flash.asSharedFlow()

    private var tapEnteredNanos = 0L

    /** Set after a successful launch: the home screen resets itself when it goes to background. */
    private var resetOnStop = false

    init {
        viewModelScope.launch {
            config.collect { c ->
                engine.configure(c.combos, c.comboSettings)
                host?.executor?.animation = c.animation.launchAnimation
            }
        }
        viewModelScope.launch {
            graph.apps.snapshot.collect { if (overlay == Overlay.Search) runSearch(query) }
        }
    }

    fun attach(host: LaunchHost) {
        this.host = host
        host.executor.animation = config.value.animation.launchAnimation
    }

    fun detach(host: LaunchHost) {
        if (this.host === host) this.host = null
    }

    /** True when a tap on [hiveId] can trigger on finger-down (setting enabled and no combo can start there). */
    fun triggersOnPress(hiveId: Int): Boolean {
        val c = config.value
        if (!c.interaction.triggerOnPress) return false
        return !engine.mayWait(hiveId) && !engine.canStartSwipe(hiveId)
    }

    fun onHiveTap(hiveId: Int, eventTime: Long) {
        tapEnteredNanos = SystemClock.elapsedRealtimeNanos()
        engine.onTap(hiveId, eventTime)
    }

    fun onHiveSwipe(path: List<Int>, eventTime: Long) {
        tapEnteredNanos = SystemClock.elapsedRealtimeNanos()
        engine.onSwipe(path, eventTime)
    }

    fun onHiveLongPress(hiveId: Int) {
        val hive = config.value.hive(hiveId)
        host?.haptic(Haptic.LONG_PRESS)
        if (hive.longPressAction !is LauncherAction.None) {
            perform(hive.longPressAction, hiveId, SystemClock.uptimeMillis())
        } else {
            openHiveEditor(hiveId)
        }
    }

    private fun onResolved(r: Resolution) {
        val perf = graph.perf
        perf.record(Metric.TOUCH_TO_RECOGNITION, (r.resolvedAt - r.eventTime).toDouble())
        if (!r.waited) perf.recordNanos(Metric.RECOGNITION_PROCESSING, SystemClock.elapsedRealtimeNanos() - tapEnteredNanos)
        when (r) {
            is Resolution.Single -> performHive(r.hive, r.eventTime)
            is Resolution.Combo -> {
                perf.record(Metric.COMBO_RECOGNITION, (r.resolvedAt - r.eventTime).toDouble())
                if (config.value.comboSettings.hapticFeedback) host?.haptic(Haptic.CONFIRM)
                perform(r.combo.action, r.sequence.last(), r.eventTime)
            }
            is Resolution.Invalid -> if (r.reason != Resolution.Invalid.Reason.CANCELLED) {
                _flash.tryEmit(r.sequence)
                if (config.value.comboSettings.hapticFeedback) host?.haptic(Haptic.REJECT)
            }
        }
    }

    private fun performHive(hiveId: Int, eventTime: Long) {
        val c = config.value
        val hive = c.hive(hiveId)
        val apps = graph.apps.snapshot.value
        when (if (apps.loaded) AppResolution.status(hive.tapAction, apps.catalog) else TargetStatus.READY) {
            TargetStatus.EMPTY -> if (!c.interaction.lockLayout) openHiveEditor(hiveId)
            TargetStatus.MISSING -> {
                host?.toast("A aplicação desta colmeia foi removida. Escolhe outra.")
                if (!c.interaction.lockLayout) openHiveEditor(hiveId)
            }
            TargetStatus.READY -> perform(hive.tapAction, hiveId, eventTime)
        }
    }

    /** Runs [action]. [sourceHive] is used for the launch animation origin. */
    fun perform(action: LauncherAction, sourceHive: Int?, eventTime: Long?) {
        val h = host
        when (action) {
            LauncherAction.None -> Unit
            is LauncherAction.OpenApp -> h?.let { launched(it.executor.openApp(action.appKey, bounds(sourceHive), it.rootView), action.appKey, eventTime) }
            is LauncherAction.AppShortcut -> h?.let {
                launched(it.executor.openShortcut(action.packageName, action.shortcutId, action.userSerial, bounds(sourceHive), it.rootView), null, eventTime)
            }
            is LauncherAction.OpenUri -> h?.let { launched(it.executor.openUri(action.uri, bounds(sourceHive), it.rootView), null, eventTime) }
            is LauncherAction.RunCombo -> {
                val combo = config.value.combo(action.comboId)
                if (combo == null) h?.toast("A combinação associada foi eliminada") else perform(combo.action, sourceHive, eventTime)
            }
            LauncherAction.OpenSearch -> openSearch()
            LauncherAction.OpenSettings -> h?.openSettings()
            LauncherAction.EnterEditMode -> enterEdit()
            LauncherAction.MusicPlayPause -> graph.music.playPause()
            LauncherAction.MusicNext -> graph.music.next()
            LauncherAction.MusicPrevious -> graph.music.previous()
            LauncherAction.MusicExpand -> overlays { expandMusic() }
        }
    }

    private fun launched(outcome: LaunchOutcome, appKey: String?, eventTime: Long?) {
        when (outcome) {
            is LaunchOutcome.Started -> {
                if (eventTime != null) graph.perf.record(Metric.TOUCH_TO_LAUNCH, (SystemClock.uptimeMillis() - eventTime).toDouble())
                appKey?.let(graph::recordLaunch)
                resetOnStop = true
                graph.logPerf("launch", "dispatch=%.2fms".format(outcome.dispatchMs))
            }
            LaunchOutcome.Missing -> host?.toast("Aplicação não encontrada")
            is LaunchOutcome.Failed -> host?.toast(outcome.message)
        }
    }

    private fun bounds(hiveId: Int?): Rect? {
        val p = placement ?: return null
        val id = hiveId ?: return null
        val hive = config.value.hives.firstOrNull { it.id == id } ?: return null
        if (hive.slot !in p.geometry.slots.indices) return null
        val s = p.geometry.slot(hive.slot)
        val r = s.radius * hive.sizeScale
        val hw = if (p.geometry.pointyTop) r * SQRT3 / 2f else r
        val hh = if (p.geometry.pointyTop) r else r * SQRT3 / 2f
        val x = p.originX + s.cx
        val y = p.originY + s.cy
        return Rect((x - hw).toInt(), (y - hh).toInt(), (x + hw).toInt(), (y + hh).toInt())
    }

    // ---- search -------------------------------------------------------------------------------

    var query by mutableStateOf("")
        private set
    var results by mutableStateOf<List<SearchHit>>(emptyList())
        private set
    var comboResults by mutableStateOf<List<ComboSearch.ComboHit>>(emptyList())
        private set

    private var searchOpenedAt = 0L
    private var lastKeyAt = 0L

    fun openSearch() {
        engine.cancelPending()
        overlays { openSearch() }
        if (overlay != Overlay.Search) return
        searchOpenedAt = SystemClock.uptimeMillis()
        lastKeyAt = searchOpenedAt
        query = ""
        results = emptyList()
        comboResults = emptyList()
        graph.apps.snapshot.value.index.reset()
    }

    fun closeSearch() {
        if (overlay == Overlay.Search) overlays { close() }
        query = ""
        results = emptyList()
        comboResults = emptyList()
    }

    fun onQueryChange(q: String) {
        lastKeyAt = SystemClock.uptimeMillis()
        query = q
        runSearch(q)
    }

    /**
     * Runs synchronously on the main thread: the index lives in memory and a query costs well under
     * a millisecond for typical app counts (measured as SEARCH_QUERY), which is cheaper than any
     * thread hop and keeps results in the same frame as the keystroke.
     */
    private fun runSearch(q: String) {
        val start = SystemClock.elapsedRealtimeNanos()
        val c = config.value
        val apps = graph.apps.snapshot.value
        val usage = graph.usage.value
        val now = System.currentTimeMillis()
        val hits = apps.index.search(
            query = q,
            limit = c.search.maxResults,
            matchPackage = c.search.matchPackageName,
            usageBoost = if (c.search.rankByUsage) { key -> usage.boost(key, now) } else { _ -> 0f },
        )
        results = hits
        comboResults = if (c.search.showCombos && q.isNotBlank()) {
            ComboSearch.find(
                query = q,
                combos = c.combos,
                actionLabel = { editor.actionLabel(it, apps, c) },
                hiveLabel = { editor.hiveLabel(it, apps, c) },
                resultKeys = hits.take(3).map { it.item.key }.toSet(),
            )
        } else {
            emptyList()
        }
        graph.perf.recordNanos(Metric.SEARCH_QUERY, SystemClock.elapsedRealtimeNanos() - start)
        if (c.search.autoLaunchSingle && hits.size == 1 && q.trim().length >= 2) openResult(hits.first())
    }

    fun onSearchSubmit() {
        if (!config.value.search.enterOpensFirst) return
        results.firstOrNull()?.let(::openResult)
    }

    fun openResult(hit: SearchHit) {
        val now = SystemClock.uptimeMillis()
        graph.perf.record(Metric.SEARCH_OPEN_TO_SELECT, (now - searchOpenedAt).toDouble())
        graph.perf.record(Metric.SEARCH_KEY_TO_SELECT, (now - lastKeyAt).toDouble())
        when (hit.item.key) {
            InternalKeys.SETTINGS -> {
                closeSearch()
                host?.openSettings()
            }
            InternalKeys.EDIT -> {
                closeSearch()
                enterEdit()
            }
            InternalKeys.MUSIC -> {
                closeSearch()
                overlays { expandMusic() }
            }
            else -> perform(LauncherAction.OpenApp(hit.item.key, hit.item.label), null, now)
        }
    }

    fun runComboFromSearch(combo: ComboConfig) {
        perform(combo.action, combo.hives.lastOrNull(), SystemClock.uptimeMillis())
    }

    fun openAppDetails(key: String) = host?.executor?.openAppDetails(key)

    // ---- music --------------------------------------------------------------------------------

    fun onMusicHiveTap() {
        if (config.value.music.tapAction == MusicTapAction.PLAY_PAUSE && overlay != Overlay.MusicExpanded) {
            graph.music.playPause()
        } else {
            overlays { toggleMusic() }
        }
    }

    fun onMusicHiveLongPress() {
        // The opposite of the configured tap action, so both are always reachable.
        if (config.value.music.tapAction == MusicTapAction.PLAY_PAUSE) overlays { expandMusic() } else graph.music.playPause()
    }

    fun collapseMusic() = overlays { collapseMusic() }

    fun openMusicPlayer() {
        if (graph.music.openPlayer()) return
        val preferred = config.value.music.preferredPlayerKey
        val pkg = graph.music.state.value.sessionPackage
        val key = preferred ?: pkg?.let { p -> graph.apps.snapshot.value.apps.firstOrNull { it.packageName == p }?.key }
        if (key != null) perform(LauncherAction.OpenApp(key), null, null) else host?.toast("Nenhum leitor de música definido")
    }

    fun requestMusicAccess() {
        host?.startIntent(graph.music.accessSettingsIntent())
    }

    // ---- edit mode ----------------------------------------------------------------------------

    fun enterEdit() {
        engine.cancelPending()
        val locked = config.value.interaction.lockLayout
        overlays { enterEdit(locked) }
        if (locked) host?.toast("Disposição bloqueada — desbloqueia nas definições")
    }

    fun onBackgroundLongPress() {
        if (!config.value.interaction.longPressEdits) return
        host?.haptic(Haptic.LONG_PRESS)
        enterEdit()
    }

    fun openHiveEditor(hiveId: Int) {
        engine.cancelPending()
        val locked = config.value.interaction.lockLayout
        overlays {
            if (enterEdit(locked)) selectHive(hiveId)
        }
        if (locked) host?.toast("Disposição bloqueada — desbloqueia nas definições")
    }

    fun selectHive(id: Int?) = overlays { selectHive(id) }

    fun exitEdit() {
        comboEditor = null
        showComboList = false
        overlays { close() }
        graph.flush()
    }

    fun moveHive(hiveId: Int, slot: Int) {
        editor.moveHive(hiveId, slot)?.let { host?.toast(it) }
    }

    fun openComboList() {
        showComboList = true
    }

    fun closeComboList() {
        showComboList = false
    }

    fun editCombo(combo: ComboConfig?) {
        comboEditor = combo ?: ComboConfig(id = editor.newComboId(), hives = emptyList(), action = LauncherAction.None)
    }

    fun newComboStartingWith(hiveId: Int) {
        comboEditor = ComboConfig(id = editor.newComboId(), hives = listOf(hiveId), action = LauncherAction.None)
    }

    fun closeComboEditor() {
        comboEditor = null
    }

    // ---- lifecycle ----------------------------------------------------------------------------

    fun back(): Boolean {
        if (comboEditor != null) {
            comboEditor = null
            return true
        }
        if (showComboList) {
            showComboList = false
            return true
        }
        if (overlay == Overlay.Search) {
            closeSearch()
            return true
        }
        val closed = overlays.back()
        overlay = overlays.current
        return closed
    }

    /** Home pressed while the launcher is in front: back to the plain composition. */
    fun onHomeIntent() {
        engine.cancelPending()
        comboEditor = null
        showComboList = false
        closeSearch()
        overlays { home() }
        graph.flush()
    }

    fun onStop() {
        engine.cancelPending()
        if (resetOnStop) {
            resetOnStop = false
            closeSearch()
            if (overlay == Overlay.MusicExpanded) overlays { collapseMusic() }
        }
        graph.flush()
    }
}
