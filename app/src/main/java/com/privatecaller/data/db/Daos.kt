package com.privatecaller.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MonitoredAppDao {
    @Query("SELECT * FROM monitored_apps ORDER BY appLabel COLLATE NOCASE")
    fun observeAll(): Flow<List<MonitoredApp>>

    @Query("SELECT * FROM monitored_apps WHERE packageName = :pkg AND enabled = 1 LIMIT 1")
    suspend fun enabledByPackage(pkg: String): MonitoredApp?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(app: MonitoredApp)

    @Query("DELETE FROM monitored_apps WHERE packageName = :pkg")
    suspend fun delete(pkg: String)
}

@Dao
interface UnblockWindowDao {
    @Insert
    suspend fun insert(window: UnblockWindow): Long

    @Query("SELECT * FROM unblock_windows WHERE expiresAt > :now ORDER BY expiresAt DESC")
    fun observeActive(now: Long): Flow<List<UnblockWindow>>

    /** Most recent still-active window, used by the screening service. */
    @Query("SELECT * FROM unblock_windows WHERE expiresAt > :now ORDER BY expiresAt DESC LIMIT 1")
    suspend fun activeNow(now: Long): UnblockWindow?

    /** An unexpired window already opened by the same trigger (avoid duplicates). */
    @Query("SELECT * FROM unblock_windows WHERE triggerPackage = :pkg AND expiresAt > :now ORDER BY expiresAt DESC LIMIT 1")
    suspend fun activeForPackage(pkg: String, now: Long): UnblockWindow?

    @Query("UPDATE unblock_windows SET expiresAt = :now WHERE expiresAt > :now")
    suspend fun cancelAllActive(now: Long)

    @Query("DELETE FROM unblock_windows WHERE expiresAt <= :cutoff")
    suspend fun purgeExpiredBefore(cutoff: Long)
}

@Dao
interface BlockedNumberDao {
    @Query("SELECT * FROM blocked_numbers ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<BlockedNumber>>

    @Query("SELECT * FROM blocked_numbers")
    suspend fun all(): List<BlockedNumber>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(blocked: BlockedNumber)

    @Query("DELETE FROM blocked_numbers WHERE normalized = :normalized")
    suspend fun delete(normalized: String)
}

@Dao
interface ContactSimPrefDao {
    @Query("SELECT * FROM contact_sim_prefs ORDER BY rawNumber")
    fun observeAll(): Flow<List<ContactSimPref>>

    @Query("SELECT * FROM contact_sim_prefs")
    suspend fun all(): List<ContactSimPref>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(pref: ContactSimPref)

    @Query("DELETE FROM contact_sim_prefs WHERE normalized = :normalized")
    suspend fun delete(normalized: String)
}

@Dao
interface ScreenedCallDao {
    @Insert
    suspend fun insert(call: ScreenedCall)

    @Query("SELECT * FROM screened_calls ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<ScreenedCall>>

    @Query("DELETE FROM screened_calls")
    suspend fun clear()
}
