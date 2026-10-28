package app.doselog.domain

import app.doselog.domain.model.DailyScore
import app.doselog.domain.model.LogEntry
import java.time.LocalDate
import java.util.Locale

data class UnitTotal(val unit: String, val total: Double, val count: Int)

object UnitTotals {
    /** "mg", " MG" and "Mg" are the same unit; anything else is never added together. */
    fun normalize(unit: String): String = unit.trim().lowercase(Locale.ROOT)

    /** Totals per unit, most-used unit first. Entries without an amount are not totalled. */
    fun totals(entries: List<LogEntry>): List<UnitTotal> =
        entries
            .filter { it.amount != null }
            .groupBy { normalize(it.unit) }
            .map { (_, sameUnit) ->
                // Show the spelling the user used most often.
                val display = sameUnit.groupingBy { it.unit.trim() }.eachCount().maxBy { it.value }.key
                UnitTotal(unit = display, total = sameUnit.sumOf { it.amount ?: 0.0 }, count = sameUnit.size)
            }
            .sortedWith(compareByDescending<UnitTotal> { it.count }.thenBy { it.unit })
}

data class ScoreSummary(
    val today: DailyScore?,
    val average7Days: Double?,
    val average30Days: Double?,
)

object ScoreStats {
    fun summarize(scores: List<DailyScore>, today: LocalDate): ScoreSummary {
        val dayScores = DayGrouping.dayScores(scores)
        return ScoreSummary(
            today = dayScores[today],
            average7Days = averageOfDays(dayScores, today, 7),
            average30Days = averageOfDays(dayScores, today, 30),
        )
    }

    /** Average day score over the [days] days ending [today]; days without a score are skipped. */
    private fun averageOfDays(dayScores: Map<LocalDate, DailyScore>, today: LocalDate, days: Long): Double? {
        val first = today.minusDays(days - 1)
        val values = dayScores.filterKeys { !it.isBefore(first) && !it.isAfter(today) }.values.map { it.score }
        return if (values.isEmpty()) null else values.average()
    }
}
