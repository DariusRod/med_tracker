package app.doselog.ui.manage

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.doselog.data.repository.ArchiveResult
import app.doselog.data.repository.DeleteItemResult
import app.doselog.data.repository.ItemRepository
import app.doselog.data.repository.SaveItemResult
import app.doselog.domain.AmountInput
import app.doselog.domain.AmountParser
import app.doselog.domain.model.Category
import app.doselog.domain.model.Item
import app.doselog.domain.model.ItemDraft
import app.doselog.domain.model.NameError
import app.doselog.domain.model.OptionDraft
import app.doselog.domain.model.OptionError
import app.doselog.ui.navigation.ItemEditRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** A preset row in the editor. [key] is a stable UI id; [id] is the database id once saved. */
@Serializable
data class PresetForm(
    val key: Long,
    val id: Long? = null,
    val amountText: String = "",
    val unit: String = "",
    val label: String = "",
) {
    val amount: Double? get() = (AmountParser.parse(amountText) as? AmountInput.Valid)?.value

    fun toDraft() = OptionDraft(id = id, amount = amount, unit = unit, label = label)
}

@Serializable
data class ItemForm(
    val name: String = "",
    val category: Category = Category.MEDICATION,
    val defaultUnit: String = "",
    val presets: List<PresetForm> = emptyList(),
    val nextKey: Long = 1,
)

data class ItemEditUiState(
    val isNew: Boolean,
    val isLoading: Boolean = false,
    val notFound: Boolean = false,
    val isSaving: Boolean = false,
    val nameError: NameError? = null,
    val presetErrors: Map<Long, OptionError> = emptyMap(),
    val entryCount: Int = 0,
    val isArchived: Boolean = false,
)

sealed interface ItemEditEvent {
    data object Closed : ItemEditEvent
    data class Message(val text: String) : ItemEditEvent
}

@HiltViewModel
class ItemEditViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val itemRepository: ItemRepository,
) : ViewModel() {

    private val itemId: Long = savedStateHandle.toRoute<ItemEditRoute>().itemId
    private val isNew = itemId == ItemEditRoute.NEW_ITEM
    private val json = Json { ignoreUnknownKeys = true }

    // The form survives process death through SavedStateHandle; the original copy tells us
    // whether there are unsaved changes.
    private val restoredForm: ItemForm? = savedStateHandle.get<String>(KEY_FORM)?.let(::decode)
    private val _form = MutableStateFlow(restoredForm ?: ItemForm())
    val form: StateFlow<ItemForm> = _form.asStateFlow()
    private var originalForm: ItemForm = savedStateHandle.get<String>(KEY_ORIGINAL)?.let(::decode) ?: ItemForm()

    private val _uiState = MutableStateFlow(ItemEditUiState(isNew = isNew, isLoading = !isNew && restoredForm == null))
    val uiState: StateFlow<ItemEditUiState> = _uiState.asStateFlow()

    private val _events = Channel<ItemEditEvent>(Channel.BUFFERED)
    val events: Flow<ItemEditEvent> = _events.receiveAsFlow()

    init {
        if (!isNew) {
            viewModelScope.launch {
                val item = itemRepository.getItem(itemId)
                if (item == null) {
                    _uiState.update { it.copy(isLoading = false, notFound = true) }
                    return@launch
                }
                _uiState.update { it.copy(isLoading = false, isArchived = item.isArchived) }
                if (restoredForm == null) {
                    val loaded = item.toForm()
                    originalForm = loaded
                    savedStateHandle[KEY_ORIGINAL] = encode(loaded)
                    setForm(loaded)
                }
            }
            viewModelScope.launch {
                itemRepository.observeEntryCount(itemId).collect { count ->
                    _uiState.update { it.copy(entryCount = count) }
                }
            }
        }
    }

    val hasUnsavedChanges: Boolean get() = _form.value != originalForm

    fun onNameChange(name: String) {
        updateForm { it.copy(name = name) }
        _uiState.update { it.copy(nameError = null) }
    }

    fun onCategoryChange(category: Category) = updateForm { it.copy(category = category) }

    fun onDefaultUnitChange(unit: String) = updateForm { it.copy(defaultUnit = unit) }

    /** Adds a new preset ([preset].key == 0) or replaces the one with the same key. */
    fun upsertPreset(preset: PresetForm) {
        updateForm { form ->
            if (preset.key == 0L) {
                form.copy(presets = form.presets + preset.copy(key = form.nextKey), nextKey = form.nextKey + 1)
            } else {
                form.copy(presets = form.presets.map { if (it.key == preset.key) preset else it })
            }
        }
        _uiState.update { it.copy(presetErrors = it.presetErrors - preset.key) }
    }

    fun removePreset(key: Long) = updateForm { form -> form.copy(presets = form.presets.filterNot { it.key == key }) }

    fun movePreset(key: Long, up: Boolean) = updateForm { form ->
        val list = form.presets.toMutableList()
        val index = list.indexOfFirst { it.key == key }
        val target = if (up) index - 1 else index + 1
        if (index < 0 || target !in list.indices) return@updateForm form
        list.add(target, list.removeAt(index))
        form.copy(presets = list)
    }

    fun save() {
        if (_uiState.value.isSaving) return
        val form = _form.value
        val draft = ItemDraft(
            id = if (isNew) null else itemId,
            name = form.name,
            category = form.category,
            defaultUnit = form.defaultUnit,
            options = form.presets.map { it.toDraft() },
        )
        _uiState.update { it.copy(isSaving = true) }
        launchSafely {
            when (val result = itemRepository.saveItem(draft)) {
                is SaveItemResult.Saved -> {
                    originalForm = form
                    _events.send(ItemEditEvent.Closed)
                }
                is SaveItemResult.Invalid -> _uiState.update { state ->
                    state.copy(
                        nameError = result.errors.name,
                        presetErrors = result.errors.options.mapKeys { (index, _) -> form.presets[index].key },
                    )
                }
            }
        }
    }

    fun delete() {
        launchSafely {
            when (val result = itemRepository.deleteItem(itemId)) {
                DeleteItemResult.Deleted, DeleteItemResult.NotFound -> _events.send(ItemEditEvent.Closed)
                is DeleteItemResult.HasEntries -> _events.send(
                    ItemEditEvent.Message("This item has ${result.entryCount} log entries, so it can only be archived."),
                )
            }
        }
    }

    fun setArchived(archived: Boolean) {
        launchSafely {
            when (itemRepository.setArchived(itemId, archived)) {
                ArchiveResult.Done -> {
                    _uiState.update { it.copy(isArchived = archived) }
                    _events.send(ItemEditEvent.Message(if (archived) "Item archived" else "Item restored"))
                }
                ArchiveResult.DuplicateName -> _events.send(
                    ItemEditEvent.Message("Another active item already uses this name. Rename one of them first."),
                )
            }
        }
    }

    private fun launchSafely(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(ItemEditEvent.Message("Something went wrong: ${e.message}"))
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    private fun updateForm(transform: (ItemForm) -> ItemForm) {
        setForm(transform(_form.value))
    }

    private fun setForm(form: ItemForm) {
        _form.value = form
        savedStateHandle[KEY_FORM] = encode(form)
    }

    private fun encode(form: ItemForm): String = json.encodeToString(ItemForm.serializer(), form)

    private fun decode(text: String): ItemForm? = runCatching { json.decodeFromString(ItemForm.serializer(), text) }.getOrNull()

    private companion object {
        const val KEY_FORM = "form"
        const val KEY_ORIGINAL = "originalForm"

        fun Item.toForm(): ItemForm = ItemForm(
            name = name,
            category = category,
            defaultUnit = defaultUnit.orEmpty(),
            presets = options.mapIndexed { index, option ->
                val autoLabel = OptionDraft(null, option.amount, option.unit, "").effectiveLabel
                PresetForm(
                    key = index + 1L,
                    id = option.id,
                    amountText = AmountParser.toEditText(option.amount),
                    unit = option.unit,
                    // Keep custom labels; an automatic label stays automatic when amount/unit change.
                    label = if (option.label == autoLabel) "" else option.label,
                )
            },
            nextKey = options.size + 1L,
        )
    }
}
