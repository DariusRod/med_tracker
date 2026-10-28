package app.doselog.export

/**
 * RFC 4180 CSV for Excel on Windows: CRLF line endings, fields containing a comma, quote or line
 * break are quoted with inner quotes doubled. Call [writeBom] first so Excel reads UTF-8 (accents).
 */
class CsvWriter(private val out: Appendable) {

    fun writeBom() {
        out.append(BOM)
    }

    fun writeRow(fields: List<String?>) {
        fields.forEachIndexed { index, field ->
            if (index > 0) out.append(',')
            out.append(escape(field.orEmpty()))
        }
        out.append(EOL)
    }

    fun writeRow(vararg fields: String?) = writeRow(fields.toList())

    companion object {
        const val BOM = "\uFEFF"
        const val EOL = "\r\n"

        fun escape(field: String): String {
            val needsQuotes = field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
            return if (needsQuotes) "\"" + field.replace("\"", "\"\"") + "\"" else field
        }
    }
}
