package com.hexa.launcher

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.util.Log
import com.hexa.core.metrics.Metric
import com.hexa.core.metrics.PerfStats
import com.hexa.core.model.LauncherConfig
import com.hexa.core.persist.AtomicTextFile
import com.hexa.core.persist.ConfigCodec
import com.hexa.core.persist.PersistedState
import com.hexa.core.search.SearchItem
import com.hexa.core.search.UsageStats
import com.hexa.launcher.apps.AppRepository
import com.hexa.launcher.apps.IconCache
import com.hexa.launcher.apps.ShortcutRepository
import com.hexa.launcher.images.ImageStore
import com.hexa.launcher.media.MusicController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

/** Keys of launcher functions that show up in search next to the apps. */
object InternalKeys {
    const val SETTINGS = "hexa:settings"
    const val EDIT = "hexa:edit"
    const val MUSIC = "hexa:music"
}

/**
 * Process-wide singletons. A launcher lives as long as the device is on, so everything here is
 * created once and shared by the home screen and the settings screen.
 */
class LauncherGraph(private val app: Application) {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val io = Dispatchers.IO.limitedParallelism(4)

    val perf = PerfStats()

    val config = PersistedState(
        file = AtomicTextFile(File(app.filesDir, "hexa_config.json")),
        default = LauncherConfig(),
        encode = ConfigCodec::encode,
        decode = ConfigCodec::decode,
        scope = scope,
        io = io,
        debounceMs = 400,
    )

    val usage = PersistedState(
        file = AtomicTextFile(File(app.filesDir, "hexa_usage.json")),
        default = UsageStats(),
        encode = { ConfigCodec.json.encodeToString(UsageStats.serializer(), it) },
        decode = { ConfigCodec.json.decodeFromString(UsageStats.serializer(), it) },
        scope = scope,
        io = io,
        debounceMs = 3_000,
    )

    val apps = AppRepository(
        context = app,
        scope = scope,
        io = io,
        perf = perf,
        extraItems = listOf(
            SearchItem(InternalKeys.SETTINGS, "Definições do Hexa", keywords = listOf("definicoes", "settings", "launcher", "configurar")),
            SearchItem(InternalKeys.EDIT, "Editar colmeias", keywords = listOf("editar", "modo de edicao", "personalizar")),
            SearchItem(InternalKeys.MUSIC, "Widget de música", keywords = listOf("musica", "music", "player")),
        ),
    )
    val icons = IconCache(app, apps, io)
    val shortcuts = ShortcutRepository(app, io)
    val images = ImageStore(app, io)
    val music = MusicController(app, scope, io)

    /** False until the configuration file has been read (first frame waits for it, ~ms). */
    val configLoaded get() = config.loaded

    fun start() {
        scope.launch {
            val t0 = SystemClock.elapsedRealtimeNanos()
            val existed = config.load()
            perf.recordNanos(Metric.CONFIG_LOAD, SystemClock.elapsedRealtimeNanos() - t0)
            usage.load()
            if (!existed) {
                // First run: wait for the app list and pre-fill hives with the usual apps.
                val snapshot = apps.snapshot.first { it.loaded }
                config.set(FirstRun.fill(app, config.value, snapshot))
                config.flush()
            } else {
                images.deleteUnreferenced(referencedImages(config.value))
            }
        }
        apps.start()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = apps.reloadAll()
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_LOCALE_CHANGED)
            addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_ADDED)
            addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED)
        }
        // System broadcasts only: they still reach a non-exported receiver.
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            app.registerReceiver(receiver, filter)
        }
    }

    fun flush() {
        scope.launch {
            config.flush()
            usage.flush()
        }
    }

    fun recordLaunch(key: String) {
        usage.update { it.record(key, System.currentTimeMillis()) }
    }

    fun logPerf(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d("HexaPerf", "$tag: $message")
    }

    companion object {
        fun referencedImages(config: LauncherConfig): Set<String> =
            (config.hives.mapNotNull { it.image?.path } + listOfNotNull(config.background.image?.path)).toSet()
    }
}
