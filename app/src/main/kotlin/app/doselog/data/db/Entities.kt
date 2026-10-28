package app.doselog.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import app.doselog.domain.model.Category

// All timestamps are UTC epoch millis. Entries and scores also store the zone they were recorded
// in, so "which day" is always answered in the zone the user was in at the time.

@Entity(tableName = "items")
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: Category,
    val defaultUnit: String?,
    val isArchived: Boolean = false,
    val sortOrder: Int,
    val createdAt: Long,
)

@Entity(
    tableName = "item_options",
    foreignKeys = [
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("itemId")],
)
data class ItemOptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val amount: Double?,
    val unit: String,
    val label: String,
    val sortOrder: Int,
)

/**
 * The snapshot columns copy the item and preset as they were when the entry was logged, so
 * renaming an item or editing/deleting a preset never rewrites history.
 */
@Entity(
    tableName = "log_entries",
    foreignKeys = [
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = ItemOptionEntity::class,
            parentColumns = ["id"],
            childColumns = ["optionId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("itemId"), Index("optionId"), Index("takenAt")],
)
data class LogEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val optionId: Long?,
    val itemNameSnapshot: String,
    val categorySnapshot: String,
    val amountSnapshot: Double?,
    val unitSnapshot: String,
    val labelSnapshot: String,
    val takenAt: Long,
    val zoneId: String,
    val recordedAt: Long,
    val updatedAt: Long?,
    val description: String?,
)

@Entity(tableName = "daily_scores", indices = [Index("scoredAt")])
data class DailyScoreEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val score: Int,
    val scoredAt: Long,
    val zoneId: String,
    val recordedAt: Long,
    val note: String?,
)

data class ItemWithOptions(
    @Embedded val item: ItemEntity,
    @Relation(parentColumn = "id", entityColumn = "itemId")
    val options: List<ItemOptionEntity>,
)
