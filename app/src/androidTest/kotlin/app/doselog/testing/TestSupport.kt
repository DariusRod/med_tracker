package app.doselog.testing

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.doselog.data.db.AppDatabase
import app.doselog.domain.TimeSource
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

val SYDNEY: ZoneId = ZoneId.of("Australia/Sydney")
val LONDON: ZoneId = ZoneId.of("Europe/London")

/** A controllable clock: starts at 2026-10-28 08:15 Sydney time. */
class FakeTimeSource(
    var instant: Instant = LocalDateTime.parse("2026-10-28T08:15:00").atZone(SYDNEY).toInstant(),
    var zoneId: ZoneId = SYDNEY,
) : TimeSource {
    override fun now(): Instant = instant
    override fun zone(): ZoneId = zoneId

    fun advanceBy(duration: Duration) {
        instant = instant.plus(duration)
    }
}

fun inMemoryDatabase(): AppDatabase = Room
    .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
    .build()

fun at(dateTime: String, zone: ZoneId = SYDNEY): Instant = LocalDateTime.parse(dateTime).atZone(zone).toInstant()
