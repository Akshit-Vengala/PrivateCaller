package com.privatecaller.ui

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.privatecaller.data.db.BlockedNumber
import com.privatecaller.domain.CallLogEntry
import com.privatecaller.domain.CallType
import com.privatecaller.domain.ContactItem
import com.privatecaller.domain.ContactSearch
import com.privatecaller.domain.NumberMatch

@Composable
fun RecentsScreen(viewModel: RecentsViewModel, modifier: Modifier = Modifier) {
    val callLog by viewModel.callLog.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val blocked by viewModel.blockedNumbers.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val call = rememberCallController()

    val matches = remember(query, contacts) { ContactSearch.filter(contacts, query) }

    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            placeholder = { Text("Search contacts") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(),
        )

        if (query.isNotEmpty()) {
            ContactResults(matches) { call(it.number) }
        } else {
            SetupBanner()
            if (callLog.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(callLog, key = { it.id }, contentType = { "callRow" }) { entry ->
                        CallRow(
                            entry = entry,
                            blocked = blocked,
                            onCall = { call(entry.number ?: "") },
                            onBlock = { entry.number?.let { viewModel.block(it, entry.cachedName) } },
                            onUnblock = { entry.number?.let { viewModel.unblock(it) } },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactResults(matches: List<ContactItem>, onCall: (ContactItem) -> Unit) {
    val context = LocalContext.current
    if (matches.isEmpty()) {
        Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No matching contacts", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(
            matches,
            key = { "${it.contactId}|${it.number}" },
            contentType = { "contactRow" },
        ) { contact ->
            ListItem(
                modifier = Modifier.clickable {
                    openContactDetail(context, contact.name, contact.number, contact.photoUri, contact.contactId)
                },
                leadingContent = { Avatar(contact.name, contact.number, photoUri = contact.photoUri) },
                headlineContent = {
                    Text(contact.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                supportingContent = { Text(contact.displayNumber) },
                trailingContent = {
                    IconButton(onClick = { onCall(contact) }) {
                        Icon(
                            Icons.Filled.Call,
                            contentDescription = "Call ${contact.name}",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
            HorizontalDivider()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CallRow(
    entry: CallLogEntry,
    blocked: List<BlockedNumber>,
    onCall: () -> Unit,
    onBlock: () -> Unit,
    onUnblock: () -> Unit,
) {
    val context = LocalContext.current
    val isBlocked = remember(entry.number, blocked) {
        entry.number != null && blocked.any { NumberMatch.sameNumber(it.normalized, entry.number) }
    }
    val title = entry.cachedName?.takeIf { it.isNotBlank() } ?: entry.displayNumber

    val (typeLabel, typeIcon, typeTint) = callTypeStyle(entry.type)
    val relative = remember(entry.date) {
        DateUtils.getRelativeTimeSpanString(
            entry.date, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
        ).toString()
    }
    val supporting = remember(entry.id, isBlocked, typeLabel, relative) {
        listOfNotNull(
            if (isBlocked) "$typeLabel · 🚫 Blocked" else typeLabel,
            entry.displayDuration.takeIf { it.isNotEmpty() },
            relative,
        ).joinToString(" · ")
    }

    var menuOpen by remember { mutableStateOf(false) }

    Box {
        ListItem(
            modifier = Modifier.combinedClickable(
                onClick = {
                    openContactDetail(context, entry.cachedName, entry.number ?: "", entry.cachedPhotoUri, -1L)
                },
                onLongClick = { menuOpen = true },
            ),
            leadingContent = { Avatar(entry.cachedName, entry.number, photoUri = entry.cachedPhotoUri) },
            headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(typeIcon, contentDescription = null, tint = typeTint, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(supporting, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            trailingContent = {
                IconButton(onClick = onCall) {
                    Icon(
                        Icons.Filled.Call,
                        contentDescription = "Call back",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            },
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Call") },
                onClick = { menuOpen = false; onCall() },
            )
            if (isBlocked) {
                DropdownMenuItem(
                    text = { Text("Unblock number") },
                    onClick = { menuOpen = false; onUnblock() },
                )
            } else {
                DropdownMenuItem(
                    text = { Text("Block number") },
                    onClick = { menuOpen = false; onBlock() },
                )
            }
        }
    }
}

private val CallGreen = Color(0xFF2E7D32)
private val CallRed = Color(0xFFC62828)
private val CallGrey = Color(0xFF757575)

/** Label + direction icon + tint for a call-log entry. */
private fun callTypeStyle(type: CallType): Triple<String, ImageVector, Color> = when (type) {
    CallType.INCOMING -> Triple("Incoming", Icons.AutoMirrored.Filled.CallReceived, CallGreen)
    CallType.OUTGOING -> Triple("Outgoing", Icons.AutoMirrored.Filled.CallMade, CallGreen)
    CallType.MISSED -> Triple("Missed", Icons.AutoMirrored.Filled.CallMissed, CallRed)
    CallType.REJECTED -> Triple("Rejected", Icons.Filled.Block, CallRed)
    CallType.BLOCKED -> Triple("Blocked", Icons.Filled.Block, CallRed)
    CallType.VOICEMAIL -> Triple("Voicemail", Icons.Filled.Voicemail, CallGrey)
    CallType.OTHER -> Triple("Call", Icons.Filled.Call, CallGrey)
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("No recent calls", style = MaterialTheme.typography.titleMedium)
        Text(
            "Your call history will appear here once PrivateCaller is your phone app.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
