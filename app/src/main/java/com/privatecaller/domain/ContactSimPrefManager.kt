package com.privatecaller.domain

import com.privatecaller.data.db.ContactSimPref
import com.privatecaller.data.db.ContactSimPrefDao
import kotlinx.coroutines.flow.Flow

/** Stores/looks up the outgoing-SIM preference for individual numbers. */
class ContactSimPrefManager(private val dao: ContactSimPrefDao) {

    fun observeAll(): Flow<List<ContactSimPref>> = dao.observeAll()

    /** The saved preference for [number], or null if none ("ask each time"). */
    suspend fun prefFor(number: String?): ContactSimPref? {
        val norm = NumberMatch.normalize(number)
        if (norm.isEmpty()) return null
        return dao.all().firstOrNull { NumberMatch.sameNumber(it.normalized, norm) }
    }

    /** slot null = always ask; otherwise always use that SIM slot. */
    suspend fun setSlot(number: String, slot: Int?) {
        val norm = NumberMatch.normalize(number)
        if (norm.isEmpty()) return
        dao.upsert(ContactSimPref(norm, number, slot))
    }

    suspend fun clear(number: String) = dao.delete(NumberMatch.normalize(number))
}
