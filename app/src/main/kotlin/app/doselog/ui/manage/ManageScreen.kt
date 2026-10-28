package app.doselog.ui.manage

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.doselog.R
import app.doselog.domain.model.Item
import app.doselog.ui.components.CategoryAvatar
import app.doselog.ui.components.CategoryFilterRow
import app.doselog.ui.components.ConfirmDialog
import app.doselog.ui.components.EmptyState
import app.doselog.ui.components.SearchField
import app.doselog.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageScreen(
    onAddItem: () -> Unit,
    onEditItem: (Long) -> Unit,
    onOpenExport: () -> Unit,
    viewModel: ManageViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var menuOpen by remember { mutableStateOf(false) }
    var confirmSampleData by rememberSaveable { mutableStateOf(false) }
    val reorderMode = state.filters.reorderMode

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(if (reorderMode) "Reorder items" else "Manage") },
                scrollBehavior = scrollBehavior,
                actions = {
                    if (reorderMode) {
                        TextButton(onClick = { viewModel.onReorderModeChange(false) }) { Text("Done") }
                    } else {
                        IconButton(onClick = { viewModel.onReorderModeChange(true) }, enabled = state.hasAnyItems) {
                            Icon(painterResource(R.drawable.ic_swap_vert), contentDescription = "Reorder items")
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
                            if (state.sampleDataAvailable) {
                                DropdownMenuItem(
                                    text = { Text("Load sample data") },
                                    leadingIcon = { Icon(painterResource(R.drawable.ic_science), contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        confirmSampleData = true
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!reorderMode && state.hasAnyItems) {
                ExtendedFloatingActionButton(
                    onClick = onAddItem,
                    icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                    text = { Text("Add item") },
                    // The extended FAB doesn't expose its text to accessibility services.
                    modifier = Modifier.semantics { contentDescription = "Add item" },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            if (reorderMode) {
                item {
                    Text(
                        "Use the arrows to set the order items appear in on the Log screen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                itemsIndexed(state.items, key = { _, item -> item.id }) { index, item ->
                    ReorderRow(
                        item = item,
                        canMoveUp = index > 0,
                        canMoveDown = index < state.items.lastIndex,
                        onMove = { up -> viewModel.moveItem(item.id, up) },
                    )
                }
                return@LazyColumn
            }

            item {
                SearchField(
                    query = query,
                    onQueryChange = viewModel::onQueryChange,
                    placeholder = "Search items or presets",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item {
                CategoryFilterRow(
                    selected = state.filters.category,
                    onSelect = viewModel::onCategoryChange,
                    trailing = {
                        FilterChip(
                            selected = state.filters.showArchived,
                            onClick = { viewModel.onShowArchivedChange(!state.filters.showArchived) },
                            label = { Text("Show archived (${state.archivedCount})") },
                            leadingIcon = {
                                Icon(painterResource(R.drawable.ic_archive), contentDescription = null)
                            },
                        )
                    },
                )
            }

            when {
                state.isLoading -> Unit
                !state.hasAnyItems -> item {
                    EmptyState(
                        icon = R.drawable.ic_list,
                        title = "No items yet",
                        message = "Add the medications, foods and supplements you want to track, " +
                            "with presets for the doses you usually take.",
                        action = {
                            Button(onClick = onAddItem) {
                                Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Add your first item")
                            }
                        },
                    )
                }
                state.items.isEmpty() -> item {
                    EmptyState(
                        icon = R.drawable.ic_search,
                        title = "No matching items",
                        message = "Try a different search or category, or show archived items.",
                    )
                }
                else -> {
                    val groups = state.items.groupBy { it.category }
                    groups.forEach { (category, items) ->
                        item(key = "header-$category") {
                            SectionHeader("${category.displayName} · ${items.size}", Modifier.padding(top = 8.dp))
                        }
                        items(items, key = { it.id }) { item ->
                            ItemRow(item = item, onClick = { onEditItem(item.id) })
                        }
                    }
                }
            }

            item(key = "settings") {
                SettingsSection(
                    useDynamicColor = state.useDynamicColor,
                    onUseDynamicColorChange = viewModel::setUseDynamicColor,
                    onOpenExport = onOpenExport,
                )
            }
        }
    }

    if (confirmSampleData) {
        ConfirmDialog(
            title = "Load sample data?",
            text = "Adds example items with 60 days of entries and scores so you can try every screen. " +
                "Only works while the app has no items. Delete it later by restoring a backup.",
            confirmLabel = "Load",
            destructive = false,
            onConfirm = {
                confirmSampleData = false
                viewModel.loadSampleData()
            },
            onDismiss = { confirmSampleData = false },
        )
    }
}

@Composable
private fun ItemRow(item: Item, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            leadingContent = { CategoryAvatar(item.category) },
            headlineContent = {
                Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = {
                Text(
                    text = if (item.options.isEmpty()) "No presets" else item.options.joinToString(" · ") { it.label },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            trailingContent = if (item.isArchived) {
                {
                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                        Text(
                            "Archived",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            } else {
                { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) }
            },
        )
    }
}

@Composable
private fun ReorderRow(item: Item, canMoveUp: Boolean, canMoveDown: Boolean, onMove: (up: Boolean) -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryAvatar(item.category)
            Text(
                item.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
            )
            IconButton(onClick = { onMove(true) }, enabled = canMoveUp) {
                Icon(painterResource(R.drawable.ic_arrow_upward), contentDescription = "Move ${item.name} up")
            }
            IconButton(onClick = { onMove(false) }, enabled = canMoveDown) {
                Icon(painterResource(R.drawable.ic_arrow_downward), contentDescription = "Move ${item.name} down")
            }
        }
    }
}

@Composable
private fun SettingsSection(
    useDynamicColor: Boolean,
    onUseDynamicColorChange: (Boolean) -> Unit,
    onOpenExport: () -> Unit,
) {
    val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    Column(Modifier.padding(top = 16.dp)) {
        SectionHeader("App")
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Column {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.clickable(onClick = onOpenExport),
                    leadingContent = { Icon(painterResource(R.drawable.ic_backup), contentDescription = null) },
                    headlineContent = { Text("Export & backup") },
                    supportingContent = { Text("Save your data as a database, CSV or backup file, or restore one") },
                    trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) },
                )
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.clickable(
                        enabled = dynamicSupported,
                        role = Role.Switch,
                        onClick = { onUseDynamicColorChange(!useDynamicColor) },
                    ),
                    leadingContent = { Icon(painterResource(R.drawable.ic_palette), contentDescription = null) },
                    headlineContent = { Text("Use wallpaper colors") },
                    supportingContent = {
                        Text(if (dynamicSupported) "Match the app to your wallpaper" else "Needs Android 12 or newer")
                    },
                    trailingContent = {
                        Switch(
                            checked = useDynamicColor && dynamicSupported,
                            onCheckedChange = null,
                            enabled = dynamicSupported,
                        )
                    },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "All data stays on this phone. DoseLog has no internet access.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
        )
    }
}
