package com.privatecaller.ui

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.privatecaller.service.SmartUnblockNotificationListener

/** Helpers for the one-time permissions / default-app setup the app needs. */
object Setup {

    /** True if PrivateCaller is the device's default call-screening app. */
    fun isScreeningRoleHeld(context: Context): Boolean {
        val rm = context.getSystemService(RoleManager::class.java) ?: return false
        return rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) &&
            rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    }

    /** Intent that asks the user to make us the call-screening app. */
    fun screeningRoleIntent(context: Context): Intent? {
        val rm = context.getSystemService(RoleManager::class.java) ?: return null
        if (!rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) return null
        return rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
    }

    /** True if PrivateCaller is the device's default phone (dialer) app. */
    fun isDialerRoleHeld(context: Context): Boolean {
        val rm = context.getSystemService(RoleManager::class.java) ?: return false
        return rm.isRoleAvailable(RoleManager.ROLE_DIALER) &&
            rm.isRoleHeld(RoleManager.ROLE_DIALER)
    }

    /** Intent that asks the user to make us the default phone app. */
    fun dialerRoleIntent(context: Context): Intent? {
        val rm = context.getSystemService(RoleManager::class.java) ?: return null
        if (!rm.isRoleAvailable(RoleManager.ROLE_DIALER)) return null
        return rm.createRequestRoleIntent(RoleManager.ROLE_DIALER)
    }

    /** True if the user has granted notification-listener access (for SmartUnblock). */
    fun isNotificationAccessGranted(context: Context): Boolean {
        val component = ComponentName(context, SmartUnblockNotificationListener::class.java)
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ) ?: return false
        return flat.split(":").any {
            ComponentName.unflattenFromString(it) == component
        }
    }

    fun notificationAccessIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    /**
     * Fallback for devices/situations where the role request intent can't be
     * created or shown: opens the system "Default apps" screen so the user can
     * pick the screening app manually.
     */
    fun defaultAppsSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
}
