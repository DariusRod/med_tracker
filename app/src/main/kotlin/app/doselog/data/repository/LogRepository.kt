package app.doselog.data.repository

import androidx.room.withTransaction
import app.doselog.data.db.AppDatabase
import app.doselog.data.db.LogEntryEntity
import app.doselog.domain.AmountFormat
import app.doselog.domain.DayGrouping
import app.doselog.domain.TimeSource
import app.doselog.domain.model.DateRange
import app.doselog.domain.model.LogEntry
import app.doselog.domain.model.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Which dose was taken: one of the item's presets, or a one-off amount. */
sealed interface DoseSelection {
    data class Preset(val optionId: Long) : DoseSelection
    data class Custom(val amount: Double, val unit: String) : DoseSelection

    /** Editing only: keep the entry's current dose snapshot. */
    data object Unchanged : DoseSelection
}

data class NewLogEntry(
    val itemId: Long,
    val dose: DoseSelection,
    val description: String?,
    val takenAt: Instant,
    val zone: ZoneId,
)

data class LogEntryEdit(
    val id: Long,
    val dose: DoseSelection,
    val description: String?,
    val takenAt: Instant,
    val zone: ZoneId,
)

@Singleton
class LogRepository @Inject constructor(
    private val db: AppDatabase,
    private val timeSource: TimeSource,
) {
    private val itemDao = db.itemDao()
    private val optionDao = db.itemOptionDao()
    private val entryDao = db.logEntryDao()

    /** Entries whose own local date falls in [range], newest first. */
    fun observeEntries(range: DateRange): Flow<List<LogEntry>> {
        val (from, to) = DayGrouping.queryWindow(range, timeSource.zone())
        return entryDao.observeBetween(from.toEpochMilli(), to.toEpochMilli())
            .map { list -> list.map { it.toDomain() }.filter { it.localDate in range } }
    }

    /** Every entry, newest first. */
    fun observeAllEntries(): Flow<List<LogEntry>> =
        entryDao.observeBetween(Long.MIN_VALUE, Long.MAX_VALUE).map { list -> list.map { it.toDomain() } }

    suspend fun getEntry(id: Long): LogEntry? = entryDao.getById(id)?.toDomain()

    /** Saves a new entry, copying item and preset details into the snapshot columns. */
    suspend fun addEntry(request: NewLogEntry): LogEntry = db.withTransaction {
        val item = requireNotNull(itemDao.getById(request.itemId)) { "Item ${request.itemId} not found" }
        val dose = resolveDose(request.itemId, request.dose)
            ?: throw IllegalArgumentException("A new entry needs a preset or a custom amount")
        val entity = LogEntryEntity(
            itemId = item.id,
            optionId = dose.optionId,
            itemNameSnapshot = item.name,
            categorySnapshot = item.category.name,
            amountSnapshot = dose.amount,
            unitSnapshot = dose.unit,
            labelSnapshot = dose.label,
            takenAt = request.takenAt.toEpochMilli(),
            zoneId = request.zone.id,
            recordedAt = timeSource.now().toEpochMilli(),
            updatedAt = null,
            description = request.description.cleanDescription(),
        )
        entity.copy(id = entryDao.insert(entity)).toDomain()
    }

    /** Changes dose, time and/or description. The item name snapshot and recordedAt never change. */
    suspend fun updateEntry(edit: LogEntryEdit): LogEntry = db.withTransaction {
        val current = requireNotNull(entryDao.getById(edit.id)) { "Entry ${edit.id} not found" }
        val dose = resolveDose(current.itemId, edit.dose)
        val withDose = if (dose == null) {
            current
        } else {
            current.copy(
                optionId = dose.optionId,
                amountSnapshot = dose.amount,
                unitSnapshot = dose.unit,
                labelSnapshot = dose.label,
            )
        }
        val updated = withDose.copy(
            takenAt = edit.takenAt.toEpochMilli(),
            zoneId = edit.zone.id,
            updatedAt = timeSource.now().toEpochMilli(),
            description = edit.description.cleanDescription(),
        )
        entryDao.update(updated)
        updated.toDomain()
    }

    suspend fun deleteEntry(id: Long): Boolean = entryDao.deleteById(id) > 0

    private data class ResolvedDose(val optionId: Long?, val amount: Double?, val unit: String, val label: String)

    /** Null for [DoseSelection.Unchanged]. */
    private suspend fun resolveDose(itemId: Long, selection: DoseSelection): ResolvedDose? = when (selection) {
        is DoseSelection.Preset -> {
            val option = requireNotNull(optionDao.getById(selection.optionId)) { "Preset ${selection.optionId} not found" }
            require(option.itemId == itemId) { "Preset ${option.id} belongs to another item" }
            ResolvedDose(option.id, option.amount, option.unit, option.label)
        }
        is DoseSelection.Custom -> {
            require(selection.amount > 0.0 && selection.amount.isFinite()) { "Amount must be greater than 0" }
            val unit = selection.unit.trim()
            ResolvedDose(null, selection.amount, unit, AmountFormat.label(selection.amount, unit))
        }
        DoseSelection.Unchanged -> null
    }

    private fun String?.cleanDescription(): String? = this?.trim()?.ifEmpty { null }
}
