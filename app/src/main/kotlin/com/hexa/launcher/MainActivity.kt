package com.hexa.launcher

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hexa.launcher.launch.ActionExecutor
import com.hexa.launcher.ui.home.Haptic
import com.hexa.launcher.ui.home.HomeScreen
import com.hexa.launcher.ui.home.HomeViewModel
import com.hexa.launcher.ui.home.LaunchHost
import com.hexa.launcher.ui.settings.SettingsActivity
import com.hexa.launcher.ui.theme.AnimPrefs
import com.hexa.launcher.ui.theme.HexaTheme

/**
 * The HOME activity. singleTask + stateNotNeeded + no recents entry, like any launcher. Pressing
 * Home while it is in front arrives as onNewIntent and resets overlays.
 */
class MainActivity : ComponentActivity(), LaunchHost {

    private val vm: HomeViewModel by viewModels()

    override lateinit var executor: ActionExecutor

    override val rootView: View get() = window.decorView

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        executor = ActionExecutor(this, graph.apps, graph.shortcuts, graph.perf)
        vm.attach(this)
        setContent {
            val config by vm.config.collectAsStateWithLifecycle()
            HexaTheme(AnimPrefs.from(config.animation)) { HomeScreen(vm) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) vm.onHomeIntent()
    }

    override fun onStart() {
        super.onStart()
        graph.music.start()
    }

    override fun onStop() {
        super.onStop()
        graph.music.stop()
        vm.onStop()
    }

    override fun onDestroy() {
        vm.detach(this)
        super.onDestroy()
    }

    override fun haptic(kind: Haptic) {
        if (!vm.config.value.interaction.haptics) return
        val constant = when (kind) {
            Haptic.TAP -> HapticFeedbackConstants.VIRTUAL_KEY
            Haptic.LONG_PRESS -> HapticFeedbackConstants.LONG_PRESS
            Haptic.CONFIRM -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
            Haptic.REJECT -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
        }
        window.decorView.performHapticFeedback(constant)
    }

    override fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    override fun startIntent(intent: Intent): Boolean = executor.startIntent(intent)
}
