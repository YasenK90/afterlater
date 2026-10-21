package com.afterlater.feature.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.afterlater.AfterLaterApplication
import com.afterlater.core.model.CaptureResult
import com.afterlater.data.repository.SavedItemRepository
import com.afterlater.domain.url.LinkParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class QuickAddUiState(
    val input: String = "",
    val error: String? = null,
    val saving: Boolean = false,
    val result: CaptureResult? = null,
    val attemptId: String = UUID.randomUUID().toString(),
)

class QuickAddViewModel(
    private val repository: SavedItemRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(QuickAddUiState())
    val state: StateFlow<QuickAddUiState> = _state.asStateFlow()

    fun onInputChange(value: String) {
        _state.update { it.copy(input = value, error = null, result = null) }
    }

    fun paste(value: String) {
        _state.update { it.copy(input = value.trim(), error = null, result = null) }
    }

    fun save() {
        val current = _state.value
        if (current.saving) return

        when (val parsed = LinkParser.validateManualInput(current.input)) {
            is LinkParser.ManualInputResult.Invalid -> {
                _state.update { it.copy(error = parsed.message) }
            }

            is LinkParser.ManualInputResult.Valid -> {
                _state.update { it.copy(saving = true, error = null, result = null) }
                viewModelScope.launch {
                    val result = try {
                        repository.capture(parsed.link)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        CaptureResult.Failed("The link could not be saved on this device. Try again.")
                    }
                    _state.update { it.copy(saving = false, result = result) }
                }
            }
        }
    }

    fun reset() {
        _state.value = QuickAddUiState()
    }

    fun dismissResult() {
        _state.update { it.copy(result = null, error = null) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AfterLaterApplication
                QuickAddViewModel(app.container.savedItemRepository)
            }
        }
    }
}
