package app.doselog.ui.log

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.doselog.R
import app.doselog.domain.AmountInput
import app.doselog.domain.model.Item
import app.doselog.domain.model.OptionError
import app.doselog.ui.components.AmountUnitInput
import app.doselog.ui.components.CategoryAvatar
import app.doselog.ui.components.DateTimeField
import java.time.Instant
import java.time.ZoneId

/** One loggable item: preset chips, optional custom amount, description, time and Submit. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogItemCard(
    item: Item,
    draft: CardDraft,
    isSaving: Boolean,
    zone: ZoneId,
    onSelectOption: (Long) -> Unit,
    onToggleCustom: () -> Unit,
    onCustomAmountChange: (String) -> Unit,
    onCustomUnitChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onTakenAtChange: (Instant?) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canSubmit = draft.doseSelection(item) != null && !isSaving
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryAvatar(item.category, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        item.category.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item.options.forEach { option ->
                    val selected = draft.selectedOptionId == option.id
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectOption(option.id) },
                        label = { Text(option.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = if (selected) {
                            {
                                Icon(
                                    painterResource(R.drawable.ic_check),
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                                )
                            }
                        } else {
                            null
                        },
                    )
                }
                FilterChip(
                    selected = draft.customSelected,
                    onClick = onToggleCustom,
                    label = { Text("Custom") },
                    leadingIcon = {
                        Icon(
                            painterResource(if (draft.customSelected) R.drawable.ic_check else R.drawable.ic_edit),
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    },
                )
            }
            if (item.options.isEmpty() && !draft.customSelected) {
                Text(
                    "No presets yet. Use Custom, or add presets in Manage.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(visible = draft.customSelected) {
                val input = draft.customAmountInput
                val error = when {
                    input is AmountInput.Invalid -> input.error.message
                    input is AmountInput.Valid && input.value <= 0.0 -> OptionError.AMOUNT_NOT_POSITIVE.message
                    else -> null
                }
                AmountUnitInput(
                    amountText = draft.customAmount,
                    onAmountChange = onCustomAmountChange,
                    unit = draft.customUnit,
                    onUnitChange = onCustomUnitChange,
                    amountError = error,
                )
            }

            OutlinedTextField(
                value = draft.description,
                onValueChange = onDescriptionChange,
                label = { Text("Description (optional)") },
                maxLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DateTimeField(
                    value = draft.takenAtMillis?.let(Instant::ofEpochMilli),
                    onValueChange = onTakenAtChange,
                    zone = zone,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = onSubmit,
                    enabled = canSubmit,
                    modifier = Modifier
                        .heightIn(min = 56.dp)
                        .semantics { contentDescription = "Submit ${item.name}" },
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Submit")
                    }
                }
            }
        }
    }
}
