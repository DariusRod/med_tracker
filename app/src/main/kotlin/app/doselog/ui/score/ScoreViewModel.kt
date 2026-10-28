package app.doselog.ui.score

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.doselog.data.repository.ScoreRepository
import app.doselog.domain.ScoreStats
import app.doselog.domain.ScoreSummary
import app.doselog.domain.TimeSource
import app.doselog.domain.model.DailyScore
import app.doselog.domain.model.ScoreRules
import app.doselog.domain.todayFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

data class ScoreUiState(
    val isLoading: Boolean = true,
    val history: List<DailyScore> = emptyList(),
    val summary: ScoreSummary = ScoreSummary(null, null, null),
    val today: LocalDate = LocalDate.now(),
    val isSaving: Boolean = false,
    val editing: DailyScore? = null,
)

sealed interface ScoreEvent {
    data class Saved(val id: Long, val score: Int, val scoredAt: ZonedDateTime) : ScoreEvent
    data class Message(val text: String) : ScoreEvent
}

@HiltViewModel
class ScoreViewModel @Inject constructor(
    private val scoreRepository: ScoreRepository,
    private val timeSource: TimeSource,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // Form fields come straight from SavedStateHandle, so they survive process death and text
    // fields update synchronously. There is deliberately no default score.
    val selectedScore: StateFlow<Int?> = savedStateHandle.getStateFlow<Int?>(KEY_SCORE, null)
    val takenAtMillis: StateFlow<Long?> = savedStateHandle.getStateFlow<Long?>(KEY_TIME, null)
    val note: StateFlow<String> = savedStateHandle.getStateFlow(KEY_NOTE, "")
    private val editingId = savedStateHandle.getStateFlow<Long?>(KEY_EDITING, null)
    private val isSaving = MutableStateFlow(false)

    val uiState: StateFlow<ScoreUiState> = combine(
        scoreRepository.observeAllScores(),
        timeSource.todayFlow(),
        isSaving,
        editingId,
    ) { scores, today, saving, editing ->
        ScoreUiState(
            isLoading = false,
            history = scores,
            summary = ScoreStats.summarize(scores, today),
            today = today,
            isSaving = saving,
            editing = editing?.let { id -> scores.firstOrNull { it.id == id } },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScoreUiState())

    private val _events = Channel<ScoreEvent>(Channel.BUFFERED)
    val events: Flow<ScoreEvent> = _events.receiveAsFlow()

    fun onScoreSelected(score: Int) {
        if (ScoreRules.isValid(score)) savedStateHandle[KEY_SCORE] = score
    }

    fun onTimeChange(value: Instant?) {
        savedStateHandle[KEY_TIME] = value?.toEpochMilli()
    }

    fun onNoteChange(value: String) {
        savedStateHandle[KEY_NOTE] = value
    }

    fun submit() {
        val score = selectedScore.value ?: return
        if (isSaving.value) return
        isSaving.value = true
        viewModelScope.launch {
            try {
                val at = takenAtMillis.value?.let(Instant::ofEpochMilli) ?: timeSource.now()
                val saved = scoreRepository.addScore(score, at, timeSource.zone(), note.value)
                savedStateHandle[KEY_SCORE] = null
                savedStateHandle[KEY_TIME] = null
                savedStateHandle[KEY_NOTE] = ""
                _events.send(ScoreEvent.Saved(saved.id, saved.score, saved.localDateTime))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(ScoreEvent.Message("Couldn't save: ${e.message}"))
            } finally {
                isSaving.update { false }
            }
        }
    }

    fun undo(id: Long) {
        viewModelScope.launch { scoreRepository.deleteScore(id) }
    }

    fun openScore(id: Long?) {
        savedStateHandle[KEY_EDITING] = id
    }

    fun updateScore(id: Long, score: Int, at: Instant, zone: ZoneId, note: String) {
        viewModelScope.launch {
            try {
                scoreRepository.updateScore(id, score, at, zone, note)
                openScore(null)
                _events.send(ScoreEvent.Message("Score updated"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(ScoreEvent.Message("Couldn't update: ${e.message}"))
            }
        }
    }

    fun deleteScore(id: Long) {
        viewModelScope.launch {
            scoreRepository.deleteScore(id)
            openScore(null)
            _events.send(ScoreEvent.Message("Score deleted"))
        }
    }

    fun deviceZone(): ZoneId = timeSource.zone()

    private companion object {
        const val KEY_SCORE = "score"
        const val KEY_TIME = "time"
        const val KEY_NOTE = "note"
        const val KEY_EDITING = "editing"
    }
}
