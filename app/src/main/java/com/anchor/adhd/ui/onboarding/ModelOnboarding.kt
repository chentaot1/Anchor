package com.anchor.adhd.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.anchor.adhd.ai.LlamaBridge
import com.anchor.adhd.ai.ModelCatalog
import com.anchor.adhd.ai.ModelDownloadManager
import com.anchor.adhd.data.AnchorContainer
import kotlinx.coroutines.launch

/** A persisted choice; composing this screen never starts a network request. */
@Composable
fun ModelOnboarding(container: AnchorContainer, content: @Composable () -> Unit) {
    val complete by container.preferences.modelOnboardingComplete.collectAsState(initial = null)
    val progress by container.modelDownloadManager.progress.collectAsState(
        initial = ModelDownloadManager.Progress(0, 0, false)
    )
    val scope = rememberCoroutineScope()
    var finishing by remember { mutableStateOf(false) }
    val entry = ModelCatalog.entries.first()
    when (complete) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        true -> content()
        false -> {
            val continueToApp: () -> Unit = {
                if (!finishing) {
                    finishing = true
                    scope.launch {
                        try { container.preferences.completeModelOnboarding() }
                        finally { finishing = false }
                    }
                }
            }
            BackHandler(enabled = !finishing, onBack = continueToApp)
            ModelOnboardingScreen(
                sizeLabel = entry.sizeLabel(),
                progress = progress,
                installed = container.modelDownloadManager.isDownloaded(entry),
                runtimeAvailable = LlamaBridge.isNativeAvailable,
                enoughSpace = container.modelDownloadManager.freeDiskBytes() >=
                    container.modelDownloadManager.requiredFreeBytes(entry),
                finishing = finishing,
                onDownload = { container.modelDownloadManager.startDownload(entry) },
                onPause = { container.modelDownloadManager.pauseDownload() },
                onContinue = continueToApp,
            )
        }
    }
}

@Composable
fun ModelOnboardingScreen(
    sizeLabel: String,
    progress: ModelDownloadManager.Progress,
    installed: Boolean,
    runtimeAvailable: Boolean,
    enoughSpace: Boolean,
    finishing: Boolean = false,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onContinue: () -> Unit,
) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("Welcome to Anchor", style = MaterialTheme.typography.headlineMedium)
            Text("Offline AI, optional", style = MaterialTheme.typography.titleLarge)
            Text("Tasks, timers, habits and basic task breakdowns work without a model. Download the model for tailored AI breakdowns and chat that run on your device.")
            Text("Download: $sizeLabel from Hugging Face. Model: MiniCPM5 1B Q8_0 (Apache 2.0). Allow $sizeLabel of storage plus 500 MB free space. Wi-Fi is recommended.")
            Text("Only the model file is downloaded. Your tasks and conversations stay on your device. No download starts unless you choose it; you can download later in Settings → AI.")
            if (!runtimeAvailable) Text("Local AI is unavailable on this device. You can still use Anchor's planning and focus tools.")
            if (!enoughSpace && !installed && !progress.active) Text("Not enough free storage for the model. Free space or skip for now.", color = MaterialTheme.colorScheme.error)
            if (progress.active) {
                LinearProgressIndicator(
                    progress = { if (progress.totalBytes > 0) (progress.bytesRead.toFloat() / progress.totalBytes).coerceIn(0f, 1f) else 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Downloading model… ${progress.bytesRead / 1_000_000} MB", Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                TextButton(onClick = onPause, enabled = !finishing) { Text("Pause download") }
            }
            progress.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (installed) Text("Model ready for offline AI", color = MaterialTheme.colorScheme.primary)
            OutlinedButton(
                onClick = onDownload,
                enabled = !finishing && !installed && !progress.active && runtimeAvailable && enoughSpace,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
            ) {
                Text(when { installed -> "Model ready"; progress.active -> "Downloading…"; progress.error != null -> "Retry download ($sizeLabel)"; else -> "Download model ($sizeLabel)" })
            }
            OutlinedButton(
                onClick = onContinue,
                enabled = !finishing,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
            ) {
                Text(when { installed -> "Continue"; progress.active -> "Continue while downloading"; else -> "Skip for now" })
            }
        }
    }
}
