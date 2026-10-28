package app.doselog.sample

import javax.inject.Inject

/** Release builds ship without sample data; see the debug source set. */
class SampleData @Inject constructor() {
    val isAvailable: Boolean = false

    suspend fun load(): String = "Sample data is only available in debug builds."
}
