package app.doselog.export

import app.doselog.data.db.DailyScoreEntity
import app.doselog.data.db.ItemEntity
import app.doselog.data.db.ItemOptionEntity
import app.doselog.data.db.LogEntryEntity
import app.doselog.domain.DayGrouping
import app.doselog.domain.model.Category
import app.doselog.domain.model.toDomain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** Minimal RFC 4180 reader used to check that what we write parses back unchanged. */
object CsvParser {
    fun parse(text: String): List<List<String>> {
        val input = text.removePrefix(CsvWriter.BOM)
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < input.length) {
            val c = input[i]
            when {
                inQuotes && c == '"' && input.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                inQuotes && c == '"' -> inQuotes = false
                inQuotes -> field.append(c)
                c == '"' -> inQuotes = true
                c == ',' -> { row += field.toString(); field.clear() }
                c == '\r' && input.getOrNull(i + 1) == '\n' -> {
                    row += field.toString(); field.clear(); rows += row; row = mutableListOf(); i++
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row += field.toString(); rows += row }
        return rows
    }
}

class CsvWriterTest {

    @Test
    fun plainFieldsAreNotQuoted() {
        assertEquals("Ibuprofen", CsvWriter.escape("Ibuprofen"))
        assertEquals("", CsvWriter.escape(""))
        assertEquals("café", CsvWriter.escape("café"))
    }

    @Test
    fun specialCharactersAreQuoted() {
        assertEquals("\"a,b\"", CsvWriter.escape("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", CsvWriter.escape("say \"hi\""))
        assertEquals("\"line1\nline2\"", CsvWriter.escape("line1\nline2"))
        assertEquals("\"line1\r\nline2\"", CsvWriter.escape("line1\r\nline2"))
        assertEquals("\"\"\"\"", CsvWriter.escape("\""))
    }

    @Test
    fun rowsUseCrlfAndNullsAreEmpty() {
        val out = StringBuilder()
        CsvWriter(out).apply {
            writeBom()
            writeRow("a", null, "c,d")
            writeRow(listOf("1"))
        }
        assertEquals("\uFEFFa,,\"c,d\"\r\n1\r\n", out.toString())
    }
}

class CsvRoundTripTest {

    private val sydney = ZoneId.of("Australia/Sydney")
    private fun millis(text: String, zone: ZoneId = sydney) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private val items = listOf(
        ItemEntity(1, "Ibuprofen, 200", Category.MEDICATION, "mg", false, 0, millis("2026-10-01T09:00")),
        ItemEntity(2, "Crème \"brûlée\"", Category.FOOD, null, true, 1, millis("2026-10-02T09:00")),
    )
    private val options = listOf(
        ItemOptionEntity(1, 1, 200.0, "mg", "200 mg", 0),
        ItemOptionEntity(2, 1, 0.1234567, "g", "a,\nb", 1),
        ItemOptionEntity(3, 2, null, "", "Small bowl", 0),
    )
    private val entries = listOf(
        LogEntryEntity(1, 1, 1, "Ibuprofen, 200", "MEDICATION", 200.0, "mg", "200 mg", millis("2026-10-28T08:15"),
            "Australia/Sydney", millis("2026-10-28T08:16"), null, "after \"lunch\",\r\nwith water"),
        LogEntryEntity(2, 2, null, "Crème \"brûlée\"", "FOOD", null, "", "Small bowl", millis("2026-10-27T23:30", ZoneId.of("Europe/London")),
            "Europe/London", millis("2026-10-27T23:31", ZoneId.of("Europe/London")), millis("2026-10-28T10:00"), null),
    )
    private val scores = listOf(
        DailyScoreEntity(1, 15, millis("2026-10-28T09:00"), "Australia/Sydney", millis("2026-10-28T09:00"), "good, 😀"),
        DailyScoreEntity(2, 1, millis("2026-10-28T21:00"), "Australia/Sydney", millis("2026-10-28T21:00"), null),
    )

    private fun write(block: (CsvWriter) -> Unit): String = StringBuilder().also { CsvWriter(it).apply { writeBom() }.also(block) }.toString()

    private fun List<String>.nullIfEmpty(i: Int) = this[i].ifEmpty { null }

    @Test
    fun rawTablesRoundTrip() {
        val itemRows = CsvParser.parse(write { CsvTables.writeItems(it, items, sydney) })
        assertEquals(CsvTables.ITEMS_HEADER, itemRows.first())
        val parsedItems = itemRows.drop(1).map {
            ItemEntity(it[0].toLong(), it[1], Category.valueOf(it[2]), it.nullIfEmpty(3), it[4].toBoolean(), it[5].toInt(), it[7].toLong())
        }
        assertEquals(items, parsedItems)

        val optionRows = CsvParser.parse(write { CsvTables.writeOptions(it, options) })
        val parsedOptions = optionRows.drop(1).map {
            ItemOptionEntity(it[0].toLong(), it[1].toLong(), it.nullIfEmpty(2)?.toDouble(), it[3], it[4], it[5].toInt())
        }
        assertEquals(options, parsedOptions)

        val entryRows = CsvParser.parse(write { CsvTables.writeEntries(it, entries) })
        assertEquals(CsvTables.ENTRIES_HEADER, entryRows.first())
        val parsedEntries = entryRows.drop(1).map {
            LogEntryEntity(
                it[0].toLong(), it[1].toLong(), it.nullIfEmpty(2)?.toLong(), it[3], it[4], it.nullIfEmpty(5)?.toDouble(), it[6], it[7],
                it[9].toLong(), it[10], it[12].toLong(), it.nullIfEmpty(14)?.toLong(), it.nullIfEmpty(15),
            )
        }
        assertEquals(entries, parsedEntries)

        val scoreRows = CsvParser.parse(write { CsvTables.writeScores(it, scores) })
        val parsedScores = scoreRows.drop(1).map {
            DailyScoreEntity(it[0].toLong(), it[1].toInt(), it[3].toLong(), it[4], it[6].toLong(), it.nullIfEmpty(7))
        }
        assertEquals(scores, parsedScores)
    }

    @Test
    fun timesAreIsoLocalWithOffsetOfTheRecordsZone() {
        val rows = CsvParser.parse(write { CsvTables.writeEntries(it, entries) })
        assertEquals("2026-10-28T08:15:00+11:00", rows[1][8])
        assertEquals("2026-10-27T23:30:00Z", rows[2][8]) // London is on GMT (UTC+0) after 25 Oct
    }

    @Test
    fun flatFileHasSeparateDateTimeAndDayScore() {
        val domainEntries = entries.map { it.toDomain() }
        val dayScores = DayGrouping.dayScores(scores.map { it.toDomain() })
        val rows = CsvParser.parse(write { CsvTables.writeEntriesFlat(it, domainEntries, dayScores) })
        assertEquals(CsvTables.FLAT_HEADER, rows.first())
        val sydneyRow = rows.first { it[2] == "Ibuprofen, 200" }
        assertEquals("2026-10-28", sydneyRow[0])
        assertEquals("08:15", sydneyRow[1])
        assertEquals("200", sydneyRow[4])
        assertEquals("after \"lunch\",\r\nwith water", sydneyRow[7])
        assertEquals("1", sydneyRow[8]) // latest score of the day, not the first one
        assertEquals("2026-10-28T08:15:00+11:00", sydneyRow[9])
    }

    @Test
    fun outputStartsWithBomAndUsesCrlf() {
        val text = write { CsvTables.writeOptions(it, options) }
        assertTrue(text.startsWith("\uFEFF"))
        assertTrue(text.removeSuffix("\r\n").split("\r\n").size >= options.size + 1)
    }

    @Test
    fun exportFileNamesIncludeATimestamp() {
        val now = LocalDateTime.parse("2026-10-28T08:15").atZone(sydney)
        assertEquals("doselog_2026-10-28_0815.zip", ExportKind.CSV_ZIP.fileName(now))
        assertEquals("doselog_2026-10-28_0815.db", ExportKind.DATABASE.fileName(now))
        assertEquals("doselog_backup_2026-10-28_0815.json", ExportKind.JSON_BACKUP.fileName(now))
    }
}
