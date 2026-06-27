package com.privatecaller

import android.content.Context
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
import com.privatecaller.edition.Edition

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

    // SmartUnblock tables stay in the shared DB; only the "full" edition uses
    // them (via [Edition]). They're harmless dead tables in the playstore build.
    val monitoredAppDao = db.monitoredAppDao()
    val unblockWindowDao = db.unblockWindowDao()
    val screenedCallDao = db.screenedCallDao()
    val blockedNumberDao = db.blockedNumberDao()
    val contactSimPrefDao = db.contactSimPrefDao()

    val blockList = BlockListManager(blockedNumberDao)
    val contactSimPref = ContactSimPrefManager(contactSimPrefDao)

    // The unblock gate is edition-specific: real in "full", no-op in "playstore".
    val callScreener = CallScreener(
        contactLookup = contactLookup,
        unblock = Edition.unblockGate(this),
        blockList = blockList,
        settingsStore = settingsStore,
        screenedCallDao = screenedCallDao,
    )
}
