package com.privatecaller.domain

/** Normalises and compares phone numbers for block-list matching. */
object NumberMatch {

    /** Digits only (drops spaces, dashes, parens, leading +). */
    fun normalize(number: String?): String =
        number?.filter { it.isDigit() } ?: ""

    /**
     * True if two numbers refer to the same line. Compares the full digit
     * strings, or their last 10 digits to tolerate country-code differences
     * (e.g. "+91 98765 43210" vs "9876543210").
     */
    fun sameNumber(a: String?, b: String?): Boolean {
        val na = normalize(a)
        val nb = normalize(b)
        if (na.isEmpty() || nb.isEmpty()) return false
        if (na == nb) return true
        val tailLen = minOf(na.length, nb.length, 10)
        if (tailLen < 7) return false
        return na.takeLast(tailLen) == nb.takeLast(tailLen)
    }
}
