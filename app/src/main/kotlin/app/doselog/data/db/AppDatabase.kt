package app.doselog.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ItemEntity::class,
        ItemOptionEntity::class,
        LogEntryEntity::class,
        DailyScoreEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun itemOptionDao(): ItemOptionDao
    abstract fun logEntryDao(): LogEntryDao
    abstract fun dailyScoreDao(): DailyScoreDao

    companion object {
        const val NAME = "doselog.db"

        /** Bump together with a new Migration in [Migrations.ALL] and a new exported schema. */
        const val VERSION = 1

        /** Tables in foreign-key order (parents first). */
        val TABLES = listOf("items", "item_options", "log_entries", "daily_scores")
    }
}
