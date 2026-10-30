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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<SavedItemEntity>): List<Long>

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
        AND status IN (:statuses)
        AND (:domain IS NULL OR domain = :domain)
        AND (:tag IS NULL OR EXISTS (
            SELECT 1 FROM saved_item_tags st
            WHERE st.itemId = saved_items.id AND st.tagName = :tag
        ))
        ORDER BY
            CASE WHEN :sort = 'NEWEST' THEN savedAt END DESC,
            CASE WHEN :sort = 'OLDEST' THEN savedAt END ASC,
            CASE WHEN :sort = 'RECENTLY_REVIEWED' THEN COALESCE(lastReviewedAt, 0) END DESC,
            savedAt DESC
        """,
    )
    fun observeArchive(
        query: String,
        statuses: List<String>,
        domain: String?,
        tag: String?,
        sort: String,
    ): Flow<List<SavedItemEntity>>

    @Query("SELECT DISTINCT domain FROM saved_items ORDER BY domain COLLATE NOCASE")
    fun observeDomains(): Flow<List<String>>

    @Query(
        """
        SELECT COUNT(*) FROM saved_items
        WHERE status IN ('NEW', 'READY', 'SNOOZED', 'USEFUL')
        AND nextReviewAt IS NOT NULL AND nextReviewAt <= :now
        AND (snoozedUntil IS NULL OR snoozedUntil <= :now)
        AND (expiresAt IS NULL OR expiresAt > :now)
        """,
    )
    fun observeDueCount(now: Long): Flow<Int>

    @Query(
        """
        SELECT * FROM saved_items
        WHERE status IN ('NEW', 'READY', 'SNOOZED', 'USEFUL')
        AND nextReviewAt IS NOT NULL AND nextReviewAt <= :now
        AND (snoozedUntil IS NULL OR snoozedUntil <= :now)
        AND (expiresAt IS NULL OR expiresAt > :now)
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
        WHERE status IN ('NEW', 'READY', 'SNOOZED', 'USEFUL')
        AND nextReviewAt IS NOT NULL AND nextReviewAt <= :now
        AND (snoozedUntil IS NULL OR snoozedUntil <= :now)
        AND (expiresAt IS NULL OR expiresAt > :now)
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

    @Insert
    suspend fun insertAll(events: List<ReviewEventEntity>): List<Long>

    @Query("SELECT * FROM review_events WHERE itemId = :itemId ORDER BY timestamp DESC")
    fun observeForItem(itemId: String): Flow<List<ReviewEventEntity>>

    @Query("SELECT * FROM review_events ORDER BY timestamp ASC")
    suspend fun getAll(): List<ReviewEventEntity>

    @Query("DELETE FROM review_events WHERE itemId = :itemId")
    suspend fun deleteForItem(itemId: String)
}

@Dao
interface TagDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun attach(link: SavedItemTagEntity): Long

    @Query("SELECT tagName FROM saved_item_tags WHERE itemId = :itemId ORDER BY tagName ASC")
    suspend fun tagsForItem(itemId: String): List<String>

    @Query("SELECT tagName FROM saved_item_tags WHERE itemId = :itemId ORDER BY tagName ASC")
    fun observeTagsForItem(itemId: String): Flow<List<String>>

    @Query("SELECT * FROM tags ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("DELETE FROM saved_item_tags WHERE itemId = :itemId")
    suspend fun detachAll(itemId: String)

    @Query("DELETE FROM saved_item_tags WHERE itemId = :itemId AND tagName = :tagName")
    suspend fun detach(itemId: String, tagName: String)
}
