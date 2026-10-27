package com.hexa.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import com.hexa.core.layout.LayoutPresets
import com.hexa.core.model.CENTER_HIVE_ID
import com.hexa.core.model.LauncherAction
import com.hexa.core.model.LauncherConfig
import com.hexa.launcher.apps.AppsSnapshot

/**
 * Pre-fills the hives on first launch so the launcher is usable immediately: the default handlers
 * of common intents first (dialer, messages, camera, browser...), then other apps alphabetically.
 */
object FirstRun {

    private fun intents(): List<Intent> = listOf(
        Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), // centre hive
        Intent(Intent.ACTION_DIAL),
        Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
        Intent(Intent.ACTION_VIEW, Uri.parse("https://example.org")),
        Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY),
        Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC),
        Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")),
        Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL),
        Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR),
        Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CONTACTS),
        Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING),
        Intent(Settings.ACTION_SETTINGS),
        Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR),
        Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_FILES),
    )

    fun fill(context: Context, config: LauncherConfig, apps: AppsSnapshot): LauncherConfig {
        if (apps.apps.isEmpty()) return config
        val pm = context.packageManager
        val chosen = LinkedHashSet<String>()
        for (intent in intents()) {
            val pkg = try {
                pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            } catch (e: Exception) {
                null
            } ?: continue
            if (pkg == "android" || pkg == context.packageName) continue // the "choose app" resolver
            apps.apps.firstOrNull { it.packageName == pkg && !it.isWorkProfile }?.let { chosen += it.key }
        }
        for (app in apps.apps) {
            if (chosen.size >= 16) break
            if (app.packageName != context.packageName && !app.isWorkProfile) chosen += app.key
        }
        val keys = chosen.toList()
        // Hive order: centre first, then the inner ring (fill order) so the most useful apps sit
        // closest to the centre.
        val order = listOf(CENTER_HIVE_ID) + (0 until LayoutPresets.HONEYCOMB.fillOrder.size)
        var result = config
        order.forEachIndexed { i, hiveId ->
            val key = keys.getOrNull(i) ?: return@forEachIndexed
            result = result.withHive(hiveId) { it.copy(tapAction = LauncherAction.OpenApp(key, apps.label(key).orEmpty())) }
        }
        return result
    }
}
