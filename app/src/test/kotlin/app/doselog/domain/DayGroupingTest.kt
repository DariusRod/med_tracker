package app.doselog.domain

import app.doselog.domain.model.DateRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate

class DayGroupingTest {

    @Test
    fun entryUsesTheDateOfItsOwnZone() {
        // 23:30 in Sydney is still the afternoon of the previous day in London.
        val sydneyLate = entry(1, instantAt("2026-03-10T23:30", SYDNEY), SYDNEY)
        val londonSameInstant = entry(2, sydneyLate.takenAt, LONDON)

        assertEquals(LocalDate.of(2026, 3, 10), sydneyLate.localDate)
        assertEquals(LocalDate.of(2026, 3, 10), londonSameInstant.localDate)

        val sydneyEarly = entry(3, instantAt("2026-03-11T00:30", SYDNEY), SYDNEY)
        val londonAtThatInstant = entry(4, sydneyEarly.takenAt, LONDON)
        assertEquals(LocalDate.of(2026, 3, 11), sydneyEarly.localDate)
        assertEquals(LocalDate.of(2026, 3, 10), londonAtThatInstant.localDate)
    }

    @Test
    fun groupsByDayNewestFirst() {
        val entries = listOf(
            entry(1, instantAt("2026-10-27T08:00", SYDNEY)),
            entry(2, instantAt("2026-10-28T09:00", SYDNEY)),
            entry(3, instantAt("2026-10-28T21:00", SYDNEY)),
            entry(4, instantAt("2026-10-26T23:59", SYDNEY)),
        )
        val groups = DayGrouping.groupEntriesByDay(entries)

        assertEquals(
            listOf(LocalDate.of(2026, 10, 28), LocalDate.of(2026, 10, 27), LocalDate.of(2026, 10, 26)),
            groups.keys.toList(),
        )
        assertEquals(listOf(3L, 2L), groups.getValue(LocalDate.of(2026, 10, 28)).map { it.id })
    }

    @Test
    fun dstStartDayKeepsEntriesOnTheRightDate() {
        // Sydney DST starts 2026-10-04 at 02:00 (clocks jump to 03:00); that day has 23 hours.
        val beforeJump = entry(1, instantAt("2026-10-04T01:30", SYDNEY))
        val afterJump = entry(2, instantAt("2026-10-04T03:30", SYDNEY))
        val lateThatDay = entry(3, instantAt("2026-10-04T23:59", SYDNEY))
        val nextDay = entry(4, instantAt("2026-10-05T00:00", SYDNEY))

        val groups = DayGrouping.groupEntriesByDay(listOf(beforeJump, afterJump, lateThatDay, nextDay))
        assertEquals(setOf(1L, 2L, 3L), groups.getValue(LocalDate.of(2026, 10, 4)).map { it.id }.toSet())
        assertEquals(listOf(4L), groups.getValue(LocalDate.of(2026, 10, 5)).map { it.id })
        assertEquals(Duration.ofHours(1), Duration.between(beforeJump.takenAt, afterJump.takenAt))
    }

    @Test
    fun dstEndDayKeepsBothRepeatedHoursOnTheSameDate() {
        // Sydney DST ends 2026-04-05 at 03:00 (clocks fall back to 02:00); 02:30 happens twice.
        val firstPass = LocalDate.of(2026, 4, 5).atTime(2, 30).atZone(SYDNEY).withEarlierOffsetAtOverlap()
        val secondPass = firstPass.withLaterOffsetAtOverlap()
        val a = entry(1, firstPass.toInstant())
        val b = entry(2, secondPass.toInstant())

        assertEquals(Duration.ofHours(1), Duration.between(a.takenAt, b.takenAt))
        val groups = DayGrouping.groupEntriesByDay(listOf(a, b))
        assertEquals(listOf(2L, 1L), groups.getValue(LocalDate.of(2026, 4, 5)).map { it.id })
    }

    @Test
    fun dayScoreIsTheLatestScoreOfEachDay() {
        val scores = listOf(
            score(1, 5, instantAt("2026-10-28T08:00", SYDNEY)),
            score(2, 15, instantAt("2026-10-28T20:00", SYDNEY)),
            score(3, 9, instantAt("2026-10-28T12:00", SYDNEY)),
            score(4, 12, instantAt("2026-10-27T23:59", SYDNEY)),
        )
        val dayScores = DayGrouping.dayScores(scores)

        assertEquals(15, dayScores.getValue(LocalDate.of(2026, 10, 28)).score)
        assertEquals(12, dayScores.getValue(LocalDate.of(2026, 10, 27)).score)
    }

    @Test
    fun sameInstantTieIsBrokenByNewestId() {
        val at = instantAt("2026-10-28T08:00", SYDNEY)
        val dayScores = DayGrouping.dayScores(listOf(score(7, 3, at), score(8, 18, at)))
        assertEquals(18, dayScores.getValue(LocalDate.of(2026, 10, 28)).score)
    }

    @Test
    fun queryWindowCoversRecordsFromAnyZone() {
        val range = DateRange(LocalDate.of(2026, 10, 28), LocalDate.of(2026, 10, 28))
        val (from, to) = DayGrouping.queryWindow(range, SYDNEY)

        // The earliest and latest instants anywhere on earth that are still "28 Oct" locally.
        val earliest = LocalDate.of(2026, 10, 28).atStartOfDay(java.time.ZoneOffset.ofHours(14)).toInstant()
        val latest = LocalDate.of(2026, 10, 29).atStartOfDay(java.time.ZoneOffset.ofHours(-12)).toInstant()
        assertTrue(!from.isAfter(earliest))
        assertTrue(to.isAfter(latest))
    }

    @Test
    fun dateRangeCountsBothEnds() {
        val range = DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
        assertEquals(31, range.dayCount)
        assertEquals(31, range.days().count())
        assertTrue(LocalDate.of(2026, 10, 31) in range)
        assertTrue(LocalDate.of(2026, 11, 1) !in range)
    }
}
