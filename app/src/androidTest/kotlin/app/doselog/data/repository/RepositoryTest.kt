package app.doselog.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import app.doselog.data.db.AppDatabase
import app.doselog.domain.model.Category
import app.doselog.domain.model.DateRange
import app.doselog.domain.model.ItemDraft
import app.doselog.domain.model.NameError
import app.doselog.domain.model.OptionDraft
import app.doselog.domain.model.OptionError
import app.doselog.testing.FakeTimeSource
import app.doselog.testing.LONDON
import app.doselog.testing.SYDNEY
import app.doselog.testing.at
import app.doselog.testing.inMemoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class RepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var time: FakeTimeSource
    private lateinit var items: ItemRepository
    private lateinit var log: LogRepository
    private lateinit var scores: ScoreRepository

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        time = FakeTimeSource()
        items = ItemRepository(db, time)
        log = LogRepository(db, time)
        scores = ScoreRepository(db, time)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun saveIbuprofen(): Long {
        val draft = ItemDraft(
            id = null,
            name = "  Ibuprofen ",
            category = Category.MEDICATION,
            defaultUnit = "mg",
            options = listOf(
                OptionDraft(id = null, amount = 200.0, unit = "mg", label = ""),
                OptionDraft(id = null, amount = 2.0, unit = "pill", label = "Two pills"),
            ),
        )
        return (items.saveItem(draft) as SaveItemResult.Saved).id
    }

    // ---- items ----

    @Test
    fun savingTrimsNameAndAutoLabelsPresets() = runTest {
        val id = saveIbuprofen()
        val item = items.getItem(id)!!
        assertEquals("Ibuprofen", item.name)
        assertEquals(listOf("200 mg", "Two pills"), item.options.map { it.label })
        assertEquals(0, item.sortOrder)
    }

    @Test
    fun validationErrors() = runTest {
        saveIbuprofen()
        val empty = items.saveItem(ItemDraft(null, "   ", Category.FOOD, null, emptyList()))
        assertEquals(NameError.REQUIRED, (empty as SaveItemResult.Invalid).errors.name)

        val duplicate = items.saveItem(ItemDraft(null, "IBUPROFEN", Category.FOOD, null, emptyList()))
        assertEquals(NameError.DUPLICATE, (duplicate as SaveItemResult.Invalid).errors.name)

        val badOption = items.saveItem(
            ItemDraft(null, "Toast", Category.FOOD, null, listOf(OptionDraft(null, null, "", ""))),
        )
        assertEquals(mapOf(0 to OptionError.NEEDS_AMOUNT_OR_LABEL), (badOption as SaveItemResult.Invalid).errors.options)
        assertEquals(1, items.observeAllItems().first().size)
    }

    @Test
    fun archivedNamesCanBeReusedButNotRestoredOverAnActiveOne() = runTest {
        val first = saveIbuprofen()
        items.setArchived(first, archived = true)
        val second = saveIbuprofen()
        assertTrue(second != first)
        assertEquals(ArchiveResult.DuplicateName, items.setArchived(first, archived = false))
        items.observeActiveItems().test { assertEquals(listOf(second), awaitItem().map { it.id }) }
    }

    @Test
    fun editingPresetsUpdatesReordersAndDeletes() = runTest {
        val id = saveIbuprofen()
        val item = items.getItem(id)!!
        val (p200, pTwo) = item.options
        val result = items.saveItem(
            ItemDraft(
                id = id,
                name = "Ibuprofen",
                category = Category.MEDICATION,
                defaultUnit = "mg",
                options = listOf(
                    OptionDraft(null, 400.0, "mg", ""),
                    OptionDraft(p200.id, 250.0, "mg", ""),
                ),
            ),
        )
        assertTrue(result is SaveItemResult.Saved)
        val updated = items.getItem(id)!!
        assertEquals(listOf("400 mg", "250 mg"), updated.options.map { it.label })
        assertEquals(p200.id, updated.options[1].id)
        assertTrue(updated.options.none { it.id == pTwo.id })
    }

    @Test
    fun deleteOnlyWhenThereAreNoEntries() = runTest {
        val id = saveIbuprofen()
        val option = items.getItem(id)!!.options.first()
        log.addEntry(NewLogEntry(id, DoseSelection.Preset(option.id), null, time.now(), SYDNEY))

        assertEquals(DeleteItemResult.HasEntries(1), items.deleteItem(id))
        assertEquals(1, items.observeAllItems().first().size)

        val empty = (items.saveItem(ItemDraft(null, "Toast", Category.FOOD, null, emptyList())) as SaveItemResult.Saved).id
        assertEquals(DeleteItemResult.Deleted, items.deleteItem(empty))
        assertEquals(DeleteItemResult.NotFound, items.deleteItem(empty))
    }

    @Test
    fun moveItemReorders() = runTest {
        val a = saveIbuprofen()
        val b = (items.saveItem(ItemDraft(null, "Toast", Category.FOOD, null, emptyList())) as SaveItemResult.Saved).id
        items.moveItem(b, up = true)
        assertEquals(listOf(b, a), items.observeActiveItems().first().map { it.id })
        items.moveItem(b, up = true) // already first: no-op
        assertEquals(listOf(b, a), items.observeActiveItems().first().map { it.id })
    }

    // ---- log entries ----

    @Test
    fun entrySnapshotsSurviveRenameAndPresetDeletion() = runTest {
        val id = saveIbuprofen()
        val option = items.getItem(id)!!.options.first()
        val entry = log.addEntry(NewLogEntry(id, DoseSelection.Preset(option.id), "  after food ", time.now(), SYDNEY))
        assertEquals("200 mg", entry.label)
        assertEquals("after food", entry.description)
        assertEquals(time.now(), entry.recordedAt)

        items.saveItem(ItemDraft(id, "Advil", Category.MEDICATION, "mg", emptyList()))

        val after = log.getEntry(entry.id)!!
        assertEquals("Ibuprofen", after.itemName)
        assertEquals("200 mg", after.label)
        assertEquals(200.0, after.amount!!, 0.0)
        assertNull(after.optionId)
    }

    @Test
    fun customEntryGetsAutoLabel() = runTest {
        val id = saveIbuprofen()
        val entry = log.addEntry(NewLogEntry(id, DoseSelection.Custom(1.5, " g "), "  ", time.now(), SYDNEY))
        assertEquals("1.5 g", entry.label)
        assertEquals("g", entry.unit)
        assertNull(entry.description)
        assertNull(entry.optionId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun customEntryRejectsZero() = runTest {
        val id = saveIbuprofen()
        log.addEntry(NewLogEntry(id, DoseSelection.Custom(0.0, "mg"), null, time.now(), SYDNEY))
    }

    @Test
    fun updateEntryChangesDoseTimeAndDescription() = runTest {
        val id = saveIbuprofen()
        val options = items.getItem(id)!!.options
        val entry = log.addEntry(NewLogEntry(id, DoseSelection.Preset(options[0].id), null, time.now(), SYDNEY))

        val newTime = at("2026-10-27T22:00")
        val updated = log.updateEntry(LogEntryEdit(entry.id, DoseSelection.Custom(3.0, "pill"), "late", newTime, SYDNEY))
        assertNull(updated.optionId)
        assertEquals("3 pill", updated.label)
        assertEquals(newTime, updated.takenAt)
        assertEquals(entry.recordedAt, updated.recordedAt)

        val unchanged = log.updateEntry(LogEntryEdit(entry.id, DoseSelection.Unchanged, null, newTime, SYDNEY))
        assertEquals("3 pill", unchanged.label)
        assertNull(unchanged.description)
    }

    @Test
    fun observeEntriesUsesEachEntrysOwnZone() = runTest {
        val id = saveIbuprofen()
        val option = items.getItem(id)!!.options.first()
        // 08:00 on 28 Oct in London is 19:00 on 28 Oct in Sydney; 23:30 in London is the 29th in Sydney.
        log.addEntry(NewLogEntry(id, DoseSelection.Preset(option.id), null, at("2026-10-28T08:00", LONDON), LONDON))
        log.addEntry(NewLogEntry(id, DoseSelection.Preset(option.id), null, at("2026-10-28T23:30", LONDON), LONDON))
        log.addEntry(NewLogEntry(id, DoseSelection.Preset(option.id), null, at("2026-10-29T00:30"), SYDNEY))

        val oct28 = DateRange(LocalDate.of(2026, 10, 28), LocalDate.of(2026, 10, 28))
        log.observeEntries(oct28).test {
            assertEquals(2, awaitItem().size)
        }
        log.observeAllEntries().test { assertEquals(3, awaitItem().size) }
    }

    // ---- scores ----

    @Test
    fun scoresAreValidated() = runTest {
        listOf(0, 21).forEach { invalid ->
            try {
                scores.addScore(invalid, time.now(), SYDNEY, null)
                throw AssertionError("score $invalid was accepted")
            } catch (_: IllegalArgumentException) {
                // expected
            }
        }
        val saved = scores.addScore(20, time.now(), SYDNEY, "  great ")
        assertEquals("great", saved.note)
        try {
            scores.updateScore(saved.id, 0, time.now(), SYDNEY, null)
            throw AssertionError("update to 0 was accepted")
        } catch (_: IllegalArgumentException) {
            // expected
        }
        assertEquals(20, scores.observeAllScores().first().single().score)
    }

    @Test
    fun scoreCrudAndRangeQuery() = runTest {
        val a = scores.addScore(5, at("2026-10-27T09:00"), SYDNEY, null)
        scores.addScore(12, at("2026-10-28T09:00"), SYDNEY, null)
        scores.updateScore(a.id, 6, at("2026-10-27T10:00"), SYDNEY, "edited")

        val oct27 = DateRange(LocalDate.of(2026, 10, 27), LocalDate.of(2026, 10, 27))
        scores.observeScores(oct27).test {
            val list = awaitItem()
            assertEquals(listOf(6), list.map { it.score })
            assertEquals("edited", list.single().note)
        }
        assertTrue(scores.deleteScore(a.id))
        assertEquals(1, scores.observeAllScores().first().size)
    }
}
