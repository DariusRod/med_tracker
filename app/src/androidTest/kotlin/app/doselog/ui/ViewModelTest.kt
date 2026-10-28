package app.doselog.ui

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.doselog.data.db.AppDatabase
import app.doselog.data.repository.ItemRepository
import app.doselog.data.repository.LogRepository
import app.doselog.data.repository.SaveItemResult
import app.doselog.data.repository.ScoreRepository
import app.doselog.domain.model.Category
import app.doselog.domain.model.ItemDraft
import app.doselog.domain.model.OptionDraft
import app.doselog.testing.FakeTimeSource
import app.doselog.testing.inMemoryDatabase
import app.doselog.ui.log.LogViewModel
import app.doselog.ui.score.ScoreViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration

/** ViewModels against a real in-memory database, driven on the main thread like the UI does. */
@RunWith(AndroidJUnit4::class)
class ViewModelTest {

    private lateinit var db: AppDatabase
    private val time = FakeTimeSource()
    private lateinit var items: ItemRepository
    private lateinit var log: LogRepository
    private lateinit var scores: ScoreRepository

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        items = ItemRepository(db, time)
        log = LogRepository(db, time)
        scores = ScoreRepository(db, time)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun ibuprofen() = (items.saveItem(
        ItemDraft(null, "Ibuprofen", Category.MEDICATION, "mg", listOf(OptionDraft(null, 200.0, "mg", ""))),
    ) as SaveItemResult.Saved).id.let { items.getItem(it)!! }

    @Test
    fun untouchedTimeIsTheMomentSubmitIsPressed() = runBlocking<Unit> {
        val item = ibuprofen()
        val vm = withContext(Dispatchers.Main) { LogViewModel(items, log, time, SavedStateHandle()) }
        withContext(Dispatchers.Main) { vm.selectOption(item.id, item.options.first().id) }

        time.advanceBy(Duration.ofMinutes(42)) // the screen stayed open for a while
        val pressedAt = time.now()
        withContext(Dispatchers.Main) { vm.submit(item) }

        val entry = withTimeout(5_000) { log.observeAllEntries().first { it.isNotEmpty() } }.single()
        assertEquals(pressedAt, entry.takenAt)
        assertEquals("200 mg", entry.label)
        withTimeout(5_000) { vm.drafts.first { item.id !in it } } // card was cleared
    }

    @Test
    fun cardDraftsSurviveProcessDeath() = runBlocking<Unit> {
        val item = ibuprofen()
        val handle = SavedStateHandle()
        val first = withContext(Dispatchers.Main) { LogViewModel(items, log, time, handle) }
        withContext(Dispatchers.Main) {
            first.toggleCustom(item)
            first.setCustomAmount(item.id, "1,5")
            first.setDescription(item.id, "with food")
        }

        // A new ViewModel restored from the same saved state, as after process death.
        val restored = withContext(Dispatchers.Main) { LogViewModel(items, log, time, SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) })) }
        val draft = restored.drafts.value.getValue(item.id)
        assertEquals("1,5", draft.customAmount)
        assertEquals("mg", draft.customUnit)
        assertEquals("with food", draft.description)
    }

    @Test
    fun scoreHasNoDefaultAndSubmitsTheChosenValue() = runBlocking<Unit> {
        val vm = withContext(Dispatchers.Main) { ScoreViewModel(scores, time, SavedStateHandle()) }
        assertNull(vm.selectedScore.value)
        withContext(Dispatchers.Main) {
            vm.submit() // nothing selected: ignored
            vm.onScoreSelected(25) // out of range: ignored
            vm.onScoreSelected(13)
            vm.onNoteChange("fine")
            vm.submit()
        }
        val saved = withTimeout(5_000) { scores.observeAllScores().first { it.isNotEmpty() } }.single()
        assertEquals(13, saved.score)
        assertEquals("fine", saved.note)
        assertEquals(time.now(), saved.scoredAt)
        withTimeout(5_000) { vm.selectedScore.first { it == null } }
    }
}
