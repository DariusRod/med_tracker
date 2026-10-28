package app.doselog.ui.components

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor
import java.util.Locale

/**
 * Date and time formatting for the UI. Times follow the phone's 12/24-hour setting; dates use
 * the phone's locale (e.g. "Tue 28 Oct" in Australia).
 */
@Stable
class DoseLogFormats(locale: Locale, val is24Hour: Boolean) {
    private fun pattern(skeleton: String, locale: Locale): DateTimeFormatter =
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)

    private val timeFormat = pattern(if (is24Hour) "Hm" else "hm", locale)
    private val shortDateFormat = pattern("EEEdMMM", locale)
    private val shortDateWithYearFormat = pattern("EEEdMMMyyyy", locale)
    private val longDateFormat = pattern("EEEEdMMMM", locale)
    private val longDateWithYearFormat = pattern("EEEEdMMMMyyyy", locale)
    private val numericDateFormat = pattern("ddMMyyyy", locale)
    private val dayMonthFormat = pattern("dMMM", locale)
    private val monthYearFormat = pattern("MMMMyyyy", locale)

    fun time(value: TemporalAccessor): String = timeFormat.format(value)

    /** "Tue 28 Oct", with the year when it isn't the current year. */
    fun shortDate(date: LocalDate, today: LocalDate = LocalDate.now()): String =
        if (date.year == today.year) shortDateFormat.format(date) else shortDateWithYearFormat.format(date)

    fun numericDate(date: LocalDate): String = numericDateFormat.format(date)

    fun dayMonth(date: LocalDate): String = dayMonthFormat.format(date)

    fun monthYear(date: LocalDate): String = monthYearFormat.format(date)

    /** "Tuesday 28 October", with the year when it isn't the current year. */
    fun longDate(date: LocalDate, today: LocalDate = LocalDate.now()): String =
        if (date.year == today.year) longDateFormat.format(date) else longDateWithYearFormat.format(date)

    /** "Today", "Yesterday" or "Tuesday 28 October". */
    fun dayHeader(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> longDate(date, today)
    }

    /** "08:15" for today, otherwise "Tue 28 Oct, 08:15". */
    fun dateTime(value: ZonedDateTime, today: LocalDate): String =
        if (value.toLocalDate() == today) time(value) else "${shortDate(value.toLocalDate(), today)}, ${time(value)}"
}

val LocalFormats = staticCompositionLocalOf { DoseLogFormats(Locale.getDefault(), is24Hour = true) }

@Composable
fun rememberDoseLogFormats(): DoseLogFormats {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]
    val is24Hour = DateFormat.is24HourFormat(context)
    return remember(locale, is24Hour) { DoseLogFormats(locale, is24Hour) }
}
