package com.privatecaller.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.privatecaller.data.db.MonitoredApp
import com.privatecaller.data.db.UnblockWindow
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartUnblockScreen(viewModel: SmartUnblockViewModel, modifier: Modifier = Modifier) {
    val apps by viewModel.monitoredApps.collectAsStateWithLifecycle()
    val active by viewModel.activeWindows.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("SmartUnblock") }) },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ManualUnblockCard(active, viewModel) }
            item {
                Text(
                    "Monitored apps",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(apps, key = { it.packageName }) { app ->
                MonitoredAppRow(app, viewModel)
            }
        }
    }
}

@Composable
private fun ManualUnblockCard(active: List<UnblockWindow>, viewModel: SmartUnblockViewModel) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (active.isNotEmpty())
                MaterialTheme.colorScheme.tertiaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (active.isNotEmpty()) {
                val w = active.first()
                val ends = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(w.expiresAt))
                Text("Unblocked until $ends", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Triggered by ${w.triggerLabel}. Unknown numbers can reach you.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = viewModel::cancelAll) { Text("End now") }
            } else {
                Text("Everyone blocked except contacts", style = MaterialTheme.typography.titleMedium)
                Text("Open a manual window to allow unknown callers:", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 60).forEach { mins ->
                        AssistChip(
                            onClick = { viewModel.openManualWindow(mins) },
                            label = { Text("${mins}m") },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonitoredAppRow(app: MonitoredApp, viewModel: SmartUnblockViewModel) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(app.appLabel, style = MaterialTheme.typography.titleMedium)
                    Text(
                        app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = app.enabled,
                    onCheckedChange = { viewModel.setEnabled(app, it) },
                )
                IconButton(onClick = { viewModel.removeApp(app) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove")
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Window:", style = MaterialTheme.typography.bodyMedium)
                listOf(15, 30, 60).forEach { mins ->
                    val selected = app.unblockMinutes == mins
                    AssistChip(
                        onClick = { viewModel.setDuration(app, mins) },
                        label = { Text("${mins}m") },
                        colors = if (selected)
                            androidx.compose.material3.AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                labelColor = MaterialTheme.colorScheme.onPrimary,
                            )
                        else androidx.compose.material3.AssistChipDefaults.assistChipColors(),
                    )
                }
            }
        }
    }
}
