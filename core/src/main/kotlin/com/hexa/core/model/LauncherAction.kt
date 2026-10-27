package com.hexa.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Everything a hive, the centre hive or a combination can do. Kept deliberately small: each action
 * maps to one public Android API call in the app module.
 */
@Serializable
sealed interface LauncherAction {

    @Serializable
    @SerialName("none")
    data object None : LauncherAction

    /** Opens an installed activity. [label] is a snapshot used while the app is missing. */
    @Serializable
    @SerialName("app")
    data class OpenApp(val appKey: String, val label: String = "") : LauncherAction

    /** Opens an app shortcut published through `ShortcutManager` (needs the launcher to be default). */
    @Serializable
    @SerialName("shortcut")
    data class AppShortcut(
        val packageName: String,
        val shortcutId: String,
        val userSerial: Long = 0,
        val label: String = "",
    ) : LauncherAction

    /** Opens a URI with ACTION_VIEW (web page, `tel:`, `geo:`, app deep link...). */
    @Serializable
    @SerialName("uri")
    data class OpenUri(val uri: String, val label: String = "") : LauncherAction

    /** Runs the action bound to another combination. */
    @Serializable
    @SerialName("combo")
    data class RunCombo(val comboId: String) : LauncherAction

    @Serializable
    @SerialName("search")
    data object OpenSearch : LauncherAction

    @Serializable
    @SerialName("settings")
    data object OpenSettings : LauncherAction

    @Serializable
    @SerialName("edit")
    data object EnterEditMode : LauncherAction

    @Serializable
    @SerialName("music_toggle")
    data object MusicPlayPause : LauncherAction

    @Serializable
    @SerialName("music_next")
    data object MusicNext : LauncherAction

    @Serializable
    @SerialName("music_prev")
    data object MusicPrevious : LauncherAction

    @Serializable
    @SerialName("music_expand")
    data object MusicExpand : LauncherAction
}

val LauncherAction.isNone: Boolean get() = this is LauncherAction.None

/** Built-in actions offered by the action picker (apps, shortcuts and URIs are picked separately). */
val BUILT_IN_ACTIONS: List<LauncherAction> = listOf(
    LauncherAction.OpenSearch,
    LauncherAction.OpenSettings,
    LauncherAction.EnterEditMode,
    LauncherAction.MusicPlayPause,
    LauncherAction.MusicNext,
    LauncherAction.MusicPrevious,
    LauncherAction.MusicExpand,
)

/** Portuguese description of built-in actions; app labels are resolved by the app module. */
fun LauncherAction.builtInLabel(): String? = when (this) {
    LauncherAction.None -> "Nenhuma ação"
    LauncherAction.OpenSearch -> "Abrir pesquisa"
    LauncherAction.OpenSettings -> "Definições do launcher"
    LauncherAction.EnterEditMode -> "Modo de edição"
    LauncherAction.MusicPlayPause -> "Música: reproduzir/pausar"
    LauncherAction.MusicNext -> "Música: faixa seguinte"
    LauncherAction.MusicPrevious -> "Música: faixa anterior"
    LauncherAction.MusicExpand -> "Abrir widget de música"
    is LauncherAction.OpenApp -> null
    is LauncherAction.AppShortcut -> null
    is LauncherAction.OpenUri -> null
    is LauncherAction.RunCombo -> null
}
