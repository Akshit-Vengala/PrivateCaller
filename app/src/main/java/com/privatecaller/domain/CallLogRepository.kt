package com.privatecaller.domain

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

enum class CallType { INCOMING, OUTGOING, MISSED, REJECTED, BLOCKED, VOICEMAIL, OTHER }

data class CallLogEntry(
    val id: Long,
    val number: String?,
    val cachedName: String?,
    val cachedPhotoUri: String?,
    val type: CallType,
    val date: Long,
    val durationSec: Long,
    // Precomputed on the IO thread so list rows don't format numbers/durations
    // on the main thread while scrolling (libphonenumber formatting is slow).
    val displayNumber: String,
    val displayDuration: String,
)

/**
 * Reads the device call log (the same one Google Phone shows). Available
 * fully once PrivateCaller is the default phone app. Emits a fresh list
 * whenever the log changes.
 */
class CallLogRepository(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED

    fun observe(limit: Int = 500): Flow<List<CallLogEntry>> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(query(limit))
            }
        }
        context.contentResolver.registerContentObserver(
            CallLog.Calls.CONTENT_URI, true, observer,
        )
        trySend(query(limit))
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }.flowOn(Dispatchers.IO)

    private fun query(limit: Int): List<CallLogEntry> {
        if (!hasPermission()) return emptyList()
        val cols = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.CACHED_PHOTO_URI,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
        )
        val out = ArrayList<CallLogEntry>()
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            cols,
            null,
            null,
            "${CallLog.Calls.DATE} DESC LIMIT $limit",
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(CallLog.Calls._ID)
            val iNum = c.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val iName = c.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
            val iPhoto = c.getColumnIndexOrThrow(CallLog.Calls.CACHED_PHOTO_URI)
            val iType = c.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val iDate = c.getColumnIndexOrThrow(CallLog.Calls.DATE)
            val iDur = c.getColumnIndexOrThrow(CallLog.Calls.DURATION)
            while (c.moveToNext()) {
                val number = c.getString(iNum)
                val durationSec = c.getLong(iDur)
                out += CallLogEntry(
                    id = c.getLong(iId),
                    number = number,
                    cachedName = c.getString(iName),
                    cachedPhotoUri = c.getString(iPhoto),
                    type = mapType(c.getInt(iType)),
                    date = c.getLong(iDate),
                    durationSec = durationSec,
                    displayNumber = PhoneFormat.pretty(context, number),
                    displayDuration = formatDuration(durationSec),
                )
            }
        }
        return out
    }

    private fun formatDuration(sec: Long): String {
        if (sec <= 0) return ""
        val m = sec / 60
        val s = sec % 60
        return if (m > 0) "${m}m ${s}s" else "${s}s"
    }

    private fun mapType(t: Int): CallType = when (t) {
        CallLog.Calls.INCOMING_TYPE -> CallType.INCOMING
        CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
        CallLog.Calls.MISSED_TYPE -> CallType.MISSED
        CallLog.Calls.REJECTED_TYPE -> CallType.REJECTED
        CallLog.Calls.BLOCKED_TYPE -> CallType.BLOCKED
        CallLog.Calls.VOICEMAIL_TYPE -> CallType.VOICEMAIL
        else -> CallType.OTHER
    }
}
