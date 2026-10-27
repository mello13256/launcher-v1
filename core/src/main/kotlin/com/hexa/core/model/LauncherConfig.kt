package com.hexa.core.model

import kotlinx.serialization.Serializable

/** Number of regular hives in the composition (ids 0..14). */
const val REGULAR_HIVE_COUNT = 15

/** Id of the special, slightly larger centre hive (the "16th hive"). */
const val CENTER_HIVE_ID = 15

/** Total configurable hives in the composition (15 regular + centre). */
const val TOTAL_HIVES = 16

/** The centre hive always occupies slot 0, which every layout preset puts at the origin. */
const val CENTER_SLOT = 0

const val CONFIG_VERSION = 1

/**
 * The full, persisted launcher configuration. Everything the user can customise lives here,
 * so a single atomic write keeps the configuration consistent.
 */
@Serializable
data class LauncherConfig(
    val version: Int = CONFIG_VERSION,
    val hives: List<HiveConfig> = DefaultConfig.hives(),
    val layout: LayoutConfig = LayoutConfig(),
    val combos: List<ComboConfig> = emptyList(),
    val comboSettings: ComboSettings = ComboSettings(),
    val search: SearchSettings = SearchSettings(),
    val music: MusicSettings = MusicSettings(),
    val animation: AnimationSettings = AnimationSettings(),
    val interaction: InteractionSettings = InteractionSettings(),
    val background: BackgroundConfig = BackgroundConfig(),
) {
    fun hive(id: Int): HiveConfig = hives.first { it.id == id }

    fun hiveAtSlot(slot: Int): HiveConfig? = hives.firstOrNull { it.slot == slot }

    fun combo(id: String): ComboConfig? = combos.firstOrNull { it.id == id }

    fun withHive(id: Int, transform: (HiveConfig) -> HiveConfig): LauncherConfig =
        copy(hives = hives.map { if (it.id == id) transform(it) else it })
}

@Serializable
enum class HiveVisual {
    /** Coloured hexagon with the original app icon. */
    ICON,

    /** Custom image filling the hexagon, no icon. */
    IMAGE,

    /** Custom image with the app icon on top. */
    IMAGE_WITH_ICON,

    /** Plain colour, no icon or image (recognised by colour/position only). */
    COLOR,
}

@Serializable
enum class ImageFit { COVER, CONTAIN, STRETCH }

/**
 * A custom image stored in the launcher's private storage. [path] is a file name relative to the
 * launcher image directory, never a content URI, so it survives reboots without URI grants.
 */
@Serializable
data class ImageConfig(
    val path: String,
    val fit: ImageFit = ImageFit.COVER,
    /** Extra zoom applied after fitting, 1..4. */
    val zoom: Float = 1f,
    /** Horizontal focus point, -1 (left) .. 1 (right). */
    val offsetX: Float = 0f,
    /** Vertical focus point, -1 (top) .. 1 (bottom). */
    val offsetY: Float = 0f,
)

@Serializable
data class HiveConfig(
    val id: Int,
    val slot: Int,
    val tapAction: LauncherAction = LauncherAction.None,
    val longPressAction: LauncherAction = LauncherAction.None,
    /** ARGB colour of the hexagon fill. Alpha is controlled separately by [opacity]. */
    val color: Long = 0xFF2B2F45,
    /** Opacity of the fill (colour and image), 0..1. Icons stay opaque for legibility. */
    val opacity: Float = 0.85f,
    val visual: HiveVisual = HiveVisual.ICON,
    val image: ImageConfig? = null,
    /** Icon size relative to the hexagon, 0.3..0.9. */
    val iconScale: Float = 0.56f,
    /** Hexagon size relative to its slot, 0.6..1.0. */
    val sizeScale: Float = 1f,
    /** Outline width in dp, 0 disables it. */
    val outlineWidth: Float = 0f,
    val outlineColor: Long = 0xFFFFFFFF,
    /** Optional small caption; off by default because hives are recognised visually. */
    val showLabel: Boolean = false,
)

@Serializable
enum class LayoutPreset { HONEYCOMB, TOWER, RING }

@Serializable
data class LayoutConfig(
    val preset: LayoutPreset = LayoutPreset.HONEYCOMB,
    /** Gap between hexagons relative to the hexagon radius, 0..0.3. */
    val gap: Float = 0.07f,
    /** Size of the centre hive relative to the regular hives, 1..1.4. */
    val centerScale: Float = 1.2f,
    /** Corner rounding relative to the radius, 0..0.35. */
    val cornerRadius: Float = 0.14f,
    /** User scale applied after fitting to the screen, 0.6..1. */
    val scale: Float = 1f,
    /** Vertical placement of the composition in the free space, -1 (top) .. 1 (bottom). */
    val verticalBias: Float = 0f,
    /** Maximum hexagon radius in dp, prevents giant hexagons on tablets. */
    val maxRadiusDp: Float = 72f,
)

@Serializable
enum class ComboInput {
    /** Separate taps within the time window. */
    TAPS,

    /** Sliding the finger across hives without lifting it. Single taps never wait. */
    SWIPE,
    BOTH,
}

@Serializable
data class ComboConfig(
    val id: String,
    /** Hive ids in the order they were defined. May contain repeats (e.g. a double tap). */
    val hives: List<Int>,
    /** When true the order of [hives] matters; otherwise any order matches. */
    val ordered: Boolean = false,
    val action: LauncherAction,
    val enabled: Boolean = true,
    val name: String = "",
)

@Serializable
data class ComboSettings(
    /** Maximum interval between taps of a combination, in ms. */
    val windowMs: Int = 300,
    val input: ComboInput = ComboInput.BOTH,
    val hapticFeedback: Boolean = true,
    /** Highlight hives already tapped while a combination is pending. */
    val showPending: Boolean = true,
) {
    companion object {
        const val MIN_WINDOW_MS = 100
        const val MAX_WINDOW_MS = 1000
        const val FAST_WINDOW_MS = 180
        const val BALANCED_WINDOW_MS = 300
        const val COMFORT_WINDOW_MS = 480
    }
}

@Serializable
enum class SearchPosition { BOTTOM, TOP }

@Serializable
data class SearchSettings(
    val position: SearchPosition = SearchPosition.BOTTOM,
    val autoKeyboard: Boolean = true,
    /** The IME action key opens the first result. */
    val enterOpensFirst: Boolean = true,
    /** Open the app as soon as the query leaves exactly one result. */
    val autoLaunchSingle: Boolean = false,
    val showCombos: Boolean = true,
    val matchPackageName: Boolean = false,
    val rankByUsage: Boolean = true,
    val swipeUpOpensSearch: Boolean = true,
    val maxResults: Int = 12,
)

@Serializable
enum class Corner { TOP_START, TOP_END, BOTTOM_START, BOTTOM_END }

@Serializable
enum class MusicTapAction { EXPAND, PLAY_PAUSE }

@Serializable
data class MusicSettings(
    val enabled: Boolean = true,
    val corner: Corner = Corner.TOP_END,
    val tapAction: MusicTapAction = MusicTapAction.EXPAND,
    val color: Long = 0xFF1E1B2E,
    val opacity: Float = 0.9f,
    /** App opened from the widget when nothing is playing. */
    val preferredPlayerKey: String? = null,
)

@Serializable
enum class LaunchAnimation { SYSTEM, REVEAL_FROM_HIVE, NONE }

@Serializable
data class AnimationSettings(
    val enabled: Boolean = true,
    /** Multiplier for UI animation durations, 0.5..1.5. */
    val speed: Float = 1f,
    val launchAnimation: LaunchAnimation = LaunchAnimation.REVEAL_FROM_HIVE,
)

@Serializable
data class InteractionSettings(
    /**
     * Fire on finger-down instead of finger-up. Saves the tap duration, but disables long-press
     * actions on hives.
     */
    val triggerOnPress: Boolean = false,
    /** Long press on empty space enters edit mode. */
    val longPressEdits: Boolean = true,
    /** Blocks entering edit mode from the home screen (only from settings). */
    val lockLayout: Boolean = false,
    val haptics: Boolean = true,
)

@Serializable
enum class BackgroundMode { WALLPAPER, COLOR, IMAGE }

@Serializable
data class BackgroundConfig(
    val mode: BackgroundMode = BackgroundMode.WALLPAPER,
    val color: Long = 0xFF0E0F17,
    val image: ImageConfig? = null,
    /** Darkening over the background, 0..0.8, helps hive legibility. */
    val dim: Float = 0.15f,
)
