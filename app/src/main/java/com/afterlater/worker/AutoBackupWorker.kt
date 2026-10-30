package com.afterlater.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.afterlater.AfterLaterApplication
import com.afterlater.data.export.AutoBackupOutcome
import kotlinx.coroutines.CancellationException

/**
 * Overwrites the automatic backup file in the user-chosen folder. Runs weekly
 * while the toggle is on; the schedule is owned by [AutoBackupManager].
 */
class AutoBackupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as AfterLaterApplication).container

        return try {
            if (!container.settingsRepository.autoBackupState().enabled) return Result.success()

            when (val outcome = container.autoBackupManager.runBackup()) {
                is AutoBackupOutcome.BackedUp -> Result.success()
                is AutoBackupOutcome.Skipped -> Result.success()
                is AutoBackupOutcome.Failed -> {
                    // A transient folder hiccup retries with backoff; a revoked
                    // grant never recovers by itself, so stop after a few tries
                    // and let the next weekly period start fresh.
                    if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val TAG = "auto-backup"
        private const val MAX_ATTEMPTS = 3
    }
}
