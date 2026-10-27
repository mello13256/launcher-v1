package com.hexa.launcher

import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The launcher must be selectable as the device's home app through the normal system chooser. */
@RunWith(AndroidJUnit4::class)
class HomeActivityManifestTest {

    @Test
    fun mainActivityHandlesHomeIntent() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(context.packageName)
        val matches = context.packageManager.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
        assertEquals(listOf(MainActivity::class.java.name), matches.map { it.activityInfo.name })
        val info = matches.single().activityInfo
        assertEquals(android.content.pm.ActivityInfo.LAUNCH_SINGLE_TASK, info.launchMode)
        assertTrue(info.exported)
    }

    @Test
    fun noBroadPackageVisibilityPermissionRequested() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val pkg = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val requested = pkg.requestedPermissions.orEmpty().toList()
        assertTrue("QUERY_ALL_PACKAGES must not be requested", "android.permission.QUERY_ALL_PACKAGES" !in requested)
    }
}
