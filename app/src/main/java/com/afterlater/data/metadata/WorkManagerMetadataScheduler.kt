package com.afterlater.data.metadata

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.afterlater.worker.MetadataWorker
import java.util.concurrent.TimeUnit

class WorkManagerMetadataScheduler(private val context: Context) : MetadataScheduler {

    override fun enqueue(itemId: String, replaceExisting: Boolean) {
        val request = OneTimeWorkRequestBuilder<MetadataWorker>()
            .setInputData(workDataOf(MetadataWorker.KEY_ITEM_ID to itemId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(MetadataWorker.TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueName(itemId),
            if (replaceExisting) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private fun uniqueName(itemId: String): String = "metadata-$itemId"
}
