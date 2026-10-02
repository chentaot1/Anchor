package com.anchor.adhd.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anchor.adhd.data.model.CheckInTag
import com.anchor.adhd.data.model.EnergyLevel
import com.anchor.adhd.domain.CheckInCodec
import com.anchor.adhd.ui.copy.AppCopy

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DailyCheckInSection(
    activation: EnergyLevel,
    selectedTags: Set<CheckInTag>,
    onActivationChange: (EnergyLevel) -> Unit,
    onTagToggle: (CheckInTag) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
        SectionHeader(AppCopy.CHECK_IN_SECTION, AppCopy.CHECK_IN_ACTIVATION_HINT)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EnergyLevel.entries.forEach { level ->
                FilterChip(
                    selected = activation == level,
                    onClick = { onActivationChange(level) },
                    label = { Text(CheckInCodec.activationLabel(level)) }
                )
            }
        }
        Text(
            AppCopy.CHECK_IN_TAGS_HINT,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CheckInTag.entries.forEach { tag ->
                FilterChip(
                    selected = tag in selectedTags,
                    onClick = { onTagToggle(tag) },
                    label = { Text(CheckInCodec.tagLabel(tag)) }
                )
            }
        }
    }
}
