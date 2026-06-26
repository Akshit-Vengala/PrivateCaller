package com.privatecaller.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.privatecaller.BuildConfig
import com.privatecaller.PrivateCallerApp
import com.privatecaller.domain.ReleaseInfo
import com.privatecaller.domain.UpdateCheck
import kotlinx.coroutines.launch
import java.io.File

private sealed interface UpdateUi {
    data object Idle : UpdateUi
    data object Checking : UpdateUi
    data object UpToDate : UpdateUi
    data class Available(val release: ReleaseInfo) : UpdateUi
    data object Downloading : UpdateUi
    data object NeedsPermission : UpdateUi
    data class Error(val message: String) : UpdateUi
}

/**
 * "About / Updates" block for Settings: shows the current version and checks
 * GitHub for a newer release. On download it installs the APK; if the user
 * hasn't granted "install unknown apps" yet, it sends them to that setting and
 * **resumes the install automatically** once they return with it granted.
 */
@Composable
fun UpdateSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val updater = remember { PrivateCallerApp.container(context).updateManager }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var state by remember { mutableStateOf<UpdateUi>(UpdateUi.Idle) }
    var progress by remember { mutableFloatStateOf(0f) }
    var pendingInstall by remember { mutableStateOf<File?>(null) }

    fun beginInstall(file: File) {
        if (updater.canInstallPackages()) {
            pendingInstall = null
            state = UpdateUi.Idle
            updater.installApk(file)
        } else {
            // First-time: send the user to grant "install unknown apps", and
            // remember the file so we can fire the install the moment they're back.
            pendingInstall = file
            state = UpdateUi.NeedsPermission
            context.startActivity(updater.unknownSourcesSettingsIntent())
        }
    }

    // Auto-resume the install once permission is granted and we return.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val file = pendingInstall
                if (file != null && updater.canInstallPackages()) {
                    pendingInstall = null
                    state = UpdateUi.Idle
                    updater.installApk(file)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun check() {
        state = UpdateUi.Checking
        scope.launch {
            state = when (val result = updater.check()) {
                is UpdateCheck.UpToDate -> UpdateUi.UpToDate
                is UpdateCheck.Available -> UpdateUi.Available(result.release)
                is UpdateCheck.Failed -> UpdateUi.Error(result.message)
            }
        }
    }

    fun download(release: ReleaseInfo) {
        val url = release.apkUrl
        if (url == null) {
            // No APK attached to the release — open the page so the user can grab it.
            updater.openReleasePage(release.pageUrl)
            return
        }
        progress = 0f
        state = UpdateUi.Downloading
        scope.launch {
            runCatching { updater.downloadApk(url) { progress = it } }
                .onSuccess { beginInstall(it) }
                .onFailure { state = UpdateUi.Error(it.message ?: "Download failed") }
        }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("About", style = MaterialTheme.typography.titleMedium)
        Text(
            "Version ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when (val s = state) {
            UpdateUi.Idle ->
                Button(onClick = { check() }) { Text("Check for updates") }

            UpdateUi.Checking ->
                LabeledSpinner("Checking GitHub…")

            UpdateUi.UpToDate -> {
                Text("You're on the latest version.", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { check() }) { Text("Check again") }
            }

            is UpdateUi.Available -> {
                Text(
                    "Update available — version ${s.release.versionName}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (s.release.notes.isNotBlank()) {
                    Text(
                        s.release.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = { download(s.release) }) {
                    Text(if (s.release.apkUrl != null) "Download & install" else "View release")
                }
            }

            UpdateUi.Downloading -> {
                Text("Downloading… ${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            UpdateUi.NeedsPermission ->
                LabeledSpinner("Waiting for install permission… it'll start automatically.")

            is UpdateUi.Error -> {
                Text(
                    s.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Button(onClick = { check() }) { Text("Try again") }
            }
        }
    }
}

@Composable
private fun LabeledSpinner(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.size(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
