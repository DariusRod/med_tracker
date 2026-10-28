package app.doselog.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.doselog.MainActivity
import app.doselog.data.db.AppDatabase
import app.doselog.data.repository.ItemRepository
import app.doselog.domain.model.Category
import app.doselog.domain.model.ItemDraft
import app.doselog.domain.model.OptionDraft
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class AppFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var db: AppDatabase

    @Inject
    lateinit var items: ItemRepository

    @Before
    fun setUp() {
        hiltRule.inject()
        runBlocking {
            items.saveItem(ItemDraft(null, "Ibuprofen", Category.MEDICATION, "mg", listOf(OptionDraft(null, 200.0, "mg", ""))))
        }
    }

    private fun entryCount() = runBlocking { db.logEntryDao().count() }

    private fun scoreCount() = runBlocking { db.dailyScoreDao().count() }

    @Test
    fun logAnEntryEndToEndThenUndo() {
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("200 mg")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Submit Ibuprofen").assertIsNotEnabled()

        compose.onNodeWithText("200 mg").performClick()
        compose.onNodeWithContentDescription("Submit Ibuprofen").assertIsEnabled().performClick()

        compose.waitUntil(10_000) { entryCount() == 1 }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Logged Ibuprofen", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Logged Ibuprofen", substring = true).assertIsDisplayed()

        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(10_000) { entryCount() == 0 }
    }

    @Test
    fun submitAScoreThenUndo() {
        compose.onNodeWithText("Score").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("Score 15")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Pick a score").assertIsNotEnabled()

        compose.onNodeWithContentDescription("Score 15").performClick()
        compose.onNodeWithText("Submit score 15").performClick()

        compose.waitUntil(10_000) { scoreCount() == 1 }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Saved score 15", substring = true)).fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(10_000) { scoreCount() == 0 }
    }

    @Test
    fun emptyStateLeadsToManage() {
        runBlocking { db.itemDao().deleteAll() }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Go to Manage")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Go to Manage").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Add your first item")).fetchSemanticsNodes().isNotEmpty() }
    }
}
