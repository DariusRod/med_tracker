package app.doselog.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.doselog.R
import app.doselog.domain.AmountFormat
import app.doselog.domain.UnitTotal
import app.doselog.domain.model.LogEntry
import app.doselog.domain.reports.ItemSummary
import app.doselog.domain.reports.TableColumn
import app.doselog.domain.reports.TimelineDay
import app.doselog.ui.components.CategoryAvatar
import app.doselog.ui.components.EmptyState
import app.doselog.ui.components.LocalFormats
import app.doselog.ui.components.ScoreBadge
import java.time.LocalDate

@Composable
internal fun ReportEmpty(message: String = "Try a longer date range or clear the filters.") {
    EmptyState(icon = R.drawable.ic_insights, title = "Nothing in this range", message = message)
}

// ---------------------------------------------------------------- Timeline

@Composable
internal fun TimelineView(viewModel: ReportsViewModel, state: ReportsUiState) {
    val days by viewModel.timeline.collectAsStateWithLifecycle()
    if (state.data.entries.isEmpty()) {
        ReportEmpty()
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        days.forEach { day ->
            stickyHeader(key = "header-${day.date}") { TimelineDayHeader(day, state.today) }
            items(day.entries, key = { it.id }) { entry -> TimelineRow(entry) }
        }
    }
}

@Composable
private fun TimelineDayHeader(day: TimelineDay, today: LocalDate) {
    val formats = LocalFormats.current
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .semantics(mergeDescendants = true) { heading() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(formats.dayHeader(day.date, today), style = MaterialTheme.typography.titleSmall)
                Text(
                    "${day.entries.size} ${if (day.entries.size == 1) "entry" else "entries"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            day.dayScore?.let { score ->
                Text("Day score", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(8.dp))
                ScoreBadge(score.score, size = 32.dp)
            }
        }
    }
}

@Composable
private fun TimelineRow(entry: LogEntry) {
    val formats = LocalFormats.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            formats.time(entry.localDateTime),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .width(72.dp)
                .padding(top = 8.dp),
        )
        CategoryAvatar(entry.category, size = 36.dp)
        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(entry.itemName, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(entry.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            if (!entry.description.isNullOrBlank()) {
                Text(
                    entry.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Table

private val TableColumn.width: Dp
    get() = when (this) {
        TableColumn.DATE -> 108.dp
        TableColumn.TIME -> 80.dp
        TableColumn.ITEM -> 168.dp
        TableColumn.CATEGORY -> 116.dp
        TableColumn.AMOUNT -> 92.dp
        TableColumn.UNIT -> 80.dp
        TableColumn.DESCRIPTION -> 260.dp
    }

@Composable
internal fun TableView(viewModel: ReportsViewModel, state: ReportsUiState) {
    val rows by viewModel.tableRows.collectAsStateWithLifecycle()
    val sort by viewModel.tableSort.collectAsStateWithLifecycle()
    if (state.data.entries.isEmpty()) {
        ReportEmpty()
        return
    }
    val tableWidth = TableColumn.entries.fold(32.dp) { acc, column -> acc + column.width }
    Box(
        Modifier
            .fillMaxSize()
            .horizontalScroll(rememberScrollState()),
    ) {
        LazyColumn(
            Modifier
                .width(tableWidth)
                .fillMaxHeight(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            stickyHeader(key = "header") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 16.dp),
                ) {
                    TableColumn.entries.forEach { column ->
                        val active = sort.column == column
                        val direction = if (sort.ascending) "ascending" else "descending"
                        Row(
                            Modifier
                                .width(column.width)
                                .heightIn(min = 48.dp)
                                .clickable(role = Role.Button) { viewModel.sortBy(column) }
                                .semantics {
                                    contentDescription = if (active) "${column.title}, sorted $direction" else "Sort by ${column.title}"
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                column.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                            if (active) {
                                Icon(
                                    painterResource(if (sort.ascending) R.drawable.ic_arrow_upward else R.drawable.ic_arrow_downward),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
            itemsIndexed(rows, key = { _, entry -> entry.id }) { index, entry ->
                TableRow(entry, striped = index % 2 == 1)
            }
        }
    }
}

@Composable
private fun TableRow(entry: LogEntry, striped: Boolean) {
    val formats = LocalFormats.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (striped) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        TableColumn.entries.forEach { column ->
            val text = when (column) {
                TableColumn.DATE -> formats.numericDate(entry.localDate)
                TableColumn.TIME -> formats.time(entry.localDateTime)
                TableColumn.ITEM -> entry.itemName
                TableColumn.CATEGORY -> entry.category.displayName
                TableColumn.AMOUNT -> entry.amount?.let { AmountFormat.format(it) } ?: "—"
                TableColumn.UNIT -> entry.unit.ifEmpty { entry.label }
                TableColumn.DESCRIPTION -> entry.description.orEmpty()
            }
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .width(column.width)
                    .padding(end = 8.dp),
            )
        }
    }
}

// ---------------------------------------------------------------- Summary

@Composable
internal fun SummaryView(viewModel: ReportsViewModel, state: ReportsUiState) {
    val summaries by viewModel.summaries.collectAsStateWithLifecycle()
    if (state.data.entries.isEmpty()) {
        ReportEmpty()
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "overview") {
            Text(
                "${state.data.entries.size} entries · ${summaries.size} items · averages over ${state.data.range.dayCount} days",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(summaries, key = { it.itemId }) { summary -> SummaryCard(summary, state.today) }
    }
}

private fun List<UnitTotal>.describe(suffix: String = "", decimals: Int = 6): String =
    joinToString(" · ") { "${AmountFormat.format(it.total, decimals)} ${it.unit}".trim() + suffix }

@Composable
private fun SummaryCard(summary: ItemSummary, today: LocalDate) {
    val formats = LocalFormats.current
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryAvatar(summary.category, size = 36.dp)
                Text(
                    summary.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                )
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(
                        "${summary.count}×",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SummaryLine("Total", summary.totals.describe().ifEmpty { "No amounts (label-only entries)" })
            if (summary.totals.isNotEmpty()) {
                SummaryLine("Average per day", summary.averagesPerDay.describe(decimals = 2))
            }
            SummaryLine("Entries per day", AmountFormat.format(summary.countPerDay, 2))
            SummaryLine("First", formats.dateTime(summary.first.localDateTime, today))
            SummaryLine("Last", formats.dateTime(summary.last.localDateTime, today))
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(132.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}
