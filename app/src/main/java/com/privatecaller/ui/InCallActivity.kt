package com.privatecaller.ui

import android.os.Build
import android.os.Bundle
import android.telecom.Call
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.privatecaller.PrivateCallerApp
import com.privatecaller.R
import com.privatecaller.domain.CallManager
import com.privatecaller.domain.PhoneFormat
import com.privatecaller.ui.theme.PrivateCallerTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Full-screen in-call UI shown while PrivateCaller is the default phone app.
 * Renders incoming/outgoing/active states and the call controls.
 */
class InCallActivity : ComponentActivity() {

    private var proximityLock: android.os.PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over the lock screen and wake the display for incoming calls.
        // setShowWhenLocked/setTurnScreenOn are the modern APIs; the matching
        // window flags are added too as a fallback for OEMs that don't honour
        // them, and KEEP_SCREEN_ON stops the display dozing off while ringing.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(
            android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        // Launched from the heads-up banner's Answer button -> answer at once.
        maybeAnswer(intent)

        // Proximity blanking: while the phone is held to the ear during a call,
        // PROXIMITY_SCREEN_OFF_WAKE_LOCK turns the display off AND ignores touch
        // input, preventing accidental cheek/ear taps. We manage it at the
        // Activity level (not in Compose) and only hold it while this screen is
        // in the foreground (RESUMED) — releasing in onPause — so the screen is
        // never blanked once the user leaves the call UI for another app.
        val pm = getSystemService(android.os.PowerManager::class.java)
        if (pm != null && pm.isWakeLockLevelSupported(
                android.os.PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK
            )
        ) {
            proximityLock = pm.newWakeLock(
                android.os.PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                "PrivateCaller:incall",
            )
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                CallManager.state.collect { st ->
                    val atEar = st == Call.STATE_ACTIVE ||
                        st == Call.STATE_DIALING || st == Call.STATE_CONNECTING
                    if (atEar) acquireProximity() else releaseProximity()
                    // Force the screen on only while ringing; once the call is
                    // answered, drop the flag so proximity blanking can work.
                    if (st == Call.STATE_RINGING) {
                        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
            }
        }

        setContent {
            PrivateCallerTheme {
                InCallScreen(onFinished = { finish() })
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // singleTop: Answer may arrive while the screen is already showing.
        maybeAnswer(intent)
    }

    private fun maybeAnswer(intent: android.content.Intent?) {
        if (intent?.getBooleanExtra(EXTRA_ANSWER, false) == true) {
            intent.removeExtra(EXTRA_ANSWER)
            CallManager.answer()
        }
    }

    override fun onPause() {
        super.onPause()
        // Never keep the screen blanked once we're no longer in front.
        releaseProximity()
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseProximity()
    }

    private fun acquireProximity() {
        proximityLock?.let { if (!it.isHeld) it.acquire() }
    }

    private fun releaseProximity() {
        proximityLock?.let { if (it.isHeld) it.release() }
    }

    companion object {
        /** Boolean extra: answer the ringing call as soon as the screen opens. */
        const val EXTRA_ANSWER = "com.privatecaller.extra.ANSWER"
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
    val canHold by CallManager.canHold.collectAsStateWithLifecycle()

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
    val prettyNumber = androidx.compose.runtime.remember(number) {
        number?.let { PhoneFormat.pretty(context, it) }
    }
    val title = displayName ?: prettyNumber ?: "Unknown"
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

    // "Outgoing call from SIM 1" / "Incoming call to SIM 1" line under the name.
    val routeLine = androidx.compose.runtime.remember(call) {
        val details = call?.details ?: return@remember null
        val slot = details.accountHandle?.let {
            PrivateCallerApp.container(context).simManager.slotForHandle(it)
        }
        val simName = slot?.let { "SIM ${it + 1}" }
        when (details.callDirection) {
            Call.Details.DIRECTION_OUTGOING ->
                simName?.let { context.getString(R.string.incall_outgoing_from, it) }
                    ?: context.getString(R.string.incall_outgoing)
            Call.Details.DIRECTION_INCOMING ->
                simName?.let { context.getString(R.string.incall_incoming_to, it) }
                    ?: context.getString(R.string.incall_incoming)
            else -> null
        }
    }
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
            if (displayName != null && prettyNumber != null) {
                Spacer(Modifier.size(4.dp))
                Text(prettyNumber, style = MaterialTheme.typography.bodyLarge)
            }
            if (routeLine != null) {
                Spacer(Modifier.size(8.dp))
                Text(
                    routeLine,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.alpha(0.7f),
                )
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
                    val controls = buildList {
                        add(
                            CtrlSpec(
                                icon = if (muted) Icons.Filled.MicOff else Icons.Filled.Mic,
                                label = "Mute",
                                active = muted,
                                onClick = { CallManager.toggleMute() },
                            )
                        )
                        add(
                            CtrlSpec(
                                icon = Icons.Filled.Dialpad,
                                label = "Keypad",
                                active = false,
                                onClick = { showKeypad = true },
                            )
                        )
                        if (canHold) {
                            add(
                                CtrlSpec(
                                    icon = if (isHolding) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                    label = if (isHolding) "Resume" else "Hold",
                                    active = isHolding,
                                    onClick = { CallManager.toggleHold() },
                                )
                            )
                        }
                        add(
                            CtrlSpec(
                                icon = Icons.Filled.VolumeUp,
                                label = "Speaker",
                                active = speaker,
                                onClick = { CallManager.toggleSpeaker() },
                            )
                        )
                    }
                    // 2x2 grid of call controls.
                    Column(
                        Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        controls.chunked(2).forEach { rowItems ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(48.dp, Alignment.CenterHorizontally),
                            ) {
                                rowItems.forEach { c ->
                                    ControlButton(
                                        icon = c.icon,
                                        label = c.label,
                                        active = c.active,
                                        onClick = c.onClick,
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.size(32.dp))
            }

            // Bottom action area: slide-to-answer while ringing, otherwise a
            // single end-call button.
            if (isRinging) {
                Text(
                    "Slide to answer · left to decline",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.alpha(0.7f),
                )
                Spacer(Modifier.size(20.dp))
                SlideToAnswer(
                    onAnswer = { CallManager.answer() },
                    onReject = { CallManager.reject() },
                )
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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

/** Spec for a single in-call control rendered in the 2x2 grid. */
private class CtrlSpec(
    val icon: ImageVector,
    val label: String,
    val active: Boolean,
    val onClick: () -> Unit,
)

/**
 * Google-Phone-style answer control. A ringing thumb sits in the middle of a
 * pill track; drag it right past the threshold to answer, left to decline.
 * Releasing before the threshold springs the thumb back to centre.
 */
@Composable
private fun SlideToAnswer(
    onAnswer: () -> Unit,
    onReject: () -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    var committed by remember { mutableStateOf(false) }

    val thumbSize = 76.dp
    val trackHeight = 92.dp
    val edgePadding = 8.dp

    val neutral = MaterialTheme.colorScheme.primary
    val answer = Color(0xFF2E7D32)
    val decline = Color(0xFFC62828)

    // Gentle pulse on the idle thumb to hint that it's interactive.
    val pulse = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(trackHeight)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center,
    ) {
        val trackWidthPx = with(density) { maxWidth.toPx() }
        val thumbPx = with(density) { thumbSize.toPx() }
        val edgePx = with(density) { edgePadding.toPx() }
        val maxOffset = ((trackWidthPx - thumbPx) / 2f - edgePx).coerceAtLeast(1f)
        val threshold = maxOffset * 0.7f

        val fraction = (offsetX.value / maxOffset).coerceIn(-1f, 1f)
        val thumbColor = when {
            fraction > 0f -> lerp(neutral, answer, fraction)
            fraction < 0f -> lerp(neutral, decline, -fraction)
            else -> neutral
        }

        // Direction hints that fade as the thumb approaches each edge.
        Row(
            Modifier.fillMaxSize().padding(horizontal = 28.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.KeyboardArrowLeft,
                contentDescription = null,
                tint = decline,
                modifier = Modifier.size(36.dp).alpha((1f - fraction.coerceAtLeast(0f)) * 0.9f),
            )
            Icon(
                Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = answer,
                modifier = Modifier.size(36.dp).alpha((1f + fraction.coerceAtMost(0f)) * 0.9f),
            )
        }

        Surface(
            shape = CircleShape,
            color = thumbColor,
            modifier = Modifier
                .size(thumbSize)
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .scale(if (offsetX.value == 0f) pulseScale else 1f)
                .pointerInput(maxOffset) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (committed) return@detectHorizontalDragGestures
                            scope.launch {
                                when {
                                    offsetX.value >= threshold -> {
                                        committed = true
                                        offsetX.animateTo(maxOffset)
                                        onAnswer()
                                    }
                                    offsetX.value <= -threshold -> {
                                        committed = true
                                        offsetX.animateTo(-maxOffset)
                                        onReject()
                                    }
                                    else -> offsetX.animateTo(
                                        0f,
                                        spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                    )
                                }
                            }
                        },
                    ) { change, dragAmount ->
                        if (committed) return@detectHorizontalDragGestures
                        change.consume()
                        scope.launch {
                            offsetX.snapTo(
                                (offsetX.value + dragAmount).coerceIn(-maxOffset, maxOffset),
                            )
                        }
                    }
                },
        ) {
            Icon(
                Icons.Filled.Call,
                contentDescription = "Slide to answer",
                tint = Color.White,
                modifier = Modifier.fillMaxSize().padding(22.dp),
            )
        }
    }
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
            modifier = Modifier.size(84.dp),
        ) {
            androidx.compose.material3.IconButton(onClick = onClick, modifier = Modifier.fillMaxSize()) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = if (active) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(36.dp),
                )
            }
        }
        Spacer(Modifier.size(6.dp))
        Box(Modifier.alpha(0.8f)) {
            Text(label, fontSize = 13.sp)
        }
    }
}
