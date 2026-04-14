package com.vedtechnologies.trafficblocker.ui.home

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {

    private lateinit var viewModel: HomeViewModel

    @Before
    fun setup() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        viewModel = HomeViewModel(app)
    }

    @Test
    fun `prefs is initialized`() {
        assertNotNull(viewModel.prefs)
    }

    @Test
    fun `hasUsageStatsPermission returns false by default in test`() {
        // In Robolectric, usage stats permission is not granted
        assertFalse(viewModel.hasUsageStatsPermission())
    }
}
