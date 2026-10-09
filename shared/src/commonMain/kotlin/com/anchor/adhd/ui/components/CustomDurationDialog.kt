package com.anchor.adhd.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.unit.sp
import com.anchor.adhd.ui.theme.AnchorColors

/**
 * Custom Duration Dialog
 *
 * Allows Anchor users to set any focus duration between 1 and 180 minutes,
 * breaking free from rigid fixed pills while offering convenient steppers
 * and ADHD-friendly quick presets.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomDurationDialog(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val clampedInitial = initialMinutes.coerceIn(1, 180)
    var minutes by remember(initialMinutes) { mutableIntStateOf(clampedInitial) }
    var textValue by remember(initialMinutes) { mutableStateOf(clampedInitial.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF161F2E),
        titleContentColor = Color.White,
        textContentColor = Color(0xFF94A3B8),
        icon = {
            Icon(
                imageVector = Icons.Default.Timer,
                contentDescription = null,
                tint = AnchorColors.HarborPrimary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Custom Focus Length",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Stepper Row: [-5] [ Value Display ] [+5]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            minutes = (minutes - 5).coerceAtLeast(1)
                            textValue = minutes.toString()
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text("-5m", fontWeight = FontWeight.Bold)
                    }

                    // Direct editable text input
                    OutlinedTextField(
                        value = textValue,
                        onValueChange = { input ->
                            val filtered = input.filter { it.isDigit() }.take(3)
                            val parsed = filtered.toIntOrNull()
                            if (parsed != null) {
                                minutes = parsed.coerceIn(1, 180)
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
                                    onConfirm(minutes.coerceIn(1, 180))
                                    true
                                } else {
                                    false
                                }
                            },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { onConfirm(minutes.coerceIn(1, 180)) }
                        ),
                        textStyle = MaterialTheme.typography.headlineMedium.copy(
                            textAlign = TextAlign.Center,
                            color = AnchorColors.HarborPrimary,
                            fontWeight = FontWeight.Bold
                        ),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AnchorColors.HarborPrimary,
                            unfocusedBorderColor = Color(0xFF334155),
                            cursorColor = AnchorColors.HarborPrimary
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )

                    OutlinedButton(
                        onClick = {
                            minutes = (minutes + 5).coerceAtMost(180)
                            textValue = minutes.toString()
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text("+5m", fontWeight = FontWeight.Bold)
                    }
                }

                // Preset Pills: [10m, 15m, 20m, 25m, 45m, 60m]
                Text(
                    text = "Quick Presets",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF64748B)
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(5, 10, 15, 20, 25, 30, 45, 60, 90).forEach { preset ->
                        val isSelected = minutes == preset
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                minutes = preset
                                textValue = preset.toString()
                            },
                            label = {
                                Text(
                                    "${preset}m",
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AnchorColors.HarborPrimary.copy(alpha = 0.25f),
                                selectedLabelColor = AnchorColors.HarborPrimary,
                                containerColor = Color(0xFF0F172A),
                                labelColor = Color(0xFF94A3B8)
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = Color(0xFF334155),
                                selectedBorderColor = AnchorColors.HarborPrimary
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(minutes) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AnchorColors.HarborPrimary,
                    contentColor = Color(0xFF0A1120)
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Set Timer", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF94A3B8))
            ) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
