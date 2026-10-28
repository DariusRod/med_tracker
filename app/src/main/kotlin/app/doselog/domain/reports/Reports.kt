package app.doselog.domain.reports

import app.doselog.domain.DayGrouping
import app.doselog.domain.UnitTotal
import app.doselog.domain.UnitTotals
import app.doselog.domain.model.Category
import app.doselog.domain.model.DailyScore
import app.doselog.domain.model.DateRange
import app.doselog.domain.model.LogEntry
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

enum class RangePreset(val label: String) {
    TODAY("Today"),
    DAYS_7("7 days"),
    DAYS_30("30 days"),
    DAYS_90("90 days"),
    THIS_MONTH("This month"),
    ALL_TIME("All time"),
    CUSTOM("Custom range"),
}

/** Filters shared by every report view. Empty sets mean "all". */
@Serializable
data class ReportFilters(
    val preset: RangePreset = RangePreset.DAYS_30,
    val customStartEpochDay: Long? = null,
    val customEndEpochDay: Long? = null,
    val categories: Set<Category> = emptySet(),
    val itemIds: Set<Long> = emptySet(),
) {
    val isAllTime: Boolean get() = preset == RangePreset.ALL_TIME

    val hasEntryFilters: Boolean get() = categories.isNotEmpty() || itemIds.isNotEmpty()

    fun matches(entry: LogEntry): Boolean =
        (categories.isEmpty() || entry.category in categories) && (itemIds.isEmpty() || entry.itemId in itemIds)

    /** The range to query, or null for "All time" (then the data decides). */
    fun requestedRange(today: LocalDate): DateRange? = when (preset) {
        RangePreset.TODAY -> DateRange(today, today)
        RangePreset.DAYS_7 -> DateRange(today.minusDays(6), today)
        RangePreset.DAYS_30 -> DateRange(today.minusDays(29), today)
        RangePreset.DAYS_90 -> DateRange(today.minusDays(89), today)
        RangePreset.THIS_MONTH -> DateRange(today.withDayOfMonth(1), today)
        RangePreset.ALL_TIME -> null
        RangePreset.CUSTOM -> {
            val start = customStartEpochDay?.let(LocalDate::ofEpochDay) ?: today
            val end = customEndEpochDay?.let(LocalDate::ofEpochDay) ?: start
            if (end.isBefore(start)) DateRange(end, start) else DateRange(start, end)
        }
    }
}

/** Everything the report views are derived from. [scores] ignore the item/category filters. */
data class ReportData(
    val range: DateRange,
    val entries: List<LogEntry>,
    val scores: List<DailyScore>,
) {
    val dayScores: Map<LocalDate, DailyScore> = DayGrouping.dayScores(scores)

    companion object {
        val EMPTY = ReportData(DateRange(LocalDate.now(), LocalDate.now()), emptyList(), emptyList())

        /**
         * Applies [filters] to the raw query results. For "All time" the range runs from the
         * first record to today (or the last record, if later).
         */
        fun build(filters: ReportFilters, today: LocalDate, entries: List<LogEntry>, scores: List<DailyScore>): ReportData {
            val range = filters.requestedRange(today) ?: run {
                val dates = entries.map { it.localDate } + scores.map { it.localDate } + today
                DateRange(dates.min(), dates.max())
            }
            return ReportData(
                range = range,
                entries = entries.filter { it.localDate in range && filters.matches(it) }
                    .sortedWith(compareByDescending<LogEntry> { it.takenAt }.thenByDescending { it.id }),
                scores = scores.filter { it.localDate in range }.sortedByDescending { it.scoredAt },
            )
        }
    }
}

// ---- Timeline ----

data class TimelineDay(val date: LocalDate, val dayScore: DailyScore?, val entries: List<LogEntry>)

fun ReportData.timeline(): List<TimelineDay> =
    DayGrouping.groupEntriesByDay(entries).map { (date, dayEntries) -> TimelineDay(date, dayScores[date], dayEntries) }

// ---- Table ----

enum class TableColumn(val title: String) {
    DATE("Date"),
    TIME("Time"),
    ITEM("Item"),
    CATEGORY("Category"),
    AMOUNT("Amount"),
    UNIT("Unit"),
    DESCRIPTION("Description"),
}

@Serializable
data class TableSort(val column: TableColumn = TableColumn.DATE, val ascending: Boolean = false) {
    /** Tapping the active column flips the direction; another column starts ascending (dates descending). */
    fun toggle(target: TableColumn): TableSort =
        if (target == column) copy(ascending = !ascending) else TableSort(target, ascending = target != TableColumn.DATE)
}

fun sortEntries(entries: List<LogEntry>, sort: TableSort): List<LogEntry> {
    val byTime = compareBy<LogEntry> { it.takenAt }.thenBy { it.id }
    val comparator: Comparator<LogEntry> = when (sort.column) {
        TableColumn.DATE -> byTime
        TableColumn.TIME -> compareBy<LogEntry> { it.localDateTime.toLocalTime() }.then(byTime)
        TableColumn.ITEM -> compareBy<LogEntry, String>(String.CASE_INSENSITIVE_ORDER) { it.itemName }.then(byTime)
        TableColumn.CATEGORY -> compareBy<LogEntry> { it.category.displayName }.then(byTime)
        TableColumn.AMOUNT -> compareBy<LogEntry, Double?>(nullsLast()) { it.amount }.then(byTime)
        TableColumn.UNIT -> compareBy<LogEntry, String>(String.CASE_INSENSITIVE_ORDER) { it.unit }.then(byTime)
        TableColumn.DESCRIPTION -> compareBy<LogEntry, String?>(nullsLast(String.CASE_INSENSITIVE_ORDER)) {
            it.description
        }.then(byTime)
    }
    return entries.sortedWith(if (sort.ascending) comparator else comparator.reversed())
}

// ---- Summary ----

data class ItemSummary(
    val itemId: Long,
    val name: String,
    val category: Category,
    val count: Int,
    val totals: List<UnitTotal>,
    /** [totals] divided by the number of days in the range. */
    val averagesPerDay: List<UnitTotal>,
    val countPerDay: Double,
    val first: LogEntry,
    val last: LogEntry,
)

/** One summary per item, most-logged first. [currentNames] lets renamed items show their new name. */
fun ReportData.itemSummaries(currentNames: Map<Long, String> = emptyMap()): List<ItemSummary> {
    val days = range.dayCount.toDouble()
    return entries.groupBy { it.itemId }.map { (itemId, itemEntries) ->
        val totals = UnitTotals.totals(itemEntries)
        val newest = itemEntries.maxWith(compareBy<LogEntry> { it.takenAt }.thenBy { it.id })
        ItemSummary(
            itemId = itemId,
            name = currentNames[itemId] ?: newest.itemName,
            category = newest.category,
            count = itemEntries.size,
            totals = totals,
            averagesPerDay = totals.map { it.copy(total = it.total / days) },
            countPerDay = itemEntries.size / days,
            first = itemEntries.minWith(compareBy<LogEntry> { it.takenAt }.thenBy { it.id }),
            last = newest,
        )
    }.sortedWith(compareByDescending<ItemSummary> { it.count }.thenBy { it.name.lowercase() })
}

// ---- Charts ----

data class ChartPoint(val epochDay: Long, val value: Double)

data class ChartItem(val itemId: Long, val name: String, val units: List<String>)

/** Items present in the data, most-logged first, with their units (most-used first). */
fun ReportData.chartItems(currentNames: Map<Long, String> = emptyMap()): List<ChartItem> =
    itemSummaries(currentNames).map { summary -> ChartItem(summary.itemId, summary.name, summary.totals.map { it.unit }) }

/** Total amount of [itemId] in [unit] for every day of the range (0 on days without intake). */
fun ReportData.dailyTotals(itemId: Long, unit: String): List<ChartPoint> {
    val key = UnitTotals.normalize(unit)
    val perDay = entries
        .filter { it.itemId == itemId && it.amount != null && UnitTotals.normalize(it.unit) == key }
        .groupBy { it.localDate }
        .mapValues { (_, list) -> list.sumOf { it.amount ?: 0.0 } }
    return range.days().map { ChartPoint(it.toEpochDay(), perDay[it] ?: 0.0) }.toList()
}

/** Number of entries of [itemId] for every day of the range. */
fun ReportData.dailyCounts(itemId: Long): List<ChartPoint> {
    val perDay = entries.filter { it.itemId == itemId }.groupingBy { it.localDate }.eachCount()
    return range.days().map { ChartPoint(it.toEpochDay(), (perDay[it] ?: 0).toDouble()) }.toList()
}

/** Day scores in date order; days without a score are left out. */
fun ReportData.dayScoreSeries(): List<ChartPoint> =
    dayScores.entries.sortedBy { it.key }.map { (date, score) -> ChartPoint(date.toEpochDay(), score.score.toDouble()) }

// ---- Calendar ----

data class CalendarDay(
    val date: LocalDate,
    val inMonth: Boolean,
    val inRange: Boolean,
    val dayScore: Int?,
    val entryCount: Int,
)

/** Full weeks covering [month], starting on [firstDayOfWeek]. */
fun ReportData.calendarMonth(month: YearMonth, firstDayOfWeek: DayOfWeek): List<CalendarDay> {
    val counts = entries.groupingBy { it.localDate }.eachCount()
    val start = month.atDay(1).with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
    val lastDayOfWeek = firstDayOfWeek.minus(1)
    val end = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(lastDayOfWeek))
    return DateRange(start, end).days().map { date ->
        CalendarDay(
            date = date,
            inMonth = YearMonth.from(date) == month,
            inRange = date in range,
            dayScore = dayScores[date]?.score,
            entryCount = counts[date] ?: 0,
        )
    }.toList()
}

data class DayDetail(val date: LocalDate, val dayScore: DailyScore?, val scores: List<DailyScore>, val entries: List<LogEntry>)

fun ReportData.dayDetail(date: LocalDate): DayDetail = DayDetail(
    date = date,
    dayScore = dayScores[date],
    scores = scores.filter { it.localDate == date }.sortedBy { it.scoredAt },
    entries = entries.filter { it.localDate == date }.sortedBy { it.takenAt },
)
