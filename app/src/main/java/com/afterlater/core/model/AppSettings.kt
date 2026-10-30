package com.afterlater.core.model

/** User-tweakable preferences. Persisted locally with DataStore. */
data class AppSettings(
    val dailyReviewLimit: Int = DEFAULT_DAILY_LIMIT,
    val theme: AppTheme = AppTheme.INK,
    val remindersEnabled: Boolean = false,
    val reminderHour: Int = DEFAULT_REMINDER_HOUR,
    val reminderMinute: Int = DEFAULT_REMINDER_MINUTE,
    val tiktokAutoDetectEnabled: Boolean = false,
    val clipboardCaptureEnabled: Boolean = false,
    val onboardingDone: Boolean = false,
) {
    companion object {
        const val DEFAULT_DAILY_LIMIT = 3
        const val MIN_DAILY_LIMIT = 1
        const val MAX_DAILY_LIMIT = 5
        const val DEFAULT_REMINDER_HOUR = 8
        const val DEFAULT_REMINDER_MINUTE = 0
    }
}

enum class AppTheme {
    /** Deep archive ink, the default identity. */
    INK,

    /** True black for OLED screens. */
    BLACKOUT,
}
