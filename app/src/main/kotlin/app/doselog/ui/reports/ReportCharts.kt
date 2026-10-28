package app.doselog.ui.reports

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.doselog.domain.reports.ChartPoint
import app.doselog.ui.components.DoseLogFormats
import app.doselog.ui.components.LocalFormats
import app.doselog.ui.theme.DoseLogColors
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import java.time.LocalDate

private val ChartHeight = 220.dp

@Composable
internal fun ChartsView(viewModel: ReportsViewModel, state: ReportsUiState) {
    val charts by viewModel.charts.collectAsStateWithLifecycle()
    if (state.data.entries.isEmpty() && state.data.scores.isEmpty()) {
        ReportEmpty()
        return
    }
    val selected = charts.selected
    val scoreColor = DoseLogColors.extended.score(16).container

    ProvideVicoTheme(rememberM3VicoTheme()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (charts.items.isNotEmpty()) {
                Text("Item", style = MaterialTheme.typography.titleSmall)
                ChipRow(
                    labels = charts.items.map { it.name },
                    selectedIndex = charts.items.indexOf(selected),
                    onSelect = { viewModel.selectChartItem(charts.items[it].itemId) },
                )
                if (selected != null && selected.units.size > 1) {
                    Text("Unit (amounts in different units are never added together)", style = MaterialTheme.typography.bodySmall)
                    ChipRow(
                        labels = selected.units,
                        selectedIndex = selected.units.indexOf(charts.unit),
                        onSelect = { viewModel.selectChartUnit(selected.units[it]) },
                    )
                }
            }

            ChartCard(
                title = if (selected != null && charts.unit != null) "Daily total · ${selected.name} (${charts.unit})" else "Daily total",
                subtitle = "Sum of the amounts taken each day",
            ) {
                if (charts.dailyTotals.any { it.value > 0.0 }) {
                    DailyBarChart(charts.dailyTotals, MaterialTheme.colorScheme.primary, "Daily totals of ${selected?.name}")
                } else {
                    ChartMessage("No amounts recorded for this item in the range.")
                }
            }

            ChartCard(title = "Day score", subtitle = "The latest score of each day") {
                if (charts.dayScores.isNotEmpty()) {
                    ScoreLineChart(charts.dayScores, scoreColor)
                } else {
                    ChartMessage("No scores in this range.")
                }
            }

            ChartCard(
                title = "Score vs. ${selected?.name ?: "intake"}",
                subtitle = "Bars: times taken per day (left axis) · Line: day score (right axis)",
            ) {
                if (selected != null && charts.dayScores.isNotEmpty() && charts.dailyCounts.isNotEmpty()) {
                    CombinedChart(charts.dailyCounts, charts.dayScores, MaterialTheme.colorScheme.tertiary, scoreColor)
                } else {
                    ChartMessage("Needs both entries and scores in the range.")
                }
            }
        }
    }
}

@Composable
private fun ChipRow(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEachIndexed { index, label ->
            FilterChip(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

@Composable
private fun ChartCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.padding(top = 12.dp)) { content() }
        }
    }
}

@Composable
private fun ChartMessage(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 32.dp),
    )
}

/** X values are epoch days; label them as "28 Oct", thinning labels so they never overlap. */
@Composable
private fun rememberDateAxis(formats: DoseLogFormats, pointCount: Int): HorizontalAxis<Axis.Position.Horizontal.Bottom> {
    val formatter = remember(formats) {
        CartesianValueFormatter { _, value, _ -> formats.dayMonth(LocalDate.ofEpochDay(value.toLong())) }
    }
    val spacing = (pointCount / 5).coerceAtLeast(1)
    return HorizontalAxis.rememberBottom(
        valueFormatter = formatter,
        itemPlacer = remember(spacing) { HorizontalAxis.ItemPlacer.aligned(spacing = { spacing }) },
    )
}

@Composable
private fun rememberMarker() = rememberDefaultCartesianMarker(
    label = rememberTextComponent(
        style = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp),
        padding = Insets(8.dp, 4.dp),
        background = rememberShapeComponent(
            fill = Fill(MaterialTheme.colorScheme.surfaceContainerHighest),
            shape = RoundedCornerShape(6.dp),
        ),
    ),
)

@Composable
private fun DailyBarChart(points: List<ChartPoint>, color: Color, description: String) {
    val formats = LocalFormats.current
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(points) {
        modelProducer.runTransaction {
            columnModel { series(points.map { it.epochDay }, points.map { it.value }) }
        }
    }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                    rememberLineComponent(fill = Fill(color), thickness = 10.dp, shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
                ),
            ),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = rememberDateAxis(formats, points.size),
            marker = rememberMarker(),
            getXStep = { _, _, _ -> 1.0 },
        ),
        modelProducer = modelProducer,
        scrollState = rememberVicoScrollState(scrollEnabled = true),
        zoomState = rememberVicoZoomState(initialZoom = remember { Zoom.Content }),
        modifier = Modifier
            .fillMaxWidth()
            .height(ChartHeight)
            .semantics { contentDescription = description },
    )
}

@Composable
private fun ScoreLineChart(points: List<ChartPoint>, color: Color) {
    val formats = LocalFormats.current
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(points) {
        modelProducer.runTransaction {
            lineModel { series(points.map { it.epochDay }, points.map { it.value }) }
        }
    }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberScoreLineLayer(color, verticalAxisPosition = null),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = rememberDateAxis(formats, points.size),
            marker = rememberMarker(),
            getXStep = { _, _, _ -> 1.0 },
        ),
        modelProducer = modelProducer,
        scrollState = rememberVicoScrollState(scrollEnabled = true),
        zoomState = rememberVicoZoomState(initialZoom = remember { Zoom.Content }),
        modifier = Modifier
            .fillMaxWidth()
            .height(ChartHeight)
            .semantics { contentDescription = "Day score over time" },
    )
}

@Composable
private fun rememberScoreLineLayer(color: Color, verticalAxisPosition: Axis.Position.Vertical?): LineCartesianLayer =
    rememberLineCartesianLayer(
        lineProvider = LineCartesianLayer.LineProvider.series(
            LineCartesianLayer.rememberLine(
                fill = remember(color) { LineCartesianLayer.LineFill.single(Fill(color)) },
                areaFill = remember(color) {
                    LineCartesianLayer.AreaFill.single(
                        Fill(Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent))),
                    )
                },
                pointProvider = LineCartesianLayer.PointProvider.single(
                    LineCartesianLayer.Point(rememberShapeComponent(fill = Fill(color), shape = CircleShape), size = 6.dp),
                ),
            ),
        ),
        rangeProvider = remember { CartesianLayerRangeProvider.fixed(minY = 0.0, maxY = 20.0) },
        verticalAxisPosition = verticalAxisPosition,
    )

@Composable
private fun CombinedChart(counts: List<ChartPoint>, scores: List<ChartPoint>, barColor: Color, lineColor: Color) {
    val formats = LocalFormats.current
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(counts, scores) {
        modelProducer.runTransaction {
            columnModel { series(counts.map { it.epochDay }, counts.map { it.value }) }
            lineModel { series(scores.map { it.epochDay }, scores.map { it.value }) }
        }
    }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                    rememberLineComponent(fill = Fill(barColor.copy(alpha = 0.75f)), thickness = 8.dp, shape = RoundedCornerShape(3.dp)),
                ),
                verticalAxisPosition = Axis.Position.Vertical.Start,
            ),
            rememberScoreLineLayer(lineColor, verticalAxisPosition = Axis.Position.Vertical.End),
            startAxis = VerticalAxis.rememberStart(
                valueFormatter = remember { CartesianValueFormatter.decimal(decimalCount = 0) },
            ),
            endAxis = VerticalAxis.rememberEnd(),
            bottomAxis = rememberDateAxis(formats, counts.size),
            marker = rememberMarker(),
            getXStep = { _, _, _ -> 1.0 },
        ),
        modelProducer = modelProducer,
        scrollState = rememberVicoScrollState(scrollEnabled = true),
        zoomState = rememberVicoZoomState(initialZoom = remember { Zoom.Content }),
        modifier = Modifier
            .fillMaxWidth()
            .height(ChartHeight)
            .semantics { contentDescription = "Day score compared with intake count" },
    )
}
