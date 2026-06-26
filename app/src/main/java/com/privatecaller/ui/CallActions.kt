package com.privatecaller.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat

/**
 * Places a call as the default phone app (routing through our InCallService),
 * falling back to the system dialer if we lack the CALL_PHONE permission.
 * When [accountHandle] is supplied the call is placed on that SIM.
 */
fun placeCallOrDial(
    context: Context,
    number: String,
    accountHandle: PhoneAccountHandle? = null,
) {
    val uri = Uri.fromParts("tel", number, null)
    val hasPerm = ContextCompat.checkSelfPermission(
        context, Manifest.permission.CALL_PHONE,
    ) == PackageManager.PERMISSION_GRANTED
    if (hasPerm) {
        val tm = context.getSystemService(TelecomManager::class.java)
        val extras = accountHandle?.let {
            Bundle().apply { putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, it) }
        }
        runCatching { tm?.placeCall(uri, extras) }
            .onFailure { context.startActivity(Intent(Intent.ACTION_DIAL, uri)) }
    } else {
        context.startActivity(Intent(Intent.ACTION_DIAL, uri))
    }
}
