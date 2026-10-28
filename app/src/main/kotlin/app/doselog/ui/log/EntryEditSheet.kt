package app.doselog.ui.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.doselog.data.repository.DoseSelection
import app.doselog.data.repository.LogEntryEdit
import app.doselog.domain.AmountInput
import app.doselog.domain.AmountParser
import app.doselog.domain.model.Item
import app.doselog.domain.model.LogEntry
import app.doselog.domain.model.OptionError
import app.doselog.ui.components.AmountUnitInput
import app.doselog.ui.components.ConfirmDialog
import app.doselog.ui.components.DateTimeField
import app.doselog.ui.components.LocalFormats
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val MODE_KEEP = 0
private const val MODE_PRESET = 1
private const val MODE_CUSTOM = 2

/**
 * Edit or delete a logged entry. The dose starts on "keep current", so saving without touching it
 * never re-snapshots from a preset that may have changed since.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EntryEditSheet(
    entry: LogEntry,
    item: Item?,
    zone: ZoneId,
    onDismiss: () -> Unit,
    onSave: (LogEntryEdit) -> Unit,
    onDelete: () -> Unit,
) {
    val formats = LocalFormats.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var mode by rememberSaveable(entry.id) { mutableIntStateOf(MODE_KEEP) }
    var optionId by rememberSaveable(entry.id) { mutableStateOf<Long?>(null) }
    var customAmount by rememberSaveable(entry.id) { mutableStateOf(AmountParser.toEditText(entry.amount)) }
    var customUnit by rememberSaveable(entry.id) { mutableStateOf(entry.unit) }
    var description by rememberSaveable(entry.id) { mutableStateOf(entry.description.orEmpty()) }
    var takenAtMillis by rememberSaveable(entry.id) { mutableStateOf<Long?>(entry.takenAt.toEpochMilli()) }
    var confirmDelete by rememberSaveable(entry.id) { mutableStateOf(false) }

    val customInput = AmountParser.parse(customAmount)
    val customValue = (customInput as? AmountInput.Valid)?.value?.takeIf { it > 0.0 }
    val dose: DoseSelection? = when (mode) {
        MODE_PRESET -> optionId?.let { DoseSelection.Preset(it) }
        MODE_CUSTOM -> customValue?.let { DoseSelection.Custom(it, customUnit) }
        else -> DoseSelection.Unchanged
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text(entry.itemName, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val recorded = formats.dateTime(entry.recordedAt.atZone(zone), LocalDate.now(zone))
                Text(
                    "Recorded $recorded" + if (entry.updatedAt != null) " · edited" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text("Dose", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = mode == MODE_KEEP,
                    onClick = { mode = MODE_KEEP },
                    label = { Text("Current: ${entry.label}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
                item?.options?.forEach { option ->
                    FilterChip(
                        selected = mode == MODE_PRESET && optionId == option.id,
                        onClick = {
                            mode = MODE_PRESET
                            optionId = option.id
                        },
                        label = { Text(option.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
                FilterChip(selected = mode == MODE_CUSTOM, onClick = { mode = MODE_CUSTOM }, label = { Text("Custom") })
            }
            if (mode == MODE_CUSTOM) {
                AmountUnitInput(
                    amountText = customAmount,
                    onAmountChange = { customAmount = it },
                    unit = customUnit,
                    onUnitChange = { customUnit = it },
                    amountError = when {
                        customInput is AmountInput.Invalid -> customInput.error.message
                        customInput is AmountInput.Valid && customValue == null -> OptionError.AMOUNT_NOT_POSITIVE.message
                        else -> null
                    },
                )
            }

            DateTimeField(
                value = takenAtMillis?.let(Instant::ofEpochMilli),
                onValueChange = { takenAtMillis = it?.toEpochMilli() },
                label = "Taken",
                zone = zone,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (optional)") },
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { confirmDelete = true }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(
                    enabled = dose != null,
                    onClick = {
                        val chosen = dose ?: return@Button
                        val newTime = takenAtMillis?.let(Instant::ofEpochMilli) ?: Instant.now()
                        // Keep the original zone unless the time was changed on this phone.
                        val timeZone = if (newTime == entry.takenAt) entry.zone else zone
                        onSave(LogEntryEdit(entry.id, chosen, description, newTime, timeZone))
                    },
                ) { Text("Save") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete entry?",
            text = "${entry.itemName} · ${entry.label} will be removed permanently.",
            confirmLabel = "Delete",
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
