package com.privatecaller.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.privatecaller.PrivateCallerApp
import com.privatecaller.domain.CallLogEntry
import com.privatecaller.domain.ContactHint
import com.privatecaller.domain.NumberMatch
import com.privatecaller.domain.PhoneFormat
import com.privatecaller.ui.theme.PrivateCallerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Opens the per-number call history screen. */
fun openCallHistory(context: Context, number: String, name: String?, photoUri: String?) {
    context.startActivity(
        Intent(context, CallHistoryActivity::class.java).apply {
            putExtra(CallHistoryActivity.EXTRA_NUMBER, number)
            putExtra(CallHistoryActivity.EXTRA_NAME, name)
            putExtra(CallHistoryActivity.EXTRA_PHOTO_URI, photoUri)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    )
}

class CallHistoryActivity : ComponentActivity() {

    companion object {
        const val EXTRA_NUMBER = "number"
        const val EXTRA_NAME = "name"
        const val EXTRA_PHOTO_URI = "photo"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val number = intent.getStringExtra(EXTRA_NUMBER).orEmpty()
        val name = intent.getStringExtra(EXTRA_NAME)
        val photo = intent.getStringExtra(EXTRA_PHOTO_URI)

        setContent {
            PrivateCallerTheme {
                CallHistoryScreen(
                    number = number,
                    initialName = name,
                    initialPhoto = photo,
                    onBack = { finish() },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CallHistoryScreen(
    number: String,
    initialName: String?,
    initialPhoto: String?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val container = remember { PrivateCallerApp.container(context) }
    val call = rememberCallController()

    val callLog by container.callLogRepository.observe()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val blocked by container.blockList.observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val entries = remember(callLog, number) {
        callLog.filter { it.number != null && NumberMatch.sameNumber(it.number, number) }
    }
    // Fallback to the device contacts when neither the caller nor the call log
    // gave us a name/photo (e.g. opened from a blocked-call notification).
    var resolved by remember { mutableStateOf<ContactHint?>(null) }
    LaunchedEffect(number) {
        if (number.isNotBlank()) {
            val key = NumberMatch.normalize(number).takeLast(10)
            if (key.length >= 7) {
                val all = withContext(Dispatchers.IO) { container.contactsRepository.loadAll() }
                resolved = all.firstOrNull { NumberMatch.normalize(it.number).takeLast(10) == key }
                    ?.let { ContactHint(it.name, it.photoUri) }
            }
        }
    }
    val name = initialName?.takeIf { it.isNotBlank() }
        ?: entries.firstNotNullOfOrNull { it.cachedName?.takeIf { n -> n.isNotBlank() } }
        ?: resolved?.name
    val photo = initialPhoto?.takeIf { it.isNotBlank() }
        ?: entries.firstNotNullOfOrNull { it.cachedPhotoUri?.takeIf { p -> p.isNotBlank() } }
        ?: resolved?.photoUri

    val isUserBlocked: (CallLogEntry) -> Boolean = remember(blocked) {
        { e -> e.number != null && blocked.any { NumberMatch.sameNumber(it.normalized, e.number) } }
    }
    // Resolve each distinct SIM account id to a label once (avoids per-row binder calls).
    val simLabels = remember(entries) {
        entries.mapNotNull { it.phoneAccountId }.distinct()
            .associateWith { container.simManager.labelForAccountId(it) }
    }

    val prettyNumber = remember(number) { PhoneFormat.pretty(context, number) }
    val title = name ?: prettyNumber

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { openContactDetail(context, name, number, photo, -1L) }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Contact settings")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Avatar(name, number, photoUri = photo, sizeDp = 96)
                Spacer(Modifier.size(10.dp))
                Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                if (name != null) {
                    Spacer(Modifier.size(2.dp))
                    Text(prettyNumber, style = MaterialTheme.typography.bodyLarge)
                }
                Spacer(Modifier.size(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                ) {
                    QuickAction(Icons.Filled.Call, "Call") { call(number) }
                    QuickAction(Icons.Filled.Message, "Message") { sendSms(context, number) }
                }
            }
            HorizontalDivider()

            entries.forEach { entry ->
                HistoryRow(entry, isUserBlocked, simLabels[entry.phoneAccountId])
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun HistoryRow(
    entry: CallLogEntry,
    isUserBlocked: (CallLogEntry) -> Boolean,
    simLabel: String?,
) {
    val context = LocalContext.current
    val (_, icon, tint) = callTypeStyle(entry.type)
    val status = statusLabelFor(entry, isUserBlocked)
    val time = remember(entry.date) {
        DateUtils.formatDateTime(
            context,
            entry.date,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
        )
    }
    val supporting = remember(entry.id, simLabel, time) {
        listOfNotNull(
            time,
            simLabel,
            entry.displayDuration.takeIf { it.isNotEmpty() },
        ).joinToString(" · ")
    }
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null, tint = tint) },
        headlineContent = { Text(status) },
        supportingContent = { Text(supporting) },
    )
}

@Composable
private fun QuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary)
        }
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

private fun sendSms(context: Context, number: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")))
    }
}
