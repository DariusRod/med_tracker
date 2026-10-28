package app.doselog.ui.export

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.doselog.domain.DayGrouping
import app.doselog.domain.TimeSource
import app.doselog.export.BackupReadResult
import app.doselog.export.BackupSource
import app.doselog.export.DataTransfer
import app.doselog.export.DatabaseSnapshot
import app.doselog.export.ExportKind
import app.doselog.export.SnapshotCounts
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ResultMessage(val success: Boolean, val text: String)

data class RestorePreview(
    val fileName: String,
    val source: BackupSource,
    val incoming: SnapshotCounts,
    val current: SnapshotCounts,
    val exportedAt: String?,
)

data class ExportUiState(
    val busyMessage: String? = null,
    val result: ResultMessage? = null,
    val restorePreview: RestorePreview? = null,
)

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val transfer: DataTransfer,
    private val timeSource: TimeSource,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    // Held in memory only: after process death the user simply picks the file again.
    private var pendingRestore: DatabaseSnapshot? = null

    /** Default file name with a timestamp, e.g. doselog_2026-10-28_0815.zip. */
    fun suggestedName(kind: ExportKind): String = kind.fileName(timeSource.now().atZone(timeSource.zone()))

    fun export(kind: ExportKind, uri: Uri) = runTask("Exporting…") {
        transfer.writeTo(uri) { out ->
            when (kind) {
                ExportKind.DATABASE -> transfer.writeDatabase(out)
                ExportKind.CSV_ZIP -> transfer.writeCsvZip(out)
                ExportKind.JSON_BACKUP -> transfer.writeJsonBackup(out)
                ExportKind.ENTRIES_CSV -> {
                    val (entries, scores) = transfer.allEntriesAndScores()
                    transfer.writeEntriesCsv(out, entries, DayGrouping.dayScores(scores))
                }
                ExportKind.SCORES_CSV -> transfer.writeScoresCsv(out, transfer.allEntriesAndScores().second)
            }
        }
        ResultMessage(true, "Saved ${transfer.displayName(uri) ?: "file"}")
    }

    fun readRestoreFile(uri: Uri) = runTask("Checking the file…") {
        when (val result = transfer.readFrom(uri) { transfer.readBackup(it) }) {
            is BackupReadResult.Rejected -> ResultMessage(false, "Can't restore this file.\n${result.reason}")
            is BackupReadResult.Ready -> {
                pendingRestore = result.snapshot
                val preview = RestorePreview(
                    fileName = transfer.displayName(uri) ?: "Selected file",
                    source = result.source,
                    incoming = result.snapshot.counts,
                    current = transfer.snapshot().counts,
                    exportedAt = result.exportedAt,
                )
                _uiState.update { it.copy(restorePreview = preview) }
                null
            }
        }
    }

    fun confirmRestore() {
        val snapshot = pendingRestore ?: return
        _uiState.update { it.copy(restorePreview = null) }
        runTask("Restoring…") {
            transfer.restore(snapshot)
            pendingRestore = null
            val c = snapshot.counts
            ResultMessage(true, "Restored ${c.items} items, ${c.presets} presets, ${c.entries} entries and ${c.scores} scores")
        }
    }

    fun cancelRestore() {
        pendingRestore = null
        _uiState.update { it.copy(restorePreview = null) }
    }

    fun dismissResult() {
        _uiState.update { it.copy(result = null) }
    }

    private fun runTask(busyMessage: String, block: suspend () -> ResultMessage?) {
        if (_uiState.value.busyMessage != null) return
        _uiState.update { it.copy(busyMessage = busyMessage, result = null) }
        viewModelScope.launch {
            try {
                val result = block()
                if (result != null) _uiState.update { it.copy(result = result) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(result = ResultMessage(false, "Failed: ${e.message ?: e.javaClass.simpleName}")) }
            } finally {
                _uiState.update { it.copy(busyMessage = null) }
            }
        }
    }
}
