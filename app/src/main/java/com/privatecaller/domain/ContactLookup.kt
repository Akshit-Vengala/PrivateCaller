package com.privatecaller.domain

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract.PhoneLookup
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager

/** Result of checking an incoming number against the device contacts. */
data class ContactMatch(val isKnown: Boolean, val displayName: String?)

/**
 * Looks up phone numbers in the system Contacts provider. A number is
 * "known" (and therefore allowed) if it resolves to any saved contact.
 */
class ContactLookup(private val context: Context) {

    fun hasContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * PhoneLookup normalises numbers (country code, formatting) internally,
     * so we can pass the raw incoming number straight through.
     */
    fun lookup(number: String?): ContactMatch {
        if (number.isNullOrBlank() || !hasContactsPermission()) {
            return ContactMatch(isKnown = false, displayName = null)
        }
        val uri: Uri = Uri.withAppendedPath(
            PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(number),
        )
        return context.contentResolver.query(
            uri,
            arrayOf(PhoneLookup.DISPLAY_NAME),
            null, null, null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val name = cursor.getString(0)
                ContactMatch(isKnown = true, displayName = name)
            } else {
                ContactMatch(isKnown = false, displayName = null)
            }
        } ?: ContactMatch(isKnown = false, displayName = null)
    }
}
