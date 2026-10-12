package com.anchor.adhd.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.anchor.adhd.desktop.blocker.BlockerDetection
import com.anchor.adhd.desktop.blocker.DesktopRescueOverlay
import com.anchor.adhd.desktop.db.AnchorDesktopDatabase
import com.anchor.adhd.desktop.db.DesktopThoughtCapture
import com.anchor.adhd.desktop.theme.AnchorTheme

/** Production rescue UI/controller with disposable SQLite; no browser or foreground-window actions. */
internal fun launchRescueScribeSmoke(size: DpSize) = application {
    val scope = rememberCoroutineScope()
    val db = remember { AnchorDesktopDatabase(scope) }
    var showRescue by remember { mutableStateOf(true) }
    var ready by remember { mutableStateOf(false) }
    var returns by remember { mutableStateOf(0) }
    val capture = remember {
        DesktopThoughtCapture(scope, allowed = { ready }, actionLabel = "Scribe", onSaved = {
            returns++
            showRescue = false
        }) { request -> db.captureThought(request.key, request.text) }
    }
    val state by capture.state.collectAsState()
    LaunchedEffect(Unit) { db.initialize(); ready = true }
    Window(onCloseRequest = ::exitApplication, state = rememberWindowState(width = size.width, height = size.height), title = "Anchor - Rescue Scribe Smoke") {
        AnchorTheme {
            if (showRescue) {
                DesktopRescueOverlay(
                    detection = BlockerDetection(true, "Disposable distraction", "Smoke fixture"),
                    taskTitle = "Verify retained Scribe thoughts",
                    remainingMinutes = 25,
                    onReturnToFocus = { showRescue = false },
                    onEmergencyPass = { showRescue = false },
                    thoughtCaptureState = state,
                    onThoughtDraftChange = capture::edit,
                    onCaptureThought = { capture.submit() },
                )
            } else {
                Column(Modifier.fillMaxSize().padding(24.dp)) {
                    Text("Acknowledged returns: $returns")
                    Text(state.feedback ?: "Rescue dismissed; the draft remains owned by the application.")
                    Button(onClick = { showRescue = true }) { Text("Reopen rescue") }
                }
            }
        }
    }
}
