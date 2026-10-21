package com.afterlater.data.export

import android.content.Context
import android.net.Uri
import com.afterlater.core.model.ArchiveSnapshot
import com.afterlater.data.repository.SavedItemRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.Instant

@Serializable
private data class ExportSnapshot(
    val format: String,
    val version: Int,
    val exportedAt: String,
    val items: List<ExportItem>,
    val reviewEvents: List<ExportEvent>,
)

@Serializable
private data class ExportItem(
    val id: String,
    val originalUrl: String,
    val canonicalUrl: String,
    val title: String?,
    val description: String?,
    val domain: String,
    val thumbnailUrl: String?,
    val savedAt: String,
    val updatedAt: String,
    val status: String,
    val lastReviewedAt: String?,
    val nextReviewAt: String?,
    val snoozedUntil: String?,
    val personalNote: String?,
    val metadataStatus: String,
)

@Serializable
private data class ExportEvent(
    val id: Long,
    val itemId: String,
    val action: String,
    val timestamp: String,
    val snoozeUntil: String?,
)

/**
 * Writes a portable JSON copy of the archive into a URI chosen through the
 * system document picker. No storage permission is required or requested.
 */
class ArchiveExportManager(
    private val context: Context,
    private val repository: SavedItemRepository,
) {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    suspend fun exportTo(uri: Uri): Int = withContext(Dispatchers.IO) {
        val snapshot = repository.exportSnapshot()
        val payload = json.encodeToString(snapshot.toExport(Instant.now()))

        val stream = context.contentResolver.openOutputStream(uri)
            ?: throw IOException("The selected document could not be opened.")
        stream.use { output ->
            output.write(payload.toByteArray(Charsets.UTF_8))
            output.flush()
        }
        snapshot.items.size
    }

    private fun ArchiveSnapshot.toExport(now: Instant): ExportSnapshot = ExportSnapshot(
        format = "afterlater.archive",
        version = 1,
        exportedAt = now.toString(),
        items = items.map { item ->
            ExportItem(
                id = item.id,
                originalUrl = item.originalUrl,
                canonicalUrl = item.canonicalUrl,
                title = item.title,
                description = item.description,
                domain = item.domain,
                thumbnailUrl = item.thumbnailUrl,
                savedAt = item.savedAt.toString(),
                updatedAt = item.updatedAt.toString(),
                status = item.status.name,
                lastReviewedAt = item.lastReviewedAt?.toString(),
                nextReviewAt = item.nextReviewAt?.toString(),
                snoozedUntil = item.snoozedUntil?.toString(),
                personalNote = item.personalNote,
                metadataStatus = item.metadataStatus.name,
            )
        },
        reviewEvents = events.map { event ->
            ExportEvent(
                id = event.id,
                itemId = event.itemId,
                action = event.action.name,
                timestamp = event.timestamp.toString(),
                snoozeUntil = event.snoozeUntil?.toString(),
            )
        },
    )
}
