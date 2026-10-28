package app.doselog.data.repository

import app.doselog.data.db.AppDatabase
import app.doselog.data.db.DailyScoreEntity
import app.doselog.domain.DayGrouping
import app.doselog.domain.TimeSource
import app.doselog.domain.model.DailyScore
import app.doselog.domain.model.DateRange
import app.doselog.domain.model.ScoreRules
import app.doselog.domain.model.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScoreRepository @Inject constructor(
    db: AppDatabase,
    private val timeSource: TimeSource,
) {
    private val scoreDao = db.dailyScoreDao()

    /** Every score, newest first. */
    fun observeAllScores(): Flow<List<DailyScore>> =
        scoreDao.observeAll().map { list -> list.map { it.toDomain() } }

    /** Scores whose own local date falls in [range], newest first. */
    fun observeScores(range: DateRange): Flow<List<DailyScore>> {
        val (from, to) = DayGrouping.queryWindow(range, timeSource.zone())
        return scoreDao.observeBetween(from.toEpochMilli(), to.toEpochMilli())
            .map { list -> list.map { it.toDomain() }.filter { it.localDate in range } }
    }

    /** @throws IllegalArgumentException if [score] is outside 1..20. */
    suspend fun addScore(score: Int, scoredAt: Instant, zone: ZoneId, note: String?): DailyScore {
        ScoreRules.requireValid(score)
        val entity = DailyScoreEntity(
            score = score,
            scoredAt = scoredAt.toEpochMilli(),
            zoneId = zone.id,
            recordedAt = timeSource.now().toEpochMilli(),
            note = note?.trim()?.ifEmpty { null },
        )
        return entity.copy(id = scoreDao.insert(entity)).toDomain()
    }

    /** @throws IllegalArgumentException if [score] is outside 1..20 or the score no longer exists. */
    suspend fun updateScore(id: Long, score: Int, scoredAt: Instant, zone: ZoneId, note: String?): DailyScore {
        ScoreRules.requireValid(score)
        val current = requireNotNull(scoreDao.getById(id)) { "Score $id not found" }
        val updated = current.copy(
            score = score,
            scoredAt = scoredAt.toEpochMilli(),
            zoneId = zone.id,
            note = note?.trim()?.ifEmpty { null },
        )
        scoreDao.update(updated)
        return updated.toDomain()
    }

    suspend fun deleteScore(id: Long): Boolean = scoreDao.deleteById(id) > 0
}
