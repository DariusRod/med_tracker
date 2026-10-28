package app.doselog.domain.model

enum class Category(val displayName: String) {
    MEDICATION("Medication"),
    FOOD("Food"),
    SUPPLEMENT("Supplement"),
    OTHER("Other"),
    ;

    companion object {
        /** Parses a stored value; unknown values (e.g. from a newer backup) fall back to [OTHER]. */
        fun fromStored(value: String): Category = entries.firstOrNull { it.name == value } ?: OTHER
    }
}
