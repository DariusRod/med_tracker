package app.doselog.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.doselog.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Shows "Now" until the user picks a date and time (date picker, then time picker). A null
 * [value] means "now": the caller resolves it at the moment of saving, not when shown.
 */
@Composable
fun DateTimeField(
    value: Instant?,
    onValueChange: (Instant?) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "When",
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val formats = LocalFormats.current
    var step by rememberSaveable { mutableStateOf(PickerStep.NONE) }
    var pickedDateEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }
    val today = LocalDate.now(zone)
    val shown = value?.atZone(zone)
    val valueText = if (shown == null) "Now" else formats.dateTime(shown, today)

    OutlinedCard(
        onClick = { step = PickerStep.DATE },
        modifier = modifier
            .heightIn(min = 56.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$label: $valueText. Tap to change" },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_schedule),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(valueText, style = MaterialTheme.typography.bodyLarge)
            }
            if (value != null) {
                IconButton(onClick = { onValueChange(null) }) {
                    Icon(painterResource(R.drawable.ic_restart_alt), contentDescription = "Reset to now")
                }
            } else {
                Spacer(Modifier.width(12.dp))
            }
        }
    }

    when (step) {
        PickerStep.DATE -> DateStep(
            initial = shown?.toLocalDate() ?: today,
            today = today,
            onDismiss = { step = PickerStep.NONE },
            onPicked = { date ->
                pickedDateEpochDay = date.toEpochDay()
                step = PickerStep.TIME
            },
        )
        PickerStep.TIME -> TimeStep(
            initial = shown?.toLocalTime() ?: LocalTime.now(zone),
            is24Hour = formats.is24Hour,
            onDismiss = { step = PickerStep.NONE },
            onPicked = { time ->
                val date = LocalDate.ofEpochDay(pickedDateEpochDay ?: today.toEpochDay())
                onValueChange(date.atTime(time).atZone(zone).toInstant())
                step = PickerStep.NONE
            },
        )
        PickerStep.NONE -> Unit
    }
}

private enum class PickerStep { NONE, DATE, TIME }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateStep(initial: LocalDate, today: LocalDate, onDismiss: () -> Unit, onPicked: (LocalDate) -> Unit) {
    val todayUtcMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = remember(todayUtcMillis) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayUtcMillis
                override fun isSelectableYear(year: Int) = year <= today.year
            }
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = state.selectedDateMillis ?: return@TextButton
                    onPicked(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                },
                enabled = state.selectedDateMillis != null,
            ) { Text("Next") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeStep(initial: LocalTime, is24Hour: Boolean, onDismiss: () -> Unit, onPicked: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = is24Hour)
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Select time",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                )
                TimePicker(state = state)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = { onPicked(LocalTime.of(state.hour, state.minute)) }) { Text("OK") }
                }
            }
        }
    }
}
