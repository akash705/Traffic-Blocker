package com.akash.apptrafficblocker.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.akash.apptrafficblocker.data.PrefsManager
import com.akash.apptrafficblocker.service.BlockerService
import com.akash.apptrafficblocker.ui.navigation.AppNavGraph
import com.akash.apptrafficblocker.ui.theme.AppTrafficBlockerTheme

class MainActivity : ComponentActivity() {

    private var blockerService: BlockerService? by mutableStateOf(null)
    private var serviceBound = false
    private lateinit var prefs: PrefsManager

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d(TAG, "Notification permission granted: $granted")
        // Proceed to VPN permission regardless — service can run without notifications
        // but the notification won't show
        requestVpnPermission()
    }

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Log.d(TAG, "VPN permission granted")
            startBlockerService()
        } else {
            Log.w(TAG, "VPN permission denied")
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? BlockerService.LocalBinder
            blockerService = binder?.service
            serviceBound = true
            Log.d(TAG, "Service connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            blockerService = null
            serviceBound = false
            Log.d(TAG, "Service disconnected")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prefs = PrefsManager(this)

        requestNotificationPermissionIfNeeded()

        setContent {
            AppTrafficBlockerTheme {
                val navController = rememberNavController()
                AppNavGraph(
                    navController = navController,
                    blockerService = blockerService,
                    onRequestVpnPermission = { ensureNotificationPermissionThenStart() }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Bind to service if it's running
        if (prefs.serviceEnabled) {
            bindBlockerService()
        }
    }

    override fun onStop() {
        super.onStop()
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            true // Pre-Android 13, no runtime permission needed
        }
    }

    private fun ensureNotificationPermissionThenStart() {
        if (!hasNotificationPermission()) {
            // Will call requestVpnPermission() after permission result
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            requestVpnPermission()
        }
    }

    private fun requestVpnPermission() {
        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            vpnPermissionLauncher.launch(vpnIntent)
        } else {
            // Already have VPN permission
            startBlockerService()
        }
    }

    private fun startBlockerService() {
        if (prefs.targetPackages.isEmpty()) return
        val intent = Intent(this, BlockerService::class.java).apply {
            action = BlockerService.ACTION_START
        }
        startForegroundService(intent)
        bindBlockerService()
    }

    private fun bindBlockerService() {
        val intent = Intent(this, BlockerService::class.java)
        bindService(intent, serviceConnection, 0)
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
