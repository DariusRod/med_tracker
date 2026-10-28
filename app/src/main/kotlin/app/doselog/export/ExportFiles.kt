package app.doselog.export

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** The things that can be exported, with their file name pattern and MIME type. */
enum class ExportKind(private val prefix: String, val extension: String, val mimeType: String) {
    // octet-stream stops document providers from swapping ".db" for another extension.
    DATABASE("doselog", "db", "application/octet-stream"),
    CSV_ZIP("doselog", "zip", "application/zip"),
    ENTRIES_CSV("doselog_entries", "csv", "text/csv"),
    SCORES_CSV("doselog_scores", "csv", "text/csv"),
    JSON_BACKUP("doselog_backup", "json", "application/json"),
    ;

    /** e.g. doselog_2026-10-28_0815.zip */
    fun fileName(now: ZonedDateTime): String = "${prefix}_${now.format(STAMP)}.$extension"

    private companion object {
        val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd_HHmm")
    }
}

object ZipEntries {
    const val ITEMS = "items.csv"
    const val OPTIONS = "item_options.csv"
    const val ENTRIES = "log_entries.csv"
    const val SCORES = "daily_scores.csv"
    const val FLAT = "entries_flat.csv"
}
