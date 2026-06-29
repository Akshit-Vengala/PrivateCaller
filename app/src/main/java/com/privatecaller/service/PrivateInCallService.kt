package com.privatecaller.service

import android.app.KeyguardManager
import android.content.Intent
import android.os.PowerManager
import android.telecom.Call
import android.telecom.DisconnectCause
import android.telecom.InCallService
import android.telecom.TelecomManager
import com.privatecaller.PrivateCallerApp
import com.privatecaller.R
import com.privatecaller.domain.CallManager
import com.privatecaller.ui.InCallActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Bound by the system when PrivateCaller is the default phone app. Owns the
 * lifecycle of active calls and launches our full-screen in-call UI.
 *
 * Also manages the persistent status-bar notification that lets users return
 * to the call screen after navigating away, and exposes speaker/mute/end
 * actions directly from the notification.
 */
class PrivateInCallService : InCallService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    // Outlives the per-call [scope] (which is cleared on each onCallRemoved) so
    // the delayed cancel of the system missed-call notification still runs.
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var callerName = ""

    /** Whether the incoming-call banner is currently posted (ringing). */
    private var incomingShown = false

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.attachService(this)
        CallManager.onCallAdded(call)

        // Presentation rules for the call UI:
        //  - Outgoing calls      -> always the full call screen.
        //  - Incoming, device idle (locked OR screen off) -> full call screen,
        //    launched explicitly here so InCallActivity can turn the screen on
        //    and show over the keyguard (the full-screen intent alone proved
        //    unreliable at waking the display).
        //  - Incoming, device awake & unlocked (home screen or another app) ->
        //    no activity launch; the heads-up Answer/Decline banner posted in
        //    observeCallState peeks over the foreground. See IncomingCallNotifier.
        val isIncoming = call.details?.callDirection == Call.Details.DIRECTION_INCOMING
        if (!isIncoming || isDeviceIdle()) {
            startActivity(
                Intent(this, InCallActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }

        val number = call.details?.handle?.schemeSpecificPart
        scope.launch {
            // Look up display name on IO then start observing call state.
            val name = withContext(Dispatchers.IO) {
                PrivateCallerApp.container(this@PrivateInCallService)
                    .contactLookup.lookup(number).displayName
            }
            callerName = name
                ?: number?.let { com.privatecaller.domain.PhoneFormat.pretty(this@PrivateInCallService, it) }
                ?: getString(R.string.unknown_caller)
            observeCallState()
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        maybeNotifyMissed(call)
        CallManager.onCallRemoved(call)
        CallManager.detachService(this)
        scope.coroutineContext.cancelChildren()
        incomingShown = false
        hideIncoming()
        CallNotifier.cancel(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        cleanupScope.cancel()
    }

    /**
     * If an incoming call rang but was never answered (DisconnectCause.MISSED),
     * post our own richer missed-call notification and suppress the bare system
     * one. User-declined calls report REJECTED (not MISSED) and blocked calls
     * never reach the InCallService, so neither triggers this.
     */
    private fun maybeNotifyMissed(call: Call) {
        val details = call.details ?: return
        val incoming = details.callDirection == Call.Details.DIRECTION_INCOMING
        val missed = details.disconnectCause?.code == DisconnectCause.MISSED
        if (!incoming || !missed) return

        val number = details.handle?.schemeSpecificPart
        MissedCallNotifier.show(this, number, callerName)

        // Cancel the system's "N missed calls" notification — now, and again
        // shortly after in case telephony posts it just after we disconnect.
        val telecom = getSystemService(TelecomManager::class.java)
        runCatching { telecom?.cancelMissedCallsNotification() }
        cleanupScope.launch {
            delay(1500)
            runCatching { telecom?.cancelMissedCallsNotification() }
        }
    }

    /**
     * True when the phone is locked or its screen is off — i.e. the user is not
     * actively using another app, so the call should take the full screen rather
     * than peek as a banner. Needs no special permission (unlike foreground-app
     * detection), so the home screen still counts as "awake & unlocked".
     */
    private fun isDeviceIdle(): Boolean {
        val pm = getSystemService(PowerManager::class.java)
        val km = getSystemService(KeyguardManager::class.java)
        val screenOff = pm?.isInteractive != true
        val locked = km?.isKeyguardLocked == true
        return screenOff || locked
    }

    /**
     * Presents the ringing call. On the full edition, when the user has granted
     * "Display over other apps", we draw our own overlay banner (icons survive
     * landscape). Otherwise we fall back to the system CallStyle notification.
     */
    private fun showIncoming() {
        if (com.privatecaller.BuildConfig.HAS_CALL_OVERLAY &&
            IncomingCallOverlay.canDraw(this)
        ) {
            IncomingCallOverlay.show(
                context = this,
                callerName = callerName,
                onBody = { IncomingCallOverlay.hide(this); openInCall(answer = false) },
                onAnswer = { IncomingCallOverlay.hide(this); openInCall(answer = true) },
                onDecline = { IncomingCallOverlay.hide(this); CallManager.reject() },
            )
        } else {
            IncomingCallNotifier.show(this, callerName)
        }
    }

    /** Hides whichever incoming-call presentation is currently showing. */
    private fun hideIncoming() {
        IncomingCallOverlay.hide(this)
        IncomingCallNotifier.cancel(this)
    }

    private fun openInCall(answer: Boolean) {
        startActivity(
            Intent(this, InCallActivity::class.java)
                .apply { if (answer) putExtra(InCallActivity.EXTRA_ANSWER, true) }
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }

    private fun observeCallState() {
        scope.launch {
            combine(
                CallManager.state,
                CallManager.muted,
                CallManager.speaker,
                CallManager.connectedAt,
            ) { state, muted, speaker, connectedAt ->
                CallSnapshot(state, muted, speaker, connectedAt)
            }.collect { snap ->
                when {
                    snap.state == null || snap.state == Call.STATE_DISCONNECTED -> {
                        hideIncoming()
                        CallNotifier.cancel(this@PrivateInCallService)
                    }
                    // Ringing: the Answer/Decline banner is the only UI. Posted
                    // once so it doesn't re-peek/re-add on every flow emission.
                    snap.state == Call.STATE_RINGING -> {
                        CallNotifier.cancel(this@PrivateInCallService)
                        if (!incomingShown) {
                            showIncoming()
                            incomingShown = true
                        }
                    }
                    // Connected / dialing / on hold: ongoing-call notification.
                    else -> {
                        if (incomingShown) {
                            hideIncoming()
                            incomingShown = false
                        }
                        CallNotifier.show(
                            context = this@PrivateInCallService,
                            callerName = callerName,
                            state = snap.state,
                            connectedAt = snap.connectedAt,
                            muted = snap.muted,
                            speaker = snap.speaker,
                        )
                    }
                }
            }
        }
    }

    private data class CallSnapshot(
        val state: Int?,
        val muted: Boolean,
        val speaker: Boolean,
        val connectedAt: Long?,
    )
}
