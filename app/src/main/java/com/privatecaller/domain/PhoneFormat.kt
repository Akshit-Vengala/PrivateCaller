package com.privatecaller.domain

import android.content.Context
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import java.util.Locale

/**
 * Formats raw phone numbers for display with readable grouping/spacing.
 *
 * Fast path: most calls are *domestic* (same country as the SIM). For those we
 * know the country up front and the grouping is a fixed, trivial rule, so we do
 * cheap string work instead of the heavy libphonenumber engine.
 *
 * Slow path: only a foreign number, or a domestic number with an unexpected
 * length, falls back to [PhoneNumberUtils.formatNumber] (rare).
 */
object PhoneFormat {

    // The device's home country rarely changes within a session; resolve once.
    @Volatile private var homeIso: String? = null
    // Country calling code for the home ISO, e.g. "91" for IN, "1" for US.
    @Volatile private var homeCode: String? = null
    @Volatile private var resolved = false

    fun pretty(context: Context, number: String?): String {
        if (number.isNullOrBlank()) return "Unknown"
        val raw = number.trim()
        // Service codes / alphanumeric sender IDs (*123#, "VM-ICICI") — leave as-is.
        if (raw.any { it.isLetter() } || raw.contains('*') || raw.contains('#')) return raw

        ensureHome(context)
        val code = homeCode

        // Split into (national digits, is-it-domestic).
        val national: String
        val domestic: Boolean
        when {
            raw.startsWith("+") -> {
                val digits = raw.drop(1).filter { it.isDigit() }
                if (code != null && digits.startsWith(code)) {
                    national = digits.drop(code.length); domestic = true
                } else {
                    national = digits; domestic = false
                }
            }
            raw.startsWith("00") -> { // some regions dial international as 00<code>
                val digits = raw.drop(2).filter { it.isDigit() }
                if (code != null && digits.startsWith(code)) {
                    national = digits.drop(code.length); domestic = true
                } else {
                    national = digits; domestic = false
                }
            }
            else -> {
                national = raw.filter { it.isDigit() }; domestic = true
            }
        }

        if (domestic) groupDomestic(national, code)?.let { return it }

        // Foreign, or a domestic length we have no rule for → full formatter (rare).
        return runCatching { PhoneNumberUtils.formatNumber(raw, homeIso) }.getOrNull() ?: raw
    }

    /** Cheap, country-specific grouping for a national number. null = "not sure". */
    private fun groupDomestic(national: String, code: String?): String? {
        if (national.isEmpty()) return null
        val prefix = if (code != null) "+$code " else ""
        return when {
            // North America: +1 (415) 555-0123
            code == "1" && national.length == 10 ->
                "$prefix(${national.take(3)}) ${national.substring(3, 6)}-${national.substring(6)}"
            // Common 10-digit mobile (India and many others): 5 + 5
            national.length == 10 -> "$prefix${national.take(5)} ${national.drop(5)}"
            // 11-digit national (some landlines / leading 0): 5 + 3 + 3
            national.length == 11 ->
                "$prefix${national.take(5)} ${national.substring(5, 8)} ${national.drop(8)}"
            else -> null // unexpected shape → let the full formatter decide
        }
    }

    private fun ensureHome(context: Context) {
        if (resolved) return
        synchronized(this) {
            if (resolved) return
            val tm = context.getSystemService(TelephonyManager::class.java)
            val iso = (tm?.simCountryIso?.takeIf { it.isNotBlank() } ?: tm?.networkCountryIso)
                .orEmpty()
                .uppercase(Locale.ROOT)
                .ifBlank { Locale.getDefault().country }
            homeIso = iso
            homeCode = CALLING_CODES[iso]
            resolved = true
        }
    }

    // Calling codes for the countries most likely to be a user's home SIM.
    // An unknown ISO just means we skip the "+code" prefix and group nationally;
    // anything genuinely odd still falls through to PhoneNumberUtils.formatNumber.
    private val CALLING_CODES = mapOf(
        "IN" to "91", "US" to "1", "CA" to "1", "GB" to "44", "AU" to "61",
        "AE" to "971", "SA" to "966", "SG" to "65", "MY" to "60", "ID" to "62",
        "PK" to "92", "BD" to "880", "LK" to "94", "NP" to "977", "PH" to "63",
        "DE" to "49", "FR" to "33", "IT" to "39", "ES" to "34", "NL" to "31",
        "ZA" to "27", "NG" to "234", "KE" to "254", "BR" to "55", "MX" to "52",
        "JP" to "81", "KR" to "82", "CN" to "86", "RU" to "7", "TR" to "90",
    )
}
