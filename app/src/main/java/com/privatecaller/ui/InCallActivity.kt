package com.privatecaller.ui

import android.os.Build
import android.os.Bundle
import android.telecom.Call
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.privatecaller.PrivateCallerApp
import com.privatecaller.domain.CallManager
import com.privatecaller.ui.theme.PrivateCallerTheme

/**
 * Full-screen in-call UI shown while PrivateCaller is the default phone app.
 * Renders incoming/outgoing/active states and the call controls.
 */
class InCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over the lock screen and wake the display for incoming calls.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        setContent {
            PrivateCallerTheme {
                InCallScreen(onFinished = { finish() })
            }
        }
    }
}

@Composable
private fun InCallScreen(onFinished: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val call by CallManager.call.collectAsStateWithLifecycle()
    val state by CallManager.state.collectAsStateWithLifecycle()
    val muted by CallManager.muted.collectAsStateWithLifecycle()
    val speaker by CallManager.speaker.collectAsStateWithLifecycle()
    val connectedAt by CallManager.connectedAt.collectAsStateWithLifecycle()

    // No call (ended) -> close the screen.
    androidx.compose.runtime.LaunchedEffect(call, state) {
        if (call == null || state == Call.STATE_DISCONNECTED) onFinished()
    }

    var showKeypad by remember { mutableStateOf(false) }

    // Ticking call duration once connected.
    var elapsedSec by remember { mutableStateOf(0L) }
    androidx.compose.runtime.LaunchedEffect(connectedAt) {
        val start = connectedAt
        if (start == null) {
            elapsedSec = 0L
        } else {
            while (true) {
                elapsedSec = (System.currentTimeMillis() - start) / 1000
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    val number = call?.details?.handle?.schemeSpecificPart
    val displayName = androidx.compose.runtime.remember(number) {
        val lookup = PrivateCallerApp.container(context).contactLookup
        number?.let { lookup.lookup(it).displayName }
    }
    val title = displayName ?: number ?: "Unknown"
    val statusText = when (state) {
        Call.STATE_RINGING -> "Incoming call"
        Call.STATE_DIALING, Call.STATE_CONNECTING -> "Calling…"
        Call.STATE_ACTIVE -> "On call"
        Call.STATE_HOLDING -> "On hold"
        Call.STATE_DISCONNECTED -> "Call ended"
        else -> ""
    }
    val isRinging = state == Call.STATE_RINGING
    val isActive = state == Call.STATE_ACTIVE || state == Call.STATE_HOLDING
    val isHolding = state == Call.STATE_HOLDING
    // Once connected, show the live duration in place of the status line.
    val statusLine = if (connectedAt != null && !isHolding) formatElapsed(elapsedSec)
        else if (isHolding) "On hold · ${formatElapsed(elapsedSec)}"
        else statusText

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.size(72.dp))
            Text(statusLine, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (displayName != null && number != null) {
                Spacer(Modifier.size(4.dp))
                Text(number, style = MaterialTheme.typography.bodyLarge)
            }

            Spacer(Modifier.weight(1f))

            // Mid controls (mute / hold / speaker / keypad) once connected.
            if (isActive) {
                if (showKeypad) {
                    DtmfPad(
                        onDigit = { CallManager.playDtmf(it) },
                        onHide = { showKeypad = false },
                    )
                } else {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    ) {
                        ControlButton(
                            icon = if (muted) Icons.Filled.MicOff else Icons.Filled.Mic,
                            label = "Mute",
                            active = muted,
                            onClick = { CallManager.toggleMute() },
                        )
                        ControlButton(
                            icon = Icons.Filled.Dialpad,
                            label = "Keypad",
                            active = false,
                            onClick = { showKeypad = true },
                        )
                        if (CallManager.canHold()) {
                            ControlButton(
                                icon = if (isHolding) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                label = if (isHolding) "Resume" else "Hold",
                                active = isHolding,
                                onClick = { CallManager.toggleHold() },
                            )
                        }
                        ControlButton(
                            icon = Icons.Filled.VolumeUp,
                            label = "Speaker",
                            active = speaker,
                            onClick = { CallManager.toggleSpeaker() },
                        )
                    }
                }
                Spacer(Modifier.size(32.dp))
            }

            // Bottom action row.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement =
                    if (isRinging) Arrangement.SpaceEvenly else Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isRinging) {
                    RoundActionButton(
                        icon = Icons.Filled.Call,
                        background = Color(0xFF2E7D32),
                        contentDescription = "Answer",
                        onClick = { CallManager.answer() },
                    )
                    RoundActionButton(
                        icon = Icons.Filled.CallEnd,
                        background = Color(0xFFC62828),
                        contentDescription = "Reject",
                        onClick = { CallManager.reject() },
                    )
                } else {
                    RoundActionButton(
                        icon = Icons.Filled.CallEnd,
                        background = Color(0xFFC62828),
                        contentDescription = "End call",
                        onClick = { CallManager.hangup() },
                    )
                }
            }
            Spacer(Modifier.size(48.dp))
        }
    }
}

private val DTMF_KEYS = listOf(
    '1', '2', '3',
    '4', '5', '6',
    '7', '8', '9',
    '*', '0', '#',
)

@Composable
private fun DtmfPad(onDigit: (Char) -> Unit, onHide: () -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DTMF_KEYS.chunked(3).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                row.forEach { digit ->
                    androidx.compose.material3.TextButton(
                        onClick = { onDigit(digit) },
                        modifier = Modifier.size(64.dp),
                    ) {
                        Text(digit.toString(), fontSize = 24.sp)
                    }
                }
            }
        }
        androidx.compose.material3.IconButton(onClick = onHide) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Hide keypad")
        }
    }
}

private fun formatElapsed(totalSec: Long): String {
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

@Composable
private fun RoundActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    background: Color,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = background,
        modifier = Modifier.size(72.dp),
    ) {
        androidx.compose.material3.IconButton(onClick = onClick, modifier = Modifier.fillMaxSize()) {
            Icon(icon, contentDescription = contentDescription, tint = Color.White)
        }
    }
}

@Composable
private fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(56.dp),
        ) {
            androidx.compose.material3.IconButton(onClick = onClick, modifier = Modifier.fillMaxSize()) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = if (active) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(Modifier.alpha(0.8f)) {
            Text(label, fontSize = 12.sp)
        }
    }
}
