package com.afterlater.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedItemDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: SavedItemEntity)

    @Update
    suspend fun update(item: SavedItemEntity)

    @Query("SELECT * FROM saved_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SavedItemEntity?

    @Query("SELECT * FROM saved_items WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<SavedItemEntity?>

    @Query("SELECT * FROM saved_items WHERE canonicalUrl = :canonicalUrl LIMIT 1")
    suspend fun findByCanonicalUrl(canonicalUrl: String): SavedItemEntity?

    @Query(
        """
        SELECT * FROM saved_items
        WHERE (:query = ''
            OR title LIKE '%' || :query || '%' ESCAPE '\'
            OR description LIKE '%' || :query || '%' ESCAPE '\'
            OR personalNote LIKE '%' || :query || '%' ESCAPE '\'
            OR domain LIKE '%' || :query || '%' ESCAPE '\'
            OR originalUrl LIKE '%' || :query || '%' ESCAPE '\'
            OR canonicalUrl LIKE '%' || :query || '%' ESCAPE '\')
        AND (:status IS NULL OR status = :status)
        AND (:domain IS NULL OR domain = :domain)
        ORDER BY
            CASE WHEN :sort = 'NEWEST' THEN savedAt END DESC,
            CASE WHEN :sort = 'OLDEST' THEN savedAt END ASC,
            CASE WHEN :sort = 'RECENTLY_REVIEWED' THEN COALESCE(lastReviewedAt, 0) END DESC,
            savedAt DESC
        """,
    )
    fun observeArchive(
        query: String,
        status: String?,
        domain: String?,
        sort: String,
    ): Flow<List<SavedItemEntity>>

    @Query("SELECT DISTINCT domain FROM saved_items ORDER BY domain COLLATE NOCASE")
    fun observeDomains(): Flow<List<String>>

    @Query(
        """
        SELECT COUNT(*) FROM saved_items
        WHERE status IN ('NEW', 'READY', 'SNOOZED')
        AND nextReviewAt IS NOT NULL AND nextReviewAt <= :now
        AND (snoozedUntil IS NULL OR snoozedUntil <= :now)
        """,
    )
    fun observeDueCount(now: Long): Flow<Int>

    @Query(
        """
        SELECT * FROM saved_items
        WHERE status IN ('NEW', 'READY', 'SNOOZED')
        AND nextReviewAt IS NOT NULL AND nextReviewAt <= :now
        AND (snoozedUntil IS NULL OR snoozedUntil <= :now)
        ORDER BY
            CASE WHEN lastReviewedAt IS NULL THEN 0 ELSE 1 END ASC,
            savedAt ASC,
            nextReviewAt ASC,
            id ASC
        LIMIT :limit
        """,
    )
    fun observeDueCandidates(now: Long, limit: Int): Flow<List<SavedItemEntity>>

    @Query(
        """
        SELECT * FROM saved_items
        WHERE status IN ('NEW', 'READY', 'SNOOZED')
        AND nextReviewAt IS NOT NULL AND nextReviewAt <= :now
        AND (snoozedUntil IS NULL OR snoozedUntil <= :now)
        ORDER BY
            CASE WHEN lastReviewedAt IS NULL THEN 0 ELSE 1 END ASC,
            savedAt ASC,
            nextReviewAt ASC,
            id ASC
        LIMIT :limit
        """,
    )
    suspend fun getDueCandidates(now: Long, limit: Int): List<SavedItemEntity>

    @Query("SELECT COUNT(*) FROM saved_items")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT * FROM saved_items ORDER BY savedAt ASC")
    suspend fun getAll(): List<SavedItemEntity>

    @Query("DELETE FROM saved_items WHERE id = :id")
    suspend fun deleteById(id: String): Int
}

@Dao
interface ReviewEventDao {

    @Insert
    suspend fun insert(event: ReviewEventEntity)

    @Query("SELECT * FROM review_events WHERE itemId = :itemId ORDER BY timestamp DESC")
    fun observeForItem(itemId: String): Flow<List<ReviewEventEntity>>

    @Query("SELECT * FROM review_events ORDER BY timestamp ASC")
    suspend fun getAll(): List<ReviewEventEntity>

    @Query("DELETE FROM review_events WHERE itemId = :itemId")
    suspend fun deleteForItem(itemId: String)
}
