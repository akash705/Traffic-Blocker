package com.vedtechnologies.trafficblocker.ui.dnslog

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vedtechnologies.trafficblocker.data.AppDatabase
import com.vedtechnologies.trafficblocker.data.DnsQueryLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LogFilter { ALL, BLOCKED, ALLOWED }

class DnsLogViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getInstance(application).dnsQueryLogDao()

    val filter = MutableStateFlow(LogFilter.ALL)

    @OptIn(ExperimentalCoroutinesApi::class)
    val logs: StateFlow<List<DnsQueryLog>> = filter.flatMapLatest { f ->
        when (f) {
            LogFilter.ALL -> dao.getRecent(500)
            LogFilter.BLOCKED -> dao.getBlockedOnly(500)
            LogFilter.ALLOWED -> dao.getAllowedOnly(500)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val blockedCount: StateFlow<Int> = dao.getBlockedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun setFilter(f: LogFilter) {
        filter.value = f
    }

    fun clearLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.clearAll()
        }
    }
}
