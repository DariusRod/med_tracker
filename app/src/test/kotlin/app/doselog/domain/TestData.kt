package app.doselog.domain

import app.doselog.domain.model.Category
import app.doselog.domain.model.DailyScore
import app.doselog.domain.model.LogEntry
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

val SYDNEY: ZoneId = ZoneId.of("Australia/Sydney")
val LONDON: ZoneId = ZoneId.of("Europe/London")

fun instantAt(dateTime: String, zone: ZoneId): Instant = LocalDateTime.parse(dateTime).atZone(zone).toInstant()

fun entry(
    id: Long,
    takenAt: Instant,
    zone: ZoneId = SYDNEY,
    amount: Double? = 1.0,
    unit: String = "pill",
    itemId: Long = 1,
    itemName: String = "Ibuprofen",
    category: Category = Category.MEDICATION,
) = LogEntry(
    id = id,
    itemId = itemId,
    optionId = null,
    itemName = itemName,
    category = category,
    amount = amount,
    unit = unit,
    label = AmountFormat.label(amount, unit),
    takenAt = takenAt,
    zone = zone,
    recordedAt = takenAt,
    updatedAt = null,
    description = null,
)

fun score(id: Long, value: Int, scoredAt: Instant, zone: ZoneId = SYDNEY) = DailyScore(
    id = id,
    score = value,
    scoredAt = scoredAt,
    zone = zone,
    recordedAt = scoredAt,
    note = null,
)
