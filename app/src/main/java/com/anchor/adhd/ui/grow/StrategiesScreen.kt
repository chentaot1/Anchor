package com.anchor.adhd.ui.grow

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.anchor.adhd.ui.components.AnchorCard
import com.anchor.adhd.ui.components.ScreenPadding
import com.anchor.adhd.ui.components.SectionHeader
import com.anchor.adhd.ui.components.SectionSpacing
import com.anchor.adhd.ui.copy.AppCopy
import com.anchor.adhd.ui.vm.AnchorViewModel

@Composable
fun StrategiesScreen(vm: AnchorViewModel, modifier: Modifier = Modifier) {
    val cards by vm.cbtCards.collectAsState()
    LazyColumn(
        modifier.fillMaxSize().padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(SectionSpacing)
    ) {
        item { SectionHeader(AppCopy.STRATEGY_SECTION, AppCopy.STRATEGY_HINT) }
        items(cards, key = { it.id }) { card ->
            AnchorCard(Modifier.clickable { vm.showCbt(card) }) {
                Text(card.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    card.body.lines().first(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
