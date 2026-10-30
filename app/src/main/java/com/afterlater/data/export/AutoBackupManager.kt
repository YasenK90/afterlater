package com.afterlater.data.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.afterlater.data.repository.SettingsRepository
import com.afterlater.worker.AutoBackupWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.io.IOException
import java.time.Clock
import java.util.concurrent.TimeUnit

sealed interface AutoBackupOutcome {
    data class BackedUp(val itemCount: Int, val target: Uri) : AutoBackupOutcome
    data object Skipped : AutoBackupOutcome
    data class Failed(val message: String) : AutoBackupOutcome
}

/**
 * Weekly local backup into a folder the user picked once with the system
 * picker. The folder URI permission is persisted, so a WorkManager job can
 * overwrite the same file — afterlater-archive-auto.json — without any
 * prompt. Nothing is ever uploaded; the file lives wherever the user chose
 * (which may be a synced folder, at their own discretion).
 */
class AutoBackupManager(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val backupService: ArchiveBackupService,
    private val clock: Clock,
) {

    /**
     * Persists the read/write grant for a tree URI picked with
     * [androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree]
     * and resolves the folder's display name for the settings screen.
     */
    fun handleSelection(uri: Uri): Boolean {
        val persisted = runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }.isSuccess
        if (!persisted) return false
        return true
    }

    fun folderDisplayName(uri: Uri): String? = runCatching {
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(
            uri,
            DocumentsContract.getTreeDocumentId(uri),
        )
        context.contentResolver.query(
            documentUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment

    suspend fun runBackup(): AutoBackupOutcome {
        val state = settingsRepository.autoBackupState()
        val dirUri = state.dirUri?.let(Uri::parse)
            ?: return AutoBackupOutcome.Skipped
        val target = resolveTarget(dirUri)
            ?: return AutoBackupOutcome.Failed("The chosen backup folder is no longer available.")

        return try {
            val count = backupService.exportTo(target)
            settingsRepository.setLastAutoBackupAt(clock.instant().toEpochMilli())
            AutoBackupOutcome.BackedUp(count, target)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: SecurityException) {
            AutoBackupOutcome.Failed("The folder permission was revoked.")
        } catch (error: IOException) {
            AutoBackupOutcome.Failed("The backup file could not be written.")
        }
    }

    /** Keeps the periodic job in step with the stored preference. */
    suspend fun syncSchedule() {
        if (settingsRepository.autoBackupState().enabled) {
            schedule()
        } else {
            cancel()
        }
    }

    fun schedule() {
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresStorageNotLow(true)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(AutoBackupWorker.TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    /**
     * Finds afterlater-archive-auto.json in the picked tree so every run
     * overwrites one file instead of accumulating copies; creates it on the
     * first run.
     */
    private fun resolveTarget(treeUri: Uri): Uri? = runCatching {
        val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocumentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        )
        val existing = context.contentResolver.query(childrenUri, projection, null, null, null)
            ?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == FILE_NAME) {
                        return@use cursor.getString(idIndex)
                    }
                }
                null
            }

        existing?.let { documentId ->
            return DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        }

        DocumentsContract.createDocument(
            context.contentResolver,
            DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocumentId),
            "application/json",
            FILE_NAME,
        )
    }.getOrNull()

    companion object {
        const val UNIQUE_WORK_NAME = "afterlater-auto-backup"
        const val FILE_NAME = "afterlater-archive-auto.json"
    }
}
