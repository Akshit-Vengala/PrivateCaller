package com.privatecaller.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.privatecaller.PrivateCallerApp
import com.privatecaller.domain.SimSlot
import kotlinx.coroutines.launch

/**
 * Returns a `call(number)` function that respects each number's outgoing-SIM
 * preference: places directly on the saved SIM, asks (with an optional "always"
 * choice) when there are 2+ SIMs and no preference, or just dials on single-SIM
 * devices. Renders the SIM picker dialog as part of the host composable.
 */
@Composable
fun rememberCallController(): (String) -> Unit {
    val context = LocalContext.current
    val container = remember { PrivateCallerApp.container(context) }
    val scope = rememberCoroutineScope()
    val sims = remember { container.simManager.availableSims() }

    var pickerNumber by remember { mutableStateOf<String?>(null) }

    fun place(number: String, slot: Int?) {
        val handle = slot?.let { container.simManager.phoneAccountHandleForSlot(it) }
        placeCallOrDial(context, number, handle)
    }

    fun call(number: String) {
        if (number.isBlank()) return
        scope.launch {
            val pref = container.contactSimPref.prefFor(number)
            when {
                pref?.simSlot != null -> place(number, pref.simSlot)
                sims.size >= 2 -> pickerNumber = number
                else -> place(number, null)
            }
        }
    }

    pickerNumber?.let { number ->
        SimPickerDialog(
            sims = sims,
            onDismiss = { pickerNumber = null },
            onPick = { slot, remember ->
                if (remember) scope.launch { container.contactSimPref.setSlot(number, slot) }
                pickerNumber = null
                place(number, slot)
            },
        )
    }

    return ::call
}

@Composable
private fun SimPickerDialog(
    sims: List<SimSlot>,
    onDismiss: () -> Unit,
    onPick: (slot: Int, remember: Boolean) -> Unit,
) {
    var selected by remember { mutableStateOf(sims.firstOrNull()?.slotIndex ?: 0) }
    var rememberChoice by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Call with") },
        text = {
            Column {
                sims.forEach { sim ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected == sim.slotIndex,
                                onClick = { selected = sim.slotIndex },
                            )
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected == sim.slotIndex,
                            onClick = { selected = sim.slotIndex },
                        )
                        Text(sim.label, Modifier.padding(start = 8.dp))
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = rememberChoice,
                            onClick = { rememberChoice = !rememberChoice },
                        )
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = rememberChoice, onCheckedChange = { rememberChoice = it })
                    Text("Always use for this number", Modifier.padding(start = 8.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(selected, rememberChoice) }) { Text("Call") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
