package com.privatecaller.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonAddAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.privatecaller.domain.ContactItem
import com.privatecaller.domain.ContactSearch
import com.privatecaller.domain.NumberMatch

private val KEYS = listOf(
    "1" to "", "2" to "ABC", "3" to "DEF",
    "4" to "GHI", "5" to "JKL", "6" to "MNO",
    "7" to "PQRS", "8" to "TUV", "9" to "WXYZ",
    "*" to "", "0" to "+", "#" to "",
)

@Composable
fun DialerScreen(viewModel: DialerViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    var number by remember { mutableStateOf("") }

    // Suggested contacts: everything when empty, T9/text-filtered as you type.
    val suggestions = remember(number, contacts) {
        if (number.isBlank()) contacts else ContactSearch.filter(contacts, number)
    }

    // Whether the typed number already belongs to a saved contact. When it
    // doesn't, we offer "Create new contact" / "Add to a contact" (like Google).
    val isKnownNumber = remember(number, contacts) {
        contacts.any { NumberMatch.sameNumber(it.number, number) }
    }
    // Only once enough digits are typed to be a real number, and it's not saved.
    val showAddContact = number.count { it.isDigit() } >= 3 && !isKnownNumber

    // Handles per-number SIM preference + picker.
    val call = rememberCallController()

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Scrollable suggested-contacts area (the top ~40% of the dialer).
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(
                suggestions,
                key = { "${it.contactId}|${it.number}" },
                contentType = { "suggestion" },
            ) { contact ->
                SuggestionRow(contact) { call(contact.number) }
                HorizontalDivider()
            }
            if (showAddContact) {
                item(key = "create_new", contentType = "addAction") {
                    AddContactRow(Icons.Filled.PersonAdd, "Create new contact") {
                        addContact(context, number, null)
                    }
                    HorizontalDivider()
                }
                item(key = "add_existing", contentType = "addAction") {
                    AddContactRow(Icons.Filled.PersonAddAlt, "Add to a contact") {
                        addToExistingContact(context, number)
                    }
                    HorizontalDivider()
                }
            }
        }

        // Typed number.
        Text(
            text = number,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            textAlign = TextAlign.Center,
        )

        // Keypad.
        KEYS.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { (digit, letters) ->
                    DialKey(digit, letters, Modifier.weight(1f)) { number += digit }
                }
            }
        }

        // Call + backspace.
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp),
            ) {
                IconButton(onClick = { call(number) }) {
                    Icon(
                        Icons.Filled.Call,
                        contentDescription = "Call",
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (number.isNotEmpty()) {
                    IconButton(onClick = { number = number.dropLast(1) }) {
                        Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Delete")
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(contact: ContactItem, onCall: () -> Unit) {
    val context = LocalContext.current
    ListItem(
        modifier = Modifier.clickable {
            openContactDetail(context, contact.name, contact.number, contact.photoUri, contact.contactId)
        },
        leadingContent = { Avatar(contact.name, contact.number, photoUri = contact.photoUri, sizeDp = 40) },
        headlineContent = {
            Text(contact.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(contact.displayNumber, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = {
            IconButton(onClick = onCall) {
                Icon(
                    Icons.Filled.Call,
                    contentDescription = "Call ${contact.name}",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
}

@Composable
private fun AddContactRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        },
        headlineContent = { Text(label) },
    )
}

@Composable
private fun DialKey(digit: String, letters: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .aspectRatio(1.6f)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onClick, modifier = Modifier.fillMaxSize()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(digit, fontSize = 26.sp, style = MaterialTheme.typography.headlineSmall)
                if (letters.isNotEmpty()) {
                    Text(letters, fontSize = 9.sp, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
