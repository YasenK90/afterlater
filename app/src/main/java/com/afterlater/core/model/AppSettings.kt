package com.afterlater.core.model

/** User-tweakable preferences. Persisted locally with DataStore. */
data class AppSettings(
    val dailyReviewLimit: Int = DEFAULT_DAILY_LIMIT,
    val theme: AppTheme = AppTheme.INK,
) {
    companion object {
        const val DEFAULT_DAILY_LIMIT = 3
        const val MIN_DAILY_LIMIT = 1
        const val MAX_DAILY_LIMIT = 3
    }
}

enum class AppTheme {
    /** Deep archive ink, the default identity. */
    INK,

    /** True black for OLED screens. */
    BLACKOUT,
}
