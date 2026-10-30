package com.afterlater.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.afterlater.core.model.AppSettings
import com.afterlater.core.model.AppTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "afterlater_settings")

class SettingsRepository(private val context: Context) {

    val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw error
        }
        .map { prefs ->
            AppSettings(
                dailyReviewLimit = (prefs[DAILY_LIMIT] ?: AppSettings.DEFAULT_DAILY_LIMIT)
                    .coerceIn(AppSettings.MIN_DAILY_LIMIT, AppSettings.MAX_DAILY_LIMIT),
                theme = prefs[THEME]?.let { name ->
                    AppTheme.entries.firstOrNull { it.name == name }
                } ?: AppTheme.INK,
                remindersEnabled = prefs[REMINDERS_ENABLED] ?: false,
                reminderHour = (prefs[REMINDER_HOUR] ?: AppSettings.DEFAULT_REMINDER_HOUR)
                    .coerceIn(0, 23),
                reminderMinute = (prefs[REMINDER_MINUTE] ?: AppSettings.DEFAULT_REMINDER_MINUTE)
                    .coerceIn(0, 59),
                tiktokAutoDetectEnabled = prefs[TIKTOK_AUTO_DETECT_ENABLED] ?: false,
                clipboardCaptureEnabled = prefs[CLIPBOARD_CAPTURE_ENABLED] ?: false,
            )
        }

    suspend fun setDailyReviewLimit(limit: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[DAILY_LIMIT] = limit.coerceIn(AppSettings.MIN_DAILY_LIMIT, AppSettings.MAX_DAILY_LIMIT)
        }
    }

    suspend fun setTheme(theme: AppTheme) {
        context.settingsDataStore.edit { prefs -> prefs[THEME] = theme.name }
    }

    suspend fun setRemindersEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[REMINDERS_ENABLED] = enabled }
    }

    suspend fun setReminderTime(hour: Int, minute: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[REMINDER_HOUR] = hour.coerceIn(0, 23)
            prefs[REMINDER_MINUTE] = minute.coerceIn(0, 59)
        }
    }

    suspend fun setTikTokAutoDetectEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[TIKTOK_AUTO_DETECT_ENABLED] = enabled }
    }

    suspend fun setClipboardCaptureEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[CLIPBOARD_CAPTURE_ENABLED] = enabled }
    }

    suspend fun lastReminderNotifiedOn(): String? =
        context.settingsDataStore.data.first()[LAST_REMINDER_NOTIFIED_ON]

    suspend fun setLastReminderNotifiedOn(date: String) {
        context.settingsDataStore.edit { prefs -> prefs[LAST_REMINDER_NOTIFIED_ON] = date }
    }

    suspend fun currentSettings(): AppSettings = settings.first()

    private companion object {
        val DAILY_LIMIT = intPreferencesKey("daily_review_limit")
        val THEME = stringPreferencesKey("theme")
        val REMINDERS_ENABLED = booleanPreferencesKey("reminders_enabled")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val TIKTOK_AUTO_DETECT_ENABLED = booleanPreferencesKey("tiktok_auto_detect_enabled")
        val CLIPBOARD_CAPTURE_ENABLED = booleanPreferencesKey("clipboard_capture_enabled")
        val LAST_REMINDER_NOTIFIED_ON = stringPreferencesKey("last_reminder_notified_on")
    }
}
