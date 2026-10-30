package com.afterlater.data.export

import com.afterlater.core.model.ArchiveSnapshot
import com.afterlater.core.model.ItemStatus
import com.afterlater.core.model.MetadataStatus
import com.afterlater.core.model.ReviewAction
import com.afterlater.core.model.ReviewEvent
import com.afterlater.core.model.SavedItem
import com.afterlater.core.model.SurfaceHint
import java.net.URI
import java.net.URISyntaxException
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class ArchiveBackup(
    val exportedAt: Instant,
    val items: List<SavedItem>,
    val events: List<ReviewEvent>,
)

enum class BackupFailureKind {
    EMPTY,
    CORRUPTED,
    NOT_AFTERLATER,
    UNSUPPORTED_VERSION,
    INVALID_DATA,
}

sealed interface BackupDecodeResult {
    data class Success(val backup: ArchiveBackup) : BackupDecodeResult
    data class Failure(
        val kind: BackupFailureKind,
        val message: String,
    ) : BackupDecodeResult
}

object ArchiveBackupCodec {

    const val FORMAT = "afterlater.archive"
    const val VERSION = 1

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(snapshot: ArchiveSnapshot, exportedAt: Instant): String {
        val backup = BackupFile(
            format = FORMAT,
            version = VERSION,
            exportedAt = exportedAt.toString(),
            items = snapshot.items.map { it.toBackupItem() },
            reviewEvents = snapshot.events.map { it.toBackupEvent() },
        )
        return json.encodeToString(backup)
    }

    fun decode(raw: String): BackupDecodeResult {
        val text = raw.removePrefix("\uFEFF")
        if (text.isBlank()) {
            return BackupDecodeResult.Failure(
                kind = BackupFailureKind.EMPTY,
                message = "The selected file is empty.",
            )
        }

        val file = try {
            json.decodeFromString<BackupFile>(text)
        } catch (_: SerializationException) {
            return BackupDecodeResult.Failure(
                kind = BackupFailureKind.CORRUPTED,
                message = "The backup file is damaged or incomplete.",
            )
        } catch (_: IllegalArgumentException) {
            return BackupDecodeResult.Failure(
                kind = BackupFailureKind.CORRUPTED,
                message = "The backup file is damaged or incomplete.",
            )
        }

        if (file.format == null && file.version == null) {
            return BackupDecodeResult.Failure(
                kind = BackupFailureKind.NOT_AFTERLATER,
                message = "This file is not an AFTERLATER backup.",
            )
        }
        if (file.format != FORMAT) {
            return BackupDecodeResult.Failure(
                kind = BackupFailureKind.NOT_AFTERLATER,
                message = "This file is not an AFTERLATER backup.",
            )
        }
        val version = file.version
            ?: return BackupDecodeResult.Failure(
                kind = BackupFailureKind.INVALID_DATA,
                message = "The backup has no version.",
            )
        if (version > VERSION) {
            return BackupDecodeResult.Failure(
                kind = BackupFailureKind.UNSUPPORTED_VERSION,
                message = "This backup uses version $version. This app supports version $VERSION.",
            )
        }
        if (version < 1) {
            return BackupDecodeResult.Failure(
                kind = BackupFailureKind.INVALID_DATA,
                message = "The backup version is not supported.",
            )
        }

        val exportedAt = try {
            parseInstant(file.exportedAt, "export date")
        } catch (error: BackupValidationException) {
            return BackupDecodeResult.Failure(BackupFailureKind.INVALID_DATA, error.message ?: "The backup is invalid.")
        }
        val rawItems = file.items
            ?: return BackupDecodeResult.Failure(
                kind = BackupFailureKind.INVALID_DATA,
                message = "The backup has no item list.",
            )

        val items = try {
            rawItems.mapIndexed { index, item -> item.toModel(index) }
        } catch (error: BackupValidationException) {
            return BackupDecodeResult.Failure(
                kind = BackupFailureKind.INVALID_DATA,
                message = error.message ?: "The backup contains an invalid entry.",
            )
        }

        val events = try {
            file.reviewEvents.orEmpty().mapIndexed { index, event -> event.toModel(index) }
        } catch (error: BackupValidationException) {
            return BackupDecodeResult.Failure(
                kind = BackupFailureKind.INVALID_DATA,
                message = error.message ?: "The backup contains an invalid review event.",
            )
        }

        return BackupDecodeResult.Success(
            ArchiveBackup(exportedAt = exportedAt, items = items, events = events),
        )
    }

    private fun SavedItem.toBackupItem() = BackupItem(
        id = id,
        originalUrl = originalUrl,
        canonicalUrl = canonicalUrl,
        title = title,
        description = description,
        domain = domain,
        thumbnailUrl = thumbnailUrl,
        savedAt = savedAt.toString(),
        updatedAt = updatedAt.toString(),
        status = status.name,
        lastReviewedAt = lastReviewedAt?.toString(),
        nextReviewAt = nextReviewAt?.toString(),
        snoozedUntil = snoozedUntil?.toString(),
        personalNote = personalNote,
        metadataStatus = metadataStatus.name,
        surfaceHint = surfaceHint.name,
        expiresAt = expiresAt?.toString(),
    )

    private fun ReviewEvent.toBackupEvent() = BackupEvent(
        id = id,
        itemId = itemId,
        action = action.name,
        timestamp = timestamp.toString(),
        snoozeUntil = snoozeUntil?.toString(),
    )

    private fun BackupItem.toModel(index: Int): SavedItem {
        val position = index + 1
        try {
            requireNotBlank(id, "id", position)
            requireNotBlank(originalUrl, "original URL", position)
            requireNotBlank(canonicalUrl, "canonical URL", position)
            requireNotBlank(domain, "domain", position)
            validateWebUrl(originalUrl, "original URL", position)
            validateWebUrl(canonicalUrl, "canonical URL", position)
            val saved = parseInstant(savedAt, "saved date", position)
            val updated = parseInstant(updatedAt, "updated date", position)
            return SavedItem(
                id = id,
                originalUrl = originalUrl,
                canonicalUrl = canonicalUrl,
                title = title,
                description = description,
                domain = domain,
                thumbnailUrl = thumbnailUrl,
                savedAt = saved,
                updatedAt = updated,
                status = parseEnum<ItemStatus>(status, "status", position),
                lastReviewedAt = parseOptionalInstant(lastReviewedAt, "last reviewed date", position),
                nextReviewAt = parseOptionalInstant(nextReviewAt, "next review date", position),
                snoozedUntil = parseOptionalInstant(snoozedUntil, "snooze date", position),
                personalNote = personalNote,
                metadataStatus = parseEnum<MetadataStatus>(metadataStatus, "metadata status", position),
                surfaceHint = surfaceHint
                    ?.let { parseEnum<SurfaceHint>(it, "surface hint", position) }
                    ?: SurfaceHint.QUEUE,
                expiresAt = parseOptionalInstant(expiresAt, "expiry date", position),
            )
        } catch (error: BackupValidationException) {
            throw error
        } catch (error: Exception) {
            throw BackupValidationException("Backup entry $position is invalid: ${error.message ?: "unknown error"}")
        }
    }

    private fun BackupEvent.toModel(index: Int): ReviewEvent {
        val position = index + 1
        try {
            requireNotBlank(itemId, "item id", position, "Review event")
            return ReviewEvent(
                id = id,
                itemId = itemId,
                action = parseEnum<ReviewAction>(action, "action", position, "Review event"),
                timestamp = parseInstant(timestamp, "timestamp", position, "Review event"),
                snoozeUntil = parseOptionalInstant(snoozeUntil, "snooze date", position, "Review event"),
            )
        } catch (error: BackupValidationException) {
            throw error
        } catch (error: Exception) {
            throw BackupValidationException("Review event $position is invalid: ${error.message ?: "unknown error"}")
        }
    }

    private fun requireNotBlank(
        value: String,
        label: String,
        position: Int,
        prefix: String = "Backup entry",
    ) {
        if (value.isBlank()) {
            throw BackupValidationException("$prefix $position has no $label.")
        }
    }

    private fun validateWebUrl(value: String, label: String, position: Int) {
        val uri = try {
            URI(value)
        } catch (_: URISyntaxException) {
            throw BackupValidationException("Backup entry $position has an invalid $label.")
        }
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") {
            throw BackupValidationException("Backup entry $position has an invalid $label.")
        }
        if (uri.host.isNullOrBlank()) {
            throw BackupValidationException("Backup entry $position has an invalid $label.")
        }
    }

    private fun parseInstant(
        value: String?,
        label: String,
        position: Int = 0,
        prefix: String = "Backup entry",
    ): Instant {
        if (value.isNullOrBlank()) {
            val message = if (position == 0) "The backup has no $label." else "$prefix $position has no $label."
            throw BackupValidationException(message)
        }
        return try {
            Instant.parse(value)
        } catch (_: DateTimeParseException) {
            val message = if (position == 0) {
                "The backup has an invalid $label."
            } else {
                "$prefix $position has an invalid $label."
            }
            throw BackupValidationException(message)
        }
    }

    private fun parseOptionalInstant(
        value: String?,
        label: String,
        position: Int,
        prefix: String = "Backup entry",
    ): Instant? = if (value == null) null else parseInstant(value, label, position, prefix)

    private inline fun <reified T : Enum<T>> parseEnum(
        value: String,
        label: String,
        position: Int,
        prefix: String = "Backup entry",
    ): T = try {
        enumValueOf<T>(value)
    } catch (_: IllegalArgumentException) {
        throw BackupValidationException("$prefix $position has an invalid $label.")
    }
}

private class BackupValidationException(message: String) : Exception(message)

@Serializable
private data class BackupFile(
    val format: String? = null,
    val version: Int? = null,
    val exportedAt: String? = null,
    val items: List<BackupItem>? = null,
    val reviewEvents: List<BackupEvent>? = null,
)

@Serializable
private data class BackupItem(
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
    val surfaceHint: String? = null,
    val expiresAt: String? = null,
)

@Serializable
private data class BackupEvent(
    val id: Long,
    val itemId: String,
    val action: String,
    val timestamp: String,
    val snoozeUntil: String?,
)
