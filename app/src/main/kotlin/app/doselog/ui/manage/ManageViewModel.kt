package app.doselog.ui.manage

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.doselog.data.repository.ItemRepository
import app.doselog.data.settings.SettingsRepository
import app.doselog.domain.model.Category
import app.doselog.domain.model.Item
import app.doselog.sample.SampleData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ManageFilters(
    val query: String = "",
    val category: Category? = null,
    val showArchived: Boolean = false,
    val reorderMode: Boolean = false,
)

data class ManageUiState(
    val isLoading: Boolean = true,
    val filters: ManageFilters = ManageFilters(),
    /** In reorder mode: active items in Log order. Otherwise: filtered items grouped by category. */
    val items: List<Item> = emptyList(),
    val hasAnyItems: Boolean = false,
    val archivedCount: Int = 0,
    val useDynamicColor: Boolean = false,
    val sampleDataAvailable: Boolean = false,
)

@HiltViewModel
class ManageViewModel @Inject constructor(
    private val itemRepository: ItemRepository,
    private val settingsRepository: SettingsRepository,
    private val sampleData: SampleData,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val query: StateFlow<String> = savedStateHandle.getStateFlow(KEY_QUERY, "")

    private val filters: Flow<ManageFilters> = combine(
        query,
        savedStateHandle.getStateFlow<String?>(KEY_CATEGORY, null),
        savedStateHandle.getStateFlow(KEY_SHOW_ARCHIVED, false),
        savedStateHandle.getStateFlow(KEY_REORDER, false),
    ) { query, category, showArchived, reorder ->
        ManageFilters(query, category?.let(Category::fromStored), showArchived, reorder)
    }

    val uiState: StateFlow<ManageUiState> = combine(
        itemRepository.observeAllItems(),
        filters,
        settingsRepository.settings,
    ) { all, filters, settings ->
        val visible = if (filters.reorderMode) {
            all.filter { !it.isArchived }
        } else {
            all.filter { item ->
                (filters.showArchived || !item.isArchived) &&
                    (filters.category == null || item.category == filters.category) &&
                    item.matches(filters.query)
            }.sortedWith(compareBy({ it.category.ordinal }, { it.isArchived }, { it.sortOrder }, { it.id }))
        }
        ManageUiState(
            isLoading = false,
            filters = filters,
            items = visible,
            hasAnyItems = all.isNotEmpty(),
            archivedCount = all.count { it.isArchived },
            useDynamicColor = settings.useDynamicColor,
            sampleDataAvailable = sampleData.isAvailable,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ManageUiState())

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    fun onQueryChange(query: String) {
        savedStateHandle[KEY_QUERY] = query
    }

    fun onCategoryChange(category: Category?) {
        savedStateHandle[KEY_CATEGORY] = category?.name
    }

    fun onShowArchivedChange(show: Boolean) {
        savedStateHandle[KEY_SHOW_ARCHIVED] = show
    }

    fun onReorderModeChange(enabled: Boolean) {
        savedStateHandle[KEY_REORDER] = enabled
    }

    fun moveItem(id: Long, up: Boolean) {
        viewModelScope.launch { itemRepository.moveItem(id, up) }
    }

    fun setUseDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setUseDynamicColor(enabled) }
    }

    fun loadSampleData() {
        viewModelScope.launch {
            val message = try {
                sampleData.load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "Couldn't load sample data: ${e.message}"
            }
            _messages.send(message)
        }
    }

    private fun Item.matches(query: String): Boolean {
        val q = query.trim()
        return q.isEmpty() || name.contains(q, ignoreCase = true) || options.any { it.label.contains(q, ignoreCase = true) }
    }

    private companion object {
        const val KEY_QUERY = "query"
        const val KEY_CATEGORY = "category"
        const val KEY_SHOW_ARCHIVED = "showArchived"
        const val KEY_REORDER = "reorder"
    }
}
