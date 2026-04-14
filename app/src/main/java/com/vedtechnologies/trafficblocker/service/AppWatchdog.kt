package com.vedtechnologies.trafficblocker.service

import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Polls UsageStatsManager to detect when any target app enters/leaves
 * the foreground. Emits a callback to drive tunnel open/close.
 */
class AppWatchdog(
    private val context: Context,
    private val targetPackages: Set<String>,
    private val pollIntervalMs: Long = 1000L,
    private val onForegroundChanged: ((foregroundPackage: String?) -> Unit)? = null,
    private val onStateChange: (isTargetInForeground: Boolean) -> Unit
) {
    private var job: Job? = null
    private var lastState = false
    private var lastForeground: String? = null

    fun start() {
        job = CoroutineScope(Dispatchers.Default).launch {
            Log.d(TAG, "AppWatchdog started — monitoring ${targetPackages.size} apps every ${pollIntervalMs}ms")
            while (isActive) {
                val foreground = getForegroundPackage()

                // Always update foreground package for background blocking
                if (foreground != lastForeground) {
                    lastForeground = foreground
                    onForegroundChanged?.invoke(foreground)
                }

                val isTarget = foreground in targetPackages
                if (isTarget != lastState) {
                    Log.d(TAG, "Foreground change: $foreground (isTarget=$isTarget)")
                    lastState = isTarget
                    onStateChange(isTarget)
                }
                delay(pollIntervalMs)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        lastState = false
        Log.d(TAG, "AppWatchdog stopped")
    }

    private fun getForegroundPackage(): String? {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE)
                    as UsageStatsManager
            val now = System.currentTimeMillis()
            val stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - 2000,
                now
            )
            stats?.maxByOrNull { it.lastTimeUsed }?.packageName
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query usage stats", e)
            null
        }
    }

    companion object {
        private const val TAG = "AppWatchdog"
    }
}
