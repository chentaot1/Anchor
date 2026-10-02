package com.anchor.adhd.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anchor.adhd.data.AnchorContainer
import com.anchor.adhd.data.model.EnergyLevel
import com.anchor.adhd.service.FocusBlockService
import com.anchor.adhd.ui.ai.AiScreen
import com.anchor.adhd.ui.chat.ChatScreen
import com.anchor.adhd.ui.components.AnchorScreenBackground
import com.anchor.adhd.ui.components.AirlockSheet
import com.anchor.adhd.ui.components.MorningRitualSheet
import com.anchor.adhd.ui.components.RoutineRunnerSheet
import com.anchor.adhd.ui.components.PostFocusDialog
import com.anchor.adhd.ui.components.ResetTimerDialog
import com.anchor.adhd.ui.components.ShutdownSheet
import com.anchor.adhd.ui.components.StrategyDialog
import com.anchor.adhd.ui.components.TaskDetailSheet
import com.anchor.adhd.ui.components.CalendarEventDetailSheet
import com.anchor.adhd.ui.focus.FocusScreen
import com.anchor.adhd.ui.plan.PlanScreen
import com.anchor.adhd.ui.vm.AnchorViewModel
import com.anchor.adhd.ui.vm.AnchorViewModelFactory
import com.anchor.adhd.ui.grove.GroveHomeScreen
import com.anchor.adhd.ui.more.MoreScreen
import com.anchor.adhd.ui.theme.LocalAnchorExtras
import com.anchor.adhd.MainActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnchorRoot(
    container: AnchorContainer,
    initialInboxText: String?,
    startFocusOnLaunch: Boolean = false,
    focusTaskId: Long? = null,
    initialTab: Int? = null,
    initialPlanTab: Int? = null,
    openMorningRitual: Boolean = false,
    openShutdown: Boolean = false,
    openCheckIn: Boolean = false,
    checkInEnergy: EnergyLevel? = null,
    intentVersion: Int = 0
) {
    val vm: AnchorViewModel = viewModel(factory = AnchorViewModelFactory(container, initialInboxText))
    var tab by rememberSaveable { mutableIntStateOf(initialTab ?: AnchorTabs.GROVE) }
    var planTab by rememberSaveable { mutableIntStateOf(initialPlanTab ?: 0) }
    var moreRoute by rememberSaveable { mutableStateOf(MoreRoute.Hub) }
    val screenState = rememberSaveableStateHolder()
    var planListView by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val message by vm.message.collectAsState()
    val aiError by vm.aiError.collectAsState()
    val pendingPostFocus by vm.pendingPostFocus.collectAsState()
    val resetSeconds by vm.resetSeconds.collectAsState()
    val selectedCbt by vm.selectedCbt.collectAsState()
    val pendingNav by vm.pendingNav.collectAsState()
    val focusDurations by vm.focusDurations.collectAsState()
    val planningSettings by vm.planningSettings.collectAsState()
    val workload by vm.workload.collectAsState()
    val replanWithTasks by vm.replanWithTasks.collectAsState()
    val showMorningRitual by vm.showMorningRitual.collectAsState()
    val showShutdown by vm.showShutdown.collectAsState()
    val selectedTask by vm.selectedTask.collectAsState()
    val selectedTaskChildren by vm.selectedTaskChildren.collectAsState()
    val selectedCalendarEvent by vm.selectedCalendarEvent.collectAsState()
    val morningRoutineRunner by vm.morningRoutineRunner.collectAsState()
    val weekly by vm.weeklySummary.collectAsState()
    val shutdownUnresolved by vm.shutdownUnresolved.collectAsState()
    var tomorrowPreview by remember { mutableStateOf<String?>(null) }
    var focusPrefsReady by remember { mutableStateOf(false) }
    var showChatSheet by remember { mutableStateOf(false) }
    var showAirlockSheet by remember { mutableStateOf(false) }
    val aiPreview by vm.aiPreview.collectAsState()
    val aiPreviewParentTitle by vm.aiPreviewParentTitle.collectAsState()
    val aiLoading by vm.aiLoading.collectAsState()
    LaunchedEffect(focusDurations) { focusPrefsReady = true }

    LaunchedEffect(vm) {
        vm.collapseChatSheet.collect { showChatSheet = false }
    }

    val aiSurface = tab == AnchorTabs.AI || showChatSheet || showAirlockSheet || (tab == AnchorTabs.MORE && moreRoute == MoreRoute.Ai)
    LaunchedEffect(aiSurface) {
        if (aiSurface) vm.onAiSurfaceOpened() else vm.onAiSurfaceClosed()
    }

    LaunchedEffect(initialInboxText, intentVersion) {
        initialInboxText?.let { vm.addInboxFromShare(it) }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { }
        LaunchedEffect(Unit) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(initialTab, initialPlanTab, intentVersion) {
        initialTab?.let {
            tab = it
            if (initialPlanTab != null && (it == MainActivity.TAB_MORE || it == MainActivity.TAB_AI)) {
                tab = AnchorTabs.MORE
                moreRoute = MoreRoute.Plan
            }
        }
        initialPlanTab?.let { planTab = it }
    }

    LaunchedEffect(pendingNav) {
        pendingNav?.let { nav ->
            tab = nav.tab
            nav.planTab?.let { planTab = it }
            nav.moreRoute?.let {
                if (it == MoreRoute.Ai) tab = AnchorTabs.AI else moreRoute = it
            }
            vm.consumePendingNav()
        }
    }

    LaunchedEffect(startFocusOnLaunch, focusTaskId, intentVersion, focusPrefsReady) {
        if (!startFocusOnLaunch || !focusPrefsReady) return@LaunchedEffect
        tab = AnchorTabs.FOCUS
        val (work, brk) = focusDurations
        vm.startFocus(work, brk, focusTaskId)
    }

    LaunchedEffect(openMorningRitual, intentVersion) {
        if (!openMorningRitual) return@LaunchedEffect
        vm.openMorningRitual()
    }

    LaunchedEffect(openShutdown, intentVersion) {
        if (!openShutdown) return@LaunchedEffect
        vm.openShutdown()
    }

    LaunchedEffect(openCheckIn, checkInEnergy, intentVersion) {
        if (!openCheckIn) return@LaunchedEffect
        tab = AnchorTabs.GROVE
        vm.applyCheckInFromIntent(checkInEnergy)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.onAppResumed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(showShutdown) {
        if (showShutdown) {
            tomorrowPreview = vm.tomorrowPreviewText()
        }
    }

    LaunchedEffect(message, aiError) {
        message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
        aiError?.let {
            snackbar.showSnackbar(it)
            vm.clearAiError()
        }
    }

    selectedCbt?.let { card ->
        StrategyDialog(
            card = card,
            onDismiss = { vm.dismissCbt() },
            onAction = { vm.runStrategyAction(it) }
        )
    }

    pendingPostFocus?.let { summary ->
        PostFocusDialog(
            summary = summary,
            onDismiss = {
                vm.dismissPostFocus()
                tab = AnchorTabs.GROVE
            },
            onConfirm = { tag, markDone, actual ->
                vm.confirmPostFocus(tag, markDone, actual)
                tab = AnchorTabs.GROVE
            },
            onShrinkNext = {
                vm.postFocusShrinkNext()
                tab = AnchorTabs.GROVE
            },
            onParkSomeday = {
                vm.postFocusParkSomeday()
                tab = AnchorTabs.GROVE
            },
            onStartTen = {
                vm.postFocusStartTen()
                tab = AnchorTabs.FOCUS
            }
        )
    }

    if (resetSeconds > 0) {
        ResetTimerDialog(remainingSeconds = resetSeconds, onDone = { vm.clearResetTimer() })
    }

    if (showMorningRitual) {
        MorningRitualSheet(
            workload = workload,
            estimateErrorPercent = weekly.avgEstimateErrorPercent.takeIf { it > 0 },
            replanItems = replanWithTasks,
            onEnergySelected = { vm.setEnergy(it) },
            onReplanReschedule = { replanId, taskId, minutes -> vm.replanRescheduleToday(replanId, taskId, minutes) },
            onReplanSomeday = { replanId, taskId -> vm.replanDeferSomeday(replanId, taskId) },
            onReplanDismiss = { vm.dismissReplan(it) },
            onRunMorningRoutine = { vm.openMorningRoutineRunner() },
            onComplete = { vm.completeMorningRitual() },
            onDismiss = { vm.dismissMorningRitual() }
        )
    }

    morningRoutineRunner?.let { routine ->
        val steps by vm.routineSteps(routine.id).collectAsState()
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { vm.dismissMorningRoutineRunner() },
            sheetState = sheetState
        ) {
            RoutineRunnerSheet(
                routine = routine,
                steps = steps,
                onStartFocus = {
                    vm.dismissMorningRoutineRunner()
                    vm.startShortFocus()
                },
                onDismiss = { vm.dismissMorningRoutineRunner() }
            )
        }
    }

    if (showShutdown) {
        ShutdownSheet(
            unresolvedTasks = shutdownUnresolved,
            tomorrowPreview = tomorrowPreview,
            onDone = { vm.shutdownMarkDone(it) },
            onDeferTomorrow = { vm.shutdownDeferTomorrow(it) },
            onSomeday = { vm.shutdownMoveSomeday(it) },
            onComplete = { vm.completeShutdown() },
            onDismiss = { vm.leaveShutdown() }
        )
    }

    selectedCalendarEvent?.let { event ->
        CalendarEventDetailSheet(
            event = event,
            onDismiss = { vm.dismissCalendarEventDetail() },
            onEditInCalendar = { vm.editCalendarEventInDeviceCalendar(event) },
            onCopyToAnchor = { vm.copyCalendarEventToAnchor(event) }
        )
    }

    selectedTask?.let { task ->
        TaskDetailSheet(
            task = task,
            children = selectedTaskChildren,
            breakdownPreview = aiPreview.takeIf { aiPreviewParentTitle == task.title },
            breakdownLoading = aiLoading && aiPreviewParentTitle == task.title,
            onDismiss = { vm.dismissTaskDetail() },
            onSave = { vm.saveTask(it) },
            onComplete = { vm.completeTask(task.id); vm.dismissTaskDetail() },
            onDelete = { vm.deleteTask(task.id) },
            onBreakDown = { vm.breakdownTask(task.title) },
            onApplyBreakdown = { vm.applyAiPreview() },
            onStartFocus = { vm.startDefaultFocus(task.id); vm.dismissTaskDetail() },
            onCompleteChild = { vm.completeTask(it.id) }
        )
    }

    if (showChatSheet) {
        ModalBottomSheet(
            onDismissRequest = { showChatSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(Modifier.fillMaxSize().height(600.dp)) {
                ChatScreen(vm, Modifier.fillMaxSize())
            }
        }
    }

    if (showAirlockSheet) {
        AirlockSheet(
            vm = vm,
            onDismiss = { showAirlockSheet = false },
            onDropAnchor = { duration ->
                vm.dropAnchorFromAirlock(duration)
                showAirlockSheet = false
                tab = AnchorTabs.FOCUS
            }
        )
    }

    AnchorShell(
        selectedTab = tab,
        onTabSelected = { selected ->
            tab = selected
            if (selected != AnchorTabs.MORE) moreRoute = MoreRoute.Hub
        },
        snackbarHostState = snackbar,
        tabBackground = { t ->
            val extras = LocalAnchorExtras.current
            when (t) {
                AnchorTabs.GROVE -> extras.groveGradient
                AnchorTabs.AI -> extras.screenGradient
                AnchorTabs.FOCUS -> extras.focusGradient
                else -> extras.screenGradient
            }
        }
    ) { padding ->
        screenState.SaveableStateProvider(tab) {
            when (tab) {
            AnchorTabs.GROVE -> GroveHomeScreen(
                vm,
                Modifier.padding(bottom = padding.calculateBottomPadding()),
                onOpenSettings = { tab = AnchorTabs.MORE; moreRoute = MoreRoute.Settings },
                onOpenForestGallery = { tab = AnchorTabs.MORE; moreRoute = MoreRoute.Forest },
                onOpenInbox = { tab = AnchorTabs.MORE; moreRoute = MoreRoute.Plan; planTab = 1 },
                onOpenChat = {
                    vm.prepareChatSheet(panic = false)
                    showChatSheet = true
                },
                onPanicDump = {
                    vm.resetAirlock()
                    showAirlockSheet = true
                },
                onOpenThink = {
                    vm.prepareChatSheet(panic = false)
                    showChatSheet = true
                }
            )
            AnchorTabs.AI -> AiScreen(vm, Modifier.padding(padding).consumeWindowInsets(padding))
            AnchorTabs.FOCUS -> FocusScreen(vm, Modifier.padding(bottom = padding.calculateBottomPadding()))
            AnchorTabs.MORE -> MoreScreen(
                vm,
                container,
                moreRoute,
                onRouteChange = { if (it == MoreRoute.Ai) tab = AnchorTabs.AI else moreRoute = it },
                Modifier.padding(padding),
                planTab = planTab,
                onPlanTabSelected = { planTab = it },
                showPlanList = planListView,
                onTogglePlanList = { planListView = !planListView }
            )
            }
        }
    }
}
