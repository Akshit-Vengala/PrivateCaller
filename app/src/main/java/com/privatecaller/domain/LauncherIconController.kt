package com.privatecaller.domain

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Swaps the home-screen launcher icon by enabling one of two activity-aliases
 * (green "On" vs grey "Off") depending on whether call screening is enabled.
 * Exactly one alias is ever enabled, and the target is enabled before the other
 * is disabled so a launcher entry always exists.
 */
class LauncherIconController(private val context: Context) {

    fun apply(screeningEnabled: Boolean) {
        val pm = context.packageManager
        val pkg = context.packageName
        val on = ComponentName(pkg, "$pkg.LauncherOn")
        val off = ComponentName(pkg, "$pkg.LauncherOff")

        val enable = if (screeningEnabled) on else off
        val disable = if (screeningEnabled) off else on

        if (pm.getComponentEnabledSetting(enable) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            pm.setComponentEnabledSetting(
                enable,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
        if (pm.getComponentEnabledSetting(disable) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
            pm.setComponentEnabledSetting(
                disable,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
