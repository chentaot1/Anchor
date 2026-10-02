package com.anchor.adhd.ui.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.anchor.adhd.data.model.TaskDifficulty
import com.anchor.adhd.data.model.TaskEntity
import com.anchor.adhd.ui.components.AnchorCard
import com.anchor.adhd.ui.components.AnchorCardStyle
import com.anchor.adhd.ui.components.AnchorScreenBackground
import com.anchor.adhd.ui.components.DayProgressBar
import com.anchor.adhd.ui.components.EmptyState
import com.anchor.adhd.ui.components.ItemSpacing
import com.anchor.adhd.ui.components.ScreenPadding
import com.anchor.adhd.ui.components.SectionHeader
import com.anchor.adhd.ui.components.SectionSpacing
import com.anchor.adhd.ui.components.StatusChip
import com.anchor.adhd.data.model.CheckInTag
import com.anchor.adhd.data.model.EnergyLevel
import com.anchor.adhd.ui.components.DailyCheckInSection
import com.anchor.adhd.ui.components.VisualTimerRing
import com.anchor.adhd.ui.copy.AppCopy
import com.anchor.adhd.ui.grove.GardenScene
import com.anchor.adhd.ui.components.VitalityBar
import com.anchor.adhd.ui.theme.AnchorTheme

@Preview(name = "Grove scene", showBackground = true, backgroundColor = 0xFF0A0E18, widthDp = 412, heightDp = 360)
@Composable
private fun GroveScenePreview() {
    AnchorTheme {
        GardenScene(
            plants = listOf(
                com.anchor.adhd.domain.PlantSpecies.OAK,
                com.anchor.adhd.domain.PlantSpecies.PINE,
                com.anchor.adhd.domain.PlantSpecies.LAVENDER,
                com.anchor.adhd.domain.PlantSpecies.MUSHROOM
            ),
            vitalityProgress = 0.55f
        )
    }
}

@Preview(name = "Vitality bar", showBackground = true, backgroundColor = 0xFF0A0E18, widthDp = 412)
@Composable
private fun VitalityBarPreview() {
    AnchorTheme {
        VitalityBar(weeklyFocusSessions = 5, modifier = Modifier.padding(16.dp))
    }
}

@Preview(name = "Today — sample", showBackground = true, backgroundColor = 0xFF080B10, widthDp = 412, heightDp = 892)
@Composable
private fun TodaySamplePreview() {
    AnchorTheme {
        AnchorScreenBackground(Modifier.fillMaxSize()) {
            TodaySampleContent()
        }
    }
}

@Preview(name = "Cards", showBackground = true, backgroundColor = 0xFF080B10, widthDp = 412)
@Composable
private fun CardStylesPreview() {
    AnchorTheme {
        AnchorScreenBackground(Modifier.fillMaxSize()) {
            Column(Modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AnchorCard(style = AnchorCardStyle.Hero) {
                    SectionHeader("Now", "One thing to do next", accentColor = MaterialTheme.colorScheme.tertiary)
                    StatusChip("Medium", MaterialTheme.colorScheme.secondary)
                    Text("Finish psych reading", style = MaterialTheme.typography.titleLarge)
                    Text("25 min", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AnchorCard(style = AnchorCardStyle.Accent) {
                    SectionHeader("Focus", "Sessions today", accentColor = MaterialTheme.colorScheme.tertiary)
                    Text(AppCopy.focusSessionsToday(2), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AnchorCard {
                    EmptyState("Nothing scheduled. Add a block in Plan.")
                }
            }
        }
    }
}

@Preview(name = "Focus timer", showBackground = true, backgroundColor = 0xFF080B10, widthDp = 412)
@Composable
private fun TimerPreview() {
    AnchorTheme {
        AnchorScreenBackground(Modifier.fillMaxSize()) {
            Column(Modifier.padding(24.dp)) {
                VisualTimerRing(
                    remainingSeconds = 12 * 60 + 34,
                    totalSeconds = 20 * 60,
                    label = "Focus",
                    startedAtMillis = System.currentTimeMillis() - 7 * 60_000L
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodaySampleContent() {
    val sampleTask = TaskEntity(
        id = 1,
        title = "Finish psych reading",
        durationMinutes = 25,
        difficulty = TaskDifficulty.MEDIUM
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(SectionSpacing)
    ) {
        DayProgressBar(0.42f)

        AnchorCard(style = AnchorCardStyle.Hero) {
            SectionHeader("Now", AppCopy.NOW_HINT, accentColor = MaterialTheme.colorScheme.tertiary)
            StatusChip("Medium", MaterialTheme.colorScheme.secondary)
            Text(sampleTask.title, style = MaterialTheme.typography.titleLarge)
            Text("${sampleTask.durationMinutes} min", color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = {}) { Text("Start 10m") }
                FilledTonalButton(onClick = {}) { Text("Done") }
                OutlinedButton(onClick = {}) { Text("Replan") }
            }
        }

        AnchorCard(style = AnchorCardStyle.Accent) {
            SectionHeader("Focus", "Sessions today", accentColor = MaterialTheme.colorScheme.tertiary)
            Text(AppCopy.focusSessionsToday(2), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        DailyCheckInSection(
            activation = EnergyLevel.OK,
            selectedTags = setOf(CheckInTag.FOGGY),
            onActivationChange = {},
            onTagToggle = {}
        )
    }
}
