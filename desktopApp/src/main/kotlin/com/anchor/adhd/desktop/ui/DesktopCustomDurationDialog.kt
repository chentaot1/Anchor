package com.anchor.adhd.desktop.ui

import androidx.compose.runtime.Composable
import com.anchor.adhd.ui.components.CustomDurationDialog
import com.anchor.adhd.desktop.DesktopDurationLimits

/**
 * Custom Duration Picker Dialog for Windows 11.
 * Directly leverages the shared Compose Multiplatform CustomDurationDialog.
 */
@Composable
fun DesktopCustomDurationDialog(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    title: String = "Custom Focus Length",
    confirmLabel: String = "Set Timer",
) {
    CustomDurationDialog(
        initialMinutes = initialMinutes,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        maxMinutes = DesktopDurationLimits.MAX_MINUTES,
        title = title,
        confirmLabel = confirmLabel,
    )
}
