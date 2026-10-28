package app.doselog.domain

import java.math.BigDecimal
import java.math.RoundingMode

object AmountFormat {
    private const val MAX_DECIMALS = 6

    /** "10" not "10.0"; "1.5" stays "1.5"; float noise such as 0.30000000000000004 becomes "0.3". */
    fun format(value: Double, maxDecimals: Int = MAX_DECIMALS): String {
        if (value.isNaN() || value.isInfinite()) return value.toString()
        val rounded = BigDecimal.valueOf(value).setScale(maxDecimals, RoundingMode.HALF_UP).stripTrailingZeros()
        return if (rounded.signum() == 0) "0" else rounded.toPlainString()
    }

    /** Lossless plain form for files: "10" not "10.0", and parsing it back gives the same Double. */
    fun exact(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return value.toString()
        val plain = BigDecimal(value.toString()).stripTrailingZeros()
        return if (plain.signum() == 0) "0" else plain.toPlainString()
    }

    /** Auto label for a preset or custom dose, e.g. "10 mg", "2 pill", "1.5 g". */
    fun label(amount: Double?, unit: String): String {
        val cleanUnit = unit.trim()
        if (amount == null) return cleanUnit
        return if (cleanUnit.isEmpty()) format(amount) else "${format(amount)} $cleanUnit"
    }
}

sealed interface AmountInput {
    data object Empty : AmountInput
    data class Valid(val value: Double) : AmountInput
    data class Invalid(val error: AmountError) : AmountInput
}

enum class AmountError(val message: String) {
    NEGATIVE("Amount can't be negative"),
    NOT_A_NUMBER("Enter a number, e.g. 2.5"),
    TOO_LARGE("That amount is too large"),
}

object AmountParser {
    const val MAX_AMOUNT = 1_000_000_000.0

    /** Accepts "." or "," as the decimal separator (e.g. "1.5" and "1,5"); rejects negatives. */
    fun parse(input: String): AmountInput {
        val text = input.trim()
        if (text.isEmpty()) return AmountInput.Empty
        if (text.startsWith("-") || text.startsWith("\u2212")) return AmountInput.Invalid(AmountError.NEGATIVE)

        val normalized = text.replace(',', '.')
        val separators = normalized.count { it == '.' }
        val digits = normalized.count { it in '0'..'9' }
        if (separators > 1 || digits == 0 || digits + separators != normalized.length) {
            return AmountInput.Invalid(AmountError.NOT_A_NUMBER)
        }
        val value = normalized.toDoubleOrNull() ?: return AmountInput.Invalid(AmountError.NOT_A_NUMBER)
        if (value > MAX_AMOUNT) return AmountInput.Invalid(AmountError.TOO_LARGE)
        return AmountInput.Valid(value)
    }

    /** Text for editing an existing amount: "10", "1.5". */
    fun toEditText(value: Double?): String = value?.let(AmountFormat::format).orEmpty()
}

/** The unit suggestions offered as chips when entering a dose. */
val UnitSuggestions = listOf("mg", "mcg", "g", "ml", "pill", "tablet", "capsule", "drop", "serving", "piece")
