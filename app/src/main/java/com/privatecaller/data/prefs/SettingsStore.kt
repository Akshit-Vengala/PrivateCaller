package com.privatecaller.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Snapshot of user-tunable behaviour read by the screening service. */
data class AppSettings(
    val screeningEnabled: Boolean = true,
    val smartUnblockEnabled: Boolean = true,
    val logBlockedCalls: Boolean = true,
    /**
     * SIM slot indices that should screen unknown calls.
     * null = all SIMs (default), empty set = none, {0}/{1}/{0,1} = those slots.
     */
    val screeningSimSlots: Set<Int>? = null,
)

class SettingsStore(private val context: Context) {

    private object Keys {
        val SCREENING_ENABLED = booleanPreferencesKey("screening_enabled")
        val SMART_UNBLOCK_ENABLED = booleanPreferencesKey("smart_unblock_enabled")
        val LOG_BLOCKED = booleanPreferencesKey("log_blocked_calls")
        // CSV of slot indices. Absent = all SIMs; "" = none; "0,1" = those slots.
        val SCREENING_SIM_SLOTS = stringPreferencesKey("screening_sim_slots")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            screeningEnabled = p[Keys.SCREENING_ENABLED] ?: true,
            smartUnblockEnabled = p[Keys.SMART_UNBLOCK_ENABLED] ?: true,
            logBlockedCalls = p[Keys.LOG_BLOCKED] ?: true,
            screeningSimSlots = p[Keys.SCREENING_SIM_SLOTS]?.let { raw ->
                raw.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
            },
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setScreeningEnabled(value: Boolean) =
        context.dataStore.edit { it[Keys.SCREENING_ENABLED] = value }

    suspend fun setSmartUnblockEnabled(value: Boolean) =
        context.dataStore.edit { it[Keys.SMART_UNBLOCK_ENABLED] = value }

    suspend fun setLogBlockedCalls(value: Boolean) =
        context.dataStore.edit { it[Keys.LOG_BLOCKED] = value }

    /** null = screen all SIMs; otherwise the exact set of slots to screen. */
    suspend fun setScreeningSimSlots(slots: Set<Int>?) =
        context.dataStore.edit { p ->
            if (slots == null) {
                p.remove(Keys.SCREENING_SIM_SLOTS)
            } else {
                p[Keys.SCREENING_SIM_SLOTS] = slots.sorted().joinToString(",")
            }
        }
}
