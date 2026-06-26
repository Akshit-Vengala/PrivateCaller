package com.privatecaller.service

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.privatecaller.PrivateCallerApp
import com.privatecaller.R
import com.privatecaller.data.db.UnblockWindow
import java.text.DateFormat
import java.util.Date

/** Shows / clears the ongoing notification for an active SmartUnblock window. */
object UnblockNotifier {

    private const val NOTIFICATION_ID = 42

    fun show(context: Context, window: UnblockWindow) {
        val ends = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(window.expiresAt))
        val notification = NotificationCompat.Builder(context, PrivateCallerApp.UNBLOCK_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.unblock_active_title))
            .setContentText(context.getString(R.string.unblock_active_text, window.triggerLabel, ends))
            .setOngoing(true)
            .setShowWhen(false)
            .build()
        manager(context).notify(NOTIFICATION_ID, notification)
    }

    fun clear(context: Context) = manager(context).cancel(NOTIFICATION_ID)

    private fun manager(context: Context) =
        context.getSystemService(NotificationManager::class.java)
}
