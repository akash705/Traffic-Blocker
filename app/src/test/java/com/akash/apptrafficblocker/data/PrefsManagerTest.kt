package com.akash.apptrafficblocker.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PrefsManagerTest {

    private lateinit var prefs: PrefsManager

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        prefs = PrefsManager(context)
        context.getSharedPreferences("app_traffic_blocker_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `targetPackages defaults to empty set`() {
        assertTrue(prefs.targetPackages.isEmpty())
    }

    @Test
    fun `addTargetApp adds package and name`() {
        prefs.addTargetApp("com.instagram.android", "Instagram")

        assertTrue(prefs.isAppSelected("com.instagram.android"))
        assertEquals("Instagram", prefs.targetAppNames["com.instagram.android"])
    }

    @Test
    fun `addTargetApp supports multiple apps`() {
        prefs.addTargetApp("com.instagram.android", "Instagram")
        prefs.addTargetApp("com.facebook.katana", "Facebook")
        prefs.addTargetApp("com.twitter.android", "Twitter")

        assertEquals(3, prefs.targetPackages.size)
        assertTrue(prefs.isAppSelected("com.instagram.android"))
        assertTrue(prefs.isAppSelected("com.facebook.katana"))
        assertTrue(prefs.isAppSelected("com.twitter.android"))
    }

    @Test
    fun `removeTargetApp removes package and name`() {
        prefs.addTargetApp("com.instagram.android", "Instagram")
        prefs.addTargetApp("com.facebook.katana", "Facebook")

        prefs.removeTargetApp("com.instagram.android")

        assertFalse(prefs.isAppSelected("com.instagram.android"))
        assertTrue(prefs.isAppSelected("com.facebook.katana"))
        assertEquals(1, prefs.targetPackages.size)
        assertFalse(prefs.targetAppNames.containsKey("com.instagram.android"))
    }

    @Test
    fun `isAppSelected returns false for unselected app`() {
        assertFalse(prefs.isAppSelected("com.nonexistent.app"))
    }

    @Test
    fun `targetAppNames round-trips correctly`() {
        prefs.addTargetApp("com.app1", "App One")
        prefs.addTargetApp("com.app2", "App Two")

        val names = prefs.targetAppNames
        assertEquals("App One", names["com.app1"])
        assertEquals("App Two", names["com.app2"])
    }

    @Test
    fun `serviceEnabled defaults to false`() {
        assertFalse(prefs.serviceEnabled)
    }

    @Test
    fun `serviceEnabled round-trips correctly`() {
        prefs.serviceEnabled = true
        assertTrue(prefs.serviceEnabled)

        prefs.serviceEnabled = false
        assertFalse(prefs.serviceEnabled)
    }

    @Test
    fun `autoStartOnBoot defaults to false`() {
        assertFalse(prefs.autoStartOnBoot)
    }

    @Test
    fun `autoStartOnBoot round-trips correctly`() {
        prefs.autoStartOnBoot = true
        assertTrue(prefs.autoStartOnBoot)
    }

    @Test
    fun `pollIntervalMs defaults to 1000`() {
        assertEquals(PrefsManager.DEFAULT_POLL_INTERVAL, prefs.pollIntervalMs)
    }

    @Test
    fun `pollIntervalMs round-trips correctly`() {
        prefs.pollIntervalMs = 500L
        assertEquals(500L, prefs.pollIntervalMs)

        prefs.pollIntervalMs = 2000L
        assertEquals(2000L, prefs.pollIntervalMs)
    }

    @Test
    fun `showBlockLog defaults to false`() {
        assertFalse(prefs.showBlockLog)
    }

    @Test
    fun `multiple settings persist independently`() {
        prefs.addTargetApp("com.example.app", "Example")
        prefs.serviceEnabled = true
        prefs.autoStartOnBoot = true
        prefs.pollIntervalMs = 500L

        assertTrue(prefs.isAppSelected("com.example.app"))
        assertTrue(prefs.serviceEnabled)
        assertTrue(prefs.autoStartOnBoot)
        assertEquals(500L, prefs.pollIntervalMs)
    }
}
