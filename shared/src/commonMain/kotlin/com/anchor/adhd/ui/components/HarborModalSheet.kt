package com.anchor.adhd.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.anchor.adhd.ui.theme.AnchorGradients

/**
 * Premium sheet shared by Android and desktop: tonal container, 28dp top
 * rounding, harbor-tinted scrim, and a beacon-gradient drag handle.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun HarborModalSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = Color(0x990E1624),
        dragHandle = {
            Box(
                Modifier
                    .size(width = 48.dp, height = 5.dp)
                    .background(AnchorGradients.beaconCta(), RoundedCornerShape(3.dp))
            )
        },
        content = content
    )
}
