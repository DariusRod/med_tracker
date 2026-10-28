package app.doselog.export

import app.doselog.data.db.DailyScoreEntity
import app.doselog.data.db.ItemEntity
import app.doselog.data.db.ItemOptionEntity
import app.doselog.data.db.LogEntryEntity
import app.doselog.domain.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {

    private val snapshot = DatabaseSnapshot(
        items = listOf(
            ItemEntity(1, "Ibuprofen", Category.MEDICATION, "mg", false, 0, 1_000),
            ItemEntity(2, "Porridge", Category.FOOD, null, true, 1, 2_000),
        ),
        options = listOf(ItemOptionEntity(10, 1, 200.0, "mg", "200 mg", 0), ItemOptionEntity(11, 2, null, "", "Bowl", 0)),
        entries = listOf(
            LogEntryEntity(100, 1, 10, "Ibuprofen", "MEDICATION", 200.0, "mg", "200 mg", 5_000, "Australia/Sydney", 5_001, null, "note"),
            LogEntryEntity(101, 2, null, "Porridge", "FOOD", null, "", "Bowl", 6_000, "Europe/London", 6_001, 7_000, null),
        ),
        scores = listOf(DailyScoreEntity(1000, 12, 8_000, "Australia/Sydney", 8_001, "ok")),
    )

    @Test
    fun jsonRoundTripKeepsEveryField() {
        val text = BackupJson.encode(BackupJson.fromSnapshot(snapshot, 1, "1.0.0", "2026-10-28T08:15:00+11:00"))
        val (restored, errors) = BackupJson.toSnapshot(BackupJson.decode(text))
        assertTrue(errors.isEmpty())
        assertEquals(snapshot, restored)
        assertEquals(SnapshotCounts(2, 2, 2, 1), restored.counts)
    }

    @Test
    fun unknownCategoryAndNewerFormatAreReported() {
        val file = BackupJson.fromSnapshot(snapshot, 1, "1.0.0", "x")
        val tampered = file.copy(formatVersion = 99, items = file.items.map { it.copy(category = "POTION") })
        val (_, errors) = BackupJson.toSnapshot(tampered)
        assertTrue(errors.any { "newer version" in it })
        assertTrue(errors.any { "unknown category" in it })
    }

    @Test
    fun validSnapshotHasNoProblems() {
        assertEquals(emptyList<String>(), SnapshotValidator.validate(snapshot))
    }

    @Test
    fun validatorFindsBrokenReferencesAndValues() {
        val broken = snapshot.copy(
            options = snapshot.options + ItemOptionEntity(12, 99, -1.0, "mg", "x", 0),
            entries = snapshot.entries + snapshot.entries[0].copy(id = 102, itemId = 2, optionId = 10, zoneId = "Mars/Base"),
            scores = snapshot.scores + DailyScoreEntity(1000, 21, 1, "UTC", 1, null),
        )
        val errors = SnapshotValidator.validate(broken).joinToString("\n")
        assertTrue(errors, "duplicate ids" in errors)
        assertTrue(errors, "missing item 99" in errors)
        assertTrue(errors, "invalid amount" in errors)
        assertTrue(errors, "invalid preset" in errors)
        assertTrue(errors, "invalid time zone" in errors)
    }

    @Test
    fun scoreOutOfRangeIsRejected() {
        val errors = SnapshotValidator.validate(snapshot.copy(scores = listOf(DailyScoreEntity(1, 0, 1, "UTC", 1, null))))
        assertTrue(errors.single().contains("1–20"))
    }
}
