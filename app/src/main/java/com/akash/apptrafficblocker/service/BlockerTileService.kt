package com.akash.apptrafficblocker.service

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.akash.apptrafficblocker.data.PrefsManager

/**
 * Quick Settings tile for toggling the blocker on/off from the notification shade.
 */
class BlockerTileService : TileService() {

    private lateinit var prefs: PrefsManager

    override fun onCreate() {
        super.onCreate()
        prefs = PrefsManager(this)
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        if (prefs.serviceEnabled) {
            // Stop the service
            val intent = Intent(this, BlockerService::class.java).apply {
                action = BlockerService.ACTION_STOP
            }
            startService(intent)
            prefs.serviceEnabled = false
            updateTileState()
        } else {
            // Start the service if we have targets configured
            val targets = prefs.targetPackages
            if (targets.isEmpty()) {
                Log.w(TAG, "No target packages configured — cannot start from tile")
                return
            }

            val vpnIntent = android.net.VpnService.prepare(this)
            if (vpnIntent != null) {
                // VPN permission not granted — need to open app
                Log.w(TAG, "VPN permission not granted — launching app")
                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivityAndCollapse(launchIntent)
                }
                return
            }

            val intent = Intent(this, BlockerService::class.java).apply {
                action = BlockerService.ACTION_START
            }
            startForegroundService(intent)
            updateTileState()
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val isActive = prefs.serviceEnabled

        tile.state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = if (isActive) "Active" else "Off"
        tile.updateTile()
    }

    companion object {
        private const val TAG = "BlockerTileService"
    }
}
