package com.privatecaller.edition

import android.content.Context
import androidx.compose.runtime.Composable
import com.privatecaller.AppContainer
import com.privatecaller.NavTab
import com.privatecaller.PrivateCallerApp
import com.privatecaller.domain.UnblockGate
import com.privatecaller.ui.SettingsViewModel
import kotlinx.coroutines.CoroutineScope

/**
 * "playstore" edition: NO SmartUnblock and NO in-app updater. Same shape as the
 * "full" [Edition] so src/main compiles unchanged, but every feature hook is a
 * no-op — and crucially none of the flagged code (notification listener, APK
 * installer) is even present in this flavor's sources.
 */
object Edition {

    fun unblockGate(container: AppContainer): UnblockGate = NoUnblock

    fun onAppCreate(app: PrivateCallerApp, container: AppContainer, scope: CoroutineScope) {
        // Nothing extra in the playstore edition.
    }

    /** No notification listener here, so treat access as "granted" (card hidden). */
    fun isNotificationAccessGranted(context: Context): Boolean = true

    fun openNotificationAccessSettings(context: Context) {
        // No-op: the playstore edition has no notification listener.
    }

    fun extraTabs(): List<NavTab> = emptyList()

    @Composable
    fun SmartUnblockSetting(viewModel: SettingsViewModel) {
        // No SmartUnblock in the playstore edition.
    }

    @Composable
    fun UpdaterSetting() {
        // Updates are delivered through the Play Store.
    }

    private object NoUnblock : UnblockGate {
        override suspend fun isUnblockActive(): Boolean = false
        override suspend fun activeLabel(): String? = null
    }
}
