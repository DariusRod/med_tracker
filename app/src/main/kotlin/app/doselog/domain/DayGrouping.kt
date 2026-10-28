package app.doselog.domain

import app.doselog.domain.model.DailyScore
import app.doselog.domain.model.DateRange
import app.doselog.domain.model.LogEntry
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Day logic. A record belongs to the local date in the zone it was recorded in, so an entry
 * logged at 23:30 in Sydney stays on that day even if the phone later moves to another zone,
 * and DST changes never shift entries to a neighbouring day.
 */
object DayGrouping {

    // Wider than the largest possible difference between two UTC offsets (26 h).
    private val PADDING: Duration = Duration.ofHours(30)

    /**
     * A UTC window guaranteed to contain every record whose own local date is inside [range].
     * Query with this window, then filter on the record's local date.
     */
    fun queryWindow(range: DateRange, deviceZone: ZoneId): Pair<Instant, Instant> {
        val from = range.start.atStartOfDay(deviceZone).toInstant().minus(PADDING)
        val to = range.endInclusive.plusDays(1).atStartOfDay(deviceZone).toInstant().plus(PADDING)
        return from to to
    }

    /** Entries grouped by their own local date, newest day first, newest entry first. */
    fun groupEntriesByDay(entries: List<LogEntry>): Map<LocalDate, List<LogEntry>> =
        entries
            .sortedWith(compareByDescending<LogEntry> { it.takenAt }.thenByDescending { it.id })
            .groupBy { it.localDate }
            .toSortedMap(compareByDescending { it })

    /** The "day score": the latest score recorded on each local date. */
    fun dayScores(scores: List<DailyScore>): Map<LocalDate, DailyScore> =
        scores
            .groupBy { it.localDate }
            .mapValues { (_, sameDay) ->
                sameDay.maxWith(compareBy<DailyScore> { it.scoredAt }.thenBy { it.id })
            }
}
