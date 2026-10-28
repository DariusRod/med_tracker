package app.doselog.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.doselog.R
import app.doselog.domain.model.Category
import app.doselog.ui.theme.DoseLogColors

@DrawableRes
fun Category.iconRes(): Int = when (this) {
    Category.MEDICATION -> R.drawable.ic_medication
    Category.FOOD -> R.drawable.ic_restaurant
    Category.SUPPLEMENT -> R.drawable.ic_eco
    Category.OTHER -> R.drawable.ic_category
}

/** A small tinted pill with the category icon and name. */
@Composable
fun CategoryTag(category: Category, modifier: Modifier = Modifier) {
    val colors = DoseLogColors.extended.category(category)
    Surface(color = colors.container, contentColor = colors.content, shape = CircleShape, modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(painterResource(category.iconRes()), contentDescription = null, modifier = Modifier.size(14.dp))
            Text(category.displayName, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** A round icon in the category's colors, used as a list leading element. */
@Composable
fun CategoryAvatar(category: Category, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val colors = DoseLogColors.extended.category(category)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(category.iconRes()),
            contentDescription = category.displayName,
            tint = colors.content,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

/** The score in a colored circle, red (1) to green (20). */
@Composable
fun ScoreBadge(score: Int, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val colors = DoseLogColors.extended.score(score)
    Box(
        modifier = modifier
            .sizeIn(minWidth = size, minHeight = size)
            .clip(CircleShape)
            .background(colors.container)
            .clearAndSetSemantics { contentDescription = "Score $score" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = score.toString(),
            color = colors.content,
            style = if (size >= 40.dp) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** A small dot in the score's color, for compact summaries. */
@Composable
fun ScoreDot(score: Int, modifier: Modifier = Modifier, size: Dp = 12.dp) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(DoseLogColors.extended.score(score).container),
    )
}

@Composable
fun EmptyState(
    @DrawableRes icon: Int,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { heading() },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
