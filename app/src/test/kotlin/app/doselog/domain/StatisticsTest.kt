package app.doselog.domain

import app.doselog.domain.model.ScoreRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class UnitTotalsTest {

    @Test
    fun differentUnitsAreNeverAddedTogether() {
        val at = instantAt("2026-10-28T08:00", SYDNEY)
        val totals = UnitTotals.totals(
            listOf(
                entry(1, at, amount = 200.0, unit = "mg"),
                entry(2, at, amount = 400.0, unit = "mg"),
                entry(3, at, amount = 2.0, unit = "pill"),
                entry(4, at, amount = 0.5, unit = "g"),
            ),
        )
        assertEquals(3, totals.size)
        assertEquals(UnitTotal("mg", 600.0, 2), totals[0])
        assertEquals(2.0, totals.first { it.unit == "pill" }.total, 0.0)
        assertEquals(0.5, totals.first { it.unit == "g" }.total, 0.0)
    }

    @Test
    fun unitSpellingIsNormalized() {
        val at = instantAt("2026-10-28T08:00", SYDNEY)
        val totals = UnitTotals.totals(
            listOf(
                entry(1, at, amount = 10.0, unit = "mg"),
                entry(2, at, amount = 10.0, unit = " MG "),
                entry(3, at, amount = 10.0, unit = "mg"),
            ),
        )
        assertEquals(listOf(UnitTotal("mg", 30.0, 3)), totals)
    }

    @Test
    fun entriesWithoutAmountAreNotTotalled() {
        val at = instantAt("2026-10-28T08:00", SYDNEY)
        val totals = UnitTotals.totals(listOf(entry(1, at, amount = null, unit = "bowl"), entry(2, at, amount = 1.0)))
        assertEquals(listOf(UnitTotal("pill", 1.0, 1)), totals)
    }

    @Test
    fun emptyInputGivesNoTotals() {
        assertTrue(UnitTotals.totals(emptyList()).isEmpty())
    }
}

class ScoreRulesTest {

    @Test
    fun boundsAreInclusive() {
        assertTrue(ScoreRules.isValid(1))
        assertTrue(ScoreRules.isValid(20))
        assertFalse(ScoreRules.isValid(0))
        assertFalse(ScoreRules.isValid(21))
        assertFalse(ScoreRules.isValid(-5))
    }

    @Test(expected = IllegalArgumentException::class)
    fun requireValidRejectsOutOfRange() {
        ScoreRules.requireValid(21)
    }
}

class ScoreStatsTest {

    private val today = LocalDate.of(2026, 10, 28)

    @Test
    fun summaryUsesDayScores() {
        val scores = listOf(
            score(1, 10, instantAt("2026-10-28T08:00", SYDNEY)),
            score(2, 16, instantAt("2026-10-28T21:00", SYDNEY)), // latest today -> day score 16
            score(3, 8, instantAt("2026-10-25T12:00", SYDNEY)),
            score(4, 20, instantAt("2026-10-10T12:00", SYDNEY)), // outside 7 days, inside 30
            score(5, 1, instantAt("2026-09-01T12:00", SYDNEY)), // outside 30 days
        )
        val summary = ScoreStats.summarize(scores, today)

        assertEquals(16, summary.today?.score)
        assertEquals((16 + 8) / 2.0, summary.average7Days!!, 1e-9)
        assertEquals((16 + 8 + 20) / 3.0, summary.average30Days!!, 1e-9)
    }

    @Test
    fun noScoresGiveEmptySummary() {
        val summary = ScoreStats.summarize(emptyList(), today)
        assertNull(summary.today)
        assertNull(summary.average7Days)
        assertNull(summary.average30Days)
    }
}
