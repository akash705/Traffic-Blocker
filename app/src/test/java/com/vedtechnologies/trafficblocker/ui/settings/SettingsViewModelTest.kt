package com.vedtechnologies.trafficblocker.ui.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {

    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setup() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        viewModel = SettingsViewModel(app)
    }

    @Test
    fun `prefs is initialized`() {
        assertNotNull(viewModel.prefs)
    }

    @Test
    fun `hasUsageStatsPermission returns false in test environment`() {
        assertFalse(viewModel.hasUsageStatsPermission())
    }

    @Test
    fun `isBatteryOptimizationExempt returns false in test environment`() {
        assertFalse(viewModel.isBatteryOptimizationExempt())
    }

    @Test
    fun `prefs auto-start toggle works`() {
        viewModel.prefs.autoStartOnBoot = true
        assertNotNull(viewModel.prefs.autoStartOnBoot)

        viewModel.prefs.autoStartOnBoot = false
        assertFalse(viewModel.prefs.autoStartOnBoot)
    }

    @Test
    fun `prefs poll interval persists`() {
        viewModel.prefs.pollIntervalMs = 500L
        assert(viewModel.prefs.pollIntervalMs == 500L)

        viewModel.prefs.pollIntervalMs = 2000L
        assert(viewModel.prefs.pollIntervalMs == 2000L)
    }
}
