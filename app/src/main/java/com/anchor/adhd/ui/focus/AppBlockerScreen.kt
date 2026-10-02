package com.anchor.adhd.ui.focus

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.anchor.adhd.data.AnchorContainer
import com.anchor.adhd.domain.*
import com.anchor.adhd.service.FocusBlockService
import com.anchor.adhd.service.FocusTimerState
import com.anchor.adhd.ui.components.AppPickerSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppBlockerScreen(container: AnchorContainer, onBack: () -> Unit) {
    val repository = container.advancedBlocker
    val saved by repository.state.collectAsState(initial = null)
    val timer by FocusTimerState.state.collectAsState()
    val scope = rememberCoroutineScope()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    val state = saved?.let { AdvancedBlocking.rollover(it, now) }
    var installed by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    LaunchedEffect(Unit) { installed = withContext(Dispatchers.IO) { InstalledApps.loadLaunchable(container.appContext) } }
    var picker by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AppProtection?>(null) }
    var confirmLockdown by remember { mutableStateOf(false) }
    val focusActive = timer.phase == FocusTimerState.Phase.WORK
    val lockdown = (state?.lockdownUntilMillis ?: 0) > now
    val locked = lockdown || focusActive

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("App Blocker") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        })
        if (state == null) { LinearProgressIndicator(Modifier.fillMaxWidth()); return@Column }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                ProtectionCard {
                    Text(if (FocusBlockService.isEnabled()) "Protection connected" else "Finish protection setup", style = MaterialTheme.typography.titleMedium)
                    if (!FocusBlockService.isEnabled()) Text("Enable Anchor in Android Accessibility to block selected apps.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { FocusBlockService.openSettings(container.appContext) }, modifier = Modifier.fillMaxWidth()) { Text("Accessibility settings") }
                    Text("Daily allowances reset at 4 AM.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Text("Selected apps", style = MaterialTheme.typography.titleLarge)
                Button(onClick = { picker = true }, enabled = !locked) { Text("Add app") }
                if (state.apps.isEmpty()) Text("Choose the apps you want to protect or track.")
            }
            items(state.apps, key = { it.packageName }) { app ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(app.label, style = MaterialTheme.typography.titleMedium)
                        Text(modeLabel(app.mode))
                        Text("Used ${AdvancedBlocking.usage(state, app) / 60}m" +
                            if (app.mode == ProtectionMode.QUOTA) " / ${app.dailyMinutes}m" else "")
                        if (app.quotaGroup.isNotBlank()) Text("Shared group: ${app.quotaGroup}", style = MaterialTheme.typography.bodySmall)
                        AdvancedBlocking.decide(state, app.packageName, focusActive, now)?.let { Text(it.reason) }
                        val appLocked = locked || app.lockedUntilMillis > now
                        if (appLocked) Text("Rule changes locked")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { editing = app }, enabled = !appLocked) { Text("Edit") }
                            TextButton(onClick = { scope.launch { repository.removeApp(app.packageName) } }, enabled = !appLocked) { Text("Remove") }
                        }
                    }
                }
            }
            item { ProtectionCard {
                Text("Protection", style = MaterialTheme.typography.titleLarge)
                ProtectionSwitch("Standing shield", "Protect selected apps even outside focus", state.standingShield, !locked) {
                    scope.launch { repository.configure { s -> s.copy(standingShield = it) } }
                }
                ProtectionSwitch("Night curfew", "Protect selected apps overnight", state.curfewEnabled, !locked) {
                    scope.launch { repository.configure { s -> s.copy(curfewEnabled = it) } }
                }
                if (state.curfewEnabled) TimeWindowEditor(state.curfewStartMinute, state.curfewEndMinute, !locked) { start, end ->
                    scope.launch { repository.configure { it.copy(curfewStartMinute = start, curfewEndMinute = end) } }
                }
                ProtectionSwitch("Weekday study window", "Protect selected apps Monday–Friday", state.studyEnabled, !locked) {
                    scope.launch { repository.configure { s -> s.copy(studyEnabled = it) } }
                }
                if (state.studyEnabled) TimeWindowEditor(state.studyStartMinute, state.studyEndMinute, !locked) { start, end ->
                    scope.launch { repository.configure { it.copy(studyStartMinute = start, studyEndMinute = end) } }
                }
                Button(onClick = { confirmLockdown = true }, modifier = Modifier.fillMaxWidth(), enabled = !locked && state.apps.any { it.mode != ProtectionMode.TRACK_ONLY }) {
                    Text(if (lockdown) "Locked until 4 AM" else if (focusActive) "Rules locked during focus" else "Lock protection until 4 AM")
                }
                Text("Track-only rules record usage without blocking. Phone, launcher and system settings stay accessible.", style = MaterialTheme.typography.bodySmall)
            } }
            item { ProtectionCard {
                Text("Earned leisure", style = MaterialTheme.typography.titleLarge)
                Text("${state.bankedMinutes} / 60 minutes banked · ${state.focusMinutes} minutes focused")
                Text("${AdvancedBlocking.nextRewardIn(state)} more focus minutes earns 30 minutes. Costs rise from 60 to 75, 90, then 120 minutes per reward.", style = MaterialTheme.typography.bodySmall)
                if (state.leisureUntilMillis > now) Text("Leisure active: ${((state.leisureUntilMillis - now + 59_999) / 60_000)} minutes left")
                val restriction = AdvancedBlocking.leisureRestriction(state, focusActive, now)
                if (restriction != null) Text("Leisure unavailable: $restriction", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30).forEach { minutes ->
                        OutlinedButton(onClick = { scope.launch { repository.spendLeisure(minutes, focusActive) } },
                            enabled = restriction == null && state.leisureUntilMillis <= now && state.bankedMinutes >= minutes) {
                            Text("Use $minutes min")
                        }
                    }
                }
                Text("Earned leisure opens standing and always-protected apps. Focus, study windows, curfew and exhausted allowances stay protected.", style = MaterialTheme.typography.bodySmall)
            } }
        }
    }
    if (picker) AppPickerSheet(installed, onSelect = { pkg ->
        picker = false
        editing = state?.apps?.find { it.packageName == pkg } ?: AppProtection(pkg, installed.find { it.packageName == pkg }?.label ?: pkg)
    }, onDismiss = { picker = false })
    editing?.let { app -> AppProtectionEditor(app, onDismiss = { editing = null }, onSave = { updated, freeze ->
        scope.launch { repository.saveApp(updated); if (freeze) repository.lockApp(updated.packageName) }
        editing = null
    }) }
    if (confirmLockdown) AlertDialog(onDismissRequest = { confirmLockdown = false }, title = { Text("Lock until 4 AM?") },
        text = { Text("Selected protected apps will stay blocked until the next 4 AM reset. App rules and protection settings cannot be changed here during lockdown. Leisure and emergency passes cannot bypass it.") },
        confirmButton = { TextButton(onClick = { scope.launch { repository.lockdown() }; confirmLockdown = false }) { Text("Lock until 4 AM") } },
        dismissButton = { TextButton(onClick = { confirmLockdown = false }) { Text("Cancel") } })
}

private fun modeLabel(mode: ProtectionMode) = when (mode) {
    ProtectionMode.FOCUS -> "During focus"; ProtectionMode.ALWAYS -> "Always protected"
    ProtectionMode.QUOTA -> "Daily allowance"; ProtectionMode.TRACK_ONLY -> "Track only"
}

@Composable
private fun ProtectionCard(content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun ProtectionSwitch(label: String, detail: String, checked: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) { Text(label); Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(checked = checked, onCheckedChange = change, enabled = enabled)
    }
}

private fun timeLabel(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)
private fun parseTime(text: String): Int? = runCatching { LocalTime.parse(text).let { it.hour * 60 + it.minute } }.getOrNull()

@Composable
private fun TimeWindowEditor(startMinute: Int, endMinute: Int, enabled: Boolean, save: (Int, Int) -> Unit) {
    var start by remember(startMinute) { mutableStateOf(timeLabel(startMinute)) }
    var end by remember(endMinute) { mutableStateOf(timeLabel(endMinute)) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(start, { start = it.take(5) }, label = { Text("Start HH:mm") }, singleLine = true, enabled = enabled, modifier = Modifier.weight(1f))
        OutlinedTextField(end, { end = it.take(5) }, label = { Text("End HH:mm") }, singleLine = true, enabled = enabled, modifier = Modifier.weight(1f))
    }
    Text("Equal times protect all day.", style = MaterialTheme.typography.bodySmall)
    TextButton(onClick = { save(parseTime(start)!!, parseTime(end)!!) }, enabled = enabled && parseTime(start) != null && parseTime(end) != null) { Text("Save window") }
}

@Composable
private fun AppProtectionEditor(app: AppProtection, onDismiss: () -> Unit, onSave: (AppProtection, Boolean) -> Unit) {
    var mode by remember(app) { mutableStateOf(app.mode) }
    var minutes by remember(app) { mutableStateOf(app.dailyMinutes.toString()) }
    var group by remember(app) { mutableStateOf(app.quotaGroup) }
    var freeze by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(app.label) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ProtectionMode.entries.forEach { candidate ->
                Row { RadioButton(selected = mode == candidate, onClick = { mode = candidate }); TextButton(onClick = { mode = candidate }) { Text(modeLabel(candidate)) } }
            }
            if (mode == ProtectionMode.QUOTA) {
                OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(4) }, label = { Text("Daily minutes (0–1440)") }, singleLine = true)
                OutlinedTextField(group, { group = it.take(40) }, label = { Text("Shared group (optional)") }, singleLine = true)
                Text("Apps in the same group share usage. Set matching allowances for those apps.", style = MaterialTheme.typography.bodySmall)
            }
            ProtectionSwitch("Freeze rule for 24 hours", "Prevents editing or removing this rule", freeze, true) { freeze = it }
        }
    }, confirmButton = { TextButton(onClick = { onSave(app.copy(mode = mode, dailyMinutes = minutes.toIntOrNull() ?: 30,
        quotaGroup = if (mode == ProtectionMode.QUOTA) group else ""), freeze) },
        enabled = mode != ProtectionMode.QUOTA || minutes.toIntOrNull() in 0..1440) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
