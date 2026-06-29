package com.privatecaller.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * An app whose notifications can trigger a SmartUnblock window
 * (e.g. Amazon, Uber, Swiggy). When a matching notification arrives,
 * a window of [unblockMinutes] is opened so the delivery/ride agent
 * can reach the user from an unknown number.
 */
@Entity(tableName = "monitored_apps")
data class MonitoredApp(
    @PrimaryKey val packageName: String,
    val appLabel: String,
    val enabled: Boolean = true,
    val unblockMinutes: Int = 30,
)

/**
 * A period during which calls from unknown numbers are allowed through.
 * The set of currently-active windows is the union of rows where
 * [expiresAt] is in the future.
 */
@Entity(tableName = "unblock_windows")
data class UnblockWindow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val triggerLabel: String,
    val triggerPackage: String? = null,
    val startedAt: Long,
    val expiresAt: Long,
)

/**
 * A number the user has explicitly blocked. Manual blocks override everything
 * (even saved contacts), on any SIM.
 */
@Entity(tableName = "blocked_numbers")
data class BlockedNumber(
    /** Digits-only form used for matching. */
    @PrimaryKey val normalized: String,
    val rawNumber: String,
    val label: String? = null,
    val addedAt: Long,
)

/**
 * Per-number outgoing-SIM preference. [simSlot] null = ask each time;
 * otherwise the SIM slot index to always use for this number.
 */
@Entity(tableName = "contact_sim_prefs")
data class ContactSimPref(
    @PrimaryKey val normalized: String,
    val rawNumber: String,
    val simSlot: Int?,
)

/** Outcome recorded for the app's own "Recents" list. */
enum class ScreenOutcome { ALLOWED_CONTACT, ALLOWED_UNBLOCK, BLOCKED, BLOCKED_MANUAL }

/** A single incoming call that was screened. */
@Entity(tableName = "screened_calls")
data class ScreenedCall(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: String,
    val displayName: String? = null,
    val timestamp: Long,
    val outcome: ScreenOutcome,
    val reason: String,
)
