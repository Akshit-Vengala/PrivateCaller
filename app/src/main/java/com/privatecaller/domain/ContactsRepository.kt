package com.privatecaller.domain

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ContactItem(
    val contactId: Long,
    val name: String,
    val number: String,
    val photoUri: String?,
    // Precomputed pretty number so list rows don't format on the main thread.
    val displayNumber: String,
)

/** Reads the device contacts for the dialer suggestions and search. */
class ContactsRepository(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun loadAll(): List<ContactItem> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        val cols = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
        )
        val seen = HashSet<String>()
        val out = ArrayList<ContactItem>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            cols,
            null,
            null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE ASC",
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val iName = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val iNum = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val iPhoto = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
            while (c.moveToNext()) {
                val number = c.getString(iNum) ?: continue
                val name = c.getString(iName) ?: number
                // De-dupe the same name+number appearing across raw contacts.
                val key = "$name|${number.filter { it.isDigit() }}"
                if (!seen.add(key)) continue
                out += ContactItem(
                    contactId = c.getLong(iId),
                    name = name,
                    number = number,
                    photoUri = c.getString(iPhoto),
                    displayNumber = PhoneFormat.pretty(context, number),
                )
            }
        }
        out
    }
}

/** T9 / text matching used by the dialer suggestions and the Recents search. */
object ContactSearch {

    private val T9 = buildMap {
        "abc".forEach { put(it, '2') }
        "def".forEach { put(it, '3') }
        "ghi".forEach { put(it, '4') }
        "jkl".forEach { put(it, '5') }
        "mno".forEach { put(it, '6') }
        "pqrs".forEach { put(it, '7') }
        "tuv".forEach { put(it, '8') }
        "wxyz".forEach { put(it, '9') }
    }

    fun filter(items: List<ContactItem>, query: String): List<ContactItem> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val digits = q.filter { it.isDigit() }
        val numericQuery = q.isNotEmpty() && q.all { it.isDigit() || it in "+ -()" }
        return items.filter { item ->
            if (item.name.contains(q, ignoreCase = true)) return@filter true
            val itemDigits = item.number.filter { it.isDigit() }
            if (digits.isNotEmpty() && itemDigits.contains(digits)) return@filter true
            if (numericQuery && digits.isNotEmpty()) {
                val t9 = item.name.lowercase().mapNotNull { T9[it] }.joinToString("")
                if (t9.contains(digits)) return@filter true
            }
            false
        }
    }
}
