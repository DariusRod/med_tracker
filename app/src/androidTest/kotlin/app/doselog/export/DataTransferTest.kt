package app.doselog.export

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.doselog.data.db.AppDatabase
import app.doselog.data.db.LogEntryEntity
import app.doselog.data.repository.DoseSelection
import app.doselog.data.repository.ItemRepository
import app.doselog.data.repository.LogRepository
import app.doselog.data.repository.NewLogEntry
import app.doselog.data.repository.SaveItemResult
import app.doselog.data.repository.ScoreRepository
import app.doselog.domain.model.Category
import app.doselog.domain.model.ItemDraft
import app.doselog.domain.model.OptionDraft
import app.doselog.testing.FakeTimeSource
import app.doselog.testing.LONDON
import app.doselog.testing.SYDNEY
import app.doselog.testing.at
import app.doselog.testing.inMemoryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

@RunWith(AndroidJUnit4::class)
class DataTransferTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDatabase
    private lateinit var transfer: DataTransfer
    private val time = FakeTimeSource()

    @Before
    fun setUp() = runBlocking {
        db = inMemoryDatabase()
        transfer = DataTransfer(context, db, time, Dispatchers.IO)
        seed()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seed() {
        val items = ItemRepository(db, time)
        val log = LogRepository(db, time)
        val scores = ScoreRepository(db, time)
        val ibuprofen = (items.saveItem(
            ItemDraft(null, "Ibuprofen", Category.MEDICATION, "mg", listOf(OptionDraft(null, 200.0, "mg", ""), OptionDraft(null, 2.0, "pill", ""))),
        ) as SaveItemResult.Saved).id
        val porridge = (items.saveItem(
            ItemDraft(null, "Crème brûlée, \"special\"", Category.FOOD, null, listOf(OptionDraft(null, null, "", "Small bowl"))),
        ) as SaveItemResult.Saved).id
        val option = items.getItem(ibuprofen)!!.options.first().id
        log.addEntry(NewLogEntry(ibuprofen, DoseSelection.Preset(option), "after food,\nwith water", at("2026-10-28T08:00"), SYDNEY))
        log.addEntry(NewLogEntry(ibuprofen, DoseSelection.Custom(1.5, "g"), null, at("2026-10-27T22:00", LONDON), LONDON))
        log.addEntry(NewLogEntry(porridge, DoseSelection.Preset(items.getItem(porridge)!!.options.first().id), null, at("2026-10-28T07:00"), SYDNEY))
        scores.addScore(14, at("2026-10-28T09:00"), SYDNEY, "ok")
        scores.addScore(17, at("2026-10-28T20:00"), SYDNEY, null)
    }

    private fun tableCount(file: SQLiteDatabase, table: String): Int =
        file.rawQuery("SELECT COUNT(*) FROM $table", null).use { it.moveToFirst(); it.getInt(0) }

    private fun assertStandaloneCopy(strategy: DbCopyStrategy) = runBlocking {
        val bytes = ByteArrayOutputStream().also { transfer.writeDatabase(it, strategy) }.toByteArray()
        val file = File(context.cacheDir, "exported-${strategy.name}.db").apply { delete(); writeBytes(bytes) }
        try {
            val expected = transfer.snapshot().counts
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { copy ->
                assertEquals(AppDatabase.VERSION, copy.version)
                assertEquals(expected.items, tableCount(copy, "items"))
                assertEquals(expected.presets, tableCount(copy, "item_options"))
                assertEquals(expected.entries, tableCount(copy, "log_entries"))
                assertEquals(expected.scores, tableCount(copy, "daily_scores"))
                assertEquals(1, tableCount(copy, "room_master_table"))
                copy.rawQuery("PRAGMA journal_mode", null).use { it.moveToFirst(); assertFalse(it.getString(0).equals("wal", true)) }
                copy.rawQuery("SELECT description FROM log_entries WHERE description IS NOT NULL", null).use {
                    it.moveToFirst()
                    assertEquals("after food,\nwith water", it.getString(0))
                }
            }
            // The header must say "rollback journal" so no -wal file is needed on Windows.
            assertEquals(1, bytes[18].toInt())
            assertEquals(1, bytes[19].toInt())
        } finally {
            listOf("", "-wal", "-shm", "-journal").forEach { File(file.path + it).delete() }
        }
    }

    @Test
    fun databaseExportWithVacuumIntoOpensAndMatches() = assertStandaloneCopy(DbCopyStrategy.VACUUM_INTO)

    @Test
    fun databaseExportWithSqlCopyOpensAndMatches() = assertStandaloneCopy(DbCopyStrategy.SQL_COPY)

    @Test
    fun csvZipHasAllFilesWithBom() = runBlocking {
        val bytes = ByteArrayOutputStream().also { transfer.writeCsvZip(it) }.toByteArray()
        val files = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                files[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        assertEquals(
            setOf(ZipEntries.ITEMS, ZipEntries.OPTIONS, ZipEntries.ENTRIES, ZipEntries.SCORES, ZipEntries.FLAT),
            files.keys,
        )
        files.values.forEach { assertTrue(it.startsWith("\uFEFF")) }
        assertTrue(files.getValue(ZipEntries.FLAT).contains("\"Crème brûlée, \"\"special\"\"\""))
        assertTrue(files.getValue(ZipEntries.ENTRIES).contains("2026-10-28T08:00:00+11:00"))
    }

    @Test
    fun jsonBackupRestoresExactly() = runBlocking {
        val original = transfer.snapshot()
        val json = ByteArrayOutputStream().also { transfer.writeJsonBackup(it) }.toByteArray()

        db.logEntryDao().deleteAll()
        db.dailyScoreDao().deleteAll()

        val read = transfer.readBackup(ByteArrayInputStream(json))
        assertTrue(read.toString(), read is BackupReadResult.Ready)
        read as BackupReadResult.Ready
        assertEquals(BackupSource.JSON, read.source)
        assertEquals(original.counts, read.snapshot.counts)

        transfer.restore(read.snapshot)
        assertEquals(original, transfer.snapshot())
    }

    @Test
    fun databaseFileRestoresExactly() = runBlocking {
        val original = transfer.snapshot()
        val dbBytes = ByteArrayOutputStream().also { transfer.writeDatabase(it) }.toByteArray()
        db.logEntryDao().deleteAll()

        val read = transfer.readBackup(ByteArrayInputStream(dbBytes))
        assertTrue(read.toString(), read is BackupReadResult.Ready)
        assertEquals(BackupSource.DATABASE, (read as BackupReadResult.Ready).source)
        transfer.restore(read.snapshot)
        assertEquals(original, transfer.snapshot())
    }

    @Test
    fun databaseWithAnotherSchemaVersionIsRejected() = runBlocking {
        val file = File(context.cacheDir, "wrong-version.db").apply { delete() }
        transfer.copyDatabaseTo(file, DbCopyStrategy.VACUUM_INTO)
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { it.version = 99 }
        val result = transfer.readBackup(file.inputStream())
        file.delete()
        assertTrue(result is BackupReadResult.Rejected)
        assertTrue((result as BackupReadResult.Rejected).reason.contains("schema version 99"))
    }

    @Test
    fun garbageIsRejected() = runBlocking {
        val result = transfer.readBackup(ByteArrayInputStream("hello, world".toByteArray()))
        assertTrue(result is BackupReadResult.Rejected)
    }

    @Test
    fun failedRestoreLeavesDataUnchanged() = runBlocking {
        val original = transfer.snapshot()
        val orphan = LogEntryEntity(999, 12345, null, "Ghost", "OTHER", null, "", "x", 0, "UTC", 0, null, null)
        val bad = original.copy(entries = original.entries + orphan)

        try {
            transfer.restore(bad)
            fail("Restore of an invalid snapshot must fail")
        } catch (_: IllegalArgumentException) {
            // rejected by validation
        }
        assertEquals(original, transfer.snapshot())

        // Bypass validation so the foreign key fails half-way through the transaction.
        try {
            transfer.replaceAll(bad)
            fail("The foreign key violation must abort the restore")
        } catch (_: Exception) {
            // expected
        }
        assertEquals(original, transfer.snapshot())
    }
}
