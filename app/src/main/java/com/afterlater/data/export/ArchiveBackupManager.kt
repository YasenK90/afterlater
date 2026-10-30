package com.afterlater.data.export

import android.content.Context
import android.net.Uri
import com.afterlater.data.repository.SavedItemRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.Clock
import java.time.Instant

interface ArchiveBackupService {
    suspend fun exportTo(uri: Uri): Int
    suspend fun inspectBackup(uri: Uri): BackupInspection
    suspend fun restore(preview: RestorePreview): RestoreOutcome
}

sealed interface BackupInspection {
    data class Ready(val preview: RestorePreview) : BackupInspection
    data class Invalid(val message: String) : BackupInspection
}

data class RestorePreview(
    val exportedAt: Instant,
    val backupItemCount: Int,
    val newItemCount: Int,
    val duplicateCount: Int,
    val newEventCount: Int,
    internal val plan: ArchiveRestorePlan,
)

sealed interface RestoreOutcome {
    data class Success(
        val insertedItems: Int,
        val skippedItems: Int,
        val insertedEvents: Int,
    ) : RestoreOutcome

    data class Failure(val message: String) : RestoreOutcome
}

class ArchiveBackupManager(
    private val context: Context,
    private val repository: SavedItemRepository,
    private val clock: Clock = Clock.systemUTC(),
) : ArchiveBackupService {

    override suspend fun exportTo(uri: Uri): Int = withContext(Dispatchers.IO) {
        val snapshot = repository.exportSnapshot()
        val payload = ArchiveBackupCodec.encode(snapshot, clock.instant())
        val stream = context.contentResolver.openOutputStream(uri)
            ?: throw IOException("The selected document could not be opened.")
        stream.use { output ->
            output.write(payload.toByteArray(Charsets.UTF_8))
            output.flush()
        }
        snapshot.items.size
    }

    override suspend fun inspectBackup(uri: Uri): BackupInspection = withContext(Dispatchers.IO) {
        val raw = try {
            readText(uri)
        } catch (tooLarge: BackupTooLargeException) {
            return@withContext BackupInspection.Invalid(
                tooLarge.message ?: "The selected backup is too large.",
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return@withContext BackupInspection.Invalid("The selected backup could not be read.")
        }
        when (val decoded = ArchiveBackupCodec.decode(raw)) {
            is BackupDecodeResult.Failure -> BackupInspection.Invalid(decoded.message)
            is BackupDecodeResult.Success -> {
                val existing = try {
                    repository.exportSnapshot()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    return@withContext BackupInspection.Invalid(
                        "The backup could not be checked against your archive.",
                    )
                }
                val plan = ArchiveRestorePlanner.plan(decoded.backup, existing)
                BackupInspection.Ready(
                    RestorePreview(
                        exportedAt = plan.backupExportedAt,
                        backupItemCount = plan.backupItemCount,
                        newItemCount = plan.newItemCount,
                        duplicateCount = plan.duplicateCount,
                        newEventCount = plan.newEvents.size,
                        plan = plan,
                    ),
                )
            }
        }
    }

    override suspend fun restore(preview: RestorePreview): RestoreOutcome = withContext(Dispatchers.IO) {
        try {
            val result = repository.restoreBackup(preview.plan.newItems, preview.plan.newEvents)
            RestoreOutcome.Success(
                insertedItems = result.insertedItems,
                skippedItems = result.skippedItems,
                insertedEvents = result.insertedEvents,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            RestoreOutcome.Failure("Restore failed. No existing entries were changed.")
        }
    }

    private fun readText(uri: Uri): String {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("The selected backup could not be opened.")
        stream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                total += read
                if (total > MAX_BACKUP_BYTES) {
                    throw BackupTooLargeException()
                }
                output.write(buffer, 0, read)
            }
            return String(output.toByteArray(), Charsets.UTF_8)
        }
    }

    private companion object {
        const val MAX_BACKUP_BYTES = 25 * 1024 * 1024
    }
}

private class BackupTooLargeException : IOException("The selected backup is too large.")
