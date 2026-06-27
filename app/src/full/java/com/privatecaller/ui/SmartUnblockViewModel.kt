package com.privatecaller.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.privatecaller.AppContainer
import com.privatecaller.data.db.MonitoredApp
import com.privatecaller.data.db.UnblockWindow
import com.privatecaller.domain.SmartUnblockManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Drives the SmartUnblock screen ("full" edition only). */
class SmartUnblockViewModel(container: AppContainer) : ViewModel() {
    private val dao = container.monitoredAppDao
    private val manager = SmartUnblockManager(container.monitoredAppDao, container.unblockWindowDao)
    private val now = System::currentTimeMillis

    val monitoredApps: StateFlow<List<MonitoredApp>> =
        dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeWindows: StateFlow<List<UnblockWindow>> =
        container.unblockWindowDao.observeActive(now())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setEnabled(app: MonitoredApp, enabled: Boolean) =
        viewModelScope.launch { dao.upsert(app.copy(enabled = enabled)) }

    fun setDuration(app: MonitoredApp, minutes: Int) =
        viewModelScope.launch { dao.upsert(app.copy(unblockMinutes = minutes)) }

    fun addApp(packageName: String, label: String, minutes: Int) =
        viewModelScope.launch {
            dao.upsert(MonitoredApp(packageName, label, enabled = true, unblockMinutes = minutes))
        }

    fun removeApp(app: MonitoredApp) = viewModelScope.launch { dao.delete(app.packageName) }

    fun openManualWindow(minutes: Int) = viewModelScope.launch { manager.openManualWindow(minutes) }

    fun cancelAll() = viewModelScope.launch { manager.cancelAll() }
}
