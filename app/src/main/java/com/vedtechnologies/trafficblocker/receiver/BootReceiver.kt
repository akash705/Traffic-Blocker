package com.vedtechnologies.trafficblocker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.vedtechnologies.trafficblocker.data.PrefsManager
import com.vedtechnologies.trafficblocker.service.BlockerService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = PrefsManager(context)
        if (!prefs.autoStartOnBoot) {
            Log.d(TAG, "Auto-start disabled, skipping")
            return
        }

        val packages = prefs.targetPackages
        if (packages.isEmpty()) {
            Log.d(TAG, "No target packages configured, skipping")
            return
        }

        Log.d(TAG, "Boot completed — starting blocker for ${packages.size} apps")
        val serviceIntent = Intent(context, BlockerService::class.java).apply {
            action = BlockerService.ACTION_START
        }
        context.startForegroundService(serviceIntent)
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
