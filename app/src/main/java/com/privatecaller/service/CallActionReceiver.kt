package com.privatecaller.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.privatecaller.domain.CallManager

/**
 * Receives action button taps from the ongoing-call notification
 * (speaker toggle, mute toggle, end call).
 */
class CallActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SPEAKER -> CallManager.toggleSpeaker()
            ACTION_MUTE -> CallManager.toggleMute()
            ACTION_END_CALL -> CallManager.hangup()
            // Decline an incoming call straight from the heads-up banner.
            ACTION_DECLINE -> CallManager.reject()
        }
    }

    companion object {
        const val ACTION_SPEAKER = "com.privatecaller.action.SPEAKER"
        const val ACTION_MUTE = "com.privatecaller.action.MUTE"
        const val ACTION_END_CALL = "com.privatecaller.action.END_CALL"
        const val ACTION_DECLINE = "com.privatecaller.action.DECLINE"
    }
}
