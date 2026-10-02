package com.anchor.adhd.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import java.time.LocalTime

enum class TimeOfDay {
    DAWN, DAY, DUSK, NIGHT;

    val isNight: Boolean get() = this == NIGHT
}

fun currentTimeOfDay(hour: Int = LocalTime.now().hour): TimeOfDay = when (hour) {
    in 5..8 -> TimeOfDay.DAWN
    in 9..16 -> TimeOfDay.DAY
    in 17..20 -> TimeOfDay.DUSK
    else -> TimeOfDay.NIGHT
}

fun TimeOfDay.skyBrush(): Brush = when (this) {
    TimeOfDay.DAWN -> Brush.verticalGradient(
        listOf(Color(0xFF2A1C28), Color(0xFF5C3A3A), Color(0xFFE8B48A), Color(0xFF7A9BB8))
    )
    TimeOfDay.DAY -> Brush.verticalGradient(
        listOf(Color(0xFF1A2A44), Color(0xFF3A5A82), AnchorColors.HarborPrimary.copy(alpha = 0.45f))
    )
    TimeOfDay.DUSK -> Brush.verticalGradient(
        listOf(AnchorColors.HarborBackground, Color(0xFF1A2A4A), Color(0xFF3D2A48), AnchorColors.HarborAi.copy(alpha = 0.28f))
    )
    TimeOfDay.NIGHT -> Brush.verticalGradient(
        listOf(Color(0xFF070A10), AnchorColors.HarborSkyNight, Color(0xFF121820))
    )
}
