package com.anchor.adhd.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Timer
import org.junit.Assert.assertEquals
import org.junit.Test

class AnchorShellTest {
    @Test
    fun anchorTabIndicesRemainStableForIntentsAndWidgets() {
        assertEquals(0, AnchorTabs.GROVE)
        assertEquals(1, AnchorTabs.PLAN)
        assertEquals(2, AnchorTabs.FOCUS)
        assertEquals(3, AnchorTabs.AI)
        assertEquals(4, AnchorTabs.MORE)
    }

    @Test
    fun anchorTabItemsMatchUnifiedDestinationLabelsAndIcons() {
        assertEquals(
            listOf("Home", "Plan", "Focus", "Captain", "More"),
            ANCHOR_TAB_ITEMS.map { it.label },
        )
        assertEquals(
            listOf(
                AnchorTabs.GROVE,
                AnchorTabs.PLAN,
                AnchorTabs.FOCUS,
                AnchorTabs.AI,
                AnchorTabs.MORE,
            ),
            ANCHOR_TAB_ITEMS.map { it.index },
        )
        assertEquals(
            listOf(
                Icons.Outlined.Home,
                Icons.Outlined.CalendarMonth,
                Icons.Outlined.Timer,
                Icons.Outlined.AutoAwesome,
                Icons.Outlined.Apps,
            ),
            ANCHOR_TAB_ITEMS.map { it.icon },
        )
        assertEquals(
            listOf(
                Icons.Filled.Home,
                Icons.Filled.CalendarMonth,
                Icons.Filled.Timer,
                Icons.Filled.AutoAwesome,
                Icons.Filled.Apps,
            ),
            ANCHOR_TAB_ITEMS.map { it.selectedIcon },
        )
    }
}
