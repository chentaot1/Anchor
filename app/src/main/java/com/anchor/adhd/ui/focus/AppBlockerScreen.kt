package com.anchor.adhd.ui.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anchor.adhd.data.AnchorContainer
import com.anchor.adhd.domain.AdvancedBlocking
import com.anchor.adhd.domain.AppProtection
import com.anchor.adhd.domain.InstalledApp
import com.anchor.adhd.domain.InstalledApps
import com.anchor.adhd.domain.ProtectionMode
import com.anchor.adhd.service.FocusBlockService
import com.anchor.adhd.service.FocusTimerState
import com.anchor.adhd.ui.components.AnchorHairline
import com.anchor.adhd.ui.components.AnchorListRow
import com.anchor.adhd.ui.components.AnchorQuietButton
import com.anchor.adhd.ui.components.AnchorSectionHeader
import com.anchor.adhd.ui.components.AnchorWell
import com.anchor.adhd.ui.components.AppPickerSheet
import com.anchor.adhd.ui.components.HarborCtaButton
import com.anchor.adhd.ui.components.HarborEmptyState
import com.anchor.adhd.ui.theme.LocalAnchorTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppBlockerScreen(container: AnchorContainer, onBack: () -> Unit) {
    val tokens = LocalAnchorTokens.current
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
    var appFilter by remember { mutableStateOf("") }
    val focusActive = timer.phase == FocusTimerState.Phase.WORK
    val lockdown = (state?.lockdownUntilMillis ?: 0) > now
    val locked = lockdown || focusActive
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("App Blocker") },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            },
        )
        if (state == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            return@Column
        }
        val visibleApps = remember(state.apps, appFilter) {
            if (appFilter.isBlank()) state.apps
            else {
                state.apps.filter {
                    it.label.contains(appFilter, ignoreCase = true) ||
                        it.packageName.contains(appFilter, ignoreCase = true) ||
                        it.quotaGroup.contains(appFilter, ignoreCase = true)
                }
            }
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                AnchorWell(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (FocusBlockService.isEnabled()) "Protection connected" else "Finish protection setup",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (!FocusBlockService.isEnabled()) {
                        Text(
                            "Enable Anchor in Android Accessibility to block selected apps.",
                            color = tokens.muted,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    AnchorQuietButton(
                        label = "Accessibility settings",
                        onClick = { FocusBlockService.openSettings(container.appContext) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    )
                    Text(
                        "Daily allowances reset at 4 AM.",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.muted,
                    )
                }
            }
            item {
                AnchorSectionHeader("Selected apps")
                HarborCtaButton(
                    onClick = { picker = true },
                    enabled = !locked,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text("Add app", style = MaterialTheme.typography.labelLarge)
                }
                if (state.apps.isEmpty()) {
                    HarborEmptyState(
                        title = "No apps selected",
                        message = "Choose the apps you want to protect or track.",
                    )
                }
                if (state.apps.isNotEmpty()) {
                    OutlinedTextField(
                        value = appFilter,
                        onValueChange = { appFilter = it },
                        label = { Text("Filter selected apps") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp),
                    )
                }
            }
            if (visibleApps.isNotEmpty()) {
                item {
                    AnchorWell(contentPadding = PaddingValues(0.dp)) {
                        visibleApps.forEachIndexed { index, app ->
                            val usedMin = AdvancedBlocking.usage(state, app) / 60
                            val limitMin = AdvancedBlocking.effectiveDailyMinutes(state, app)
                            val frozen = app.lockedUntilMillis > now
                            val appLocked = locked || frozen
                            val quotaLine =
                                when (app.mode) {
                                    ProtectionMode.QUOTA ->
                                        "${usedMin}m / ${limitMin}m"
                                    else ->
                                        "${usedMin}m used"
                                }
                            val statusBits = buildList {
                                add(modeLabel(app.mode))
                                if (app.quotaGroup.isNotBlank()) add("Group: ${app.quotaGroup}")
                                AdvancedBlocking.decide(state, app.packageName, focusActive, now)?.let { add(it.reason) }
                                when {
                                    frozen -> add("Frozen (${AdvancedBlocking.formatLockRemaining(app.lockedUntilMillis, now)})")
                                    locked -> add("Rule changes locked")
                                }
                            }
                            AnchorListRow(
                                title = app.label,
                                subtitle = statusBits.joinToString(" · "),
                                trailingText = quotaLine,
                                showDivider = index < visibleApps.lastIndex,
                                minHeight = 48.dp,
                                trailing = {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        AnchorQuietButton(
                                            label = "Edit",
                                            onClick = { editing = app },
                                            enabled = !appLocked,
                                            outlined = false,
                                            modifier = Modifier.heightIn(min = 48.dp),
                                        )
                                        AnchorQuietButton(
                                            label = "Remove",
                                            onClick = { scope.launch { repository.removeApp(app.packageName) } },
                                            enabled = !appLocked,
                                            outlined = false,
                                            tint = tokens.clayRose,
                                            modifier = Modifier.heightIn(min = 48.dp),
                                        )
                                    }
                                },
                            )
                            if (app.mode == ProtectionMode.QUOTA && limitMin > 0) {
                                val progress = (usedMin.toFloat() / limitMin.toFloat()).coerceIn(0f, 1f)
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                    color = if (usedMin >= limitMin) tokens.clayRose else tokens.primary,
                                    trackColor = tokens.rule,
                                )
                            }
                        }
                    }
                }
            }
            item {
                AnchorWell {
                    AnchorSectionHeader("Protection")
                    ProtectionSwitch("Standing shield", "Protect selected apps even outside focus", state.standingShield, !locked) {
                        scope.launch { repository.configure { s -> s.copy(standingShield = it) } }
                    }
                    AnchorHairline()
                    ProtectionSwitch("Night curfew", "Protect selected apps overnight", state.curfewEnabled, !locked) {
                        scope.launch { repository.configure { s -> s.copy(curfewEnabled = it) } }
                    }
                    if (state.curfewEnabled) {
                        TimeWindowEditor(state.curfewStartMinute, state.curfewEndMinute, !locked) { start, end ->
                            scope.launch { repository.configure { it.copy(curfewStartMinute = start, curfewEndMinute = end) } }
                        }
                    }
                    AnchorHairline()
                    ProtectionSwitch("Weekday study window", "Protect selected apps Monday–Friday", state.studyEnabled, !locked) {
                        scope.launch { repository.configure { s -> s.copy(studyEnabled = it) } }
                    }
                    if (state.studyEnabled) {
                        TimeWindowEditor(state.studyStartMinute, state.studyEndMinute, !locked) { start, end ->
                            scope.launch { repository.configure { it.copy(studyStartMinute = start, studyEndMinute = end) } }
                        }
                    }
                    HarborCtaButton(
                        onClick = { confirmLockdown = true },
                        enabled = !locked && state.apps.any { it.mode != ProtectionMode.TRACK_ONLY },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        val lockLabel =
                            when {
                                lockdown -> "Locked until 4 AM"
                                focusActive -> "Rules locked during focus"
                                else -> "Lock protection until 4 AM"
                            }
                        Text(lockLabel, style = MaterialTheme.typography.labelLarge)
                    }
                    Text(
                        "Track-only rules record usage without blocking. Phone, launcher and system settings stay accessible.",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.muted,
                    )
                }
            }
            item {
                AnchorWell {
                    AnchorSectionHeader("Earned leisure")
                    Text(
                        "${state.bankedMinutes} / 60 minutes banked · ${state.focusMinutes} minutes focused",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                    )
                    Text(
                        "${AdvancedBlocking.nextRewardIn(state)} more focus minutes earns 30 minutes. Costs rise from 60 to 75, 90, then 120 minutes per reward.",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.muted,
                    )
                    if (state.leisureUntilMillis > now) {
                        Text(
                            "Leisure active: ${((state.leisureUntilMillis - now + 59_999) / 60_000)} minutes left",
                            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                            color = tokens.sage,
                        )
                    }
                    val restriction = AdvancedBlocking.leisureRestriction(state, focusActive, now)
                    if (restriction != null) {
                        Text("Leisure unavailable: $restriction", style = MaterialTheme.typography.bodySmall, color = tokens.clayRose)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15, 30).forEach { minutes ->
                            val chargedMinutes = AdvancedBlocking.leisureSpendMinutes(state, minutes, now)
                            AnchorQuietButton(
                                label = "${if (state.leisureUntilMillis > now) "Add" else "Use"} $chargedMinutes min",
                                onClick = { scope.launch { repository.spendLeisure(minutes, focusActive) } },
                                enabled = restriction == null && chargedMinutes > 0 && state.bankedMinutes >= chargedMinutes,
                                modifier = Modifier.heightIn(min = 48.dp),
                            )
                        }
                    }
                    Text(
                        "Earned leisure opens standing and always-protected apps. Focus, study windows, curfew and exhausted allowances stay protected.",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.muted,
                    )
                }
            }
        }
    }
    if (picker) {
        AppPickerSheet(
            installed,
            onSelect = { pkg ->
                picker = false
                editing =
                    state?.apps?.find { it.packageName == pkg }
                        ?: AppProtection(pkg, installed.find { it.packageName == pkg }?.label ?: pkg)
            },
            onDismiss = { picker = false },
        )
    }
    editing?.let { app ->
        AppProtectionEditor(
            app,
            onDismiss = { editing = null },
            onSave = { updated, freeze ->
                scope.launch {
                    repository.saveApp(updated)
                    if (freeze) repository.lockApp(updated.packageName)
                }
                editing = null
            },
        )
    }
    if (confirmLockdown) {
        AlertDialog(
            onDismissRequest = { confirmLockdown = false },
            title = { Text("Lock until 4 AM?") },
            text = {
                Text(
                    "Selected protected apps will stay blocked until the next 4 AM reset. App rules and protection settings cannot be changed here during lockdown. Leisure and emergency passes cannot bypass it.",
                )
            },
            confirmButton = {
                TextButton(onClick = { scope.launch { repository.lockdown() }; confirmLockdown = false }) {
                    Text("Lock until 4 AM")
                }
            },
            dismissButton = { TextButton(onClick = { confirmLockdown = false }) { Text("Cancel") } },
        )
    }
}

private fun modeLabel(mode: ProtectionMode) =
    when (mode) {
        ProtectionMode.FOCUS -> "During focus"
        ProtectionMode.ALWAYS -> "Always protected"
        ProtectionMode.QUOTA -> "Daily allowance"
        ProtectionMode.TRACK_ONLY -> "Track only"
    }

@Composable
private fun ProtectionSwitch(label: String, detail: String, checked: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    val tokens = LocalAnchorTokens.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(label)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = tokens.muted)
        }
        Switch(checked = checked, onCheckedChange = change, enabled = enabled)
    }
}

private fun timeLabel(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

private fun parseTime(text: String): Int? =
    runCatching {
        LocalTime.parse(text).let { it.hour * 60 + it.minute }
    }.getOrNull()

@Composable
private fun TimeWindowEditor(startMinute: Int, endMinute: Int, enabled: Boolean, save: (Int, Int) -> Unit) {
    var start by remember(startMinute) { mutableStateOf(timeLabel(startMinute)) }
    var end by remember(endMinute) { mutableStateOf(timeLabel(endMinute)) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            start,
            { start = it.take(5) },
            label = { Text("Start HH:mm") },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
        )
        OutlinedTextField(
            end,
            { end = it.take(5) },
            label = { Text("End HH:mm") },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
        )
    }
    Text("Equal times protect all day.", style = MaterialTheme.typography.bodySmall)
    TextButton(
        onClick = { save(parseTime(start)!!, parseTime(end)!!) },
        enabled = enabled && parseTime(start) != null && parseTime(end) != null,
        modifier = Modifier.heightIn(min = 48.dp),
    ) {
        Text("Save window")
    }
}

@Composable
private fun AppProtectionEditor(app: AppProtection, onDismiss: () -> Unit, onSave: (AppProtection, Boolean) -> Unit) {
    val tokens = LocalAnchorTokens.current
    var mode by remember(app.packageName) { mutableStateOf(app.mode) }
    var minutes by remember(app.packageName) { mutableStateOf(app.dailyMinutes.toString()) }
    var group by remember(app.packageName) { mutableStateOf(app.quotaGroup) }
    var freeze by remember(app.packageName) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(app.label) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProtectionMode.entries.forEach { candidate ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = mode == candidate, onClick = { mode = candidate })
                        TextButton(onClick = { mode = candidate }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(modeLabel(candidate))
                        }
                    }
                }
                if (mode == ProtectionMode.QUOTA) {
                    OutlinedTextField(
                        minutes,
                        { minutes = it.filter(Char::isDigit).take(4) },
                        label = { Text("Daily minutes (0–1440)") },
                        singleLine = true,
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                    OutlinedTextField(
                        group,
                        { group = it.take(40) },
                        label = { Text("Shared group (optional)") },
                        singleLine = true,
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                    Text(
                        "Apps in the same group share usage and sync their daily allowance automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.muted,
                    )
                }
                ProtectionSwitch("Freeze rule for 24 hours", "Prevents editing or removing this rule", freeze, true) { freeze = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        app.copy(
                            mode = mode,
                            dailyMinutes = minutes.toIntOrNull() ?: 30,
                            quotaGroup = if (mode == ProtectionMode.QUOTA) group else "",
                        ),
                        freeze,
                    )
                },
                enabled = mode != ProtectionMode.QUOTA || minutes.toIntOrNull() in 0..1440,
            ) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}