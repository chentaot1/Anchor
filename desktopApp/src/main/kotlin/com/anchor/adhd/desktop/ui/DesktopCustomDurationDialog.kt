package com.anchor.adhd.desktop.ui

import androidx.compose.runtime.Composable
import com.anchor.adhd.ui.components.CustomDurationDialog

/**
 * Custom Duration Picker Dialog for Windows 11.
 * Directly leverages the shared Compose Multiplatform CustomDurationDialog.
 */
@Composable
fun DesktopCustomDurationDialog(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    CustomDurationDialog(
        initialMinutes = initialMinutes,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}
