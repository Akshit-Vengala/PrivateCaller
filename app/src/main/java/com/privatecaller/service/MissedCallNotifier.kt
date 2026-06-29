package com.privatecaller.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.core.app.NotificationCompat
import com.privatecaller.MainActivity
import com.privatecaller.R
import com.privatecaller.ui.CallHistoryActivity
import java.util.concurrent.atomic.AtomicInteger

/**
 * Posts a notification for each missed (rung-but-unanswered) call, grouped so
 * they stack in the shade like a stock phone app. This replaces the bare
 * system "N missed calls" notification (which carries no caller info and whose
 * tap target our app doesn't handle) — [PrivateInCallService] cancels the
 * system one via TelecomManager.
 *
 * Each entry shows the caller's name/number and, when tapped, opens that
 * number's call history (Recents if the number is withheld). Mirrors
 * [BlockedCallNotifier].
 */
object MissedCallNotifier {

    const val CHANNEL_ID = "missed_calls"
    private const val GROUP_KEY = "com.privatecaller.MISSED_CALLS"
    private const val SUMMARY_ID = 1003

    private val nextId = AtomicInteger(3000)

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.missed_call_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { setShowBadge(true) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun show(context: Context, number: String?, displayName: String?) {
        val caller = displayName?.takeIf { it.isNotBlank() }
            ?: number?.let { com.privatecaller.domain.PhoneFormat.pretty(context, it) }
            ?: context.getString(R.string.unknown_caller)
        val manager = context.getSystemService(NotificationManager::class.java)

        // Tap target: this number's call history, or Recents if it's withheld.
        val tapIntent = if (number != null) {
            Intent(context, CallHistoryActivity::class.java)
                .putExtra(CallHistoryActivity.EXTRA_NUMBER, number)
                .putExtra(CallHistoryActivity.EXTRA_NAME, displayName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        } else {
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            number?.hashCode() ?: 0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val individual = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_missed)
            .setColor(Color.parseColor("#C62828"))
            .setContentTitle(context.getString(R.string.notif_missed_title))
            .setContentText(caller)
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setGroup(GROUP_KEY)
            .setAutoCancel(true)
            .setShowWhen(true)
            .setContentIntent(contentIntent)
            .build()
        manager.notify(nextId.getAndIncrement(), individual)

        // Summary so the OS collapses multiple entries into one group.
        val summary = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_missed)
            .setColor(Color.parseColor("#C62828"))
            .setContentTitle(context.getString(R.string.notif_missed_summary))
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()
        manager.notify(SUMMARY_ID, summary)
    }
}
