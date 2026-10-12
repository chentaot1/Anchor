package com.anchor.adhd.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.anchor.adhd.desktop.ai.DesktopAiEngine
import com.anchor.adhd.desktop.ai.DesktopRoutingSession
import com.anchor.adhd.desktop.ai.ResetType
import com.anchor.adhd.desktop.blocker.BlockerDetection
import com.anchor.adhd.desktop.blocker.DesktopProcessMonitor
import com.anchor.adhd.desktop.blocker.DesktopRescueOverlay
import com.anchor.adhd.desktop.blocker.ScaledTimeBank
import com.anchor.adhd.desktop.blocker.TimeBankStatus
import com.anchor.adhd.desktop.db.AnchorDesktopDatabase
import com.anchor.adhd.desktop.db.DesktopTask
import com.anchor.adhd.desktop.db.DesktopDataOperations
import com.anchor.adhd.desktop.db.DesktopStreak
import com.anchor.adhd.desktop.db.DesktopThoughtCapture
import com.anchor.adhd.desktop.db.DesktopTaskDeletion
import com.anchor.adhd.desktop.db.DesktopTaskChanges
import com.anchor.adhd.desktop.db.DesktopThoughtCaptureState
import com.anchor.adhd.desktop.focus.DesktopCountdown
import com.anchor.adhd.desktop.focus.DesktopFocusCredit
import com.anchor.adhd.desktop.focus.DesktopFocusLedger
import com.anchor.adhd.desktop.focus.DesktopFocusSaveQueue
import com.anchor.adhd.desktop.focus.DesktopActiveTimer
import com.anchor.adhd.desktop.focus.DesktopTimerRecovery
import com.anchor.adhd.desktop.focus.DesktopTimerRecoveryStore
import com.anchor.adhd.desktop.platform.DesktopAutostartManager
import com.anchor.adhd.desktop.platform.DesktopFullscreenManager
import com.anchor.adhd.desktop.platform.DesktopNotifications
import com.anchor.adhd.desktop.platform.DesktopAppIcon
import com.anchor.adhd.desktop.platform.DesktopInstanceLock
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

internal fun restoreExistingAnchorWindow() {
    if (com.sun.jna.Platform.isWindows()) {
        try {
            val anchorHwnd = com.sun.jna.platform.win32.User32.INSTANCE.FindWindow(null, "Anchor ADHD — Living Maritime Focus")
            if (anchorHwnd != null) {
                val user32 = com.sun.jna.platform.win32.User32.INSTANCE
                // SW_RESTORE also unmaximizes a visible window; reopening should retain its layout.
                val minimized = user32.GetWindowLong(anchorHwnd, com.sun.jna.platform.win32.WinUser.GWL_STYLE) and
                    com.sun.jna.platform.win32.WinUser.WS_MINIMIZE != 0
                val show = if (minimized) com.sun.jna.platform.win32.WinUser.SW_RESTORE
                    else com.sun.jna.platform.win32.WinUser.SW_SHOW
                user32.ShowWindow(anchorHwnd, show)
                com.sun.jna.platform.win32.User32.INSTANCE.SetForegroundWindow(anchorHwnd)
            }
        } catch (_: Throwable) {
        }
    }
}

/** The startup gate also serves the isolated UI launcher; it never opens data without a lease. */
internal fun withSingleInstance(
    appDataRoot: java.nio.file.Path,
    onAlreadyRunning: () -> Unit = ::restoreExistingAnchorWindow,
    onFailure: (DesktopInstanceLock.Result.Failed) -> Unit = ::showStartupLockFailure,
    start: () -> Unit,
) {
    when (val result = DesktopInstanceLock.acquire(appDataRoot)) {
        is DesktopInstanceLock.Result.Acquired -> result.lease.use { start() }
        DesktopInstanceLock.Result.AlreadyRunning -> onAlreadyRunning()
        is DesktopInstanceLock.Result.Failed -> onFailure(result)
    }
}

internal fun showStartupLockFailure(failure: DesktopInstanceLock.Result.Failed) {
    val message = "Anchor could not secure access to its data folder.\n" +
        "Check that the folder is writable, then launch Anchor again.\n\n" +
        "Lock file: ${failure.file}\n\n" +
        "Details: ${failure.cause.message ?: failure.cause.javaClass.simpleName}"
    System.err.println(message)
    if (!java.awt.GraphicsEnvironment.isHeadless()) {
        javax.swing.SwingUtilities.invokeAndWait {
            val details = javax.swing.JTextArea(message, 0, 60).apply {
                isEditable = false
                lineWrap = true
                wrapStyleWord = true
                caretPosition = 0
                accessibleContext.accessibleName = "Startup failure details"
            }
            val scroll = javax.swing.JScrollPane(details).apply {
                preferredSize = java.awt.Dimension(620, 230)
            }
            val pane = javax.swing.JOptionPane(scroll, javax.swing.JOptionPane.ERROR_MESSAGE)
            val dialog = pane.createDialog("Anchor couldn't start")
            try {
                dialog.iconImages = DesktopAppIcon.sizes.map(DesktopAppIcon::image)
                dialog.isVisible = true
            } finally {
                dialog.dispose()
            }
        }
    }
    kotlin.system.exitProcess(1)
}

fun main(args: Array<String> = emptyArray()) {
    val appData = System.getenv("APPDATA") ?: (System.getProperty("user.home") + "/AppData/Roaming")
    withSingleInstance(java.nio.file.Path.of(appData)) { startDesktopApplication(args) }
}

private fun startDesktopApplication(args: Array<String>) {

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

    launchDesktopApplication(shouldStartMinimized)
}

/** Shares the production windows with the development smoke launcher, without startup registration. */
internal fun launchDesktopApplication(
    shouldStartMinimized: Boolean,
    initialWindowSize: DpSize = DpSize(1200.dp, 860.dp),
    startFullscreen: Boolean = true,
    initialTaskDeletions: List<DesktopTaskDeletion> = emptyList(),
) {
    application {
        val mainWindowState =
            rememberWindowState(
                placement = if (startFullscreen) WindowPlacement.Maximized else WindowPlacement.Floating,
                isMinimized = shouldStartMinimized,
                width = initialWindowSize.width,
                height = initialWindowSize.height,
            )
        var isFullscreen by remember { mutableStateOf(false) }
        var appKeyHandler by remember { mutableStateOf<((KeyEvent) -> Boolean)?>(null) }
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
        var rescueThoughtState by remember { mutableStateOf(DesktopThoughtCaptureState()) }
        var onRescueThoughtEdit by remember { mutableStateOf<((String) -> Unit)?>(null) }
        var onRescueThoughtSubmit by remember { mutableStateOf<((BlockerDetection) -> Unit)?>(null) }
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
            icon = DesktopAppIcon.painter,
            title = "Anchor ADHD — Living Maritime Focus",
            onPreviewKeyEvent = { event ->
                // Let focused controls handle Space before using it as a timer shortcut.
                if (event.key == Key.Spacebar) false
                else if (appKeyHandler?.invoke(event) == true) true
                else if (event.type == KeyEventType.KeyDown) {
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
            onKeyEvent = { event ->
                event.key == Key.Spacebar && appKeyHandler?.invoke(event) == true
            },
        ) {
            LaunchedEffect(this.window) {
                val win = this@Window.window
                appWindow = win
                if (shouldStartMinimized) {
                    mainWindowState.isMinimized = true
                    (win as? java.awt.Frame)?.state = java.awt.Frame.ICONIFIED
                } else if (startFullscreen) {
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
                    initialTaskDeletions = initialTaskDeletions,
                    window = this.window,
                    isFullscreen = isFullscreen,
                    onToggleFullscreen = toggleFullscreen,
                    onRegisterKeyHandler = { appKeyHandler = it },
                    onShowMainWindow = {
                        mainWindowState.isMinimized = false
                        appWindow?.let { win ->
                            (win as? java.awt.Frame)?.let { frame ->
                                frame.extendedState = frame.extendedState and java.awt.Frame.ICONIFIED.inv()
                            }
                            win.toFront()
                            win.requestFocus()
                        }
                    },
                    onDistractionDetected = { detection, taskTitle, mins ->
                        activeDistraction = detection
                        currentTaskTitle = taskTitle
                        remainingMinutes = mins
                    },
                    onDistractionCleared = {
                        activeDistraction = null
                    },
                    onDistractionScribed = { expected ->
                        if (activeDistraction === expected) {
                            activeDistraction = null
                            mainWindowState.isMinimized = false
                        }
                    },
                    onRegisterRescueCapture = { state, edit, submit ->
                        rescueThoughtState = state
                        onRescueThoughtEdit = edit
                        onRescueThoughtSubmit = submit
                    },
                    onRegisterRescueActions = { onReturn, onCloseTab, onSwitchGrace, onPass, onQuickPass, remaining, timeBank, onUnlockTimeBank ->
                        onReturnToFocusCallback = onReturn
                        onCloseTabCallback = onCloseTab
                        onSwitchTabGraceCallback = onSwitchGrace
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
                icon = DesktopAppIcon.painter,
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
                        thoughtCaptureState = rescueThoughtState,
                        onThoughtDraftChange = { onRescueThoughtEdit?.invoke(it) },
                        onCaptureThought = { onRescueThoughtSubmit?.invoke(distraction) },
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
internal fun DesktopAnchorApp(
    initialTaskDeletions: List<DesktopTaskDeletion> = emptyList(),
    window: java.awt.Window? = null,
    isFullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {},
    onRegisterKeyHandler: (((KeyEvent) -> Boolean)?) -> Unit = {},
    onShowMainWindow: () -> Unit = {},
    onDistractionDetected: (BlockerDetection, String, Int) -> Unit = { _, _, _ -> },
    onDistractionCleared: () -> Unit = {},
    onDistractionScribed: (BlockerDetection) -> Unit = {},
    onRegisterRescueCapture: ((DesktopThoughtCaptureState, (String) -> Unit, (BlockerDetection) -> Unit) -> Unit)? = null,
    onRegisterRescueActions: (
        (
            onReturnToFocus: () -> Unit,
            onCloseTab: () -> Unit,
            onSwitchTabGrace: () -> Unit,
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
    val dataOperations = remember { DesktopDataOperations(scope) }
    val dataOperationState by dataOperations.state.collectAsState()
    val routingSession = remember { DesktopRoutingSession(scope) }
    val routingState by routingSession.state.collectAsState()
    var isSavingMilestoneCapture by remember { mutableStateOf(false) }
    val thoughtCapture = remember {
        DesktopThoughtCapture(scope, allowed = { !dataOperations.isBusy && !routingSession.isActing && !isSavingMilestoneCapture }, save = { request ->
            db.captureThought(request.key, request.text)
        })
    }
    val thoughtCaptureState by thoughtCapture.state.collectAsState()
    var returnAfterScribe by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingScribeDetection by remember { mutableStateOf<BlockerDetection?>(null) }
    val rescueCapture = remember {
        DesktopThoughtCapture(
            scope,
            allowed = { !dataOperations.isBusy && !routingSession.isActing && !isSavingMilestoneCapture },
            actionLabel = "Scribe",
            onSaved = { returnAfterScribe?.invoke() },
            save = { request -> db.captureThought(request.key, request.text) },
        )
    }
    val rescueCaptureState by rescueCapture.state.collectAsState()
    val prefs = remember { DesktopUserPreferences() }
    val focusSaves = remember {
        DesktopFocusSaveQueue(java.io.File(db.appDataDirectory, "pending-focus"), save = { credit ->
            db.recordFocusSession(
                taskTitle = credit.taskTitle,
                durationSeconds = credit.plannedSeconds,
                actualSeconds = credit.actualSeconds,
                completed = credit.completed,
                now = java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(credit.endedAtMillis), java.time.ZoneId.systemDefault()),
                saveKey = credit.saveKey,
            )
        })
    }
    val focusSaveState by focusSaves.state.collectAsState()
    val timerRecovery = remember { DesktopTimerRecoveryStore(java.io.File(db.appDataDirectory, "timer-recovery.json")) }
    val timerRecoveryStatus by timerRecovery.status.collectAsState()
    val showMainWindow by rememberUpdatedState(onShowMainWindow)
    val notifications = remember { DesktopNotifications { showMainWindow() } }
    DisposableEffect(notifications) {
        notifications.install()
        onDispose { notifications.close() }
    }

    // Navigation State
    var currentScreen by remember { mutableStateOf(DesktopScreen.GROVE) }

    // Database Reactive States
    val tasks by db.tasks.collectAsState()
    val funLinks by db.funLinks.collectAsState()
    val storedHarborState by db.harborState.collectAsState()
    var statisticsNow by remember { mutableStateOf(java.time.ZonedDateTime.now()) }
    val harborState = storedHarborState.copy(streakDays = DesktopStreak.currentDays(
        storedHarborState.streakDays, storedHarborState.lastFocusLogicalDate,
        AnchorDesktopDatabase.getLogicalDate(statisticsNow.toLocalDateTime()),
    ))
    LaunchedEffect(Unit) {
        while (isActive) {
            statisticsNow = java.time.ZonedDateTime.now()
            delay(30_000)
        }
    }
    val blockRules by db.blockRules.collectAsState()
    val classSchedules by db.classSchedules.collectAsState()
    val dailyUsages by db.dailyUsages.collectAsState()

    // Focus & Preferences State
    var durationMinutes by remember { mutableIntStateOf(25) }
    var savedWorkDurationMinutes by remember { mutableIntStateOf(25) }
    var pendingDefaultFocusMinutes by remember { mutableStateOf<Int?>(null) }
    var isFocusActive by remember { mutableStateOf(false) }
    var isWorkPhase by remember { mutableStateOf(true) }
    var showSessionCompleteBanner by remember { mutableStateOf(false) }
    var remainingSeconds by remember { mutableIntStateOf(25 * 60) }
    var timerSessionId by remember { mutableIntStateOf(0) }
    val countdown = remember { DesktopCountdown(25 * 60) }
    val focusLedger = remember { DesktopFocusLedger() }
    var countdownSessionId by remember { mutableIntStateOf(-1) }
    var focusTaskSnapshot by remember { mutableStateOf<DesktopTask?>(null) }
    var hasFocusSession by remember { mutableStateOf(false) }
    var todayFocusMinutes by remember { mutableIntStateOf(0) }
    var timerRecoveryReady by remember { mutableStateOf(false) }
    var recoveredTimerNotice by remember { mutableStateOf(false) }
    var archivedRecoveryPath by remember { mutableStateOf<String?>(null) }
    val taskDeletionHistory = remember { mutableStateListOf<DesktopTaskDeletion>().apply { addAll(initialTaskDeletions) } }
    var prefilledAiTask by remember { mutableStateOf("") }
    val standingShieldEnabled by prefs.standingShieldEnabled.collectAsState(initial = false)
    val focusBreakMinutes by prefs.focusBreakMinutes.collectAsState(initial = 5)
    val defaultWorkMinutes by prefs.focusWorkMinutes.collectAsState(initial = 25)

    var isSavingMilestoneSteps by remember { mutableStateOf(false) }
    val airlock = remember {
        com.anchor.adhd.desktop.ai.DesktopAirlockSession(
            scope,
            allowed = { timerRecoveryReady && !dataOperations.isBusy && !routingSession.isActing && !thoughtCapture.isSaving && !rescueCapture.isSaving && !isSavingMilestoneCapture && !isSavingMilestoneSteps },
            save = { request ->
                val selected = db.saveAirlockTasks(request)
                if (selected != null && isWorkPhase && !focusLedger.isOpen && !isFocusActive) {
                    durationMinutes = selected.durationMinutes
                    savedWorkDurationMinutes = durationMinutes
                    remainingSeconds = durationMinutes * 60
                    countdown.reset(remainingSeconds)
                    hasFocusSession = false
                    showSessionCompleteBanner = false
                }
            },
        )
    }
    var startSavedMilestone by remember { mutableStateOf<((DesktopTask) -> Unit)?>(null) }
    val milestoneSteps = remember {
        com.anchor.adhd.desktop.ai.DesktopMilestoneSteps(
            scope,
            allowed = { timerRecoveryReady && !dataOperations.isBusy && !routingSession.isActing &&
                !thoughtCapture.isSaving && !rescueCapture.isSaving && !isSavingMilestoneCapture && !airlock.isSaving },
            onStart = { task -> checkNotNull(startSavedMilestone).invoke(task) },
            onSavingChanged = { isSavingMilestoneSteps = it },
            save = db::saveMilestoneSteps,
        )
    }
    val milestoneCapture = remember {
        DesktopThoughtCapture(
            scope,
            allowed = { timerRecoveryReady && !dataOperations.isBusy && !routingSession.isActing &&
                !thoughtCapture.isSaving && !rescueCapture.isSaving && !airlock.isSaving && !milestoneSteps.isSaving },
            actionLabel = "Add Milestone",
            draftLabel = "milestone",
            durationMinutes = { durationMinutes },
            onSavingChanged = { isSavingMilestoneCapture = it },
            save = { request -> db.captureThought(request.key, request.text, request.durationMinutes) },
        )
    }
    val taskChanges = remember {
        DesktopTaskChanges(dataOperations, allowed = { timerRecoveryReady && !routingSession.isActing &&
            !thoughtCapture.isSaving && !rescueCapture.isSaving && !isSavingMilestoneCapture &&
            !airlock.isSaving && !milestoneSteps.isSaving })
    }
    val milestoneCaptureState by milestoneCapture.state.collectAsState()
    val milestoneStepsState by milestoneSteps.state.collectAsState()
    val airlockState by airlock.state.collectAsState()
    val showAirlock = airlockState.isOpen
    var showDurationPicker by remember { mutableStateOf(false) }
    var dataOperationError by remember { mutableStateOf<String?>(null) }
    var isFloatingIslandVisible by remember { mutableStateOf(false) }
    var activeRabbitHoleTarget by remember { mutableStateOf<String?>(null) }
    var activeRabbitHoleMinutes by remember { mutableIntStateOf(25) }

    val hasUpdateBadge by DesktopUpdateManager.hasUpdateBadge.collectAsState()

    val currentTask = tasks.firstOrNull { !it.isCompleted }
    val focusTask = if (hasFocusSession) focusTaskSnapshot else currentTask

    fun canReplaceData(): Boolean = !dataOperations.isBusy && !routingSession.isActing && !thoughtCapture.isSaving && !rescueCapture.isSaving && !isSavingMilestoneCapture && !airlock.isSaving && !milestoneSteps.isSaving && timerRecoveryReady && timerRecoveryStatus.error == null &&
        !timerRecoveryStatus.hasPendingWrite && !focusLedger.isOpen && isWorkPhase && !isFocusActive &&
        focusSaveState.isReady && focusSaveState.pendingCount == 0 && !focusSaveState.isSaving

    fun checkpointTimer() {
        if (!timerRecoveryReady) return
        val session = focusLedger.snapshot()
        val active = if (session != null || !isWorkPhase) {
            DesktopActiveTimer(countdown.snapshot(), session, focusTaskSnapshot, isWorkPhase, durationMinutes, savedWorkDurationMinutes)
        } else null
        timerRecovery.stage(DesktopTimerRecovery(timer = active, credits = focusSaves.pendingCredits()))
        scope.launch { timerRecovery.persist() }
    }

    fun saveFocusCredit(credit: DesktopFocusCredit?) {
        if (credit == null) return
        focusSaves.enqueue(credit)
        if (credit.completed) notifications.focusCompleted(credit.taskTitle, credit.actualSeconds)
        scope.launch { focusSaves.retry() }
    }

    LaunchedEffect(timerRecoveryReady, statisticsNow, focusSaveState.savedRevision) {
        if (timerRecoveryReady) todayFocusMinutes = db.getTodayFocusMinutes()
    }

    LaunchedEffect(focusSaveState.pendingCount, focusSaveState.savedRevision) {
        checkpointTimer()
    }

    fun creditPartialFocusIfEligible() {
        if (isWorkPhase) saveFocusCredit(focusLedger.finish(countdown.elapsedSeconds, completed = false))
    }

    fun applyPendingDefaultDuration() {
        pendingDefaultFocusMinutes?.let { minutes ->
            durationMinutes = minutes
            savedWorkDurationMinutes = minutes
            pendingDefaultFocusMinutes = null
        }
    }

    fun finishTimer(endedAtMillis: Long = System.currentTimeMillis()) {
        isFocusActive = false
        if (isWorkPhase) {
            // The save outlives the timer effect, which is cancelled when focus stops.
            saveFocusCredit(focusLedger.finish(countdown.elapsedSeconds, completed = true, endedAtMillis = endedAtMillis))
            showSessionCompleteBanner = true
        } else {
            notifications.breakCompleted()
            isWorkPhase = true
            durationMinutes = savedWorkDurationMinutes
        }
        applyPendingDefaultDuration()
        remainingSeconds = durationMinutes * 60
        countdown.reset(remainingSeconds)
        recoveredTimerNotice = false
        checkpointTimer()
    }

    fun toggleFocus() {
        if (!timerRecoveryReady || dataOperations.isBusy || routingSession.isActing) return
        if (!isFocusActive) {
            if (isWorkPhase && !focusLedger.isOpen) {
                focusTaskSnapshot = currentTask
                hasFocusSession = true
                focusLedger.begin(currentTask, durationMinutes * 60)
            }
            showSessionCompleteBanner = false
            if (remainingSeconds <= 0) remainingSeconds = durationMinutes * 60
        } else {
            countdown.pause()
            remainingSeconds = countdown.remainingSeconds
        }
        isFocusActive = !isFocusActive
        recoveredTimerNotice = false
        checkpointTimer()
    }

    fun startTaskFocus(minutes: Int, task: DesktopTask? = currentTask, prepared: Boolean = false) {
        if (!timerRecoveryReady || ((dataOperations.isBusy || routingSession.isActing || milestoneSteps.isSaving ||
            airlock.isSaving || thoughtCapture.isSaving || rescueCapture.isSaving || isSavingMilestoneCapture) && !prepared)) return
        creditPartialFocusIfEligible()
        val safeMinutes = minutes.coerceIn(DesktopDurationLimits.MIN_MINUTES, DesktopDurationLimits.MAX_MINUTES)
        isWorkPhase = true
        showSessionCompleteBanner = false
        durationMinutes = safeMinutes
        savedWorkDurationMinutes = safeMinutes
        remainingSeconds = safeMinutes * 60
        countdown.reset(remainingSeconds)
        focusLedger.begin(task, remainingSeconds)
        focusTaskSnapshot = task
        hasFocusSession = true
        timerSessionId++
        isFocusActive = true
        currentScreen = DesktopScreen.FOCUS
        recoveredTimerNotice = false
        checkpointTimer()
    }

    val startMilestoneFocus: (DesktopTask) -> Unit = remember {
        { task -> startTaskFocus(task.durationMinutes, task, prepared = true) }
    }
    SideEffect { startSavedMilestone = startMilestoneFocus }

    fun startNamedTaskFocus(title: String, minutes: Int) {
        if (!timerRecoveryReady || dataOperations.isBusy) return
        dataOperations.start("Preparing focus") {
            val target = db.upsertAndMakeTaskNow(title, minutes) ?: error("This task is unavailable. Choose it again.")
            startTaskFocus(target.durationMinutes, target, prepared = true)
            null
        }
    }

    // Windows 11 Native Blocker Monitor
    val processMonitor =
        remember {
            lateinit var monitor: DesktopProcessMonitor
            monitor =
                DesktopProcessMonitor(scope, database = db) { detection ->
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

    // Windows 11 Taskbar Progress Integration
    LaunchedEffect(isFocusActive, remainingSeconds, durationMinutes) {
        DesktopTaskbarIntegration.updateProgress(window, isFocusActive, remainingSeconds, durationMinutes * 60)
    }

    // Windows 11 Floating Island Micro-HUD Window
    DesktopFloatingIslandWindow(
        visible = isFloatingIslandVisible,
        taskTitle = focusTask?.title ?: "Deep Work",
        isFocusActive = isFocusActive,
        remainingSeconds = remainingSeconds,
        onToggleFocus = { toggleFocus() },
        onExpandMainApp = {
            processMonitor.bringAnchorToFront()
            isFloatingIslandVisible = false
        },
        onCloseHud = {
            isFloatingIslandVisible = false
        },
    )

    val updateRescueActions: () -> Unit = {
        returnAfterScribe = {
            // A save from a dismissed rescue must not act on a later distraction.
            val expected = pendingScribeDetection
            if (expected != null && processMonitor.currentDetection.value === expected) {
                if (expected.isWebDistraction) {
                    processMonitor.closeActiveBrowserTab()
                } else {
                    processMonitor.minimizeDistractionWindow()
                }
                if (!processMonitor.restorePreviousWorkWindow()) processMonitor.bringAnchorToFront()
            }
            if (expected != null) onDistractionScribed(expected)
        }
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

    val editRescueDraft = remember(rescueCapture) { rescueCapture::edit }
    val submitRescueThought: (BlockerDetection) -> Unit = remember(rescueCapture) {
        { displayedDetection ->
            val previous = pendingScribeDetection
            pendingScribeDetection = displayedDetection
            if (!rescueCapture.submit()) pendingScribeDetection = previous
        }
    }
    SideEffect {
        onRegisterRescueCapture?.invoke(rescueCaptureState, editRescueDraft, submitRescueThought)
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
        processMonitor.syncProtectionStatsFromDatabase()
        DesktopAiEngine.connectSyllabus(db.syllabusItems, db.courses)
        durationMinutes = (db.tasks.value.firstOrNull { !it.isCompleted }?.durationMinutes
            ?: prefs.getFocusWorkMinutesSnapshot()).coerceIn(DesktopDurationLimits.MIN_MINUTES, DesktopDurationLimits.MAX_MINUTES)
        savedWorkDurationMinutes = durationMinutes
        remainingSeconds = durationMinutes * 60
        countdown.reset(remainingSeconds)
        val recovery = timerRecovery.load()
        recovery?.credits?.forEach(focusSaves::enqueue)
        focusSaves.retry()
        try {
            val active = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                recovery?.restorableTimer { key -> focusSaves.contains(key) || db.hasFocusSave(key) }
            }
            if (active != null) {
                countdown.restore(active.clock)
                active.session?.let(focusLedger::restore)
                isWorkPhase = active.isWorkPhase
                durationMinutes = active.durationMinutes
                savedWorkDurationMinutes = active.savedWorkDurationMinutes
                focusTaskSnapshot = active.task
                hasFocusSession = true
                remainingSeconds = countdown.remainingSeconds
                timerSessionId++
                countdownSessionId = timerSessionId
                currentScreen = DesktopScreen.FOCUS
                recoveredTimerNotice = true
            }
            timerRecoveryReady = true
            if (remainingSeconds == 0 && recovery != null) finishTimer(recovery.capturedAtMillis)
            else if (recoveredTimerNotice) notifications.timerRecovered(isWorkPhase, remainingSeconds)
            checkpointTimer()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            timerRecovery.protectOriginal("Recovered focus work could not be checked. The original file is kept at ${timerRecovery.file.absolutePath}.")
            timerRecoveryReady = true
        }

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

    LaunchedEffect(isFocusActive, isWorkPhase, standingShieldEnabled, has247Rules, hasSchedulesOrQuotas, isStrictLockdownActive, focusTask, remainingSeconds, blockRules, classSchedules, dailyUsages, scopeSentinelEnabled, timeBankStatus) {
        val isProtectedFocus = isFocusActive && isWorkPhase
        processMonitor.isFocusActive = isProtectedFocus
        processMonitor.isStandingShieldActive = standingShieldEnabled || has247Rules
        processMonitor.setStrictDailyLockdown(isStrictLockdownActive)
        processMonitor.isScopeSentinelEnabled = scopeSentinelEnabled
        processMonitor.availableBankedMinutes = timeBankStatus.availableBankedMinutes
        processMonitor.currentTaskTitle = if (isProtectedFocus) (focusTask?.title ?: "Deep Work") else "Shield Active"
        processMonitor.remainingMinutes = if (isProtectedFocus) (remainingSeconds / 60).coerceAtLeast(1) else 0
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
            if (countdownSessionId != timerSessionId || countdown.remainingSeconds != remainingSeconds) {
                countdown.reset(remainingSeconds)
                countdownSessionId = timerSessionId
            }
            countdown.resume()
            checkpointTimer()
            var checkpointAtNanos = System.nanoTime()
            try {
                while (isActive && remainingSeconds > 0) {
                    delay(200)
                    remainingSeconds = countdown.remainingSeconds
                    val nowNanos = System.nanoTime()
                    if (nowNanos - checkpointAtNanos >= 5_000_000_000L) {
                        checkpointTimer()
                        checkpointAtNanos = nowNanos
                    }
                }
            } finally {
                countdown.pause()
                checkpointTimer()
            }
            if (remainingSeconds <= 0 && isFocusActive) {
                finishTimer()
            }
        }
    }

    val keyHandler by rememberUpdatedState<(KeyEvent) -> Boolean> { event ->
        if (dataOperations.isBusy) false
        else if (event.type != KeyEventType.KeyDown) false
        else if (event.key == Key.Escape) {
            when {
                activeRabbitHoleTarget != null -> {
                    processMonitor.resetContinuousRabbitHoleTimer()
                    activeRabbitHoleTarget = null
                    true
                }
                showDurationPicker -> { showDurationPicker = false; true }
                showAirlock -> { airlock.dismiss(); true }
                else -> false
            }
        } else if (showAirlock || showDurationPicker || activeRabbitHoleTarget != null) false
        else if (event.key == Key.Spacebar && currentScreen == DesktopScreen.FOCUS &&
            !event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed) {
            toggleFocus()
            true
        }
        else if (event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed) {
            when (event.key) {
                Key.K -> { airlock.open(); true }
                Key.I -> { isFloatingIslandVisible = !isFloatingIslandVisible; true }
                Key.One -> { currentScreen = DesktopScreen.GROVE; true }
                Key.Two -> { currentScreen = DesktopScreen.FOCUS; true }
                Key.Three -> { currentScreen = DesktopScreen.BLOCKER; true }
                Key.Four -> { currentScreen = DesktopScreen.PLAN; true }
                Key.Five -> { currentScreen = DesktopScreen.SYLLABUS; true }
                Key.Six -> { currentScreen = DesktopScreen.AI; true }
                Key.Seven -> { currentScreen = DesktopScreen.SETTINGS; true }
                else -> false
            }
        } else false
    }
    DisposableEffect(Unit) {
        onRegisterKeyHandler { event -> keyHandler(event) }
        onDispose { onRegisterKeyHandler(null) }
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
                onScreenSelected = { if (!dataOperations.isBusy) currentScreen = it },
                isFocusActive = isFocusActive,
                remainingSeconds = remainingSeconds,
                onOpenAirlock = airlock::open,
                onToggleFloatingIsland = { isFloatingIslandVisible = !isFloatingIslandVisible },
                isFloatingIslandActive = isFloatingIslandVisible,
                isFullscreen = isFullscreen,
                onToggleFullscreen = onToggleFullscreen,
                hasUpdateBadge = hasUpdateBadge,
                isStandingShieldActive = standingShieldEnabled || has247Rules,
            )

            // Main Active Screen Content Area
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxSize(),
            ) {
                dataOperationState.feedback?.let { message ->
                    Column(
                        modifier = Modifier.fillMaxWidth().background(AnchorColors.HarborDock)
                            .heightIn(max = 160.dp).verticalScroll(rememberScrollState()).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SelectionContainer {
                            Text(message, color = if (dataOperationState.isError) Color(0xFFFFB4AB) else Color.White)
                        }
                        OutlinedButton(onClick = dataOperations::dismissFeedback) { Text("Dismiss") }
                    }
                }
                if (!timerRecoveryReady || recoveredTimerNotice || timerRecoveryStatus.error != null || archivedRecoveryPath != null) {
                    Column(
                        modifier = Modifier.fillMaxWidth().background(AnchorColors.HarborDock)
                            .heightIn(max = 180.dp).verticalScroll(rememberScrollState()).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (!timerRecoveryReady) Text("Preparing focus timer…", color = Color.White)
                        if (recoveredTimerNotice) {
                            Text("Recovered timer — paused", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("Time while Anchor was closed was excluded. Continue or reset the timer below.", color = Color.White.copy(alpha = 0.8f))
                        }
                        timerRecoveryStatus.error?.let { error ->
                            Text(error, color = Color(0xFFFFB4AB))
                            Text("Keep Anchor open until timer recovery can be saved. A damaged file is preserved until you archive it.", color = Color.White.copy(alpha = 0.8f))
                            if (timerRecoveryStatus.damagedFile) {
                                Button(onClick = {
                                    scope.launch {
                                        try {
                                            archivedRecoveryPath = timerRecovery.archiveDamagedFile().absolutePath
                                            checkpointTimer()
                                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                            throw cancelled
                                        } catch (failure: Exception) {
                                            dataOperationError = failure.message ?: "Recovery file could not be archived."
                                        }
                                    }
                                }) { Text("Archive original and enable recovery") }
                            } else {
                                Button(onClick = { checkpointTimer() }) { Text("Retry timer checkpoint") }
                            }
                        }
                        archivedRecoveryPath?.let { path ->
                            Text("Original recovery file kept at $path", color = Color.White.copy(alpha = 0.8f))
                            OutlinedButton(onClick = { archivedRecoveryPath = null }) { Text("Dismiss") }
                        }
                    }
                }
                if (focusSaveState.error != null || focusSaveState.pendingCount > 0) {
                    Column(
                        modifier = Modifier.fillMaxWidth().background(AnchorColors.HarborDock)
                            .heightIn(max = 180.dp).verticalScroll(rememberScrollState()).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = if (focusSaveState.isSaving) "Saving focus history…" else "Focus history needs attention (${focusSaveState.pendingCount} pending).",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (!focusSaveState.isSaving) {
                            Text(
                                text = when {
                                    focusSaveState.memoryOnlyCount > 0 -> "Keep Anchor open and retry: some focus history recovery records could not be written."
                                    focusSaveState.pendingCount > 0 -> "Recovery records are kept on this computer. Retry to add this work to your history."
                                    else -> "The focus recovery folder could not be checked. Retry before resetting or restoring data."
                                },
                                color = Color.White.copy(alpha = 0.8f),
                            )
                            focusSaveState.error?.let { Text(it, color = Color(0xFFFFB4AB)) }
                            Button(onClick = { scope.launch { focusSaves.retry() } }) { Text("Retry saving history") }
                        }
                    }
                }
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (currentScreen) {
                        DesktopScreen.GROVE -> {
                            // Grove Dashboard (ADHD Evidence-Based Bento Command Deck)
                            DesktopHomeDeck(
                                routingSession = routingSession,
                                canRunRouterAction = { timerRecoveryReady && !dataOperations.isBusy && !thoughtCapture.isSaving && !rescueCapture.isSaving && !isSavingMilestoneCapture && !airlock.isSaving && !milestoneSteps.isSaving },
                                currentTask = if (focusLedger.isOpen || !isWorkPhase) focusTask else currentTask,
                                tasks = tasks,
                                funLinks = funLinks,
                                isFocusActive = isFocusActive,
                                isWorkPhase = isWorkPhase,
                                hasOpenFocusSession = focusLedger.isOpen,
                                remainingSeconds = remainingSeconds,
                                durationMinutes = durationMinutes,
                                todayFocusMinutes = todayFocusMinutes,
                                deflectedCount = deflectedCount,
                                streakDays = harborState.streakDays,
                                onStartFocus = {
                                    if (isFocusActive) {
                                        currentScreen = DesktopScreen.FOCUS
                                    } else if (focusLedger.isOpen || !isWorkPhase) {
                                        currentScreen = DesktopScreen.FOCUS
                                        toggleFocus()
                                    } else {
                                        val selected = currentTask
                                        val selectedMinutes = durationMinutes
                                        dataOperations.start("Preparing focus", allowed = { timerRecoveryReady && !routingSession.isActing && !thoughtCapture.isSaving && !rescueCapture.isSaving && !isSavingMilestoneCapture && !airlock.isSaving && !milestoneSteps.isSaving }, deniedMessage = "Wait for timer recovery or a pending save to finish, then try again.") {
                                            val preparedTask = selected?.let { db.makeTaskNow(it.id) }
                                            if (scope.isActive) startTaskFocus(selectedMinutes, preparedTask, prepared = true)
                                            null
                                        }
                                    }
                                },
                                onOpenDurationPicker = { showDurationPicker = true },
                                onLaunchMomentumPreset = { minutes ->
                                    startTaskFocus(minutes)
                                },
                                thoughtCaptureState = thoughtCaptureState,
                                onThoughtDraftChange = thoughtCapture::edit,
                                onCaptureThought = { thoughtCapture.submit() },
                                onMakeTaskNow = { taskId ->
                                    dataOperations.start("Selecting next task", allowed = { timerRecoveryReady && !routingSession.isActing &&
                                        !thoughtCapture.isSaving && !rescueCapture.isSaving && !isSavingMilestoneCapture && !airlock.isSaving && !milestoneSteps.isSaving },
                                        deniedMessage = "Wait for timer recovery or a pending save to finish, then try again.") {
                                        val selected = db.makeTaskNow(taskId)
                                        if (scope.isActive && !isFocusActive && isWorkPhase && !focusLedger.isOpen && !showSessionCompleteBanner) {
                                            durationMinutes = selected.durationMinutes.coerceIn(DesktopDurationLimits.MIN_MINUTES, DesktopDurationLimits.MAX_MINUTES)
                                            savedWorkDurationMinutes = durationMinutes
                                            remainingSeconds = durationMinutes * 60
                                            countdown.reset(remainingSeconds)
                                            hasFocusSession = false
                                        }
                                        null
                                    }
                                },
                                onToggleTaskCompleted = { taskId ->
                                    val displayedTask = tasks.firstOrNull { it.id == taskId }
                                    if (displayedTask != null) taskChanges.start("Updating milestone") {
                                        db.setTaskCompleted(taskId, !displayedTask.isCompleted)
                                    }
                                },
                                onNavigateToPlan = { currentScreen = DesktopScreen.PLAN },
                                onStartFocusWithTask = { taskTitle, minutes ->
                                    val target = db.upsertAndMakeTaskNow(taskTitle, minutes)
                                        ?: error("This task is unavailable. Choose it again.")
                                    startTaskFocus(target.durationMinutes, target, prepared = true)
                                },
                                onSaveTasksToInbox = { key, newTasks ->
                                    db.saveRouterTasks(key, newTasks)
                                },
                                onApplyBlockerRule = { target, enable ->
                                    db.setBlockRuleEnabled(target, enable)
                                },
                                onResetData = { resetType ->
                                    dataOperations.start("Resetting data", allowed = ::canReplaceData) {
                                        when (resetType) {
                                            ResetType.CLEAR_TASKS -> db.clearAllTasks()
                                            ResetType.RESET_STREAK -> db.resetFocusHistoryAndStreak()
                                            ResetType.FACTORY_RESET -> db.resetAllData()
                                        }
                                        taskDeletionHistory.clear()
                                        hasFocusSession = false
                                        showSessionCompleteBanner = false
                                        todayFocusMinutes = db.getTodayFocusMinutes()
                                        "Data reset completed. Recovery backup: ${db.lastAutoBackupFile?.absolutePath}"
                                    }
                                },
                                onNavigateToSyllabus = { currentScreen = DesktopScreen.SYLLABUS },
                                harborState = harborState,
                            )
                        }

                        DesktopScreen.FOCUS -> {
                            // Dedicated Focus Canvas Screen (Tide Ring, Countdown, Micro-steps, Controls)
                            DesktopFocusScreen(
                                currentTask = focusTask,
                                isFocusActive = isFocusActive,
                                remainingSeconds = remainingSeconds,
                                durationMinutes = durationMinutes,
                                isFullscreen = isFullscreen,
                                onToggleFullscreen = onToggleFullscreen,
                                onToggleFocus = {
                                    toggleFocus()
                                },
                                onAdjustSeconds = { delta ->
                                    if (timerRecoveryReady && !dataOperations.isBusy && !routingSession.isActing && !airlock.isSaving && !isSavingMilestoneCapture) {
                                        if (isWorkPhase && !focusLedger.isOpen && !isFocusActive) {
                                            // Before work starts, steppers change the complete ready plan.
                                            val seconds = (durationMinutes.toLong() * 60 + delta)
                                                .coerceIn(DesktopDurationLimits.MIN_SECONDS.toLong(), DesktopDurationLimits.MAX_SECONDS.toLong())
                                            durationMinutes = ((seconds + 59) / 60).toInt()
                                            savedWorkDurationMinutes = durationMinutes
                                            remainingSeconds = durationMinutes * 60
                                            countdown.reset(remainingSeconds)
                                        } else {
                                            countdown.adjustSeconds(delta)
                                            remainingSeconds = countdown.remainingSeconds
                                        }
                                        checkpointTimer()
                                    }
                                },
                                onStartSpark = {
                                    if (timerRecoveryReady && !dataOperations.isBusy && !routingSession.isActing) {
                                        if (isWorkPhase && focusLedger.isOpen) {
                                            countdown.setRemainingSeconds(180)
                                            remainingSeconds = countdown.remainingSeconds
                                            if (!isFocusActive) toggleFocus()
                                            checkpointTimer()
                                        } else {
                                            startTaskFocus(3)
                                        }
                                    }
                                },
                                onResetTimer = {
                                    creditPartialFocusIfEligible()
                                    isFocusActive = false
                                    if (!isWorkPhase) {
                                        isWorkPhase = true
                                        durationMinutes = savedWorkDurationMinutes
                                    }
                                    applyPendingDefaultDuration()
                                    remainingSeconds = durationMinutes * 60
                                    countdown.reset(remainingSeconds)
                                    hasFocusSession = false
                                    recoveredTimerNotice = false
                                    checkpointTimer()
                                },
                                onOpenDurationPicker = { showDurationPicker = true },
                                onOpenAirlock = airlock::open,
                                isWorkPhase = isWorkPhase,
                                hasOpenFocusSession = focusLedger.isOpen,
                                showSessionCompleteBanner = showSessionCompleteBanner,
                                breakDurationMinutes = focusBreakMinutes,
                                onMarkTaskCompleted = {
                                    val taskToComplete = focusTask
                                    if (taskToComplete != null) taskChanges.start("Completing milestone") {
                                        db.setTaskCompleted(taskToComplete.id, true)
                                        if (scope.isActive) showSessionCompleteBanner = false
                                    }
                                },
                                onStartRestorativeBreak = {
                                    savedWorkDurationMinutes = durationMinutes
                                    isWorkPhase = false
                                    showSessionCompleteBanner = false
                                    durationMinutes = focusBreakMinutes
                                    remainingSeconds = focusBreakMinutes * 60
                                    countdown.reset(remainingSeconds)
                                    timerSessionId++
                                    isFocusActive = true
                                    recoveredTimerNotice = false
                                    checkpointTimer()
                                },
                                onDismissSessionCompleteBanner = {
                                    showSessionCompleteBanner = false
                                },
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
                                        if (isFocusActive && isWorkPhase) (focusTask?.title ?: "Deep Work") else "Standing Shield (No Timer)",
                                        if (isFocusActive) (remainingSeconds / 60).coerceAtLeast(1) else 0,
                                    )
                                },
                            )
                        }

                        DesktopScreen.PLAN -> {
                            // Milestones & Plan Screen
                            DesktopPlanScreen(
                                db = db,
                                captureState = milestoneCaptureState,
                                onDraftChange = milestoneCapture::edit,
                                onAddMilestone = { milestoneCapture.submit() },
                                deletionHistory = taskDeletionHistory,
                                onTaskChange = { label, action -> taskChanges.start(label, action) },
                                isAppActionBusy = dataOperations.isBusy || thoughtCapture.isSaving || rescueCapture.isSaving ||
                                    routingSession.isActing || airlock.isSaving,
                                nanoState = milestoneStepsState,
                                onToggleNanoTask = milestoneSteps::toggle,
                                onSaveNanoSteps = { key, start -> milestoneSteps.savePreview(key, start) },
                                onSelectTaskForFocus = { task ->
                                    dataOperations.start("Preparing focus", allowed = { timerRecoveryReady && !routingSession.isActing &&
                                        !thoughtCapture.isSaving && !rescueCapture.isSaving && !isSavingMilestoneCapture && !airlock.isSaving && !milestoneSteps.isSaving },
                                        deniedMessage = "Wait for timer recovery or a pending save to finish, then try again.") {
                                        val selected = db.makeTaskNow(task.id)
                                        if (scope.isActive) startTaskFocus(selected.durationMinutes, selected, prepared = true)
                                        null
                                    }
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
                                    startNamedTaskFocus(taskTitle, minutes)
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
                                    val selected = tasks.firstOrNull { !it.isCompleted && it.title.equals(taskTitle.trim(), ignoreCase = true) }
                                    startNamedTaskFocus(taskTitle, selected?.durationMinutes ?: durationMinutes)
                                },
                            )
                        }

                        DesktopScreen.SETTINGS -> {
                            // Settings & Database Storage Screen
                            DesktopSettingsScreen(
                                currentStreakDays = harborState.streakDays,
                                db = db,
                                prefs = prefs,
                                durationMinutes = defaultWorkMinutes,
                                backupOperationScope = scope,
                                dataOperations = dataOperations,
                                canReplaceData = ::canReplaceData,
                                canRestoreBackup = canReplaceData(),
                                dataReplacementBlockReason = if (routingState.isActing) {
                                    "Wait for your router action to finish before restoring or resetting data."
                                } else if (thoughtCapture.isSaving || rescueCapture.isSaving || isSavingMilestoneCapture) {
                                    "Wait for your thought to finish saving before restoring or resetting data."
                                } else if (airlock.isSaving || milestoneSteps.isSaving) {
                                    "Wait for your task steps to finish saving before restoring or resetting data."
                                } else if (!timerRecoveryReady || timerRecoveryStatus.error != null || timerRecoveryStatus.hasPendingWrite) {
                                    "Resolve timer recovery before restoring or resetting data."
                                } else if (!focusSaveState.isReady || focusSaveState.pendingCount > 0 || focusSaveState.isSaving) {
                                    "Save pending focus history before restoring or resetting data."
                                } else "Finish or reset your current timer before restoring or resetting data.",
                                onBackupRestored = {
                                    taskDeletionHistory.clear()
                                    scope.launch { todayFocusMinutes = db.getTodayFocusMinutes() }
                                    if (!focusLedger.isOpen) {
                                        hasFocusSession = false
                                        showSessionCompleteBanner = false
                                    }
                                },
                                onDurationChanged = { mins ->
                                    dataOperations.start("Saving focus duration", allowed = { timerRecoveryReady },
                                        deniedMessage = "Wait for timer recovery to finish, then try again.") {
                                        try {
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                                                prefs.setFocusWorkMinutes(mins)
                                            }
                                        } catch (error: java.io.IOException) {
                                            throw IllegalStateException("Your duration could not be saved. Try again.", error)
                                        }
                                        if (scope.isActive) {
                                            if (isWorkPhase && !focusLedger.isOpen && !isFocusActive) {
                                                durationMinutes = mins
                                                savedWorkDurationMinutes = mins
                                                remainingSeconds = mins * 60
                                                countdown.reset(remainingSeconds)
                                            } else {
                                                pendingDefaultFocusMinutes = mins
                                            }
                                        }
                                        null
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        // Custom Duration Dialog
        dataOperationState.runningLabel?.let { label ->
            AlertDialog(
                onDismissRequest = {},
                title = { Text(label) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text("Please wait while Anchor finishes this operation.")
                    }
                },
                confirmButton = {},
            )
        }

        dataOperationError?.let { message ->
            AlertDialog(
                onDismissRequest = { dataOperationError = null },
                title = { Text("Data change could not finish") },
                text = { Text(message) },
                confirmButton = { Button(onClick = { dataOperationError = null }) { Text("OK") } },
            )
        }

        if (showDurationPicker) {
            DesktopCustomDurationDialog(
                initialMinutes = durationMinutes,
                onDismiss = { showDurationPicker = false },
                onConfirm = { mins ->
                    pendingDefaultFocusMinutes = null
                    savedWorkDurationMinutes = mins
                    if (isWorkPhase) {
                        durationMinutes = mins
                        if (focusLedger.isOpen) {
                            countdown.setTotalSeconds(mins * 60)
                            focusLedger.setPlannedSeconds(mins * 60)
                            remainingSeconds = countdown.remainingSeconds
                            if (remainingSeconds == 0) finishTimer()
                        } else {
                            remainingSeconds = mins * 60
                            countdown.reset(remainingSeconds)
                        }
                    }
                    scope.launch { prefs.setFocusWorkMinutes(mins) }
                    showDurationPicker = false
                    checkpointTimer()
                },
            )
        }

        // Airlock Brain-Dump Dialog
        if (showAirlock) {
            DesktopAirlockDialog(
                state = airlockState,
                session = airlock,
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
                            text = "Are you still working on your homework anchor (\"${focusTask?.title ?: "Deep Work"}\"), or did you get pulled into a rabbit hole?",
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
