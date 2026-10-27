package com.hexa.launcher.ui.common

import android.net.Uri
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.hexa.core.apps.AppResolution
import com.hexa.core.apps.TargetStatus
import com.hexa.core.combo.ComboIssue
import com.hexa.core.combo.ComboValidator
import com.hexa.core.layout.Arrangement
import com.hexa.core.model.ComboConfig
import com.hexa.core.model.HiveConfig
import com.hexa.core.model.HiveVisual
import com.hexa.core.model.ImageConfig
import com.hexa.core.model.LauncherAction
import com.hexa.core.model.LauncherConfig
import com.hexa.core.model.LayoutPreset
import com.hexa.core.model.builtInLabel
import com.hexa.launcher.LauncherGraph
import com.hexa.launcher.apps.AppShortcutInfo
import com.hexa.launcher.apps.AppsSnapshot
import com.hexa.launcher.apps.IconSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Bitmaps and statuses needed to draw the hives, kept in snapshot state so the canvas redraws
 * (without recomposing) when an icon finishes loading. Refreshed only when the configuration or
 * the installed apps change.
 */
class HiveVisuals(private val graph: LauncherGraph, scope: CoroutineScope) {
    val icons = mutableStateMapOf<Int, ImageBitmap>()
    val images = mutableStateMapOf<String, ImageBitmap>()
    val status = mutableStateMapOf<Int, TargetStatus>()

    init {
        scope.launch {
            combine(graph.config.state, graph.apps.snapshot) { c, a -> c to a }.collectLatest { (config, apps) ->
                refresh(config, apps)
            }
        }
    }

    private suspend fun refresh(config: LauncherConfig, apps: AppsSnapshot) {
        for (hive in config.hives) {
            status[hive.id] = if (!apps.loaded) TargetStatus.READY else AppResolution.status(hive.tapAction, apps.catalog)
        }
        // Icons: hives first (they are on screen), most important (centre) first.
        for (hive in config.hives.sortedByDescending { it.id }) {
            val key = AppResolution.iconKey(hive.tapAction, apps.catalog)
            if (key == null) {
                icons.remove(hive.id)
                continue
            }
            val bmp = graph.icons.load(key, IconSize.LARGE)
            if (bmp == null) icons.remove(hive.id)
            else if (icons[hive.id]?.asAndroidBitmap() !== bmp) icons[hive.id] = bmp.asImageBitmap()
        }
        val wanted = LauncherGraph.referencedImages(config)
        images.keys.filter { it !in wanted }.forEach { images.remove(it) }
        for (name in wanted) {
            if (images.containsKey(name)) continue
            graph.images.load(name)?.let { images[name] = it.asImageBitmap() }
        }
    }
}

/**
 * Editing operations shared by the home edit mode and the settings screen. All writes go to the
 * in-memory configuration (instant UI update); persistence is debounced by [LauncherGraph.config].
 */
class EditorModel(val graph: LauncherGraph, private val scope: CoroutineScope) {

    val config: StateFlow<LauncherConfig> get() = graph.config.state
    val apps: StateFlow<AppsSnapshot> get() = graph.apps.snapshot
    val visuals = HiveVisuals(graph, scope)

    fun update(transform: (LauncherConfig) -> LauncherConfig) = graph.config.update(transform)

    fun updateHive(id: Int, transform: (HiveConfig) -> HiveConfig) = update { it.withHive(id, transform) }

    fun setTapAction(id: Int, action: LauncherAction) = updateHive(id) { it.copy(tapAction = action) }

    fun setLongPressAction(id: Int, action: LauncherAction) = updateHive(id) { it.copy(longPressAction = action) }

    /** Returns an error message or null. */
    fun moveHive(id: Int, slot: Int): String? = when (val r = Arrangement.move(config.value, id, slot)) {
        is Arrangement.MoveResult.Moved -> {
            graph.config.set(r.config)
            null
        }
        is Arrangement.MoveResult.Rejected -> r.reason
    }

    fun changePreset(preset: LayoutPreset) = update { Arrangement.changePreset(it, preset) }

    fun resetHive(id: Int) = updateHive(id) { HiveConfig(id = id, slot = it.slot, color = it.color) }

    fun importHiveImage(id: Int, uri: Uri, onDone: (Boolean) -> Unit = {}) {
        scope.launch {
            val name = graph.images.import(uri, maxSide = 768)
            if (name == null) {
                onDone(false)
                return@launch
            }
            val old = config.value.hive(id).image?.path
            updateHive(id) {
                it.copy(
                    image = ImageConfig(name),
                    visual = if (it.visual == HiveVisual.IMAGE_WITH_ICON || it.tapAction is LauncherAction.OpenApp) HiveVisual.IMAGE_WITH_ICON else HiveVisual.IMAGE,
                )
            }
            old?.let { graph.images.delete(it) }
            onDone(true)
        }
    }

    fun removeHiveImage(id: Int) {
        val old = config.value.hive(id).image?.path ?: return
        updateHive(id) { it.copy(image = null, visual = if (it.visual == HiveVisual.IMAGE) HiveVisual.COLOR else HiveVisual.ICON) }
        scope.launch { graph.images.delete(old) }
    }

    fun importBackground(uri: Uri, onDone: (Boolean) -> Unit = {}) {
        scope.launch {
            val name = graph.images.import(uri, maxSide = 2048)
            if (name == null) {
                onDone(false)
                return@launch
            }
            val old = config.value.background.image?.path
            update { it.copy(background = it.background.copy(image = ImageConfig(name), mode = com.hexa.core.model.BackgroundMode.IMAGE)) }
            old?.let { graph.images.delete(it) }
            onDone(true)
        }
    }

    /** Validates and stores [combo]. Returns the blocking issues (empty when saved). */
    fun saveCombo(combo: ComboConfig): List<ComboIssue> {
        val existing = config.value.combos
        val errors = ComboValidator.errors(combo, existing)
        if (errors.isNotEmpty()) return errors
        update { c ->
            val list = if (c.combos.any { it.id == combo.id }) c.combos.map { if (it.id == combo.id) combo else it } else c.combos + combo
            c.copy(combos = list)
        }
        return emptyList()
    }

    fun deleteCombo(id: String) = update { c ->
        c.copy(
            combos = c.combos.filter { it.id != id },
            // Hives that ran this combination fall back to no action instead of a dangling reference.
            hives = c.hives.map { h ->
                h.copy(
                    tapAction = if ((h.tapAction as? LauncherAction.RunCombo)?.comboId == id) LauncherAction.None else h.tapAction,
                    longPressAction = if ((h.longPressAction as? LauncherAction.RunCombo)?.comboId == id) LauncherAction.None else h.longPressAction,
                )
            },
        )
    }

    fun setComboEnabled(id: String, enabled: Boolean) = update { c ->
        c.copy(combos = c.combos.map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    suspend fun shortcuts(packageName: String, userSerial: Long): List<AppShortcutInfo> =
        graph.shortcuts.shortcutsFor(packageName, userSerial)

    fun actionLabel(action: LauncherAction, apps: AppsSnapshot = this.apps.value, config: LauncherConfig = this.config.value): String =
        when (action) {
            is LauncherAction.OpenApp -> apps.label(action.appKey) ?: action.label.ifEmpty { "Aplicação removida" }
            is LauncherAction.AppShortcut -> action.label.ifEmpty { action.shortcutId }
            is LauncherAction.OpenUri -> action.label.ifEmpty { action.uri }
            is LauncherAction.RunCombo -> config.combo(action.comboId)?.let { c -> "Combinação: " + (c.name.ifEmpty { actionLabel(c.action, apps, config) }) }
                ?: "Combinação removida"
            else -> action.builtInLabel().orEmpty()
        }

    fun hiveLabel(id: Int, apps: AppsSnapshot = this.apps.value, config: LauncherConfig = this.config.value): String {
        val hive = config.hives.firstOrNull { it.id == id } ?: return ""
        return if (hive.tapAction is LauncherAction.None) "" else actionLabel(hive.tapAction, apps, config)
    }

    fun newComboId(): String = "c" + System.currentTimeMillis().toString(36) + (0..999).random()
}
