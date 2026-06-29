package com.privatecaller.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.privatecaller.R
import com.privatecaller.ui.InCallActivity

/**
 * Posts the incoming-call notification while a call is ringing.
 *
 * Uses the platform's [NotificationCompat.CallStyle] — the same style Google's
 * dialer uses — on a HIGH-importance channel with a full-screen intent. The
 * system owns the layout, so the red Decline / green Answer buttons render with
 * their icons in BOTH portrait and landscape (a custom RemoteViews layout gets
 * clipped in the short landscape heads-up and the buttons vanish). The rich
 * coloured CallStyle treatment needs setColorized(true) + a colour on an
 * ongoing notification, which is what we set below. Behaviour:
 *
 *  - device locked or screen off  -> the OS fires the full-screen intent and
 *    launches [InCallActivity] (the full ringing screen with slide-to-answer);
 *  - device unlocked & interactive (home screen *or* another app in front) ->
 *    the OS shows a heads-up banner peeking over the current screen.
 *
 * Tapping the banner body (not a button) opens [InCallActivity]; tapping Answer
 * answers the call and opens it; Decline rejects the call.
 */
object IncomingCallNotifier {

    const val CHANNEL_ID = "incoming_call"
    private const val NOTIFICATION_ID = 1002

    fun createChannel(context: Context) {
        // HIGH importance is what lets the notification peek as a heads-up
        // banner and lets the full-screen intent escalate when locked.
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.incoming_call_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { setShowBadge(false) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun show(context: Context, callerName: String) {
        val immutableFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        // Tapping the body / full-screen escalation: open the ringing screen
        // WITHOUT answering (slide-to-answer is shown there).
        val openIntent = PendingIntent.getActivity(
            context, 200,
            Intent(context, InCallActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            immutableFlags,
        )

        // Answer button: open the call screen and answer immediately.
        val answerIntent = PendingIntent.getActivity(
            context, 201,
            Intent(context, InCallActivity::class.java)
                .putExtra(InCallActivity.EXTRA_ANSWER, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            immutableFlags,
        )

        // Decline button: reject in the background, no UI needed.
        val declineIntent = PendingIntent.getBroadcast(
            context, 202,
            Intent(CallActionReceiver.ACTION_DECLINE)
                .setClass(context, CallActionReceiver::class.java),
            immutableFlags,
        )

        val caller = Person.Builder().setName(callerName).build()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(callerName)
            .setContentText(context.getString(R.string.notif_incoming_call))
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // Colorized + colour on an ongoing call notification is what makes
            // CallStyle paint the full coloured Answer/Decline buttons (with
            // icons) that survive landscape — same as the stock dialer.
            .setColorized(true)
            .setColor(Color.parseColor("#1B5E20"))
            .setContentIntent(openIntent)
            .setFullScreenIntent(openIntent, true)
            .setStyle(
                NotificationCompat.CallStyle.forIncomingCall(caller, declineIntent, answerIntent)
            )
            .build()

        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }
}
