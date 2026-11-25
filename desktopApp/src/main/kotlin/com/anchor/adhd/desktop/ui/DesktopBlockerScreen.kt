package com.anchor.adhd.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import com.anchor.adhd.desktop.blocker.DesktopScopeSentinel
import com.anchor.adhd.desktop.blocker.ScaledTimeBank
import com.anchor.adhd.desktop.blocker.ScopeVerdict
import com.anchor.adhd.desktop.blocker.TimeBankStatus
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anchor.adhd.desktop.blocker.DesktopProcessMonitor
import com.anchor.adhd.desktop.db.AnchorDesktopDatabase
import com.anchor.adhd.desktop.db.DesktopBlockRule
import com.anchor.adhd.desktop.prefs.DesktopUserPreferences
import com.anchor.adhd.ui.theme.LocalAnchorTokens
import com.anchor.adhd.ui.components.HarborEmptyState
import com.anchor.adhd.ui.components.HarborCtaButton
import com.anchor.adhd.ui.components.AnchorWellTone
import com.anchor.adhd.ui.components.AnchorWell
import com.anchor.adhd.ui.components.AnchorSectionHeader
import com.anchor.adhd.ui.components.AnchorQuietButton
import com.anchor.adhd.ui.components.AnchorListRow
import com.anchor.adhd.ui.components.AnchorHairline
import com.anchor.adhd.ui.components.AnchorChip
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.BoxWithConstraints
import com.anchor.adhd.desktop.theme.AnchorColors
import com.anchor.adhd.desktop.theme.AnchorSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class PendingCoolingOffAction {
    data class ToggleRuleOff(val rule: DesktopBlockRule) : PendingCoolingOffAction()
    data class DeleteRule(val rule: DesktopBlockRule) : PendingCoolingOffAction()
    data class ChangeScheduleMode(val rule: DesktopBlockRule, val newMode: String) : PendingCoolingOffAction()
    object ToggleStandingShieldOff : PendingCoolingOffAction()

    val targetName: String
        get() = when (this) {
            is ToggleRuleOff -> rule.target
            is DeleteRule -> rule.target
            is ChangeScheduleMode -> rule.target
            is ToggleStandingShieldOff -> "Continuous Protection Shield"
        }

    val actionDescription: String
        get() = when (this) {
            is ToggleRuleOff -> "Disable blocking for ${rule.target}"
            is DeleteRule -> "Permanently delete ${rule.target} from blocklist"
            is ChangeScheduleMode -> "Change ${rule.target} to $newMode"
            is ToggleStandingShieldOff -> "Turn off 24/7 background distraction guard"
        }
}

/**
 * Windows 11 Native App & Browser Blocker Configuration Screen.
 * Allows the user to view, toggle, add, and test distraction blocking rules.
 */
@Composable
fun DesktopBlockerScreen(
    db: AnchorDesktopDatabase,
    prefs: DesktopUserPreferences,
    processMonitor: DesktopProcessMonitor,
    isFocusActive: Boolean,
    onTestRescueShield: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAnchorTokens.current
    val scope = rememberCoroutineScope()
    val blockRules by db.blockRules.collectAsState()
    val classSchedules by db.classSchedules.collectAsState()
    val dailyUsages by db.dailyUsages.collectAsState()
    val activeClass by processMonitor.activeClassSchedule.collectAsState()
    val currentForeground by processMonitor.currentForeground.collectAsState()
    val standingShieldEnabled by prefs.standingShieldEnabled.collectAsState(false)
    val strictLockdownUntil by prefs.strictLockdownUntilEpoch.collectAsState(0L)
    val isStrictDailyLockdownActive = strictLockdownUntil > System.currentTimeMillis()

    val baseStudyMinutes by prefs.earnedLeisureBaseStudyMinutes.collectAsState(60)
    val spentLeisureMinutes by prefs.earnedLeisureSpentMinutesToday.collectAsState(0)
    val scopeSentinelEnabled by prefs.scopeSentinelEnabled.collectAsState(true)
    val scopeTaxMinutesToday by prefs.scopeTaxMinutesToday.collectAsState(0)
    val isEarnedLeisureActive = processMonitor.isEarnedLeisureActive()
    val remainingLeisureSeconds = processMonitor.getRemainingEarnedLeisureSeconds()

    var todayFocusMinutes by remember { mutableIntStateOf(0) }
    LaunchedEffect(isFocusActive) {
        todayFocusMinutes = db.getTodayFocusMinutes()
    }

    val timeBankStatus = remember(todayFocusMinutes, spentLeisureMinutes, baseStudyMinutes) {
        ScaledTimeBank.calculateStatus(todayFocusMinutes, spentLeisureMinutes, baseStudyMinutes)
    }

    val handleActivateLeisure: (Int) -> Unit = { mins ->
        scope.launch {
            val newSpent = spentLeisureMinutes + mins
            prefs.setEarnedLeisureSpentMinutesToday(newSpent)
            val expiry = System.currentTimeMillis() + (mins * 60_000L)
            prefs.setEarnedLeisureExpiryEpoch(expiry)
            processMonitor.activateEarnedLeisure(mins)
        }
    }

    val handleCancelLeisure: () -> Unit = {
        scope.launch {
            val remSec = processMonitor.getRemainingEarnedLeisureSeconds()
            val remMin = remSec / 60
            val newSpent = (spentLeisureMinutes - remMin).coerceAtLeast(0)
            prefs.setEarnedLeisureSpentMinutesToday(newSpent)
            prefs.setEarnedLeisureExpiryEpoch(0L)
            processMonitor.cancelEarnedLeisure()
        }
    }

    val handleSetBaseStudyRatio: (Int) -> Unit = { newBase ->
        scope.launch {
            prefs.setEarnedLeisureBaseStudyMinutes(newBase)
        }
    }

    val handleToggleScopeSentinel: (Boolean) -> Unit = { enabled ->
        scope.launch {
            prefs.setScopeSentinelEnabled(enabled)
            processMonitor.isScopeSentinelEnabled = enabled
        }
    }

    var showLockReason by remember { mutableStateOf<String?>(null) }
    var pendingCoolingOffAction by remember { mutableStateOf<PendingCoolingOffAction?>(null) }
    var showConfirmLockdownDialog by remember { mutableStateOf(false) }

    val attemptToggleRule: (DesktopBlockRule, Boolean, Boolean) -> Unit = { rule, isQuotaExhausted, isLocked ->
        if (!rule.enabled) {
            // Enabling / tightening is ALWAYS allowed immediately
            scope.launch { db.toggleBlockRule(rule.id) }
        } else {
            // Attempting to turn OFF / loosen:
            when {
                isFocusActive -> {
                    showLockReason = "Locked During Focus Session\n\nYou have an active study session running. Distraction shields cannot be disabled until your session completes.\n\nTip: If you need an emergency check, use the Rescue Overlay for an intentional timed pass."
                }
                activeClass != null -> {
                    showLockReason = "Locked During Class\n\nAutomated Lecture Shield is active for ${activeClass?.courseCode} until ${activeClass?.formatTimeRange()}. All distraction rules are locked during class hours."
                }
                isStrictDailyLockdownActive -> {
                    val remainingMillis = (strictLockdownUntil - System.currentTimeMillis()).coerceAtLeast(0L)
                    val remHours = remainingMillis / 3600_000L
                    val remMins = (remainingMillis % 3600_000L) / 60_000L
                    showLockReason = "Strict Daily Lockdown Active\n\nAll rules and quotas are frozen until 4:00 AM (${remHours}h ${remMins}m remaining). Rules cannot be turned off today."
                }
                isQuotaExhausted -> {
                    showLockReason = "🔒 Daily Quota Exhausted\n\nYou have used up your daily screen time allowance for ${rule.target}. Locked until 4:00 AM reset to protect your commitment."
                }
                isLocked -> {
                    val remainingMillis = (rule.lockUntilEpoch - System.currentTimeMillis()).coerceAtLeast(0L)
                    val remHours = remainingMillis / 3600_000L
                    val remMins = (remainingMillis % 3600_000L) / 60_000L
                    showLockReason = "🔒 24-Hour Cooling Lock Active\n\nThis rule was locked with a 24-hour cooling lock (${remHours}h ${remMins}m left). It cannot be disabled until the cooling period ends."
                }
                else -> {
                    pendingCoolingOffAction = PendingCoolingOffAction.ToggleRuleOff(rule)
                }
            }
        }
    }

    val attemptDeleteRule: (DesktopBlockRule, Boolean) -> Unit = { rule, isLocked ->
        when {
            isFocusActive -> {
                showLockReason = "Locked During Focus Session\n\nRules cannot be deleted while a study session is running."
            }
            activeClass != null -> {
                showLockReason = "Locked During Class\n\nRules cannot be deleted during scheduled lecture hours (${activeClass?.courseCode})."
            }
            isStrictDailyLockdownActive -> {
                showLockReason = "Strict Daily Lockdown Active\n\nRules cannot be deleted while daily lockdown is active."
            }
            isLocked -> {
                showLockReason = "🔒 24-Hour Cooling Lock Active\n\nThis rule is locked and cannot be deleted until the cooling lock expires."
            }
            else -> {
                pendingCoolingOffAction = PendingCoolingOffAction.DeleteRule(rule)
            }
        }
    }

    val attemptToggleSchedule: (DesktopBlockRule, String, Boolean) -> Unit = { rule, nextMode, isLocked ->
        val isLoosening = (rule.scheduleMode.equals("ALWAYS_24_7", true) && !nextMode.equals("ALWAYS_24_7", true)) ||
                (rule.dailyAllowanceMinutes >= 0 && nextMode.equals("FOCUS_ONLY", true))
        if (!isLoosening) {
            // Tightening schedule mode is always allowed immediately!
            scope.launch { db.updateBlockRuleScheduleMode(rule.id, nextMode) }
        } else {
            when {
                isFocusActive -> showLockReason = "Locked During Focus: Schedule mode cannot be loosened during active focus."
                activeClass != null -> showLockReason = "Locked During Class: Cannot loosen protection during class hours."
                isStrictDailyLockdownActive -> showLockReason = "Strict Daily Lockdown Active: Cannot loosen rules until 4:00 AM."
                isLocked -> showLockReason = "🔒 24-Hour Lock Active: Cannot loosen rule schedule mode."
                else -> pendingCoolingOffAction = PendingCoolingOffAction.ChangeScheduleMode(rule, nextMode)
            }
        }
    }

    val attemptToggleStandingShield: (Boolean) -> Unit = { checked ->
        if (checked) {
            scope.launch { prefs.setStandingShieldEnabled(true) }
        } else {
            when {
                isFocusActive -> showLockReason = "Locked During Focus: Continuous shield cannot be turned off during active focus."
                activeClass != null -> showLockReason = "Locked During Class: Continuous shield cannot be turned off during class hours."
                isStrictDailyLockdownActive -> showLockReason = "Strict Daily Lockdown Active: Continuous shield cannot be disabled until 4:00 AM."
                else -> pendingCoolingOffAction = PendingCoolingOffAction.ToggleStandingShieldOff
            }
        }
    }

    val count247 =
        blockRules.count {
            it.enabled &&
                (it.scheduleMode.equals("ALWAYS_24_7", ignoreCase = true) || it.scheduleMode.equals("ALWAYS", ignoreCase = true))
        }
    val countStudy = blockRules.count { it.enabled && it.scheduleMode.equals("FOCUS_ONLY", ignoreCase = true) }
    val countQuota = blockRules.count { it.enabled && (it.dailyAllowanceMinutes >= 0 || it.scheduleMode.equals("LEISURE_QUOTA", ignoreCase = true)) }
    val isShieldActive = isFocusActive || count247 > 0 || standingShieldEnabled || activeClass != null || isStrictDailyLockdownActive

    var newTargetText by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("APP") }
    var selectedSchedule by remember { mutableStateOf("FOCUS_ONLY") }
    var showClassScheduleDrawer by remember { mutableStateOf(false) }
    var rulesFilterTab by remember { mutableIntStateOf(0) } // 0 = Both (Two Columns), 1 = Apps Only, 2 = Websites Only
    val scrollState = rememberScrollState()

    val appRules = blockRules.filter { it.ruleType == "APP" }
    val webRules = blockRules.filter { it.ruleType == "WEB" }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(tokens.paper)
                .verticalScroll(scrollState)
                .padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // Title & Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Distraction Shield & App Blocker",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = tokens.ink,
                )
                Text(
                    text = "Native Win32 process & browser tab scanner (<0.05% CPU)",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.primary.copy(alpha = 0.8f),
                )
            }

            // Test Shield Button
            OutlinedButton(
                onClick = onTestRescueShield,
                shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                border = BorderStroke(1.dp, tokens.beacon),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = tokens.beacon),
            ) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Preview Rescue Overlay", fontWeight = FontWeight.SemiBold)
            }
        }

        // ====================================================
        // 1. Unified Shield Command Hub (Clean, Calm Executive Card)
        // ====================================================
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AnchorSpacing.radiusCard))
                    .border(
                        1.dp,
                        if (isShieldActive) tokens.sage.copy(alpha = 0.5f) else tokens.ink.copy(alpha = 0.1f),
                        RoundedCornerShape(AnchorSpacing.radiusCard),
                    ),
            shape = RoundedCornerShape(AnchorSpacing.radiusCard),
            color = if (isShieldActive) tokens.sageSoft else tokens.surface,
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // Top Row: Status badge & primary toggles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isShieldActive) tokens.sage.copy(alpha = 0.25f) else tokens.ink.copy(alpha = 0.08f),
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = if (isShieldActive) tokens.sage else tokens.ink.copy(alpha = 0.6f),
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            val titleText =
                                when {
                                    activeClass != null -> "Lecture shield active — ${activeClass?.courseCode} (${activeClass?.formatTimeRange()})"
                                    isFocusActive -> "Study mode active — guard engaged"
                                    count247 > 0 -> "24/7 continuous protection active ($count247 rules)"
                                    standingShieldEnabled -> "Continuous shield active — standing guard"
                                    countQuota > 0 -> "Quota shield active — ($countQuota metered)"
                                    else -> "Shield standby — ready for focus"
                                }
                            val descText =
                                when {
                                    activeClass != null -> "In ${activeClass?.courseName}. Leisure apps, games & dev tools locked."
                                    isFocusActive -> "Blocking $countStudy study-time and $count247 continuous rules during this session."
                                    count247 > 0 -> "$count247 apps/sites blocked 24/7. $countStudy study rules active during focus."
                                    standingShieldEnabled -> "24/7 background guard enabled. Blocks distractions without active timer."
                                    countQuota > 0 -> "$countQuota apps have daily time allowances. Locked once time expires."
                                    else -> "Turn on Continuous Guard or start a focus session to engage protection."
                                }
                            Text(
                                text = titleText,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isShieldActive) tokens.sage else tokens.ink,
                            )
                            Text(
                                text = descText,
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.ink.copy(alpha = 0.7f),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Controls cluster: Continuous Shield + Strict Daily Lockdown
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // 24/7 Guard Switch
                        Surface(
                            shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                            color = if (standingShieldEnabled) tokens.sage.copy(alpha = 0.2f) else tokens.ink.copy(alpha = 0.05f),
                            border = BorderStroke(1.dp, if (standingShieldEnabled) tokens.sage.copy(alpha = 0.4f) else tokens.ink.copy(alpha = 0.1f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "24/7 Guard",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (standingShieldEnabled) tokens.sage else tokens.ink.copy(alpha = 0.7f),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Switch(
                                    checked = standingShieldEnabled,
                                    onCheckedChange = { checked -> attemptToggleStandingShield(checked) },
                                    colors =
                                        SwitchDefaults.colors(
                                            checkedThumbColor = tokens.sage,
                                            checkedTrackColor = tokens.sage,
                                            uncheckedThumbColor = tokens.ink.copy(alpha = 0.5f),
                                            uncheckedTrackColor = tokens.ink.copy(alpha = 0.1f),
                                        ),
                                )
                            }
                        }

                        // Strict Daily Lockdown Action
                        if (!isStrictDailyLockdownActive) {
                            OutlinedButton(
                                onClick = { showConfirmLockdownDialog = true },
                                shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                border = BorderStroke(1.dp, tokens.beacon.copy(alpha = 0.7f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = tokens.beacon),
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Lockdown to 4 AM", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            val remMillis = (strictLockdownUntil - System.currentTimeMillis()).coerceAtLeast(0L)
                            val remHours = remMillis / 3600_000L
                            val remMins = (remMillis % 3600_000L) / 60_000L
                            Surface(
                                shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                color = tokens.beacon.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, tokens.beacon),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = tokens.beacon, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Locked (${remHours}h ${remMins}m left)",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = tokens.beacon,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Sub-bar: Timetable toggle + Foreground inspector
                var showAddClassScheduleDialog by remember { mutableStateOf(false) }
                val distinctCourseCodes = remember(classSchedules) { classSchedules.map { it.courseCode }.distinct() }
                val syncedClassesSummary =
                    remember(distinctCourseCodes) {
                        if (distinctCourseCodes.isEmpty()) {
                            "0 Classes Synced (Click to add class schedule)"
                        } else {
                            "${distinctCourseCodes.size} ${if (distinctCourseCodes.size == 1) "Class" else "Classes"} Synced (${distinctCourseCodes.joinToString(", ")})"
                        }
                    }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Class schedule pill button (expandable)
                    Surface(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(AnchorSpacing.radiusPill))
                                .clickable { showClassScheduleDrawer = !showClassScheduleDrawer },
                        shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                        color = if (activeClass != null) tokens.clayRoseSoft else tokens.ink.copy(alpha = 0.05f),
                        border = BorderStroke(1.dp, if (activeClass != null) tokens.beacon.copy(alpha = 0.5f) else tokens.ink.copy(alpha = 0.1f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("📅", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (activeClass != null) "In Session: ${activeClass?.courseCode}" else syncedClassesSummary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (activeClass != null) tokens.beacon else tokens.ink.copy(alpha = 0.8f),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (showClassScheduleDrawer) "▲" else "▼",
                                fontSize = 10.sp,
                                color = tokens.ink.copy(alpha = 0.5f),
                            )
                        }
                    }

                    // Live Foreground Window chip
                    if (currentForeground != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = tokens.primary.copy(alpha = 0.7f),
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Foreground: ${currentForeground?.executableName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.ink.copy(alpha = 0.6f),
                                maxLines = 1,
                            )
                        }
                    }
                }

                // Expandable Class Timetable Cards (Only shown when drawer is open)
                if (showClassScheduleDrawer) {
                    Spacer(modifier = Modifier.height(12.dp))
                    val dayNames = mapOf(1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat", 7 to "Sun")
                    val groupedByCourse = remember(classSchedules) { classSchedules.groupBy { it.courseCode } }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (groupedByCourse.isEmpty()) {
                            Text(
                                text = "No class sessions configured yet. Add your weekly lectures to enable automatic Lecture Shield.",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.ink.copy(alpha = 0.6f),
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                for ((code, sessions) in groupedByCourse) {
                                    val isThisCourseActive = activeClass?.courseCode?.equals(code, ignoreCase = true) == true
                                    val timeSummary =
                                        sessions
                                            .groupBy { it.formatTimeRange() to it.sessionType }
                                            .entries
                                            .joinToString(" | ") { (pair, list) ->
                                                val daysStr = list.mapNotNull { dayNames[it.dayOfWeek] }.joinToString(",")
                                                "$daysStr ${pair.first}"
                                            }
                                    Surface(
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                                        color = if (isThisCourseActive) tokens.clayRoseSoft else tokens.ink.copy(alpha = 0.04f),
                                        border = BorderStroke(1.dp, if (isThisCourseActive) tokens.beacon else tokens.ink.copy(alpha = 0.08f)),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(code, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = tokens.ink)
                                                Text(timeSummary, style = MaterialTheme.typography.bodySmall, fontSize = 10.sp, color = tokens.ink.copy(alpha = 0.6f))
                                            }
                                            if (!isFocusActive && activeClass == null && !isStrictDailyLockdownActive) {
                                                IconButton(
                                                    onClick = {
                                                        scope.launch {
                                                            sessions.forEach { s -> db.deleteClassSchedule(s.id) }
                                                        }
                                                    },
                                                    modifier = Modifier.size(22.dp),
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Remove $code schedule",
                                                        tint = tokens.ink.copy(alpha = 0.4f),
                                                        modifier = Modifier.size(13.dp),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            OutlinedButton(
                                onClick = { showAddClassScheduleDialog = true },
                                shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                border = BorderStroke(1.dp, tokens.primary.copy(alpha = 0.4f)),
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = tokens.primary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("+ Add Class Schedule", style = MaterialTheme.typography.labelSmall, color = tokens.primary)
                            }
                        }
                    }
                }

                if (showAddClassScheduleDialog) {
                    var courseCodeInput by remember { mutableStateOf("") }
                    var courseNameInput by remember { mutableStateOf("") }
                    var dayOfWeekInput by remember { mutableIntStateOf(1) }
                    var startHourInput by remember { mutableStateOf("11:00") }
                    var endHourInput by remember { mutableStateOf("12:00") }

                    fun parseTimeMinutes(text: String, fallback: Int): Int {
                        val parts = text.trim().split(":")
                        val h = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: return fallback
                        val m = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
                        return h * 60 + m
                    }

                    AlertDialog(
                        onDismissRequest = { showAddClassScheduleDialog = false },
                        title = { Text("📅 Add Class Schedule Slot", fontWeight = FontWeight.Bold, color = tokens.ink) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = courseCodeInput,
                                    onValueChange = { courseCodeInput = it },
                                    label = { Text("Course Code (e.g. CS 101)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    value = courseNameInput,
                                    onValueChange = { courseNameInput = it },
                                    label = { Text("Course Name") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(1 to "M", 2 to "Tu", 3 to "W", 4 to "Th", 5 to "F").forEach { (d, label) ->
                                        Surface(
                                            shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                            color = if (dayOfWeekInput == d) tokens.primary else tokens.ink.copy(alpha = 0.08f),
                                            modifier = Modifier.clickable { dayOfWeekInput = d },
                                        ) {
                                            Text(
                                                text = label,
                                                color = if (dayOfWeekInput == d) tokens.beaconInk else tokens.ink,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            )
                                        }
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = startHourInput,
                                        onValueChange = { startHourInput = it },
                                        label = { Text("Start (24h HH:MM)") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                    )
                                    OutlinedTextField(
                                        value = endHourInput,
                                        onValueChange = { endHourInput = it },
                                        label = { Text("End (24h HH:MM)") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    val startMin = parseTimeMinutes(startHourInput, 660)
                                    val endMin = parseTimeMinutes(endHourInput, 720).coerceAtLeast(startMin + 15)
                                    if (courseCodeInput.isNotBlank()) {
                                        scope.launch {
                                            db.insertClassSchedule(
                                                courseCode = courseCodeInput.trim().uppercase(),
                                                courseName = courseNameInput.trim().ifBlank { courseCodeInput.trim().uppercase() },
                                                sessionType = "Lecture",
                                                dayOfWeek = dayOfWeekInput,
                                                startMinuteOfDay = startMin,
                                                endMinuteOfDay = endMin,
                                            )
                                            showAddClassScheduleDialog = false
                                        }
                                    }
                                },
                                enabled = courseCodeInput.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = tokens.primary, contentColor = tokens.beaconInk),
                            ) {
                                Text("Add Class", fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            OutlinedButton(onClick = { showAddClassScheduleDialog = false }) {
                                Text("Cancel", color = tokens.ink)
                            }
                        },
                        containerColor = tokens.surface,
                    )
                }
            }
        }

        // ====================================================
        // 1.5. Scaled Time-Bank: Focus-to-Leisure Economy Card
        // ====================================================
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(AnchorSpacing.radiusCard),
            color = if (isEarnedLeisureActive) tokens.sageSoft else tokens.surface,
            border = BorderStroke(
                1.dp,
                if (isEarnedLeisureActive) tokens.sage else tokens.primary.copy(alpha = 0.25f),
            ),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            modifier = Modifier.size(38.dp),
                            shape = CircleShape,
                            color = if (isEarnedLeisureActive) tokens.sage.copy(alpha = 0.2f) else tokens.paper2,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = if (isEarnedLeisureActive) tokens.sage else tokens.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Leisure Time-Bank",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = tokens.ink,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                    color = tokens.ink.copy(alpha = 0.08f),
                                ) {
                                    Text(
                                        text = "Anti-Idling Scaled Stack",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = tokens.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            Text(
                                text = "Study to earn 30m of 24/7 unblocked leisure. Requirement scales up per tier to discourage idling.",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.ink.copy(alpha = 0.65f),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Action Button or Active Pass Indicator
                    if (isEarnedLeisureActive) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                color = tokens.sage.copy(alpha = 0.25f),
                                border = BorderStroke(1.dp, tokens.sage),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LockOpen,
                                        contentDescription = null,
                                        tint = tokens.sage,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    val remMin = remainingLeisureSeconds / 60
                                    val remSec = remainingLeisureSeconds % 60
                                    Text(
                                        text = String.format("Unlocked: %02d:%02d", remMin, remSec),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = tokens.sage,
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = handleCancelLeisure,
                                shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                border = BorderStroke(1.dp, tokens.ink.copy(alpha = 0.3f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = tokens.ink),
                            ) {
                                Text("Lock Up & Save", fontSize = 11.sp)
                            }
                        }
                    } else {
                        Button(
                            onClick = { handleActivateLeisure(30) },
                            enabled = timeBankStatus.availableBankedMinutes >= 30 && activeClass == null,
                            shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = tokens.sage,
                                contentColor = tokens.beaconInk,
                                disabledContainerColor = tokens.ink.copy(alpha = 0.08f),
                                disabledContentColor = tokens.ink.copy(alpha = 0.35f),
                            ),
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (timeBankStatus.availableBankedMinutes >= 30) "Claim 30m Break (${timeBankStatus.availableBankedMinutes}m Banked)" else "Banked: ${timeBankStatus.availableBankedMinutes}m (Need 30m)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Progress Bar towards Next Tier Block
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AnchorSpacing.radiusChip))
                        .background(tokens.ink.copy(alpha = 0.03f))
                        .padding(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Next 30m Unlock Progress (Tier ${timeBankStatus.currentTier}):",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = tokens.ink.copy(alpha = 0.8f),
                        )
                        Text(
                            text = "${timeBankStatus.minutesIntoCurrentTier} / ${timeBankStatus.minutesRequiredForNextTier}m studied (${(timeBankStatus.nextTierProgress * 100).toInt()}%)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = tokens.primary,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { timeBankStatus.nextTierProgress },
                        modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)),
                        color = tokens.primary,
                        trackColor = tokens.ink.copy(alpha = 0.1f),
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tier Scaling Cards & Exchange Rate Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Scaling curve chips
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val tiers = listOf(
                            1 to "${baseStudyMinutes}m" to "30m",
                            2 to "+${(baseStudyMinutes * 1.25).toInt()}m" to "30m",
                            3 to "+${(baseStudyMinutes * 1.5).toInt()}m" to "30m",
                            4 to "+${baseStudyMinutes * 2}m" to "30m",
                        )
                        for ((tierInfo, leisure) in tiers) {
                            val (tNum, req) = tierInfo
                            val isCompleted = timeBankStatus.currentTier > tNum
                            val isCurrent = timeBankStatus.currentTier == tNum
                            Surface(
                                shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                color = when {
                                    isCompleted -> tokens.sage.copy(alpha = 0.2f)
                                    isCurrent -> tokens.primary.copy(alpha = 0.2f)
                                    else -> tokens.ink.copy(alpha = 0.04f)
                                },
                                border = BorderStroke(
                                    1.dp,
                                    when {
                                        isCompleted -> tokens.sage.copy(alpha = 0.6f)
                                        isCurrent -> tokens.primary
                                        else -> tokens.ink.copy(alpha = 0.08f)
                                    },
                                ),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (isCompleted) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = tokens.sage, modifier = Modifier.size(11.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                    }
                                    Text(
                                        text = "T$tNum: $req → $leisure",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = if (isCompleted) tokens.sage else if (isCurrent) tokens.primary else tokens.ink.copy(alpha = 0.5f),
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                    }

                    // Base Ratio Selector
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Base Rate:",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = tokens.ink.copy(alpha = 0.5f),
                        )
                        listOf(45 to "45m", 60 to "60m", 75 to "75m").forEach { (mins, label) ->
                            val isSelected = baseStudyMinutes == mins
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(AnchorSpacing.radiusPill))
                                    .clickable { handleSetBaseStudyRatio(mins) },
                                shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                color = if (isSelected) tokens.primary else tokens.ink.copy(alpha = 0.06f),
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) tokens.beaconInk else tokens.ink.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // ====================================================
        // 1.6. AI Scope Sentinel & Distraction Tax Card
        // ====================================================
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(AnchorSpacing.radiusCard),
            color = if (scopeSentinelEnabled) tokens.captainSoft else tokens.surface,
            border = BorderStroke(
                1.dp,
                if (scopeSentinelEnabled) tokens.primary.copy(alpha = 0.4f) else tokens.ink.copy(alpha = 0.1f),
            ),
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // Header with Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            modifier = Modifier.size(38.dp),
                            shape = CircleShape,
                            color = tokens.primary.copy(alpha = 0.15f),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = tokens.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "AI Scope Sentinel & Distraction Tax",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = tokens.ink,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                    color = if (scopeSentinelEnabled) tokens.sage.copy(alpha = 0.2f) else tokens.ink.copy(alpha = 0.08f),
                                ) {
                                    Text(
                                        text = if (scopeSentinelEnabled) "Active in Focus" else "Paused",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = if (scopeSentinelEnabled) tokens.sage else tokens.ink.copy(alpha = 0.5f),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            Text(
                                text = "Lightweight background checker detects out-of-scope browsing during study. Drains 1m banked leisure per minute.",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.ink.copy(alpha = 0.65f),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Switch(
                        checked = scopeSentinelEnabled,
                        onCheckedChange = handleToggleScopeSentinel,
                        colors =
                            SwitchDefaults.colors(
                                checkedThumbColor = tokens.primary,
                                checkedTrackColor = tokens.sage,
                                uncheckedThumbColor = tokens.ink.copy(alpha = 0.5f),
                                uncheckedTrackColor = tokens.ink.copy(alpha = 0.1f),
                            ),
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Allowed vs Out-of-Scope Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Allowed Disciplines Box
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                        color = tokens.sageSoft,
                        border = BorderStroke(1.dp, tokens.sage.copy(alpha = 0.3f)),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("✓", color = tokens.sage, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "In-Scope Disciplines (Protected)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = tokens.sage,
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Current task topics and study workspaces. All academic subjects are supported.",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = tokens.ink.copy(alpha = 0.85f),
                                lineHeight = 16.sp,
                            )
                        }
                    }

                    // Out-of-Scope Distractions Box
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                        color = tokens.clayRoseSoft,
                        border = BorderStroke(1.dp, tokens.clayRose.copy(alpha = 0.3f)),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("✕", color = tokens.clayRose, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Out-of-Scope Triggers (-1m/min Tax)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = tokens.clayRose,
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Activities clearly unrelated to the current task, as classified by you or the local model. Ambiguous titles are allowed.",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = tokens.ink.copy(alpha = 0.85f),
                                lineHeight = 16.sp,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Scope Status & Daily Tax Summary Bar
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                    color = tokens.ink.copy(alpha = 0.04f),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Tax Rate: -1 min banked leisure per sustained minute out of scope",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                color = tokens.ink.copy(alpha = 0.6f),
                            )
                        }
                        Text(
                            text = "Taxed Today: ${scopeTaxMinutesToday}m deducted",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (scopeTaxMinutesToday > 0) tokens.clayRose else tokens.primary,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }

        // ====================================================
        // 2. Add Rule Composer (Spacious 2-tier design)
        // ====================================================
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AnchorSpacing.radiusCard)),
            color = tokens.surface,
            border = BorderStroke(1.dp, tokens.ink.copy(alpha = 0.1f)),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Row 1: Target Input + Type Toggle + Add Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = newTargetText,
                        onValueChange = { newTargetText = it },
                        placeholder = {
                            Text(
                                if (selectedType == "APP") "Enter application process (e.g. discord.exe, steam.exe, game.exe)..." else "Enter domain to block (e.g. reddit.com, youtube.com, twitch.tv)...",
                                color = tokens.ink.copy(alpha = 0.4f),
                                fontSize = 13.sp,
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = tokens.primary,
                                unfocusedBorderColor = tokens.ink.copy(alpha = 0.2f),
                                focusedTextColor = tokens.ink,
                                unfocusedTextColor = tokens.ink,
                            ),
                        singleLine = true,
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // Type Selector Segmented Pill
                    Surface(
                        shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                        color = tokens.ink.copy(alpha = 0.06f),
                        border = BorderStroke(1.dp, tokens.ink.copy(alpha = 0.1f)),
                    ) {
                        Row(modifier = Modifier.padding(3.dp)) {
                            Button(
                                onClick = { selectedType = "APP" },
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = if (selectedType == "APP") tokens.primary else Color.Transparent,
                                        contentColor = if (selectedType == "APP") tokens.beaconInk else tokens.ink.copy(alpha = 0.7f),
                                    ),
                                shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                Text("App (.exe)", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                            Button(
                                onClick = { selectedType = "WEB" },
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = if (selectedType == "WEB") tokens.primary else Color.Transparent,
                                        contentColor = if (selectedType == "WEB") tokens.beaconInk else tokens.ink.copy(alpha = 0.7f),
                                    ),
                                shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                Text("Website", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = {
                            if (newTargetText.isNotBlank()) {
                                val quotaMins = if (selectedSchedule == "LEISURE_QUOTA") 75 else -1
                                scope.launch {
                                    db.insertBlockRule(
                                        target = newTargetText.trim(),
                                        ruleType = selectedType,
                                        scheduleMode = selectedSchedule,
                                        dailyAllowanceMinutes = quotaMins,
                                    )
                                    newTargetText = ""
                                }
                            }
                        },
                        shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                        colors = ButtonDefaults.buttonColors(containerColor = tokens.beacon),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Rule", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Row 2: Protection Mode Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Protection Schedule:",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.ink.copy(alpha = 0.5f),
                        fontWeight = FontWeight.SemiBold,
                    )

                    val modes = listOf(
                        Triple("FOCUS_ONLY", "Focus sessions only", tokens.sage),
                        Triple("ALWAYS_24_7", "24/7 continuous block", tokens.clayRose),
                        Triple("LEISURE_QUOTA", "Daily quota (75m allowance)", tokens.primary),
                    )

                    for ((modeId, label, color) in modes) {
                        val isSelected = selectedSchedule == modeId
                        Surface(
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(AnchorSpacing.radiusPill))
                                    .clickable { selectedSchedule = modeId },
                            shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                            color = if (isSelected) color.copy(alpha = 0.2f) else tokens.ink.copy(alpha = 0.04f),
                            border = BorderStroke(1.dp, if (isSelected) color else tokens.ink.copy(alpha = 0.08f)),
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) color else tokens.ink.copy(alpha = 0.7f),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        }

        // ====================================================
        // 3. Rules Explorer & Filter Tabs (Apps & Websites)
        // ====================================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Configured rules (${blockRules.size})",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = tokens.ink.copy(alpha = 0.6f),
            )

            // Filter Tabs
            Surface(
                shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                color = tokens.ink.copy(alpha = 0.05f),
                border = BorderStroke(1.dp, tokens.ink.copy(alpha = 0.1f)),
            ) {
                Row(modifier = Modifier.padding(3.dp)) {
                    val tabs = listOf(
                        "All Rules (${blockRules.size})",
                        "Apps (${appRules.size})",
                        "Websites (${webRules.size})",
                    )
                    tabs.forEachIndexed { index, label ->
                        val isSelected = rulesFilterTab == index
                        Button(
                            onClick = { rulesFilterTab = index },
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) tokens.primary else Color.Transparent,
                                    contentColor = if (isSelected) tokens.beaconInk else tokens.ink.copy(alpha = 0.7f),
                                ),
                            shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Rules Cards (Natural full height, smooth scrolling, unconstrained)
        when (rulesFilterTab) {
            0 -> {
                // Two-Column Grid: Apps on Left, Websites on Right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Blocked Apps Column
                    Surface(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(AnchorSpacing.radiusCard)),
                        color = tokens.surface.copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, tokens.ink.copy(alpha = 0.1f)),
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "BLOCKED APPLICATIONS (${appRules.size})",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = tokens.primary,
                            )
                            if (appRules.isEmpty()) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(AnchorSpacing.radiusChip)),
                                    color = tokens.ink.copy(alpha = 0.03f),
                                ) {
                                    Text(
                                        text = "No applications configured. Add games or chat apps above.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = tokens.ink.copy(alpha = 0.4f),
                                        modifier = Modifier.padding(16.dp),
                                    )
                                }
                            } else {
                                appRules.forEach { rule ->
                                    val groupUsed = if (!rule.quotaGroup.isNullOrBlank()) {
                                        appRules.filter { it.quotaGroup == rule.quotaGroup }.sumOf { dailyUsages[it.target.lowercase().trim()] ?: 0 }
                                    } else {
                                        dailyUsages[rule.target.lowercase().trim()] ?: 0
                                    }
                                    RuleItemCard(
                                        rule = rule,
                                        usedSeconds = dailyUsages[rule.target.lowercase().trim()] ?: 0,
                                        groupUsedSeconds = groupUsed,
                                        isHardLocked = isFocusActive || activeClass != null || isStrictDailyLockdownActive,
                                        onAttemptToggle = { isExhausted, isLocked -> attemptToggleRule(rule, isExhausted, isLocked) },
                                        onAttemptToggleSchedule = { nextMode, isLocked -> attemptToggleSchedule(rule, nextMode, isLocked) },
                                        onUpdateAllowance = { newMinutes, lock24h ->
                                            if (isFocusActive || activeClass != null || isStrictDailyLockdownActive) {
                                                showLockReason = "Locked: Allowances cannot be loosened while Focus, Class, or Strict Lockdown is active."
                                            } else {
                                                scope.launch { db.updateBlockRuleAllowance(rule.id, newMinutes, lock24h) }
                                            }
                                        },
                                        onLockFor24Hours = { scope.launch { db.lockBlockRuleFor24Hours(rule.id) } },
                                        onAttemptDelete = { isLocked -> attemptDeleteRule(rule, isLocked) },
                                    )
                                }
                            }
                        }
                    }

                    // Blocked Websites Column
                    Surface(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(AnchorSpacing.radiusCard)),
                        color = tokens.surface.copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, tokens.ink.copy(alpha = 0.1f)),
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Blocked browser SITES (${webRules.size})",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = tokens.beacon,
                            )
                            if (webRules.isEmpty()) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(AnchorSpacing.radiusChip)),
                                    color = tokens.ink.copy(alpha = 0.03f),
                                ) {
                                    Text(
                                        text = "No websites configured. Add social media or streaming sites above.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = tokens.ink.copy(alpha = 0.4f),
                                        modifier = Modifier.padding(16.dp),
                                    )
                                }
                            } else {
                                webRules.forEach { rule ->
                                    val groupUsed = if (!rule.quotaGroup.isNullOrBlank()) {
                                        webRules.filter { it.quotaGroup == rule.quotaGroup }.sumOf { dailyUsages[it.target.lowercase().trim()] ?: 0 }
                                    } else {
                                        dailyUsages[rule.target.lowercase().trim()] ?: 0
                                    }
                                    RuleItemCard(
                                        rule = rule,
                                        usedSeconds = dailyUsages[rule.target.lowercase().trim()] ?: 0,
                                        groupUsedSeconds = groupUsed,
                                        isHardLocked = isFocusActive || activeClass != null || isStrictDailyLockdownActive,
                                        onAttemptToggle = { isExhausted, isLocked -> attemptToggleRule(rule, isExhausted, isLocked) },
                                        onAttemptToggleSchedule = { nextMode, isLocked -> attemptToggleSchedule(rule, nextMode, isLocked) },
                                        onUpdateAllowance = { newMinutes, lock24h ->
                                            if (isFocusActive || activeClass != null || isStrictDailyLockdownActive) {
                                                showLockReason = "Locked: Allowances cannot be loosened while Focus, Class, or Strict Lockdown is active."
                                            } else {
                                                scope.launch { db.updateBlockRuleAllowance(rule.id, newMinutes, lock24h) }
                                            }
                                        },
                                        onLockFor24Hours = { scope.launch { db.lockBlockRuleFor24Hours(rule.id) } },
                                        onAttemptDelete = { isLocked -> attemptDeleteRule(rule, isLocked) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            1 -> {
                // Apps Only (Full Width)
                Surface(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(AnchorSpacing.radiusCard)),
                    color = tokens.surface.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, tokens.ink.copy(alpha = 0.1f)),
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "BLOCKED APPLICATIONS (${appRules.size})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = tokens.primary,
                        )
                        appRules.forEach { rule ->
                            val groupUsed = if (!rule.quotaGroup.isNullOrBlank()) {
                                appRules.filter { it.quotaGroup == rule.quotaGroup }.sumOf { dailyUsages[it.target.lowercase().trim()] ?: 0 }
                            } else {
                                dailyUsages[rule.target.lowercase().trim()] ?: 0
                            }
                            RuleItemCard(
                                rule = rule,
                                usedSeconds = dailyUsages[rule.target.lowercase().trim()] ?: 0,
                                groupUsedSeconds = groupUsed,
                                isHardLocked = isFocusActive || activeClass != null || isStrictDailyLockdownActive,
                                onAttemptToggle = { isExhausted, isLocked -> attemptToggleRule(rule, isExhausted, isLocked) },
                                onAttemptToggleSchedule = { nextMode, isLocked -> attemptToggleSchedule(rule, nextMode, isLocked) },
                                onUpdateAllowance = { newMinutes, lock24h ->
                                    if (isFocusActive || activeClass != null || isStrictDailyLockdownActive) {
                                        showLockReason = "Locked: Allowances cannot be loosened while Focus, Class, or Strict Lockdown is active."
                                    } else {
                                        scope.launch { db.updateBlockRuleAllowance(rule.id, newMinutes, lock24h) }
                                    }
                                },
                                onLockFor24Hours = { scope.launch { db.lockBlockRuleFor24Hours(rule.id) } },
                                onAttemptDelete = { isLocked -> attemptDeleteRule(rule, isLocked) },
                            )
                        }
                    }
                }
            }
            2 -> {
                // Websites Only (Full Width)
                Surface(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(AnchorSpacing.radiusCard)),
                    color = tokens.surface.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, tokens.ink.copy(alpha = 0.1f)),
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Blocked browser SITES (${webRules.size})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = tokens.beacon,
                        )
                        webRules.forEach { rule ->
                            val groupUsed = if (!rule.quotaGroup.isNullOrBlank()) {
                                webRules.filter { it.quotaGroup == rule.quotaGroup }.sumOf { dailyUsages[it.target.lowercase().trim()] ?: 0 }
                            } else {
                                dailyUsages[rule.target.lowercase().trim()] ?: 0
                            }
                            RuleItemCard(
                                rule = rule,
                                usedSeconds = dailyUsages[rule.target.lowercase().trim()] ?: 0,
                                groupUsedSeconds = groupUsed,
                                isHardLocked = isFocusActive || activeClass != null || isStrictDailyLockdownActive,
                                onAttemptToggle = { isExhausted, isLocked -> attemptToggleRule(rule, isExhausted, isLocked) },
                                onAttemptToggleSchedule = { nextMode, isLocked -> attemptToggleSchedule(rule, nextMode, isLocked) },
                                onUpdateAllowance = { newMinutes, lock24h ->
                                    if (isFocusActive || activeClass != null || isStrictDailyLockdownActive) {
                                        showLockReason = "Locked: Allowances cannot be loosened while Focus, Class, or Strict Lockdown is active."
                                    } else {
                                        scope.launch { db.updateBlockRuleAllowance(rule.id, newMinutes, lock24h) }
                                    }
                                },
                                onLockFor24Hours = { scope.launch { db.lockBlockRuleFor24Hours(rule.id) } },
                                onAttemptDelete = { isLocked -> attemptDeleteRule(rule, isLocked) },
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Anti-Bypass 30-Second Intentional Cooling-Off Dialog
        val currentCoolingAction = pendingCoolingOffAction
        if (currentCoolingAction != null) {
            DesktopCoolingOffDialog(
                action = currentCoolingAction,
                onDismiss = { pendingCoolingOffAction = null },
                onConfirmPermanent = {
                    scope.launch {
                        when (currentCoolingAction) {
                            is PendingCoolingOffAction.ToggleRuleOff -> db.toggleBlockRule(currentCoolingAction.rule.id)
                            is PendingCoolingOffAction.DeleteRule -> db.deleteBlockRule(currentCoolingAction.rule.id)
                            is PendingCoolingOffAction.ChangeScheduleMode -> db.updateBlockRuleScheduleMode(currentCoolingAction.rule.id, currentCoolingAction.newMode)
                            is PendingCoolingOffAction.ToggleStandingShieldOff -> prefs.setStandingShieldEnabled(false)
                        }
                    }
                    pendingCoolingOffAction = null
                },
                onConfirmTemporary15Min = if (currentCoolingAction is PendingCoolingOffAction.ToggleRuleOff) {
                    {
                        val rule = currentCoolingAction.rule
                        processMonitor.grantEmergencyPass(rule.target, 15 * 60_000L)
                        db.scope.launch {
                            db.toggleBlockRule(rule.id)
                            delay(15 * 60_000L) // 15 minutes
                            db.setBlockRuleEnabled(rule.target, true) // Auto re-enable even if user switches tabs!
                        }
                        pendingCoolingOffAction = null
                    }
                } else null,
            )
        }

        // Explanation Dialog when an action is strictly locked
        val lockReason = showLockReason
        if (lockReason != null) {
            AlertDialog(
                onDismissRequest = { showLockReason = null },
                title = {
                    Text("Protection Shield Locked", fontWeight = FontWeight.Bold, color = tokens.ink)
                },
                text = {
                    Text(lockReason, color = tokens.ink.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium)
                },
                confirmButton = {
                    Button(
                        onClick = { showLockReason = null },
                        colors = ButtonDefaults.buttonColors(containerColor = tokens.primary, contentColor = tokens.beaconInk),
                    ) {
                        Text("Understood", fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = tokens.surface,
            )
        }

        // Confirmation Dialog for Strict Daily Lockdown
        if (showConfirmLockdownDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmLockdownDialog = false },
                title = {
                    Text("Engage Strict Daily Lockdown?", fontWeight = FontWeight.Bold, color = tokens.ink)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Are you sure you want to lock all distraction rules until tomorrow morning at 4:00 AM?",
                            color = tokens.ink.copy(alpha = 0.9f),
                        )
                        Text(
                            "• All active rules, 24/7 shields, and quotas are frozen.\n• Rules cannot be turned off, loosened, or deleted until 4:00 AM.\n• You can still ADD new block targets anytime.",
                            color = tokens.beacon,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                val next4Am = DesktopUserPreferences.calculateNext4AmEpoch()
                                prefs.setStrictLockdownUntil(next4Am)
                                showConfirmLockdownDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = tokens.beacon, contentColor = tokens.beaconInk),
                    ) {
                        Text("Confirm & Lock Until 4:00 AM", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showConfirmLockdownDialog = false },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = tokens.ink),
                    ) {
                        Text("Cancel")
                    }
                },
                containerColor = tokens.surface,
            )
        }
    }
}

@Composable
private fun RuleItemCard(
    rule: DesktopBlockRule,
    usedSeconds: Int,
    groupUsedSeconds: Int = usedSeconds,
    isHardLocked: Boolean = false,
    onAttemptToggle: (isQuotaExhausted: Boolean, isLocked: Boolean) -> Unit,
    onAttemptToggleSchedule: (newMode: String, isLocked: Boolean) -> Unit,
    onUpdateAllowance: (Int, Boolean) -> Unit,
    onLockFor24Hours: () -> Unit,
    onAttemptDelete: (isLocked: Boolean) -> Unit,
) {
    val tokens = LocalAnchorTokens.current
    val now = remember(rule.lockUntilEpoch, rule.enabled) { System.currentTimeMillis() }
    val isLocked = rule.lockUntilEpoch > now
    val is247 = rule.scheduleMode.equals("ALWAYS_24_7", ignoreCase = true) || rule.scheduleMode.equals("ALWAYS", ignoreCase = true)
    val isSharedPool = rule.scheduleMode.equals("SHARED_POOL", ignoreCase = true) || !rule.quotaGroup.isNullOrBlank()
    val hasQuota = rule.dailyAllowanceMinutes >= 0
    val effectiveUsedSeconds = if (isSharedPool) groupUsedSeconds else usedSeconds
    val usedMinutes = effectiveUsedSeconds / 60
    val quotaMinutes = rule.dailyAllowanceMinutes
    val isQuotaExhausted = hasQuota && usedMinutes >= quotaMinutes

    var showTuneDialog by remember { mutableStateOf(false) }

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AnchorSpacing.radiusChip)),
        color = if (rule.enabled) tokens.paper.copy(alpha = 0.8f) else tokens.ink.copy(alpha = 0.03f),
        border = BorderStroke(1.dp, if (rule.enabled) tokens.ink.copy(alpha = 0.12f) else tokens.ink.copy(alpha = 0.05f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = rule.target,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (rule.enabled) tokens.ink else tokens.ink.copy(alpha = 0.4f),
                        )

                        // Mode Badge
                        Surface(
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable {
                                        if (!isSharedPool) {
                                            val nextMode =
                                                when {
                                                    is247 -> "FOCUS_ONLY"
                                                    hasQuota -> "ALWAYS_24_7"
                                                    else -> "LEISURE_QUOTA"
                                                }
                                            if (nextMode == "LEISURE_QUOTA") {
                                                onUpdateAllowance(75, false)
                                            } else {
                                                onAttemptToggleSchedule(nextMode, isLocked)
                                            }
                                        }
                                    },
                            color =
                                when {
                                    is247 -> tokens.clayRoseSoft.copy(alpha = 0.8f)
                                    isSharedPool -> tokens.sage.copy(alpha = 0.25f)
                                    hasQuota -> tokens.primary.copy(alpha = 0.25f)
                                    else -> tokens.sage.copy(alpha = 0.35f)
                                },
                            border =
                                BorderStroke(
                                    1.dp,
                                    when {
                                        is247 -> tokens.clayRose.copy(alpha = 0.5f)
                                        isSharedPool -> tokens.sage.copy(alpha = 0.6f)
                                        hasQuota -> tokens.primary.copy(alpha = 0.6f)
                                        else -> tokens.sage.copy(alpha = 0.4f)
                                    },
                                ),
                        ) {
                            Text(
                                text =
                                    when {
                                        is247 -> "24/7 block"
                                        isSharedPool -> "Shared 3.5h Pool"
                                        hasQuota -> "Quota: ${quotaMinutes}m/d"
                                        else -> "Study Time"
                                    },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color =
                                    when {
                                        is247 -> tokens.clayRose
                                        isSharedPool -> tokens.sage
                                        hasQuota -> tokens.primary
                                        else -> tokens.sage
                                    },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }

                        // 24h Lock Badge if active
                        if (isLocked) {
                            val remainingMillis = (rule.lockUntilEpoch - now).coerceAtLeast(0L)
                            val remainingHours = remainingMillis / 3600_000L
                            val remainingMins = (remainingMillis % 3600_000L) / 60_000L
                            Surface(
                                modifier = Modifier.clip(RoundedCornerShape(4.dp)),
                                color = tokens.paper3.copy(alpha = 0.8f),
                                border = BorderStroke(1.dp, tokens.beacon.copy(alpha = 0.7f)),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = tokens.beacon,
                                        modifier = Modifier.size(11.dp),
                                    )
                                    Text(
                                        text = "24h Lock (${remainingHours}h ${remainingMins}m left)",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = tokens.beacon,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    if (hasQuota) {
                        val progress = if (quotaMinutes > 0) (usedMinutes.toFloat() / quotaMinutes.toFloat()).coerceIn(0f, 1f) else 1f
                        Column {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier =
                                    Modifier
                                        .fillMaxWidth(0.9f)
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                color = if (isQuotaExhausted) tokens.clayRose else tokens.primary,
                                trackColor = tokens.ink.copy(alpha = 0.12f),
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                val labelText = if (isSharedPool) {
                                    val h = usedMinutes / 60
                                    val m = usedMinutes % 60
                                    "${h}h ${m}m of 3.5h used (Shared AI: Claude, Gemini, ChatGPT)"
                                } else {
                                    "$usedMinutes of ${quotaMinutes}m used today"
                                }
                                Text(
                                    text = labelText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isQuotaExhausted) tokens.clayRose else tokens.ink.copy(alpha = 0.7f),
                                )
                                if (isQuotaExhausted) {
                                    Text(
                                        text = "• Quota Exhausted (Locked until 4:00 AM reset)",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = tokens.clayRose,
                                    )
                                } else if (isSharedPool) {
                                    Text(
                                        text = "• Homework AI (Active)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tokens.sage.copy(alpha = 0.85f),
                                    )
                                }
                            }
                        }
                    } else {
                        val isTrackOnly = rule.scheduleMode.equals("TRACK_ONLY", ignoreCase = true)
                        Text(
                            text =
                                when {
                                    !rule.enabled -> "Rule turned off"
                                    isTrackOnly -> "${usedMinutes}m active today • Unmetered Homework Tracker (Blocked in class & curfew)"
                                    is247 -> "Always blocked (24/7 continuous guard)"
                                    else -> "Blocked during active study sessions only"
                                },
                            style = MaterialTheme.typography.labelSmall,
                            color =
                                when {
                                    !rule.enabled -> tokens.ink.copy(alpha = 0.3f)
                                    isTrackOnly -> tokens.sage.copy(alpha = 0.85f)
                                    is247 -> tokens.clayRose.copy(alpha = 0.8f)
                                    else -> tokens.primary.copy(alpha = 0.75f)
                                },
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Settings / Tune Button
                    IconButton(
                        onClick = { showTuneDialog = true },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Configure Rule Quota & Lock",
                            tint = tokens.ink.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp),
                        )
                    }

                    // Toggle Switch (intercepts click to enforce guardrails)
                    Switch(
                        checked = rule.enabled,
                        onCheckedChange = { onAttemptToggle(isQuotaExhausted, isLocked) },
                        colors =
                            SwitchDefaults.colors(
                                checkedThumbColor = if (is247) tokens.clayRose else tokens.primary,
                                checkedTrackColor = if (is247) tokens.clayRoseSoft else tokens.primary.copy(alpha = 0.4f),
                                uncheckedThumbColor = tokens.ink.copy(alpha = 0.5f),
                                uncheckedTrackColor = tokens.ink.copy(alpha = 0.1f),
                                disabledCheckedThumbColor = tokens.ink.copy(alpha = 0.4f),
                                disabledCheckedTrackColor = tokens.ink.copy(alpha = 0.2f),
                            ),
                    )

                    // Delete Button (disabled / locked icon if locked)
                    IconButton(
                        onClick = { onAttemptDelete(isLocked) },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = if (isLocked || isHardLocked) Icons.Default.Lock else Icons.Default.Delete,
                            contentDescription = if (isLocked || isHardLocked) "Rule Locked (Cannot Delete)" else "Delete Rule",
                            tint = if (isLocked || isHardLocked) tokens.beacon.copy(alpha = 0.5f) else tokens.ink.copy(alpha = 0.35f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }

    if (showTuneDialog) {
        RuleSettingsDialog(
            rule = rule,
            isLocked = isLocked,
            onDismiss = { showTuneDialog = false },
            onSave = { newQuota, lock24h ->
                onUpdateAllowance(newQuota, lock24h)
                showTuneDialog = false
            },
        )
    }
}

@Composable
private fun RuleSettingsDialog(
    rule: DesktopBlockRule,
    isLocked: Boolean,
    onDismiss: () -> Unit,
    onSave: (quotaMinutes: Int, lock24Hours: Boolean) -> Unit,
) {
    val tokens = LocalAnchorTokens.current
    var selectedQuota by remember { mutableStateOf(rule.dailyAllowanceMinutes) }
    var engageLock by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.HourglassTop,
                    contentDescription = null,
                    tint = tokens.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Allowance & Lock: ${rule.target}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = tokens.ink,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Configure daily screen time allowance for ${rule.target}. Once exhausted, it is automatically locked until the 4:00 AM daily reset.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.ink.copy(alpha = 0.8f),
                )

                // Presets
                Text(
                    text = "Daily allowance",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = tokens.primary,
                )

                val options = listOf(
                    15 to "15m",
                    30 to "30m",
                    45 to "45m",
                    60 to "60m",
                    75 to "75m",
                    90 to "90m",
                    120 to "2h",
                    -1 to "Unmetered",
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    options.take(4).forEach { (minutes, label) ->
                        // If locked, cannot increase quota beyond current quota
                        val isOptionLoosening = isLocked && rule.dailyAllowanceMinutes >= 0 && (minutes > rule.dailyAllowanceMinutes || minutes == -1)
                        OutlinedButton(
                            onClick = { selectedQuota = minutes },
                            enabled = !isOptionLoosening,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                            colors =
                                ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (selectedQuota == minutes) tokens.primary.copy(alpha = 0.3f) else Color.Transparent,
                                    contentColor = if (selectedQuota == minutes) tokens.primary else tokens.ink,
                                ),
                            border =
                                BorderStroke(
                                    1.dp,
                                    if (selectedQuota == minutes) tokens.primary else tokens.ink.copy(alpha = 0.15f),
                                ),
                        ) {
                            Text(label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    options.drop(4).forEach { (minutes, label) ->
                        val isOptionLoosening = isLocked && rule.dailyAllowanceMinutes >= 0 && (minutes > rule.dailyAllowanceMinutes || minutes == -1)
                        OutlinedButton(
                            onClick = { selectedQuota = minutes },
                            enabled = !isOptionLoosening,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                            colors =
                                ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (selectedQuota == minutes) tokens.primary.copy(alpha = 0.3f) else Color.Transparent,
                                    contentColor = if (selectedQuota == minutes) tokens.primary else tokens.ink,
                                ),
                            border =
                                BorderStroke(
                                    1.dp,
                                    if (selectedQuota == minutes) tokens.primary else tokens.ink.copy(alpha = 0.15f),
                                ),
                        ) {
                            Text(label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                // Lock section
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(AnchorSpacing.radiusCard),
                    color = if (isLocked) tokens.paper3 else tokens.ink.copy(alpha = 0.05f),
                    border = BorderStroke(1.dp, if (isLocked) tokens.beacon.copy(alpha = 0.4f) else tokens.ink.copy(alpha = 0.1f)),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        if (isLocked) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = tokens.beacon,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "24-Hour Cooling Lock Active",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = tokens.beacon,
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Rule cannot be loosened, increased, or deleted until cooling period expires. You may shorten the quota at any time.",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.ink.copy(alpha = 0.7f),
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Engage 24-Hour Cooling Lock",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = tokens.ink,
                                    )
                                    Text(
                                        text = "Prevents increasing allowance or disabling rule for 24 hours.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = tokens.ink.copy(alpha = 0.6f),
                                    )
                                }
                                Checkbox(
                                    checked = engageLock,
                                    onCheckedChange = { engageLock = it },
                                    colors =
                                        CheckboxDefaults.colors(
                                            checkedColor = tokens.beacon,
                                            checkmarkColor = tokens.beaconInk,
                                        ),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(selectedQuota, engageLock) },
                colors = ButtonDefaults.buttonColors(containerColor = tokens.primary),
                shape = RoundedCornerShape(AnchorSpacing.radiusPill),
            ) {
                Text("Save Changes", color = tokens.beaconInk, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = tokens.ink.copy(alpha = 0.7f))
            }
        },
        containerColor = tokens.surface,
    )
}

@Composable
private fun DesktopCoolingOffDialog(
    action: PendingCoolingOffAction,
    onDismiss: () -> Unit,
    onConfirmPermanent: () -> Unit,
    onConfirmTemporary15Min: (() -> Unit)? = null,
) {
    val tokens = LocalAnchorTokens.current
    var secondsRemaining by remember { mutableIntStateOf(30) }
    var reflectionNote by remember { mutableStateOf("") }

    LaunchedEffect(action) {
        secondsRemaining = 30
        while (secondsRemaining > 0) {
            delay(1000L)
            secondsRemaining -= 1
        }
    }

    val progress = (30 - secondsRemaining) / 30f

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = tokens.beacon,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Anti-Impulse Cooling-Off Pause",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = tokens.ink,
                    )
                    Text(
                        text = "Intentional airlock before modifying protection",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.ink.copy(alpha = 0.6f),
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(AnchorSpacing.radiusCard),
                    color = tokens.ink.copy(alpha = 0.05f),
                    border = BorderStroke(1.dp, tokens.ink.copy(alpha = 0.12f)),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Target Action:",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.primary,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = action.actionDescription,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = tokens.ink,
                        )
                    }
                }

                // Countdown Ticker & Grounding Message
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                        color = if (secondsRemaining == 0) tokens.sage else tokens.beacon,
                        trackColor = tokens.ink.copy(alpha = 0.1f),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    if (secondsRemaining > 0) {
                        Text(
                            text = "⏳ Pausing for $secondsRemaining seconds...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = tokens.beacon,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "ADHD dopamine cravings peak in the first 20–30 seconds. Take 3 deep, slow breaths. Ask yourself: Is this an intentional choice, or a momentary impulse?",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.ink.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        Text(
                            text = "✅ 30-Second Pause Complete",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = tokens.sage,
                        )
                        Text(
                            text = "If this is a genuine, intentional choice, you may now proceed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.ink.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                OutlinedTextField(
                    value = reflectionNote,
                    onValueChange = { reflectionNote = it },
                    placeholder = {
                        Text(
                            "Optional: What are you intending to do?",
                            color = tokens.ink.copy(alpha = 0.4f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = tokens.primary,
                            unfocusedBorderColor = tokens.ink.copy(alpha = 0.2f),
                            focusedTextColor = tokens.ink,
                            unfocusedTextColor = tokens.ink,
                        ),
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onConfirmTemporary15Min != null) {
                    OutlinedButton(
                        onClick = onConfirmTemporary15Min,
                        enabled = secondsRemaining == 0,
                        shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                contentColor = tokens.sage,
                                disabledContentColor = tokens.ink.copy(alpha = 0.3f),
                            ),
                        border = BorderStroke(1.dp, if (secondsRemaining == 0) tokens.sage else tokens.ink.copy(alpha = 0.15f)),
                    ) {
                        Text(
                            if (secondsRemaining > 0) "15m Pass (${secondsRemaining}s)" else "Unblock 15m Only",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Button(
                    onClick = onConfirmPermanent,
                    enabled = secondsRemaining == 0,
                    shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = if (secondsRemaining == 0) tokens.clayRoseSoft else tokens.ink.copy(alpha = 0.1f),
                            contentColor = tokens.ink,
                            disabledContainerColor = tokens.ink.copy(alpha = 0.08f),
                            disabledContentColor = tokens.ink.copy(alpha = 0.3f),
                        ),
                ) {
                    Text(
                        if (secondsRemaining > 0) "Disable (${secondsRemaining}s)" else "Disable Rule",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = tokens.primary,
                        contentColor = tokens.beaconInk,
                    ),
            ) {
                Text("Keep Protected (Cancel)", fontWeight = FontWeight.Bold)
            }
        },
        containerColor = tokens.surface,
    )
}

