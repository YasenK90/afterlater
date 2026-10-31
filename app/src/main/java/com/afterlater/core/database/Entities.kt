package com.afterlater.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.FtsOptions
import com.afterlater.core.model.ItemStatus
import com.afterlater.core.model.MetadataStatus
import com.afterlater.core.model.ReviewAction
import com.afterlater.core.model.ReviewEvent
import com.afterlater.core.model.SavedItem
import com.afterlater.core.model.SurfaceHint
import java.time.Instant

@Entity(
    tableName = "saved_items",
    indices = [
        Index(value = ["canonicalUrl"], unique = true),
        Index(value = ["savedAt"]),
        Index(value = ["nextReviewAt"]),
        Index(value = ["status"]),
        Index(value = ["domain"]),
    ],
)
data class SavedItemEntity(
    @PrimaryKey val id: String,
    val originalUrl: String,
    val canonicalUrl: String,
    val title: String?,
    val description: String?,
    val domain: String,
    val thumbnailUrl: String?,
    val savedAt: Long,
    val updatedAt: Long,
    val status: ItemStatus,
    val lastReviewedAt: Long?,
    val nextReviewAt: Long?,
    val snoozedUntil: Long?,
    val personalNote: String?,
    val metadataStatus: MetadataStatus,
    @ColumnInfo(defaultValue = "QUEUE") val surfaceHint: SurfaceHint = SurfaceHint.QUEUE,
    val expiresAt: Long? = null,
)

@Entity(
    tableName = "review_events",
    foreignKeys = [
        ForeignKey(
            entity = SavedItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["itemId"]),
        Index(value = ["itemId", "timestamp"]),
        Index(value = ["timestamp"]),
    ],
)
data class ReviewEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: String,
    val action: ReviewAction,
    val timestamp: Long,
    val snoozeUntil: Long?,
)

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey val name: String,
    val createdAt: Long,
)

@Entity(
    tableName = "saved_item_tags",
    primaryKeys = ["itemId", "tagName"],
    foreignKeys = [
        ForeignKey(
            entity = SavedItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["name"],
            childColumns = ["tagName"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["itemId"]), Index(value = ["tagName"])],
)
data class SavedItemTagEntity(
    val itemId: String,
    val tagName: String,
)

/**
 * External-content full-text index over the searchable columns of
 * [SavedItemEntity]. Room's generated sync triggers keep it in step with
 * saved_items; MIGRATION_3_4 must recreate both the table and the triggers
 * for pre-existing installs.
 */
@Fts4(contentEntity = SavedItemEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "saved_items_fts")
data class SavedItemFtsEntity(
    val title: String?,
    val description: String?,
    val personalNote: String?,
    val domain: String,
    val originalUrl: String,
    val canonicalUrl: String,
)

fun SavedItem.toEntity() = SavedItemEntity(
    id = id,
    originalUrl = originalUrl,
    canonicalUrl = canonicalUrl,
    title = title,
    description = description,
    domain = domain,
    thumbnailUrl = thumbnailUrl,
    savedAt = savedAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    status = status,
    lastReviewedAt = lastReviewedAt?.toEpochMilli(),
    nextReviewAt = nextReviewAt?.toEpochMilli(),
    snoozedUntil = snoozedUntil?.toEpochMilli(),
    personalNote = personalNote,
    metadataStatus = metadataStatus,
    surfaceHint = surfaceHint,
    expiresAt = expiresAt?.toEpochMilli(),
)

fun ReviewEvent.toEntity() = ReviewEventEntity(
    id = 0,
    itemId = itemId,
    action = action,
    timestamp = timestamp.toEpochMilli(),
    snoozeUntil = snoozeUntil?.toEpochMilli(),
)

fun SavedItemEntity.toModel() = com.afterlater.core.model.SavedItem(
    id = id,
    originalUrl = originalUrl,
    canonicalUrl = canonicalUrl,
    title = title,
    description = description,
    domain = domain,
    thumbnailUrl = thumbnailUrl,
    savedAt = Instant.ofEpochMilli(savedAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    status = status,
    lastReviewedAt = lastReviewedAt?.let(Instant::ofEpochMilli),
    nextReviewAt = nextReviewAt?.let(Instant::ofEpochMilli),
    snoozedUntil = snoozedUntil?.let(Instant::ofEpochMilli),
    personalNote = personalNote,
    metadataStatus = metadataStatus,
    surfaceHint = surfaceHint,
    expiresAt = expiresAt?.let(Instant::ofEpochMilli),
)

fun ReviewEventEntity.toModel() = com.afterlater.core.model.ReviewEvent(
    id = id,
    itemId = itemId,
    action = action,
    timestamp = Instant.ofEpochMilli(timestamp),
    snoozeUntil = snoozeUntil?.let(Instant::ofEpochMilli),
)
