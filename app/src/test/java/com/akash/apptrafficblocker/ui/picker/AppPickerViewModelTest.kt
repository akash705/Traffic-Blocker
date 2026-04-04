package com.akash.apptrafficblocker.ui.picker

import android.app.Application
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
class AppPickerViewModelTest {

    private lateinit var viewModel: AppPickerViewModel

    @Before
    fun setup() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        // Clear prefs
        app.getSharedPreferences("app_traffic_blocker_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
        viewModel = AppPickerViewModel(app)
    }

    @Test
    fun `initial search query is empty`() {
        assertEquals("", viewModel.searchQuery.value)
    }

    @Test
    fun `initial loading state is true`() {
        assertTrue(viewModel.isLoading.value)
    }

    @Test
    fun `initial selected packages is empty`() {
        assertTrue(viewModel.selectedPackages.value.isEmpty())
    }

    @Test
    fun `updateSearch changes search query`() {
        viewModel.updateSearch("instagram")
        assertEquals("instagram", viewModel.searchQuery.value)
    }

    @Test
    fun `updateSearch with empty string resets filter`() {
        viewModel.updateSearch("test")
        viewModel.updateSearch("")
        assertEquals("", viewModel.searchQuery.value)
    }

    @Test
    fun `toggleApp selects an app`() {
        val app = AppInfo("com.instagram.android", "Instagram")
        viewModel.toggleApp(app)

        assertTrue(viewModel.isSelected("com.instagram.android"))
        assertEquals(1, viewModel.selectedPackages.value.size)
    }

    @Test
    fun `toggleApp deselects a selected app`() {
        val app = AppInfo("com.instagram.android", "Instagram")
        viewModel.toggleApp(app) // select
        viewModel.toggleApp(app) // deselect

        assertFalse(viewModel.isSelected("com.instagram.android"))
        assertTrue(viewModel.selectedPackages.value.isEmpty())
    }

    @Test
    fun `toggleApp supports multiple selections`() {
        viewModel.toggleApp(AppInfo("com.instagram.android", "Instagram"))
        viewModel.toggleApp(AppInfo("com.facebook.katana", "Facebook"))
        viewModel.toggleApp(AppInfo("com.twitter.android", "Twitter"))

        assertEquals(3, viewModel.selectedPackages.value.size)
        assertTrue(viewModel.isSelected("com.instagram.android"))
        assertTrue(viewModel.isSelected("com.facebook.katana"))
        assertTrue(viewModel.isSelected("com.twitter.android"))
    }

    @Test
    fun `toggleApp persists to prefs`() {
        viewModel.toggleApp(AppInfo("com.instagram.android", "Instagram"))

        assertTrue(viewModel.prefs.isAppSelected("com.instagram.android"))
        assertEquals("Instagram", viewModel.prefs.targetAppNames["com.instagram.android"])
    }
}
