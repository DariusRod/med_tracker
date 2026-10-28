package app.doselog.export

import app.doselog.data.db.DailyScoreEntity
import app.doselog.data.db.ItemEntity
import app.doselog.data.db.ItemOptionEntity
import app.doselog.data.db.LogEntryEntity
import app.doselog.domain.AmountFormat
import app.doselog.domain.model.DailyScore
import app.doselog.domain.model.LogEntry
import app.doselog.domain.model.parseZone
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * The CSV files. Raw tables keep every column (plus epoch millis, so nothing is lost); times are
 * ISO 8601 local time with offset in the zone the record was made in, e.g. 2026-10-28T08:15:00+11:00.
 */
object CsvTables {
    private val ISO_WITH_OFFSET = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ssXXX")
    private val DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd")
    private val TIME = DateTimeFormatter.ofPattern("HH:mm")

    val ITEMS_HEADER = listOf("id", "name", "category", "default_unit", "is_archived", "sort_order", "created_at", "created_at_epoch_ms")
    val OPTIONS_HEADER = listOf("id", "item_id", "amount", "unit", "label", "sort_order")
    val ENTRIES_HEADER = listOf(
        "id", "item_id", "option_id", "item_name", "category", "amount", "unit", "label", "taken_at", "taken_at_epoch_ms",
        "zone_id", "recorded_at", "recorded_at_epoch_ms", "updated_at", "updated_at_epoch_ms", "description",
    )
    val SCORES_HEADER = listOf("id", "score", "scored_at", "scored_at_epoch_ms", "zone_id", "recorded_at", "recorded_at_epoch_ms", "note")
    val FLAT_HEADER = listOf(
        "date", "time", "item", "category", "amount", "unit", "label", "description", "day_score", "taken_at", "time_zone",
    )
    val SCORE_LIST_HEADER = listOf("date", "time", "score", "is_day_score", "note", "scored_at", "time_zone")

    fun iso(millis: Long, zone: ZoneId): String = iso(Instant.ofEpochMilli(millis).atZone(zone))

    fun iso(dateTime: ZonedDateTime): String = dateTime.truncatedTo(ChronoUnit.SECONDS).format(ISO_WITH_OFFSET)

    private fun number(value: Double?): String = value?.let(AmountFormat::exact).orEmpty()

    fun writeItems(writer: CsvWriter, items: List<ItemEntity>, zone: ZoneId) {
        writer.writeRow(ITEMS_HEADER)
        items.forEach {
            writer.writeRow(
                it.id.toString(), it.name, it.category.name, it.defaultUnit, it.isArchived.toString(),
                it.sortOrder.toString(), iso(it.createdAt, zone), it.createdAt.toString(),
            )
        }
    }

    fun writeOptions(writer: CsvWriter, options: List<ItemOptionEntity>) {
        writer.writeRow(OPTIONS_HEADER)
        options.forEach {
            writer.writeRow(it.id.toString(), it.itemId.toString(), number(it.amount), it.unit, it.label, it.sortOrder.toString())
        }
    }

    fun writeEntries(writer: CsvWriter, entries: List<LogEntryEntity>) {
        writer.writeRow(ENTRIES_HEADER)
        entries.forEach {
            val zone = parseZone(it.zoneId)
            writer.writeRow(
                it.id.toString(), it.itemId.toString(), it.optionId?.toString(), it.itemNameSnapshot, it.categorySnapshot,
                number(it.amountSnapshot), it.unitSnapshot, it.labelSnapshot, iso(it.takenAt, zone), it.takenAt.toString(),
                it.zoneId, iso(it.recordedAt, zone), it.recordedAt.toString(), it.updatedAt?.let { u -> iso(u, zone) },
                it.updatedAt?.toString(), it.description,
            )
        }
    }

    fun writeScores(writer: CsvWriter, scores: List<DailyScoreEntity>) {
        writer.writeRow(SCORES_HEADER)
        scores.forEach {
            val zone = parseZone(it.zoneId)
            writer.writeRow(
                it.id.toString(), it.score.toString(), iso(it.scoredAt, zone), it.scoredAt.toString(), it.zoneId,
                iso(it.recordedAt, zone), it.recordedAt.toString(), it.note,
            )
        }
    }

    /** One human-friendly row per entry, with separate date and time columns and the day score. */
    fun writeEntriesFlat(writer: CsvWriter, entries: List<LogEntry>, dayScores: Map<LocalDate, DailyScore>) {
        writer.writeRow(FLAT_HEADER)
        entries.sortedWith(compareBy<LogEntry> { it.takenAt }.thenBy { it.id }).forEach {
            writer.writeRow(
                it.localDateTime.format(DATE), it.localDateTime.format(TIME), it.itemName, it.category.displayName,
                number(it.amount), it.unit, it.label, it.description, dayScores[it.localDate]?.score?.toString(),
                iso(it.localDateTime), it.zone.id,
            )
        }
    }

    fun writeScoreList(writer: CsvWriter, scores: List<DailyScore>, dayScores: Map<LocalDate, DailyScore>) {
        writer.writeRow(SCORE_LIST_HEADER)
        scores.sortedWith(compareBy<DailyScore> { it.scoredAt }.thenBy { it.id }).forEach {
            writer.writeRow(
                it.localDateTime.format(DATE), it.localDateTime.format(TIME), it.score.toString(),
                (dayScores[it.localDate]?.id == it.id).toString(), it.note, iso(it.localDateTime), it.zone.id,
            )
        }
    }
}
