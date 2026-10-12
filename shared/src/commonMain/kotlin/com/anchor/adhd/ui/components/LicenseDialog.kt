package com.anchor.adhd.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp

/** Offline legal notices, with a link to this edition's source and build instructions. */
@Composable
fun LicenseDialog(readNotice: (String) -> String, onDismiss: () -> Unit) {
    var sdkPermission by remember { mutableStateOf(false) }
    var thirdParty by remember { mutableStateOf(false) }
    val text = remember(thirdParty, sdkPermission) {
        readNotice(if (sdkPermission) "ANDROID_SDK_EXCEPTION.txt" else if (thirdParty) "THIRD_PARTY_NOTICES.txt" else "AGPL-3.0-only.txt")
    }
    val chunks = remember(text) { text.chunked(4000) }
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (sdkPermission) "Android SDK linking permission" else if (thirdParty) "Third-party notices" else "Anchor • AGPLv3 + SDK permission") },
        text = {
            Column {
                Text("Copyright © 2026 chentaot1. AGPL version 3 with an Android SDK linking permission. No warranty.")
                TextButton(onClick = { sdkPermission = false; thirdParty = !thirdParty }) {
                    Text(if (thirdParty) "Read AGPLv3" else "Read third-party notices")
                }
                TextButton(onClick = { sdkPermission = true }) {
                    Text("Android SDK linking permission and vendor terms")
                }
                TextButton(onClick = { uriHandler.openUri("https://github.com/chentaot1/Anchor/tree/codex/vendor-integrations") }) {
                    Text("Source code and build instructions")
                }
                SelectionContainer {
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(chunks) { chunk -> Text(chunk, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
