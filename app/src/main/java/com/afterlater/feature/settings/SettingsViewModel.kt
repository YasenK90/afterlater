package com.afterlater.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.afterlater.AfterLaterApplication
import com.afterlater.core.model.AppSettings
import com.afterlater.core.model.AppTheme
import com.afterlater.data.export.ArchiveBackupService
import com.afterlater.data.export.AutoBackupManager
import com.afterlater.data.export.BackupInspection
import com.afterlater.data.export.RestoreOutcome
import com.afterlater.data.export.RestorePreview
import com.afterlater.data.reminder.ReminderScheduler
import com.afterlater.data.repository.AutoBackupState
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
    val autoBackup: AutoBackupState = AutoBackupState(),
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    repository: SavedItemRepository,
    private val backupService: ArchiveBackupService,
    private val reminderScheduler: ReminderScheduler,
    private val autoBackupManager: AutoBackupManager,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        repository.observeTotalCount(),
        settingsRepository.autoBackup,
    ) { settings, total, autoBackup ->
        SettingsUiState(settings = settings, totalItems = total, autoBackup = autoBackup)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private val _exporting = MutableStateFlow(false)
    val exporting: StateFlow<Boolean> = _exporting.asStateFlow()

    private val _inspecting = MutableStateFlow(false)
    val inspecting: StateFlow<Boolean> = _inspecting.asStateFlow()

    private val _restoring = MutableStateFlow(false)
    val restoring: StateFlow<Boolean> = _restoring.asStateFlow()

    private val _pendingRestore = MutableStateFlow<RestorePreview?>(null)
    val pendingRestore: StateFlow<RestorePreview?> = _pendingRestore.asStateFlow()

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

    fun setRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsRepository.setRemindersEnabled(enabled)
                syncReminderSchedule()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _message.value = "The reminder schedule could not be updated."
            }
        }
    }

    fun setReminderTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            try {
                settingsRepository.setReminderTime(hour, minute)
                syncReminderSchedule()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _message.value = "The reminder schedule could not be updated."
            }
        }
    }

    fun setTikTokAutoDetectEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setTikTokAutoDetectEnabled(enabled)
        }
    }

    fun setClipboardCaptureEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setClipboardCaptureEnabled(enabled)
        }
    }

    fun chooseAutoBackupFolder(uri: Uri) {
        viewModelScope.launch {
            try {
                val folderName = withContext(Dispatchers.IO) {
                    if (!autoBackupManager.handleSelection(uri)) {
                        null
                    } else {
                        autoBackupManager.folderDisplayName(uri) ?: "Chosen folder"
                    }
                }
                if (folderName == null) {
                    _message.value = "The folder permission was not granted. Try again."
                    return@launch
                }
                settingsRepository.chooseAutoBackupFolder(uri.toString(), folderName)
                autoBackupManager.schedule()
                _message.value = "Automatic backup is on for \"$folderName\"."
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _message.value = "The folder could not be used for backups."
            }
        }
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                val state = settingsRepository.autoBackupState()
                if (enabled && state.dirUri == null) {
                    _message.value = "Choose a folder for the backup first."
                    return@launch
                }
                settingsRepository.setAutoBackupEnabled(enabled)
                if (enabled) autoBackupManager.schedule() else autoBackupManager.cancel()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _message.value = "The automatic backup setting could not be saved."
            }
        }
    }

    fun showMessage(message: String) {
        _message.value = message
    }

    private suspend fun syncReminderSchedule() {
        val settings = settingsRepository.currentSettings()
        if (settings.remindersEnabled) {
            reminderScheduler.reschedule(settings)
        } else {
            reminderScheduler.cancel()
        }
    }

    fun exportTo(uri: Uri) {
        if (_exporting.value) return
        viewModelScope.launch {
            _exporting.value = true
            try {
                val count = backupService.exportTo(uri)
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

    fun inspectBackup(uri: Uri) {
        if (_inspecting.value || _restoring.value) return
        viewModelScope.launch {
            _inspecting.value = true
            try {
                when (val result = backupService.inspectBackup(uri)) {
                    is BackupInspection.Ready -> _pendingRestore.value = result.preview
                    is BackupInspection.Invalid -> _message.value = result.message
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _message.value = "The selected backup could not be read."
            } finally {
                _inspecting.value = false
            }
        }
    }

    fun confirmRestore() {
        val preview = _pendingRestore.value ?: return
        if (_restoring.value) return
        viewModelScope.launch {
            _restoring.value = true
            try {
                when (val result = backupService.restore(preview)) {
                    is RestoreOutcome.Success -> {
                        _pendingRestore.value = null
                        _message.value = restoreMessage(result)
                    }
                    is RestoreOutcome.Failure -> {
                        _pendingRestore.value = null
                        _message.value = result.message
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _pendingRestore.value = null
                _message.value = "Restore failed. No existing entries were changed."
            } finally {
                _restoring.value = false
            }
        }
    }

    fun cancelRestore() {
        if (!_restoring.value) {
            _pendingRestore.value = null
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun restoreMessage(result: RestoreOutcome.Success): String {
        if (result.insertedItems == 0) {
            return if (result.skippedItems == 0) {
                "No entries were restored."
            } else {
                "Nothing new was added. ${entryLabel(result.skippedItems)} left unchanged."
            }
        }
        val restored = "Restored ${entryLabel(result.insertedItems)}."
        return if (result.skippedItems > 0) {
            "$restored ${entryLabel(result.skippedItems)} left unchanged."
        } else {
            restored
        }
    }

    private fun entryLabel(count: Int): String =
        if (count == 1) "1 entry" else "$count entries"

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AfterLaterApplication
                SettingsViewModel(
                    settingsRepository = app.container.settingsRepository,
                    repository = app.container.savedItemRepository,
                    backupService = app.container.archiveBackupManager,
                    reminderScheduler = app.container.reminderScheduler,
                    autoBackupManager = app.container.autoBackupManager,
                )
            }
        }
    }
}
