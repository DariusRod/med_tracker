package app.doselog.ui.score

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.doselog.R
import app.doselog.domain.ScoreSummary
import app.doselog.domain.model.DailyScore
import app.doselog.ui.components.ConfirmDialog
import app.doselog.ui.components.DateTimeField
import app.doselog.ui.components.EmptyState
import app.doselog.ui.components.LocalFormats
import app.doselog.ui.components.ScoreBadge
import app.doselog.ui.components.ScoreDot
import app.doselog.ui.components.SectionHeader
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoreScreen(
    onOpenExport: () -> Unit,
    viewModel: ScoreViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selected by viewModel.selectedScore.collectAsStateWithLifecycle()
    val takenAtMillis by viewModel.takenAtMillis.collectAsStateWithLifecycle()
    val note by viewModel.note.collectAsStateWithLifecycle()
    val formats = LocalFormats.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ScoreEvent.Saved -> {
                    val time = formats.dateTime(event.scoredAt, LocalDate.now(event.scoredAt.zone))
                    val result = snackbarHostState.showSnackbar(
                        message = "Saved score ${event.score} · $time",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undo(event.id)
                }
                is ScoreEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Score") },
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(painterResource(R.drawable.ic_more_vert), contentDescription = "More options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Export & backup") },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_backup), contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onOpenExport()
                            },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "form") {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("How do you feel?", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "1 is the worst, 20 the best.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ScoreGrid(selected = selected, onSelect = viewModel::onScoreSelected)
                        DateTimeField(
                            value = takenAtMillis?.let(Instant::ofEpochMilli),
                            onValueChange = viewModel::onTimeChange,
                            zone = viewModel.deviceZone(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = note,
                            onValueChange = viewModel::onNoteChange,
                            label = { Text("Note (optional)") },
                            maxLines = 4,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = viewModel::submit,
                            enabled = selected != null && !state.isSaving,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp),
                        ) {
                            if (state.isSaving) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text(selected?.let { "Submit score $it" } ?: "Pick a score")
                            }
                        }
                    }
                }
            }

            item(key = "summary") { SummaryCard(state.summary) }

            item(key = "history-header") { SectionHeader("History", Modifier.padding(top = 8.dp)) }
            if (!state.isLoading && state.history.isEmpty()) {
                item(key = "history-empty") {
                    EmptyState(
                        icon = R.drawable.ic_mood,
                        title = "No scores yet",
                        message = "Pick a score above to start tracking how you feel.",
                    )
                }
            }
            items(state.history, key = { it.id }) { score ->
                HistoryRow(score = score, today = state.today, onClick = { viewModel.openScore(score.id) })
            }
        }
    }

    state.editing?.let { score ->
        ScoreEditSheet(
            score = score,
            zone = viewModel.deviceZone(),
            onDismiss = { viewModel.openScore(null) },
            onSave = { value, at, zone, newNote -> viewModel.updateScore(score.id, value, at, zone, newNote) },
            onDelete = { viewModel.deleteScore(score.id) },
        )
    }
}

@Composable
private fun SummaryCard(summary: ScoreSummary) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat("Today", summary.today?.score?.toString(), summary.today?.score)
                Stat("7-day avg", summary.average7Days?.let(::formatAverage), summary.average7Days?.let { Math.round(it).toInt() })
                Stat("30-day avg", summary.average30Days?.let(::formatAverage), summary.average30Days?.let { Math.round(it).toInt() })
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Day score = the latest score of each day. Reports and averages use it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String?, colorScore: Int?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "$label: ${value ?: "none"}"
        },
    ) {
        if (value != null && colorScore != null) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    ScoreDot(colorScore)
                    Spacer(Modifier.size(8.dp))
                    Text(value, style = MaterialTheme.typography.titleLarge)
                }
            }
        } else {
            Text("—", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 6.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

private fun formatAverage(value: Double): String = String.format(Locale.getDefault(), "%.1f", value)

@Composable
private fun HistoryRow(score: DailyScore, today: LocalDate, onClick: () -> Unit) {
    val formats = LocalFormats.current
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ScoreBadge(score.score)
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
            ) {
                Text(
                    "${formats.dayHeader(score.localDate, today)} · ${formats.time(score.localDateTime)}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (!score.note.isNullOrBlank()) {
                    Text(
                        score.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScoreEditSheet(
    score: DailyScore,
    zone: ZoneId,
    onDismiss: () -> Unit,
    onSave: (Int, Instant, ZoneId, String) -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var value by rememberSaveable(score.id) { mutableIntStateOf(score.score) }
    var atMillis by rememberSaveable(score.id) { mutableStateOf<Long?>(score.scoredAt.toEpochMilli()) }
    var note by rememberSaveable(score.id) { mutableStateOf(score.note.orEmpty()) }
    var confirmDelete by rememberSaveable(score.id) { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Edit score", style = MaterialTheme.typography.titleLarge)
            ScoreGrid(selected = value, onSelect = { value = it })
            DateTimeField(
                value = atMillis?.let(Instant::ofEpochMilli),
                onValueChange = { atMillis = it?.toEpochMilli() },
                zone = zone,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { confirmDelete = true }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = {
                    val at = atMillis?.let(Instant::ofEpochMilli) ?: Instant.now()
                    onSave(value, at, if (at == score.scoredAt) score.zone else zone, note)
                }) { Text("Save") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete score?",
            text = "The score ${score.score} will be removed permanently.",
            confirmLabel = "Delete",
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
