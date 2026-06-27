package com.privatecaller.domain

import com.privatecaller.data.db.ScreenOutcome
import com.privatecaller.data.db.ScreenedCall
import com.privatecaller.data.db.ScreenedCallDao
import com.privatecaller.data.prefs.SettingsStore

/** Decision returned to the platform CallScreeningService. */
data class ScreenDecision(
    val allow: Boolean,
    val outcome: ScreenOutcome,
    val reason: String,
    val displayName: String?,
)

/**
 * Pure-ish decision logic shared by the screening service. Order of checks:
 *  1. manually blocked         -> block (overrides everything, any SIM)
 *  2. screening off            -> allow everything
 *  3. call's SIM not screened  -> allow
 *  4. known contact            -> allow
 *  5. SmartUnblock on + window active -> allow
 *  6. otherwise                -> block with the configured message
 */
class CallScreener(
    private val contactLookup: ContactLookup,
    private val unblock: UnblockGate,
    private val blockList: BlockListManager,
    private val settingsStore: SettingsStore,
    private val screenedCallDao: ScreenedCallDao,
    private val now: () -> Long = System::currentTimeMillis,
) {

    suspend fun decide(number: String?, simSlot: Int? = null): ScreenDecision {
        val settings = settingsStore.current()

        // Manual block list is authoritative — block even known contacts.
        if (blockList.isBlocked(number)) {
            return ScreenDecision(false, ScreenOutcome.BLOCKED, "Blocked number", null)
        }

        if (!settings.screeningEnabled) {
            return ScreenDecision(true, ScreenOutcome.ALLOWED_UNBLOCK, "Screening off", null)
        }

        if (!shouldScreenSim(settings.screeningSimSlots, simSlot)) {
            return ScreenDecision(true, ScreenOutcome.ALLOWED_UNBLOCK, "SIM not screened", null)
        }

        val match = contactLookup.lookup(number)
        if (match.isKnown) {
            return ScreenDecision(
                allow = true,
                outcome = ScreenOutcome.ALLOWED_CONTACT,
                reason = "Known contact",
                displayName = match.displayName,
            )
        }

        if (settings.smartUnblockEnabled && unblock.isUnblockActive()) {
            val label = unblock.activeLabel()
            return ScreenDecision(
                allow = true,
                outcome = ScreenOutcome.ALLOWED_UNBLOCK,
                reason = "SmartUnblock active" + (label?.let { " ($it)" } ?: ""),
                displayName = null,
            )
        }

        return ScreenDecision(
            allow = false,
            outcome = ScreenOutcome.BLOCKED,
            reason = settings.blockMessage,
            displayName = null,
        )
    }

    /**
     * Whether a call on [simSlot] should be screened.
     *  - slots == null      -> screen all SIMs
     *  - slots is empty      -> screen none
     *  - unknown slot + specific config -> don't block (avoid over-blocking)
     */
    private fun shouldScreenSim(slots: Set<Int>?, simSlot: Int?): Boolean {
        if (slots == null) return true
        if (simSlot == null) return false
        return simSlot in slots
    }

    /** Records the screened call for the Recents list (respecting log settings). */
    suspend fun log(number: String?, decision: ScreenDecision) {
        val settings = settingsStore.current()
        if (decision.outcome == ScreenOutcome.BLOCKED && !settings.logBlockedCalls) return
        screenedCallDao.insert(
            ScreenedCall(
                number = number ?: "Unknown",
                displayName = decision.displayName,
                timestamp = now(),
                outcome = decision.outcome,
                reason = decision.reason,
            )
        )
    }
}
