package com.privatecaller.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.privatecaller.domain.PhoneFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val sims by viewModel.sims.collectAsStateWithLifecycle()
    val blocked by viewModel.blockedNumbers.collectAsStateWithLifecycle()
    val simPrefs by viewModel.simPrefs.collectAsStateWithLifecycle()
    var showAddBlock by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Settings") }) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SwitchRow(
                title = "Call screening",
                subtitle = "Block calls from numbers not in your contacts",
                checked = settings.screeningEnabled,
                onChange = viewModel::setScreening,
            )

            if (sims.isNotEmpty()) {
                Text(
                    "Screen these SIMs",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                sims.forEach { sim ->
                    // null = all SIMs screened, so absence means "on".
                    val screened = settings.screeningSimSlots?.contains(sim.slotIndex) ?: true
                    SwitchRow(
                        title = sim.label,
                        subtitle = "Block unknown calls on this SIM",
                        checked = screened && settings.screeningEnabled,
                        enabled = settings.screeningEnabled,
                        onChange = { viewModel.setSimScreening(sim.slotIndex, it) },
                    )
                }
            }
            HorizontalDivider()
            SwitchRow(
                title = "SmartUnblock",
                subtitle = "Auto-allow unknown calls when delivery/ride apps notify you",
                checked = settings.smartUnblockEnabled,
                onChange = viewModel::setSmartUnblock,
            )
            HorizontalDivider()
            SwitchRow(
                title = "Log blocked calls",
                subtitle = "Show blocked numbers in Recents",
                checked = settings.logBlockedCalls,
                onChange = viewModel::setLogBlocked,
            )
            HorizontalDivider()

            Text("Block message", style = MaterialTheme.typography.titleMedium)
            BlockMessageField(settings.blockMessage, viewModel::setBlockMessage)

            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Blocked numbers",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { showAddBlock = true }) { Text("Add") }
            }
            if (blocked.isEmpty()) {
                Text(
                    "No blocked numbers. Long-press a call in Recents to block it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                blocked.forEach { b ->
                    val pretty = PhoneFormat.pretty(context, b.rawNumber)
                    ListItem(
                        headlineContent = {
                            Text(b.label ?: pretty, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = if (b.label != null) {
                            { Text(pretty) }
                        } else null,
                        trailingContent = {
                            IconButton(onClick = { viewModel.unblock(b.normalized) }) {
                                Icon(Icons.Filled.Close, contentDescription = "Unblock")
                            }
                        },
                    )
                }
            }

            if (sims.size >= 2 && simPrefs.isNotEmpty()) {
                HorizontalDivider()
                Text("Outgoing SIM per number", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Set while calling via \"Always use for this number\". Remove to be asked again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                simPrefs.forEach { pref ->
                    ListItem(
                        headlineContent = {
                            Text(
                                PhoneFormat.pretty(context, pref.rawNumber),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = { Text(viewModel.simLabel(pref.simSlot)) },
                        trailingContent = {
                            IconButton(onClick = { viewModel.clearSimPref(pref.rawNumber) }) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove")
                            }
                        },
                    )
                }
            }

            HorizontalDivider()
            UpdateSection()
        }
    }

    if (showAddBlock) {
        AddBlockDialog(
            onDismiss = { showAddBlock = false },
            onConfirm = { number ->
                if (number.isNotBlank()) viewModel.blockNumber(number)
                showAddBlock = false
            },
        )
    }
}

@Composable
private fun AddBlockDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var number by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Block a number") },
        text = {
            OutlinedTextField(
                value = number,
                onValueChange = { number = it },
                placeholder = { Text("Phone number") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(number) }) { Text("Block") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun BlockMessageField(value: String, onCommit: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    LaunchedEffect(value) { text = value }

    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onCommit(it)
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        supportingText = { Text("Reported to the caller / system when a number is rejected.") },
    )
}
