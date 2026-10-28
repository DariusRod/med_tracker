package app.doselog.data.db

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import app.doselog.domain.model.Category
import app.doselog.testing.at
import app.doselog.testing.inMemoryDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DaoTest {

    private lateinit var db: AppDatabase
    private lateinit var itemDao: ItemDao
    private lateinit var optionDao: ItemOptionDao
    private lateinit var entryDao: LogEntryDao
    private lateinit var scoreDao: DailyScoreDao

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        itemDao = db.itemDao()
        optionDao = db.itemOptionDao()
        entryDao = db.logEntryDao()
        scoreDao = db.dailyScoreDao()
    }

    @After
    fun tearDown() = db.close()

    private fun item(name: String, sortOrder: Int = 0, archived: Boolean = false) = ItemEntity(
        name = name,
        category = Category.MEDICATION,
        defaultUnit = "mg",
        isArchived = archived,
        sortOrder = sortOrder,
        createdAt = 0,
    )

    private fun option(itemId: Long, amount: Double?, label: String, sortOrder: Int = 0) =
        ItemOptionEntity(itemId = itemId, amount = amount, unit = "mg", label = label, sortOrder = sortOrder)

    private fun entry(itemId: Long, takenAt: Long, optionId: Long? = null) = LogEntryEntity(
        itemId = itemId,
        optionId = optionId,
        itemNameSnapshot = "Ibuprofen",
        categorySnapshot = "MEDICATION",
        amountSnapshot = 200.0,
        unitSnapshot = "mg",
        labelSnapshot = "200 mg",
        takenAt = takenAt,
        zoneId = "Australia/Sydney",
        recordedAt = takenAt,
        updatedAt = null,
        description = null,
    )

    private fun score(value: Int, scoredAt: Long) =
        DailyScoreEntity(score = value, scoredAt = scoredAt, zoneId = "Australia/Sydney", recordedAt = scoredAt, note = null)

    // ---- items ----

    @Test
    fun itemsAreOrderedBySortOrderAndArchivedOnesAreFiltered() = runTest {
        val b = itemDao.insert(item("B", sortOrder = 1))
        val a = itemDao.insert(item("A", sortOrder = 0))
        val c = itemDao.insert(item("C", sortOrder = 2, archived = true))

        itemDao.observeAllWithOptions().test {
            assertEquals(listOf(a, b, c), awaitItem().map { it.item.id })
        }
        itemDao.observeActiveWithOptions().test {
            assertEquals(listOf(a, b), awaitItem().map { it.item.id })
        }
        assertEquals(listOf(a, b), itemDao.getActive().map { it.id })
        assertEquals(listOf(b, a, c), itemDao.getAll().map { it.id })
        assertEquals(3, itemDao.count())
        assertEquals(2, itemDao.maxSortOrder())
    }

    @Test
    fun maxSortOrderOfEmptyTableIsMinusOne() = runTest {
        assertEquals(-1, itemDao.maxSortOrder())
    }

    @Test
    fun itemUpdatesAreObserved() = runTest {
        val id = itemDao.insert(item("Ibuprofen"))
        itemDao.observeAllWithOptions().test {
            assertEquals("Ibuprofen", awaitItem().single().item.name)
            itemDao.update(itemDao.getById(id)!!.copy(name = "Advil"))
            assertEquals("Advil", awaitItem().single().item.name)
            itemDao.updateSortOrder(id, 5)
            assertEquals(5, awaitItem().single().item.sortOrder)
            itemDao.setArchived(id, archived = true, sortOrder = 6)
            val archived = awaitItem().single().item
            assertTrue(archived.isArchived)
            assertEquals(6, archived.sortOrder)
        }
    }

    @Test
    fun getWithOptionsReturnsPresets() = runTest {
        val id = itemDao.insert(item("Ibuprofen"))
        optionDao.insert(option(id, 200.0, "200 mg", sortOrder = 1))
        optionDao.insert(option(id, 400.0, "400 mg", sortOrder = 0))

        val loaded = itemDao.getWithOptions(id)!!
        assertEquals(setOf("200 mg", "400 mg"), loaded.options.map { it.label }.toSet())
        assertEquals(listOf("400 mg", "200 mg"), optionDao.getForItem(id).map { it.label })
        assertNull(itemDao.getWithOptions(999))
    }

    @Test
    fun bulkInsertAndDeleteAll() = runTest {
        itemDao.insertAll(listOf(item("A").copy(id = 10), item("B").copy(id = 11)))
        assertEquals(2, itemDao.count())
        assertEquals(1, itemDao.deleteById(10))
        assertEquals(0, itemDao.deleteById(10))
        itemDao.deleteAll()
        assertEquals(0, itemDao.count())
    }

    // ---- options ----

    @Test
    fun optionCrud() = runTest {
        val itemId = itemDao.insert(item("Ibuprofen"))
        val optionId = optionDao.insert(option(itemId, 200.0, "200 mg"))
        optionDao.insertAll(listOf(option(itemId, null, "with food", sortOrder = 1)))
        assertEquals(2, optionDao.count())

        optionDao.update(optionDao.getById(optionId)!!.copy(label = "Two hundred"))
        assertEquals("Two hundred", optionDao.getById(optionId)!!.label)
        assertEquals(2, optionDao.getAll().size)

        optionDao.deleteByIds(listOf(optionId))
        assertNull(optionDao.getById(optionId))
        optionDao.deleteAll()
        assertEquals(0, optionDao.count())
    }

    @Test
    fun deletingAnItemCascadesToItsPresets() = runTest {
        val itemId = itemDao.insert(item("Ibuprofen"))
        optionDao.insert(option(itemId, 200.0, "200 mg"))
        itemDao.deleteById(itemId)
        assertEquals(0, optionDao.count())
    }

    // ---- log entries ----

    @Test
    fun observeBetweenIsHalfOpenAndNewestFirst() = runTest {
        val itemId = itemDao.insert(item("Ibuprofen"))
        val t1 = at("2026-10-28T08:00").toEpochMilli()
        val t2 = at("2026-10-28T12:00").toEpochMilli()
        val t3 = at("2026-10-28T20:00").toEpochMilli()
        entryDao.insertAll(listOf(entry(itemId, t1), entry(itemId, t2), entry(itemId, t3)))

        entryDao.observeBetween(t1, t3).test {
            assertEquals(listOf(t2, t1), awaitItem().map { it.takenAt })
        }
        entryDao.observeBetween(Long.MIN_VALUE, Long.MAX_VALUE).test {
            assertEquals(listOf(t3, t2, t1), awaitItem().map { it.takenAt })
        }
    }

    @Test
    fun entryCrudAndCounts() = runTest {
        val itemId = itemDao.insert(item("Ibuprofen"))
        val otherId = itemDao.insert(item("Paracetamol"))
        val id = entryDao.insert(entry(itemId, 1_000))
        entryDao.insert(entry(otherId, 2_000))

        assertEquals(2, entryDao.count())
        assertEquals(1, entryDao.countForItem(itemId))
        entryDao.observeCountForItem(itemId).test { assertEquals(1, awaitItem()) }

        entryDao.update(entryDao.getById(id)!!.copy(description = "after lunch", updatedAt = 5_000))
        assertEquals("after lunch", entryDao.getById(id)!!.description)
        assertEquals(listOf(id), entryDao.getAll().filter { it.itemId == itemId }.map { it.id })

        assertEquals(1, entryDao.deleteById(id))
        assertNull(entryDao.getById(id))
        entryDao.deleteAll()
        assertEquals(0, entryDao.count())
    }

    @Test
    fun itemWithEntriesCannotBeDeleted() = runTest {
        val itemId = itemDao.insert(item("Ibuprofen"))
        entryDao.insert(entry(itemId, 1_000))
        try {
            itemDao.deleteById(itemId)
            fail("Expected the RESTRICT foreign key to block the delete")
        } catch (_: SQLiteConstraintException) {
            // expected
        }
        assertEquals(1, itemDao.count())
    }

    @Test
    fun deletingAPresetKeepsTheEntrySnapshot() = runTest {
        val itemId = itemDao.insert(item("Ibuprofen"))
        val optionId = optionDao.insert(option(itemId, 200.0, "200 mg"))
        val entryId = entryDao.insert(entry(itemId, 1_000, optionId))

        optionDao.deleteByIds(listOf(optionId))

        val after = entryDao.getById(entryId)!!
        assertNull(after.optionId)
        assertEquals("200 mg", after.labelSnapshot)
        assertEquals(200.0, after.amountSnapshot!!, 0.0)
    }

    // ---- scores ----

    @Test
    fun scoreQueries() = runTest {
        val t1 = at("2026-10-27T08:00").toEpochMilli()
        val t2 = at("2026-10-28T08:00").toEpochMilli()
        val id1 = scoreDao.insert(score(10, t1))
        scoreDao.insertAll(listOf(score(15, t2)))

        scoreDao.observeAll().test { assertEquals(listOf(15, 10), awaitItem().map { it.score }) }
        scoreDao.observeBetween(t1, t2).test { assertEquals(listOf(10), awaitItem().map { it.score }) }
        assertEquals(2, scoreDao.count())
        assertEquals(2, scoreDao.getAll().size)

        scoreDao.update(scoreDao.getById(id1)!!.copy(score = 11, note = "tired"))
        assertEquals(11, scoreDao.getById(id1)!!.score)

        assertEquals(1, scoreDao.deleteById(id1))
        scoreDao.deleteAll()
        assertEquals(0, scoreDao.count())
    }
}
