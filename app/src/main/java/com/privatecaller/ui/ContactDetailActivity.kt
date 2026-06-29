package com.privatecaller.ui

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.ContactsContract.PhoneLookup
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.privatecaller.PrivateCallerApp
import com.privatecaller.domain.NumberMatch
import com.privatecaller.domain.PhoneFormat
import com.privatecaller.ui.theme.PrivateCallerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Opens the contact detail screen for a number (with whatever we already know). */
fun openContactDetail(
    context: Context,
    name: String?,
    number: String,
    photoUri: String?,
    contactId: Long,
) {
    context.startActivity(
        Intent(context, ContactDetailActivity::class.java).apply {
            putExtra(ContactDetailActivity.EXTRA_NAME, name)
            putExtra(ContactDetailActivity.EXTRA_NUMBER, number)
            putExtra(ContactDetailActivity.EXTRA_PHOTO_URI, photoUri)
            putExtra(ContactDetailActivity.EXTRA_CONTACT_ID, contactId)
        }
    )
}

class ContactDetailActivity : ComponentActivity() {

    companion object {
        const val EXTRA_NAME = "name"
        const val EXTRA_NUMBER = "number"
        const val EXTRA_PHOTO_URI = "photo"
        const val EXTRA_CONTACT_ID = "contactId"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val number = intent.getStringExtra(EXTRA_NUMBER).orEmpty()
        val name = intent.getStringExtra(EXTRA_NAME)
        val photo = intent.getStringExtra(EXTRA_PHOTO_URI)
        val contactId = intent.getLongExtra(EXTRA_CONTACT_ID, -1L)

        setContent {
            PrivateCallerTheme {
                ContactDetailScreen(
                    initialName = name,
                    number = number,
                    initialPhoto = photo,
                    initialContactId = contactId,
                    onBack = { finish() },
                )
            }
        }
    }
}

private data class ResolvedContact(val name: String?, val photoUri: String?, val contactId: Long)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactDetailScreen(
    initialName: String?,
    number: String,
    initialPhoto: String?,
    initialContactId: Long,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val container = remember { PrivateCallerApp.container(context) }
    val scope = rememberCoroutineScope()
    val call = rememberCallController()

    var name by remember { mutableStateOf(initialName) }
    var photoUri by remember { mutableStateOf(initialPhoto) }
    var contactId by remember { mutableLongStateOf(initialContactId) }

    // If we only have a number, try to resolve it to a saved contact.
    LaunchedEffect(number) {
        if (contactId <= 0 && number.isNotBlank()) {
            val resolved = withContext(Dispatchers.IO) { lookupContact(context, number) }
            if (resolved != null) {
                if (name.isNullOrBlank()) name = resolved.name
                if (photoUri == null) photoUri = resolved.photoUri
                contactId = resolved.contactId
            }
        }
    }

    val sims = remember { container.simManager.availableSims() }
    val prefs by container.contactSimPref.observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val blocked by container.blockList.observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val currentSlot = remember(prefs, number) {
        prefs.firstOrNull { NumberMatch.sameNumber(it.normalized, number) }?.simSlot
    }
    val hasPref = remember(prefs, number) {
        prefs.any { NumberMatch.sameNumber(it.normalized, number) }
    }
    val isBlocked = remember(blocked, number) {
        blocked.any { NumberMatch.sameNumber(it.normalized, number) }
    }

    val prettyNumber = remember(number) { PhoneFormat.pretty(context, number) }
    val title = name?.takeIf { it.isNotBlank() } ?: prettyNumber
    val isSaved = contactId > 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contact") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Avatar(name, number, photoUri = photoUri, sizeDp = 112)
            Spacer(Modifier.size(12.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            if (name != null) {
                Spacer(Modifier.size(2.dp))
                Text(prettyNumber, style = MaterialTheme.typography.bodyLarge)
            }

            Spacer(Modifier.size(20.dp))

            // Quick actions.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
            ) {
                ActionItem(Icons.Filled.Call, "Call") { call(number) }
                ActionItem(Icons.Filled.Message, "Message") { sendSms(context, number) }
                if (isSaved) {
                    ActionItem(Icons.Filled.Edit, "Edit") { editContact(context, contactId) }
                } else {
                    ActionItem(Icons.Filled.PersonAdd, "Add") { addContact(context, number, name) }
                }
            }

            Spacer(Modifier.size(24.dp))
            HorizontalDivider()

            // Per-contact outgoing SIM.
            if (sims.size >= 2) {
                Spacer(Modifier.size(12.dp))
                Text(
                    "Calling SIM",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
                SimOption("Ask every time", selected = !hasPref || currentSlot == null) {
                    scope.launch { container.contactSimPref.clear(number) }
                }
                sims.forEach { sim ->
                    SimOption(sim.label, selected = hasPref && currentSlot == sim.slotIndex) {
                        scope.launch { container.contactSimPref.setSlot(number, sim.slotIndex) }
                    }
                }
                HorizontalDivider()
            }

            // Block toggle.
            Spacer(Modifier.size(8.dp))
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Block this number", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Calls are rejected even if it's a saved contact.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = isBlocked,
                    onCheckedChange = { checked ->
                        scope.launch {
                            if (checked) container.blockList.block(number, name)
                            else container.blockList.unblock(number)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ActionItem(
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

@Composable
private fun SimOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, Modifier.padding(start = 8.dp))
    }
}

private fun lookupContact(context: Context, number: String): ResolvedContact? {
    val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
    return context.contentResolver.query(
        uri,
        arrayOf(PhoneLookup.CONTACT_ID, PhoneLookup.DISPLAY_NAME, PhoneLookup.PHOTO_URI),
        null, null, null,
    )?.use { c ->
        if (c.moveToFirst()) {
            ResolvedContact(
                name = c.getString(1),
                photoUri = c.getString(2),
                contactId = c.getLong(0),
            )
        } else null
    }
}

private fun editContact(context: Context, contactId: Long) {
    val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
    val intent = Intent(Intent.ACTION_EDIT).apply {
        setDataAndType(uri, ContactsContract.Contacts.CONTENT_ITEM_TYPE)
        putExtra("finishActivityOnSaveCompleted", true)
    }
    runCatching { context.startActivity(intent) }
}

/** Opens the system "new contact" screen with the number prefilled. */
fun addContact(context: Context, number: String, name: String?) {
    val intent = Intent(Intent.ACTION_INSERT).apply {
        type = ContactsContract.Contacts.CONTENT_TYPE
        putExtra(ContactsContract.Intents.Insert.PHONE, number)
        if (!name.isNullOrBlank()) putExtra(ContactsContract.Intents.Insert.NAME, name)
    }
    runCatching { context.startActivity(intent) }
}

/** Opens the system picker to add this number to an existing contact (or a new one). */
fun addToExistingContact(context: Context, number: String) {
    val intent = Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
        type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
        putExtra(ContactsContract.Intents.Insert.PHONE, number)
    }
    runCatching { context.startActivity(intent) }
}

private fun sendSms(context: Context, number: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")))
    }
}
