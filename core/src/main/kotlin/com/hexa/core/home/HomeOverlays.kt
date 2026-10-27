package com.hexa.core.home

/**
 * Which transient layer is shown above the hives. Only one is open at a time, so opening search
 * collapses the music widget and vice-versa; Back closes the top layer; Home closes everything.
 */
sealed interface Overlay {
    data object None : Overlay
    data object Search : Overlay
    data object MusicExpanded : Overlay

    /** Edit mode, optionally with the editor of one hive open. */
    data class Edit(val selectedHive: Int? = null) : Overlay
}

class HomeOverlays(initial: Overlay = Overlay.None) {
    var current: Overlay = initial
        private set

    val isMusicExpanded: Boolean get() = current == Overlay.MusicExpanded
    val isSearchOpen: Boolean get() = current == Overlay.Search
    val isEditing: Boolean get() = current is Overlay.Edit

    fun openSearch() {
        if (current !is Overlay.Edit) current = Overlay.Search
    }

    fun toggleMusic() {
        current = when (current) {
            Overlay.MusicExpanded -> Overlay.None
            is Overlay.Edit -> current
            else -> Overlay.MusicExpanded
        }
    }

    fun expandMusic() {
        if (current !is Overlay.Edit) current = Overlay.MusicExpanded
    }

    fun collapseMusic() {
        if (current == Overlay.MusicExpanded) current = Overlay.None
    }

    /** Returns false when edit mode is locked. */
    fun enterEdit(locked: Boolean): Boolean {
        if (locked) return false
        current = Overlay.Edit()
        return true
    }

    fun selectHive(id: Int?) {
        if (current is Overlay.Edit) current = Overlay.Edit(id)
    }

    /** Back: close the innermost layer. Returns true if something was closed. */
    fun back(): Boolean {
        current = when (val c = current) {
            Overlay.None -> return false
            is Overlay.Edit -> if (c.selectedHive != null) Overlay.Edit(null) else Overlay.None
            else -> Overlay.None
        }
        return true
    }

    /** Home button pressed while the launcher is visible: return to the plain composition. */
    fun home() {
        current = Overlay.None
    }

    fun close() {
        current = Overlay.None
    }
}
