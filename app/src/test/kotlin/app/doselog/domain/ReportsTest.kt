package app.doselog.domain

import app.doselog.domain.model.Category
import app.doselog.domain.model.DateRange
import app.doselog.domain.reports.RangePreset
import app.doselog.domain.reports.ReportData
import app.doselog.domain.reports.ReportFilters
import app.doselog.domain.reports.TableColumn
import app.doselog.domain.reports.TableSort
import app.doselog.domain.reports.calendarMonth
import app.doselog.domain.reports.dailyTotals
import app.doselog.domain.reports.itemSummaries
import app.doselog.domain.reports.sortEntries
import app.doselog.domain.reports.timeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class ReportsTest {

    private val today = LocalDate.of(2026, 10, 28)

    private val entries = listOf(
        entry(1, instantAt("2026-10-28T08:00", SYDNEY), amount = 200.0, unit = "mg"),
        entry(2, instantAt("2026-10-27T08:00", SYDNEY), amount = 400.0, unit = "mg"),
        entry(3, instantAt("2026-10-27T09:00", SYDNEY), amount = 2.0, unit = "pill"),
        entry(4, instantAt("2026-10-26T07:00", SYDNEY), amount = null, unit = "", itemId = 2, itemName = "Porridge", category = Category.FOOD),
        entry(5, instantAt("2026-09-01T07:00", SYDNEY), amount = 1.0, unit = "mg"),
    )
    private val scores = listOf(
        score(1, 9, instantAt("2026-10-27T10:00", SYDNEY)),
        score(2, 14, instantAt("2026-10-27T21:00", SYDNEY)),
    )

    @Test
    fun presetsResolveToInclusiveRanges() {
        assertEquals(DateRange(today, today), ReportFilters(RangePreset.TODAY).requestedRange(today))
        assertEquals(7, ReportFilters(RangePreset.DAYS_7).requestedRange(today)!!.dayCount)
        assertEquals(30, ReportFilters(RangePreset.DAYS_30).requestedRange(today)!!.dayCount)
        assertEquals(90, ReportFilters(RangePreset.DAYS_90).requestedRange(today)!!.dayCount)
        assertEquals(LocalDate.of(2026, 10, 1), ReportFilters(RangePreset.THIS_MONTH).requestedRange(today)!!.start)
        assertEquals(null, ReportFilters(RangePreset.ALL_TIME).requestedRange(today))
        val custom = ReportFilters(RangePreset.CUSTOM, today.toEpochDay(), today.minusDays(3).toEpochDay())
        assertEquals(DateRange(today.minusDays(3), today), custom.requestedRange(today))
    }

    @Test
    fun buildFiltersByRangeCategoryAndItem() {
        val week = ReportData.build(ReportFilters(RangePreset.DAYS_7), today, entries, scores)
        assertEquals(listOf(1L, 3L, 2L, 4L), week.entries.map { it.id })

        val foodOnly = ReportData.build(ReportFilters(RangePreset.DAYS_7, categories = setOf(Category.FOOD)), today, entries, scores)
        assertEquals(listOf(4L), foodOnly.entries.map { it.id })

        val item1 = ReportData.build(ReportFilters(RangePreset.DAYS_7, itemIds = setOf(1)), today, entries, scores)
        assertEquals(3, item1.entries.size)
        assertEquals(2, item1.scores.size) // scores ignore item filters
    }

    @Test
    fun allTimeStartsAtTheFirstRecord() {
        val all = ReportData.build(ReportFilters(RangePreset.ALL_TIME), today, entries, scores)
        assertEquals(LocalDate.of(2026, 9, 1), all.range.start)
        assertEquals(today, all.range.endInclusive)
        assertEquals(5, all.entries.size)
    }

    @Test
    fun timelineShowsTheDayScore() {
        val week = ReportData.build(ReportFilters(RangePreset.DAYS_7), today, entries, scores)
        val days = week.timeline()
        assertEquals(listOf(today, today.minusDays(1), today.minusDays(2)), days.map { it.date })
        assertEquals(14, days[1].dayScore?.score)
    }

    @Test
    fun tableSortsByEachColumnBothWays() {
        val list = entries.take(4)
        assertEquals(listOf(1L, 3L, 2L, 4L), sortEntries(list, TableSort(TableColumn.DATE, ascending = false)).map { it.id })
        assertEquals(listOf(4L, 2L, 3L, 1L), sortEntries(list, TableSort(TableColumn.DATE, ascending = true)).map { it.id })
        assertEquals(listOf(3L, 1L, 2L, 4L), sortEntries(list, TableSort(TableColumn.AMOUNT, ascending = true)).map { it.id })
        assertEquals("Ibuprofen", sortEntries(list, TableSort(TableColumn.ITEM, ascending = true)).first().itemName)
        assertEquals("Porridge", sortEntries(list, TableSort(TableColumn.ITEM, ascending = false)).first().itemName)
        assertEquals(TableSort(TableColumn.DATE, ascending = true), TableSort().toggle(TableColumn.DATE))
        assertEquals(TableSort(TableColumn.UNIT, ascending = true), TableSort().toggle(TableColumn.UNIT))
    }

    @Test
    fun summaryTotalsPerUnitAndAveragesPerDay() {
        val week = ReportData.build(ReportFilters(RangePreset.DAYS_7), today, entries, scores)
        val ibuprofen = week.itemSummaries(mapOf(1L to "Advil")).first { it.itemId == 1L }
        assertEquals("Advil", ibuprofen.name)
        assertEquals(3, ibuprofen.count)
        assertEquals(listOf(UnitTotal("mg", 600.0, 2), UnitTotal("pill", 2.0, 1)), ibuprofen.totals)
        assertEquals(600.0 / 7, ibuprofen.averagesPerDay.first { it.unit == "mg" }.total, 1e-9)
        assertEquals(2L, ibuprofen.first.id)
        assertEquals(1L, ibuprofen.last.id)
    }

    @Test
    fun dailyTotalsCoverEveryDayWithZeros() {
        val week = ReportData.build(ReportFilters(RangePreset.DAYS_7), today, entries, scores)
        val totals = week.dailyTotals(1, "MG")
        assertEquals(7, totals.size)
        assertEquals(200.0, totals.last().value, 0.0)
        assertEquals(400.0, totals[totals.size - 2].value, 0.0)
        assertTrue(totals.dropLast(2).all { it.value == 0.0 })
    }

    @Test
    fun calendarCoversWholeWeeks() {
        val month = ReportData.build(ReportFilters(RangePreset.THIS_MONTH), today, entries, scores)
            .calendarMonth(YearMonth.of(2026, 10), DayOfWeek.MONDAY)
        assertEquals(0, month.size % 7)
        assertEquals(DayOfWeek.MONDAY, month.first().date.dayOfWeek)
        assertEquals(LocalDate.of(2026, 9, 28), month.first().date) // 1 Oct 2026 is a Thursday
        val oct27 = month.first { it.date == LocalDate.of(2026, 10, 27) }
        assertEquals(14, oct27.dayScore)
        assertEquals(2, oct27.entryCount)
        assertTrue(month.first { it.date == LocalDate.of(2026, 10, 29) }.let { it.inMonth && !it.inRange })
    }
}
