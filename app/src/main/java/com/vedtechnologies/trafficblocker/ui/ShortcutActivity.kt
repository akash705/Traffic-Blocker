package com.vedtechnologies.trafficblocker.ui

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.util.Log
import com.vedtechnologies.trafficblocker.data.PrefsManager
import com.vedtechnologies.trafficblocker.service.BlockerService

class ShortcutActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val action = intent.getStringExtra(EXTRA_ACTION)
        Log.d(TAG, "ShortcutActivity triggered with action: $action")

        when (action) {
            ACTION_VALUE_START -> handleStart()
            ACTION_VALUE_STOP -> handleStop()
            else -> Log.w(TAG, "Unknown action: $action")
        }

        finish()
    }

    private fun handleStart() {
        val prefs = PrefsManager(this)
        if (prefs.targetPackages.isEmpty()) {
            Log.w(TAG, "No target packages configured — opening main app")
            startActivity(Intent(this, MainActivity::class.java))
            return
        }

        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            Log.w(TAG, "VPN permission not granted — opening main app")
            startActivity(Intent(this, MainActivity::class.java))
            return
        }

        val serviceIntent = Intent(this, BlockerService::class.java).apply {
            this.action = BlockerService.ACTION_START
        }
        startForegroundService(serviceIntent)
        Log.d(TAG, "BlockerService started via shortcut")
    }

    private fun handleStop() {
        val serviceIntent = Intent(this, BlockerService::class.java).apply {
            this.action = BlockerService.ACTION_STOP
        }
        startForegroundService(serviceIntent)
        Log.d(TAG, "BlockerService stopped via shortcut")
    }

    companion object {
        private const val TAG = "ShortcutActivity"
        const val EXTRA_ACTION = "action"
        const val ACTION_VALUE_START = "start"
        const val ACTION_VALUE_STOP = "stop"
    }
}
