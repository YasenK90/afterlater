package com.afterlater.feature.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.afterlater.AfterLaterApplication
import com.afterlater.core.model.ArchiveSort
import com.afterlater.core.model.ItemStatus
import com.afterlater.core.model.SavedItem
import com.afterlater.data.repository.SavedItemRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ArchiveLayout { VISUAL, COMPACT }

private data class ArchiveRequest(
    val query: String,
    val status: ItemStatus?,
    val domain: String?,
    val sort: ArchiveSort,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class ArchiveViewModel(
    private val repository: SavedItemRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _statusFilter = MutableStateFlow<ItemStatus?>(null)
    val statusFilter: StateFlow<ItemStatus?> = _statusFilter.asStateFlow()

    private val _domainFilter = MutableStateFlow<String?>(null)
    val domainFilter: StateFlow<String?> = _domainFilter.asStateFlow()

    private val _sort = MutableStateFlow(ArchiveSort.NEWEST)
    val sort: StateFlow<ArchiveSort> = _sort.asStateFlow()

    private val _layout = MutableStateFlow(ArchiveLayout.VISUAL)
    val layout: StateFlow<ArchiveLayout> = _layout.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var actionInFlight = false

    private val request = combine(
        _query.debounce(180),
        _statusFilter,
        _domainFilter,
        _sort,
    ) { query, status, domain, sort ->
        ArchiveRequest(query.trim(), status, domain, sort)
    }.distinctUntilChanged()

    val items: StateFlow<List<SavedItem>> = request
        .flatMapLatest { req ->
            repository.observeArchive(
                query = req.query,
                status = req.status,
                domain = req.domain,
                sort = req.sort,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val domains: StateFlow<List<String>> = repository.observeDomains()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalCount: StateFlow<Int> = repository.observeTotalCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), -1)

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setStatus(status: ItemStatus?) {
        _statusFilter.value = status
    }

    fun setDomain(domain: String?) {
        _domainFilter.value = domain
    }

    fun setSort(sort: ArchiveSort) {
        _sort.value = sort
    }

    fun setLayout(layout: ArchiveLayout) {
        _layout.value = layout
    }

    fun toggleLayout() {
        _layout.value = if (_layout.value == ArchiveLayout.VISUAL) ArchiveLayout.COMPACT else ArchiveLayout.VISUAL
    }

    fun restore(id: String) = runAction("Could not restore this entry.") {
        if (repository.restore(id) != null) "RESTORED TO TODAY." else "ENTRY NOT FOUND."
    }

    fun delete(id: String) = runAction("Entry could not be deleted.") {
        if (repository.delete(id)) "DELETED PERMANENTLY." else "ENTRY NOT FOUND."
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun runAction(failure: String, block: suspend () -> String) {
        if (actionInFlight) return
        actionInFlight = true
        viewModelScope.launch {
            _busy.value = true
            try {
                _message.value = block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _message.value = failure
            } finally {
                _busy.value = false
                actionInFlight = false
            }
        }
    }

    fun clearFilters() {
        _query.value = ""
        _statusFilter.value = null
        _domainFilter.value = null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AfterLaterApplication
                ArchiveViewModel(app.container.savedItemRepository)
            }
        }
    }
}
