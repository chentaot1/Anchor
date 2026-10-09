package com.anchor.adhd.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedCard
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.anchor.adhd.ui.components.AppPickerSheet
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.anchor.adhd.data.AnchorContainer
import com.anchor.adhd.data.model.BlockListMode
import com.anchor.adhd.data.model.BlockRuleType
import com.anchor.adhd.data.model.BreakdownGranularity
import com.anchor.adhd.service.FocusBlockService
import com.anchor.adhd.domain.InstalledApp
import com.anchor.adhd.domain.InstalledApps
import com.anchor.adhd.ai.LocalAiEngine
import com.anchor.adhd.ai.LlamaBridge
import com.anchor.adhd.ai.ModelCatalogEntry
import com.anchor.adhd.calendar.GoogleSignInBridge
import com.anchor.adhd.ui.copy.AppCopy
import com.anchor.adhd.ui.vm.AnchorViewModel

private fun BreakdownGranularity.settingsLabel(): String = when (this) {
    BreakdownGranularity.MILD -> "Mild (2–3)"
    BreakdownGranularity.NORMAL -> "Normal (3–5)"
    BreakdownGranularity.SPICY -> "Spicy (5–7)"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    vm: AnchorViewModel,
    container: AnchorContainer,
    onBack: () -> Unit
) {
    var section by rememberSaveable { mutableStateOf("Focus") }
    val settingsList = rememberLazyListState()
    LaunchedEffect(section) { settingsList.scrollToItem(0) }
    val blockRules by vm.blockRules.collectAsState()
    val bundles by vm.temptationBundles.collectAsState()
    val shieldActive by vm.scheduledShieldActive.collectAsState()
    val download by vm.downloadProgress.collectAsState()
    val activeModelId by vm.activeModelEntryId.collectAsState()
    val benchmark by vm.benchmarkResult.collectAsState()
    val benchmarkRunning by vm.benchmarkRunning.collectAsState()
    val modelReady by vm.modelDownloaded.collectAsState()
    val breakdownGranularity by vm.breakdownGranularity.collectAsState()
    val aiJobs by vm.aiJobs.collectAsState()
    var newPackage by remember { mutableStateOf("") }
    var bundleName by remember { mutableStateOf("") }
    var bundlePackage by remember { mutableStateOf("") }
    var shieldPackage by remember { mutableStateOf("") }
    var shieldStart by remember { mutableStateOf("9") }
    var shieldEnd by remember { mutableStateOf("17") }
    var showResetDialog by remember { mutableStateOf(false) }
    var showClearChatDialog by remember { mutableStateOf(false) }
    var showClearAiMemoryDialog by remember { mutableStateOf(false) }
    var showClearFocusDialog by remember { mutableStateOf(false) }
    var deleteModelOnReset by remember { mutableStateOf(true) }
    var showAppPicker by remember { mutableStateOf(false) }
    var workMin by remember { mutableStateOf("20") }
    var breakMin by remember { mutableStateOf("5") }
    var quietStart by remember { mutableStateOf("22") }
    var quietEnd by remember { mutableStateOf("7") }
    var replanHour by remember { mutableStateOf("8") }
    val context = LocalContext.current
    val activity = context as android.app.Activity
    var installedApps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    val healthConnected by vm.samsungHealthConnected.collectAsState()
    val healthAvailable = remember { container.samsungHealth.isAvailable() }
    val focusDurations by vm.focusDurations.collectAsState()
    val planningSettings by vm.planningSettings.collectAsState()
    val blockListMode by vm.blockListMode.collectAsState()

    LaunchedEffect(Unit) { vm.refreshModelStatus() }
    LaunchedEffect(showAppPicker) {
        if (showAppPicker) installedApps = InstalledApps.loadLaunchable(context)
    }

    val downloading = download.totalBytes > 0 && !download.done && download.error == null

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        GoogleSignInBridge.getSignedInAccountFromIntent(result.data)
            .addOnSuccessListener { vm.importGoogleCalendar {} }
    }

    var calendarGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        calendarGranted = granted
        vm.syncDeviceCalendar()
    }

    val backupImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            vm.importBackup(uri)
        }
    }

    BackHandler(enabled = showResetDialog) { showResetDialog = false }
    PredictiveBackHandler(enabled = !showResetDialog) { updates ->
        updates.collect { }
        onBack()
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset everything?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Deletes all tasks, focus history, check-ins, block rules, routines, calendar imports, and settings. Default routines and blocklist are restored. This cannot be undone.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = deleteModelOnReset,
                            onCheckedChange = { deleteModelOnReset = it }
                        )
                        Text("Also remove the installed local model (1.15 GB)")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetDialog = false
                        vm.resetAllAppData(deleteModelOnReset)
                        onBack()
                    }
                ) { Text("Reset") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            state = settingsList,
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "categories") {
                Text("Choose a category", color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Focus", "AI", "Planning", "Reminders", "Connections", "Data", "Advanced").forEach { category ->
                        FilterChip(selected = section == category, onClick = { section = category }, label = { Text(category) })
                    }
                }
            }
            if (section == "Focus") item(key = "app-protection") {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("App protection", style = MaterialTheme.typography.titleMedium)
                        Text("Manage protected apps, daily allowances, curfew and earned leisure.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { vm.openAppBlocker() }, modifier = Modifier.fillMaxWidth()) { Text("Open App Blocker") }
                    }
                }
            }
            if (section == "Reminders") item(key = "settings-0") {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Reminders", style = MaterialTheme.typography.titleMedium)
                val reminderSettings by vm.reminderSettings.collectAsState()
                val scheduleInitialized = remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    if (!scheduleInitialized.value) {
                        replanHour = reminderSettings.morningReplanHour.toString()
                        quietStart = reminderSettings.quietHoursStart.toString()
                        quietEnd = reminderSettings.quietHoursEnd.toString()
                        scheduleInitialized.value = true
                    }
                }
                RowSwitch(
                    label = "Enable reminders",
                    checked = reminderSettings.enabled,
                    onChecked = { vm.updateReminderSettings(reminderSettings.copy(enabled = it)) }
                )
                RowSwitch(
                    label = "Block start reminders",
                    checked = reminderSettings.blockReminders,
                    onChecked = { vm.updateReminderSettings(reminderSettings.copy(blockReminders = it)) }
                )
                RowSwitch(
                    label = "Assignment due reminders",
                    checked = reminderSettings.assignmentReminders,
                    onChecked = { vm.updateReminderSettings(reminderSettings.copy(assignmentReminders = it)) }
                )
                RowSwitch(
                    label = "Morning replan reminder",
                    checked = reminderSettings.morningReplan,
                    onChecked = { vm.updateReminderSettings(reminderSettings.copy(morningReplan = it)) }
                )
                RowSwitch(
                    label = "5-minute focus warning",
                    checked = reminderSettings.timer5MinWarning,
                    onChecked = { vm.updateReminderSettings(reminderSettings.copy(timer5MinWarning = it)) }
                )
                RowSwitch(
                    label = "Timer hard stop (no end early)",
                    checked = reminderSettings.timerHardStop,
                    onChecked = { vm.updateReminderSettings(reminderSettings.copy(timerHardStop = it)) }
                )
                OutlinedTextField(
                    replanHour, { replanHour = it.filter { ch -> ch.isDigit() }.take(2) },
                    label = { Text("Morning replan hour (0–23)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    quietStart, { quietStart = it.filter { ch -> ch.isDigit() }.take(2) },
                    label = { Text("Quiet hours start") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    quietEnd, { quietEnd = it.filter { ch -> ch.isDigit() }.take(2) },
                    label = { Text("Quiet hours end") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Button(
                    onClick = {
                        vm.updateReminderSettings(
                            reminderSettings.copy(
                                morningReplanHour = replanHour.toIntOrNull()?.coerceIn(0, 23) ?: 8,
                                quietHoursStart = quietStart.toIntOrNull()?.coerceIn(0, 23) ?: 22,
                                quietHoursEnd = quietEnd.toIntOrNull()?.coerceIn(0, 23) ?: 7
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Save schedule & quiet hours") }
                    }
                }
            }

            if (section == "Planning") item(key = "settings-1") {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Planning", style = MaterialTheme.typography.titleMedium)
                val capacityHours = planningSettings.dailyCapacityMinutes / 60f
                Text(
                    "Daily capacity: ${planningSettings.dailyCapacityMinutes / 60}h ${planningSettings.dailyCapacityMinutes % 60}m",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = capacityHours,
                    onValueChange = { vm.setDailyCapacity((it * 60).toInt().coerceIn(120, 720)) },
                    valueRange = 2f..12f,
                    steps = 19,
                    modifier = Modifier.fillMaxWidth()
                )
                RowSwitch(
                    label = "Today-only filter (default on Today tab)",
                    checked = planningSettings.todayOnlyMode,
                    onChecked = { vm.setTodayOnlyMode(it) }
                )
                var shutdownHourField by remember(planningSettings.shutdownHour) {
                    mutableStateOf(planningSettings.shutdownHour.toString())
                }
                OutlinedTextField(
                    shutdownHourField,
                    { shutdownHourField = it.filter { ch -> ch.isDigit() }.take(2) },
                    label = { Text("Shutdown hour (17–23)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                RowSwitch(
                    label = "Shutdown reminder notification",
                    checked = planningSettings.shutdownNotificationEnabled,
                    onChecked = { vm.setShutdownNotificationEnabled(it) }
                )
                Button(
                    onClick = {
                        vm.setShutdownHour(shutdownHourField.toIntOrNull()?.coerceIn(17, 23) ?: 21)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Save shutdown time") }
                    }
                }
            }

            if (section == "Advanced") item(key = "settings-2") {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Block mode", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Whitelist mode blocks everything except listed apps during focus.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                RowSwitch(
                    label = "Whitelist mode (allow-list)",
                    checked = blockListMode == BlockListMode.WHITELIST,
                    onChecked = {
                        vm.setBlockListMode(if (it) BlockListMode.WHITELIST else BlockListMode.BLOCKLIST)
                    }
                )
                    }
                }
            }

            if (section == "Advanced") item(key = "settings-3") {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Automations (ADB)", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Trigger Anchor from Tasker, MacroDroid, or shell:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("adb shell am start -a com.anchor.adhd.START_FOCUS -n com.anchor.adhd/.MainActivity")
                Text("adb shell am start -a com.anchor.adhd.OPEN_MORNING_RITUAL -n com.anchor.adhd/.MainActivity")
                Text("adb shell am start -a com.anchor.adhd.CHECK_IN -n com.anchor.adhd/.MainActivity")
                Text("adb shell am start -a com.anchor.adhd.SHUTDOWN -n com.anchor.adhd/.MainActivity")
                    }
                }
            }

            if (section == "Focus") item(key = "settings-4") {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Focus durations", style = MaterialTheme.typography.titleMedium)
                val (work, brk) = focusDurations
                LaunchedEffect(work, brk) {
                    workMin = work.toString()
                    breakMin = brk.toString()
                }
                OutlinedTextField(workMin, { workMin = it.filter { ch -> ch.isDigit() }.take(2) }, label = { Text("Work minutes") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(breakMin, { breakMin = it.filter { ch -> ch.isDigit() }.take(2) }, label = { Text("Break minutes") }, modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = {
                        vm.setFocusDurations(
                            workMin.toIntOrNull()?.coerceIn(5, 90) ?: 20,
                            breakMin.toIntOrNull()?.coerceIn(1, 30) ?: 5
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Save focus durations") }
                    }
                }
            }

            if (section == "Connections") item(key = "settings-5") {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Samsung Health", style = MaterialTheme.typography.titleMedium)
                if (healthAvailable) {
                    Text(
                        if (healthConnected) "Connected — habits with step/exercise auto-rules can sync"
                        else "Connect to auto-complete step and exercise habits from Samsung Health",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { vm.connectSamsungHealth(activity) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Connect Samsung Health") }
                    OutlinedButton(
                        onClick = { vm.syncSamsungHealth() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Sync habits now") }
                } else {
                    Text(
                        "Open or update the Samsung Health app, then return here to connect.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                    }
                }
            }

            if (section == "Focus") item(key = "settings-6") {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Samsung setup", style = MaterialTheme.typography.titleMedium)
                Text("1. Settings → Battery → Anchor → Unrestricted")
                Text("2. Enable Anchor in Accessibility (for app blocking)")
                Text("3. Allow notifications for Replan reminders")
                Button(onClick = { vm.openAccessibilitySettings() }) {
                    Text(if (FocusBlockService.isEnabled()) "Accessibility enabled ✓" else "Open Accessibility settings")
                }
                    }
                }
            }

            if (section == "Advanced") item(key = "settings-7") {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Blocklist", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = newPackage,
                    onValueChange = { newPackage = it },
                    label = { Text("Package name (e.g. com.twitter.android)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { showAppPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Pick from installed apps") }
                Button(
                    onClick = {
                        if (newPackage.isNotBlank()) {
                            vm.addBlockPackage(newPackage)
                            newPackage = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Add package manually") }
                    }
                }
            }

            if (section == "Advanced") items(blockRules, key = { it.id }) { rule ->
                val typeLabel = if (rule.ruleType == BlockRuleType.SCHEDULED) {
                    " [${rule.startHour}:${rule.startMinute ?: 0}–${rule.endHour}:${rule.endMinute ?: 0}]"
                } else ""
                RowSwitch(
                    label = rule.packageName + typeLabel,
                    checked = rule.enabled,
                    onChecked = { vm.toggleBlockRule(rule.id, it) }
                )
            }

            if (section == "Advanced") item(key = "settings-9") {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Scheduled shields", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (shieldActive) "A scheduled shield is active now"
                    else "Block apps during set hours (works without focus timer)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    shieldPackage, { shieldPackage = it },
                    label = { Text("Package name") },
                    modifier = Modifier.fillMaxWidth()
                )
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        shieldStart, { shieldStart = it.filter(Char::isDigit).take(2) },
                        label = { Text("Start hour (0–23)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        shieldEnd, { shieldEnd = it.filter(Char::isDigit).take(2) },
                        label = { Text("End hour (0–23)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                Button(
                    onClick = {
                        if (shieldPackage.isNotBlank()) {
                            vm.addScheduledShield(
                                shieldPackage,
                                (shieldStart.toIntOrNull() ?: 9).coerceIn(0, 23), 0,
                                (shieldEnd.toIntOrNull() ?: 17).coerceIn(0, 23), 0
                            )
                            shieldPackage = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Add scheduled shield") }
                Button(onClick = { vm.refreshShields() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Refresh shields")
                }
                    }
                }
            }

            if (section == "Advanced") item(key = "settings-10") {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Temptation bundles", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Reward app stays blocked until you finish focus, then unlocks for a set time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    bundleName, { bundleName = it },
                    label = { Text("Label (e.g. YouTube)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    bundlePackage, { bundlePackage = it },
                    label = { Text("Reward package name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        if (bundleName.isNotBlank() && bundlePackage.isNotBlank()) {
                            vm.addTemptationBundle(bundleName, bundlePackage, 15)
                            bundleName = ""
                            bundlePackage = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Add bundle (15 min unlock)") }
                    }
                }
            }

            if (section == "Advanced") items(bundles, key = { it.id }) { bundle ->
                RowSwitch(
                    label = "${bundle.name} · ${bundle.rewardPackageName}",
                    checked = bundle.enabled,
                    onChecked = { vm.toggleBundle(bundle.id, it) }
                )
            }

            if (section == "Connections") item(key = "settings-12") {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Device calendar", style = MaterialTheme.typography.titleMedium)
                Text(
                    AppCopy.CALENDAR_OCCUPANCY,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (calendarGranted) AppCopy.CALENDAR_ALLOWED else AppCopy.CALENDAR_DENIED,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Button(
                    onClick = {
                        if (calendarGranted) vm.syncDeviceCalendar()
                        else calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (calendarGranted) "Refresh occupancy" else "Allow calendar access")
                }
                    }
                }
            }

            if (section == "Connections") item(key = "settings-13") {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Google Calendar (read-only)", style = MaterialTheme.typography.titleMedium)
                Button(
                    onClick = {
                        val account = GoogleSignInBridge.getLastSignedInAccount(context)
                        if (account != null) vm.importGoogleCalendar {}
                        else signInLauncher.launch(container.calendarImporter.signInClient().signInIntent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Import this week's events") }
                    }
                }
            }

            if (section == "AI") item(key = "settings-14") {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("On-device AI", style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        modelReady && vm.aiNativeRuntime ->
                            "Ready offline · same model and settings as desktop"
                        modelReady -> "Model on disk; native runtime unavailable"
                        vm.aiNativeRuntime -> "Preparing the model included in this app"
                        else -> "Local AI runtime unavailable on this device"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val freeGb = vm.freeDiskBytes() / 1_000_000_000.0
                Text(
                    "Free storage: ${"%.1f".format(freeGb)} GB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (download.totalBytes > 0 && !download.done && download.error == null) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = {
                            if (download.totalBytes > 0) {
                                download.bytesRead.toFloat() / download.totalBytes
                            } else {
                                0f
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Preparing desktop model…",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                download.error?.let { err ->
                    Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                    }
                }
            }

            if (section == "AI") item(key = "settings-breakdown-granularity") {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Breakdown granularity", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Controls how many grounded micro-steps AI task breakdowns generate.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BreakdownGranularity.entries.forEach { option ->
                                FilterChip(
                                    selected = breakdownGranularity == option,
                                    onClick = { vm.setBreakdownGranularity(option) },
                                    label = { Text(option.settingsLabel()) }
                                )
                            }
                        }
                    }
                }
            }

            if (section == "AI") items(vm.modelCatalogEntries(), key = { it.id }) { entry ->
                ModelCatalogCard(
                    entry = entry,
                    downloaded = vm.isModelEntryDownloaded(entry),
                    active = activeModelId == entry.id,
                    downloading = downloading && download.filename == entry.filename,
                    freeDiskBytes = vm.freeDiskBytes(),
                    onDownload = { vm.downloadModelEntry(entry.id) },
                    onActivate = { vm.setActiveModelEntry(entry.id) },
                )
            }

            if (section == "AI") item(key = "settings-16") {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (vm.aiNativeRuntime) {
                    Text("Matches desktop: 2048-token context, 4 CPU threads, Q8 cache, reasoning off. Chat temperature 0.4; task temperature 0.2.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { vm.runAiBenchmark() },
                        enabled = modelReady && !downloading && !benchmarkRunning,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (benchmarkRunning) "Running benchmark…" else "Benchmark active model")
                    }
                    benchmark?.let { result ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            result.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (result.success) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    }
                }
                    }
                }
            }

            if (section == "Planning") item(key = "settings-17") {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FunLinksSettingsSection(vm = vm)
                    }
                }
            }

            if (section == "Data") item(key = "settings-18") {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Backup", style = MaterialTheme.typography.titleMedium)
                        Button(onClick = { vm.exportBackup() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Export backup JSON")
                        }
                        OutlinedButton(
                            onClick = { backupImportLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Restore backup (JSON)")
                        }
                    }
                }
            }

            if (section == "Data") item(key = "settings-19") {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("AI Memory & Privacy", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Transparent control over on-device AI memory, chat history, and focus logs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showClearChatDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Clear Chat History & Undo Stack")
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { showClearAiMemoryDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Clear AI Prompt Memory & Model Cache")
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { showClearFocusDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Clear Focus Session Records")
                }
                    }
                }
            }

            if (section == "Data") item(key = "settings-20") {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Danger zone", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                Text(
                    "Reset wipes local data and returns the app to a fresh install state.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = { showResetDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reset all app data", color = MaterialTheme.colorScheme.error)
                }
                    }
                }
            }

            if (section == "Advanced") item(key = "settings-21") {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("AI debug (recent jobs)", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            if (section == "Advanced") items(aiJobs.take(10), key = { it.id }) { job ->
                Text("${job.type.name}: ${job.status.name} (${job.durationMs}ms)")
            }
        }
    }

    if (showAppPicker) {
        AppPickerSheet(
            apps = installedApps,
            onSelect = { vm.addBlockPackage(it) },
            onDismiss = { showAppPicker = false }
        )
    }

    if (showClearChatDialog) {
        AlertDialog(
            onDismissRequest = { showClearChatDialog = false },
            title = { Text("Clear Chat History?") },
            text = { Text("This erases all conversation messages and temporary undo actions from your local device database.") },
            confirmButton = {
                Button(
                    onClick = {
                        vm.clearChat()
                        showClearChatDialog = false
                    }
                ) { Text("Clear History") }
            },
            dismissButton = {
                TextButton(onClick = { showClearChatDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showClearAiMemoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearAiMemoryDialog = false },
            title = { Text("Clear AI Memory & Cache?") },
            text = { Text("This purges cached AI jobs, clears remembered task context, and unloads any active model from memory.") },
            confirmButton = {
                Button(
                    onClick = {
                        vm.clearAiMemoryAndCache()
                        showClearAiMemoryDialog = false
                    }
                ) { Text("Clear AI Cache") }
            },
            dismissButton = {
                TextButton(onClick = { showClearAiMemoryDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showClearFocusDialog) {
        AlertDialog(
            onDismissRequest = { showClearFocusDialog = false },
            title = { Text("Clear Focus Records?") },
            text = { Text("This resets your logged focus sessions from the local database. Your task list and settings will remain intact.") },
            confirmButton = {
                Button(
                    onClick = {
                        vm.clearFocusSessionHistory()
                        showClearFocusDialog = false
                    }
                ) { Text("Clear Records") }
            },
            dismissButton = {
                TextButton(onClick = { showClearFocusDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ModelCatalogCard(
    entry: ModelCatalogEntry,
    downloaded: Boolean,
    active: Boolean,
    downloading: Boolean,
    freeDiskBytes: Long,
    onDownload: () -> Unit,
    onActivate: () -> Unit
) {
    val needsSpace = freeDiskBytes < entry.sizeBytes + 500_000_000L
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Desktop main model", style = MaterialTheme.typography.titleMedium)
            Text(entry.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${entry.quant} · ${entry.sizeLabel()} · works offline", style = MaterialTheme.typography.bodySmall)
            if (downloaded) {
                Text(if (active) "Installed and active" else "Installed", color = MaterialTheme.colorScheme.primary)
                if (!active) Button(onClick = onActivate, modifier = Modifier.fillMaxWidth()) { Text("Use desktop model") }
            } else {
                Text(if (needsSpace) "Free ${entry.sizeLabel()} plus 500 MB to install the model." else "Included in this app. Installation normally starts automatically.",
                    color = if (needsSpace) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onDownload, enabled = !downloading && !needsSpace, modifier = Modifier.fillMaxWidth()) {
                    Text(if (downloading) "Preparing model…" else "Retry model installation")
                }
            }
        }
    }
}

@Composable
private fun RowSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
