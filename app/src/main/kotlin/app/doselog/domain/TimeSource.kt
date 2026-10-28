package app.doselog.domain

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** Source of "now" and the device time zone. Injected so tests can control time. */
interface TimeSource {
    fun now(): Instant
    fun zone(): ZoneId
    fun today(): LocalDate = now().atZone(zone()).toLocalDate()
}

class SystemTimeSource @Inject constructor() : TimeSource {
    override fun now(): Instant = Instant.now()

    // Read every time: the user may travel or change the zone while the app is running.
    override fun zone(): ZoneId = ZoneId.systemDefault()
}

/** Emits the current local date, and again whenever it changes (midnight or a zone change). */
fun TimeSource.todayFlow(): Flow<LocalDate> = flow {
    while (true) {
        val now = now()
        val zone = zone()
        val today = now.atZone(zone).toLocalDate()
        emit(today)
        val nextMidnight = today.plusDays(1).atStartOfDay(zone).toInstant()
        delay(Duration.between(now, nextMidnight).toMillis().coerceIn(1_000L, 60_000L))
    }
}.distinctUntilChanged()
