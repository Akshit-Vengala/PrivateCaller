package com.privatecaller

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.privatecaller.edition.Edition
import com.privatecaller.ui.DialerScreen
import com.privatecaller.ui.RecentsScreen
import com.privatecaller.ui.SettingsScreen
import com.privatecaller.ui.ViewModelFactory
import com.privatecaller.ui.theme.PrivateCallerTheme

/** A bottom-navigation destination. Editions can contribute extra tabs. */
class NavTab(
    val label: String,
    val icon: ImageVector,
    val content: @Composable (Modifier) -> Unit,
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val factory = ViewModelFactory(PrivateCallerApp.container(this))
        setContent {
            PrivateCallerTheme {
                AppRoot(factory)
            }
        }
    }
}

@Composable
private fun AppRoot(factory: ViewModelFactory) {
    // Base tabs + any edition-specific tabs (SmartUnblock in the "full" edition).
    val tabs = remember(factory) {
        buildList {
            add(NavTab("Recents", Icons.Filled.History) { RecentsScreen(viewModel(factory = factory), it) })
            add(NavTab("Dialer", Icons.Filled.Dialpad) { DialerScreen(viewModel(factory = factory), it) })
            addAll(Edition.extraTabs())
            add(NavTab("Settings", Icons.Filled.Settings) { SettingsScreen(viewModel(factory = factory), it) })
        }
    }
    var selected by remember { mutableIntStateOf(0) }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* result handled by screens re-reading state */ }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        val perms = buildList {
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.CALL_PHONE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
        permissionLauncher.launch(perms)
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        }
    ) { padding ->
        tabs[selected].content(Modifier.padding(padding))
    }
}
