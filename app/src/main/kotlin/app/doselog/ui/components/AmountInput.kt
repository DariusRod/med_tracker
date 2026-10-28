package app.doselog.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.doselog.domain.UnitSuggestions

/** Amount (decimal keyboard, "." or ",") + free-text unit with suggestion chips. */
@Composable
fun AmountUnitInput(
    amountText: String,
    onAmountChange: (String) -> Unit,
    unit: String,
    onUnitChange: (String) -> Unit,
    amountError: String?,
    modifier: Modifier = Modifier,
    amountLabel: String = "Amount",
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            OutlinedTextField(
                value = amountText,
                onValueChange = onAmountChange,
                label = { Text(amountLabel) },
                singleLine = true,
                isError = amountError != null,
                supportingText = amountError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = unit,
                onValueChange = onUnitChange,
                label = { Text("Unit") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
                modifier = Modifier.weight(1f),
            )
        }
        UnitSuggestionRow(selected = unit, onSelect = onUnitChange)
    }
}

@Composable
fun UnitSuggestionRow(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        UnitSuggestions.forEach { suggestion ->
            FilterChip(
                selected = selected.trim().equals(suggestion, ignoreCase = true),
                onClick = { onSelect(suggestion) },
                label = { Text(suggestion) },
            )
        }
    }
}
