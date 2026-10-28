package app.doselog.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

data class Item(
    val id: Long,
    val name: String,
    val category: Category,
    val defaultUnit: String?,
    val isArchived: Boolean,
    val sortOrder: Int,
    val createdAt: Instant,
    val options: List<ItemOption>,
)

data class ItemOption(
    val id: Long,
    val itemId: Long,
    val amount: Double?,
    val unit: String,
    val label: String,
    val sortOrder: Int,
)

data class LogEntry(
    val id: Long,
    val itemId: Long,
    val optionId: Long?,
    val itemName: String,
    val category: Category,
    val amount: Double?,
    val unit: String,
    val label: String,
    val takenAt: Instant,
    val zone: ZoneId,
    val recordedAt: Instant,
    val updatedAt: Instant?,
    val description: String?,
) {
    /** When it was taken, in the zone the user was in at the time. */
    val localDateTime: ZonedDateTime = takenAt.atZone(zone)
    val localDate: LocalDate = localDateTime.toLocalDate()
}

data class DailyScore(
    val id: Long,
    val score: Int,
    val scoredAt: Instant,
    val zone: ZoneId,
    val recordedAt: Instant,
    val note: String?,
) {
    val localDateTime: ZonedDateTime = scoredAt.atZone(zone)
    val localDate: LocalDate = localDateTime.toLocalDate()
}

/** An inclusive range of local dates. */
data class DateRange(val start: LocalDate, val endInclusive: LocalDate) {
    init {
        require(!endInclusive.isBefore(start)) { "Range end $endInclusive is before start $start" }
    }

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(endInclusive)

    /** Number of days in the range, counting both ends. */
    val dayCount: Long get() = endInclusive.toEpochDay() - start.toEpochDay() + 1

    fun days(): Sequence<LocalDate> = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(endInclusive) }
}
