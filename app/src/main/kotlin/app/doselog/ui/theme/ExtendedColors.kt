package app.doselog.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import app.doselog.domain.model.Category
import app.doselog.domain.model.ScoreRules

@Immutable
data class TonalPair(val container: Color, val content: Color)

/** Colors Material 3 has no role for: category tags and the red-to-green score scale. */
@Immutable
data class ExtendedColors(
    val isDark: Boolean,
    val medication: TonalPair,
    val food: TonalPair,
    val supplement: TonalPair,
    val other: TonalPair,
) {
    fun category(category: Category): TonalPair = when (category) {
        Category.MEDICATION -> medication
        Category.FOOD -> food
        Category.SUPPLEMENT -> supplement
        Category.OTHER -> other
    }

    /** Red (score 1) through amber to green (score 20). */
    fun score(score: Int): TonalPair {
        val fraction = (score.coerceIn(ScoreRules.MIN, ScoreRules.MAX) - ScoreRules.MIN).toFloat() /
            (ScoreRules.MAX - ScoreRules.MIN)
        val hue = 4f + fraction * 128f
        val container = if (isDark) {
            Color.hsv(hue, 0.62f, 0.78f)
        } else {
            Color.hsv(hue, 0.74f, 0.80f)
        }
        val content = if (container.luminance() > 0.42f) Color(0xFF1A1C1C) else Color.White
        return TonalPair(container, content)
    }
}

internal val LightExtendedColors = ExtendedColors(
    isDark = false,
    medication = TonalPair(Color(0xFFDCE5FF), Color(0xFF1F3F86)),
    food = TonalPair(Color(0xFFFFE2CF), Color(0xFF7A3500)),
    supplement = TonalPair(Color(0xFFD3F2D5), Color(0xFF1C5228)),
    other = TonalPair(Color(0xFFEEDDFF), Color(0xFF572C88)),
)

internal val DarkExtendedColors = ExtendedColors(
    isDark = true,
    medication = TonalPair(Color(0xFF243A6E), Color(0xFFD9E2FF)),
    food = TonalPair(Color(0xFF5D2C08), Color(0xFFFFDCC8)),
    supplement = TonalPair(Color(0xFF1F4A2B), Color(0xFFC6F0CA)),
    other = TonalPair(Color(0xFF462F6B), Color(0xFFECDCFF)),
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }
