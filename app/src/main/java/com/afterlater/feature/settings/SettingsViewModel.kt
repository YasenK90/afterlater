package com.afterlater.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.afterlater.AfterLaterApplication
import com.afterlater.core.model.AppSettings
import com.afterlater.core.model.AppTheme
import com.afterlater.data.export.ArchiveExportManager
import com.afterlater.data.repository.SavedItemRepository
import com.afterlater.data.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val totalItems: Int = 0,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    repository: SavedItemRepository,
    private val exportManager: ArchiveExportManager,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        repository.observeTotalCount(),
    ) { settings, total ->
        SettingsUiState(settings = settings, totalItems = total)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private val _exporting = MutableStateFlow(false)
    val exporting: StateFlow<Boolean> = _exporting.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun setDailyLimit(limit: Int) {
        viewModelScope.launch {
            settingsRepository.setDailyReviewLimit(limit)
        }
    }

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch {
            settingsRepository.setTheme(theme)
        }
    }

    fun exportTo(uri: Uri) {
        if (_exporting.value) return
        viewModelScope.launch {
            _exporting.value = true
            try {
                val count = exportManager.exportTo(uri)
                _message.value = "Exported $count ${if (count == 1) "entry" else "entries"}."
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _message.value = "Export failed. The selected file may be incomplete."
            } finally {
                _exporting.value = false
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AfterLaterApplication
                SettingsViewModel(
                    settingsRepository = app.container.settingsRepository,
                    repository = app.container.savedItemRepository,
                    exportManager = app.container.archiveExportManager,
                )
            }
        }
    }
}
