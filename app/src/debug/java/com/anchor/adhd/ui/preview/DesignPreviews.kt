package com.anchor.adhd.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.anchor.adhd.ui.components.AnchorBeaconPlate
import com.anchor.adhd.ui.components.AnchorChip
import com.anchor.adhd.ui.components.AnchorListRow
import com.anchor.adhd.ui.components.AnchorQuietButton
import com.anchor.adhd.ui.components.AnchorSectionHeader
import com.anchor.adhd.ui.components.AnchorTodayTideStrip
import com.anchor.adhd.ui.components.AnchorWell
import com.anchor.adhd.ui.components.HarborCtaButton
import com.anchor.adhd.ui.components.HarborEmptyState
import com.anchor.adhd.ui.focus.FocusCanvas
import com.anchor.adhd.ui.theme.AnchorSpacing
import com.anchor.adhd.ui.theme.AnchorTheme
import com.anchor.adhd.ui.theme.LocalAnchorTokens

@Preview(name = "Home Next Action — Morning Harbor", showBackground = true, backgroundColor = 0xFFF6F1E7, widthDp = 412, heightDp = 640)
@Composable
private fun BeaconPlateMorningPreview() {
    AnchorTheme(darkTheme = false) {
        HomePreviewContent()
    }
}

@Preview(name = "Home Next Action — Night Harbor", showBackground = true, backgroundColor = 0xFF0E1624, widthDp = 412, heightDp = 640)
@Composable
private fun BeaconPlateNightPreview() {
    AnchorTheme(darkTheme = true) {
        HomePreviewContent()
    }
}

@Preview(name = "Living Tide Focus — Morning Harbor", showBackground = true, backgroundColor = 0xFFF6F1E7, widthDp = 412, heightDp = 360)
@Composable
private fun FocusCanvasMorningPreview() {
    AnchorTheme(darkTheme = false) {
        FocusCanvasPreviewContent()
    }
}

@Preview(name = "Living Tide Focus — Night Harbor", showBackground = true, backgroundColor = 0xFF0E1624, widthDp = 412, heightDp = 360)
@Composable
private fun FocusCanvasNightPreview() {
    AnchorTheme(darkTheme = true) {
        FocusCanvasPreviewContent()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomePreviewContent() {
    val tokens = LocalAnchorTokens.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.paper)
            .verticalScroll(rememberScrollState())
            .padding(AnchorSpacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(AnchorSpacing.sectionGap)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Good morning", style = MaterialTheme.typography.headlineMedium, color = tokens.ink)
            Text("2 sessions today, 45 min focused", style = MaterialTheme.typography.bodyMedium, color = tokens.muted)
        }

        AnchorTodayTideStrip(
            todayMinutes = 45,
            goalMinutes = 50,
            stageLabel = "Growing"
        )

        AnchorBeaconPlate(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Next action",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = tokens.beaconPlateCta
            )
            Text(
                text = "Finish psychology reading chapter 4",
                style = MaterialTheme.typography.titleMedium,
                color = tokens.beaconPlateInk
            )
            Text(
                text = "Open notes to section 4.2 and summarize key terms.",
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.beaconPlateInk2
            )
            HarborCtaButton(
                onClick = {},
                onBeaconPlate = true,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start 25 min focus")
            }
            AnchorQuietButton(
                label = "Break down with Captain",
                onClick = {},
                outlined = false,
                tint = tokens.beaconPlateCaptain
            )
        }

        AnchorWell(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AnchorSectionHeader("Quick start", trailingLabel = "Session length")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AnchorChip(label = "3m", selected = false, onClick = {})
                AnchorChip(label = "15m", selected = false, onClick = {})
                AnchorChip(label = "25m", selected = true, onClick = {})
                AnchorChip(label = "50m", selected = false, onClick = {})
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AnchorSectionHeader("Today's plan", trailingLabel = "Inbox")
            AnchorListRow(
                title = "Draft lab report introduction",
                trailingText = "20m",
                showDivider = true
            )
            AnchorListRow(
                title = "Email TA about section swap",
                trailingText = "10m",
                showDivider = false
            )
        }

        HarborEmptyState(
            title = "All clear on deck",
            message = "Capture a thought whenever you are ready."
        )
    }
}

@Composable
private fun FocusCanvasPreviewContent() {
    val tokens = LocalAnchorTokens.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.paper)
            .padding(24.dp)
    ) {
        FocusCanvas(
            progress = 0.58f,
            isWorkPhase = true,
            remainingSeconds = 10 * 60 + 24,
            taskTitle = "Finish psychology reading",
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
        )
    }
}
