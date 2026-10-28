package app.doselog.data.db

import androidx.room.migration.Migration

/**
 * Every schema change must ship a migration here; the app never uses destructive migration.
 *
 * To change the schema:
 * 1. Edit the entities and bump [AppDatabase.VERSION].
 * 2. Build once so Room exports `app/schemas/.../<new version>.json` (commit it).
 * 3. Add `val MIGRATION_1_2 = object : Migration(1, 2) { ... }` and list it in [ALL].
 * 4. Add a case to `MigrationTest` that creates the old version, inserts rows and migrates.
 */
object Migrations {
    val ALL: Array<Migration> = emptyArray()
}
