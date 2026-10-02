package com.anchor.adhd.desktop.blocker

import com.sun.jna.Platform
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.ptr.IntByReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.awt.Robot
import java.awt.event.KeyEvent
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Information about the currently active foreground window on Windows 11.
 */
data class ActiveWindowInfo(
    val executablePath: String,
    val executableName: String,
    val windowTitle: String,
    val pid: Int,
)

/**
 * State of the Desktop App Blocker.
 */
data class BlockerDetection(
    val isBlocked: Boolean,
    val appOrSiteName: String = "",
    val reason: String = "",
    val timestampMillis: Long = 0L,
    val previousWorkContext: ActiveWindowInfo? = null,
    val isWebDistraction: Boolean = false,
)

/**
 * High-performance Windows 11 active window and process monitor.
 * Uses JNA Win32 APIs (GetForegroundWindow, QueryFullProcessImageName, GetWindowText)
 * with negligible CPU (<0.05%) running on a 250ms interval.
 */
class DesktopProcessMonitor(
    private val scope: CoroutineScope,
    private val onBlockTriggered: (BlockerDetection) -> Unit,
) {
    private var monitorJob: Job? = null

    // Session state
    @Volatile
    var isFocusActive: Boolean = false

    @Volatile
    var isStandingShieldActive: Boolean = false

    @Volatile
    var currentTaskTitle: String = "Current Focus"

    @Volatile
    var remainingMinutes: Int = 25

    // Rules
    private val blockedExecutables =
        mutableSetOf(
            "discord.exe",
            "spotify.exe",
            "telegram.exe",
            "epicgameslauncher.exe",
            "battlenet.exe",
            "leagueclient.exe",
            "riotclientservices.exe",
            "tiktok.exe",
            "whatsapp.exe",
        )

    private val blockedTitleKeywords =
        mutableSetOf(
            "youtube",
            "reddit",
            "twitter / x",
            "x.com",
            "twitch",
            "netflix",
            "instagram",
            "tiktok",
        )

    val activeBlockedExecutables: Set<String> get() = blockedExecutables.toSet()
    val activeBlockedTitleKeywords: Set<String> get() = blockedTitleKeywords.toSet()

    // Class Schedules & Lecture Shield
    @Volatile
    var classSchedules: List<com.anchor.adhd.desktop.db.DesktopClassSchedule> = emptyList()

    private val _activeClassSchedule = MutableStateFlow<com.anchor.adhd.desktop.db.DesktopClassSchedule?>(null)
    val activeClassSchedule = _activeClassSchedule.asStateFlow()

    // Full rule definitions
    @Volatile
    var ruleDefinitions: List<com.anchor.adhd.desktop.db.DesktopBlockRule> = emptyList()

    // Usage tracking map (cleanTarget -> secondsUsedToday)
    val dailyUsageMap = java.util.concurrent.ConcurrentHashMap<String, Int>()

    var onUsageRecorded: ((target: String, seconds: Int) -> Unit)? = null

    // Quick utility pass (e.g. 2-minute Discord file drop, max 5/day)
    private var quickPassTarget: String? = null
    private var quickPassExpiryMillis: Long = 0L
    var quickPassesUsedToday: Int = 0
    val maxQuickPassesPerDay: Int = 5
    val remainingQuickPasses: Int get() = (maxQuickPassesPerDay - quickPassesUsedToday).coerceAtLeast(0)

    fun getLogicalDate(now: LocalDateTime = LocalDateTime.now()): LocalDate {
        return com.anchor.adhd.desktop.db.AnchorDesktopDatabase.getLogicalDate(now)
    }

    @Volatile
    var currentLogicalDate: LocalDate = getLogicalDate()

    var onDailyRollover: (() -> Unit)? = null

    fun checkDailyRollover(now: LocalDateTime = LocalDateTime.now()): Boolean {
        val logicalDate = getLogicalDate(now)
        if (logicalDate != currentLogicalDate) {
            currentLogicalDate = logicalDate
            quickPassesUsedToday = 0
            dailyUsageMap.clear()
            updateRules(ruleDefinitions)
            onDailyRollover?.invoke()
            return true
        }
        return false
    }

    fun getEffectiveDailyUsageSeconds(rule: com.anchor.adhd.desktop.db.DesktopBlockRule): Int {
        val group = rule.quotaGroup
        return if (!group.isNullOrBlank()) {
            val groupRules = ruleDefinitions.filter { it.quotaGroup.equals(group, ignoreCase = true) }
            groupRules.sumOf { r -> getRecordedUsageSeconds(r) }
        } else {
            getRecordedUsageSeconds(rule)
        }
    }

    private fun getRecordedUsageSeconds(rule: com.anchor.adhd.desktop.db.DesktopBlockRule): Int {
        val target = rule.target.lowercase().trim()
        dailyUsageMap[target]?.let { return it }
        val legacyTarget = target.removeSuffix(".exe")
        // Older app counters omitted .exe. Never borrow a website's counter.
        val isWebCounter = ruleDefinitions.any {
            it.ruleType.equals("WEB", true) && it.target.lowercase().trim() == legacyTarget
        }
        return if (rule.ruleType.equals("APP", true) && !isWebCounter) dailyUsageMap[legacyTarget] ?: 0 else 0
    }

    // Continuous Rabbit Hole Tracker for AI Browser tabs (Gemini, Claude, ChatGPT)
    private var currentContinuousTarget: String? = null
    var currentContinuousSeconds: Int = 0
        private set
    var onRabbitHoleDetected: ((target: String, continuousMinutes: Int) -> Unit)? = null

    // AI Scope Sentinel & Distraction Tax Engine (Psych/Bio vs CS/AI/Hard Science)
    var isScopeSentinelEnabled: Boolean = true
    var availableBankedMinutes: Int = 0
    var consecutiveOutOfScopeSeconds: Int = 0
    var onOutOfScopeWarning: ((title: String, reason: String) -> Unit)? = null
    var onOutOfScopeTaxApplied: ((reason: String, minutesTaxed: Int) -> Unit)? = null
    private val scopeAiEvaluatingTitles = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun resetContinuousRabbitHoleTimer() {
        currentContinuousSeconds = 0
    }

    fun grantQuickPass(
        target: String,
        durationMillis: Long = 120_000L,
    ): Boolean {
        if (quickPassesUsedToday >= maxQuickPassesPerDay) return false
        quickPassTarget = target.lowercase().trim()
        quickPassExpiryMillis = System.currentTimeMillis() + durationMillis
        quickPassesUsedToday += 1
        clearDetection()
        return true
    }

    fun isQuickPassActive(target: String): Boolean {
        val qTarget = quickPassTarget ?: return false
        val clean = target.lowercase().trim()
        val cleanNoExe = clean.removeSuffix(".exe")
        val qTargetNoExe = qTarget.removeSuffix(".exe")
        val isMatch =
            clean == qTarget ||
                cleanNoExe == qTargetNoExe ||
                clean.contains(qTarget) ||
                qTarget.contains(clean) ||
                cleanNoExe.contains(qTargetNoExe) ||
                qTargetNoExe.contains(cleanNoExe)
        return isMatch && System.currentTimeMillis() < quickPassExpiryMillis
    }

    fun getCurrentlyActiveClass(now: java.time.LocalDateTime = java.time.LocalDateTime.now()): com.anchor.adhd.desktop.db.DesktopClassSchedule? {
        val dayOfWeek = now.dayOfWeek.value
        val minuteOfDay = now.hour * 60 + now.minute
        return classSchedules.firstOrNull { it.isCurrentlyActive(dayOfWeek, minuteOfDay) }
    }

    fun isNightCurfewActive(now: java.time.LocalTime = java.time.LocalTime.now()): Boolean {
        val minuteOfDay = now.hour * 60 + now.minute
        // 1:00 AM (60) until 7:00 AM (420)
        return minuteOfDay in 60 until 420
    }

    // Temporary emergency pass (granted via 7-second continuous hold)
    private var emergencyPassTarget: String? = null
    private var emergencyPassExpiryMillis: Long = 0L

    // Earned Leisure Pass (earned via Scaled Time-Bank from completed study focus sessions)
    @Volatile
    var earnedLeisureExpiryMillis: Long = 0L

    var onEarnedLeisureExpired: (() -> Unit)? = null

    fun activateEarnedLeisure(durationMinutes: Int = 30) {
        earnedLeisureExpiryMillis = System.currentTimeMillis() + (durationMinutes * 60_000L)
        updateRules(ruleDefinitions)
        clearDetection()
    }

    fun setEarnedLeisureExpiry(expiryMillis: Long) {
        earnedLeisureExpiryMillis = expiryMillis
        updateRules(ruleDefinitions)
        if (isEarnedLeisureActive()) {
            clearDetection()
        }
    }

    fun cancelEarnedLeisure() {
        earnedLeisureExpiryMillis = 0L
        updateRules(ruleDefinitions)
        clearDetection()
    }

    fun isEarnedLeisureActive(): Boolean {
        return System.currentTimeMillis() < earnedLeisureExpiryMillis
    }

    fun getRemainingEarnedLeisureSeconds(): Int {
        val rem = (earnedLeisureExpiryMillis - System.currentTimeMillis()) / 1000L
        return rem.coerceAtLeast(0L).toInt()
    }

    private val _currentForeground = MutableStateFlow<ActiveWindowInfo?>(null)
    val currentForeground = _currentForeground.asStateFlow()

    private val _currentDetection = MutableStateFlow<BlockerDetection?>(null)
    val currentDetection = _currentDetection.asStateFlow()

    private val _deflectedCount = MutableStateFlow(0)
    val deflectedCount = _deflectedCount.asStateFlow()

    fun recordDeflection() {
        _deflectedCount.value += 1
    }

    fun start() {
        if (monitorJob != null) return
        monitorJob =
            scope.launch(Dispatchers.IO) {
                var tick = 0
                while (isActive) {
                    if (isFocusActive || isStandingShieldActive || isScopeSentinelEnabled || blockedExecutables.isNotEmpty() || blockedTitleKeywords.isNotEmpty() || classSchedules.isNotEmpty()) {
                        evaluateForeground()
                    }
                    tick++
                    if (tick % 4 == 0) {
                        checkDailyRollover()
                        trackActiveWindowUsage()
                        if (earnedLeisureExpiryMillis > 0L && System.currentTimeMillis() >= earnedLeisureExpiryMillis) {
                            earnedLeisureExpiryMillis = 0L
                            updateRules(ruleDefinitions)
                            onEarnedLeisureExpired?.invoke()
                        }
                    }
                    delay(250)
                }
            }
    }

    fun trackActiveWindowUsage() {
        val windowInfo = _currentForeground.value ?: return
        val exeLower = windowInfo.executableName.lowercase()
        val titleLower = windowInfo.windowTitle.lowercase()
        if (exeLower.contains("java") || exeLower.contains("anchor")) return

        var matchedAiBrowserTarget: String? = null

        val activeClass = getCurrentlyActiveClass()
        for (rule in ruleDefinitions) {
            val effectivelyEnabled = rule.enabled || isStrictDailyLockdownActive || activeClass != null
            if (!effectivelyEnabled) continue
            val isTrackOnly = rule.scheduleMode.equals("TRACK_ONLY", ignoreCase = true)
            val isSharedAi = rule.scheduleMode.equals("SHARED_POOL", ignoreCase = true)
            if (rule.dailyAllowanceMinutes < 0 && !isTrackOnly && !isSharedAi) continue
            val clean = rule.target.lowercase().trim()
            val cleanNoExe = clean.removeSuffix(".exe")
            val isAppMatch = rule.ruleType.equals("APP", ignoreCase = true) &&
                (exeLower == clean || exeLower.removeSuffix(".exe") == cleanNoExe || exeLower.startsWith(cleanNoExe))
            val isWebMatch = isBrowserExecutable(exeLower) && rule.ruleType.equals("WEB", ignoreCase = true) &&
                extractWebKeywords(rule.target).any { kw -> kw.isNotBlank() && titleLower.contains(kw) }

            if (isAppMatch || isWebMatch) {
                val currentSec = dailyUsageMap[clean] ?: 0
                val updatedSec = currentSec + 1
                dailyUsageMap[clean] = updatedSec
                onUsageRecorded?.invoke(clean, 1)

                if (isSharedAi || rule.quotaGroup.equals("AI_BROWSER", ignoreCase = true)) {
                    matchedAiBrowserTarget = clean
                }

                val effectiveSec = getEffectiveDailyUsageSeconds(rule)
                if (rule.dailyAllowanceMinutes >= 0 && effectiveSec >= rule.dailyAllowanceMinutes * 60) {
                    updateRules(ruleDefinitions)
                }
                break
            }
        }

        // Continuous Rabbit Hole Tracker for AI Browser tabs (Gemini, Claude, ChatGPT)
        if (matchedAiBrowserTarget != null) {
            if (currentContinuousTarget == matchedAiBrowserTarget) {
                currentContinuousSeconds += 1
            } else {
                currentContinuousTarget = matchedAiBrowserTarget
                currentContinuousSeconds = 1
            }

            // 25 minutes continuous uninterrupted focus on AI browser tab
            if (currentContinuousSeconds >= 25 * 60) {
                onRabbitHoleDetected?.invoke(matchedAiBrowserTarget, currentContinuousSeconds / 60)
                currentContinuousSeconds = 0 // Reset after alerting so it fires again after another 25m
            }
        } else {
            // User switched to Word, Docs, Code Editor, or Anchor -> trance broken!
            currentContinuousTarget = null
            currentContinuousSeconds = 0
        }

        // Evaluate Focus Scope Sentinel during active focus sessions
        if (isFocusActive && isScopeSentinelEnabled && windowInfo.windowTitle.isNotBlank()) {
            val scopeResult = DesktopScopeSentinel.evaluateHeuristics(windowInfo.windowTitle, currentTaskTitle)
            if (scopeResult.verdict == ScopeVerdict.UNKNOWN) {
                // If not cached, trigger an async one-shot check with the local AI model
                val titleToCheck = windowInfo.windowTitle
                if (scopeAiEvaluatingTitles.add(titleToCheck)) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val aiResult = com.anchor.adhd.desktop.ai.DesktopAiEngine.evaluateScopeWithAiAsync(
                                currentTaskTitle,
                                titleToCheck,
                            )
                            if (aiResult.verdict != ScopeVerdict.UNKNOWN) {
                                DesktopScopeSentinel.recordManualClassification(
                                    titleToCheck,
                                    aiResult.verdict,
                                    aiResult.reason,
                                )
                            }
                        } finally {
                            scopeAiEvaluatingTitles.remove(titleToCheck)
                        }
                    }
                }
            }

            if (scopeResult.verdict == ScopeVerdict.OUT_OF_SCOPE) {
                consecutiveOutOfScopeSeconds += 1
                if (consecutiveOutOfScopeSeconds == 30) {
                    onOutOfScopeWarning?.invoke(windowInfo.windowTitle, scopeResult.reason)
                } else if (consecutiveOutOfScopeSeconds >= 60 && consecutiveOutOfScopeSeconds % 60 == 0) {
                    onOutOfScopeTaxApplied?.invoke(scopeResult.reason, 1)
                }
            } else if (scopeResult.verdict == ScopeVerdict.IN_SCOPE) {
                consecutiveOutOfScopeSeconds = 0
            }
        } else {
            consecutiveOutOfScopeSeconds = 0
        }
    }

    fun setUsageMap(usages: Map<String, Int>) {
        dailyUsageMap.clear()
        dailyUsageMap.putAll(usages)
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
    }

    var isStrictDailyLockdownActive: Boolean = false
        private set

    fun setStrictDailyLockdown(active: Boolean) {
        if (isStrictDailyLockdownActive != active) {
            isStrictDailyLockdownActive = active
            updateRules(ruleDefinitions)
        }
    }

    var lastBlockedHwnd: HWND? = null
        private set

    var lastAllowedWorkWindow: ActiveWindowInfo? = null
        private set

    var lastAllowedWorkHwnd: HWND? = null
        private set

    fun updateRules(rules: List<com.anchor.adhd.desktop.db.DesktopBlockRule>) {
        ruleDefinitions = rules
        val activeClass = getCurrentlyActiveClass()
        _activeClassSchedule.value = activeClass
        val isCurfew = isNightCurfewActive()

        val activeRules =
            rules.filter { rule ->
                // Anti-Bypass: During active class lectures or Strict Daily Lockdown, rules cannot be bypassed
                val effectivelyEnabled = rule.enabled || isStrictDailyLockdownActive || activeClass != null
                if (!effectivelyEnabled) return@filter false
                val isSharedAi = rule.scheduleMode.equals("SHARED_POOL", ignoreCase = true)
                val isTrackOnly = rule.scheduleMode.equals("TRACK_ONLY", ignoreCase = true)

                if (isSharedAi) {
                    // Shared AI browser pool (Gemini, ChatGPT, Claude):
                    // 1. Blocked during class lecture hours
                    // 2. Blocked during 1:00 AM - 7:00 AM night curfew
                    // 3. Blocked if shared 3.5h pool exhausted
                    // Crucially NOT blocked by isFocusActive (allowed for homework!)
                    val usedSec = getEffectiveDailyUsageSeconds(rule)
                    val isExhausted = rule.dailyAllowanceMinutes >= 0 && usedSec >= (rule.dailyAllowanceMinutes * 60)
                    activeClass != null || isCurfew || isExhausted
                } else if (isTrackOnly) {
                    activeClass != null || isCurfew
                } else if (isFocusActive) {
                    true // Enforce all enabled rules during focus/study session
                } else if (activeClass != null) {
                    // During class time, enforce all active rules to keep you in the lecture
                    true
                } else if (rule.scheduleMode.equals("ALWAYS_24_7", ignoreCase = true) ||
                    rule.scheduleMode.equals("ALWAYS", ignoreCase = true)
                ) {
                    !isEarnedLeisureActive()
                } else if (isCurfew && (rule.scheduleMode.equals("LEISURE_QUOTA", ignoreCase = true) || rule.dailyAllowanceMinutes >= 0)) {
                    true
                } else if (rule.dailyAllowanceMinutes >= 0) {
                    val usedSec = getEffectiveDailyUsageSeconds(rule)
                    usedSec >= (rule.dailyAllowanceMinutes * 60)
                } else {
                    false
                }
            }

        val appList =
            activeRules
                .filter { it.ruleType.equals("APP", ignoreCase = true) }
                .map { it.target.lowercase().trim() }
        val webList =
            activeRules
                .filter { it.ruleType.equals("WEB", ignoreCase = true) }
                .flatMap { extractWebKeywords(it.target) }

        blockedExecutables.clear()
        blockedExecutables.addAll(appList)

        blockedTitleKeywords.clear()
        blockedTitleKeywords.addAll(webList)
    }

    fun setBlockedExecutables(executables: Collection<String>) {
        blockedExecutables.clear()
        blockedExecutables.addAll(executables.map { it.lowercase().trim() })
    }

    fun setBlockedTitleKeywords(keywords: Collection<String>) {
        blockedTitleKeywords.clear()
        blockedTitleKeywords.addAll(keywords.map { it.lowercase().trim() })
    }

    fun grantEmergencyPass(
        target: String,
        durationMillis: Long = 60_000L,
    ) {
        emergencyPassTarget = target.lowercase().trim()
        emergencyPassExpiryMillis = System.currentTimeMillis() + durationMillis
        clearDetection()
    }

    fun isEmergencyPassActive(target: String): Boolean {
        val activeTarget = emergencyPassTarget ?: return false
        val cleanTarget = target.lowercase().trim()
        val cleanTargetNoExe = cleanTarget.removeSuffix(".exe")
        val activeTargetNoExe = activeTarget.removeSuffix(".exe")
        val isMatch =
            activeTarget == cleanTarget ||
                activeTargetNoExe == cleanTargetNoExe ||
                cleanTarget.contains(activeTarget) ||
                activeTarget.contains(cleanTarget) ||
                cleanTargetNoExe.contains(activeTargetNoExe) ||
                activeTargetNoExe.contains(cleanTargetNoExe)
        return isMatch && System.currentTimeMillis() < emergencyPassExpiryMillis
    }

    @Volatile
    private var tabSwitchGraceExpiryMillis: Long = 0L

    fun grantTabSwitchGrace(durationMillis: Long = 15_000L) {
        tabSwitchGraceExpiryMillis = System.currentTimeMillis() + durationMillis
        clearDetection()
    }

    fun isTabSwitchGraceActive(): Boolean {
        return System.currentTimeMillis() < tabSwitchGraceExpiryMillis
    }

    fun evaluateForeground(): BlockerDetection? {
        if (!Platform.isWindows()) {
            return null
        }

        if (isTabSwitchGraceActive()) {
            return null
        }

        val hwnd = User32.INSTANCE.GetForegroundWindow() ?: return null

        val windowInfo = getWindowInfo(hwnd) ?: return null
        _currentForeground.value = windowInfo
        val exeLower = windowInfo.executableName.lowercase()
        if (exeLower.contains("java") || exeLower.contains("anchor")) return null

        val detection = evaluateWindow(windowInfo)
        if (detection != null) {
            triggerBlock(detection, hwnd)
            return detection
        }

        // Foreground window is allowed / legitimate work app!
        lastAllowedWorkWindow = windowInfo
        lastAllowedWorkHwnd = hwnd
        if (lastBlockedHwnd != null && hwnd != lastBlockedHwnd) {
            clearDetection()
        }
        return null
    }

    // Keep rule evaluation independent of native window operations.
    internal fun evaluateWindow(windowInfo: ActiveWindowInfo): BlockerDetection? {

        // Skip Anchor itself
        val exeLower = windowInfo.executableName.lowercase()
        if (exeLower.contains("java") || exeLower.contains("anchor")) {
            return null
        }

        val activeClass = getCurrentlyActiveClass()
        _activeClassSchedule.value = activeClass
        val isCurfew = isNightCurfewActive()

        // Check 1: Executable match (e.g. Discord.exe, Steam.exe, Cursor.exe)
        val exeClean = exeLower.removeSuffix(".exe")
        for (blockedExe in blockedExecutables) {
            val cleanTarget = blockedExe.removeSuffix(".exe")
            if (exeClean == cleanTarget || exeClean.startsWith(cleanTarget)) {
                if (isEmergencyPassActive(blockedExe) || isQuickPassActive(blockedExe)) return null

                val matchedRule = ruleDefinitions.firstOrNull {
                    it.ruleType.equals("APP", true) && it.target.lowercase().trim().removeSuffix(".exe") == cleanTarget
                }
                if (isEarnedLeisureActive() && activeClass == null && (matchedRule?.scheduleMode.equals("ALWAYS_24_7", true) || matchedRule?.scheduleMode.equals("ALWAYS", true))) {
                    return null
                }
                val usedSec = matchedRule?.let { getEffectiveDailyUsageSeconds(it) } ?: 0
                val isQuotaExhausted = matchedRule != null && matchedRule.dailyAllowanceMinutes >= 0 && usedSec >= (matchedRule.dailyAllowanceMinutes * 60)

                val reasonText = when {
                    activeClass != null -> "Lecture Shield Active (${activeClass.courseCode} — ${activeClass.sessionType})"
                    isCurfew && (matchedRule?.scheduleMode.equals("LEISURE_QUOTA", ignoreCase = true) || (matchedRule?.dailyAllowanceMinutes ?: -1) >= 0) ->
                        "Night Curfew Active (10:30 PM - 7:00 AM)"
                    isQuotaExhausted ->
                        "Daily Allowance Reached (${matchedRule?.dailyAllowanceMinutes}m Used Today)"
                    isFocusActive ->
                        "Distracting Application in Focus Mode (${windowInfo.executableName})"
                    else ->
                        "Distracting Application (${windowInfo.executableName})"
                }

                val detection =
                    BlockerDetection(
                        isBlocked = true,
                        appOrSiteName = windowInfo.executableName.removeSuffix(".exe").replaceFirstChar { it.uppercase() },
                        reason = reasonText,
                        timestampMillis = System.currentTimeMillis(),
                        previousWorkContext = lastAllowedWorkWindow,
                        isWebDistraction = false,
                    )
                return detection
            }
        }

        // Check 2: Browser Window Title match (e.g. YouTube, Reddit, AnimePahe, Comix in Chrome/Edge/Brave/Firefox)
        val titleLower = windowInfo.windowTitle.lowercase()
        val activeWebRules =
            ruleDefinitions.filter { rule ->
                val effectivelyEnabled = rule.enabled || isStrictDailyLockdownActive || activeClass != null
                isBrowserExecutable(exeLower) && effectivelyEnabled && rule.ruleType.equals("WEB", ignoreCase = true)
            }

        for (rule in activeWebRules) {
            val isSharedAi = rule.scheduleMode.equals("SHARED_POOL", ignoreCase = true)
            val isTrackOnly = rule.scheduleMode.equals("TRACK_ONLY", ignoreCase = true)
            val usedSec = getEffectiveDailyUsageSeconds(rule)
            val isExhausted = rule.dailyAllowanceMinutes >= 0 && usedSec >= (rule.dailyAllowanceMinutes * 60)

            val shouldBlock =
                when {
                    isSharedAi -> activeClass != null || isCurfew || isExhausted
                    isTrackOnly -> activeClass != null || isCurfew
                    isFocusActive -> true
                    activeClass != null -> true
                    rule.scheduleMode.equals("ALWAYS_24_7", ignoreCase = true) || rule.scheduleMode.equals("ALWAYS", ignoreCase = true) ->
                        !isEarnedLeisureActive()
                    isCurfew && (rule.scheduleMode.equals("LEISURE_QUOTA", ignoreCase = true) || rule.dailyAllowanceMinutes >= 0) -> true
                    rule.dailyAllowanceMinutes >= 0 -> isExhausted
                    else -> false
                }

            if (!shouldBlock) continue

            val keywords = extractWebKeywords(rule.target)
            val matchedKeyword = keywords.firstOrNull { kw -> kw.isNotBlank() && titleLower.contains(kw) }

            if (matchedKeyword != null) {
                if (isEmergencyPassActive(rule.target) || isQuickPassActive(rule.target) ||
                    isEmergencyPassActive(matchedKeyword) || isQuickPassActive(matchedKeyword)
                ) {
                    return null
                }

                val displayName =
                    (if (matchedKeyword.length > 2) matchedKeyword else rule.target)
                        .replaceFirstChar { it.uppercase() }

                val reasonText =
                    when {
                        activeClass != null -> "Lecture Shield Active (${activeClass.courseCode} — ${activeClass.sessionType})"
                        isCurfew && (rule.scheduleMode.equals("LEISURE_QUOTA", ignoreCase = true) || rule.dailyAllowanceMinutes >= 0) ->
                            "Night Curfew Active (10:30 PM - 7:00 AM)"
                        isExhausted ->
                            "Daily Allowance Reached (${rule.dailyAllowanceMinutes}m Used Today)"
                        isFocusActive ->
                            "Distracting Website in Focus Mode ($displayName)"
                        else ->
                            "Distracting Website ($displayName)"
                    }

                val detection =
                    BlockerDetection(
                        isBlocked = true,
                        appOrSiteName = displayName,
                        reason = reasonText,
                        timestampMillis = System.currentTimeMillis(),
                        previousWorkContext = lastAllowedWorkWindow,
                        isWebDistraction = true,
                    )
                return detection
            }
        }

        // Scope is a study-session boundary, independent of the leisure bank.
        if (isFocusActive && isScopeSentinelEnabled && windowInfo.windowTitle.isNotBlank()) {
            val scopeResult = DesktopScopeSentinel.evaluateHeuristics(windowInfo.windowTitle, currentTaskTitle)
            if (scopeResult.verdict == ScopeVerdict.UNKNOWN) {
                val titleToCheck = windowInfo.windowTitle
                if (scopeAiEvaluatingTitles.add(titleToCheck)) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val aiResult = com.anchor.adhd.desktop.ai.DesktopAiEngine.evaluateScopeWithAiAsync(
                                currentTaskTitle,
                                titleToCheck,
                            )
                            if (aiResult.verdict != ScopeVerdict.UNKNOWN) {
                                DesktopScopeSentinel.recordManualClassification(
                                    titleToCheck,
                                    aiResult.verdict,
                                    aiResult.reason,
                                )
                            }
                        } finally {
                            scopeAiEvaluatingTitles.remove(titleToCheck)
                        }
                    }
                }
            }

            if (scopeResult.verdict == ScopeVerdict.OUT_OF_SCOPE) {
                if (isEmergencyPassActive(scopeResult.category) || isQuickPassActive(scopeResult.category) ||
                    isEmergencyPassActive(windowInfo.windowTitle) || isQuickPassActive(windowInfo.windowTitle)
                ) {
                    return null
                }

                val titleLower = windowInfo.windowTitle.lowercase()
                val displayName = when {
                    titleLower.contains("hugging face") || titleLower.contains("huggingface") -> "Hugging Face"
                    titleLower.contains("github") -> "GitHub"
                    titleLower.contains("arena") || titleLower.contains("lmsys") -> "LMSYS Chatbot Arena"
                    titleLower.contains("artificial analysis") || titleLower.contains("artificialanalysis") -> "Artificial Analysis"
                    titleLower.contains("openrouter") -> "OpenRouter"
                    titleLower.contains("papers with code") || titleLower.contains("paperswithcode") -> "Papers with Code"
                    titleLower.contains("swe-bench") || titleLower.contains("swebench") -> "SWE-bench"
                    titleLower.contains("leaderboard") || titleLower.contains("benchmark") -> "AI Benchmark / Leaderboard"
                    titleLower.contains("kaggle") -> "Kaggle"
                    titleLower.contains("wandb") || titleLower.contains("weights & biases") -> "Weights & Biases"
                    titleLower.contains("plato") || titleLower.contains("stanford encyclopedia") -> "Stanford Philosophy (Plato)"
                    titleLower.contains("arxiv") -> "ArXiv"
                    titleLower.contains("leetcode") -> "LeetCode"
                    titleLower.contains("chatgpt") || titleLower.contains("openai") -> "ChatGPT"
                    titleLower.contains("claude.ai") -> "Claude"
                    titleLower.contains("groq") -> "Groq"
                    titleLower.contains("ollama") -> "Ollama"
                    titleLower.contains("vllm") -> "vLLM"
                    titleLower.contains("runpod") -> "RunPod"
                    else -> scopeResult.category
                }

                val reasonText = when {
                    activeClass != null -> "Lecture Shield Active (${activeClass.courseCode} — ${activeClass.sessionType})"
                    else -> "Out-of-Scope in Focus Mode (${scopeResult.category})"
                }

                val detection =
                    BlockerDetection(
                        isBlocked = true,
                        appOrSiteName = displayName,
                        reason = reasonText,
                        timestampMillis = System.currentTimeMillis(),
                        previousWorkContext = lastAllowedWorkWindow,
                        isWebDistraction = isBrowserExecutable(exeLower),
                    )
                return detection
            }
        }

        return null
    }

    private fun triggerBlock(
        detection: BlockerDetection,
        hwnd: HWND,
    ) {
        // Enforce physical lockout on the offending window immediately via Win32 SW_MINIMIZE.
        // This ensures distracting apps and tabs (e.g. Hugging Face, GitHub, Plato) disappear instantly
        // and cannot bypass the lockout even if Windows 11 focus stealing prevention is active.
        try {
            User32.INSTANCE.ShowWindow(hwnd, WinUser.SW_MINIMIZE)
        } catch (_: Throwable) {}

        val current = _currentDetection.value
        if (current != null && current.appOrSiteName == detection.appOrSiteName && lastBlockedHwnd == hwnd) {
            return
        }
        lastBlockedHwnd = hwnd
        _currentDetection.value = detection
        _deflectedCount.value += 1

        bringAnchorToFront()
        onBlockTriggered(detection)
    }

    /**
     * Minimizes the distracting window gracefully via Win32 SW_MINIMIZE.
     */
    fun minimizeDistractionWindow() {
        if (!Platform.isWindows()) return
        val hwnd = lastBlockedHwnd ?: User32.INSTANCE.GetForegroundWindow()
        if (hwnd != null) {
            User32.INSTANCE.ShowWindow(hwnd, WinUser.SW_MINIMIZE)
        }
        clearDetection()
    }

    /**
     * Gracefully closes only the offending browser tab via Ctrl+W dispatched to the target window.
     * Keeps all other browser tabs, work documents, and windows intact.
     */
    fun closeActiveBrowserTab(hwnd: HWND? = lastBlockedHwnd): Boolean {
        grantTabSwitchGrace(5_000L)
        val targetHwnd = hwnd ?: lastBlockedHwnd ?: (if (Platform.isWindows()) User32.INSTANCE.GetForegroundWindow() else null) ?: return false
        return try {
            if (Platform.isWindows()) {
                User32.INSTANCE.ShowWindow(targetHwnd, WinUser.SW_RESTORE)
                User32.INSTANCE.SetForegroundWindow(targetHwnd)
                Thread.sleep(80)
                val robot = Robot()
                robot.keyPress(KeyEvent.VK_CONTROL)
                robot.keyPress(KeyEvent.VK_W)
                robot.keyRelease(KeyEvent.VK_W)
                robot.keyRelease(KeyEvent.VK_CONTROL)
            }
            clearDetection()
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Restores the distracting browser window and grants a grace window so the user
     * can manually switch to their legitimate work tab or close tabs without Anchor re-triggering.
     */
    fun restoreDistractionWindowForTabSwitch(
        hwnd: HWND? = lastBlockedHwnd,
        durationMillis: Long = 15_000L,
    ): Boolean {
        grantTabSwitchGrace(durationMillis)
        val targetHwnd = hwnd ?: lastBlockedHwnd ?: (if (Platform.isWindows()) User32.INSTANCE.GetForegroundWindow() else null) ?: return false
        return try {
            if (Platform.isWindows()) {
                User32.INSTANCE.ShowWindow(targetHwnd, WinUser.SW_RESTORE)
                User32.INSTANCE.SetForegroundWindow(targetHwnd)
            }
            clearDetection()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun clearDetection() {
        _currentDetection.value = null
        lastBlockedHwnd = null
    }

    /**
     * Seamless Context-Bridge: restores the work window you were just in before the distraction.
     */
    fun restorePreviousWorkWindow(): Boolean {
        if (!Platform.isWindows()) return false
        val hwnd = lastAllowedWorkHwnd ?: return false
        return try {
            User32.INSTANCE.ShowWindow(hwnd, WinUser.SW_RESTORE)
            User32.INSTANCE.SetForegroundWindow(hwnd)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Brings Anchor window forward via Win32 SW_RESTORE and SetForegroundWindow.
     */
    fun bringAnchorToFront() {
        if (!Platform.isWindows()) return
        val anchorHwnd = User32.INSTANCE.FindWindow(null, "Anchor ADHD — Living Maritime Focus")
        if (anchorHwnd != null) {
            User32.INSTANCE.ShowWindow(anchorHwnd, WinUser.SW_RESTORE)
            User32.INSTANCE.SetForegroundWindow(anchorHwnd)
        }
    }

    private fun getWindowInfo(hwnd: HWND): ActiveWindowInfo? {
        val pidRef = IntByReference()
        User32.INSTANCE.GetWindowThreadProcessId(hwnd, pidRef)
        val pid = pidRef.value
        if (pid <= 0) return null

        // Get process executable path
        val hProcess =
            Kernel32.INSTANCE.OpenProcess(
                WinNT.PROCESS_QUERY_LIMITED_INFORMATION,
                false,
                pid,
            ) ?: return null

        val pathBuf = CharArray(1024)
        val sizeRef = IntByReference(pathBuf.size)
        val fullPath =
            try {
                val success =
                    Kernel32.INSTANCE.QueryFullProcessImageName(
                        hProcess,
                        0,
                        pathBuf,
                        sizeRef,
                    )
                if (success) {
                    String(pathBuf, 0, sizeRef.value)
                } else {
                    ""
                }
            } finally {
                Kernel32.INSTANCE.CloseHandle(hProcess)
            }

        val exeName = if (fullPath.isNotEmpty()) File(fullPath).name else "Unknown"

        // Get window title
        val titleBuf = CharArray(1024)
        val titleLen = User32.INSTANCE.GetWindowText(hwnd, titleBuf, 1024)
        val title = if (titleLen > 0) String(titleBuf, 0, titleLen).trim() else ""

        return ActiveWindowInfo(
            executablePath = fullPath,
            executableName = exeName,
            windowTitle = title,
            pid = pid,
        )
    }

    companion object {
        private val browserExecutables = setOf(
            "chrome.exe", "msedge.exe", "firefox.exe", "brave.exe", "opera.exe",
            "vivaldi.exe", "chromium.exe", "arc.exe", "zen.exe", "waterfox.exe", "floorp.exe",
        )

        internal fun isBrowserExecutable(executableName: String): Boolean =
            executableName.lowercase() in browserExecutables

        /**
         * Robust multi-pattern keyword extractor for web blocking rules.
         * Extracts domain, subdomain brand names, root names, and platform aliases
         * so that URLs like "https://animepahe.pw/" or "comix.to" or "twitter / x"
         * accurately match browser window titles (e.g. "AnimePahe :: Watch Anime", "Solo Leveling - Comix").
         */
        fun extractWebKeywords(rawTarget: String): Set<String> {
            val target = rawTarget.lowercase().trim()
            if (target.isBlank()) return emptySet()

            val keywords = mutableSetOf<String>()

            // 1. Special case: Twitter / X
            if (target.contains("twitter") || target.contains("/ x") || target == "x.com" || target == "x") {
                keywords.add("twitter")
                keywords.add("x.com")
                keywords.add(" / x")
                keywords.add("on x")
                keywords.add(" - x")
                keywords.add(" | x")
            }

            // 2. Strip scheme (http://, https://)
            var cleaned = target
            if (cleaned.startsWith("https://")) cleaned = cleaned.removePrefix("https://")
            if (cleaned.startsWith("http://")) cleaned = cleaned.removePrefix("http://")

            // Strip www.
            if (cleaned.startsWith("www.")) cleaned = cleaned.removePrefix("www.")

            // Strip path, query params, hash
            val slashIdx = cleaned.indexOf('/')
            if (slashIdx >= 0) {
                cleaned = cleaned.substring(0, slashIdx)
            }
            val queryIdx = cleaned.indexOf('?')
            if (queryIdx >= 0) {
                cleaned = cleaned.substring(0, queryIdx)
            }
            val hashIdx = cleaned.indexOf('#')
            if (hashIdx >= 0) {
                cleaned = cleaned.substring(0, hashIdx)
            }
            cleaned = cleaned.trim()

            if (cleaned.isNotBlank()) {
                keywords.add(cleaned) // e.g. "animepahe.pw" or "comix.to" or "youtube.com"

                // 3. Extract core domain / brand name if it has a TLD dot
                if (cleaned.contains(".")) {
                    val parts = cleaned.split(".")
                    val brandCandidate =
                        if (parts.size >= 3 &&
                            parts[0] in listOf("m", "mobile", "old", "new", "np", "www", "app", "web", "login", "beta", "en", "ww", "w1", "w2")
                        ) {
                            parts[1]
                        } else {
                            parts[0]
                        }

                    if (brandCandidate.length >= 2) {
                        keywords.add(brandCandidate) // e.g. "animepahe", "comix", "youtube", "reddit"

                        // If brand name has hyphens (e.g. read-comic), also add spaced version ("read comic")
                        if (brandCandidate.contains("-")) {
                            keywords.add(brandCandidate.replace('-', ' '))
                        }
                    }
                }
            }

            // 4. Also add raw target if it had no domain or slash (e.g. "youtube", "reddit")
            if (!target.contains(".") && !target.contains("/")) {
                keywords.add(target)
            }

            return keywords.filter { it.isNotBlank() }.toSet()
        }
    }
}
