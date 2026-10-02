package com.anchor.adhd.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anchor.adhd.data.model.FunLinkEntity
import com.anchor.adhd.ui.theme.AnchorColors
import com.anchor.adhd.ui.vm.AnchorViewModel

@Composable
fun FunLinksSettingsSection(
    vm: AnchorViewModel,
    modifier: Modifier = Modifier
) {
    val allLinks by vm.allFunLinks.collectAsState()
    val credits by vm.funCredits.collectAsState()
    val strictMode by vm.funStrictMode.collectAsState()

    var editingLink by remember { mutableStateOf<FunLinkEntity?>(null) }
    var isAddingNew by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Fun Links (Trojan Horse)",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "Bite-sized, low-dopamine micro-breaks. Once completed, your phone returns directly to Anchor facing your NOW anchor.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Strict Mode Toggle
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Strict Mode (Focus to Play)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = if (strictMode) {
                                "0 free daily games. Every game must be earned via focus (≥5m)."
                            } else {
                                "1 free game daily at midnight + 1 earned per focus session (≥5m)."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = strictMode,
                        onCheckedChange = { vm.setFunStrictMode(it) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Current game credits: $credits",
                        style = MaterialTheme.typography.labelMedium,
                        color = AnchorColors.HarborPrimary
                    )
                }
            }
        }

        // List of configured links
        Text(
            text = "Active Links (max 4 displayed on Grove):",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        allLinks.forEach { link ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (link.enabled) {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                    }
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = link.enabled,
                        onCheckedChange = { isChecked ->
                            vm.saveFunLink(link.copy(enabled = isChecked))
                        }
                    )
                    Text(
                        text = link.emoji,
                        fontSize = 22.sp,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = link.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = link.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { editingLink = link }) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { vm.deleteFunLink(link.id) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { isAddingNew = true },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Link")
            }

            OutlinedButton(
                onClick = { vm.restoreDefaultFunLinks() },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Restore Defaults")
            }
        }
    }

    if (isAddingNew) {
        FunLinkEditDialog(
            link = FunLinkEntity(name = "", url = "https://", emoji = "🎮", sortOrder = allLinks.size, enabled = true),
            title = "Add Fun Link",
            onDismiss = { isAddingNew = false },
            onConfirm = { newLink ->
                vm.saveFunLink(newLink)
                isAddingNew = false
            }
        )
    }

    editingLink?.let { link ->
        FunLinkEditDialog(
            link = link,
            title = "Edit Fun Link",
            onDismiss = { editingLink = null },
            onConfirm = { updated ->
                vm.saveFunLink(updated)
                editingLink = null
            }
        )
    }
}

@Composable
private fun FunLinkEditDialog(
    link: FunLinkEntity,
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (FunLinkEntity) -> Unit
) {
    var name by remember { mutableStateOf(link.name) }
    var url by remember { mutableStateOf(link.url) }
    var emoji by remember { mutableStateOf(link.emoji) }

    val presetEmojis = listOf("🟩", "🟪", "♟️", "🧩", "🎮", "🎯", "📖", "☕", "🌊", "✨")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Game / Link Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Web URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = emoji,
                        onValueChange = { emoji = it.take(4) },
                        label = { Text("Emoji") },
                        singleLine = true,
                        modifier = Modifier.width(90.dp)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        presetEmojis.take(5).forEach { preset ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (emoji == preset) AnchorColors.HarborPrimary.copy(alpha = 0.3f)
                                        else Color.Transparent
                                    )
                                    .clickable { emoji = preset },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(preset, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && url.isNotBlank()) {
                        onConfirm(
                            link.copy(
                                name = name.trim(),
                                url = url.trim(),
                                emoji = if (emoji.isBlank()) "🎮" else emoji.trim()
                            )
                        )
                    }
                },
                enabled = name.isNotBlank() && url.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
