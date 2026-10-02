package com.anchor.adhd.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.anchor.adhd.desktop.ai.DesktopAiEngine
import com.anchor.adhd.desktop.ai.ResetType
import com.anchor.adhd.desktop.blocker.BlockerDetection
import com.anchor.adhd.desktop.blocker.DesktopProcessMonitor
import com.anchor.adhd.desktop.blocker.DesktopRescueOverlay
import com.anchor.adhd.desktop.blocker.ScaledTimeBank
import com.anchor.adhd.desktop.blocker.TimeBankStatus
import com.anchor.adhd.desktop.db.AnchorDesktopDatabase
import com.anchor.adhd.desktop.platform.DesktopAutostartManager
import com.anchor.adhd.desktop.platform.DesktopFullscreenManager
import com.anchor.adhd.desktop.prefs.DesktopUserPreferences
import com.anchor.adhd.desktop.theme.AnchorColors
import com.anchor.adhd.desktop.theme.AnchorTheme
import com.anchor.adhd.desktop.ui.DesktopAiScreen
import com.anchor.adhd.desktop.ui.DesktopAirlockDialog
import com.anchor.adhd.desktop.ui.DesktopBlockerScreen
import com.anchor.adhd.desktop.ui.DesktopCustomDurationDialog
import com.anchor.adhd.desktop.ui.DesktopFloatingIslandWindow
import com.anchor.adhd.desktop.ui.DesktopFocusScreen
import com.anchor.adhd.desktop.ui.DesktopHomeDeck
import com.anchor.adhd.desktop.ui.DesktopNavigationSidebar
import com.anchor.adhd.desktop.ui.DesktopPlanScreen
import com.anchor.adhd.desktop.ui.DesktopScreen
import com.anchor.adhd.desktop.ui.DesktopSettingsScreen
import com.anchor.adhd.desktop.ui.DesktopSyllabusScreen
import com.anchor.adhd.desktop.ui.DesktopTaskbarIntegration
import com.anchor.adhd.desktop.update.DesktopUpdateManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private var singleInstanceLock: java.nio.channels.FileLock? = null
private var singleInstanceChannel: java.io.RandomAccessFile? = null

private fun acquireSingleInstanceLock(): Boolean {
    return try {
        val appData = System.getenv("APPDATA") ?: (System.getProperty("user.home") + "/AppData/Roaming")
        val dir = java.io.File(appData, "Anchor")
        if (!dir.exists()) dir.mkdirs()
        val lockFile = java.io.File(dir, "anchor.lock")
        val raf = java.io.RandomAccessFile(lockFile, "rw")
        val lock = raf.channel.tryLock()
        if (lock != null) {
            singleInstanceLock = lock
            singleInstanceChannel = raf
            true
        } else {
            raf.close()
            false
        }
    } catch (_: Exception) {
        true
    }
}

private fun restoreExistingAnchorWindow() {
    if (com.sun.jna.Platform.isWindows()) {
        try {
            val anchorHwnd = com.sun.jna.platform.win32.User32.INSTANCE.FindWindow(null, "Anchor ADHD — Living Maritime Focus")
            if (anchorHwnd != null) {
                com.sun.jna.platform.win32.User32.INSTANCE.ShowWindow(anchorHwnd, com.sun.jna.platform.win32.WinUser.SW_RESTORE)
                com.sun.jna.platform.win32.User32.INSTANCE.SetForegroundWindow(anchorHwnd)
            }
        } catch (_: Throwable) {
        }
    }
}

fun main(args: Array<String> = emptyArray()) {
    if (!acquireSingleInstanceLock()) {
        restoreExistingAnchorWindow()
        return
    }

    val prefs = DesktopUserPreferences()
    val initialStartMinimized = runBlocking {
        try {
            prefs.getStartMinimizedSnapshot()
        } catch (_: Exception) {
            true
        }
    }
    val initialAutostart = runBlocking {
        try {
            prefs.getAutostartOnBootSnapshot()
        } catch (_: Exception) {
            true
        }
    }

    if (DesktopAutostartManager.isSupported()) {
        try {
            DesktopAutostartManager.setAutostart(initialAutostart, startMinimized = initialStartMinimized)
        } catch (_: Exception) {
        }
    }

    val isCliMinimized = args.any {
        it.equals("--minimized", ignoreCase = true) ||
            it.equals("-minimized", ignoreCase = true) ||
            it.equals("--startup", ignoreCase = true) ||
            it.equals("/minimized", ignoreCase = true)
    }

    val shouldStartMinimized = isCliMinimized || initialStartMinimized

    application {
        val mainWindowState =
            rememberWindowState(
                placement = WindowPlacement.Maximized,
                isMinimized = shouldStartMinimized,
                width = 1200.dp,
                height = 860.dp,
            )
        var isFullscreen by remember { mutableStateOf(false) }
        var appWindow by remember { mutableStateOf<java.awt.Window?>(null) }

        val toggleFullscreen: () -> Unit = {
            appWindow?.let { win ->
                isFullscreen = DesktopFullscreenManager.toggleFullscreen(win)
            }
        }

        var activeDistraction by remember { mutableStateOf<BlockerDetection?>(null) }
        var currentTaskTitle by remember { mutableStateOf("Deep Work") }
        var remainingMinutes by remember { mutableIntStateOf(25) }
        var onReturnToFocusCallback by remember { mutableStateOf<(() -> Unit)?>(null) }
        var onCloseTabCallback by remember { mutableStateOf<(() -> Unit)?>(null) }
        var onSwitchTabGraceCallback by remember { mutableStateOf<(() -> Unit)?>(null) }
        var onQuickThoughtScribeCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }
        var onEmergencyPassCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }
        var onQuickPassCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }
        var onUnlockWithTimeBankCallback by remember { mutableStateOf<((Int) -> Unit)?>(null) }
        var timeBankStatusState by remember { mutableStateOf<TimeBankStatus?>(null) }
        var quickPassesRemaining by remember { mutableIntStateOf(5) }

        Window(
            onCloseRequest = {
                appWindow?.let { win ->
                    if (isFullscreen) {
                        DesktopFullscreenManager.exitFullscreen(win)
                    }
                    // Anti-Bypass: Minimize window rather than terminating Anchor
                    // Keeps lecture shields, daily quotas, and monitor running in background!
                    mainWindowState.isMinimized = true
                    (win as? java.awt.Frame)?.state = java.awt.Frame.ICONIFIED
                }
            },
            state = mainWindowState,
            title = "Anchor ADHD — Living Maritime Focus",
            onPreviewKeyEvent = { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.F11 -> {
                            toggleFullscreen()
                            true
                        }
                        Key.Escape -> {
                            if (isFullscreen) {
                                toggleFullscreen()
                                true
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                } else {
                    false
                }
            },
        ) {
            LaunchedEffect(this.window) {
                val win = this@Window.window
                appWindow = win
                if (shouldStartMinimized) {
                    mainWindowState.isMinimized = true
                    (win as? java.awt.Frame)?.state = java.awt.Frame.ICONIFIED
                } else {
                    // Force Anchor to start in full screen when not launched minimized
                    var attempts = 0
                    while (!isFullscreen && attempts < 10) {
                        if (win.isDisplayable) {
                            isFullscreen = DesktopFullscreenManager.enterFullscreen(win)
                            if (isFullscreen) break
                        }
                        attempts++
                        delay(50)
                    }
                }
            }

            AnchorTheme {
                DesktopAnchorApp(
                    window = this.window,
                    isFullscreen = isFullscreen,
                    onToggleFullscreen = toggleFullscreen,
                    onDistractionDetected = { detection, taskTitle, mins ->
                        activeDistraction = detection
                        currentTaskTitle = taskTitle
                        remainingMinutes = mins
                    },
                    onDistractionCleared = {
                        activeDistraction = null
                    },
                    onRegisterRescueActions = { onReturn, onCloseTab, onSwitchGrace, onScribe, onPass, onQuickPass, remaining, timeBank, onUnlockTimeBank ->
                        onReturnToFocusCallback = onReturn
                        onCloseTabCallback = onCloseTab
                        onSwitchTabGraceCallback = onSwitchGrace
                        onQuickThoughtScribeCallback = onScribe
                        onEmergencyPassCallback = onPass
                        onQuickPassCallback = onQuickPass
                        quickPassesRemaining = remaining
                        timeBankStatusState = timeBank
                        onUnlockWithTimeBankCallback = onUnlockTimeBank
                    },
                )
            }
        }

        // Windows 11 Native Always-On-Top Maximized Rescue Shield Window
        // Covers distracting apps (Discord, YouTube, Reddit, etc.) completely across the screen
        val distraction = activeDistraction
        if (distraction != null) {
            val rescueWindowState = rememberWindowState(placement = WindowPlacement.Maximized)
            Window(
                onCloseRequest = {
                    onReturnToFocusCallback?.invoke()
                    activeDistraction = null
                },
                state = rescueWindowState,
                title = "Anchor — Gentle Focus Rescue",
                alwaysOnTop = true,
                undecorated = true,
                resizable = false,
            ) {
                LaunchedEffect(this.window) {
                    val win = this@Window.window
                    win.toFront()
                    win.requestFocus()
                    if (com.sun.jna.Platform.isWindows()) {
                        try {
                            val windowPointer = com.sun.jna.Native.getWindowPointer(win)
                            if (windowPointer != null) {
                                val hwnd = com.sun.jna.platform.win32.WinDef.HWND(windowPointer)
                                com.sun.jna.platform.win32.User32.INSTANCE.SetWindowPos(
                                    hwnd,
                                    com.sun.jna.platform.win32.WinDef.HWND(com.sun.jna.Pointer.createConstant(-1)), // HWND_TOPMOST
                                    0,
                                    0,
                                    0,
                                    0,
                                    com.sun.jna.platform.win32.WinUser.SWP_NOMOVE or
                                        com.sun.jna.platform.win32.WinUser.SWP_NOSIZE or
                                        com.sun.jna.platform.win32.WinUser.SWP_SHOWWINDOW,
                                )
                                com.sun.jna.platform.win32.User32.INSTANCE.SetForegroundWindow(hwnd)
                            }
                        } catch (_: Throwable) {}
                    }
                }
                AnchorTheme {
                    DesktopRescueOverlay(
                        detection = distraction,
                        taskTitle = currentTaskTitle,
                        remainingMinutes = remainingMinutes,
                        onReturnToFocus = {
                            onReturnToFocusCallback?.invoke()
                            activeDistraction = null
                            mainWindowState.isMinimized = false
                        },
                        onCloseTab = {
                            onCloseTabCallback?.invoke()
                            activeDistraction = null
                        },
                        onSwitchTabGrace = {
                            onSwitchTabGraceCallback?.invoke()
                            activeDistraction = null
                        },
                        onQuickThoughtScribe = { thought ->
                            onQuickThoughtScribeCallback?.invoke(thought)
                            activeDistraction = null
                        },
                        onEmergencyPass = { target ->
                            onEmergencyPassCallback?.invoke(target)
                            activeDistraction = null
                        },
                        onQuickPass = { target ->
                            onQuickPassCallback?.invoke(target)
                            quickPassesRemaining = (quickPassesRemaining - 1).coerceAtLeast(0)
                            activeDistraction = null
                        },
                        quickPassesRemaining = quickPassesRemaining,
                        timeBankStatus = timeBankStatusState,
                        onUnlockWithTimeBank = { minutesToSpend ->
                            onUnlockWithTimeBankCallback?.invoke(minutesToSpend)
                            activeDistraction = null
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun DesktopAnchorApp(
    window: java.awt.Window? = null,
    isFullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {},
    onDistractionDetected: (BlockerDetection, String, Int) -> Unit = { _, _, _ -> },
    onDistractionCleared: () -> Unit = {},
    onRegisterRescueActions: (
        (
            onReturnToFocus: () -> Unit,
            onCloseTab: () -> Unit,
            onSwitchTabGrace: () -> Unit,
            onQuickThoughtScribe: (String) -> Unit,
            onEmergencyPass: (String) -> Unit,
            onQuickPass: (String) -> Unit,
            quickPassesRemaining: Int,
            timeBankStatus: TimeBankStatus?,
            onUnlockWithTimeBank: (Int) -> Unit,
        ) -> Unit
    )? = null,
) {
    val scope = rememberCoroutineScope()
    val db = remember { AnchorDesktopDatabase(scope) }
    val prefs = remember { DesktopUserPreferences() }

    // Navigation State
    var currentScreen by remember { mutableStateOf(DesktopScreen.GROVE) }

    // Database Reactive States
    val tasks by db.tasks.collectAsState()
    val funLinks by db.funLinks.collectAsState()
    val harborState by db.harborState.collectAsState()
    val blockRules by db.blockRules.collectAsState()
    val classSchedules by db.classSchedules.collectAsState()
    val dailyUsages by db.dailyUsages.collectAsState()

    // Focus & Preferences State
    var durationMinutes by remember { mutableIntStateOf(25) }
    var isFocusActive by remember { mutableStateOf(false) }
    var remainingSeconds by remember { mutableIntStateOf(25 * 60) }
    var timerSessionId by remember { mutableIntStateOf(0) }
    var prefilledAiTask by remember { mutableStateOf("") }
    val standingShieldEnabled by prefs.standingShieldEnabled.collectAsState(initial = false)

    var showAirlock by remember { mutableStateOf(false) }
    var showDurationPicker by remember { mutableStateOf(false) }
    var isFloatingIslandVisible by remember { mutableStateOf(false) }
    var activeRabbitHoleTarget by remember { mutableStateOf<String?>(null) }
    var activeRabbitHoleMinutes by remember { mutableIntStateOf(25) }

    val hasUpdateBadge by DesktopUpdateManager.hasUpdateBadge.collectAsState()

    val currentTask = tasks.firstOrNull { !it.isCompleted }

    // Windows 11 Native Blocker Monitor
    val processMonitor =
        remember {
            lateinit var monitor: DesktopProcessMonitor
            monitor =
                DesktopProcessMonitor(scope) { detection ->
                    java.awt.EventQueue.invokeLater {
                        onDistractionDetected(
                            detection,
                            monitor.currentTaskTitle,
                            monitor.remainingMinutes,
                        )
                    }
                }
            monitor
        }

    val deflectedCount by processMonitor.deflectedCount.collectAsState()
    var todayFocusMinutes by remember { mutableIntStateOf(0) }
    val earnedLeisureBaseStudyMinutes by prefs.earnedLeisureBaseStudyMinutes.collectAsState(initial = 60)
    val earnedLeisureSpentMinutesToday by prefs.earnedLeisureSpentMinutesToday.collectAsState(initial = 0)
    val earnedLeisureExpiryEpoch by prefs.earnedLeisureExpiryEpoch.collectAsState(initial = 0L)
    val scopeSentinelEnabled by prefs.scopeSentinelEnabled.collectAsState(initial = true)
    val scopeTaxMinutesToday by prefs.scopeTaxMinutesToday.collectAsState(initial = 0)

    val timeBankStatus = remember(todayFocusMinutes, earnedLeisureSpentMinutesToday, earnedLeisureBaseStudyMinutes) {
        ScaledTimeBank.calculateStatus(
            focusMinutesToday = todayFocusMinutes,
            spentMinutesToday = earnedLeisureSpentMinutesToday,
            baseMinutes = earnedLeisureBaseStudyMinutes,
        )
    }

    val handleUnlockWithTimeBank: (Int) -> Unit = { minutesToSpend ->
        scope.launch {
            val currentSpent = prefs.getEarnedLeisureSpentMinutesTodaySnapshot()
            val newSpent = currentSpent + minutesToSpend
            prefs.setEarnedLeisureSpentMinutesToday(newSpent)
            val expiry = System.currentTimeMillis() + (minutesToSpend * 60_000L)
            prefs.setEarnedLeisureExpiryEpoch(expiry)
            processMonitor.activateEarnedLeisure(minutesToSpend)
            onDistractionCleared()
        }
    }

    LaunchedEffect(isFocusActive, tasks) {
        todayFocusMinutes = db.getTodayFocusMinutes()
    }

    // Windows 11 Taskbar Progress Integration
    LaunchedEffect(isFocusActive, remainingSeconds, durationMinutes) {
        DesktopTaskbarIntegration.updateProgress(window, isFocusActive, remainingSeconds, durationMinutes * 60)
    }

    // Windows 11 Floating Island Micro-HUD Window
    DesktopFloatingIslandWindow(
        visible = isFloatingIslandVisible,
        taskTitle = currentTask?.title ?: "Deep Work",
        isFocusActive = isFocusActive,
        remainingSeconds = remainingSeconds,
        onToggleFocus = { isFocusActive = !isFocusActive },
        onExpandMainApp = {
            processMonitor.bringAnchorToFront()
            isFloatingIslandVisible = false
        },
        onCloseHud = {
            isFloatingIslandVisible = false
        },
    )

    val updateRescueActions: () -> Unit = {
        onRegisterRescueActions?.invoke(
            {
                if (processMonitor.currentDetection.value?.isWebDistraction == true) {
                    processMonitor.closeActiveBrowserTab()
                } else {
                    processMonitor.minimizeDistractionWindow()
                }
                val restored = processMonitor.restorePreviousWorkWindow()
                if (!restored) {
                    processMonitor.bringAnchorToFront()
                }
                onDistractionCleared()
                if (processMonitor.isFocusActive) {
                    currentScreen = DesktopScreen.FOCUS
                }
            },
            {
                processMonitor.closeActiveBrowserTab()
                val restored = processMonitor.restorePreviousWorkWindow()
                if (!restored) {
                    processMonitor.bringAnchorToFront()
                }
                onDistractionCleared()
            },
            {
                processMonitor.restoreDistractionWindowForTabSwitch(durationMillis = 15_000L)
                onDistractionCleared()
            },
            { thought ->
                scope.launch {
                    db.insertTask(thought, durationMinutes = 5)
                }
                if (processMonitor.currentDetection.value?.isWebDistraction == true) {
                    processMonitor.closeActiveBrowserTab()
                } else {
                    processMonitor.minimizeDistractionWindow()
                }
                val restored = processMonitor.restorePreviousWorkWindow()
                if (!restored) {
                    processMonitor.bringAnchorToFront()
                }
                onDistractionCleared()
            },
            { target ->
                processMonitor.grantEmergencyPass(target, 60_000L)
                onDistractionCleared()
            },
            { target ->
                processMonitor.grantQuickPass(target, 120_000L)
                onDistractionCleared()
            },
            processMonitor.remainingQuickPasses,
            timeBankStatus,
            handleUnlockWithTimeBank,
        )
    }

    LaunchedEffect(timeBankStatus, processMonitor.remainingQuickPasses) {
        updateRescueActions()
    }

    LaunchedEffect(earnedLeisureExpiryEpoch) {
        processMonitor.setEarnedLeisureExpiry(earnedLeisureExpiryEpoch)
    }

    LaunchedEffect(Unit) {
        processMonitor.onEarnedLeisureExpired = {
            scope.launch {
                prefs.setEarnedLeisureExpiryEpoch(0L)
                updateRescueActions()
            }
        }
    }

    // Initialize Database, Preferences, AI Engine, and register callbacks
    LaunchedEffect(Unit) {
        db.initialize()
        DesktopAiEngine.connectSyllabus(db.syllabusItems, db.courses)
        durationMinutes = prefs.getFocusWorkMinutesSnapshot()
        remainingSeconds = durationMinutes * 60

        // Initialize On-Device Claude-Opus-Fable5 AI Engine (Q8_0)
        val aiEnabled = prefs.getAiEnabledSnapshot()
        val aiModel = prefs.getAiModelPathSnapshot()
        val aiBinary = prefs.getAiBinaryPathSnapshot()
        val aiThreads = prefs.getAiThreadsSnapshot()
        val aiPort = prefs.getAiPortSnapshot()
        DesktopAiEngine.initialize(
            scope = scope,
            modelPath = aiModel,
            binaryPath = aiBinary,
            port = aiPort,
            threads = aiThreads,
            enabled = aiEnabled,
        )

        // Initialize Update Manager (silent background check on start)
        DesktopUpdateManager.initialize(scope, checkImmediately = true)

        updateRescueActions()
    }

    // Connect Monitor State & Rules
    val has247Rules =
        blockRules.any {
            it.enabled &&
                (it.scheduleMode.equals("ALWAYS_24_7", ignoreCase = true) || it.scheduleMode.equals("ALWAYS", ignoreCase = true))
        }
    val hasSchedulesOrQuotas =
        classSchedules.isNotEmpty() ||
            blockRules.any { it.enabled && (it.dailyAllowanceMinutes >= 0 || it.scheduleMode.equals("LEISURE_QUOTA", ignoreCase = true)) }

    val strictLockdownUntil by prefs.strictLockdownUntilEpoch.collectAsState(0L)
    val isStrictLockdownActive = strictLockdownUntil > System.currentTimeMillis()

    LaunchedEffect(isFocusActive, standingShieldEnabled, has247Rules, hasSchedulesOrQuotas, isStrictLockdownActive, currentTask, remainingSeconds, blockRules, classSchedules, dailyUsages, scopeSentinelEnabled, timeBankStatus) {
        processMonitor.isFocusActive = isFocusActive
        processMonitor.isStandingShieldActive = standingShieldEnabled || has247Rules
        processMonitor.setStrictDailyLockdown(isStrictLockdownActive)
        processMonitor.isScopeSentinelEnabled = scopeSentinelEnabled
        processMonitor.availableBankedMinutes = timeBankStatus.availableBankedMinutes
        processMonitor.currentTaskTitle = if (isFocusActive) (currentTask?.title ?: "Deep Work") else "Shield Active"
        processMonitor.remainingMinutes = if (isFocusActive) (remainingSeconds / 60).coerceAtLeast(1) else 0
        processMonitor.classSchedules = classSchedules
        processMonitor.setUsageMap(dailyUsages)
        processMonitor.onUsageRecorded = { target, sec ->
            scope.launch {
                db.recordAppUsage(target, sec)
            }
        }
        processMonitor.onOutOfScopeTaxApplied = { reason, mins ->
            scope.launch {
                val currentSpent = prefs.getEarnedLeisureSpentMinutesTodaySnapshot()
                prefs.setEarnedLeisureSpentMinutesToday(currentSpent + mins)
                val currentTax = prefs.getScopeTaxMinutesTodaySnapshot()
                prefs.setScopeTaxMinutesToday(currentTax + mins)
                updateRescueActions()
            }
        }
        processMonitor.onDailyRollover = {
            scope.launch {
                db.refreshDailyUsage()
                todayFocusMinutes = db.getTodayFocusMinutes()
                prefs.setEarnedLeisureSpentMinutesToday(0)
                prefs.setEarnedLeisureExpiryEpoch(0L)
                prefs.setScopeTaxMinutesToday(0)
                processMonitor.cancelEarnedLeisure()
                updateRescueActions()
            }
        }
        processMonitor.onRabbitHoleDetected = { target, mins ->
            activeRabbitHoleTarget = target
            activeRabbitHoleMinutes = mins
        }
        processMonitor.updateRules(blockRules)

        if (isFocusActive || standingShieldEnabled || has247Rules || hasSchedulesOrQuotas || isStrictLockdownActive || scopeSentinelEnabled) {
            processMonitor.start()
        } else {
            processMonitor.stop()
            onDistractionCleared()
        }
    }

    // Focus Timer Loop
    LaunchedEffect(isFocusActive, timerSessionId) {
        if (isFocusActive) {
            while (isActive && remainingSeconds > 0) {
                delay(1000)
                remainingSeconds -= 1
            }
            if (remainingSeconds <= 0 && isFocusActive) {
                isFocusActive = false
                db.recordFocusSession(
                    taskTitle = currentTask?.title ?: "Deep Work",
                    durationSeconds = durationMinutes * 60,
                    actualSeconds = durationMinutes * 60,
                    completed = true,
                )
                todayFocusMinutes = db.getTodayFocusMinutes()
                remainingSeconds = durationMinutes * 60
            }
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(AnchorColors.HarborBackground),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Sidebar Navigation Rail
            DesktopNavigationSidebar(
                currentScreen = currentScreen,
                onScreenSelected = { currentScreen = it },
                isFocusActive = isFocusActive,
                remainingSeconds = remainingSeconds,
                onOpenAirlock = { showAirlock = true },
                onToggleFloatingIsland = { isFloatingIslandVisible = !isFloatingIslandVisible },
                isFloatingIslandActive = isFloatingIslandVisible,
                isFullscreen = isFullscreen,
                onToggleFullscreen = onToggleFullscreen,
                hasUpdateBadge = hasUpdateBadge,
                isStandingShieldActive = standingShieldEnabled || has247Rules,
            )

            // Main Active Screen Content Area
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxSize(),
            ) {
                when (currentScreen) {
                    DesktopScreen.GROVE -> {
                        // Grove Dashboard (ADHD Evidence-Based Bento Command Deck)
                        DesktopHomeDeck(
                            currentTask = currentTask,
                            tasks = tasks,
                            funLinks = funLinks,
                            isFocusActive = isFocusActive,
                            remainingSeconds = remainingSeconds,
                            durationMinutes = durationMinutes,
                            todayFocusMinutes = todayFocusMinutes,
                            deflectedCount = deflectedCount,
                            streakDays = harborState.streakDays,
                            onStartFocus = {
                                if (isFocusActive) {
                                    isFocusActive = false
                                    remainingSeconds = durationMinutes * 60
                                } else {
                                    timerSessionId++
                                    remainingSeconds = durationMinutes * 60
                                    isFocusActive = true
                                    currentScreen = DesktopScreen.FOCUS
                                }
                            },
                            onOpenDurationPicker = { showDurationPicker = true },
                            onLaunchMomentumPreset = { minutes ->
                                durationMinutes = minutes
                                remainingSeconds = minutes * 60
                                timerSessionId++
                                isFocusActive = true
                                currentScreen = DesktopScreen.FOCUS
                            },
                            onCaptureThought = { thought ->
                                scope.launch {
                                    db.insertTask(thought, durationMinutes = 5)
                                }
                            },
                            onMakeTaskNow = { taskId ->
                                scope.launch {
                                    db.makeTaskNow(taskId)
                                }
                            },
                            onToggleTaskCompleted = { taskId ->
                                scope.launch {
                                    db.toggleTaskCompleted(taskId)
                                }
                            },
                            onNavigateToPlan = { currentScreen = DesktopScreen.PLAN },
                            onStartFocusWithTask = { taskTitle, minutes ->
                                scope.launch {
                                    db.insertTask(taskTitle, durationMinutes = minutes)
                                    val created = db.tasks.value.firstOrNull { it.title.equals(taskTitle, ignoreCase = true) }
                                    if (created != null) {
                                        db.makeTaskNow(created.id)
                                    }
                                }
                                durationMinutes = minutes
                                remainingSeconds = minutes * 60
                                timerSessionId++
                                isFocusActive = true
                                currentScreen = DesktopScreen.FOCUS
                            },
                            onSaveTasksToInbox = { newTasks ->
                                scope.launch {
                                    for (t in newTasks) {
                                        if (t.isNotBlank()) db.insertTask(t.trim(), durationMinutes = 15)
                                    }
                                }
                            },
                            onApplyBlockerRule = { target, enable ->
                                scope.launch {
                                    db.setBlockRuleEnabled(target, enable)
                                }
                            },
                            onResetData = { resetType ->
                                scope.launch {
                                    when (resetType) {
                                        ResetType.CLEAR_TASKS -> db.clearAllTasks()
                                        ResetType.RESET_STREAK -> db.resetFocusHistoryAndStreak()
                                        ResetType.FACTORY_RESET -> db.resetAllData()
                                    }
                                }
                            },
                            onNavigateToSyllabus = { currentScreen = DesktopScreen.SYLLABUS },
                        )
                    }

                    DesktopScreen.FOCUS -> {
                        // Dedicated Focus Canvas Screen (Tide Ring, Countdown, Micro-steps, Controls)
                        DesktopFocusScreen(
                            currentTask = currentTask,
                            isFocusActive = isFocusActive,
                            remainingSeconds = remainingSeconds,
                            durationMinutes = durationMinutes,
                            isFullscreen = isFullscreen,
                            onToggleFullscreen = onToggleFullscreen,
                            onToggleFocus = {
                                if (!isFocusActive) timerSessionId++
                                isFocusActive = !isFocusActive
                            },
                            onAdjustSeconds = { delta ->
                                remainingSeconds = (remainingSeconds + delta).coerceIn(60, 180 * 60)
                            },
                            onResetTimer = {
                                isFocusActive = false
                                remainingSeconds = durationMinutes * 60
                            },
                            onOpenDurationPicker = { showDurationPicker = true },
                            onOpenAirlock = { showAirlock = true },
                        )
                    }

                    DesktopScreen.BLOCKER -> {
                        // Windows 11 App & Web Blocker Settings Screen
                        DesktopBlockerScreen(
                            db = db,
                            prefs = prefs,
                            processMonitor = processMonitor,
                            isFocusActive = isFocusActive,
                            onTestRescueShield = {
                                onDistractionDetected(
                                    BlockerDetection(
                                        isBlocked = true,
                                        appOrSiteName = "Discord Preview",
                                        reason = "Manual Test of Maritime Rescue Shield",
                                        timestampMillis = System.currentTimeMillis(),
                                    ),
                                    if (isFocusActive) (currentTask?.title ?: "Deep Work") else "Standing Shield (No Timer)",
                                    if (isFocusActive) (remainingSeconds / 60).coerceAtLeast(1) else 0,
                                )
                            },
                        )
                    }

                    DesktopScreen.PLAN -> {
                        // Milestones & Plan Screen
                        DesktopPlanScreen(
                            db = db,
                            durationMinutes = durationMinutes,
                            onSelectTaskForFocus = { task ->
                                timerSessionId++
                                isFocusActive = true
                                remainingSeconds = task.durationMinutes * 60
                                currentScreen = DesktopScreen.FOCUS
                            },
                            onBreakdownTask = { taskTitle ->
                                prefilledAiTask = taskTitle
                                currentScreen = DesktopScreen.AI
                            },
                        )
                    }

                    DesktopScreen.SYLLABUS -> {
                        // Academic Harbor & Syllabus Hub
                        DesktopSyllabusScreen(
                            db = db,
                            onPlanWithAi = { item ->
                                prefilledAiTask = "${item.courseCode}: ${item.title}"
                                currentScreen = DesktopScreen.AI
                            },
                            onStartFocusWithTask = { taskTitle, minutes ->
                                scope.launch {
                                    db.insertTask(taskTitle, durationMinutes = minutes)
                                    val created = db.tasks.value.firstOrNull { it.title.equals(taskTitle, ignoreCase = true) }
                                    if (created != null) {
                                        db.makeTaskNow(created.id)
                                    }
                                }
                                durationMinutes = minutes
                                remainingSeconds = minutes * 60
                                timerSessionId++
                                isFocusActive = true
                                currentScreen = DesktopScreen.FOCUS
                            },
                        )
                    }

                    DesktopScreen.AI -> {
                        // Executive Function Studio (Break Down, Brain Dump, Triage, Replan, Ask Anchor)
                        DesktopAiScreen(
                            db = db,
                            onOpenSyllabus = { currentScreen = DesktopScreen.SYLLABUS },
                            initialTask = prefilledAiTask,
                            onStartFocus = { taskTitle ->
                                scope.launch {
                                    val existing = db.tasks.value.find { it.title == taskTitle }
                                    if (existing == null) {
                                        db.insertTask(taskTitle, durationMinutes)
                                    }
                                    timerSessionId++
                                    isFocusActive = true
                                    remainingSeconds = durationMinutes * 60
                                    currentScreen = DesktopScreen.FOCUS
                                }
                            },
                        )
                    }

                    DesktopScreen.SETTINGS -> {
                        // Settings & Database Storage Screen
                        DesktopSettingsScreen(
                            db = db,
                            prefs = prefs,
                            durationMinutes = durationMinutes,
                            onDurationChanged = { mins ->
                                durationMinutes = mins
                                if (!isFocusActive) remainingSeconds = mins * 60
                            },
                        )
                    }
                }
            }
        }

        // Custom Duration Dialog
        if (showDurationPicker) {
            DesktopCustomDurationDialog(
                initialMinutes = durationMinutes,
                onDismiss = { showDurationPicker = false },
                onConfirm = { mins ->
                    durationMinutes = mins
                    if (!isFocusActive) remainingSeconds = mins * 60
                    scope.launch { prefs.setFocusWorkMinutes(mins) }
                    showDurationPicker = false
                },
            )
        }

        // Airlock Brain-Dump Dialog
        if (showAirlock) {
            DesktopAirlockDialog(
                onDismiss = { showAirlock = false },
                onAnchorMicroStep = { microStep ->
                    scope.launch {
                        db.insertTask(microStep, durationMinutes)
                    }
                },
            )
        }

        // Rabbit Hole Reality Check Dialog (Continuous 25-minute stretch in AI browser)
        val rabbitHoleTarget = activeRabbitHoleTarget
        if (rabbitHoleTarget != null) {
            AlertDialog(
                onDismissRequest = {
                    processMonitor.resetContinuousRabbitHoleTimer()
                    activeRabbitHoleTarget = null
                },
                title = {
                    Text("🐇 Rabbit Hole Reality Check", fontWeight = FontWeight.Bold, color = Color.White)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "You've been active in ${rabbitHoleTarget.replaceFirstChar { it.uppercase() }} for $activeRabbitHoleMinutes continuous minutes without switching windows.",
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "Are you still working on your homework anchor (\"${currentTask?.title ?: "Deep Work"}\"), or did you get pulled into a rabbit hole?",
                            color = AnchorColors.HarborPrimary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            processMonitor.resetContinuousRabbitHoleTimer()
                            activeRabbitHoleTarget = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AnchorColors.HarborPrimary, contentColor = Color(0xFF002A4A)),
                    ) {
                        Text("Still on Task", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            processMonitor.resetContinuousRabbitHoleTimer()
                            processMonitor.minimizeDistractionWindow()
                            processMonitor.restorePreviousWorkWindow()
                            activeRabbitHoleTarget = null
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    ) {
                        Text("Back to Notes / Anchor")
                    }
                },
            )
        }
    }
}
