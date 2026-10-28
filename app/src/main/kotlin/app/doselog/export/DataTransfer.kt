package app.doselog.export

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import app.doselog.BuildConfig
import app.doselog.data.db.AppDatabase
import app.doselog.data.db.DailyScoreEntity
import app.doselog.data.db.ItemEntity
import app.doselog.data.db.ItemOptionEntity
import app.doselog.data.db.LogEntryEntity
import app.doselog.di.IoDispatcher
import app.doselog.domain.DayGrouping
import app.doselog.domain.TimeSource
import app.doselog.domain.model.Category
import app.doselog.domain.model.DailyScore
import app.doselog.domain.model.LogEntry
import app.doselog.domain.model.toDomain
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.io.BufferedInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** How the live (WAL-mode) database is copied into a standalone file. */
enum class DbCopyStrategy {
    /** SQLite's own consistent copy; needs SQLite 3.27+, i.e. Android 11+. */
    VACUUM_INTO,

    /** Recreates the schema and copies every row inside one read transaction; works everywhere. */
    SQL_COPY,
    ;

    companion object {
        fun forDevice(): DbCopyStrategy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) VACUUM_INTO else SQL_COPY
    }
}

enum class BackupSource(val label: String) {
    JSON("JSON backup"),
    DATABASE("Database file"),
}

sealed interface BackupReadResult {
    data class Ready(val snapshot: DatabaseSnapshot, val source: BackupSource, val exportedAt: String?) : BackupReadResult
    data class Rejected(val reason: String) : BackupReadResult
}

/** Export, backup and restore. All work runs on the IO dispatcher. */
@Singleton
class DataTransfer @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val timeSource: TimeSource,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {

    /** Every row of every table, read in one transaction so the copy is consistent. */
    suspend fun snapshot(): DatabaseSnapshot = db.withTransaction {
        DatabaseSnapshot(
            items = db.itemDao().getAll(),
            options = db.itemOptionDao().getAll(),
            entries = db.logEntryDao().getAll(),
            scores = db.dailyScoreDao().getAll(),
        )
    }

    // ------------------------------------------------------------------ export

    /** A standalone SQLite file (no -wal/-shm needed) that DB Browser for SQLite can open. */
    suspend fun writeDatabase(out: OutputStream, strategy: DbCopyStrategy = DbCopyStrategy.forDevice()) = withContext(io) {
        val temp = tempFile("export")
        try {
            copyDatabaseTo(temp, strategy)
            temp.inputStream().use { it.copyTo(out) }
            out.flush()
        } finally {
            deleteWithSidecars(temp)
        }
    }

    /** Writes a checked, rollback-journal copy of the live database to [target], which must not exist. */
    internal suspend fun copyDatabaseTo(target: File, strategy: DbCopyStrategy) = withContext(io) {
        when (strategy) {
            DbCopyStrategy.VACUUM_INTO -> db.openHelper.writableDatabase.execSQL("VACUUM INTO ?", arrayOf<Any>(target.absolutePath))
            DbCopyStrategy.SQL_COPY -> copyBySql(target)
        }
        SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READWRITE).use { copy ->
            copy.disableWriteAheadLogging()
            copy.rawQuery("PRAGMA journal_mode=DELETE", null).use { it.moveToFirst() }
            copy.rawQuery("PRAGMA integrity_check", null).use {
                check(it.moveToFirst() && it.getString(0) == "ok") { "The exported database failed its integrity check" }
            }
        }
    }

    private suspend fun copyBySql(target: File) {
        db.withTransaction {
            val source = db.openHelper.writableDatabase
            val schema = source.query(
                "SELECT type, name, sql FROM sqlite_master WHERE sql IS NOT NULL " +
                    "AND name NOT LIKE 'sqlite_%' AND name <> 'android_metadata'",
            ).use { cursor ->
                buildList { while (cursor.moveToNext()) add(Triple(cursor.getString(0), cursor.getString(1), cursor.getString(2))) }
            }
            val tables = schema.filter { it.first == "table" }
            SQLiteDatabase.openOrCreateDatabase(target, null).use { out ->
                out.beginTransaction()
                try {
                    tables.forEach { out.execSQL(it.third) }
                    tables.forEach { copyRows(source, out, it.second) }
                    schema.filter { it.first != "table" }.forEach { out.execSQL(it.third) }
                    out.version = source.version
                    out.setTransactionSuccessful()
                } finally {
                    out.endTransaction()
                }
            }
        }
    }

    private fun copyRows(source: SupportSQLiteDatabase, out: SQLiteDatabase, table: String) {
        source.query("SELECT * FROM `$table`").use { cursor ->
            val columns = cursor.columnNames
            val sql = "INSERT INTO `$table` (${columns.joinToString { "`$it`" }}) VALUES (${columns.joinToString { "?" }})"
            out.compileStatement(sql).use { statement ->
                while (cursor.moveToNext()) {
                    statement.clearBindings()
                    for (i in columns.indices) {
                        val index = i + 1
                        when (cursor.getType(i)) {
                            Cursor.FIELD_TYPE_NULL -> statement.bindNull(index)
                            Cursor.FIELD_TYPE_INTEGER -> statement.bindLong(index, cursor.getLong(i))
                            Cursor.FIELD_TYPE_FLOAT -> statement.bindDouble(index, cursor.getDouble(i))
                            Cursor.FIELD_TYPE_BLOB -> statement.bindBlob(index, cursor.getBlob(i))
                            else -> statement.bindString(index, cursor.getString(i))
                        }
                    }
                    statement.executeInsert()
                }
            }
        }
    }

    /** items.csv, item_options.csv, log_entries.csv, daily_scores.csv and entries_flat.csv. */
    suspend fun writeCsvZip(out: OutputStream) = withContext(io) {
        val snapshot = snapshot()
        val entries = snapshot.entries.map { it.toDomain() }
        val dayScores = DayGrouping.dayScores(snapshot.scores.map { it.toDomain() })
        val zip = ZipOutputStream(out)
        fun entry(name: String, write: (CsvWriter) -> Unit) {
            zip.putNextEntry(ZipEntry(name))
            val writer = OutputStreamWriter(zip, Charsets.UTF_8)
            CsvWriter(writer).apply { writeBom() }.also(write)
            writer.flush()
            zip.closeEntry()
        }
        entry(ZipEntries.ITEMS) { CsvTables.writeItems(it, snapshot.items, timeSource.zone()) }
        entry(ZipEntries.OPTIONS) { CsvTables.writeOptions(it, snapshot.options) }
        entry(ZipEntries.ENTRIES) { CsvTables.writeEntries(it, snapshot.entries) }
        entry(ZipEntries.SCORES) { CsvTables.writeScores(it, snapshot.scores) }
        entry(ZipEntries.FLAT) { CsvTables.writeEntriesFlat(it, entries, dayScores) }
        zip.finish()
        zip.flush()
    }

    suspend fun writeEntriesCsv(out: OutputStream, entries: List<LogEntry>, dayScores: Map<LocalDate, DailyScore>) =
        writeSingleCsv(out) { CsvTables.writeEntriesFlat(it, entries, dayScores) }

    suspend fun writeScoresCsv(out: OutputStream, scores: List<DailyScore>) =
        writeSingleCsv(out) { CsvTables.writeScoreList(it, scores, DayGrouping.dayScores(scores)) }

    /** All entries and all scores, for the Export screen's single-CSV options. */
    suspend fun allEntriesAndScores(): Pair<List<LogEntry>, List<DailyScore>> {
        val snapshot = snapshot()
        return snapshot.entries.map { it.toDomain() } to snapshot.scores.map { it.toDomain() }
    }

    private suspend fun writeSingleCsv(out: OutputStream, write: (CsvWriter) -> Unit) = withContext(io) {
        val writer = OutputStreamWriter(out, Charsets.UTF_8)
        CsvWriter(writer).apply { writeBom() }.also(write)
        writer.flush()
    }

    suspend fun writeJsonBackup(out: OutputStream) = withContext(io) {
        val file = BackupJson.fromSnapshot(
            snapshot = snapshot(),
            schemaVersion = AppDatabase.VERSION,
            appVersion = BuildConfig.VERSION_NAME,
            exportedAt = CsvTables.iso(timeSource.now().atZone(timeSource.zone())),
        )
        out.write(BackupJson.encode(file).toByteArray(Charsets.UTF_8))
        out.flush()
    }

    // ------------------------------------------------------------------ import

    /** Reads a JSON backup or an exported .db file and validates it. Nothing is changed yet. */
    suspend fun readBackup(input: InputStream): BackupReadResult = withContext(io) {
        val buffered = BufferedInputStream(input)
        buffered.mark(SQLITE_HEADER.size)
        val header = ByteArray(SQLITE_HEADER.size)
        var read = 0
        while (read < header.size) {
            val n = buffered.read(header, read, header.size - read)
            if (n < 0) break
            read += n
        }
        buffered.reset()
        if (read == header.size && header.contentEquals(SQLITE_HEADER)) readDatabaseFile(buffered) else readJson(buffered)
    }

    private fun readJson(input: InputStream): BackupReadResult {
        val text = input.readBytes().toString(Charsets.UTF_8).removePrefix("\uFEFF")
        val file = try {
            BackupJson.decode(text)
        } catch (_: SerializationException) {
            return BackupReadResult.Rejected("This file isn't a DoseLog backup (.json) or database (.db).")
        } catch (_: IllegalArgumentException) {
            return BackupReadResult.Rejected("This file isn't a DoseLog backup (.json) or database (.db).")
        }
        if (file.schemaVersion > AppDatabase.VERSION) {
            return BackupReadResult.Rejected("This backup was made by a newer version of DoseLog. Update the app first.")
        }
        val (snapshot, conversionErrors) = BackupJson.toSnapshot(file)
        val errors = conversionErrors + SnapshotValidator.validate(snapshot)
        return if (errors.isEmpty()) {
            BackupReadResult.Ready(snapshot, BackupSource.JSON, file.exportedAt)
        } else {
            BackupReadResult.Rejected(errors.joinToString("\n"))
        }
    }

    private fun readDatabaseFile(input: InputStream): BackupReadResult {
        val temp = tempFile("import")
        return try {
            temp.outputStream().use { input.copyTo(it) }
            SQLiteDatabase.openDatabase(temp.path, null, SQLiteDatabase.OPEN_READWRITE).use { file ->
                val version = file.version
                if (version != AppDatabase.VERSION) {
                    return BackupReadResult.Rejected(
                        "This database has schema version $version, but this app uses version ${AppDatabase.VERSION}.",
                    )
                }
                val identity = file.rawQuery("SELECT identity_hash FROM room_master_table LIMIT 1", null)
                    .use { if (it.moveToFirst()) it.getString(0) else null }
                if (identity == null || identity != currentIdentityHash()) {
                    return BackupReadResult.Rejected("This database wasn't created by this version of DoseLog.")
                }
                val integrity = file.rawQuery("PRAGMA integrity_check", null).use { if (it.moveToFirst()) it.getString(0) else null }
                if (integrity != "ok") return BackupReadResult.Rejected("The database file is damaged ($integrity).")

                val (snapshot, readErrors) = readSnapshot(file)
                val errors = readErrors + SnapshotValidator.validate(snapshot)
                if (errors.isEmpty()) {
                    BackupReadResult.Ready(snapshot, BackupSource.DATABASE, exportedAt = null)
                } else {
                    BackupReadResult.Rejected(errors.joinToString("\n"))
                }
            }
        } catch (e: SQLiteException) {
            BackupReadResult.Rejected("Couldn't read this database file: ${e.message}")
        } finally {
            deleteWithSidecars(temp)
        }
    }

    private fun currentIdentityHash(): String? =
        db.openHelper.readableDatabase.query("SELECT identity_hash FROM room_master_table LIMIT 1")
            .use { if (it.moveToFirst()) it.getString(0) else null }

    private fun readSnapshot(file: SQLiteDatabase): Pair<DatabaseSnapshot, List<String>> {
        val errors = mutableListOf<String>()
        fun Cursor.stringOrNull(i: Int) = if (isNull(i)) null else getString(i)
        fun Cursor.longOrNull(i: Int) = if (isNull(i)) null else getLong(i)
        fun Cursor.doubleOrNull(i: Int) = if (isNull(i)) null else getDouble(i)
        fun <T> rows(sql: String, map: (Cursor) -> T): List<T> =
            file.rawQuery(sql, null).use { c -> buildList { while (c.moveToNext()) add(map(c)) } }

        val items = rows("SELECT id, name, category, defaultUnit, isArchived, sortOrder, createdAt FROM items ORDER BY id") { c ->
            val category = Category.entries.firstOrNull { it.name == c.getString(2) }
            if (category == null) errors += "Item ${c.getLong(0)} has an unknown category \"${c.getString(2)}\"."
            ItemEntity(c.getLong(0), c.getString(1), category ?: Category.OTHER, c.stringOrNull(3), c.getInt(4) != 0, c.getInt(5), c.getLong(6))
        }
        val options = rows("SELECT id, itemId, amount, unit, label, sortOrder FROM item_options ORDER BY id") { c ->
            ItemOptionEntity(c.getLong(0), c.getLong(1), c.doubleOrNull(2), c.getString(3), c.getString(4), c.getInt(5))
        }
        val entries = rows(
            "SELECT id, itemId, optionId, itemNameSnapshot, categorySnapshot, amountSnapshot, unitSnapshot, labelSnapshot, " +
                "takenAt, zoneId, recordedAt, updatedAt, description FROM log_entries ORDER BY id",
        ) { c ->
            LogEntryEntity(
                c.getLong(0), c.getLong(1), c.longOrNull(2), c.getString(3), c.getString(4), c.doubleOrNull(5), c.getString(6),
                c.getString(7), c.getLong(8), c.getString(9), c.getLong(10), c.longOrNull(11), c.stringOrNull(12),
            )
        }
        val scores = rows("SELECT id, score, scoredAt, zoneId, recordedAt, note FROM daily_scores ORDER BY id") { c ->
            DailyScoreEntity(c.getLong(0), c.getInt(1), c.getLong(2), c.getString(3), c.getLong(4), c.stringOrNull(5))
        }
        return DatabaseSnapshot(items, options, entries, scores) to errors
    }

    /**
     * Replaces all data with [snapshot] in a single transaction: on any failure the database is
     * left exactly as it was.
     */
    suspend fun restore(snapshot: DatabaseSnapshot) {
        val errors = SnapshotValidator.validate(snapshot)
        require(errors.isEmpty()) { errors.joinToString("\n") }
        replaceAll(snapshot)
    }

    /** The unvalidated core of [restore]; a constraint failure rolls everything back. */
    internal suspend fun replaceAll(snapshot: DatabaseSnapshot) {
        db.withTransaction {
            // Children first (entries RESTRICT item deletion), parents first on insert.
            db.logEntryDao().deleteAll()
            db.dailyScoreDao().deleteAll()
            db.itemOptionDao().deleteAll()
            db.itemDao().deleteAll()
            db.itemDao().insertAll(snapshot.items)
            db.itemOptionDao().insertAll(snapshot.options)
            db.logEntryDao().insertAll(snapshot.entries)
            db.dailyScoreDao().insertAll(snapshot.scores)
        }
    }

    // ------------------------------------------------------------------ files picked by the user

    /** Writes to a document created with the system picker; a half-written file is deleted. */
    suspend fun writeTo(uri: Uri, block: suspend (OutputStream) -> Unit) = withContext(io) {
        try {
            openForWriting(uri).use { block(it) }
        } catch (e: Exception) {
            if (e !is CancellationException) {
                runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
            }
            throw e
        }
    }

    suspend fun <T> readFrom(uri: Uri, block: suspend (InputStream) -> T): T = withContext(io) {
        val stream = context.contentResolver.openInputStream(uri) ?: throw IOException("Couldn't open the chosen file")
        stream.use { block(it) }
    }

    fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }

    private fun openForWriting(uri: Uri): OutputStream {
        val resolver = context.contentResolver
        // "wt" truncates; a few providers only support "w" (the new document is empty anyway).
        val stream = try {
            resolver.openOutputStream(uri, "wt")
        } catch (_: FileNotFoundException) {
            resolver.openOutputStream(uri, "w")
        } catch (_: IllegalArgumentException) {
            resolver.openOutputStream(uri, "w")
        }
        return stream ?: throw IOException("Couldn't write to the chosen file")
    }

    private fun tempFile(prefix: String): File {
        val dir = File(context.cacheDir, "transfer").apply { mkdirs() }
        return File(dir, "$prefix-${System.nanoTime()}.db").also { deleteWithSidecars(it) }
    }

    private fun deleteWithSidecars(file: File) {
        listOf("", "-wal", "-shm", "-journal").forEach { File(file.path + it).delete() }
    }

    private companion object {
        val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    }
}
