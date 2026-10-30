package com.afterlater.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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

/** Local automatic-backup configuration, stored alongside the other preferences. */
data class AutoBackupState(
    val enabled: Boolean = false,
    val dirUri: String? = null,
    val folderName: String? = null,
    val lastBackupAtMillis: Long? = null,
)

class SettingsRepository(private val context: Context) {

    val autoBackup: Flow<AutoBackupState> = context.settingsDataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw error
        }
        .map { prefs ->
            AutoBackupState(
                enabled = prefs[AUTO_BACKUP_ENABLED] ?: false,
                dirUri = prefs[AUTO_BACKUP_DIR],
                folderName = prefs[AUTO_BACKUP_FOLDER_NAME],
                lastBackupAtMillis = prefs[LAST_AUTO_BACKUP_AT],
            )
        }

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
                onboardingDone = prefs[ONBOARDING_DONE] ?: false,
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

    suspend fun autoBackupState(): AutoBackupState = autoBackup.first()

    /** Picks a folder and switches automatic backup on in one atomic write. */
    suspend fun chooseAutoBackupFolder(dirUri: String, folderName: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[AUTO_BACKUP_DIR] = dirUri
            prefs[AUTO_BACKUP_FOLDER_NAME] = folderName
            prefs[AUTO_BACKUP_ENABLED] = true
        }
    }

    suspend fun setAutoBackupEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[AUTO_BACKUP_ENABLED] = enabled }
    }

    suspend fun setLastAutoBackupAt(millis: Long) {
        context.settingsDataStore.edit { prefs -> prefs[LAST_AUTO_BACKUP_AT] = millis }
    }

    suspend fun completeOnboarding() {
        context.settingsDataStore.edit { prefs -> prefs[ONBOARDING_DONE] = true }
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
        val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        val AUTO_BACKUP_DIR = stringPreferencesKey("auto_backup_dir")
        val AUTO_BACKUP_FOLDER_NAME = stringPreferencesKey("auto_backup_folder_name")
        val LAST_AUTO_BACKUP_AT = longPreferencesKey("last_auto_backup_at")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }
}
