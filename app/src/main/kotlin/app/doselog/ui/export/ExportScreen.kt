package app.doselog.ui.export

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.doselog.R
import app.doselog.export.ExportKind
import app.doselog.export.SnapshotCounts
import app.doselog.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    onBack: () -> Unit,
    viewModel: ExportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val busy = state.busyMessage != null

    // One launcher per MIME type; the picker lets the user choose Downloads, Drive, etc.
    var pendingCsv by rememberSaveable { mutableStateOf(ExportKind.ENTRIES_CSV) }
    val dbLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportKind.DATABASE.mimeType)) { uri ->
        uri?.let { viewModel.export(ExportKind.DATABASE, it) }
    }
    val zipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportKind.CSV_ZIP.mimeType)) { uri ->
        uri?.let { viewModel.export(ExportKind.CSV_ZIP, it) }
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportKind.ENTRIES_CSV.mimeType)) { uri ->
        uri?.let { viewModel.export(pendingCsv, it) }
    }
    val jsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportKind.JSON_BACKUP.mimeType)) { uri ->
        uri?.let { viewModel.export(ExportKind.JSON_BACKUP, it) }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::readRestoreFile)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Export & backup") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    state.busyMessage.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            state.result?.let { result -> ResultCard(result, onDismiss = viewModel::dismissResult) }

            Text(
                "Files are saved where you choose (e.g. Downloads or Google Drive). Nothing is uploaded by DoseLog itself.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )

            SectionHeader("Export")
            ActionGroup {
                Action(R.drawable.ic_database, "Database (.db)", "A complete SQLite copy. Opens in DB Browser for SQLite on Windows.", !busy) {
                    dbLauncher.launch(viewModel.suggestedName(ExportKind.DATABASE))
                }
                Action(R.drawable.ic_folder_zip, "Spreadsheets (.zip of CSV)", "Every table plus entries_flat.csv. Opens in Excel.", !busy) {
                    zipLauncher.launch(viewModel.suggestedName(ExportKind.CSV_ZIP))
                }
                Action(R.drawable.ic_csv, "Log entries (.csv)", "All entries in one sheet, with date, time and day score.", !busy) {
                    pendingCsv = ExportKind.ENTRIES_CSV
                    csvLauncher.launch(viewModel.suggestedName(ExportKind.ENTRIES_CSV))
                }
                Action(R.drawable.ic_csv, "Scores (.csv)", "All scores in one sheet. Filtered exports are in Reports.", !busy) {
                    pendingCsv = ExportKind.SCORES_CSV
                    csvLauncher.launch(viewModel.suggestedName(ExportKind.SCORES_CSV))
                }
            }

            SectionHeader("Backup", Modifier.padding(top = 16.dp))
            ActionGroup {
                Action(R.drawable.ic_data_object, "Full backup (.json)", "Everything needed to restore DoseLog on this or another phone.", !busy) {
                    jsonLauncher.launch(viewModel.suggestedName(ExportKind.JSON_BACKUP))
                }
                Action(R.drawable.ic_restore, "Restore from backup", "From a .json backup or an exported .db file. Replaces all current data.", !busy) {
                    restoreLauncher.launch(arrayOf("*/*"))
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(24.dp))
        }
    }

    state.restorePreview?.let { preview ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRestore,
            icon = { Icon(painterResource(R.drawable.ic_warning), contentDescription = null) },
            title = { Text("Replace all data?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${preview.fileName} · ${preview.source.label}" + (preview.exportedAt?.let { "\nExported $it" } ?: ""))
                    CountsTable(preview.incoming, preview.current)
                    Text(
                        "All current data will be replaced in one step. If anything fails, nothing changes. " +
                            "This can't be undone, so export a backup first if you might need your current data.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmRestore) {
                    Text("Replace all data", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelRestore) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CountsTable(incoming: SnapshotCounts, current: SnapshotCounts) {
    val rows = listOf(
        Triple("Items", incoming.items, current.items),
        Triple("Presets", incoming.presets, current.presets),
        Triple("Entries", incoming.entries, current.entries),
        Triple("Scores", incoming.scores, current.scores),
    )
    Column {
        Row {
            Text("", Modifier.weight(1f))
            Text("In file", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text("Now", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
        }
        rows.forEach { (label, new, old) ->
            Row {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("$new", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("$old", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ResultCard(result: ResultMessage, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (result.success) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(if (result.success) R.drawable.ic_check_circle else R.drawable.ic_warning),
                contentDescription = null,
            )
            Text(
                result.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            )
            IconButton(onClick = onDismiss) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = "Dismiss")
            }
        }
    }
}

@Composable
private fun ActionGroup(content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column { content() }
    }
}

@Composable
private fun Action(@DrawableRes icon: Int, title: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            leadingContent = { Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            headlineContent = { Text(title) },
            supportingContent = { Text(description) },
        )
    }
}
