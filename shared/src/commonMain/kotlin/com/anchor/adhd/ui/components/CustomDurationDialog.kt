package com.anchor.adhd.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.anchor.adhd.ui.theme.AnchorSpacing
import com.anchor.adhd.ui.theme.LocalAnchorTokens

/**
 * Custom Duration Dialog — focus durations from one minute up to [maxMinutes].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomDurationDialog(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    maxMinutes: Int = 180,
    title: String = "Custom focus length",
    confirmLabel: String = "Set timer",
) {
    require(maxMinutes in 1..999)
    val tokens = LocalAnchorTokens.current
    val clampedInitial = initialMinutes.coerceIn(1, maxMinutes)
    var minutes by remember(initialMinutes) { mutableIntStateOf(clampedInitial) }
    var textValue by remember(initialMinutes) { mutableStateOf(clampedInitial.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tokens.surface,
        titleContentColor = tokens.ink,
        textContentColor = tokens.ink2,
        icon = {
            Icon(
                imageVector = Icons.Default.Timer,
                contentDescription = null,
                tint = tokens.primary,
                modifier = Modifier.size(32.dp),
            )
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    AnchorQuietButton(
                        label = "-5m",
                        onClick = {
                            minutes = (minutes - 5).coerceAtLeast(1)
                            textValue = minutes.toString()
                        },
                    )

                    OutlinedTextField(
                        value = textValue,
                        onValueChange = { input ->
                            val filtered = input.filter { it.isDigit() }.take(3)
                            val parsed = filtered.toIntOrNull()
                            if (parsed != null) {
                                minutes = parsed.coerceIn(1, maxMinutes)
                                textValue = minutes.toString()
                            } else {
                                textValue = ""
                            }
                        },
                        modifier = Modifier
                            .size(width = 110.dp, height = 64.dp)
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown &&
                                    (event.key == Key.Enter || event.key == Key.NumPadEnter)
                                ) {
                                    onConfirm(minutes.coerceIn(1, maxMinutes))
                                    true
                                } else {
                                    false
                                }
                            },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { onConfirm(minutes.coerceIn(1, maxMinutes)) },
                        ),
                        textStyle = MaterialTheme.typography.headlineMedium.copy(
                            textAlign = TextAlign.Center,
                            color = tokens.primary,
                            fontWeight = FontWeight.Bold,
                        ),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = tokens.primary,
                            unfocusedBorderColor = tokens.fieldEdge,
                            cursorColor = tokens.primary,
                            focusedTextColor = tokens.primary,
                            unfocusedTextColor = tokens.primary,
                        ),
                        shape = RoundedCornerShape(AnchorSpacing.radiusPlate),
                    )

                    AnchorQuietButton(
                        label = "+5m",
                        onClick = {
                            minutes = (minutes + 5).coerceAtMost(maxMinutes)
                            textValue = minutes.toString()
                        },
                    )
                }

                Text(
                    text = "Quick presets",
                    style = MaterialTheme.typography.labelMedium,
                    color = tokens.muted,
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(5, 10, 15, 20, 25, 30, 45, 60, 90).forEach { preset ->
                        AnchorChip(
                            label = "${preset}m",
                            selected = minutes == preset,
                            onClick = {
                                minutes = preset
                                textValue = preset.toString()
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            HarborCtaButton(onClick = { onConfirm(minutes) }) {
                Text(confirmLabel, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = tokens.muted)
            }
        },
        shape = RoundedCornerShape(AnchorSpacing.radiusSheet),
    )
}