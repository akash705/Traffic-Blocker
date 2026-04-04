package com.akash.apptrafficblocker.ui.home

import android.app.Application
import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.AndroidViewModel
import com.akash.apptrafficblocker.data.PrefsManager

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    val prefs = PrefsManager(application)

    val appBlockingModes = mutableStateMapOf<String, String>().also {
        it.putAll(prefs.appBlockingModes)
    }

    fun getAppMode(packageName: String): String {
        return appBlockingModes[packageName] ?: PrefsManager.MODE_BLOCK_ALL
    }

    fun toggleAppMode(packageName: String) {
        val current = getAppMode(packageName)
        val newMode = if (current == PrefsManager.MODE_BLOCK_ALL) {
            PrefsManager.MODE_BLOCK_DOMAINS
        } else {
            PrefsManager.MODE_BLOCK_ALL
        }
        prefs.setAppBlockingMode(packageName, newMode)
        appBlockingModes[packageName] = newMode
    }

    fun hasUsageStatsPermission(): Boolean {
        val context = getApplication<Application>()
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
