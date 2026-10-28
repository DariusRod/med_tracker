package app.doselog.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.doselog.R
import app.doselog.domain.model.ScoreRules
import app.doselog.domain.reports.CalendarDay
import app.doselog.domain.reports.DayDetail
import app.doselog.ui.components.CategoryAvatar
import app.doselog.ui.components.LocalFormats
import app.doselog.ui.components.ScoreBadge
import app.doselog.ui.theme.DoseLogColors
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.WeekFields

@Composable
internal fun CalendarView(viewModel: ReportsViewModel, state: ReportsUiState) {
    val locale = LocalConfiguration.current.locales[0]
    val firstDayOfWeek = WeekFields.of(locale).firstDayOfWeek
    val calendarFlow = remember(viewModel, firstDayOfWeek) { viewModel.calendar(firstDayOfWeek) }
    val calendar by calendarFlow.collectAsStateWithLifecycle(CalendarUiState())
    val formats = LocalFormats.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.showMonth(calendar.month.minusMonths(1)) }) {
                Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Previous month")
            }
            Text(
                formats.monthYear(calendar.month.atDay(1)),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { viewModel.showMonth(calendar.month.plusMonths(1)) }) {
                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = "Next month")
            }
        }

        Row(Modifier.fillMaxWidth()) {
            (0 until 7).forEach { offset ->
                Text(
                    firstDayOfWeek.plus(offset.toLong()).getDisplayName(TextStyle.SHORT, locale),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        calendar.days.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { day ->
                    DayCell(
                        day = day,
                        isToday = day.date == state.today,
                        onClick = { viewModel.selectDay(day.date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        Spacer(Modifier.height(12.dp))
        ScoreLegend()
        Text(
            "Days are colored by day score (the latest score of that day). A dot means something was logged. " +
                "Days outside the selected range are faded.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }

    calendar.selectedDay?.let { detail ->
        DayDetailSheet(detail = detail, today = state.today, onDismiss = { viewModel.selectDay(null) })
    }
}

@Composable
private fun DayCell(day: CalendarDay, isToday: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (!day.inMonth) {
        Spacer(modifier.aspectRatio(1f))
        return
    }
    val scoreColors = day.dayScore?.let { DoseLogColors.extended.score(it) }
    val alpha = if (day.inRange) 1f else 0.35f
    val background = scoreColors?.container?.copy(alpha = alpha) ?: MaterialTheme.colorScheme.surfaceContainerLow
    val textColor = scoreColors?.content ?: MaterialTheme.colorScheme.onSurface.copy(alpha = if (day.inRange) 1f else 0.5f)
    val description = buildString {
        append(day.date.toString())
        day.dayScore?.let { append(", day score $it") }
        if (day.entryCount > 0) append(", ${day.entryCount} entries")
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.small)
            .background(background)
            .then(
                if (isToday) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small) else Modifier,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                day.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = textColor,
            )
            Box(
                Modifier
                    .padding(top = 2.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(if (day.entryCount > 0) textColor else Color.Transparent),
            )
        }
    }
}

@Composable
private fun ScoreLegend() {
    val extended = DoseLogColors.extended
    val colors = (ScoreRules.MIN..ScoreRules.MAX).map { extended.score(it).container }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("1", style = MaterialTheme.typography.labelMedium)
        Box(
            Modifier
                .padding(horizontal = 8.dp)
                .weight(1f)
                .height(10.dp)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(colors)),
        )
        Text("20", style = MaterialTheme.typography.labelMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayDetailSheet(detail: DayDetail, today: LocalDate, onDismiss: () -> Unit) {
    val formats = LocalFormats.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formats.dayHeader(detail.date, today), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                detail.dayScore?.let { ScoreBadge(it.score) }
            }
            if (detail.scores.isEmpty() && detail.entries.isEmpty()) {
                Text("Nothing recorded on this day.", style = MaterialTheme.typography.bodyMedium)
            }
            if (detail.scores.isNotEmpty()) {
                Text("Scores", style = MaterialTheme.typography.titleSmall)
                detail.scores.forEach { score ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ScoreBadge(score.score, size = 32.dp)
                        Text(
                            formats.time(score.localDateTime) + (score.note?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
            if (detail.entries.isNotEmpty()) {
                if (detail.scores.isNotEmpty()) HorizontalDivider()
                Text("Entries", style = MaterialTheme.typography.titleSmall)
                detail.entries.forEach { entry ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryAvatar(entry.category, size = 32.dp)
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(start = 12.dp),
                        ) {
                            Text("${entry.itemName} · ${entry.label}", style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (!entry.description.isNullOrBlank()) {
                                Text(entry.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(formats.time(entry.localDateTime), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
