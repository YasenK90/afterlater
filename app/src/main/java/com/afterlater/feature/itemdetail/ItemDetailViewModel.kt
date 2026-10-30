package com.afterlater.feature.itemdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.afterlater.AfterLaterApplication
import com.afterlater.core.model.NoteText
import com.afterlater.core.model.ReviewNotice
import com.afterlater.core.model.ReviewUndo
import com.afterlater.core.model.SavedItem
import com.afterlater.core.util.TimeFormat
import com.afterlater.data.repository.SavedItemRepository
import com.afterlater.domain.review.ExpiryChoice
import com.afterlater.domain.review.SnoozeWindow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ItemDetailUiState(
    val loading: Boolean = true,
    val item: SavedItem? = null,
    val noteDraft: String = "",
    val noteDirty: Boolean = false,
    val savingNote: Boolean = false,
    val busy: Boolean = false,
    val deleted: Boolean = false,
    val message: ReviewNotice? = null,
)

class ItemDetailViewModel(
    private val itemId: String,
    private val repository: SavedItemRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ItemDetailUiState())
    val uiState: StateFlow<ItemDetailUiState> = _uiState.asStateFlow()
    private var actionInFlight = false
    private var noteSaveCallback: (() -> Unit)? = null

    init {
        viewModelScope.launch {
            repository.observeItem(itemId).collect { item ->
                _uiState.update { state ->
                    state.copy(
                        loading = false,
                        item = item,
                        noteDraft = if (state.noteDirty) state.noteDraft else item?.personalNote.orEmpty(),
                    )
                }
            }
        }
    }

    fun onNoteChange(value: String) {
        val bounded = NoteText.limit(value)
        _uiState.update { state ->
            state.copy(
                noteDraft = bounded,
                noteDirty = NoteText.normalize(bounded) != NoteText.normalize(state.item?.personalNote),
            )
        }
    }

    fun saveNote(onSaved: (() -> Unit)? = null) {
        if (onSaved != null) noteSaveCallback = onSaved

        val state = _uiState.value
        if (state.item == null) {
            val callback = noteSaveCallback
            noteSaveCallback = null
            callback?.invoke()
            return
        }
        if (state.savingNote) return

        viewModelScope.launch {
            _uiState.update { it.copy(savingNote = true) }
            try {
                val savedText = NoteText.normalize(state.noteDraft)
                val updated = repository.updateNote(itemId, savedText)
                if (updated == null) {
                    _uiState.update {
                        it.copy(savingNote = false, message = ReviewNotice("Note could not be saved."))
                    }
                    noteSaveCallback = null
                    return@launch
                }
                _uiState.update { current ->
                    current.copy(
                        savingNote = false,
                        noteDirty = NoteText.normalize(current.noteDraft) != savedText,
                        message = ReviewNotice("Note saved."),
                    )
                }
                val callback = noteSaveCallback
                noteSaveCallback = null
                callback?.invoke()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(savingNote = false, message = ReviewNotice("Note could not be saved."))
                }
                noteSaveCallback = null
            }
        }
    }

    fun recordOpen() = runAction("Could not update the review schedule.") {
        val change = repository.openItem(itemId)
        if (change != null) {
            ReviewNotice(
                text = if (change.item.isReviewable) {
                    "OPENED — BACK IN 7 DAYS."
                } else {
                    "OPENED. STILL OUT OF ROTATION."
                },
                undo = change.undo(),
            )
        } else {
            ReviewNotice("ENTRY NOT FOUND.")
        }
    }

    fun markUseful() = runAction("Could not mark this entry useful.") {
        val change = repository.markUseful(itemId)
        if (change != null) {
            ReviewNotice("MARKED USEFUL — OUT OF TODAY.", change.undo())
        } else {
            ReviewNotice("ENTRY NOT FOUND.")
        }
    }

    fun snooze(window: SnoozeWindow) = runAction("Could not snooze this entry.") {
        val change = repository.snooze(itemId, window)
        val until = change?.item?.snoozedUntil
        if (until != null) {
            ReviewNotice("SNOOZED UNTIL ${TimeFormat.dateTime(until)}.", change?.undo())
        } else {
            ReviewNotice("This entry is no longer in rotation.")
        }
    }

    fun archive() = runAction("Could not archive this entry.") {
        val change = repository.archive(itemId)
        if (change != null) {
            ReviewNotice("ARCHIVED — OUT OF TODAY.", change.undo())
        } else {
            ReviewNotice("ENTRY NOT FOUND.")
        }
    }

    fun restore() = runAction("Could not restore this entry.") {
        val change = repository.restore(itemId)
        if (change != null) {
            ReviewNotice("RETURNED TO TODAY.", change.undo())
        } else {
            ReviewNotice("ENTRY NOT FOUND.")
        }
    }

    fun retryMetadata() = runAction("Could not queue a preview retry.") {
        repository.retryMetadata(itemId)
        ReviewNotice("Preview queued.")
    }

    fun setDeadline(choice: ExpiryChoice) = runAction("Could not set the deadline.") {
        val updated = repository.setDeadline(itemId, choice)
        ReviewNotice(
            when {
                updated == null -> "ENTRY NOT FOUND."
                choice == ExpiryChoice.NONE -> "DEADLINE CLEARED."
                else -> "DEADLINE SET."
            },
        )
    }

    fun undo(undo: ReviewUndo) = runAction("Could not undo that action.") {
        val change = repository.undoReview(undo)
        ReviewNotice(
            text = if (change != null) "RESTORED TO THE PREVIOUS STATE." else "ENTRY CHANGED. UNDO SKIPPED.",
        )
    }

    fun delete() {
        if (!beginAction()) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            try {
                val deleted = repository.delete(itemId)
                _uiState.update {
                    it.copy(
                        busy = false,
                        deleted = deleted,
                        message = if (deleted) null else ReviewNotice("Entry could not be deleted."),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(busy = false, message = ReviewNotice("Entry could not be deleted."))
                }
            } finally {
                endAction()
            }
        }
    }

    fun showMessage(message: String) {
        _uiState.update { it.copy(message = ReviewNotice(message)) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun runAction(failure: String, block: suspend () -> ReviewNotice) {
        if (!beginAction()) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            try {
                val notice = block()
                _uiState.update { it.copy(busy = false, message = notice) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(busy = false, message = ReviewNotice(failure))
                }
            } finally {
                endAction()
            }
        }
    }

    private fun beginAction(): Boolean {
        if (actionInFlight) return false
        actionInFlight = true
        return true
    }

    private fun endAction() {
        actionInFlight = false
    }

    companion object {
        const val MAX_NOTE_LENGTH = NoteText.MAX_LENGTH

        fun factory(itemId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AfterLaterApplication
                ItemDetailViewModel(
                    itemId = itemId,
                    repository = app.container.savedItemRepository,
                )
            }
        }
    }
}
