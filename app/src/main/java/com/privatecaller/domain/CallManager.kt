package com.privatecaller.domain

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for the call PrivateCaller is currently handling as
 * the default phone app. The [PrivateInCallService] feeds calls in here and the
 * in-call UI observes the exposed flows. We keep it simple by tracking one
 * primary call at a time (sufficient for a basic dialer).
 */
object CallManager {

    private val _call = MutableStateFlow<Call?>(null)
    val call: StateFlow<Call?> = _call.asStateFlow()

    /** Mirrors the primary call's state (Call.STATE_*); null when no call. */
    private val _state = MutableStateFlow<Int?>(null)
    val state: StateFlow<Int?> = _state.asStateFlow()

    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    private val _speaker = MutableStateFlow(false)
    val speaker: StateFlow<Boolean> = _speaker.asStateFlow()

    /** Wall-clock time the call first became active; null until connected. */
    private val _connectedAt = MutableStateFlow<Long?>(null)
    val connectedAt: StateFlow<Long?> = _connectedAt.asStateFlow()

    /**
     * Whether the current call supports hold. Exposed as a flow (rather than a
     * one-shot query) because the platform often reports the HOLD capability a
     * beat *after* the call connects via onDetailsChanged — observing it keeps
     * the Hold button from popping in a second late.
     */
    private val _canHold = MutableStateFlow(false)
    val canHold: StateFlow<Boolean> = _canHold.asStateFlow()

    /** Set by the InCallService so we can route audio / mute. */
    private var service: InCallService? = null

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, newState: Int) {
            if (call == _call.value) {
                _state.value = newState
                _canHold.value = call.details.can(Call.Details.CAPABILITY_HOLD)
                if (newState == Call.STATE_ACTIVE && _connectedAt.value == null) {
                    _connectedAt.value = System.currentTimeMillis()
                }
            }
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            if (call == _call.value) {
                _canHold.value = details.can(Call.Details.CAPABILITY_HOLD)
            }
        }
    }

    fun attachService(inCallService: InCallService) {
        service = inCallService
    }

    fun detachService(inCallService: InCallService) {
        if (service == inCallService) service = null
    }

    fun onCallAdded(call: Call) {
        call.registerCallback(callback)
        _call.value = call
        @Suppress("DEPRECATION")
        val s = call.state
        _state.value = s
        _canHold.value = call.details.can(Call.Details.CAPABILITY_HOLD)
        if (s == Call.STATE_ACTIVE) _connectedAt.value = System.currentTimeMillis()
    }

    fun onCallRemoved(call: Call) {
        call.unregisterCallback(callback)
        if (_call.value == call) {
            _call.value = null
            _state.value = null
            _muted.value = false
            _speaker.value = false
            _connectedAt.value = null
            _canHold.value = false
        }
    }

    fun answer() {
        _call.value?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    fun reject() {
        _call.value?.reject(false, null)
    }

    fun hangup() {
        _call.value?.disconnect()
    }

    fun toggleHold() {
        val c = _call.value ?: return
        if (_state.value == Call.STATE_HOLDING) c.unhold() else c.hold()
    }

    fun toggleMute() {
        val next = !_muted.value
        service?.setMuted(next)
        _muted.value = next
    }

    fun toggleSpeaker() {
        val next = !_speaker.value
        service?.setAudioRoute(
            if (next) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE
        )
        _speaker.value = next
    }

    /** Send a DTMF tone (e.g. when navigating phone menus). */
    fun playDtmf(digit: Char) {
        _call.value?.let {
            it.playDtmfTone(digit)
            it.stopDtmfTone()
        }
    }
}
