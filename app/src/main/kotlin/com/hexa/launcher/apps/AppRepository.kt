package com.hexa.launcher.apps

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.UserHandle
import android.os.UserManager
import com.hexa.core.apps.AppCatalog
import com.hexa.core.apps.MapAppCatalog
import com.hexa.core.metrics.Metric
import com.hexa.core.metrics.PerfStats
import com.hexa.core.model.AppKey
import com.hexa.core.search.SearchIndex
import com.hexa.core.search.SearchItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** One launchable activity. */
data class AppInfo(
    val key: String,
    val label: String,
    val packageName: String,
    val component: ComponentName,
    val user: UserHandle,
    val userSerial: Long,
    val isWorkProfile: Boolean,
)

/** Immutable view of the installed apps, rebuilt off the main thread when packages change. */
class AppsSnapshot(
    val apps: List<AppInfo>,
    val byKey: Map<String, AppInfo>,
    val catalog: AppCatalog,
    val index: SearchIndex,
    val loaded: Boolean,
) {
    fun label(key: String): String? = byKey[key]?.label

    companion object {
        val EMPTY = AppsSnapshot(emptyList(), emptyMap(), MapAppCatalog(emptyMap()), SearchIndex(emptyList()), loaded = false)
    }
}

/**
 * Keeps an in-memory list of launchable activities for every user profile using [LauncherApps],
 * the API meant for launchers (it also covers the work profile and respects package visibility via
 * the `<queries>` MAIN/LAUNCHER entry in the manifest).
 *
 * The system is queried once at start, then only the package reported by a [LauncherApps.Callback]
 * is re-queried. The search index is rebuilt from memory on each change; searching never touches
 * the PackageManager.
 */
class AppRepository(
    context: Context,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val perf: PerfStats,
    private val extraItems: List<SearchItem> = emptyList(),
) {
    private val appContext = context.applicationContext
    private val launcherApps = appContext.getSystemService(LauncherApps::class.java)!!
    private val userManager = appContext.getSystemService(UserManager::class.java)!!

    private val _snapshot = MutableStateFlow(AppsSnapshot.EMPTY)
    val snapshot: StateFlow<AppsSnapshot> = _snapshot.asStateFlow()

    /** Activity infos by key, used for icons and launching. */
    private val infos = ConcurrentHashMap<String, LauncherActivityInfo>()
    private val mutex = Mutex()
    private var rebuildJob: Job? = null

    /** Listeners told which packages changed so they can drop cached icons. */
    private val packageListeners = mutableListOf<(String) -> Unit>()

    fun addPackageChangeListener(listener: (String) -> Unit) {
        packageListeners += listener
    }

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = refresh(packageName, user)
        override fun onPackageAdded(packageName: String, user: UserHandle) = refresh(packageName, user)
        override fun onPackageChanged(packageName: String, user: UserHandle) = refresh(packageName, user)
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            packageNames.forEach { refresh(it, user) }

        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            packageNames.forEach { refresh(it, user) }

        override fun onPackagesSuspended(packageNames: Array<out String>, user: UserHandle) =
            packageNames.forEach { refresh(it, user) }

        override fun onPackagesUnsuspended(packageNames: Array<out String>, user: UserHandle) =
            packageNames.forEach { refresh(it, user) }
    }

    fun start() {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        reloadAll()
    }

    /** Full reload (first start, locale change, work profile toggled). */
    fun reloadAll() {
        scope.launch {
            val start = SystemClock.elapsedRealtimeNanos()
            mutex.withLock {
                withContext(io) {
                    infos.clear()
                    for (user in userManager.userProfiles) {
                        val serial = userManager.getSerialNumberForUser(user)
                        val list = try {
                            launcherApps.getActivityList(null, user)
                        } catch (e: SecurityException) {
                            emptyList()
                        }
                        for (info in list) infos[keyOf(info, serial)] = info
                    }
                }
                publish()
            }
            perf.recordNanos(Metric.APP_INDEX_LOAD, SystemClock.elapsedRealtimeNanos() - start)
        }
    }

    private fun refresh(packageName: String, user: UserHandle) {
        packageListeners.forEach { it(packageName) }
        scope.launch {
            mutex.withLock {
                withContext(io) {
                    val serial = userManager.getSerialNumberForUser(user)
                    val keySerial = if (isMainUser(user)) 0L else serial
                    infos.keys.removeAll { AppKey.packageOf(it) == packageName && AppKey.userSerialOf(it) == keySerial }
                    val list = try {
                        if (launcherApps.isPackageEnabled(packageName, user)) launcherApps.getActivityList(packageName, user) else emptyList()
                    } catch (e: Exception) {
                        emptyList()
                    }
                    for (info in list) infos[keyOf(info, serial)] = info
                }
            }
            schedulePublish()
        }
    }

    /** Several callbacks often arrive together (app update); rebuild once. */
    private fun schedulePublish() {
        rebuildJob?.cancel()
        rebuildJob = scope.launch {
            delay(150)
            mutex.withLock { publish() }
        }
    }

    private suspend fun publish() {
        val next = withContext(io) {
            val myUser = android.os.Process.myUserHandle()
            val apps = infos.map { (key, info) ->
                AppInfo(
                    key = key,
                    label = info.label?.toString()?.trim().orEmpty().ifEmpty { info.componentName.packageName },
                    packageName = info.componentName.packageName,
                    component = info.componentName,
                    user = info.user,
                    userSerial = AppKey.userSerialOf(key),
                    isWorkProfile = info.user != myUser,
                )
            }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            val byKey = apps.associateBy { it.key }
            val items = apps.map { app ->
                SearchItem(app.key, if (app.isWorkProfile) "${app.label} (trabalho)" else app.label, app.packageName)
            } + extraItems
            AppsSnapshot(apps, byKey, MapAppCatalog(apps.associate { it.key to it.label }), SearchIndex(items), loaded = true)
        }
        _snapshot.value = next
    }

    fun activityInfo(key: String): LauncherActivityInfo? = infos[key]

    private fun keyOf(info: LauncherActivityInfo, serial: Long) =
        AppKey.of(info.componentName.packageName, info.componentName.className, if (isMainUser(info.user)) 0 else serial)

    private fun isMainUser(user: UserHandle) = user == android.os.Process.myUserHandle()
}
