package com.privatecaller

import android.content.Context
import com.privatecaller.data.db.MonitoredApp
import com.privatecaller.data.db.PrivateCallerDatabase
import com.privatecaller.data.prefs.SettingsStore
import com.privatecaller.domain.BlockListManager
import com.privatecaller.domain.CallLogRepository
import com.privatecaller.domain.CallScreener
import com.privatecaller.domain.ContactLookup
import com.privatecaller.domain.ContactSimPrefManager
import com.privatecaller.domain.ContactsRepository
import com.privatecaller.domain.LauncherIconController
import com.privatecaller.domain.SimManager
import com.privatecaller.domain.SmartUnblockManager
import com.privatecaller.domain.UpdateManager
import kotlinx.coroutines.flow.first

/** Manual dependency-injection container, created once in [PrivateCallerApp]. */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val db = PrivateCallerDatabase.get(appContext)

    val settingsStore = SettingsStore(appContext)
    val contactLookup = ContactLookup(appContext)
    val contactsRepository = ContactsRepository(appContext)
    val callLogRepository = CallLogRepository(appContext)
    val simManager = SimManager(appContext)
    val launcherIcon = LauncherIconController(appContext)
    val updateManager = UpdateManager(appContext)

    val monitoredAppDao = db.monitoredAppDao()
    val unblockWindowDao = db.unblockWindowDao()
    val screenedCallDao = db.screenedCallDao()
    val blockedNumberDao = db.blockedNumberDao()
    val contactSimPrefDao = db.contactSimPrefDao()

    val smartUnblock = SmartUnblockManager(monitoredAppDao, unblockWindowDao)
    val blockList = BlockListManager(blockedNumberDao)
    val contactSimPref = ContactSimPrefManager(contactSimPrefDao)

    val callScreener = CallScreener(
        contactLookup = contactLookup,
        smartUnblock = smartUnblock,
        blockList = blockList,
        settingsStore = settingsStore,
        screenedCallDao = screenedCallDao,
    )

    /** Populate a starter list of common delivery/ride apps on first launch. */
    suspend fun seedDefaultMonitoredAppsIfEmpty() {
        if (monitoredAppDao.observeAll().first().isNotEmpty()) return
        DEFAULT_MONITORED_APPS.forEach { (pkg, label) ->
            monitoredAppDao.upsert(MonitoredApp(pkg, label, enabled = true, unblockMinutes = 30))
        }
    }

    private companion object {
        val DEFAULT_MONITORED_APPS = listOf(
            "in.amazon.mShop.android.shopping" to "Amazon",
            "com.ubercab" to "Uber",
            "in.swiggy.android" to "Swiggy",
            "com.application.zomato" to "Zomato",
            "com.olacabs.customer" to "Ola",
            "com.flipkart.android" to "Flipkart",
            "com.grofers.customerapp" to "Blinkit",
            "com.zeptoconsumerapp" to "Zepto",
        )
    }
}
