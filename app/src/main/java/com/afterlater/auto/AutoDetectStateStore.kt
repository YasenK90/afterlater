package com.afterlater.auto

import android.content.Context

class AutoDetectStateStore(context: Context) {

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun history(): PromptHistory = PromptHistory(
        lastPromptAtMillis = preferences.getLong(KEY_LAST_PROMPT_AT, NO_PROMPT).takeIf { it >= 0 },
        lastPromptUrl = preferences.getString(KEY_LAST_PROMPT_URL, null),
    )

    fun saveHistory(history: PromptHistory) {
        preferences.edit()
            .putLong(KEY_LAST_PROMPT_AT, history.lastPromptAtMillis ?: NO_PROMPT)
            .putString(KEY_LAST_PROMPT_URL, history.lastPromptUrl)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "afterlater_auto_detect"
        const val KEY_LAST_PROMPT_AT = "last_prompt_at"
        const val KEY_LAST_PROMPT_URL = "last_prompt_url"
        const val NO_PROMPT = -1L
    }
}
