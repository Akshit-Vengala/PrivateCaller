package com.privatecaller.domain

/**
 * Seam so the (flavor-neutral) call screener doesn't depend on SmartUnblock,
 * which only exists in the "full" edition. The "full" flavor backs this with
 * SmartUnblockManager; the "playstore" flavor uses a no-op that never unblocks.
 */
interface UnblockGate {
    /** True while an unblock window is active (let unknown numbers through). */
    suspend fun isUnblockActive(): Boolean

    /** Label of the active window (e.g. the app that opened it), if any. */
    suspend fun activeLabel(): String?
}
