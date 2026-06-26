package com.privatecaller.domain

import com.privatecaller.data.db.MonitoredAppDao
import com.privatecaller.data.db.UnblockWindow
import com.privatecaller.data.db.UnblockWindowDao
import java.util.concurrent.TimeUnit

/**
 * Opens and queries SmartUnblock windows. A window means "let unknown
 * numbers through until it expires" — used while you're expecting a call
 * from a delivery agent, cab driver, etc.
 */
class SmartUnblockManager(
    private val monitoredAppDao: MonitoredAppDao,
    private val windowDao: UnblockWindowDao,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /** True while any unblock window is still active. */
    suspend fun isUnblockActive(): Boolean = windowDao.activeNow(now()) != null

    suspend fun activeWindow(): UnblockWindow? = windowDao.activeNow(now())

    /**
     * Called from the notification listener. If [packageName] is a monitored,
     * enabled app and it has no still-active window, opens a fresh one.
     * Returns the opened window, or null if nothing was opened.
     */
    suspend fun onNotificationFrom(packageName: String): UnblockWindow? {
        val app = monitoredAppDao.enabledByPackage(packageName) ?: return null
        val current = now()
        if (windowDao.activeForPackage(packageName, current) != null) return null

        val window = UnblockWindow(
            triggerLabel = app.appLabel,
            triggerPackage = packageName,
            startedAt = current,
            expiresAt = current + TimeUnit.MINUTES.toMillis(app.unblockMinutes.toLong()),
        )
        val id = windowDao.insert(window)
        return window.copy(id = id)
    }

    /** Manual "unblock everyone for N minutes" from the UI. */
    suspend fun openManualWindow(minutes: Int): UnblockWindow {
        val current = now()
        val window = UnblockWindow(
            triggerLabel = "Manual",
            triggerPackage = null,
            startedAt = current,
            expiresAt = current + TimeUnit.MINUTES.toMillis(minutes.toLong()),
        )
        val id = windowDao.insert(window)
        return window.copy(id = id)
    }

    suspend fun cancelAll() = windowDao.cancelAllActive(now())

    /** Drop windows that expired more than a day ago to keep the table small. */
    suspend fun purgeOld() =
        windowDao.purgeExpiredBefore(now() - TimeUnit.DAYS.toMillis(1))
}
