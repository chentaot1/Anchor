package com.anchor.adhd.ui.components

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import com.anchor.adhd.health.HealthConnectBridge
import com.anchor.adhd.ui.vm.AnchorViewModel

@Composable
fun HealthConnectButton(vm: AnchorViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted -> vm.onHealthPermissionsResult(granted) }
    OutlinedButton(onClick = {
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> launcher.launch(HealthConnectBridge.permissions)
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                val uri = Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                    .onFailure { vm.healthConnectUnavailable() }
            }
            else -> vm.healthConnectUnavailable()
        }
    }, modifier = modifier) { Text("Connect Health Connect") }
}
