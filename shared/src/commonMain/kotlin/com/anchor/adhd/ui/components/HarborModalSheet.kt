package com.anchor.adhd.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.anchor.adhd.ui.theme.AnchorSpacing
import com.anchor.adhd.ui.theme.LocalAnchorTokens

/**
 * Tokenized modal sheet shared by Android and desktop: flat surface plate,
 * 24dp top rounding, warm harbor scrim, and a subtle rule drag handle.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun HarborModalSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = LocalAnchorTokens.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier,
        shape = RoundedCornerShape(
            topStart = AnchorSpacing.radiusSheet,
            topEnd = AnchorSpacing.radiusSheet,
        ),
        containerColor = tokens.surface,
        contentColor = tokens.ink,
        scrimColor = tokens.paper.copy(alpha = 0.65f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(tokens.rule, RoundedCornerShape(AnchorSpacing.radiusPill))
                    .clearAndSetSemantics { },
            )
        },
        content = content,
    )
}
