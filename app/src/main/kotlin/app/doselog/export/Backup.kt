package app.doselog.export

import app.doselog.data.db.DailyScoreEntity
import app.doselog.data.db.ItemEntity
import app.doselog.data.db.ItemOptionEntity
import app.doselog.data.db.LogEntryEntity
import app.doselog.domain.model.Category
import app.doselog.domain.model.ScoreRules
import app.doselog.domain.model.isValidZoneId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A complete copy of every table, read inside one transaction. */
data class DatabaseSnapshot(
    val items: List<ItemEntity>,
    val options: List<ItemOptionEntity>,
    val entries: List<LogEntryEntity>,
    val scores: List<DailyScoreEntity>,
) {
    val counts: SnapshotCounts get() = SnapshotCounts(items.size, options.size, entries.size, scores.size)
}

data class SnapshotCounts(val items: Int, val presets: Int, val entries: Int, val scores: Int)

// ---- JSON backup format ----

@Serializable
data class BackupItem(
    val id: Long,
    val name: String,
    val category: String,
    val defaultUnit: String? = null,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long,
)

@Serializable
data class BackupOption(
    val id: Long,
    val itemId: Long,
    val amount: Double? = null,
    val unit: String,
    val label: String,
    val sortOrder: Int = 0,
)

@Serializable
data class BackupEntry(
    val id: Long,
    val itemId: Long,
    val optionId: Long? = null,
    val itemNameSnapshot: String,
    val categorySnapshot: String,
    val amountSnapshot: Double? = null,
    val unitSnapshot: String,
    val labelSnapshot: String,
    val takenAt: Long,
    val zoneId: String,
    val recordedAt: Long,
    val updatedAt: Long? = null,
    val description: String? = null,
)

@Serializable
data class BackupScore(
    val id: Long,
    val score: Int,
    val scoredAt: Long,
    val zoneId: String,
    val recordedAt: Long,
    val note: String? = null,
)

@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val formatVersion: Int = FORMAT_VERSION,
    val schemaVersion: Int,
    val appVersion: String,
    val exportedAt: String,
    val items: List<BackupItem>,
    val itemOptions: List<BackupOption>,
    val logEntries: List<BackupEntry>,
    val dailyScores: List<BackupScore>,
) {
    companion object {
        const val FORMAT = "doselog-backup"
        const val FORMAT_VERSION = 1
    }
}

object BackupJson {
    val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    /** @throws kotlinx.serialization.SerializationException or IllegalArgumentException on bad input. */
    fun decode(text: String): BackupFile = json.decodeFromString(BackupFile.serializer(), text)

    fun fromSnapshot(snapshot: DatabaseSnapshot, schemaVersion: Int, appVersion: String, exportedAt: String) = BackupFile(
        schemaVersion = schemaVersion,
        appVersion = appVersion,
        exportedAt = exportedAt,
        items = snapshot.items.map {
            BackupItem(it.id, it.name, it.category.name, it.defaultUnit, it.isArchived, it.sortOrder, it.createdAt)
        },
        itemOptions = snapshot.options.map { BackupOption(it.id, it.itemId, it.amount, it.unit, it.label, it.sortOrder) },
        logEntries = snapshot.entries.map {
            BackupEntry(
                it.id, it.itemId, it.optionId, it.itemNameSnapshot, it.categorySnapshot, it.amountSnapshot,
                it.unitSnapshot, it.labelSnapshot, it.takenAt, it.zoneId, it.recordedAt, it.updatedAt, it.description,
            )
        },
        dailyScores = snapshot.scores.map { BackupScore(it.id, it.score, it.scoredAt, it.zoneId, it.recordedAt, it.note) },
    )

    /** Converts a parsed backup; unknown categories are reported as errors rather than guessed. */
    fun toSnapshot(file: BackupFile): Pair<DatabaseSnapshot, List<String>> {
        val errors = mutableListOf<String>()
        if (file.format != BackupFile.FORMAT) errors += "This isn't a DoseLog backup file."
        if (file.formatVersion > BackupFile.FORMAT_VERSION) {
            errors += "This backup was made by a newer version of DoseLog. Update the app to restore it."
        }
        val items = file.items.map { item ->
            val category = Category.entries.firstOrNull { it.name == item.category }
            if (category == null) errors += "Item ${item.id} has an unknown category \"${item.category}\"."
            ItemEntity(item.id, item.name, category ?: Category.OTHER, item.defaultUnit, item.isArchived, item.sortOrder, item.createdAt)
        }
        val snapshot = DatabaseSnapshot(
            items = items,
            options = file.itemOptions.map { ItemOptionEntity(it.id, it.itemId, it.amount, it.unit, it.label, it.sortOrder) },
            entries = file.logEntries.map {
                LogEntryEntity(
                    it.id, it.itemId, it.optionId, it.itemNameSnapshot, it.categorySnapshot, it.amountSnapshot,
                    it.unitSnapshot, it.labelSnapshot, it.takenAt, it.zoneId, it.recordedAt, it.updatedAt, it.description,
                )
            },
            scores = file.dailyScores.map { DailyScoreEntity(it.id, it.score, it.scoredAt, it.zoneId, it.recordedAt, it.note) },
        )
        return snapshot to errors
    }
}

/** Checks a snapshot before it may replace the database. Returns human-readable problems. */
object SnapshotValidator {
    private const val MAX_REPORTED = 5

    fun validate(snapshot: DatabaseSnapshot): List<String> {
        val errors = mutableListOf<String>()
        fun duplicates(table: String, ids: List<Long>) {
            val dupes = ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
            if (dupes.isNotEmpty()) errors += "$table has duplicate ids: ${dupes.take(MAX_REPORTED).joinToString()}"
        }
        duplicates("Items", snapshot.items.map { it.id })
        duplicates("Presets", snapshot.options.map { it.id })
        duplicates("Entries", snapshot.entries.map { it.id })
        duplicates("Scores", snapshot.scores.map { it.id })

        val itemIds = snapshot.items.map { it.id }.toSet()
        val optionOwner = snapshot.options.associate { it.id to it.itemId }

        snapshot.items.filter { it.name.isBlank() }.forEach { errors += "Item ${it.id} has no name." }
        snapshot.options.filter { it.itemId !in itemIds }.forEach { errors += "Preset ${it.id} belongs to missing item ${it.itemId}." }
        snapshot.options.filter { !it.amount.isValidAmount() }.forEach { errors += "Preset ${it.id} has an invalid amount." }
        snapshot.entries.forEach { entry ->
            if (entry.itemId !in itemIds) errors += "Entry ${entry.id} refers to missing item ${entry.itemId}."
            val owner = entry.optionId?.let { optionOwner[it] }
            if (entry.optionId != null && owner != entry.itemId) errors += "Entry ${entry.id} refers to an invalid preset."
            if (!entry.amountSnapshot.isValidAmount()) errors += "Entry ${entry.id} has an invalid amount."
            if (!isValidZoneId(entry.zoneId)) errors += "Entry ${entry.id} has an invalid time zone \"${entry.zoneId}\"."
        }
        snapshot.scores.forEach { score ->
            if (!ScoreRules.isValid(score.score)) errors += "Score ${score.id} is ${score.score}; scores must be 1–20."
            if (!isValidZoneId(score.zoneId)) errors += "Score ${score.id} has an invalid time zone \"${score.zoneId}\"."
        }
        return if (errors.size > MAX_REPORTED) errors.take(MAX_REPORTED) + "…and ${errors.size - MAX_REPORTED} more problems." else errors
    }

    private fun Double?.isValidAmount() = this == null || (isFinite() && this >= 0.0)
}
