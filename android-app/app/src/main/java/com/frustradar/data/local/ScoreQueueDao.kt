package com.frustradar.data.local

import androidx.room.*

/**
 * Room DAO for the score upload queue.
 *
 * Supports the Phase 3 offline buffering workflow:
 * 1. Insert scores as they're computed (on-device fusion).
 * 2. Query un-uploaded scores in batches.
 * 3. Mark scores as uploaded after successful 201 response.
 * 4. Periodically delete uploaded scores to reclaim storage.
 */
@Dao
interface ScoreQueueDao {

    /** Insert a new score into the queue. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(score: ScoreQueueEntity): Long

    /** Insert multiple scores at once. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(scores: List<ScoreQueueEntity>): List<Long>

    /** Get un-uploaded scores, oldest first, up to [limit]. */
    @Query("SELECT * FROM score_queue WHERE uploaded = 0 ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getUnuploaded(limit: Int = 50): List<ScoreQueueEntity>

    /** Count un-uploaded scores. */
    @Query("SELECT COUNT(*) FROM score_queue WHERE uploaded = 0")
    suspend fun countUnuploaded(): Int

    /** Mark specific scores as uploaded (after successful 201). */
    @Query("UPDATE score_queue SET uploaded = 1 WHERE id IN (:ids)")
    suspend fun markUploaded(ids: List<Long>)

    /** Delete all uploaded scores (storage cleanup). */
    @Query("DELETE FROM score_queue WHERE uploaded = 1")
    suspend fun deleteUploaded(): Int

    /** Get all scores (for testing). */
    @Query("SELECT * FROM score_queue ORDER BY createdAt ASC")
    suspend fun getAll(): List<ScoreQueueEntity>
}
