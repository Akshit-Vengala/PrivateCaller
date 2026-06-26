package com.privatecaller.domain

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

data class SimSlot(
    val slotIndex: Int,
    val subscriptionId: Int,
    val label: String,
)

/**
 * Enumerates the device SIMs and maps an incoming call's phone account to the
 * SIM slot it arrived on, so screening can be configured per-SIM.
 */
class SimManager(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED

    fun availableSims(): List<SimSlot> {
        if (!hasPermission()) return emptyList()
        val sm = context.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
        val list = runCatching { sm.activeSubscriptionInfoList }.getOrNull() ?: return emptyList()
        return list.sortedBy { it.simSlotIndex }.map { info ->
            val carrier = info.displayName?.toString()?.takeIf { it.isNotBlank() }
                ?: info.carrierName?.toString()?.takeIf { it.isNotBlank() }
            SimSlot(
                slotIndex = info.simSlotIndex,
                subscriptionId = info.subscriptionId,
                label = "SIM ${info.simSlotIndex + 1}" + (carrier?.let { " · $it" } ?: ""),
            )
        }
    }

    /** Maps an incoming call's phone account to its SIM slot index, or null. */
    fun slotForHandle(handle: PhoneAccountHandle?): Int? {
        if (handle == null || !hasPermission()) return null
        val subId = subscriptionIdForHandle(handle) ?: return null
        val sm = context.getSystemService(SubscriptionManager::class.java) ?: return null
        val info = runCatching { sm.getActiveSubscriptionInfo(subId) }.getOrNull()
        return info?.simSlotIndex
    }

    /** The phone account (for placeCall) belonging to a given SIM slot, or null. */
    fun phoneAccountHandleForSlot(slot: Int): PhoneAccountHandle? {
        if (!hasPermission()) return null
        val tm = context.getSystemService(TelecomManager::class.java) ?: return null
        val accounts = runCatching { tm.callCapablePhoneAccounts }.getOrNull() ?: return null
        return accounts.firstOrNull { slotForHandle(it) == slot }
    }

    private fun subscriptionIdForHandle(handle: PhoneAccountHandle): Int? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val tm = context.getSystemService(TelephonyManager::class.java)
            val id = runCatching { tm?.getSubscriptionId(handle) }.getOrNull()
            if (id != null && id != SubscriptionManager.INVALID_SUBSCRIPTION_ID) return id
        }
        // Fallback (API 29): the handle id is commonly the subscription id.
        return handle.id.toIntOrNull()
    }
}
