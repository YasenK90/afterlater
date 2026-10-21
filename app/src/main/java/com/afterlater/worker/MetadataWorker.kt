package com.afterlater.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.afterlater.AfterLaterApplication
import com.afterlater.core.model.MetadataStatus
import com.afterlater.data.metadata.MetadataResult
import kotlinx.coroutines.CancellationException

class MetadataWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val itemId = inputData.getString(KEY_ITEM_ID) ?: return Result.failure()
        val container = (applicationContext as AfterLaterApplication).container
        val repository = container.savedItemRepository

        return try {
            val item = repository.getItem(itemId) ?: return Result.success()
            if (item.metadataStatus == MetadataStatus.SUCCESS) return Result.success()

            when (val result = container.metadataExtractor.extract(item.originalUrl)) {
                is MetadataResult.Success -> {
                    repository.applyMetadata(itemId, result.metadata)
                    Result.success()
                }

                is MetadataResult.Unavailable -> {
                    if (result.retryable && runAttemptCount < MAX_ATTEMPTS) {
                        Result.retry()
                    } else {
                        repository.markMetadataFailed(itemId)
                        Result.success()
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            repository.markMetadataFailed(itemId)
            Result.success()
        }
    }

    companion object {
        const val TAG = "metadata"
        const val KEY_ITEM_ID = "item_id"
        private const val MAX_ATTEMPTS = 2
    }
}
