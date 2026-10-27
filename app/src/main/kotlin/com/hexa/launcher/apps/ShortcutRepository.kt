package com.hexa.launcher.apps

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

data class AppShortcutInfo(
    val packageName: String,
    val id: String,
    val label: String,
    val userSerial: Long,
)

/**
 * App shortcuts ("Nova mensagem", "Navegar para casa"...) published by apps. Only the default home
 * app may query them ([LauncherApps.hasShortcutHostPermission]).
 */
class ShortcutRepository(context: Context, private val io: CoroutineDispatcher) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)!!
    private val userManager = context.getSystemService(UserManager::class.java)!!

    val hasPermission: Boolean
        get() = try {
            launcherApps.hasShortcutHostPermission()
        } catch (e: Exception) {
            false
        }

    suspend fun shortcutsFor(packageName: String, userSerial: Long): List<AppShortcutInfo> = withContext(io) {
        if (!hasPermission) return@withContext emptyList()
        val user = userFor(userSerial) ?: return@withContext emptyList()
        val query = LauncherApps.ShortcutQuery()
            .setPackage(packageName)
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
            )
        val list: List<ShortcutInfo> = try {
            launcherApps.getShortcuts(query, user) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        list.filter { it.isEnabled }
            .sortedBy { it.rank }
            .map { AppShortcutInfo(it.`package`, it.id, (it.shortLabel ?: it.longLabel ?: it.id).toString(), userSerial) }
    }

    fun userFor(serial: Long): UserHandle? =
        if (serial == 0L) Process.myUserHandle() else userManager.getUserForSerialNumber(serial)
}
