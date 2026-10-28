package app.doselog.ui.log

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.doselog.R
import app.doselog.domain.model.LogEntry
import app.doselog.ui.components.CategoryAvatar
import app.doselog.ui.components.CategoryFilterRow
import app.doselog.ui.components.EmptyState
import app.doselog.ui.components.LocalFormats
import app.doselog.ui.components.SearchField
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    onOpenManage: () -> Unit,
    onOpenExport: () -> Unit,
    viewModel: LogViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val drafts by viewModel.drafts.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val editing by viewModel.editingEntry.collectAsStateWithLifecycle()
    val formats = LocalFormats.current
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is LogEvent.Logged -> {
                    val time = formats.dateTime(event.takenAt, LocalDate.now(event.takenAt.zone))
                    val result = snackbarHostState.showSnackbar(
                        message = "Logged ${event.itemName} · ${event.label} · $time",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoLog(event.entryId)
                }
                is LogEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    // Index of the "Today" header: search, filters, then one row per card (or the empty state).
    val todayHeaderIndex = 2 + maxOf(state.items.size, 1)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Column {
                        Text("DoseLog")
                        Text(
                            formats.longDate(state.today),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.onTodayExpandedChange(true)
                        scope.launch { listState.animateScrollToItem(todayHeaderIndex) }
                    }) {
                        Icon(painterResource(R.drawable.ic_history), contentDescription = "Today's entries")
                    }
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
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "search") {
                SearchField(
                    query = query,
                    onQueryChange = viewModel::onQueryChange,
                    placeholder = "Search items",
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item(key = "filters") {
                CategoryFilterRow(selected = state.category, onSelect = viewModel::onCategoryChange)
            }

            when {
                state.isLoading -> item(key = "loading") { Box(Modifier.fillMaxWidth()) }
                !state.hasAnyItems -> item(key = "empty") {
                    EmptyState(
                        icon = R.drawable.ic_medication,
                        title = "Nothing to log yet",
                        message = "Add your medications, foods and supplements in Manage. They'll appear here as cards you can log with one tap.",
                        action = { Button(onClick = onOpenManage) { Text("Go to Manage") } },
                    )
                }
                state.items.isEmpty() -> item(key = "no-match") {
                    EmptyState(
                        icon = R.drawable.ic_search,
                        title = "No matching items",
                        message = "Try a different search or category.",
                    )
                }
                else -> items(state.items, key = { it.id }) { item ->
                    LogItemCard(
                        item = item,
                        draft = drafts[item.id] ?: CardDraft(),
                        isSaving = item.id in state.savingItemIds,
                        zone = viewModel.deviceZone(),
                        onSelectOption = { viewModel.selectOption(item.id, it) },
                        onToggleCustom = { viewModel.toggleCustom(item) },
                        onCustomAmountChange = { viewModel.setCustomAmount(item.id, it) },
                        onCustomUnitChange = { viewModel.setCustomUnit(item.id, it) },
                        onDescriptionChange = { viewModel.setDescription(item.id, it) },
                        onTakenAtChange = { viewModel.setTakenAt(item.id, it) },
                        onSubmit = { viewModel.submit(item) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }

            item(key = "today-header") {
                TodayHeader(
                    count = state.todayEntries.size,
                    expanded = state.todayExpanded,
                    onToggle = { viewModel.onTodayExpandedChange(!state.todayExpanded) },
                )
            }
            if (state.todayExpanded) {
                if (state.todayEntries.isEmpty()) {
                    item(key = "today-empty") {
                        Text(
                            "Nothing logged yet today.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                    }
                } else {
                    items(state.todayEntries, key = { "entry-${it.id}" }) { entry ->
                        TodayEntryRow(entry = entry, onClick = { viewModel.openEntry(entry.id) })
                    }
                }
            }
        }
    }

    editing?.let { (entry, item) ->
        EntryEditSheet(
            entry = entry,
            item = item,
            zone = viewModel.deviceZone(),
            onDismiss = { viewModel.openEntry(null) },
            onSave = viewModel::saveEntry,
            onDelete = { viewModel.deleteEntry(entry.id) },
        )
    }
}

@Composable
private fun TodayHeader(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {
                heading()
                stateDescription = if (expanded) "Expanded" else "Collapsed"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Today", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
            Text(
                "$count",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Icon(
            painterResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
            contentDescription = null,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun TodayEntryRow(entry: LogEntry, onClick: () -> Unit) {
    val formats = LocalFormats.current
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CategoryAvatar(entry.category)
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(entry.itemName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(entry.label, entry.description).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(formats.time(entry.localDateTime), style = MaterialTheme.typography.labelLarge)
        }
    }
}
