package app.doselog.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AmountFormatTest {

    @Test
    fun wholeNumbersHaveNoDecimals() {
        assertEquals("10", AmountFormat.format(10.0))
        assertEquals("2", AmountFormat.format(2.0))
        assertEquals("0", AmountFormat.format(0.0))
        assertEquals("1000000", AmountFormat.format(1_000_000.0))
    }

    @Test
    fun fractionsKeepOnlySignificantDigits() {
        assertEquals("1.5", AmountFormat.format(1.5))
        assertEquals("0.25", AmountFormat.format(0.25))
        assertEquals("0.125", AmountFormat.format(0.125))
    }

    @Test
    fun floatingPointNoiseIsRemoved() {
        assertEquals("0.3", AmountFormat.format(0.1 + 0.2))
        assertEquals("2.7", AmountFormat.format(0.9 * 3))
    }

    @Test
    fun labelCombinesAmountAndUnit() {
        assertEquals("10 mg", AmountFormat.label(10.0, "mg"))
        assertEquals("2 pill", AmountFormat.label(2.0, "pill"))
        assertEquals("1.5 g", AmountFormat.label(1.5, "g"))
        assertEquals("1.5 g", AmountFormat.label(1.5, "  g "))
    }

    @Test
    fun labelWithoutUnitOrAmount() {
        assertEquals("3", AmountFormat.label(3.0, ""))
        assertEquals("bowl", AmountFormat.label(null, "bowl"))
        assertEquals("", AmountFormat.label(null, " "))
    }
}

class AmountParserTest {

    private fun valid(text: String): Double = (AmountParser.parse(text) as AmountInput.Valid).value

    private fun error(text: String): AmountError = (AmountParser.parse(text) as AmountInput.Invalid).error

    @Test
    fun acceptsDotAndCommaAsDecimalSeparator() {
        assertEquals(1.5, valid("1.5"), 0.0)
        assertEquals(1.5, valid("1,5"), 0.0)
        assertEquals(0.25, valid("0,25"), 0.0)
    }

    @Test
    fun acceptsLeadingOrTrailingSeparatorAndWhitespace() {
        assertEquals(0.5, valid(",5"), 0.0)
        assertEquals(0.5, valid(".5"), 0.0)
        assertEquals(5.0, valid("5."), 0.0)
        assertEquals(2.0, valid("  2  "), 0.0)
        assertEquals(0.0, valid("0"), 0.0)
    }

    @Test
    fun emptyInputIsEmpty() {
        assertEquals(AmountInput.Empty, AmountParser.parse(""))
        assertEquals(AmountInput.Empty, AmountParser.parse("   "))
    }

    @Test
    fun rejectsNegativeValues() {
        assertEquals(AmountError.NEGATIVE, error("-1"))
        assertEquals(AmountError.NEGATIVE, error("-0,5"))
        assertEquals(AmountError.NEGATIVE, error("\u22122"))
    }

    @Test
    fun rejectsMalformedNumbers() {
        listOf("1.2.3", "1,2.3", "abc", ".", ",", "1e5", "+3", "1 000", "NaN", "Infinity", "٣").forEach {
            assertEquals("input '$it'", AmountError.NOT_A_NUMBER, error(it))
        }
    }

    @Test
    fun rejectsHugeValues() {
        assertEquals(AmountError.TOO_LARGE, error("10000000000"))
    }

    @Test
    fun editTextRoundTrips() {
        assertEquals("10", AmountParser.toEditText(10.0))
        assertEquals("1.5", AmountParser.toEditText(1.5))
        assertEquals("", AmountParser.toEditText(null))
        assertEquals(1.5, valid(AmountParser.toEditText(1.5)), 0.0)
    }
}
