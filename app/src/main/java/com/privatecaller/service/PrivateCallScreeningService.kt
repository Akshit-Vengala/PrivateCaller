package com.privatecaller.service

import android.telecom.Call
import android.telecom.CallScreeningService
import com.privatecaller.PrivateCallerApp
import com.privatecaller.domain.CallScreener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The system hands every incoming call to this service first. We allow
 * known contacts (and unknown callers while a SmartUnblock window is open)
 * and silently reject everyone else.
 */
class PrivateCallScreeningService : CallScreeningService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onScreenCall(callDetails: Call.Details) {
        // Only screen genuine incoming calls.
        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        val number = callDetails.handle?.schemeSpecificPart
        val container = PrivateCallerApp.container(this)
        val screener: CallScreener = container.callScreener
        val simSlot = container.simManager.slotForHandle(callDetails.accountHandle)

        scope.launch {
            val decision = screener.decide(number, simSlot)
            val response = CallResponse.Builder()
                .setDisallowCall(!decision.allow)
                .setRejectCall(!decision.allow)
                // Keep blocked calls IN the call log (shown as "Blocked" in
                // Recents) but suppress the missed-call notification.
                .setSkipCallLog(false)
                .setSkipNotification(!decision.allow)
                .build()
            respondToCall(callDetails, response)
            screener.log(number, decision)
        }
    }
}
