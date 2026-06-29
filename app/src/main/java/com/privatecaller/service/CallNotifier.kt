package com.privatecaller.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.telecom.Call
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.privatecaller.R
import com.privatecaller.ui.InCallActivity

/**
 * Posts and updates the persistent notification shown while a call is active.
 * Tapping the notification (anywhere except the action buttons) reopens
 * [InCallActivity]. The notification is posted as a regular ongoing notification
 * — no startForeground() needed since InCallService is kept alive by the system.
 */
object CallNotifier {

    const val CHANNEL_ID = "active_call"
    private const val NOTIFICATION_ID = 1000

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.active_call_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun show(
        context: Context,
        callerName: String,
        state: Int?,
        connectedAt: Long?,
        muted: Boolean,
        speaker: Boolean,
    ) {
        val isActive = state == Call.STATE_ACTIVE
        val isHolding = state == Call.STATE_HOLDING

        val statusText = when {
            state == Call.STATE_RINGING -> context.getString(R.string.notif_incoming_call)
            state == Call.STATE_DIALING || state == Call.STATE_CONNECTING ->
                context.getString(R.string.notif_calling)
            isHolding -> context.getString(R.string.notif_on_hold)
            isActive -> context.getString(R.string.notif_on_call)
            else -> ""
        }

        val immutableFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val contentIntent = PendingIntent.getActivity(
            context, 100,
            Intent(context, InCallActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            immutableFlags,
        )

        val speakerIntent = PendingIntent.getBroadcast(
            context, 101,
            Intent(CallActionReceiver.ACTION_SPEAKER)
                .setClass(context, CallActionReceiver::class.java),
            immutableFlags,
        )
        val muteIntent = PendingIntent.getBroadcast(
            context, 102,
            Intent(CallActionReceiver.ACTION_MUTE)
                .setClass(context, CallActionReceiver::class.java),
            immutableFlags,
        )
        val endIntent = PendingIntent.getBroadcast(
            context, 103,
            Intent(CallActionReceiver.ACTION_END_CALL)
                .setClass(context, CallActionReceiver::class.java),
            immutableFlags,
        )

        // Compact button row, reachable without expanding. Speaker/Mute get a
        // subtle green tint when enabled; the row is identical collapsed/expanded.
        val titleLine = if (statusText.isBlank()) callerName else "$callerName · $statusText"
        fun buildActions() = RemoteViews(context.packageName, R.layout.notification_call).apply {
            setTextViewText(R.id.notif_title, titleLine)

            setOnClickPendingIntent(R.id.action_speaker, speakerIntent)
            setOnClickPendingIntent(R.id.action_mute, muteIntent)
            setOnClickPendingIntent(R.id.action_end, endIntent)

            setTextViewText(R.id.label_speaker, context.getString(R.string.notif_speaker))
            if (speaker) {
                setInt(R.id.action_speaker, "setBackgroundResource", R.drawable.bg_notif_btn_active)
                setInt(R.id.icon_speaker, "setColorFilter", Color.WHITE)
                setTextColor(R.id.label_speaker, Color.WHITE)
            }

            setImageViewResource(
                R.id.icon_mute,
                if (muted) R.drawable.ic_notif_mic_off else R.drawable.ic_notif_mic,
            )
            setTextViewText(
                R.id.label_mute,
                context.getString(if (muted) R.string.notif_unmute else R.string.notif_mute),
            )
            if (muted) {
                setInt(R.id.action_mute, "setBackgroundResource", R.drawable.bg_notif_btn_active)
                setInt(R.id.icon_mute, "setColorFilter", Color.WHITE)
                setTextColor(R.id.label_mute, Color.WHITE)
            }

            setTextViewText(R.id.label_end, context.getString(R.string.notif_end))
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(callerName)
            .setContentText(statusText)
            .setOngoing(true)
            .setShowWhen(connectedAt != null)
            .setUsesChronometer(connectedAt != null && isActive)
            .setWhen(connectedAt ?: System.currentTimeMillis())
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(buildActions())
            .setCustomBigContentView(buildActions())
            .build()

        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }
}
