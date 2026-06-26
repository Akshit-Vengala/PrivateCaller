package com.privatecaller.service

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService
import com.privatecaller.domain.CallManager
import com.privatecaller.ui.InCallActivity

/**
 * Bound by the system when PrivateCaller is the default phone app. Owns the
 * lifecycle of active calls and launches our full-screen in-call UI.
 */
class PrivateInCallService : InCallService() {

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.attachService(this)
        CallManager.onCallAdded(call)
        startActivity(
            Intent(this, InCallActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        CallManager.onCallRemoved(call)
        CallManager.detachService(this)
    }
}
