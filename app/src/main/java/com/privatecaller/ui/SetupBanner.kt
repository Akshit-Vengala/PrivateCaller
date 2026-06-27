package com.privatecaller.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import com.privatecaller.edition.Edition

/**
 * Shows actionable cards for the two pieces of setup PrivateCaller needs:
 * being the default call-screening app, and notification-listener access.
 * Re-checks status whenever the screen resumes (after returning from settings).
 */
@Composable
fun SetupBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var dialerHeld by remember { mutableStateOf(Setup.isDialerRoleHeld(context)) }
    var roleHeld by remember { mutableStateOf(Setup.isScreeningRoleHeld(context)) }
    // Notification access only matters for SmartUnblock (the "full" edition);
    // the playstore Edition reports it granted so this card never appears.
    var notifAccess by remember { mutableStateOf(Edition.isNotificationAccessGranted(context)) }

    // Roles MUST be requested for a result, otherwise the system dialog
    // silently never appears.
    val dialerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        dialerHeld = Setup.isDialerRoleHeld(context)
        // Becoming default phone app usually also grants screening implicitly.
        roleHeld = Setup.isScreeningRoleHeld(context)
    }
    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        roleHeld = Setup.isScreeningRoleHeld(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                dialerHeld = Setup.isDialerRoleHeld(context)
                roleHeld = Setup.isScreeningRoleHeld(context)
                notifAccess = Edition.isNotificationAccessGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (dialerHeld && roleHeld && notifAccess) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!dialerHeld) {
            SetupCard(
                title = "Set PrivateCaller as your phone app",
                body = "Lets you place and receive calls inside PrivateCaller with its own dialer and in-call screen.",
                action = "Set up",
            ) {
                val intent = Setup.dialerRoleIntent(context)
                if (intent != null) {
                    dialerLauncher.launch(intent)
                } else {
                    Toast.makeText(
                        context,
                        "Choose PrivateCaller under \"Phone app\" / \"Default apps\"",
                        Toast.LENGTH_LONG,
                    ).show()
                    runCatching { context.startActivity(Setup.defaultAppsSettingsIntent()) }
                }
            }
        }
        if (!roleHeld) {
            SetupCard(
                title = "Set PrivateCaller as your screening app",
                body = "Required so PrivateCaller can block calls from unknown numbers.",
                action = "Set up",
            ) {
                val intent = Setup.screeningRoleIntent(context)
                if (intent != null) {
                    roleLauncher.launch(intent)
                } else {
                    // Role unavailable on this device — let the user pick manually.
                    Toast.makeText(
                        context,
                        "Choose PrivateCaller under \"Caller ID & spam app\"",
                        Toast.LENGTH_LONG,
                    ).show()
                    runCatching { context.startActivity(Setup.defaultAppsSettingsIntent()) }
                }
            }
        }
        if (!notifAccess) {
            SetupCard(
                title = "Allow notification access",
                body = "Lets SmartUnblock detect delivery & ride notifications and open call windows.",
                action = "Grant",
            ) { Edition.openNotificationAccessSettings(context) }
        }
    }
}

@Composable
private fun SetupCard(title: String, body: String, action: String, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onClick) { Text(action) }
        }
    }
}
