package app.doselog.ui.reports

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.doselog.R
import app.doselog.domain.model.Category
import app.doselog.domain.model.DateRange
import app.doselog.domain.model.Item
import app.doselog.domain.reports.RangePreset
import app.doselog.domain.reports.ReportFilters
import app.doselog.export.ExportKind
import app.doselog.ui.components.CategoryAvatar
import app.doselog.ui.components.LocalFormats
import app.doselog.ui.components.SearchField
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    onOpenExport: () -> Unit,
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val view by viewModel.view.collectAsStateWithLifecycle()
    val allItems by viewModel.allItems.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    val entriesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportKind.ENTRIES_CSV.mimeType)) { uri ->
        uri?.let(viewModel::exportFilteredEntries)
    }
    val scoresLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportKind.SCORES_CSV.mimeType)) { uri ->
        uri?.let(viewModel::exportFilteredScores)
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reports") },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(painterResource(R.drawable.ic_more_vert), contentDescription = "More options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Export shown entries (.csv)") },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_csv), contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                entriesLauncher.launch(viewModel.suggestedName(ExportKind.ENTRIES_CSV))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Export scores in range (.csv)") },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_csv), contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                scoresLauncher.launch(viewModel.suggestedName(ExportKind.SCORES_CSV))
                            },
                        )
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
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            FilterBar(
                filters = state.filters,
                range = state.data.range,
                today = state.today,
                items = allItems,
                onPreset = viewModel::setPreset,
                onCustomRange = viewModel::setCustomRange,
                onToggleCategory = viewModel::toggleCategory,
                onSetItems = viewModel::setItems,
                onClear = viewModel::clearEntryFilters,
            )
            PrimaryScrollableTabRow(selectedTabIndex = view.ordinal, edgePadding = 16.dp) {
                ReportView.entries.forEach { tab ->
                    Tab(
                        selected = tab == view,
                        onClick = { viewModel.selectView(tab) },
                        text = { Text(tab.title) },
                        icon = { Icon(painterResource(tab.iconRes()), contentDescription = null) },
                    )
                }
            }
            Box(Modifier.weight(1f)) {
                if (!state.isLoading) {
                    when (view) {
                        ReportView.TIMELINE -> TimelineView(viewModel, state)
                        ReportView.TABLE -> TableView(viewModel, state)
                        ReportView.SUMMARY -> SummaryView(viewModel, state)
                        ReportView.CHARTS -> ChartsView(viewModel, state)
                        ReportView.CALENDAR -> CalendarView(viewModel, state)
                    }
                }
            }
        }
    }
}

@DrawableRes
private fun ReportView.iconRes(): Int = when (this) {
    ReportView.TIMELINE -> R.drawable.ic_view_timeline
    ReportView.TABLE -> R.drawable.ic_table
    ReportView.SUMMARY -> R.drawable.ic_summarize
    ReportView.CHARTS -> R.drawable.ic_bar_chart
    ReportView.CALENDAR -> R.drawable.ic_calendar_month
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBar(
    filters: ReportFilters,
    range: DateRange,
    today: LocalDate,
    items: List<Item>,
    onPreset: (RangePreset) -> Unit,
    onCustomRange: (LocalDate, LocalDate) -> Unit,
    onToggleCategory: (Category) -> Unit,
    onSetItems: (Set<Long>) -> Unit,
    onClear: () -> Unit,
) {
    val formats = LocalFormats.current
    var rangeMenu by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }
    var itemSheet by rememberSaveable { mutableStateOf(false) }
    var customPicker by rememberSaveable { mutableStateOf(false) }
    val rangeText = "${formats.shortDate(range.start, today)} – ${formats.shortDate(range.endInclusive, today)}"

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box {
                FilterChip(
                    selected = true,
                    onClick = { rangeMenu = true },
                    label = { Text(if (filters.preset == RangePreset.CUSTOM) rangeText else filters.preset.label) },
                    leadingIcon = { ChipIcon(R.drawable.ic_date_range) },
                    trailingIcon = { ChipIcon(R.drawable.ic_expand_more) },
                )
                DropdownMenu(expanded = rangeMenu, onDismissRequest = { rangeMenu = false }) {
                    RangePreset.entries.forEach { preset ->
                        DropdownMenuItem(
                            text = { Text(if (preset == RangePreset.CUSTOM) "Custom range…" else preset.label) },
                            trailingIcon = if (preset == filters.preset) {
                                { Icon(painterResource(R.drawable.ic_check), contentDescription = "Selected") }
                            } else {
                                null
                            },
                            onClick = {
                                rangeMenu = false
                                if (preset == RangePreset.CUSTOM) customPicker = true else onPreset(preset)
                            },
                        )
                    }
                }
            }
            Box {
                FilterChip(
                    selected = filters.categories.isNotEmpty(),
                    onClick = { categoryMenu = true },
                    label = {
                        Text(
                            when (filters.categories.size) {
                                0 -> "All categories"
                                1 -> filters.categories.first().displayName
                                else -> "${filters.categories.size} categories"
                            },
                        )
                    },
                    leadingIcon = { ChipIcon(R.drawable.ic_filter_list) },
                    trailingIcon = { ChipIcon(R.drawable.ic_expand_more) },
                )
                DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                    Category.entries.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.displayName) },
                            leadingIcon = { Checkbox(checked = category in filters.categories, onCheckedChange = null) },
                            onClick = { onToggleCategory(category) },
                        )
                    }
                }
            }
            FilterChip(
                selected = filters.itemIds.isNotEmpty(),
                onClick = { itemSheet = true },
                label = {
                    Text(
                        when (filters.itemIds.size) {
                            0 -> "All items"
                            1 -> items.firstOrNull { it.id in filters.itemIds }?.name ?: "1 item"
                            else -> "${filters.itemIds.size} items"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingIcon = { ChipIcon(R.drawable.ic_expand_more) },
            )
            if (filters.hasEntryFilters) {
                AssistChip(onClick = onClear, label = { Text("Clear filters") }, leadingIcon = { ChipIcon(R.drawable.ic_close) })
            }
        }
        Text(
            "$rangeText · ${range.dayCount} ${if (range.dayCount == 1L) "day" else "days"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }

    if (customPicker) {
        CustomRangeDialog(
            initial = range,
            today = today,
            onDismiss = { customPicker = false },
            onConfirm = { start, end ->
                customPicker = false
                onCustomRange(start, end)
            },
        )
    }
    if (itemSheet) {
        ItemFilterSheet(
            items = items,
            selected = filters.itemIds,
            onDismiss = { itemSheet = false },
            onApply = {
                itemSheet = false
                onSetItems(it)
            },
        )
    }
}

@Composable
private fun ChipIcon(@DrawableRes icon: Int) {
    Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomRangeDialog(
    initial: DateRange,
    today: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit,
) {
    fun LocalDate.utcMillis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    fun Long.toDate() = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initial.start.utcMillis(),
        initialSelectedEndDateMillis = initial.endInclusive.utcMillis(),
        yearRange = 2000..today.year,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null,
                onClick = {
                    val start = state.selectedStartDateMillis?.toDate() ?: return@TextButton
                    onConfirm(start, state.selectedEndDateMillis?.toDate() ?: start)
                },
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DateRangePicker(state = state, modifier = Modifier.weight(1f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemFilterSheet(
    items: List<Item>,
    selected: Set<Long>,
    onDismiss: () -> Unit,
    onApply: (Set<Long>) -> Unit,
) {
    var chosen by rememberSaveable { mutableStateOf(selected.toList()) }
    var query by rememberSaveable { mutableStateOf("") }
    val visible = items.filter { it.name.contains(query.trim(), ignoreCase = true) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Filter items", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { chosen = emptyList() }) { Text("Select none") }
            }
            SearchField(query = query, onQueryChange = { query = it }, placeholder = "Search items")
            Text(
                if (chosen.isEmpty()) "No selection shows all items." else "${chosen.size} selected",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        LazyColumn(Modifier.weight(1f, fill = false)) {
            items(visible, key = { it.id }) { item ->
                val checked = item.id in chosen
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Checkbox) {
                            chosen = if (checked) chosen - item.id else chosen + item.id
                        }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = checked, onCheckedChange = null)
                    Spacer(Modifier.size(12.dp))
                    CategoryAvatar(item.category, size = 32.dp)
                    Spacer(Modifier.size(12.dp))
                    Text(
                        item.name + if (item.isArchived) " (archived)" else "",
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            TextButton(onClick = { onApply(chosen.toSet()) }) { Text("Apply") }
        }
        Spacer(Modifier.height(16.dp))
    }
}
