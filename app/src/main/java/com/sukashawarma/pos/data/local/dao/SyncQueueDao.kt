package com.sukashawarma.pos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sukashawarma.pos.data.local.entity.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncQueueDao {

    /** Antrian yang boleh dicoba sekarang, FIFO. FAILED tidak ikut — butuh aksi kasir. */
    @Query(
        "SELECT * FROM sync_queue WHERE status = 'PENDING' AND nextAttemptAt <= :now " +
            "ORDER BY queueId ASC"
    )
    suspend fun getReady(now: Long): List<SyncQueueEntity>

    @Query("SELECT * FROM sync_queue ORDER BY queueId ASC")
    suspend fun getAllOrdered(): List<SyncQueueEntity>

    /** REPLACE + unique index idempotencyKey: mutasi berulang menimpa, tidak menumpuk. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: SyncQueueEntity): Long

    @Query("DELETE FROM sync_queue WHERE queueId = :queueId")
    suspend fun delete(queueId: Long)

    @Query("DELETE FROM sync_queue WHERE idempotencyKey = :key")
    suspend fun deleteByIdempotencyKey(key: String)

    @Query(
        "UPDATE sync_queue SET status = 'FAILED', lastError = :error, attemptCount = attemptCount + 1 " +
            "WHERE queueId = :queueId"
    )
    suspend fun markFailed(queueId: Long, error: String)

    @Query(
        "UPDATE sync_queue SET attemptCount = :attemptCount, nextAttemptAt = :nextAttemptAt, " +
            "lastError = :error WHERE queueId = :queueId"
    )
    suspend fun markRetry(queueId: Long, attemptCount: Int, nextAttemptAt: Long, error: String?)

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'PENDING'")
    fun countPendingFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'FAILED'")
    fun countFailedFlow(): Flow<Int>
}
