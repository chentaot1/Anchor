package com.anchor.adhd.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anchor.adhd.data.model.CbtCardEntity
import com.anchor.adhd.domain.StrategyAction
import com.anchor.adhd.domain.StrategyCatalog
import com.anchor.adhd.ui.copy.AppCopy

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StrategyDialog(
    card: CbtCardEntity,
    onDismiss: () -> Unit,
    onAction: (StrategyAction) -> Unit
) {
    val actions = StrategyCatalog.actionsFor(card.id)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(card.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(card.body)
                if (actions.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        actions.forEach { action ->
                            FilledTonalButton(onClick = { onAction(action) }) {
                                Text(StrategyCatalog.actionLabel(action))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(AppCopy.DIALOG_CLOSE) }
        }
    )
}

@Composable
fun DailyStrategyCard(
    card: CbtCardEntity,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnchorCard(modifier = modifier.fillMaxWidth()) {
        SectionHeader(AppCopy.STRATEGY_TODAY, AppCopy.STRATEGY_TODAY_HINT)
        Text(card.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Text(
            card.body.lines().first(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2
        )
        TextButton(onClick = onOpen, modifier = Modifier.padding(top = 4.dp)) {
            Text("Read + act")
        }
    }
}
