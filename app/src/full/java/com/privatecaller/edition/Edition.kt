package com.privatecaller.edition

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.privatecaller.AppContainer
import com.privatecaller.NavTab
import com.privatecaller.PrivateCallerApp
import com.privatecaller.R
import com.privatecaller.data.db.MonitoredApp
import com.privatecaller.domain.SmartUnblockManager
import com.privatecaller.domain.UnblockGate
import com.privatecaller.service.SmartUnblockNotificationListener
import com.privatecaller.ui.SettingsViewModel
import com.privatecaller.ui.SmartUnblockScreen
import com.privatecaller.ui.SmartUnblockViewModel
import com.privatecaller.ui.SwitchRow
import com.privatecaller.ui.UpdateSection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * "full" edition wiring: SmartUnblock (notification reading) + the in-app
 * GitHub updater. The [playstore] flavor provides a no-op object with the same
 * shape, so flavor-neutral code in src/main never references these features.
 */
object Edition {

    /** Real SmartUnblock gate, backed by the window/monitored-app tables. */
    fun unblockGate(container: AppContainer): UnblockGate =
        SmartUnblockManager(container.monitoredAppDao, container.unblockWindowDao)

    fun onAppCreate(app: PrivateCallerApp, container: AppContainer, scope: CoroutineScope) {
        createUnblockChannel(app)
        scope.launch {
            seedDefaultMonitoredAppsIfEmpty(container)
            SmartUnblockManager(container.monitoredAppDao, container.unblockWindowDao).purgeOld()
        }
    }

    fun isNotificationAccessGranted(context: Context): Boolean {
        val component = ComponentName(context, SmartUnblockNotificationListener::class.java)
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ) ?: return false
        return flat.split(":").any { ComponentName.unflattenFromString(it) == component }
    }

    fun openNotificationAccessSettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    /** SmartUnblock tab in the bottom navigation. */
    fun extraTabs(): List<NavTab> = listOf(
        NavTab("Unblock", Icons.Filled.Shield) { modifier ->
            val context = LocalContext.current
            val container = remember { PrivateCallerApp.container(context) }
            val vm: SmartUnblockViewModel = viewModel(
                factory = viewModelFactory { initializer { SmartUnblockViewModel(container) } },
            )
            SmartUnblockScreen(vm, modifier)
        },
    )

    @Composable
    fun SmartUnblockSetting(viewModel: SettingsViewModel) {
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        HorizontalDivider()
        SwitchRow(
            title = "SmartUnblock",
            subtitle = "Auto-allow unknown calls when delivery/ride apps notify you",
            checked = settings.smartUnblockEnabled,
            onChange = viewModel::setSmartUnblock,
        )
    }

    @Composable
    fun UpdaterSetting() {
        HorizontalDivider()
        UpdateSection()
    }

    private fun createUnblockChannel(app: PrivateCallerApp) {
        val channel = NotificationChannel(
            PrivateCallerApp.UNBLOCK_CHANNEL_ID,
            app.getString(R.string.unblock_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        app.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private suspend fun seedDefaultMonitoredAppsIfEmpty(container: AppContainer) {
        if (container.monitoredAppDao.observeAll().first().isNotEmpty()) return
        DEFAULT_MONITORED_APPS.forEach { (pkg, label) ->
            container.monitoredAppDao.upsert(
                MonitoredApp(pkg, label, enabled = true, unblockMinutes = 30),
            )
        }
    }

    private val DEFAULT_MONITORED_APPS = listOf(
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
