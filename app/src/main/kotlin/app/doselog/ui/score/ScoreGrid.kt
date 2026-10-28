package app.doselog.ui.score

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.doselog.domain.model.ScoreRules
import app.doselog.ui.theme.DoseLogColors

private const val COLUMNS = 5

/** Scores 1–20 as a 4×5 grid of buttons. Nothing is selected until the user picks. */
@Composable
fun ScoreGrid(selected: Int?, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val scores = (ScoreRules.MIN..ScoreRules.MAX).toList()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        scores.chunked(COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { score ->
                    ScoreButton(
                        score = score,
                        selected = score == selected,
                        onClick = { onSelect(score) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ScoreButton(score: Int, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scoreColors = DoseLogColors.extended.score(score)
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(if (selected) scoreColors.container else MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(
                width = if (selected) 0.dp else 1.dp,
                color = if (selected) scoreColors.container else scoreColors.container.copy(alpha = 0.55f),
                shape = shape,
            )
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .semantics { contentDescription = "Score $score" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = score.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) scoreColors.content else MaterialTheme.colorScheme.onSurface,
        )
    }
}
