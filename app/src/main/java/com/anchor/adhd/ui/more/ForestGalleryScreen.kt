package com.anchor.adhd.ui.more

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.anchor.adhd.data.model.PlantedTile
import com.anchor.adhd.domain.GroveLevel
import com.anchor.adhd.domain.PlantCatalog
import com.anchor.adhd.domain.PlantSpecies
import com.anchor.adhd.ui.components.HarborForestGrid
import com.anchor.adhd.ui.components.PlantIllustration
import com.anchor.adhd.ui.components.PlantLod
import com.anchor.adhd.ui.theme.AnchorColors
import com.anchor.adhd.ui.theme.AnchorSpacing
import com.anchor.adhd.ui.vm.AnchorViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForestGalleryScreen(vm: AnchorViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val tiles by vm.plantedTiles.collectAsState()
    val weekly by vm.weeklySummary.collectAsState()
    val groveStage = GroveLevel.vitalityStageForWeeklySessions(weekly.focusSessions)
    var segment by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<PlantedTile?>(null) }
    PredictiveBackHandler { updates ->
        updates.collect { }
        onBack()
    }
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (segment == 0) "${tiles.size} plants" else "Species") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = AnchorSpacing.screenHorizontal, vertical = 8.dp)
        ) {
            SegmentedButton(selected = segment == 0, onClick = { segment = 0 }, shape = MaterialTheme.shapes.medium) {
                Text("Forest")
            }
            SegmentedButton(selected = segment == 1, onClick = { segment = 1 }, shape = MaterialTheme.shapes.medium) {
                Text("Species")
            }
        }
        if (segment == 0) {
            HarborForestGrid(
                tiles = tiles,
                onTileClick = { selected = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = AnchorSpacing.screenHorizontal)
            )
        } else {
            SpeciesCatalog(
                groveStage = groveStage,
                modifier = Modifier.weight(1f).padding(horizontal = AnchorSpacing.screenHorizontal)
            )
        }
    }
    selected?.let { tile ->
        val zone = ZoneId.systemDefault()
        val date = Instant.ofEpochMilli(tile.plantedAtMillis).atZone(zone).format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
        val minutes = tile.actualMinutes ?: tile.plannedMinutes
        ModalBottomSheet(
            onDismissRequest = { selected = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(Modifier.padding(AnchorSpacing.screenHorizontal), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(PlantSpecies.fromTreeType(tile.treeType).displayName, style = MaterialTheme.typography.titleLarge)
                Text(date, color = MaterialTheme.colorScheme.onSurfaceVariant)
                minutes?.let { Text("$it min", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                tile.taskTitle?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun SpeciesCatalog(groveStage: Int, modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(PlantCatalog.all, key = { it.species }) { entry ->
            val unlocked = PlantCatalog.isUnlocked(entry.species, groveStage)
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(if (unlocked) 1f else 0.35f)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(MaterialTheme.shapes.medium)
                        .background(AnchorColors.HarborDock),
                    contentAlignment = Alignment.Center
                ) {
                    PlantIllustration(
                        entry.species,
                        growth = if (unlocked) 1f else 0.45f,
                        lod = PlantLod.GRID,
                        modifier = Modifier.fillMaxSize().padding(8.dp)
                    )
                }
                Text(entry.species.displayName, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
