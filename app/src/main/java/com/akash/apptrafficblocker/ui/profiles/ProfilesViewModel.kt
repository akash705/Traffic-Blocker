package com.akash.apptrafficblocker.ui.profiles

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.akash.apptrafficblocker.data.AppDatabase
import com.akash.apptrafficblocker.data.PrefsManager
import com.akash.apptrafficblocker.data.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfilesViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getInstance(application).profileDao()
    val prefs = PrefsManager(application)

    val profiles: StateFlow<List<Profile>> = dao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Save current blocking config as a new profile */
    fun saveCurrentAsProfile(name: String) {
        val packages = prefs.targetPackages
        val appNames = prefs.targetAppNames
        val modes = prefs.appBlockingModes

        val profile = Profile(
            name = name,
            packages = packages.joinToString(","),
            appNames = appNames.map { "${it.key}|${it.value}" }.joinToString(","),
            blockingModes = modes.map { "${it.key}|${it.value}" }.joinToString(",")
        )

        viewModelScope.launch(Dispatchers.IO) {
            dao.insert(profile)
        }
    }

    /** Load a profile's config into active prefs */
    fun activateProfile(profile: Profile) {
        val packages = profile.packages.split(",").filter { it.isNotBlank() }.toSet()
        val appNames = profile.appNames.split(",").filter { it.contains("|") }.associate {
            val parts = it.split("|", limit = 2)
            parts[0] to parts[1]
        }
        val modes = profile.blockingModes.split(",").filter { it.contains("|") }.associate {
            val parts = it.split("|", limit = 2)
            parts[0] to parts[1]
        }

        prefs.targetPackages = packages
        prefs.targetAppNames = appNames
        prefs.appBlockingModes = modes
    }

    fun deleteProfile(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteById(id)
        }
    }
}
