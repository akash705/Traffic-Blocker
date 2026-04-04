package com.akash.apptrafficblocker.ui.blocklist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.akash.apptrafficblocker.data.AppDatabase
import com.akash.apptrafficblocker.data.BlocklistRepository
import com.akash.apptrafficblocker.data.BlocklistSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BlocklistViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val repo = BlocklistRepository(application, db.blocklistDao())

    val blocklists: StateFlow<List<BlocklistSource>> = repo.allSources
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun addBlocklist(url: String, name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null
            try {
                repo.addBlocklist(url, name.ifBlank { extractNameFromUrl(url) })
            } catch (e: Exception) {
                _error.value = "Failed to add blocklist: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun removeBlocklist(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.removeBlocklist(id)
        }
    }

    fun toggleBlocklist(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.toggleBlocklist(id)
        }
    }

    fun refreshAll() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null
            try {
                repo.refreshAll()
            } catch (e: Exception) {
                _error.value = "Refresh failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    private fun extractNameFromUrl(url: String): String {
        return try {
            val path = url.substringAfterLast("/").substringBefore("?")
            if (path.isNotBlank()) path else url.substringAfter("://").take(30)
        } catch (_: Exception) {
            "Blocklist"
        }
    }
}
