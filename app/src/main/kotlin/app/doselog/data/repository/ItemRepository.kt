package app.doselog.data.repository

import androidx.room.withTransaction
import app.doselog.data.db.AppDatabase
import app.doselog.data.db.ItemEntity
import app.doselog.data.db.ItemOptionEntity
import app.doselog.domain.TimeSource
import app.doselog.domain.model.Item
import app.doselog.domain.model.ItemDraft
import app.doselog.domain.model.ItemErrors
import app.doselog.domain.model.ItemValidator
import app.doselog.domain.model.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SaveItemResult {
    data class Saved(val id: Long) : SaveItemResult
    data class Invalid(val errors: ItemErrors) : SaveItemResult
}

sealed interface DeleteItemResult {
    data object Deleted : DeleteItemResult
    data class HasEntries(val entryCount: Int) : DeleteItemResult
    data object NotFound : DeleteItemResult
}

sealed interface ArchiveResult {
    data object Done : ArchiveResult
    data object DuplicateName : ArchiveResult
}

@Singleton
class ItemRepository @Inject constructor(
    private val db: AppDatabase,
    private val timeSource: TimeSource,
) {
    private val itemDao = db.itemDao()
    private val optionDao = db.itemOptionDao()
    private val entryDao = db.logEntryDao()

    /** All items (archived included), in display order. */
    fun observeAllItems(): Flow<List<Item>> =
        itemDao.observeAllWithOptions().map { list -> list.map { it.toDomain() } }

    /** Non-archived items, in the order shown on the Log screen. */
    fun observeActiveItems(): Flow<List<Item>> =
        itemDao.observeActiveWithOptions().map { list -> list.map { it.toDomain() } }

    suspend fun getItem(id: Long): Item? = itemDao.getWithOptions(id)?.toDomain()

    fun observeEntryCount(itemId: Long): Flow<Int> = entryDao.observeCountForItem(itemId)

    /** Validates and saves an item and its presets in one transaction. */
    suspend fun saveItem(draft: ItemDraft): SaveItemResult = db.withTransaction {
        val existing = draft.id?.let { itemDao.getById(it) }
        require(draft.id == null || existing != null) { "Item ${draft.id} no longer exists" }

        // Archived items don't take part in the uniqueness rule until they are restored.
        val errors = ItemValidator.validate(
            draft = draft,
            activeItems = itemDao.getActive().map { it.id to it.name },
            checkDuplicates = existing?.isArchived != true,
        )
        if (!errors.isEmpty) return@withTransaction SaveItemResult.Invalid(errors)

        val name = draft.name.trim()
        val defaultUnit = draft.defaultUnit?.trim()?.ifEmpty { null }
        val itemId = if (existing == null) {
            itemDao.insert(
                ItemEntity(
                    name = name,
                    category = draft.category,
                    defaultUnit = defaultUnit,
                    sortOrder = itemDao.maxSortOrder() + 1,
                    createdAt = timeSource.now().toEpochMilli(),
                ),
            )
        } else {
            itemDao.update(existing.copy(name = name, category = draft.category, defaultUnit = defaultUnit))
            existing.id
        }

        // Presets: delete the removed ones (entries keep their snapshots), update the rest in order.
        val currentOptions = optionDao.getForItem(itemId).associateBy { it.id }
        val keptIds = draft.options.mapNotNull { it.id }.toSet()
        optionDao.deleteByIds(currentOptions.keys.filter { it !in keptIds })
        draft.options.forEachIndexed { index, option ->
            val entity = ItemOptionEntity(
                itemId = itemId,
                amount = option.amount,
                unit = option.unit.trim(),
                label = option.effectiveLabel,
                sortOrder = index,
            )
            if (option.id != null && option.id in currentOptions) {
                optionDao.update(entity.copy(id = option.id))
            } else {
                optionDao.insert(entity)
            }
        }
        SaveItemResult.Saved(itemId)
    }

    /** Items with log entries can't be deleted (only archived), so history is never lost. */
    suspend fun deleteItem(id: Long): DeleteItemResult = db.withTransaction {
        if (itemDao.getById(id) == null) return@withTransaction DeleteItemResult.NotFound
        val entryCount = entryDao.countForItem(id)
        if (entryCount > 0) return@withTransaction DeleteItemResult.HasEntries(entryCount)
        itemDao.deleteById(id)
        DeleteItemResult.Deleted
    }

    suspend fun setArchived(id: Long, archived: Boolean): ArchiveResult = db.withTransaction {
        val item = itemDao.getById(id) ?: return@withTransaction ArchiveResult.Done
        if (!archived) {
            val active = itemDao.getActive().map { it.id to it.name }
            if (ItemValidator.isDuplicate(item.name, item.id, active)) {
                return@withTransaction ArchiveResult.DuplicateName
            }
        }
        // A restored item goes to the end of the Log screen.
        val sortOrder = if (archived) item.sortOrder else itemDao.maxSortOrder() + 1
        itemDao.setArchived(id, archived, sortOrder)
        ArchiveResult.Done
    }

    /** Moves an active item one place up or down in the Log screen order. */
    suspend fun moveItem(id: Long, up: Boolean) {
        db.withTransaction {
            val active = itemDao.getActive().toMutableList()
            val index = active.indexOfFirst { it.id == id }
            val target = if (up) index - 1 else index + 1
            if (index < 0 || target !in active.indices) return@withTransaction
            active.add(target, active.removeAt(index))
            active.forEachIndexed { order, item ->
                if (item.sortOrder != order) itemDao.updateSortOrder(item.id, order)
            }
        }
    }
}
