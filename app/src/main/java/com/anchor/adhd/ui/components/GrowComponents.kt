package com.anchor.adhd.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.anchor.adhd.data.model.PlantedTile
import com.anchor.adhd.domain.PlantSpecies
import com.anchor.adhd.ui.theme.AnchorColors
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HarborForestGrid(
    tiles: List<PlantedTile>,
    onTileClick: (PlantedTile) -> Unit,
    modifier: Modifier = Modifier
) {
    if (tiles.isEmpty()) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "No plants yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    val zone = ZoneId.systemDefault()
    val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy")
    val grouped = tiles.groupBy {
        YearMonth.from(Instant.ofEpochMilli(it.plantedAtMillis).atZone(zone))
    }
    val rows = grouped.entries.toList()
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEach { (month, monthTiles) ->
            item(span = { GridItemSpan(4) }, key = "m-${month}") {
                Text(
                    month.format(monthFmt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }
            items(monthTiles, key = { it.gardenId }) { tile ->
                Box(
                    Modifier
                        .aspectRatio(1f)
                        .clip(MaterialTheme.shapes.medium)
                        .background(AnchorColors.HarborDock)
                        .clickable { onTileClick(tile) },
                    contentAlignment = Alignment.Center
                ) {
                    PlantIllustration(
                        species = PlantSpecies.fromTreeType(tile.treeType),
                        growth = 1f,
                        lod = PlantLod.GRID,
                        modifier = Modifier.fillMaxSize().padding(6.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun WeeklyMirrorCard(
    focusSessions: Int,
    focusMinutes: Int,
    tasksCompleted: Int,
    checkInsLogged: Int,
    replanQueue: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("This week", style = MaterialTheme.typography.titleMedium)
        StatRow("Focus sessions", "$focusSessions")
        StatRow("Focus minutes", "$focusMinutes")
        StatRow("Tasks completed", "$tasksCompleted")
        StatRow("Check-ins", "$checkInsLogged")
        StatRow("Replan queue", "$replanQueue")
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
