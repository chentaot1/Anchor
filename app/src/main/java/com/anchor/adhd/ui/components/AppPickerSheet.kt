package com.anchor.adhd.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anchor.adhd.domain.InstalledApp
import com.anchor.adhd.domain.WhitelistBasics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerSheet(
    apps: List<InstalledApp>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        val selectable = apps.filterNot { it.packageName in WhitelistBasics.essentialPackages }
        if (query.isBlank()) selectable
        else selectable.filter {
            it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search apps") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
        LazyColumn(modifier = Modifier.padding(bottom = 24.dp)) {
            items(filtered.take(250), key = { it.packageName }) { app ->
                Text(
                    "${app.label}\n${app.packageName}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelect(app.packageName)
                            onDismiss()
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    }
}
