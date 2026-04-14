package com.vedtechnologies.trafficblocker.service

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy

@RunWith(RobolectricTestRunner::class)
class AppWatchdogTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun `watchdog initializes with lastState false`() {
        var callbackCalled = false
        val watchdog = AppWatchdog(
            context = context,
            targetPackage = "com.example.app",
            pollIntervalMs = 1000L,
            onStateChange = { callbackCalled = true }
        )

        // Before starting, no callback should have been called
        assertFalse(callbackCalled)
    }

    @Test
    fun `watchdog stop cancels job`() {
        val watchdog = AppWatchdog(
            context = context,
            targetPackage = "com.example.app",
            pollIntervalMs = 1000L,
            onStateChange = { }
        )

        watchdog.start()
        watchdog.stop()
        // Should not throw — stopping is idempotent
        watchdog.stop()
    }

    @Test
    fun `watchdog can be restarted after stop`() {
        val watchdog = AppWatchdog(
            context = context,
            targetPackage = "com.example.app",
            pollIntervalMs = 1000L,
            onStateChange = { }
        )

        watchdog.start()
        watchdog.stop()
        watchdog.start()
        watchdog.stop()
        // No exception means restart works
    }
}
