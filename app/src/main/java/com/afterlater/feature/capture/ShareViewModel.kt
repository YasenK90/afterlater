package com.afterlater.feature.capture

import android.content.Intent
import android.net.Uri
import android.text.Spanned
import android.text.style.URLSpan
import androidx.core.content.IntentCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.afterlater.AfterLaterApplication
import com.afterlater.core.model.CaptureResult
import com.afterlater.core.model.SavedItem
import com.afterlater.core.model.SharedContent
import com.afterlater.data.repository.SavedItemRepository
import com.afterlater.domain.url.LinkParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ShareCaptureUiState {
    data object Waiting : ShareCaptureUiState
    data object Saving : ShareCaptureUiState
    data class Saved(val item: SavedItem) : ShareCaptureUiState
    data class Duplicate(val item: SavedItem) : ShareCaptureUiState
    data class Error(val heading: String, val message: String) : ShareCaptureUiState
}

class ShareViewModel(
    private val repository: SavedItemRepository,
    private val appScope: CoroutineScope,
) : ViewModel() {

    private val _state = MutableStateFlow<ShareCaptureUiState>(ShareCaptureUiState.Waiting)
    val state: StateFlow<ShareCaptureUiState> = _state.asStateFlow()

    @Volatile
    private var captureRequest = 0
    private var lastCanonicalUrl: String? = null

    fun handleIntent(intent: Intent?) {
        handleContent(intent?.toSharedContent())
    }

    fun handleContent(content: SharedContent?) {
        val parsed = content?.let { LinkParser.fromSharedContent(it.text, it.subject) }
        if (parsed != null && parsed.canonicalUrl == lastCanonicalUrl) {
            when (_state.value) {
                ShareCaptureUiState.Saving,
                is ShareCaptureUiState.Saved,
                is ShareCaptureUiState.Duplicate -> return

                ShareCaptureUiState.Waiting,
                is ShareCaptureUiState.Error -> Unit
            }
        }

        val request = ++captureRequest

        if (content == null) {
            lastCanonicalUrl = null
            _state.value = ShareCaptureUiState.Error(
                heading = "NOTHING TO SAVE",
                message = "No shared content was received.",
            )
            return
        }

        if (parsed == null) {
            lastCanonicalUrl = null
            _state.value = ShareCaptureUiState.Error(
                heading = "NO LINK FOUND",
                message = "This share did not contain a web link. Paste it manually instead.",
            )
            return
        }

        lastCanonicalUrl = parsed.canonicalUrl
        _state.value = ShareCaptureUiState.Saving
        appScope.launch {
            try {
                val result = repository.capture(parsed)
                if (request != captureRequest) return@launch
                ensureActive()
                when (result) {
                    is CaptureResult.Saved -> _state.value = ShareCaptureUiState.Saved(result.item)
                    is CaptureResult.Duplicate -> _state.value = ShareCaptureUiState.Duplicate(result.existing)
                    is CaptureResult.Invalid -> _state.value = ShareCaptureUiState.Error(
                        heading = "NOT SAVED",
                        message = result.reason,
                    )
                    is CaptureResult.Failed -> _state.value = ShareCaptureUiState.Error(
                        heading = "NOT SAVED",
                        message = result.reason,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (request == captureRequest) {
                    _state.value = ShareCaptureUiState.Error(
                        heading = "NOT SAVED",
                        message = "The link could not be saved on this device. Try again.",
                    )
                }
            }
        }
    }

    private fun Intent.toSharedContent(): SharedContent {
        val intent = this
        val candidates = buildList {
            addTextCandidates(intent.getCharSequenceExtra(Intent.EXTRA_TEXT))
            addTextCandidates(intent.getCharSequenceExtra(Intent.EXTRA_HTML_TEXT))
            intent.clipData?.let { clip ->
                for (index in 0 until clip.itemCount) {
                    val item = clip.getItemAt(index)
                    addTextCandidates(item.text)
                    addTextCandidates(item.htmlText)
                    addWebUri(item.uri)
                    item.intent?.data?.let { addWebUri(it) }
                }
            }
            intent.data?.let { addWebUri(it) }
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                ?.let { addWebUri(it) }
        }

        return SharedContent(
            text = candidates.firstOrNull { LinkParser.fromSharedContent(it, null) != null }
                ?: candidates.firstOrNull { it.isNotBlank() },
            subject = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString(),
        )
    }

    private fun MutableList<String>.addTextCandidates(value: CharSequence?) {
        if (value == null) return
        if (value is Spanned) {
            value.getSpans(0, value.length, URLSpan::class.java)
                .mapTo(this) { it.url }
        }
        value.toString().takeIf { it.isNotBlank() }?.let(::add)
    }

    private fun MutableList<String>.addWebUri(uri: Uri?) {
        if (uri == null) return
        when (uri.scheme?.lowercase()) {
            "http", "https" -> uri.toString().takeIf { it.isNotBlank() }?.let(::add)
            else -> Unit
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AfterLaterApplication
                ShareViewModel(
                    repository = app.container.savedItemRepository,
                    appScope = app.container.applicationScope,
                )
            }
        }
    }
}
