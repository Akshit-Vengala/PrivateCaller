package com.privatecaller.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.privatecaller.R
import com.privatecaller.data.db.ScreenOutcome
import com.privatecaller.ui.CallHistoryActivity
import java.util.concurrent.atomic.AtomicInteger

/**
 * Posts a notification for each silently-rejected call, grouping them so they
 * stack in the notification shade exactly like a stock phone app does.
 *
 * Each blocked call gets its own auto-cancel notification; a group-summary
 * notification is always posted/updated so Android collapses the group when
 * there are multiple entries.
 */
object BlockedCallNotifier {

    // v2: bumped from "blocked_calls" so the raised importance (DEFAULT, audible)
    // takes effect on upgrades — Android ignores importance changes to an
    // already-created channel, so a new id is required.
    const val CHANNEL_ID = "blocked_calls_v2"
    private const val OLD_CHANNEL_ID = "blocked_calls"
    private const val GROUP_KEY = "com.privatecaller.BLOCKED_CALLS"
    private const val SUMMARY_ID = 1001

    private val nextId = AtomicInteger(2000)

    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        runCatching { manager.deleteNotificationChannel(OLD_CHANNEL_ID) }
        // DEFAULT (not LOW) so a blocked call makes the normal notification
        // sound instead of arriving silently.
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.blocked_call_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { setShowBadge(true) }
        manager.createNotificationChannel(channel)
    }

    fun show(context: Context, number: String?, displayName: String?, outcome: ScreenOutcome) {
        val caller = displayName
            ?: number?.let { com.privatecaller.domain.PhoneFormat.pretty(context, it) }
            ?: context.getString(R.string.unknown_caller)
        val manager = context.getSystemService(NotificationManager::class.java)
        val title = context.getString(
            if (outcome == ScreenOutcome.BLOCKED_MANUAL) R.string.notif_blocked_manual_title
            else R.string.notif_blocked_title
        )

        // Tapping opens this number's call history in the app.
        val contentIntent = number?.let {
            PendingIntent.getActivity(
                context,
                it.hashCode(),
                Intent(context, CallHistoryActivity::class.java)
                    .putExtra(CallHistoryActivity.EXTRA_NUMBER, it)
                    .putExtra(CallHistoryActivity.EXTRA_NAME, displayName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        // Individual entry — auto-cancels when tapped.
        val individual = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_block)
            .setContentTitle(title)
            .setContentText(caller)
            .setGroup(GROUP_KEY)
            .setAutoCancel(true)
            .setShowWhen(true)
            .apply { if (contentIntent != null) setContentIntent(contentIntent) }
            .build()
        manager.notify(nextId.getAndIncrement(), individual)

        // Summary is required for the OS to collapse multiple entries into a
        // single group. It must be posted every time a new entry is added.
        val summary = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_block)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()
        manager.notify(SUMMARY_ID, summary)
    }
}
