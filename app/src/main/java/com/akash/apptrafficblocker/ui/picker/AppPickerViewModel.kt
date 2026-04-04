package com.akash.apptrafficblocker.ui.picker

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.akash.apptrafficblocker.data.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AppInfo(
    val packageName: String,
    val appName: String
)

class AppPickerViewModel(application: Application) : AndroidViewModel(application) {

    val prefs = PrefsManager(application)

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedPackages = MutableStateFlow<Set<String>>(emptySet())
    val selectedPackages: StateFlow<Set<String>> = _selectedPackages.asStateFlow()

    private var allApps: List<AppInfo> = emptyList()

    init {
        _selectedPackages.value = prefs.targetPackages
        loadApps()
    }

    private fun loadApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = getApplication<Application>().packageManager
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

            allApps = installedApps
                .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
                .map { appInfo ->
                    AppInfo(
                        packageName = appInfo.packageName,
                        appName = pm.getApplicationLabel(appInfo).toString()
                    )
                }
                .sortedBy { it.appName.lowercase() }

            _apps.value = allApps
            _isLoading.value = false
        }
    }

    fun updateSearch(query: String) {
        _searchQuery.value = query
        _apps.value = if (query.isBlank()) {
            allApps
        } else {
            allApps.filter {
                it.appName.contains(query, ignoreCase = true) ||
                        it.packageName.contains(query, ignoreCase = true)
            }
        }
    }

    fun toggleApp(app: AppInfo) {
        if (prefs.isAppSelected(app.packageName)) {
            prefs.removeTargetApp(app.packageName)
        } else {
            prefs.addTargetApp(app.packageName, app.appName)
        }
        _selectedPackages.value = prefs.targetPackages
    }

    fun selectAll() {
        for (app in allApps) {
            if (!prefs.isAppSelected(app.packageName)) {
                prefs.addTargetApp(app.packageName, app.appName)
            }
        }
        _selectedPackages.value = prefs.targetPackages
    }

    fun unselectAll() {
        for (app in allApps) {
            if (prefs.isAppSelected(app.packageName)) {
                prefs.removeTargetApp(app.packageName)
            }
        }
        _selectedPackages.value = prefs.targetPackages
    }

    fun isSelected(packageName: String): Boolean {
        return packageName in _selectedPackages.value
    }
}
