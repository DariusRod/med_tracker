package app.doselog.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Transaction
    @Query("SELECT * FROM items ORDER BY sortOrder, id")
    fun observeAllWithOptions(): Flow<List<ItemWithOptions>>

    @Transaction
    @Query("SELECT * FROM items WHERE isArchived = 0 ORDER BY sortOrder, id")
    fun observeActiveWithOptions(): Flow<List<ItemWithOptions>>

    @Transaction
    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getWithOptions(id: Long): ItemWithOptions?

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getById(id: Long): ItemEntity?

    @Query("SELECT * FROM items WHERE isArchived = 0 ORDER BY sortOrder, id")
    suspend fun getActive(): List<ItemEntity>

    @Query("SELECT * FROM items ORDER BY id")
    suspend fun getAll(): List<ItemEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM items")
    suspend fun maxSortOrder(): Int

    @Query("SELECT COUNT(*) FROM items")
    suspend fun count(): Int

    @Insert
    suspend fun insert(item: ItemEntity): Long

    @Insert
    suspend fun insertAll(items: List<ItemEntity>)

    @Update
    suspend fun update(item: ItemEntity)

    @Query("UPDATE items SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: Long, sortOrder: Int)

    @Query("UPDATE items SET isArchived = :archived, sortOrder = :sortOrder WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean, sortOrder: Int)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM items")
    suspend fun deleteAll()
}

@Dao
interface ItemOptionDao {
    @Query("SELECT * FROM item_options WHERE itemId = :itemId ORDER BY sortOrder, id")
    suspend fun getForItem(itemId: Long): List<ItemOptionEntity>

    @Query("SELECT * FROM item_options WHERE id = :id")
    suspend fun getById(id: Long): ItemOptionEntity?

    @Query("SELECT * FROM item_options ORDER BY itemId, sortOrder, id")
    suspend fun getAll(): List<ItemOptionEntity>

    @Query("SELECT COUNT(*) FROM item_options")
    suspend fun count(): Int

    @Insert
    suspend fun insert(option: ItemOptionEntity): Long

    @Insert
    suspend fun insertAll(options: List<ItemOptionEntity>)

    @Update
    suspend fun update(option: ItemOptionEntity)

    @Query("DELETE FROM item_options WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM item_options")
    suspend fun deleteAll()
}

@Dao
interface LogEntryDao {
    /** Entries with [fromMillis] <= takenAt < [toMillis], newest first. */
    @Query(
        "SELECT * FROM log_entries WHERE takenAt >= :fromMillis AND takenAt < :toMillis " +
            "ORDER BY takenAt DESC, id DESC",
    )
    fun observeBetween(fromMillis: Long, toMillis: Long): Flow<List<LogEntryEntity>>

    @Query("SELECT * FROM log_entries WHERE id = :id")
    suspend fun getById(id: Long): LogEntryEntity?

    @Query("SELECT * FROM log_entries ORDER BY id")
    suspend fun getAll(): List<LogEntryEntity>

    @Query("SELECT COUNT(*) FROM log_entries")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM log_entries WHERE itemId = :itemId")
    suspend fun countForItem(itemId: Long): Int

    @Query("SELECT COUNT(*) FROM log_entries WHERE itemId = :itemId")
    fun observeCountForItem(itemId: Long): Flow<Int>

    @Insert
    suspend fun insert(entry: LogEntryEntity): Long

    @Insert
    suspend fun insertAll(entries: List<LogEntryEntity>)

    @Update
    suspend fun update(entry: LogEntryEntity)

    @Query("DELETE FROM log_entries WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM log_entries")
    suspend fun deleteAll()
}

@Dao
interface DailyScoreDao {
    @Query("SELECT * FROM daily_scores ORDER BY scoredAt DESC, id DESC")
    fun observeAll(): Flow<List<DailyScoreEntity>>

    /** Scores with [fromMillis] <= scoredAt < [toMillis], newest first. */
    @Query(
        "SELECT * FROM daily_scores WHERE scoredAt >= :fromMillis AND scoredAt < :toMillis " +
            "ORDER BY scoredAt DESC, id DESC",
    )
    fun observeBetween(fromMillis: Long, toMillis: Long): Flow<List<DailyScoreEntity>>

    @Query("SELECT * FROM daily_scores WHERE id = :id")
    suspend fun getById(id: Long): DailyScoreEntity?

    @Query("SELECT * FROM daily_scores ORDER BY id")
    suspend fun getAll(): List<DailyScoreEntity>

    @Query("SELECT COUNT(*) FROM daily_scores")
    suspend fun count(): Int

    @Insert
    suspend fun insert(score: DailyScoreEntity): Long

    @Insert
    suspend fun insertAll(scores: List<DailyScoreEntity>)

    @Update
    suspend fun update(score: DailyScoreEntity)

    @Query("DELETE FROM daily_scores WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM daily_scores")
    suspend fun deleteAll()
}
