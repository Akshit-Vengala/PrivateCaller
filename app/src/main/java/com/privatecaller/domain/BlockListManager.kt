package com.privatecaller.domain

import com.privatecaller.data.db.BlockedNumber
import com.privatecaller.data.db.BlockedNumberDao
import kotlinx.coroutines.flow.Flow

/** Manages the user's manual block list (numbers blocked regardless of contacts). */
class BlockListManager(
    private val dao: BlockedNumberDao,
    private val now: () -> Long = System::currentTimeMillis,
) {

    fun observeAll(): Flow<List<BlockedNumber>> = dao.observeAll()

    suspend fun isBlocked(number: String?): Boolean {
        val norm = NumberMatch.normalize(number)
        if (norm.isEmpty()) return false
        return dao.all().any { NumberMatch.sameNumber(it.normalized, norm) }
    }

    suspend fun block(number: String, label: String? = null) {
        val norm = NumberMatch.normalize(number)
        if (norm.isEmpty()) return
        dao.insert(BlockedNumber(norm, number, label, now()))
    }

    suspend fun unblock(number: String) = dao.delete(NumberMatch.normalize(number))

    suspend fun unblockNormalized(normalized: String) = dao.delete(normalized)
}
