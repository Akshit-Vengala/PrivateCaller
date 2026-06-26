package com.privatecaller

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PrivateCallerApp : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createUnblockChannel()
        appScope.launch {
            container.seedDefaultMonitoredAppsIfEmpty()
            container.smartUnblock.purgeOld()
        }
        registerActivityLifecycleCallbacks(IconReconciler())
    }

    /**
     * Swaps the launcher icon to match the screening setting only once the app
     * has fully gone to the background — changing the launcher component while
     * the user is in the app can close it.
     */
    private inner class IconReconciler : ActivityLifecycleCallbacks {
        private var started = 0

        override fun onActivityStarted(activity: Activity) {
            started++
        }

        override fun onActivityStopped(activity: Activity) {
            started--
            if (started <= 0) {
                appScope.launch {
                    container.launcherIcon.apply(
                        container.settingsStore.current().screeningEnabled,
                    )
                }
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityResumed(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }

    private fun createUnblockChannel() {
        val channel = NotificationChannel(
            UNBLOCK_CHANNEL_ID,
            getString(R.string.unblock_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val UNBLOCK_CHANNEL_ID = "smart_unblock"

        fun from(context: Context): PrivateCallerApp =
            context.applicationContext as PrivateCallerApp

        fun container(context: Context): AppContainer = from(context).container
    }
}
