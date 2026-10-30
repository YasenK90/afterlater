package com.afterlater.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.afterlater.AfterLaterApplication
import com.afterlater.data.reminder.areReminderNotificationsAllowed
import com.afterlater.domain.reminder.ReminderPolicy
import com.afterlater.domain.reminder.ReminderSchedule
import com.afterlater.domain.review.ReviewScheduler
import kotlinx.coroutines.CancellationException
import java.time.LocalDate

class ReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as AfterLaterApplication).container
        val repository = container.savedItemRepository

        return try {
            val settings = container.settingsRepository.currentSettings()
            if (!settings.remindersEnabled) return Result.success()
            if (!areReminderNotificationsAllowed(applicationContext)) return Result.success()

            val now = container.clock.instant()
            val today = ReminderSchedule.localDate(now, container.reminderZone)
            val lastNotifiedOn = container.settingsRepository.lastReminderNotifiedOn()
                ?.let { stored -> runCatching { LocalDate.parse(stored) }.getOrNull() }
            val candidates = repository.findDueCandidates(
                nowMillis = now.toEpochMilli(),
                limit = settings.dailyReviewLimit * 4,
            )
            val batch = ReviewScheduler.selectDailyBatch(
                candidates = candidates,
                limit = settings.dailyReviewLimit,
                now = now,
            )
            if (!ReminderPolicy.shouldNotify(
                    enabled = settings.remindersEnabled,
                    eligibleCount = batch.size,
                    today = today,
                    lastNotifiedOn = lastNotifiedOn,
                )
            ) {
                return Result.success()
            }

            if (container.reminderNotifier.notify(batch.size)) {
                container.settingsRepository.setLastReminderNotifiedOn(today.toString())
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The periodic schedule retries anyway on the next day, so a run that
            // keeps failing must not spin with backoff forever.
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val TAG = "daily-reminder"
        private const val MAX_ATTEMPTS = 3
    }
}
