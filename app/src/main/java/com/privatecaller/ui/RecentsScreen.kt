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
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.privatecaller.data.db.BlockedNumber
import com.privatecaller.domain.CallAggregate
import com.privatecaller.domain.CallFilter
import com.privatecaller.domain.CallLogEntry
import com.privatecaller.domain.CallLogGrouping
import com.privatecaller.domain.CallType
import com.privatecaller.domain.ContactHint
import com.privatecaller.domain.ContactItem
import com.privatecaller.domain.ContactSearch
import com.privatecaller.domain.DateSection
import com.privatecaller.domain.NumberMatch

@Composable
fun RecentsScreen(viewModel: RecentsViewModel, modifier: Modifier = Modifier) {
    val callLog by viewModel.callLog.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val blocked by viewModel.blockedNumbers.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(CallFilter.ALL) }
    val call = rememberCallController()
    val context = LocalContext.current

    val matches = remember(query, contacts) { ContactSearch.filter(contacts, query) }

    val isUserBlocked: (CallLogEntry) -> Boolean = remember(blocked) {
        { e -> e.number != null && blocked.any { NumberMatch.sameNumber(it.normalized, e.number) } }
    }
    // Index contacts by their last-10 digits so we can fill in the caller name /
    // photo when the call log didn't cache one (matches NumberMatch's tolerance).
    val contactIndex = remember(contacts) {
        val map = HashMap<String, ContactItem>()
        contacts.forEach { c ->
            val key = NumberMatch.normalize(c.number).takeLast(10)
            if (key.length >= 7) map.putIfAbsent(key, c)
        }
        map
    }
    val resolveContact: (CallLogEntry) -> ContactHint? = remember(contactIndex) {
        { e ->
            val key = NumberMatch.normalize(e.number).takeLast(10)
            contactIndex[key]?.let { ContactHint(it.name, it.photoUri) }
        }
    }
    val sections = remember(callLog, blocked, filter, contactIndex) {
        CallLogGrouping.build(callLog, filter, isUserBlocked, resolveContact = resolveContact)
    }

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
            FilterChips(filter) { filter = it }
            SetupBanner()
            when {
                callLog.isEmpty() -> EmptyState()
                sections.isEmpty() -> NoFilterResults(filter)
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    sections.forEach { sec ->
                        item(key = "hdr-${sec.section}", contentType = "header") {
                            SectionHeader(sec.section)
                        }
                        items(sec.items, key = { it.id }, contentType = { "agg" }) { agg ->
                            AggregateRow(
                                agg = agg,
                                isUserBlocked = isUserBlocked,
                                onOpen = { openCallHistory(context, agg.number ?: "", agg.name, agg.photoUri) },
                                onCall = { call(agg.number ?: "") },
                                onBlock = { agg.number?.let { viewModel.block(it, agg.name) } },
                                onUnblock = { agg.number?.let { viewModel.unblock(it) } },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChips(selected: CallFilter, onSelect: (CallFilter) -> Unit) {
    val chips = listOf(
        CallFilter.ALL to "All",
        CallFilter.MISSED to "Missed",
        CallFilter.CONTACTS to "Contacts",
        CallFilter.BLOCKED to "Blocked",
        CallFilter.AUTOBLOCKED to "Autoblocked",
    )
    LazyRow(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(chips, key = { it.first.name }) { (f, label) ->
            FilterChip(selected = selected == f, onClick = { onSelect(f) }, label = { Text(label) })
        }
    }
}

@Composable
private fun SectionHeader(section: DateSection) {
    val label = when (section) {
        DateSection.TODAY -> "Today"
        DateSection.YESTERDAY -> "Yesterday"
        DateSection.OLDER -> "Older"
    }
    Text(
        label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
    )
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
private fun AggregateRow(
    agg: CallAggregate,
    isUserBlocked: (CallLogEntry) -> Boolean,
    onOpen: () -> Unit,
    onCall: () -> Unit,
    onBlock: () -> Unit,
    onUnblock: () -> Unit,
) {
    val entry = agg.latest
    val blockedUser = remember(entry, agg) { isUserBlocked(entry) }
    val statusLabel = statusLabelFor(entry, isUserBlocked)
    val (_, typeIcon, typeTint) = callTypeStyle(entry.type)

    val baseTitle = agg.name?.takeIf { it.isNotBlank() } ?: entry.displayNumber
    val title = if (agg.count > 1) "$baseTitle (${agg.count})" else baseTitle

    val relative = remember(entry.date) {
        DateUtils.getRelativeTimeSpanString(
            entry.date, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
        ).toString()
    }
    val supporting = remember(entry.id, statusLabel, relative) {
        listOfNotNull(
            statusLabel,
            entry.displayDuration.takeIf { it.isNotEmpty() },
            relative,
        ).joinToString(" · ")
    }

    var menuOpen by remember { mutableStateOf(false) }

    Box {
        ListItem(
            modifier = Modifier.combinedClickable(
                onClick = onOpen,
                onLongClick = { menuOpen = true },
            ),
            leadingContent = { Avatar(agg.name, agg.number, photoUri = agg.photoUri) },
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
            if (blockedUser) {
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

internal val CallGreen = Color(0xFF2E7D32)
internal val CallRed = Color(0xFFC62828)
internal val CallGrey = Color(0xFF757575)

/** Label + direction icon + tint for a call-log entry's raw type. */
internal fun callTypeStyle(type: CallType): Triple<String, ImageVector, Color> = when (type) {
    CallType.INCOMING -> Triple("Incoming", Icons.AutoMirrored.Filled.CallReceived, CallGreen)
    CallType.OUTGOING -> Triple("Outgoing", Icons.AutoMirrored.Filled.CallMade, CallGreen)
    CallType.MISSED -> Triple("Missed", Icons.AutoMirrored.Filled.CallMissed, CallRed)
    CallType.REJECTED -> Triple("Rejected", Icons.Filled.Block, CallRed)
    CallType.BLOCKED -> Triple("Blocked", Icons.Filled.Block, CallRed)
    CallType.VOICEMAIL -> Triple("Voicemail", Icons.Filled.Voicemail, CallGrey)
    CallType.OTHER -> Triple("Call", Icons.Filled.Call, CallGrey)
}

/** User-facing status: "Blocked" (manual), "Autoblock" (screening), else the direction. */
internal fun statusLabelFor(entry: CallLogEntry, isUserBlocked: (CallLogEntry) -> Boolean): String = when {
    isUserBlocked(entry) -> "Blocked"
    CallLogGrouping.isAutoBlocked(entry, isUserBlocked) -> "Autoblock"
    else -> callTypeStyle(entry.type).first
}

@Composable
private fun NoFilterResults(filter: CallFilter) {
    val what = when (filter) {
        CallFilter.ALL -> "calls"
        CallFilter.MISSED -> "missed calls"
        CallFilter.CONTACTS -> "calls from contacts"
        CallFilter.BLOCKED -> "blocked calls"
        CallFilter.AUTOBLOCKED -> "auto-blocked calls"
    }
    Column(
        Modifier.fillMaxWidth().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No $what", style = MaterialTheme.typography.bodyLarge)
    }
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
