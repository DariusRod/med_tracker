package app.doselog.ui.log

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.doselog.data.repository.DoseSelection
import app.doselog.data.repository.ItemRepository
import app.doselog.data.repository.LogEntryEdit
import app.doselog.data.repository.LogRepository
import app.doselog.data.repository.NewLogEntry
import app.doselog.domain.AmountInput
import app.doselog.domain.AmountParser
import app.doselog.domain.TimeSource
import app.doselog.domain.model.Category
import app.doselog.domain.model.DateRange
import app.doselog.domain.model.Item
import app.doselog.domain.model.LogEntry
import app.doselog.domain.todayFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import javax.inject.Inject

/** The in-progress state of one item card. Survives rotation and process death. */
@Serializable
data class CardDraft(
    val selectedOptionId: Long? = null,
    val customSelected: Boolean = false,
    val customAmount: String = "",
    val customUnit: String = "",
    val description: String = "",
    /** Null means "now", resolved at the moment Submit is pressed. */
    val takenAtMillis: Long? = null,
) {
    val customAmountInput: AmountInput get() = AmountParser.parse(customAmount)

    /** The dose to log, or null if nothing valid is selected yet. */
    fun doseSelection(item: Item): DoseSelection? = when {
        selectedOptionId != null && item.options.any { it.id == selectedOptionId } -> DoseSelection.Preset(selectedOptionId)
        customSelected -> (customAmountInput as? AmountInput.Valid)
            ?.value
            ?.takeIf { it > 0.0 }
            ?.let { DoseSelection.Custom(it, customUnit) }
        else -> null
    }
}

data class LogUiState(
    val isLoading: Boolean = true,
    val hasAnyItems: Boolean = false,
    val items: List<Item> = emptyList(),
    val category: Category? = null,
    val today: LocalDate = LocalDate.now(),
    val todayEntries: List<LogEntry> = emptyList(),
    val savingItemIds: Set<Long> = emptySet(),
    val todayExpanded: Boolean = true,
)

data class EditingEntry(val entry: LogEntry, val item: Item?)

sealed interface LogEvent {
    data class Logged(val entryId: Long, val itemName: String, val label: String, val takenAt: ZonedDateTime) : LogEvent
    data class Message(val text: String) : LogEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LogViewModel @Inject constructor(
    private val itemRepository: ItemRepository,
    private val logRepository: LogRepository,
    private val timeSource: TimeSource,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    // Card drafts are a plain MutableStateFlow (not derived) so text fields update synchronously.
    private val _drafts = MutableStateFlow(restoreDrafts())
    val drafts: StateFlow<Map<Long, CardDraft>> = _drafts.asStateFlow()

    val query: StateFlow<String> = savedStateHandle.getStateFlow(KEY_QUERY, "")
    private val category = savedStateHandle.getStateFlow<String?>(KEY_CATEGORY, null)
    private val todayExpanded = savedStateHandle.getStateFlow(KEY_TODAY_EXPANDED, true)
    val editingEntryId: StateFlow<Long?> = savedStateHandle.getStateFlow<Long?>(KEY_EDITING, null)
    private val saving = MutableStateFlow<Set<Long>>(emptySet())

    private val today: Flow<Pair<LocalDate, List<LogEntry>>> = timeSource.todayFlow().flatMapLatest { date ->
        logRepository.observeEntries(DateRange(date, date)).map { date to it }
    }

    private val filters = combine(query, category, todayExpanded) { q, c, expanded -> Triple(q, c?.let(Category::fromStored), expanded) }

    val uiState: StateFlow<LogUiState> = combine(
        itemRepository.observeActiveItems(),
        filters,
        today,
        saving,
    ) { items, (q, cat, expanded), (date, entries), savingIds ->
        LogUiState(
            isLoading = false,
            hasAnyItems = items.isNotEmpty(),
            items = items.filter { item ->
                (cat == null || item.category == cat) && (q.isBlank() || item.name.contains(q.trim(), ignoreCase = true))
            },
            category = cat,
            today = date,
            todayEntries = entries,
            savingItemIds = savingIds,
            todayExpanded = expanded,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LogUiState())

    private val _events = Channel<LogEvent>(Channel.BUFFERED)
    val events: Flow<LogEvent> = _events.receiveAsFlow()

    fun onQueryChange(value: String) {
        savedStateHandle[KEY_QUERY] = value
    }

    fun onCategoryChange(value: Category?) {
        savedStateHandle[KEY_CATEGORY] = value?.name
    }

    fun onTodayExpandedChange(expanded: Boolean) {
        savedStateHandle[KEY_TODAY_EXPANDED] = expanded
    }

    // ---- card drafts ----

    fun selectOption(itemId: Long, optionId: Long) = updateDraft(itemId) {
        it.copy(selectedOptionId = if (it.selectedOptionId == optionId) null else optionId, customSelected = false)
    }

    fun toggleCustom(item: Item) = updateDraft(item.id) {
        val unit = it.customUnit.ifEmpty { item.defaultUnit.orEmpty() }
        it.copy(customSelected = !it.customSelected, selectedOptionId = null, customUnit = unit)
    }

    fun setCustomAmount(itemId: Long, text: String) = updateDraft(itemId) { it.copy(customAmount = text) }

    fun setCustomUnit(itemId: Long, unit: String) = updateDraft(itemId) { it.copy(customUnit = unit) }

    fun setDescription(itemId: Long, text: String) = updateDraft(itemId) { it.copy(description = text) }

    fun setTakenAt(itemId: Long, takenAt: Instant?) = updateDraft(itemId) { it.copy(takenAtMillis = takenAt?.toEpochMilli()) }

    fun submit(item: Item) {
        if (item.id in saving.value) return
        val draft = _drafts.value[item.id] ?: return
        val dose = draft.doseSelection(item) ?: return
        saving.update { it + item.id }
        viewModelScope.launch {
            try {
                // An untouched time field means "the moment Submit was pressed".
                val takenAt = draft.takenAtMillis?.let(Instant::ofEpochMilli) ?: timeSource.now()
                val entry = logRepository.addEntry(
                    NewLogEntry(item.id, dose, draft.description, takenAt, timeSource.zone()),
                )
                _drafts.update { it - item.id }
                persistDrafts()
                _events.send(LogEvent.Logged(entry.id, entry.itemName, entry.label, entry.localDateTime))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(LogEvent.Message("Couldn't save: ${e.message}"))
            } finally {
                saving.update { it - item.id }
            }
        }
    }

    fun undoLog(entryId: Long) {
        viewModelScope.launch { logRepository.deleteEntry(entryId) }
    }

    // ---- editing today's entries ----

    fun openEntry(entryId: Long?) {
        savedStateHandle[KEY_EDITING] = entryId
    }

    /** The entry open in the edit sheet, with its item's current presets. */
    val editingEntry: StateFlow<EditingEntry?> = editingEntryId.flatMapLatest { id ->
        if (id == null) {
            flowOf<EditingEntry?>(null)
        } else {
            flow<EditingEntry?> {
                val entry = logRepository.getEntry(id)
                emit(entry?.let { EditingEntry(it, itemRepository.getItem(it.itemId)) })
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun saveEntry(edit: LogEntryEdit) {
        viewModelScope.launch {
            try {
                logRepository.updateEntry(edit)
                openEntry(null)
                _events.send(LogEvent.Message("Entry updated"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(LogEvent.Message("Couldn't update: ${e.message}"))
            }
        }
    }

    fun deleteEntry(entryId: Long) {
        viewModelScope.launch {
            logRepository.deleteEntry(entryId)
            openEntry(null)
            _events.send(LogEvent.Message("Entry deleted"))
        }
    }

    fun deviceZone() = timeSource.zone()

    private fun updateDraft(itemId: Long, transform: (CardDraft) -> CardDraft) {
        _drafts.update { drafts -> drafts + (itemId to transform(drafts[itemId] ?: CardDraft())) }
        persistDrafts()
    }

    private fun persistDrafts() {
        savedStateHandle[KEY_DRAFTS] = json.encodeToString(DRAFTS_SERIALIZER, _drafts.value)
    }

    private fun restoreDrafts(): Map<Long, CardDraft> =
        savedStateHandle.get<String>(KEY_DRAFTS)
            ?.let { runCatching { json.decodeFromString(DRAFTS_SERIALIZER, it) }.getOrNull() }
            ?: emptyMap()

    private companion object {
        const val KEY_DRAFTS = "drafts"
        const val KEY_QUERY = "query"
        const val KEY_CATEGORY = "category"
        const val KEY_TODAY_EXPANDED = "todayExpanded"
        const val KEY_EDITING = "editingEntry"
        val DRAFTS_SERIALIZER = MapSerializer(Long.serializer(), CardDraft.serializer())
    }
}
