package app.doselog.ui.reports

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.doselog.data.repository.ItemRepository
import app.doselog.data.repository.LogRepository
import app.doselog.data.repository.ScoreRepository
import app.doselog.di.DefaultDispatcher
import app.doselog.domain.TimeSource
import app.doselog.domain.model.Category
import app.doselog.domain.model.Item
import app.doselog.domain.reports.CalendarDay
import app.doselog.domain.reports.ChartItem
import app.doselog.domain.reports.ChartPoint
import app.doselog.domain.reports.DayDetail
import app.doselog.domain.reports.ItemSummary
import app.doselog.domain.reports.RangePreset
import app.doselog.domain.reports.ReportData
import app.doselog.domain.reports.ReportFilters
import app.doselog.domain.reports.TableColumn
import app.doselog.domain.reports.TableSort
import app.doselog.domain.reports.TimelineDay
import app.doselog.domain.reports.calendarMonth
import app.doselog.domain.reports.chartItems
import app.doselog.domain.reports.dailyCounts
import app.doselog.domain.reports.dailyTotals
import app.doselog.domain.reports.dayDetail
import app.doselog.domain.reports.dayScoreSeries
import app.doselog.domain.reports.itemSummaries
import app.doselog.domain.reports.sortEntries
import app.doselog.domain.reports.timeline
import app.doselog.domain.todayFlow
import app.doselog.export.DataTransfer
import app.doselog.export.ExportKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.OutputStream
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

enum class ReportView(val title: String) {
    TIMELINE("Timeline"),
    TABLE("Table"),
    SUMMARY("Summary"),
    CHARTS("Charts"),
    CALENDAR("Calendar"),
}

data class ReportsUiState(
    val isLoading: Boolean = true,
    val filters: ReportFilters = ReportFilters(),
    val data: ReportData = ReportData.EMPTY,
    val today: LocalDate = LocalDate.now(),
)

data class ChartsUiState(
    val items: List<ChartItem> = emptyList(),
    val selected: ChartItem? = null,
    val unit: String? = null,
    val dailyTotals: List<ChartPoint> = emptyList(),
    val dailyCounts: List<ChartPoint> = emptyList(),
    val dayScores: List<ChartPoint> = emptyList(),
)

data class CalendarUiState(
    val month: YearMonth = YearMonth.now(),
    val days: List<CalendarDay> = emptyList(),
    val selectedDay: DayDetail? = null,
)

/**
 * All report views share one filtered [ReportData]. Each view's derived data is a separate flow
 * computed off the main thread, and only while that view is on screen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val logRepository: LogRepository,
    private val scoreRepository: ScoreRepository,
    itemRepository: ItemRepository,
    private val savedStateHandle: SavedStateHandle,
    @param:DefaultDispatcher private val computeDispatcher: CoroutineDispatcher,
    private val transfer: DataTransfer,
    private val timeSource: TimeSource,
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    val filters: StateFlow<ReportFilters> = savedStateHandle.getStateFlow(KEY_FILTERS, "")
        .map { decode(it, ReportFilters.serializer()) ?: ReportFilters() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, currentFilters())

    val view: StateFlow<ReportView> = savedStateHandle.getStateFlow(KEY_VIEW, ReportView.TIMELINE.name)
        .map { name -> ReportView.entries.firstOrNull { it.name == name } ?: ReportView.TIMELINE }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReportView.TIMELINE)

    val tableSort: StateFlow<TableSort> = savedStateHandle.getStateFlow(KEY_SORT, "")
        .map { decode(it, TableSort.serializer()) ?: TableSort() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, TableSort())

    /** Every item, archived included, for the item filter. */
    val allItems: StateFlow<List<Item>> = itemRepository.observeAllItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val itemNames: Flow<Map<Long, String>> = itemRepository.observeAllItems().map { items -> items.associate { it.id to it.name } }

    val uiState: StateFlow<ReportsUiState> = combine(filters, timeSource.todayFlow()) { f, today -> f to today }
        .flatMapLatest { (f, today) ->
            val range = f.requestedRange(today)
            val entries = if (range == null) logRepository.observeAllEntries() else logRepository.observeEntries(range)
            val scores = if (range == null) scoreRepository.observeAllScores() else scoreRepository.observeScores(range)
            combine(entries, scores) { e, s ->
                ReportsUiState(isLoading = false, filters = f, data = ReportData.build(f, today, e, s), today = today)
            }
        }
        .flowOn(computeDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsUiState())

    private val data: Flow<ReportData> = uiState.map { it.data }

    val timeline: StateFlow<List<TimelineDay>> = data.map { it.timeline() }
        .flowOn(computeDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tableRows = combine(data, tableSort) { d, sort -> sortEntries(d.entries, sort) }
        .flowOn(computeDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val summaries: StateFlow<List<ItemSummary>> = combine(data, itemNames) { d, names -> d.itemSummaries(names) }
        .flowOn(computeDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val chartSelection = combine(
        savedStateHandle.getStateFlow<Long?>(KEY_CHART_ITEM, null),
        savedStateHandle.getStateFlow<String?>(KEY_CHART_UNIT, null),
    ) { item, unit -> item to unit }

    val charts: StateFlow<ChartsUiState> = combine(data, itemNames, chartSelection) { d, names, (itemId, unit) ->
        val items = d.chartItems(names)
        val selected = items.firstOrNull { it.itemId == itemId } ?: items.firstOrNull()
        val chosenUnit = selected?.units?.firstOrNull { it.equals(unit, ignoreCase = true) } ?: selected?.units?.firstOrNull()
        ChartsUiState(
            items = items,
            selected = selected,
            unit = chosenUnit,
            dailyTotals = if (selected != null && chosenUnit != null) d.dailyTotals(selected.itemId, chosenUnit) else emptyList(),
            dailyCounts = selected?.let { d.dailyCounts(it.itemId) }.orEmpty(),
            dayScores = d.dayScoreSeries(),
        )
    }
        .flowOn(computeDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChartsUiState())

    private val calendarSelection = combine(
        savedStateHandle.getStateFlow<String?>(KEY_MONTH, null),
        savedStateHandle.getStateFlow<Long?>(KEY_DAY, null),
    ) { month, day -> month to day }

    fun calendar(firstDayOfWeek: DayOfWeek): Flow<CalendarUiState> =
        combine(data, calendarSelection) { d, (monthText, day) ->
            val month = monthText?.let(YearMonth::parse) ?: YearMonth.from(d.range.endInclusive)
            CalendarUiState(
                month = month,
                days = d.calendarMonth(month, firstDayOfWeek),
                selectedDay = day?.let { d.dayDetail(LocalDate.ofEpochDay(it)) },
            )
        }.flowOn(computeDispatcher)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    // ---- actions ----

    fun suggestedName(kind: ExportKind): String = kind.fileName(timeSource.now().atZone(timeSource.zone()))

    /** Exports the entries currently shown (date range, categories and items applied). */
    fun exportFilteredEntries(uri: Uri) = exportCsv(uri) { out ->
        val data = uiState.value.data
        transfer.writeEntriesCsv(out, data.entries, data.dayScores)
        "${data.entries.size} entries"
    }

    /** Exports the scores in the current date range. */
    fun exportFilteredScores(uri: Uri) = exportCsv(uri) { out ->
        val data = uiState.value.data
        transfer.writeScoresCsv(out, data.scores)
        "${data.scores.size} scores"
    }

    private fun exportCsv(uri: Uri, write: suspend (OutputStream) -> String) {
        viewModelScope.launch {
            val message = try {
                var what = ""
                transfer.writeTo(uri) { what = write(it) }
                "Saved ${transfer.displayName(uri) ?: "file"} ($what)"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "Export failed: ${e.message}"
            }
            _messages.send(message)
        }
    }

    fun selectView(view: ReportView) {
        savedStateHandle[KEY_VIEW] = view.name
    }

    fun setPreset(preset: RangePreset) = updateFilters { it.copy(preset = preset) }

    fun setCustomRange(start: LocalDate, end: LocalDate) = updateFilters {
        it.copy(preset = RangePreset.CUSTOM, customStartEpochDay = start.toEpochDay(), customEndEpochDay = end.toEpochDay())
    }

    fun toggleCategory(category: Category) = updateFilters {
        it.copy(categories = if (category in it.categories) it.categories - category else it.categories + category)
    }

    fun setItems(ids: Set<Long>) = updateFilters { it.copy(itemIds = ids) }

    fun clearEntryFilters() = updateFilters { it.copy(categories = emptySet(), itemIds = emptySet()) }

    fun sortBy(column: TableColumn) {
        savedStateHandle[KEY_SORT] = json.encodeToString(TableSort.serializer(), tableSort.value.toggle(column))
    }

    fun selectChartItem(itemId: Long) {
        savedStateHandle[KEY_CHART_ITEM] = itemId
        savedStateHandle[KEY_CHART_UNIT] = null
    }

    fun selectChartUnit(unit: String) {
        savedStateHandle[KEY_CHART_UNIT] = unit
    }

    fun showMonth(month: YearMonth) {
        savedStateHandle[KEY_MONTH] = month.toString()
    }

    fun selectDay(date: LocalDate?) {
        savedStateHandle[KEY_DAY] = date?.toEpochDay()
    }

    private fun currentFilters(): ReportFilters =
        savedStateHandle.get<String>(KEY_FILTERS)?.let { decode(it, ReportFilters.serializer()) } ?: ReportFilters()

    private fun updateFilters(transform: (ReportFilters) -> ReportFilters) {
        savedStateHandle[KEY_FILTERS] = json.encodeToString(ReportFilters.serializer(), transform(filters.value))
    }

    private fun <T> decode(text: String, serializer: KSerializer<T>): T? =
        if (text.isEmpty()) null else runCatching { json.decodeFromString(serializer, text) }.getOrNull()

    private companion object {
        const val KEY_FILTERS = "filters"
        const val KEY_VIEW = "view"
        const val KEY_SORT = "sort"
        const val KEY_CHART_ITEM = "chartItem"
        const val KEY_CHART_UNIT = "chartUnit"
        const val KEY_MONTH = "month"
        const val KEY_DAY = "day"
    }
}
