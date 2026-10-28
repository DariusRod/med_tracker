# DoseLog

A private, offline medication and food intake tracker for Android, built for a Samsung Galaxy S24 Ultra
(works on any phone with Android 8.0 or newer).

- **Log**: one card per item with preset doses as chips, a one-off *Custom* amount, an optional
  description and a date-time field that defaults to *now* (the moment you press Submit). Undo from the
  snackbar; edit or delete today's entries.
- **Score**: rate how you feel from 1 to 20, with history and today / 7-day / 30-day averages. The latest
  score of each day is that day's *day score*.
- **Reports**: timeline, sortable table, per-item summary (totals per unit, never mixing units), charts
  (daily totals, day score, score vs. intake) and a calendar coloured by day score. Filter by date range,
  category and item.
- **Manage**: items with categories and presets, search, archive, reorder.
- **Export & backup**: SQLite database, CSV (zip or single files), JSON backup, and restore from a JSON
  backup or an exported `.db` file.

**Privacy:** the app has no internet permission, no analytics and no network libraries. Android cloud backup
is switched off for its data (device-to-device transfer, e.g. Smart Switch, still works). Files only leave the
phone when you export them to a location you choose.

---

## 1. Build

### Requirements

| Tool | Version |
| --- | --- |
| Android Studio | **Quail 4 (2026.1.4) or Rabbit (2026.2.1) or newer**. Older versions can't open AGP 9.4 projects. Update via *Help → Check for Updates*. |
| JDK | 17 or newer. Android Studio's bundled JDK (`C:\Program Files\Android\Android Studio\jbr`) is fine. |
| Android SDK | Platform 37 (Android 17) and Build-Tools 36+. Android Studio installs them on first sync. |

Versions used: AGP 9.4.1, Gradle 9.6.1, Kotlin 2.4.21, KSP 2.3.12, Compose BOM 2026.09.00 (Material 3 1.4),
Room 2.8.5, Hilt 2.60.1, Navigation 2.10.2, Vico 3.3.1. All are managed in `gradle/libs.versions.toml`.

### In Android Studio

1. *File → Open…* and pick the project folder (`C:\dev\med_tracker`).
2. Wait for Gradle sync, choose the **app** run configuration and press **Run**.

### From the command line (Windows)

```bat
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
gradlew.bat assembleDebug
```

The APK is written to `app\build\outputs\apk\debug\app-debug.apk`.

### Tests and lint

```bat
gradlew.bat testDebugUnitTest            :: fast JVM tests (formatting, parsing, DST, CSV, reports, backup)
gradlew.bat connectedDebugAndroidTest    :: needs a connected phone or a running emulator
gradlew.bat lintDebug
```

The instrumented tests cover every DAO query, the repositories, schema migrations, `.db`/CSV/JSON export,
restore (including roll-back on failure) and Compose UI flows (log an entry, submit a score, undo).

---

## 2. Install on your phone (S24 Ultra)

1. **Enable Developer options**: *Settings → About phone → Software information*, then tap **Build number**
   seven times and enter your PIN.
2. **Enable USB debugging**: *Settings → Developer options → USB debugging* → on.
3. Connect the phone with a USB-C data cable. On the phone, accept **Allow USB debugging?** for this computer
   (tick *Always allow*).
4. Install:
   - **Android Studio**: select the phone in the device dropdown and press **Run**, or
   - **Command line**: `gradlew.bat installDebug`

Wireless alternative: *Developer options → Wireless debugging*, then *Pair device* from Android Studio's
device manager.

To try every screen quickly, a **debug** build has *Manage → ⋮ → Load sample data* (only while the app has
no items). Release builds don't include it.

---

## 3. Build a signed release APK

A release build is smaller and faster (R8 minified). It must be signed with your own key.

1. Create a keystore once (or use *Build → Generate Signed App Bundle / APK…* in Android Studio):

   ```bat
   "C:\Program Files\Android\Android Studio\jbr\bin\keytool" -genkeypair -v -keystore doselog-release.jks ^
     -alias doselog -keyalg RSA -keysize 4096 -validity 36500
   ```

2. Create `keystore.properties` in the project root (it is git-ignored):

   ```properties
   storeFile=doselog-release.jks
   storePassword=your-store-password
   keyAlias=doselog
   keyPassword=your-key-password
   ```

3. Build and install:

   ```bat
   gradlew.bat assembleRelease
   adb install -r app\build\outputs\apk\release\app-release.apk
   ```

**Keep the keystore and passwords safe.** Updates must be signed with the same key. Debug and release builds
have different signatures, so switching between them requires uninstalling, which **deletes the app's
data**. Export a JSON backup first and restore it afterwards.

---

## 4. Export, backup and restore

*Manage → Export & backup* (also in every screen's ⋮ menu). Each option opens the system file picker, so you
choose where the file goes (Downloads, Google Drive, …). Default names include a timestamp, e.g.
`doselog_2026-10-28_0815.zip`.

| Option | File | Use it for |
| --- | --- | --- |
| Database | `doselog_….db` | A complete SQLite copy for DB Browser for SQLite. It can also be restored. |
| Spreadsheets | `doselog_….zip` | `items.csv`, `item_options.csv`, `log_entries.csv`, `daily_scores.csv` and the human-friendly `entries_flat.csv`. |
| Log entries / Scores | `doselog_entries_….csv`, `doselog_scores_….csv` | One sheet each. From *Reports → ⋮* these exports respect the current filters. |
| Full backup | `doselog_backup_….json` | Restoring on this or another phone. |
| Restore | `.json` or `.db` | Shows what the file contains, asks for confirmation, then replaces all data in a single transaction. If anything fails, nothing changes. |

**How the `.db` export works.** The live database uses write-ahead logging (WAL). The export uses SQLite's
`VACUUM INTO` on Android 11+ (a consistent copy that includes all recent changes), or a schema-and-row copy
inside one transaction on older Android. The copy is then switched to a normal rollback journal and
integrity-checked, so it is a single self-contained file.

---

## 5. Open the exports on Windows

### `.db` in DB Browser for SQLite

1. Install DB Browser for SQLite (sqlitebrowser.org, free).
2. *Open Database Read Only…* and choose the `.db` file.
3. *Browse Data* shows each table: `items`, `item_options`, `log_entries`, `daily_scores`.
   Times are UTC milliseconds (`takenAt`, `scoredAt`) plus the time zone they were recorded in (`zoneId`).
4. Useful queries for *Execute SQL*:

   ```sql
   -- Every entry, newest first, in this PC's local time
   SELECT datetime(takenAt / 1000, 'unixepoch', 'localtime') AS taken,
          itemNameSnapshot AS item, labelSnapshot AS dose, description
   FROM log_entries ORDER BY takenAt DESC;

   -- Totals per item and unit (units are never added together)
   SELECT itemNameSnapshot AS item, unitSnapshot AS unit,
          COUNT(*) AS entries, SUM(amountSnapshot) AS total
   FROM log_entries
   GROUP BY itemId, lower(trim(unitSnapshot))
   ORDER BY entries DESC;
   ```

### CSV in Excel

1. Right-click the `.zip` → *Extract All…*.
2. Double-click a `.csv` file. The files are UTF-8 with a byte-order mark, so accents display correctly.
   If Excel ever shows everything in one column, use *Data → From Text/CSV* and pick *Comma* as the delimiter.
3. `entries_flat.csv` is the easiest to read: `date`, `time`, `item`, `category`, `amount`, `unit`, `label`,
   `description`, `day_score`, plus the full ISO time (`2026-10-28T08:15:00+11:00`) and time zone.
   The raw table files keep every column, including `*_epoch_ms` columns for exact values.

---

## 6. Project notes

```
app/src/main/kotlin/app/doselog/
├── data/db           entities, DAOs, AppDatabase, Migrations
├── data/repository   ItemRepository, LogRepository, ScoreRepository
├── data/settings     DataStore settings (wallpaper colours)
├── domain            amounts, day grouping, statistics, reports (pure Kotlin, unit-tested)
├── export            CSV, JSON backup, DB export, restore (DataTransfer)
├── ui                theme, navigation, components and one package per screen
└── di                Hilt modules
app/schemas/          exported Room schemas (commit these)
```

- **Time:** everything is stored as UTC milliseconds plus the zone ID at the time of recording. A record's
  day is its local date in its own zone, so travel and daylight-saving changes never move entries to another
  day.
- **History is immutable:** entries copy the item name, category, amount, unit and label when they are
  logged. Renaming an item or editing or deleting a preset doesn't change past entries. Items that have
  entries can only be archived, never deleted.
- **Schema changes:** never use destructive migration. Bump `AppDatabase.VERSION`, build once to export the
  new schema JSON, add a `Migration` to `Migrations.ALL` and a case to `MigrationTest`.
