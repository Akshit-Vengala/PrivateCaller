package com.privatecaller.domain

import java.util.Calendar

/** Recents filter chips. */
enum class CallFilter { ALL, MISSED, CONTACTS, BLOCKED, AUTOBLOCKED }

/** Date buckets shown as section headers in Recents. */
enum class DateSection { TODAY, YESTERDAY, OLDER }

/**
 * A run of consecutive call-log entries from the same contact (Google-Phone
 * style aggregation). [latest] is the most recent call; [count] is how many
 * calls the run collapses, shown as "Name (count)".
 */
data class CallAggregate(
    val number: String?,
    val name: String?,
    val photoUri: String?,
    val latest: CallLogEntry,
    val calls: List<CallLogEntry>,
) {
    val count: Int get() = calls.size
    val id: Long get() = latest.id
}

data class CallSection(
    val section: DateSection,
    val items: List<CallAggregate>,
)

/** Contact display info resolved from the device contacts as a fallback. */
data class ContactHint(val name: String?, val photoUri: String?)

/**
 * Turns a flat, date-descending call log into date sections, each containing
 * consecutive-same-contact aggregates. Aggregation never spans across a date
 * section or across a different contact: as soon as a different contact's call
 * appears, the current run ends and a new one begins — exactly how the stock
 * dialer groups history.
 */
object CallLogGrouping {

    fun build(
        entries: List<CallLogEntry>,
        filter: CallFilter,
        isUserBlocked: (CallLogEntry) -> Boolean,
        now: Long = System.currentTimeMillis(),
        // Falls back to the device contacts when the call log didn't cache a
        // name/photo for a row (some OEMs leave CACHED_NAME blank, e.g. when the
        // saved number's format differs from the incoming number).
        resolveContact: (CallLogEntry) -> ContactHint? = { null },
    ): List<CallSection> {
        val filtered = entries.filter { matches(it, filter, isUserBlocked) }
        if (filtered.isEmpty()) return emptyList()

        val todayStart = startOfDay(now)
        val yesterdayStart = todayStart - DAY_MS

        val result = ArrayList<CallSection>(3)
        for (section in DateSection.entries) {
            // `filtered` is date-descending, so each section keeps that order.
            val inSection = filtered.filter { sectionOf(it.date, todayStart, yesterdayStart) == section }
            if (inSection.isNotEmpty()) {
                result += CallSection(section, aggregateConsecutive(inSection, resolveContact))
            }
        }
        return result
    }

    /** Whether [entry] was auto-blocked by screening (vs manually blocked by the user). */
    fun isAutoBlocked(entry: CallLogEntry, isUserBlocked: (CallLogEntry) -> Boolean): Boolean =
        entry.type == CallType.BLOCKED && !isUserBlocked(entry)

    private fun matches(
        entry: CallLogEntry,
        filter: CallFilter,
        isUserBlocked: (CallLogEntry) -> Boolean,
    ): Boolean = when (filter) {
        CallFilter.ALL -> true
        CallFilter.MISSED -> entry.type == CallType.MISSED
        CallFilter.CONTACTS -> !entry.cachedName.isNullOrBlank()
        CallFilter.BLOCKED -> isUserBlocked(entry)
        CallFilter.AUTOBLOCKED -> isAutoBlocked(entry, isUserBlocked)
    }

    private fun aggregateConsecutive(
        entries: List<CallLogEntry>,
        resolveContact: (CallLogEntry) -> ContactHint?,
    ): List<CallAggregate> {
        val out = ArrayList<CallAggregate>()
        var run = ArrayList<CallLogEntry>()
        for (e in entries) {
            if (run.isEmpty() || sameContact(run.first(), e)) {
                run.add(e)
            } else {
                out += toAggregate(run, resolveContact)
                run = arrayListOf(e)
            }
        }
        if (run.isNotEmpty()) out += toAggregate(run, resolveContact)
        return out
    }

    private fun sameContact(a: CallLogEntry, b: CallLogEntry): Boolean {
        val an = a.number?.takeIf { it.isNotBlank() } ?: return false
        val bn = b.number?.takeIf { it.isNotBlank() } ?: return false
        return NumberMatch.sameNumber(an, bn)
    }

    private fun toAggregate(
        run: List<CallLogEntry>,
        resolveContact: (CallLogEntry) -> ContactHint?,
    ): CallAggregate {
        val latest = run.first()
        val cachedName = run.firstNotNullOfOrNull { it.cachedName?.takeIf { n -> n.isNotBlank() } }
        val cachedPhoto = run.firstNotNullOfOrNull { it.cachedPhotoUri?.takeIf { p -> p.isNotBlank() } }
        // Fall back to the device contacts only when the call log lacks the info.
        val hint = if (cachedName == null || cachedPhoto == null) resolveContact(latest) else null
        return CallAggregate(
            number = latest.number,
            name = cachedName ?: hint?.name,
            photoUri = cachedPhoto ?: hint?.photoUri,
            latest = latest,
            calls = run,
        )
    }

    private fun sectionOf(date: Long, todayStart: Long, yesterdayStart: Long): DateSection = when {
        date >= todayStart -> DateSection.TODAY
        date >= yesterdayStart -> DateSection.YESTERDAY
        else -> DateSection.OLDER
    }

    private fun startOfDay(now: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private const val DAY_MS = 24L * 60 * 60 * 1000
}
