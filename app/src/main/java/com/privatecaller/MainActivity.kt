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
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.privatecaller.ui.DialerScreen
import com.privatecaller.ui.RecentsScreen
import com.privatecaller.ui.SettingsScreen
import com.privatecaller.ui.SmartUnblockScreen
import com.privatecaller.ui.ViewModelFactory
import com.privatecaller.ui.theme.PrivateCallerTheme

private enum class Tab(val label: String, val icon: ImageVector) {
    Recents("Recents", Icons.Filled.History),
    Dialer("Dialer", Icons.Filled.Dialpad),
    SmartUnblock("Unblock", Icons.Filled.Shield),
    Settings("Settings", Icons.Filled.Settings),
}

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
    var selected by remember { mutableStateOf(Tab.Recents) }

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
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selected == tab,
                        onClick = { selected = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        }
    ) { padding ->
        val content = Modifier.padding(padding)
        when (selected) {
            Tab.Recents -> RecentsScreen(viewModel(factory = factory), content)
            Tab.Dialer -> DialerScreen(viewModel(factory = factory), content)
            Tab.SmartUnblock -> SmartUnblockScreen(viewModel(factory = factory), content)
            Tab.Settings -> SettingsScreen(viewModel(factory = factory), content)
        }
    }
}
