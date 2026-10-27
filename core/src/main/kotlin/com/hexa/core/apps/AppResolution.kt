package com.hexa.core.apps

import com.hexa.core.model.AppKey
import com.hexa.core.model.LauncherAction

/** Read-only view of the installed, launchable activities. */
interface AppCatalog {
    fun contains(key: String): Boolean

    /** Some launchable activity of [packageName] for the given user, if any. */
    fun firstKeyForPackage(packageName: String, userSerial: Long): String?

    fun label(key: String): String?
}

enum class TargetStatus {
    /** Nothing assigned. */
    EMPTY,

    /** Assigned and launchable. */
    READY,

    /** Assigned app was uninstalled/disabled: the hive keeps its look and offers re-assignment. */
    MISSING,
}

object AppResolution {

    /**
     * Resolves a stored key against what is installed now. If the exact activity disappeared but the
     * package still has a launcher activity (the app renamed it in an update), that one is used.
     */
    fun resolve(key: String, catalog: AppCatalog): String? {
        if (catalog.contains(key)) return key
        return catalog.firstKeyForPackage(AppKey.packageOf(key), AppKey.userSerialOf(key))
    }

    fun status(action: LauncherAction, catalog: AppCatalog): TargetStatus = when (action) {
        LauncherAction.None -> TargetStatus.EMPTY
        is LauncherAction.OpenApp -> if (resolve(action.appKey, catalog) != null) TargetStatus.READY else TargetStatus.MISSING
        is LauncherAction.AppShortcut ->
            if (catalog.firstKeyForPackage(action.packageName, action.userSerial) != null) TargetStatus.READY else TargetStatus.MISSING
        else -> TargetStatus.READY
    }

    /** Key of the app whose icon represents [action], if it is app-backed. */
    fun iconKey(action: LauncherAction, catalog: AppCatalog): String? = when (action) {
        is LauncherAction.OpenApp -> resolve(action.appKey, catalog)
        is LauncherAction.AppShortcut -> catalog.firstKeyForPackage(action.packageName, action.userSerial)
        else -> null
    }
}

/** Simple in-memory catalog; the app module builds one from LauncherApps on every change. */
class MapAppCatalog(private val labels: Map<String, String>) : AppCatalog {
    private val byPackage: Map<Pair<String, Long>, String> = labels.keys
        .sortedBy { it }
        .groupBy { AppKey.packageOf(it) to AppKey.userSerialOf(it) }
        .mapValues { it.value.first() }

    override fun contains(key: String) = key in labels
    override fun firstKeyForPackage(packageName: String, userSerial: Long) = byPackage[packageName to userSerial]
    override fun label(key: String) = labels[key]
}
