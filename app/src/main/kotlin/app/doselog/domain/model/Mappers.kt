package app.doselog.domain.model

import app.doselog.data.db.DailyScoreEntity
import app.doselog.data.db.ItemOptionEntity
import app.doselog.data.db.ItemWithOptions
import app.doselog.data.db.LogEntryEntity
import java.time.DateTimeException
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

fun ItemWithOptions.toDomain(): Item = Item(
    id = item.id,
    name = item.name,
    category = item.category,
    defaultUnit = item.defaultUnit,
    isArchived = item.isArchived,
    sortOrder = item.sortOrder,
    createdAt = Instant.ofEpochMilli(item.createdAt),
    options = options.sortedWith(compareBy({ it.sortOrder }, { it.id })).map { it.toDomain() },
)

fun ItemOptionEntity.toDomain(): ItemOption = ItemOption(
    id = id,
    itemId = itemId,
    amount = amount,
    unit = unit,
    label = label,
    sortOrder = sortOrder,
)

fun LogEntryEntity.toDomain(): LogEntry = LogEntry(
    id = id,
    itemId = itemId,
    optionId = optionId,
    itemName = itemNameSnapshot,
    category = Category.fromStored(categorySnapshot),
    amount = amountSnapshot,
    unit = unitSnapshot,
    label = labelSnapshot,
    takenAt = Instant.ofEpochMilli(takenAt),
    zone = parseZone(zoneId),
    recordedAt = Instant.ofEpochMilli(recordedAt),
    updatedAt = updatedAt?.let(Instant::ofEpochMilli),
    description = description,
)

fun DailyScoreEntity.toDomain(): DailyScore = DailyScore(
    id = id,
    score = score,
    scoredAt = Instant.ofEpochMilli(scoredAt),
    zone = parseZone(zoneId),
    recordedAt = Instant.ofEpochMilli(recordedAt),
    note = note,
)

/** Zone ids are validated on write and on restore; UTC is only a last-resort fallback. */
fun parseZone(zoneId: String): ZoneId = try {
    ZoneId.of(zoneId)
} catch (_: DateTimeException) {
    ZoneOffset.UTC
}

fun isValidZoneId(zoneId: String): Boolean = try {
    ZoneId.of(zoneId)
    true
} catch (_: DateTimeException) {
    false
}
