package app.doselog.sample

import androidx.room.withTransaction
import app.doselog.data.db.AppDatabase
import app.doselog.data.repository.DoseSelection
import app.doselog.data.repository.ItemRepository
import app.doselog.data.repository.LogRepository
import app.doselog.data.repository.NewLogEntry
import app.doselog.data.repository.SaveItemResult
import app.doselog.data.repository.ScoreRepository
import app.doselog.domain.TimeSource
import app.doselog.domain.model.Category
import app.doselog.domain.model.ItemDraft
import app.doselog.domain.model.OptionDraft
import java.time.LocalTime
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Debug builds only: fills an empty database with realistic example data. */
class SampleData @Inject constructor(
    private val db: AppDatabase,
    private val itemRepository: ItemRepository,
    private val logRepository: LogRepository,
    private val scoreRepository: ScoreRepository,
    private val timeSource: TimeSource,
) {
    val isAvailable: Boolean = true

    suspend fun load(): String {
        if (db.itemDao().count() > 0) return "Sample data can only be added while there are no items."
        var entryCount = 0
        var scoreCount = 0
        db.withTransaction {
            val ids = ITEMS.associate { draft ->
                draft.name to (itemRepository.saveItem(draft) as SaveItemResult.Saved).id
            }
            val presets = ids.mapValues { (_, id) -> requireNotNull(itemRepository.getItem(id)).options }
            val random = Random(42)
            val now = timeSource.now()
            val zone = timeSource.zone()
            val today = timeSource.today()

            suspend fun log(name: String, presetIndex: Int, hour: Int, minute: Int, dayOffset: Long, note: String? = null) {
                val takenAt = today.minusDays(dayOffset).atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant()
                if (takenAt.isAfter(now)) return
                val option = presets.getValue(name)[presetIndex]
                logRepository.addEntry(NewLogEntry(ids.getValue(name), DoseSelection.Preset(option.id), note, takenAt, zone))
                entryCount++
            }

            for (day in 59L downTo 0L) {
                if (random.nextFloat() < 0.9f) log("Vitamin D", if (random.nextBoolean()) 0 else 1, 8, random.nextInt(0, 30), day)
                if (random.nextFloat() < 0.6f) log("Porridge", random.nextInt(2), 7, random.nextInt(15, 59), day)
                repeat(random.nextInt(1, 4)) { i -> log("Coffee", 0, 9 + i * 3, random.nextInt(60), day) }
                repeat(random.nextInt(2, 6)) { i -> log("Water", random.nextInt(2), 10 + i * 2, random.nextInt(60), day) }
                if (random.nextFloat() < 0.7f) log("Magnesium", 0, 21, random.nextInt(15, 50), day)
                if (random.nextFloat() < 0.25f) {
                    log("Ibuprofen", random.nextInt(2), 13, random.nextInt(60), day, "Headache".takeIf { random.nextBoolean() })
                }
                if (random.nextFloat() < 0.12f) log("Paracetamol", 0, 18, random.nextInt(60), day)

                // A slow wave plus noise, so the charts have something to show.
                val base = 12 + 5 * sin(day / 6.0)
                repeat(if (random.nextFloat() < 0.25f) 2 else 1) { i ->
                    val at = today.minusDays(day).atTime(12 + i * 8, random.nextInt(60)).atZone(zone).toInstant()
                    if (!at.isAfter(now)) {
                        val value = (base + random.nextInt(-3, 4)).roundToInt().coerceIn(1, 20)
                        scoreRepository.addScore(value, at, zone, "Slept badly".takeIf { value < 8 })
                        scoreCount++
                    }
                }
            }
        }
        return "Added ${ITEMS.size} items, $entryCount entries and $scoreCount scores"
    }

    private companion object {
        fun preset(amount: Double?, unit: String, label: String = "") = OptionDraft(null, amount, unit, label)

        val ITEMS = listOf(
            ItemDraft(null, "Ibuprofen", Category.MEDICATION, "mg", listOf(preset(200.0, "mg"), preset(400.0, "mg"))),
            ItemDraft(null, "Paracetamol", Category.MEDICATION, "mg", listOf(preset(500.0, "mg"), preset(1000.0, "mg"))),
            ItemDraft(null, "Vitamin D", Category.SUPPLEMENT, "IU", listOf(preset(1000.0, "IU"), preset(2000.0, "IU"))),
            ItemDraft(null, "Magnesium", Category.SUPPLEMENT, "mg", listOf(preset(300.0, "mg"))),
            ItemDraft(null, "Porridge", Category.FOOD, "bowl", listOf(preset(null, "", "Small bowl"), preset(null, "", "Large bowl"))),
            ItemDraft(null, "Coffee", Category.FOOD, "cup", listOf(preset(1.0, "cup"))),
            ItemDraft(null, "Water", Category.OTHER, "ml", listOf(preset(250.0, "ml"), preset(500.0, "ml"))),
        )
    }
}
