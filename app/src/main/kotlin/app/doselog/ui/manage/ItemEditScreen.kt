package app.doselog.ui.manage

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.doselog.R
import app.doselog.domain.AmountFormat
import app.doselog.domain.AmountInput
import app.doselog.domain.AmountParser
import app.doselog.domain.model.Category
import app.doselog.domain.model.OptionError
import app.doselog.ui.components.AmountUnitInput
import app.doselog.ui.components.ConfirmDialog
import app.doselog.ui.components.EmptyState
import app.doselog.ui.components.UnitSuggestionRow
import app.doselog.ui.components.iconRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemEditScreen(
    onDone: () -> Unit,
    viewModel: ItemEditViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var editingPreset by rememberSaveable { mutableStateOf<Long?>(null) } // 0 = new preset
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ItemEditEvent.Closed -> onDone()
                is ItemEditEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    val requestClose = {
        if (viewModel.hasUnsavedChanges) confirmDiscard = true else onDone()
    }
    BackHandler(onBack = requestClose)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "New item" else "Edit item") },
                navigationIcon = {
                    IconButton(onClick = requestClose) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = "Close")
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::save, enabled = !state.isSaving && !state.isLoading && !state.notFound) {
                        Text("Save")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.isLoading -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }

            state.notFound -> EmptyState(
                icon = R.drawable.ic_warning,
                title = "Item not found",
                message = "It may have been deleted.",
                modifier = Modifier.padding(padding),
                action = { Button(onClick = onDone) { Text("Back") } },
            )

            else -> ItemForm(
                form = form,
                state = state,
                viewModel = viewModel,
                onEditPreset = { editingPreset = it },
                onDelete = { confirmDelete = true },
                modifier = Modifier.padding(padding),
            )
        }
    }

    editingPreset?.let { key ->
        val existing = form.presets.firstOrNull { it.key == key }
        key(key) {
            PresetDialog(
                initial = existing ?: PresetForm(key = 0, unit = form.defaultUnit),
                onDismiss = { editingPreset = null },
                onConfirm = {
                    viewModel.upsertPreset(it)
                    editingPreset = null
                },
            )
        }
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = "Discard changes?",
            text = "Your changes to this item haven't been saved.",
            confirmLabel = "Discard",
            onConfirm = {
                confirmDiscard = false
                onDone()
            },
            onDismiss = { confirmDiscard = false },
        )
    }

    if (confirmDelete) {
        val name = form.name.trim().ifEmpty { "this item" }
        if (state.entryCount > 0) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                icon = { Icon(painterResource(R.drawable.ic_archive), contentDescription = null) },
                title = { Text("Archive instead?") },
                text = {
                    Text(
                        "$name has ${state.entryCount} log ${if (state.entryCount == 1) "entry" else "entries"}. " +
                            "Deleting it would erase that history, so it can only be archived. Archived items " +
                            "disappear from the Log screen but stay in reports and exports.",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmDelete = false
                            viewModel.setArchived(true)
                        },
                        enabled = !state.isArchived,
                    ) { Text("Archive") }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
            )
        } else {
            ConfirmDialog(
                title = "Delete $name?",
                text = "This removes the item and its presets. It has no log entries. This can't be undone.",
                confirmLabel = "Delete",
                onConfirm = {
                    confirmDelete = false
                    viewModel.delete()
                },
                onDismiss = { confirmDelete = false },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemForm(
    form: ItemForm,
    state: ItemEditUiState,
    viewModel: ItemEditViewModel,
    onEditPreset: (Long) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = form.name,
            onValueChange = viewModel::onNameChange,
            label = { Text("Name") },
            singleLine = true,
            isError = state.nameError != null,
            supportingText = state.nameError?.let { { Text(it.message) } },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Category", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Category.entries.forEach { category ->
                    FilterChip(
                        selected = form.category == category,
                        onClick = { viewModel.onCategoryChange(category) },
                        label = { Text(category.displayName) },
                        leadingIcon = {
                            Icon(
                                painterResource(category.iconRes()),
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                            )
                        },
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = form.defaultUnit,
                onValueChange = viewModel::onDefaultUnitChange,
                label = { Text("Default unit (optional)") },
                supportingText = { Text("Suggested when you add a preset or a custom dose") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            UnitSuggestionRow(selected = form.defaultUnit, onSelect = viewModel::onDefaultUnitChange)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Presets", style = MaterialTheme.typography.titleSmall)
            Text(
                "Presets are the quick-pick doses shown on the Log screen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            form.presets.forEachIndexed { index, preset ->
                PresetRow(
                    preset = preset,
                    error = state.presetErrors[preset.key],
                    canMoveUp = index > 0,
                    canMoveDown = index < form.presets.lastIndex,
                    onMove = { up -> viewModel.movePreset(preset.key, up) },
                    onEdit = { onEditPreset(preset.key) },
                    onRemove = { viewModel.removePreset(preset.key) },
                )
            }
            OutlinedButton(onClick = { onEditPreset(0L) }) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Add preset")
            }
        }

        if (!state.isNew) {
            ManageItemSection(
                isArchived = state.isArchived,
                entryCount = state.entryCount,
                onArchiveChange = viewModel::setArchived,
                onDelete = onDelete,
            )
        }

        Button(
            onClick = viewModel::save,
            enabled = !state.isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(if (state.isNew) "Create item" else "Save changes")
        }
        Spacer(Modifier.navigationBarsPadding().height(16.dp))
    }
}

@Composable
private fun PresetRow(
    preset: PresetForm,
    error: OptionError?,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    val draft = preset.toDraft()
    Surface(
        onClick = onEdit,
        shape = MaterialTheme.shapes.medium,
        color = if (error != null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    draft.effectiveLabel.ifEmpty { "(empty)" },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val detail = AmountFormat.label(draft.amount, preset.unit)
                if (preset.label.isNotBlank() && detail.isNotEmpty()) {
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (error != null) {
                    Text(error.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            IconButton(onClick = { onMove(true) }, enabled = canMoveUp) {
                Icon(painterResource(R.drawable.ic_arrow_upward), contentDescription = "Move ${draft.effectiveLabel} up")
            }
            IconButton(onClick = { onMove(false) }, enabled = canMoveDown) {
                Icon(painterResource(R.drawable.ic_arrow_downward), contentDescription = "Move ${draft.effectiveLabel} down")
            }
            IconButton(onClick = onRemove) {
                Icon(painterResource(R.drawable.ic_delete), contentDescription = "Remove ${draft.effectiveLabel}")
            }
        }
    }
}

@Composable
private fun ManageItemSection(
    isArchived: Boolean,
    entryCount: Int,
    onArchiveChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Archive or delete", style = MaterialTheme.typography.titleSmall)
            Text(
                text = when {
                    isArchived -> "This item is archived: it's hidden from the Log screen but kept in reports."
                    entryCount > 0 -> "Logged $entryCount ${if (entryCount == 1) "time" else "times"}. " +
                        "Items with history can be archived but not deleted."
                    else -> "Not logged yet, so it can be deleted."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onArchiveChange(!isArchived) }) {
                    Icon(
                        painterResource(if (isArchived) R.drawable.ic_unarchive else R.drawable.ic_archive),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(if (isArchived) "Restore" else "Archive")
                }
                OutlinedButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(painterResource(R.drawable.ic_delete), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Delete")
                }
            }
        }
    }
}

/** Add or edit one preset. The label auto-fills from amount and unit unless the user types one. */
@Composable
private fun PresetDialog(initial: PresetForm, onDismiss: () -> Unit, onConfirm: (PresetForm) -> Unit) {
    var amountText by rememberSaveable { mutableStateOf(initial.amountText) }
    var unit by rememberSaveable { mutableStateOf(initial.unit) }
    var label by rememberSaveable { mutableStateOf(initial.label) }
    var showErrors by rememberSaveable { mutableStateOf(false) }

    val parsed = AmountParser.parse(amountText)
    val amount = (parsed as? AmountInput.Valid)?.value
    val amountError = when {
        parsed is AmountInput.Invalid -> parsed.error.message
        amount != null && amount <= 0.0 -> OptionError.AMOUNT_NOT_POSITIVE.message
        else -> null
    }
    val autoLabel = AmountFormat.label(amount, unit)
    val missingBoth = parsed is AmountInput.Empty && label.isBlank() && unit.isBlank()
    val canSave = amountError == null && !missingBoth && (amount != null || label.isNotBlank() || unit.isNotBlank())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.key == 0L) "Add preset" else "Edit preset") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AmountUnitInput(
                    amountText = amountText,
                    onAmountChange = { amountText = it },
                    unit = unit,
                    onUnitChange = { unit = it },
                    amountError = amountError,
                    amountLabel = "Amount (optional)",
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    placeholder = { Text(autoLabel.ifEmpty { "e.g. Small bowl" }) },
                    supportingText = {
                        Text(
                            if (showErrors && !canSave && amountError == null) {
                                OptionError.NEEDS_AMOUNT_OR_LABEL.message
                            } else {
                                "Leave empty to use \"${autoLabel.ifEmpty { "amount + unit" }}\""
                            },
                        )
                    },
                    isError = showErrors && !canSave && amountError == null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (canSave) {
                    onConfirm(initial.copy(amountText = amountText.trim(), unit = unit.trim(), label = label.trim()))
                } else {
                    showErrors = true
                }
            }) { Text(if (initial.key == 0L) "Add" else "Done") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
