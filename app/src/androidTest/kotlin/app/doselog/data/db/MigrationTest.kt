package app.doselog.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards schema changes. Each future migration gets a test that creates the old version from its
 * exported schema, inserts representative rows, migrates and checks the data survived.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun version1SchemaMatchesTheEntities() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO items (id, name, category, defaultUnit, isArchived, sortOrder, createdAt) " +
                    "VALUES (1, 'Ibuprofen', 'MEDICATION', 'mg', 0, 0, 0)",
            )
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB, 1, true, *Migrations.ALL).close()
    }

    @Test
    fun migratingFromTheFirstVersionToTheLatestKeepsData() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO items (id, name, category, defaultUnit, isArchived, sortOrder, createdAt) " +
                    "VALUES (1, 'Ibuprofen', 'MEDICATION', 'mg', 0, 0, 0)",
            )
            close()
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .addMigrations(*Migrations.ALL)
            .build()
        try {
            db.openHelper.writableDatabase.query("SELECT name FROM items").use { cursor ->
                cursor.moveToFirst()
                assertEquals("Ibuprofen", cursor.getString(0))
            }
        } finally {
            db.close()
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
