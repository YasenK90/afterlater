package com.afterlater.data.reminder

import com.afterlater.core.model.AppSettings

interface ReminderScheduler {
    fun ensureScheduled(settings: AppSettings)
    fun reschedule(settings: AppSettings)
    fun cancel()
}
