package com.afterlater.data.reminder

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.afterlater.core.model.AppSettings
import com.afterlater.domain.reminder.ReminderSchedule
import com.afterlater.worker.ReminderWorker
import java.time.Clock
import java.time.Duration
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class WorkManagerReminderScheduler(
    context: Context,
    private val clock: Clock,
    private val zone: ZoneId,
    private val notifier: ReminderNotifier,
) : ReminderScheduler {

    private val appContext = context.applicationContext

    override fun ensureScheduled(settings: AppSettings) {
        if (!settings.remindersEnabled) {
            cancel()
            return
        }
        enqueue(settings, ExistingPeriodicWorkPolicy.KEEP)
    }

    override fun reschedule(settings: AppSettings) {
        if (!settings.remindersEnabled) {
            cancel()
            return
        }
        enqueue(settings, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE)
    }

    override fun cancel() {
        WorkManager.getInstance(appContext).cancelUniqueWork(UNIQUE_WORK_NAME)
        notifier.cancel()
    }

    private fun enqueue(settings: AppSettings, policy: ExistingPeriodicWorkPolicy) {
        val now = clock.instant()
        val next = ReminderSchedule.nextTrigger(
            now = now,
            zone = zone,
            hour = settings.reminderHour,
            minute = settings.reminderMinute,
        )
        val delay = Duration.between(now, next).toMillis().coerceAtLeast(0L)
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(ReminderWorker.TAG)
            .build()

        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            policy,
            request,
        )
    }

    companion object {
        const val UNIQUE_WORK_NAME = "afterlater-daily-reminder"
    }
}
