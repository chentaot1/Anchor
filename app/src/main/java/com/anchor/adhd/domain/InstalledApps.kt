package com.anchor.adhd.domain

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

data class InstalledApp(val label: String, val packageName: String)

object InstalledApps {
    private val blockedPackagePrefixes = listOf(
        "com.android.",
        "com.google.android.packageinstaller",
        "com.google.android.permissioncontroller",
        "com.samsung.android.app.telephonyui",
        "com.samsung.android.incallui",
        "com.samsung.android.dialer",
        "com.sec.android.app.launcher",
        "com.sec.android.emergencylauncher"
    )

    fun loadLaunchable(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val flags = PackageManager.MATCH_DEFAULT_ONLY
        return pm.queryIntentActivities(intent, flags)
            .mapNotNull { resolve ->
                val pkg = resolve.activityInfo.packageName
                if (!isUserBlockable(pm, pkg, context.packageName)) return@mapNotNull null
                val label = resolve.loadLabel(pm).toString()
                InstalledApp(label = label, packageName = pkg)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    private fun isUserBlockable(pm: PackageManager, packageName: String, selfPackage: String): Boolean {
        if (packageName == selfPackage) return false
        if (blockedPackagePrefixes.any { packageName.startsWith(it) }) return false
        val info = runCatching { pm.getApplicationInfo(packageName, 0) }.getOrNull() ?: return false
        val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val isUpdatedSystem = (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        if (isSystem && !isUpdatedSystem) return false
        return true
    }
}
