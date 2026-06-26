package com.privatecaller.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.privatecaller.AppContainer
import com.privatecaller.data.db.BlockedNumber
import com.privatecaller.data.db.ContactSimPref
import com.privatecaller.data.db.MonitoredApp
import com.privatecaller.data.db.UnblockWindow
import com.privatecaller.data.prefs.AppSettings
import com.privatecaller.domain.CallLogEntry
import com.privatecaller.domain.ContactItem
import com.privatecaller.domain.NumberMatch
import com.privatecaller.domain.SimSlot
import com.privatecaller.domain.SmartUnblockManager
import com.privatecaller.data.prefs.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Builds the app's ViewModels from the shared [AppContainer]. */
class ViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(RecentsViewModel::class.java) ->
            RecentsViewModel(container) as T
        modelClass.isAssignableFrom(DialerViewModel::class.java) ->
            DialerViewModel(container) as T
        modelClass.isAssignableFrom(SmartUnblockViewModel::class.java) ->
            SmartUnblockViewModel(container) as T
        modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
            SettingsViewModel(container) as T
        else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}

class RecentsViewModel(container: AppContainer) : ViewModel() {
    private val callLogRepo = container.callLogRepository
    private val contactsRepo = container.contactsRepository
    private val blockList = container.blockList

    val callLog: StateFlow<List<CallLogEntry>> =
        callLogRepo.observe()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val blockedNumbers: StateFlow<List<BlockedNumber>> =
        blockList.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _contacts = MutableStateFlow<List<ContactItem>>(emptyList())
    val contacts: StateFlow<List<ContactItem>> = _contacts.asStateFlow()

    init { refreshContacts() }

    fun refreshContacts() = viewModelScope.launch {
        _contacts.value = contactsRepo.loadAll()
    }

    fun isBlocked(number: String?, blocked: List<BlockedNumber>): Boolean {
        if (number.isNullOrBlank()) return false
        return blocked.any { NumberMatch.sameNumber(it.normalized, number) }
    }

    fun block(number: String, label: String?) =
        viewModelScope.launch { blockList.block(number, label) }

    fun unblock(number: String) = viewModelScope.launch { blockList.unblock(number) }
}

class DialerViewModel(container: AppContainer) : ViewModel() {
    private val contactsRepo = container.contactsRepository

    private val _contacts = MutableStateFlow<List<ContactItem>>(emptyList())
    val contacts: StateFlow<List<ContactItem>> = _contacts.asStateFlow()

    init { refreshContacts() }

    fun refreshContacts() = viewModelScope.launch {
        _contacts.value = contactsRepo.loadAll()
    }
}

class SmartUnblockViewModel(container: AppContainer) : ViewModel() {
    private val dao = container.monitoredAppDao
    private val manager: SmartUnblockManager = container.smartUnblock
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

class SettingsViewModel(container: AppContainer) : ViewModel() {
    private val store = container.settingsStore
    private val simManager = container.simManager
    private val blockList = container.blockList
    private val contactSimPref = container.contactSimPref

    val settings: StateFlow<AppSettings> =
        store.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val blockedNumbers: StateFlow<List<BlockedNumber>> =
        blockList.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val simPrefs: StateFlow<List<ContactSimPref>> =
        contactSimPref.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _sims = MutableStateFlow<List<SimSlot>>(emptyList())
    val sims: StateFlow<List<SimSlot>> = _sims.asStateFlow()

    init { refreshSims() }

    fun refreshSims() {
        _sims.value = simManager.availableSims()
    }

    // Note: the launcher icon is NOT swapped here — doing so mid-session can
    // close the app. It's reconciled when the app goes to the background.
    fun setScreening(value: Boolean) = viewModelScope.launch { store.setScreeningEnabled(value) }
    fun setSmartUnblock(value: Boolean) = viewModelScope.launch { store.setSmartUnblockEnabled(value) }
    fun setBlockMessage(value: String) = viewModelScope.launch { store.setBlockMessage(value) }
    fun setLogBlocked(value: Boolean) = viewModelScope.launch { store.setLogBlockedCalls(value) }

    /** Toggle screening for a single SIM slot (resolving the "all" default first). */
    fun setSimScreening(slotIndex: Int, screen: Boolean) = viewModelScope.launch {
        val allSlots = _sims.value.map { it.slotIndex }.toSet()
        val base = store.current().screeningSimSlots ?: allSlots
        val updated = if (screen) base + slotIndex else base - slotIndex
        // Storing null keeps the clean "all SIMs" default when everything is on.
        store.setScreeningSimSlots(if (updated == allSlots) null else updated)
    }

    fun blockNumber(number: String) = viewModelScope.launch { blockList.block(number) }

    fun unblock(normalized: String) = viewModelScope.launch { blockList.unblockNormalized(normalized) }

    /** Human label for a SIM slot, e.g. "SIM 1 · Airtel". */
    fun simLabel(slot: Int?): String =
        slot?.let { s -> _sims.value.firstOrNull { it.slotIndex == s }?.label ?: "SIM ${s + 1}" }
            ?: "Ask every time"

    fun clearSimPref(number: String) = viewModelScope.launch { contactSimPref.clear(number) }
}
