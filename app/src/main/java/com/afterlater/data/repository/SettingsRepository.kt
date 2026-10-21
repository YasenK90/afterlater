package com.afterlater.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.afterlater.core.model.AppSettings
import com.afterlater.core.model.AppTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
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

    private companion object {
        val DAILY_LIMIT = intPreferencesKey("daily_review_limit")
        val THEME = stringPreferencesKey("theme")
    }
}
