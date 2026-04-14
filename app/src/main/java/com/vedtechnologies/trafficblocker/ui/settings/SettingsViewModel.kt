package com.vedtechnologies.trafficblocker.ui.settings

import android.app.AppOpsManager
import android.app.Application
import android.content.Context
import android.net.VpnService
import android.os.PowerManager
import android.os.Process
import androidx.lifecycle.AndroidViewModel
import com.vedtechnologies.trafficblocker.data.PrefsManager

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    val prefs = PrefsManager(application)

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

    fun hasVpnPermission(): Boolean {
        val context = getApplication<Application>()
        return VpnService.prepare(context) == null
    }

    fun isBatteryOptimizationExempt(): Boolean {
        val context = getApplication<Application>()
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }
}
