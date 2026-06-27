package com.privatecaller.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.privatecaller.PrivateCallerApp
import com.privatecaller.domain.SmartUnblockManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Watches notifications from delivery/ride apps. When a notification from a
 * monitored app appears, opens a SmartUnblock window so the user can receive
 * the inevitable call from an unknown number (delivery agent, cab driver…).
 */
class SmartUnblockNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val pkg = sbn?.packageName ?: return
        // Ignore our own notifications to avoid feedback loops.
        if (pkg == packageName) return

        val container = PrivateCallerApp.container(this)
        scope.launch {
            if (!container.settingsStore.current().smartUnblockEnabled) return@launch
            val manager = SmartUnblockManager(container.monitoredAppDao, container.unblockWindowDao)
            val window = manager.onNotificationFrom(pkg) ?: return@launch
            UnblockNotifier.show(this@SmartUnblockNotificationListener, window)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
