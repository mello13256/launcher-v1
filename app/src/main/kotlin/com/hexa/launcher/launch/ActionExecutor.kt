package com.hexa.launcher.launch

import android.app.Activity
import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import com.hexa.core.apps.AppResolution
import com.hexa.core.metrics.Metric
import com.hexa.core.metrics.PerfStats
import com.hexa.core.model.LaunchAnimation
import com.hexa.launcher.apps.AppRepository
import com.hexa.launcher.apps.ShortcutRepository

sealed interface LaunchOutcome {
    /** The request was handed to the system. [dispatchMs] = request → startActivity returned. */
    data class Started(val dispatchMs: Double) : LaunchOutcome

    /** The app is not installed (uninstalled, disabled, work profile off). */
    data object Missing : LaunchOutcome

    data class Failed(val message: String) : LaunchOutcome
}

/**
 * Turns app/shortcut/URI actions into the corresponding system calls. Everything here runs on the
 * main thread synchronously, directly from the touch handler, so no frame or dispatcher hop is
 * added between recognising a tap and asking Android to open the app.
 */
class ActionExecutor(
    private val activity: Activity,
    private val apps: AppRepository,
    private val shortcuts: ShortcutRepository,
    private val perf: PerfStats,
) {
    private val launcherApps = activity.getSystemService(LauncherApps::class.java)!!

    /** Animation preference, updated from the configuration. */
    var animation: LaunchAnimation = LaunchAnimation.REVEAL_FROM_HIVE

    fun openApp(appKey: String, bounds: Rect?, view: View?): LaunchOutcome {
        val start = SystemClock.elapsedRealtimeNanos()
        val key = AppResolution.resolve(appKey, apps.snapshot.value.catalog) ?: return LaunchOutcome.Missing
        val app = apps.snapshot.value.byKey[key] ?: return LaunchOutcome.Missing
        return try {
            launcherApps.startMainActivity(app.component, app.user, bounds, options(bounds, view))
            started(start)
        } catch (e: ActivityNotFoundException) {
            LaunchOutcome.Missing
        } catch (e: SecurityException) {
            LaunchOutcome.Failed("Sem permissão para abrir a aplicação")
        } catch (e: IllegalStateException) {
            // Work profile paused / user locked.
            LaunchOutcome.Failed("Perfil indisponível")
        }
    }

    fun openShortcut(packageName: String, shortcutId: String, userSerial: Long, bounds: Rect?, view: View?): LaunchOutcome {
        val start = SystemClock.elapsedRealtimeNanos()
        val user = shortcuts.userFor(userSerial) ?: return LaunchOutcome.Missing
        return try {
            launcherApps.startShortcut(packageName, shortcutId, bounds, options(bounds, view), user)
            started(start)
        } catch (e: ActivityNotFoundException) {
            LaunchOutcome.Missing
        } catch (e: IllegalStateException) {
            LaunchOutcome.Failed("Define o Hexa como launcher predefinido para usar atalhos")
        } catch (e: SecurityException) {
            LaunchOutcome.Failed("Atalho indisponível")
        }
    }

    fun openUri(uri: String, bounds: Rect?, view: View?): LaunchOutcome {
        val start = SystemClock.elapsedRealtimeNanos()
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.sourceBounds = bounds
            activity.startActivity(intent, options(bounds, view))
            started(start)
        } catch (e: ActivityNotFoundException) {
            LaunchOutcome.Failed("Nenhuma aplicação abre este endereço")
        } catch (e: Exception) {
            LaunchOutcome.Failed("Endereço inválido")
        }
    }

    fun openAppDetails(appKey: String) {
        val app = apps.snapshot.value.byKey[appKey] ?: return
        try {
            launcherApps.startAppDetailsActivity(app.component, app.user, null, null)
        } catch (e: Exception) {
            // Nothing sensible to do; the user can open Settings manually.
        }
    }

    fun startIntent(intent: Intent): Boolean = try {
        activity.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }

    private fun started(startNanos: Long): LaunchOutcome.Started {
        val ms = (SystemClock.elapsedRealtimeNanos() - startNanos) / 1_000_000.0
        perf.record(Metric.LAUNCH_DISPATCH, ms)
        return LaunchOutcome.Started(ms)
    }

    private fun options(bounds: Rect?, view: View?): Bundle? = when (animation) {
        LaunchAnimation.SYSTEM -> null
        LaunchAnimation.NONE -> ActivityOptions.makeCustomAnimation(activity, 0, 0).toBundle()
        LaunchAnimation.REVEAL_FROM_HIVE ->
            if (bounds != null && view != null) {
                // Bounds are in window coordinates; the reveal origin is relative to the view.
                val loc = IntArray(2)
                view.getLocationInWindow(loc)
                ActivityOptions.makeClipRevealAnimation(view, bounds.left - loc[0], bounds.top - loc[1], bounds.width(), bounds.height()).toBundle()
            } else {
                null
            }
    }
}
