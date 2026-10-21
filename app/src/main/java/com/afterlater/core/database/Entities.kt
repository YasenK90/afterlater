package com.afterlater.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.afterlater.core.model.ItemStatus
import com.afterlater.core.model.MetadataStatus
import com.afterlater.core.model.ReviewAction
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
)

fun ReviewEventEntity.toModel() = com.afterlater.core.model.ReviewEvent(
    id = id,
    itemId = itemId,
    action = action,
    timestamp = Instant.ofEpochMilli(timestamp),
    snoozeUntil = snoozeUntil?.let(Instant::ofEpochMilli),
)
