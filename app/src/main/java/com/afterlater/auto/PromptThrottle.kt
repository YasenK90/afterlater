package com.afterlater.auto

data class PromptHistory(
    val lastPromptAtMillis: Long? = null,
    val lastPromptUrl: String? = null,
)

class PromptThrottle(
    private val sameVideoCooldownMillis: Long = DEFAULT_SAME_VIDEO_COOLDOWN_MILLIS,
    private val globalCooldownMillis: Long = DEFAULT_GLOBAL_COOLDOWN_MILLIS,
) {

    fun canPrompt(
        history: PromptHistory,
        canonicalUrl: String,
        nowMillis: Long,
    ): Boolean {
        val lastPromptAt = history.lastPromptAtMillis ?: return true
        val elapsed = nowMillis - lastPromptAt
        if (elapsed < 0) return false
        if (history.lastPromptUrl == canonicalUrl) {
            return elapsed >= sameVideoCooldownMillis
        }
        return elapsed >= globalCooldownMillis
    }

    fun record(
        canonicalUrl: String,
        nowMillis: Long,
    ): PromptHistory = PromptHistory(
        lastPromptAtMillis = nowMillis,
        lastPromptUrl = canonicalUrl,
    )

    companion object {
        const val DEFAULT_SAME_VIDEO_COOLDOWN_MILLIS = 10 * 60 * 1_000L
        const val DEFAULT_GLOBAL_COOLDOWN_MILLIS = 60 * 1_000L
    }
}
