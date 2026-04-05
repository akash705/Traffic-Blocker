package com.akash.apptrafficblocker.service

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Binder
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.akash.apptrafficblocker.BlockerApp
import com.akash.apptrafficblocker.R
import com.akash.apptrafficblocker.data.AppDatabase
import com.akash.apptrafficblocker.data.BlockEvent
import com.akash.apptrafficblocker.data.BlocklistRepository
import com.akash.apptrafficblocker.data.PrefsManager
import com.akash.apptrafficblocker.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BlockerService : VpnService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var dnsProxy: DnsPacketProxy? = null
    private var watchdog: AppWatchdog? = null
    private var vpnInterface: ParcelFileDescriptor? = null
    private lateinit var prefs: PrefsManager
    private lateinit var db: AppDatabase
    private lateinit var blocklistRepo: BlocklistRepository

    private val _state = MutableStateFlow(BlockerState())
    val state: StateFlow<BlockerState> = _state.asStateFlow()

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        val service: BlockerService get() = this@BlockerService
    }

    override fun onBind(intent: Intent?): IBinder {
        if (intent?.action == SERVICE_INTERFACE) {
            return super.onBind(intent)!!
        }
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        prefs = PrefsManager(this)
        db = AppDatabase.getInstance(this)
        blocklistRepo = BlocklistRepository(this, db.blocklistDao())
        Log.d(TAG, "BlockerService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val packages = prefs.targetPackages
                if (packages.isEmpty()) {
                    Log.e(TAG, "No target packages configured")
                    stopSelf()
                    return START_NOT_STICKY
                }
                startBlocking(packages)
            }
            ACTION_STOP -> {
                stopBlocking()
                stopSelf()
            }
            ACTION_PAUSE -> {
                pauseBlocking()
            }
            ACTION_RESUME -> {
                val packages = _state.value.targetPackages
                if (packages.isNotEmpty()) {
                    resumeBlocking(packages)
                }
            }
            ACTION_RELOAD_BLOCKLIST -> {
                reloadBlocklist()
            }
            else -> {
                val packages = prefs.targetPackages
                if (packages.isNotEmpty() && prefs.serviceEnabled) {
                    startBlocking(packages)
                } else {
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }
        return START_STICKY
    }

    private fun startBlocking(targetPackages: Set<String>) {
        Log.d(TAG, "Starting blocking for ${targetPackages.size} apps: $targetPackages")
        startForeground(NOTIF_ID, buildNotification(isBlocking = false))

        serviceScope.launch(Dispatchers.IO) {
            val domains = blocklistRepo.loadBlockedDomains()
            val appModes = prefs.appBlockingModes
            val bgBlockApps = prefs.backgroundBlockingApps

            serviceScope.launch(Dispatchers.Main) {
                openTunnel(targetPackages, domains, appModes, bgBlockApps)
                startWatchdog(targetPackages)
                prefs.serviceEnabled = true
                _state.value = BlockerState(
                    isRunning = true,
                    isBlocking = false,
                    isPaused = false,
                    targetPackages = targetPackages,
                    blockedDomainCount = domains.size,
                    appBlockingModes = appModes
                )
            }
        }
    }

    private fun startWatchdog(targetPackages: Set<String>) {
        val pollInterval = prefs.pollIntervalMs
        watchdog = AppWatchdog(
            context = this,
            targetPackages = targetPackages,
            pollIntervalMs = pollInterval,
            onForegroundChanged = { foregroundPkg ->
                // Update the DNS proxy with current foreground app so it can
                // block background data for apps that have it enabled
                dnsProxy?.foregroundPackage = foregroundPkg
            }
        ) { isTargetInForeground ->
            _state.value = _state.value.copy(isBlocking = isTargetInForeground)
            if (isTargetInForeground) {
                _state.value = _state.value.copy(lastBlockedAt = System.currentTimeMillis())
                serviceScope.launch(Dispatchers.IO) {
                    for (pkg in targetPackages) {
                        db.blockEventDao().insert(
                            BlockEvent(packageName = pkg, eventType = "BLOCKED")
                        )
                    }
                }
            }
            updateNotification()
        }
        watchdog?.start()
    }

    private fun stopBlocking() {
        Log.d(TAG, "Stopping blocking")
        watchdog?.stop()
        watchdog = null
        closeTunnel()
        prefs.serviceEnabled = false
        _state.value = BlockerState()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun pauseBlocking() {
        Log.d(TAG, "Pausing blocking")
        watchdog?.stop()
        closeTunnel()
        _state.value = _state.value.copy(isPaused = true, isBlocking = false)
        updateNotification()
    }

    private fun resumeBlocking(targetPackages: Set<String>) {
        Log.d(TAG, "Resuming blocking for ${targetPackages.size} apps")

        serviceScope.launch(Dispatchers.IO) {
            val domains = blocklistRepo.loadBlockedDomains()
            val appModes = prefs.appBlockingModes
            val bgBlockApps = prefs.backgroundBlockingApps

            serviceScope.launch(Dispatchers.Main) {
                openTunnel(targetPackages, domains, appModes, bgBlockApps)
                startWatchdog(targetPackages)
                _state.value = _state.value.copy(
                    isPaused = false,
                    blockedDomainCount = domains.size,
                    appBlockingModes = appModes
                )
                updateNotification()
            }
        }
    }

    private fun reloadBlocklist() {
        Log.d(TAG, "Reloading blocklist")
        val targetPackages = _state.value.targetPackages
        if (targetPackages.isEmpty()) return

        serviceScope.launch(Dispatchers.IO) {
            val domains = blocklistRepo.loadBlockedDomains()
            val appModes = prefs.appBlockingModes
            val bgBlockApps = prefs.backgroundBlockingApps

            serviceScope.launch(Dispatchers.Main) {
                closeTunnel()
                openTunnel(targetPackages, domains, appModes, bgBlockApps)
                _state.value = _state.value.copy(
                    blockedDomainCount = domains.size,
                    appBlockingModes = appModes
                )
                updateNotification()
            }
        }
    }

    private fun openTunnel(
        targetPackages: Set<String>,
        blockedDomains: Set<String>,
        appModes: Map<String, String>,
        bgBlockApps: Set<String> = emptySet()
    ) {
        if (vpnInterface != null) return

        try {
            val builder = Builder()
                .addAddress("10.0.0.2", 32)
                .setSession("TrafficBlocker")
                .setBlocking(true)
                .addDnsServer(UPSTREAM_DNS)
                .addRoute(UPSTREAM_DNS, 32)
                .addDnsServer(UPSTREAM_DNS_SECONDARY)
                .addRoute(UPSTREAM_DNS_SECONDARY, 32)

            // Route known DoH/DoT provider IPs through VPN so their HTTPS/TLS
            // connections are dropped, forcing browsers to fall back to standard DNS.
            for (ip in DOH_PROVIDER_IPS) {
                builder.addRoute(ip, 32)
            }

            for (pkg in targetPackages) {
                try {
                    builder.addAllowedApplication(pkg)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to add allowed application: $pkg", e)
                }
            }

            vpnInterface = builder.establish()

            if (vpnInterface == null) {
                Log.e(TAG, "VPN establish() returned null — another VPN may be active")
                return
            }

            dnsProxy = DnsPacketProxy(
                vpnFd = vpnInterface!!,
                blockedDomains = blockedDomains,
                vpnService = this,
                appBlockingModes = appModes,
                context = this,
                backgroundBlockingApps = bgBlockApps,
                onDnsQuery = { domain, pkg, blocked, queryType ->
                    serviceScope.launch(Dispatchers.IO) {
                        try {
                            db.dnsQueryLogDao().insert(
                                com.akash.apptrafficblocker.data.DnsQueryLog(
                                    domain = domain,
                                    packageName = pkg,
                                    blocked = blocked,
                                    queryType = queryType
                                )
                            )
                            // Auto-prune logs older than 24 hours
                            val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000
                            db.dnsQueryLogDao().deleteOlderThan(cutoff)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to log DNS query", e)
                        }
                    }
                }
            )
            dnsProxy?.start()

            val blockAllCount = appModes.count { it.value == PrefsManager.MODE_BLOCK_ALL }
            val blockDomainsCount = targetPackages.size - blockAllCount
            Log.d(TAG, "DNS proxy opened — $blockAllCount apps block-all, $blockDomainsCount apps block-domains, ${blockedDomains.size} domains in blocklist")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open tunnel", e)
        }
    }

    private fun closeTunnel() {
        if (vpnInterface == null) return

        dnsProxy?.stop()
        dnsProxy = null

        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface", e)
        }
        vpnInterface = null

        Log.d(TAG, "Tunnel closed")
    }

    private fun buildNotification(
        isBlocking: Boolean = _state.value.isBlocking
    ): android.app.Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, BlockerService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeIntent = PendingIntent.getService(
            this, 2,
            Intent(this, BlockerService::class.java).apply {
                action = if (_state.value.isPaused) ACTION_RESUME else ACTION_PAUSE
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        val appNames = prefs.targetAppNames
        val appListText = _state.value.targetPackages
            .mapNotNull { appNames[it] ?: it }
            .joinToString(", ")

        val statusText = when {
            _state.value.isPaused -> "Paused"
            isBlocking -> "Active: $appListText"
            else -> "Watching: $appListText"
        }

        return NotificationCompat.Builder(this, BlockerApp.CHANNEL_ID)
            .setContentTitle("Traffic Blocker")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_block)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(
                R.drawable.ic_block,
                if (_state.value.isPaused) "Resume" else "Pause",
                pauseResumeIntent
            )
            .addAction(R.drawable.ic_block, "Stop", stopIntent)
            .build()
    }

    private fun updateNotification() {
        try {
            val manager = getSystemService(android.app.NotificationManager::class.java)
            manager.notify(NOTIF_ID, buildNotification())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update notification", e)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // App swiped from recents — restart the service if it was enabled
        if (prefs.serviceEnabled && prefs.targetPackages.isNotEmpty()) {
            Log.d(TAG, "Task removed — scheduling service restart")
            val restartIntent = Intent(this, BlockerService::class.java).apply {
                action = ACTION_START
            }
            startForegroundService(restartIntent)
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "BlockerService destroyed")
        watchdog?.stop()
        closeTunnel()

        // If service was still enabled, schedule restart via alarm
        if (prefs.serviceEnabled && prefs.targetPackages.isNotEmpty()) {
            Log.d(TAG, "Service destroyed while enabled — scheduling restart")
            val restartIntent = Intent(this, BlockerService::class.java).apply {
                action = ACTION_START
            }
            val pendingIntent = PendingIntent.getForegroundService(
                this, RESTART_REQUEST_CODE, restartIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
            )
            val alarmManager = getSystemService(android.app.AlarmManager::class.java)
            alarmManager.setExactAndAllowWhileIdle(
                android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                android.os.SystemClock.elapsedRealtime() + 1000,
                pendingIntent
            )
        }

        super.onDestroy()
    }

    companion object {
        private const val TAG = "BlockerService"
        const val ACTION_START = "com.akash.apptrafficblocker.ACTION_START"
        const val ACTION_STOP = "com.akash.apptrafficblocker.ACTION_STOP"
        const val ACTION_PAUSE = "com.akash.apptrafficblocker.ACTION_PAUSE"
        const val ACTION_RESUME = "com.akash.apptrafficblocker.ACTION_RESUME"
        const val ACTION_RELOAD_BLOCKLIST = "com.akash.apptrafficblocker.ACTION_RELOAD_BLOCKLIST"
        private const val NOTIF_ID = 1001
        private const val SERVICE_INTERFACE = "android.net.VpnService"
        private const val UPSTREAM_DNS = "8.8.8.8"
        private const val UPSTREAM_DNS_SECONDARY = "8.8.4.4"
        private const val RESTART_REQUEST_CODE = 9999

        // IPs of major DoH/DoT providers — route these through the VPN so
        // encrypted DNS connections are dropped, forcing standard DNS fallback.
        // (8.8.8.8 and 8.8.4.4 are already routed as our upstream DNS.)
        private val DOH_PROVIDER_IPS = listOf(
            "1.1.1.1",         // Cloudflare
            "1.0.0.1",         // Cloudflare secondary
            "9.9.9.9",         // Quad9
            "149.112.112.112", // Quad9 secondary
            "208.67.222.222",  // OpenDNS
            "208.67.220.220",  // OpenDNS secondary
            "94.140.14.14",    // AdGuard
            "94.140.15.15",    // AdGuard secondary
            "185.228.168.168", // CleanBrowsing
            "185.228.169.168", // CleanBrowsing secondary
        )
    }
}

data class BlockerState(
    val isRunning: Boolean = false,
    val isBlocking: Boolean = false,
    val isPaused: Boolean = false,
    val targetPackages: Set<String> = emptySet(),
    val lastBlockedAt: Long? = null,
    val blockedDomainCount: Int = 0,
    val appBlockingModes: Map<String, String> = emptyMap()
)
