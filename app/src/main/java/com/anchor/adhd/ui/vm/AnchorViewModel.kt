@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.anchor.adhd.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.anchor.adhd.MainActivity
import com.anchor.adhd.ai.AiBrainDumpResult
import com.anchor.adhd.ai.AiBreakdownResult
import com.anchor.adhd.ai.AiReplanResult
import com.anchor.adhd.ai.AiTriageResult
import com.anchor.adhd.ai.AiBenchmarkResult
import com.anchor.adhd.ai.AiBenchmarkRunner
import com.anchor.adhd.ai.LocalAiEngine
import com.anchor.adhd.ai.LlamaBridge
import com.anchor.adhd.ai.ModelCatalog
import com.anchor.adhd.ai.ModelCatalogEntry
import com.anchor.adhd.data.AnchorContainer
import com.anchor.adhd.data.prefs.AiGpuSettings
import com.anchor.adhd.data.model.AssignmentEntity
import com.anchor.adhd.data.model.ActivityLogItem
import com.anchor.adhd.data.model.BlockRuleEntity
import com.anchor.adhd.data.model.CbtCardEntity
import com.anchor.adhd.data.model.CalendarEventEntity
import com.anchor.adhd.data.model.EnergyLevel
import com.anchor.adhd.data.model.InboxState
import com.anchor.adhd.data.model.ReplanItemEntity
import com.anchor.adhd.data.model.RoutineEntity
import com.anchor.adhd.data.model.RoutineStepEntity
import com.anchor.adhd.data.model.TaskEntity
import com.anchor.adhd.data.model.TemptationBundleEntity
import com.anchor.adhd.data.model.CheckInPatterns
import com.anchor.adhd.data.model.CheckInTag
import com.anchor.adhd.data.model.FocusGardenEntity
import com.anchor.adhd.data.model.CbtMomentTag
import com.anchor.adhd.data.model.FocusEndTag
import com.anchor.adhd.data.model.HabitScheduleType
import com.anchor.adhd.data.model.NowTask
import com.anchor.adhd.data.model.PostFocusSummary
import com.anchor.adhd.data.model.WeeklySummary
import com.anchor.adhd.data.model.FunLinkEntity
import com.anchor.adhd.data.model.DefaultFunLinks
import com.anchor.adhd.data.repository.HabitLimitException
import com.anchor.adhd.domain.CheckInCodec
import com.anchor.adhd.domain.BreakdownClamp
import com.anchor.adhd.domain.ChatFollowUp
import com.anchor.adhd.domain.ChatUndoApplier
import com.anchor.adhd.domain.ChatUndoCodec
import com.anchor.adhd.domain.ChatUndoEntry
import com.anchor.adhd.domain.ChatUndoManager
import com.anchor.adhd.domain.ChatUndoOp
import com.anchor.adhd.domain.ChatUndoSource
import com.anchor.adhd.domain.NowTaskPicker
import com.anchor.adhd.domain.buildDaySnapshot
import com.anchor.adhd.domain.pickOneTask
import com.anchor.adhd.domain.SchedulingSlots
import com.anchor.adhd.domain.scanRemind
import com.anchor.adhd.domain.scheduleTargetMillis
import com.anchor.adhd.domain.stripSchedulingMarkers
import com.anchor.adhd.domain.snoozeTargetMillis
import com.anchor.adhd.domain.splitCapture
import com.anchor.adhd.domain.AirlockHeuristic
import com.anchor.adhd.domain.StrategyAction
import com.anchor.adhd.domain.StrategyCatalog
import com.anchor.adhd.domain.StrategyPicker
import com.anchor.adhd.notify.ReminderScheduler
import com.anchor.adhd.service.PendingPostFocus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.anchor.adhd.notify.ReplanNotifier
import com.anchor.adhd.service.FocusBlockService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import com.anchor.adhd.service.FocusTimerState
import com.anchor.adhd.service.FocusTimerService
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Job
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class AirlockState(
    val rawInput: String = "",
    val isSequencing: Boolean = false,
    val primaryTaskTitle: String = "",
    val secondaryTaskTitles: List<String> = emptyList(),
    val primaryTaskId: Long? = null,
    val somaticStarter: String = "",
    val isRefiningWithAi: Boolean = false,
    val isReadyForFocus: Boolean = false,
    val selectedWorkMinutes: Int = 20
)

class AnchorViewModel(
    private val container: AnchorContainer,
    initialInboxText: String?
) : ViewModel() {

    private val appContext get() = container.appContext

    val inboxToday = container.planRepository.observeInbox(InboxState.TODAY)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val inboxSomeday = container.planRepository.observeInbox(InboxState.SOMEDAY)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val inboxWaiting = container.planRepository.observeInbox(InboxState.WAITING)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val energy = container.preferences.energyTodayFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EnergyLevel.OK)

    val scheduled = container.planRepository.observeScheduledForEnergy(energy)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val inboxTodaySorted = container.planRepository.observeInboxForEnergy(InboxState.TODAY, energy)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val assignmentsDueSoon = container.planRepository.observeAssignmentsDueSoon()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val assignments = container.planRepository.observeAssignments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val replanWithTasks = container.planRepository.observeReplanWithTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val routines = container.planRepository.observeRoutines()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val calendarToday = container.planRepository.observeCalendarDay(LocalDate.now())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val focusCountToday = container.focusRepository.observeTodaySessionCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val blockRules = container.focusRepository.observeBlockRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val companion = container.growRepository.observeCompanion()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val treeCount = container.growRepository.observeTreeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val garden = container.growRepository.observeGarden()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val plantedTiles = container.growRepository.observePlantedTiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val weeklySummary = container.growRepository.observeWeeklySummary()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeeklySummary(0, 0, 0, 0, 0, 0))

    val activityLog = container.growRepository.observeActivityLog()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val temptationBundles = container.focusRepository.observeBundles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val scheduledShieldActive = MutableStateFlow(FocusBlockService.scheduledShieldActive)

    val checkIns = container.growRepository.observeCheckIns()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val checkInPatterns = container.growRepository.observeCheckInPatterns()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CheckInPatterns(0, emptyMap()))

    val habitsWithProgress = container.habitRepository.observeHabitsWithProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeHabitCount = container.habitRepository.observeActiveHabits()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val activeFunLinks: StateFlow<List<FunLinkEntity>> = container.funLinkDao.observeActiveFunLinks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allFunLinks: StateFlow<List<FunLinkEntity>> = container.funLinkDao.observeAllFunLinks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val funCredits: StateFlow<Int> = container.preferences.funCreditsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val funStrictMode: StateFlow<Boolean> = container.preferences.funStrictModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val planningSettings = container.preferences.planningSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.anchor.adhd.data.prefs.PlanningSettings())

    val workload = combine(scheduled, calendarToday, planningSettings) { tasks, events, planning ->
        com.anchor.adhd.domain.WorkloadCalculator.calculate(tasks, events, planning.dailyCapacityMinutes)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        com.anchor.adhd.domain.WorkloadCalculator.calculate(emptyList(), emptyList(), 360)
    )

    val blockListMode = container.preferences.blockListModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.anchor.adhd.data.model.BlockListMode.BLOCKLIST)

    val timerState = FocusTimerState.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusTimerState.State())

    val activeFocusTaskId = timerState
        .map { s -> if (s.phase != FocusTimerState.Phase.IDLE) s.taskId else null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null as Long?)

    private val _selectedTaskId = MutableStateFlow<Long?>(null)
    val selectedTask = _selectedTaskId.flatMapLatest { id ->
        if (id == null) flowOf(null) else container.planRepository.observeTask(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val selectedTaskChildren = _selectedTaskId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else container.planRepository.observeChildren(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedCalendarEvent = MutableStateFlow<CalendarEventEntity?>(null)
    val selectedCalendarEvent = _selectedCalendarEvent.asStateFlow()

    private val _showMorningRitual = MutableStateFlow(false)
    val showMorningRitual = _showMorningRitual.asStateFlow()
    private val _morningRitualHandledDay = MutableStateFlow<String?>(null)

    private val _showShutdown = MutableStateFlow(false)
    val showShutdown = _showShutdown.asStateFlow()

    private val _morningRoutineRunner = MutableStateFlow<RoutineEntity?>(null)
    val morningRoutineRunner = _morningRoutineRunner.asStateFlow()

    private val _checkInTags = MutableStateFlow<Set<CheckInTag>>(emptySet())
    val blockedAttempts = MutableStateFlow(FocusBlockService.blockedAttempts)
    val checkInTags = _checkInTags.asStateFlow()

    val cbtCards = container.database.cbtCardDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val aiJobs = container.database.aiJobDao().observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val nowTask = combine(scheduled, inboxTodaySorted, energy, routines) { s, inbox, e, routines ->
        NowTaskPicker.pick(s, inbox, e, routines)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NowTask(null, com.anchor.adhd.data.model.NowSource.EMPTY))

    val laterToday = combine(scheduled, nowTask) { s, now ->
        NowTaskPicker.laterToday(s, now)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val shutdownUnresolved = combine(
        container.planRepository.observeUnresolvedTodayTasks(),
        inboxToday
    ) { scheduled, inbox ->
        (scheduled + inbox).filter { !it.isCompleted }.distinctBy { it.id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val modelDownloaded = MutableStateFlow(container.modelDownloadManager.isDownloaded())
    val samsungHealthConnected = MutableStateFlow(false)
    val aiNativeRuntime = LlamaBridge.isNativeAvailable
    val downloadProgress = container.modelDownloadManager.progress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.anchor.adhd.ai.ModelDownloadManager.Progress(0, 0, false))

    val aiGpuSettings = container.preferences.aiGpuSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiGpuSettings())

    private val _activeModelEntryId = MutableStateFlow("q8")
    val activeModelEntryId = _activeModelEntryId.asStateFlow()

    private val _benchmarkResult = MutableStateFlow<AiBenchmarkResult?>(null)
    val benchmarkResult = _benchmarkResult.asStateFlow()

    private val _benchmarkRunning = MutableStateFlow(false)
    val benchmarkRunning = _benchmarkRunning.asStateFlow()

    private val _aiPreview = MutableStateFlow<AiBreakdownResult?>(null)
    val aiPreview = _aiPreview.asStateFlow()

    private val _aiPreviewParentTitle = MutableStateFlow<String?>(null)
    val aiPreviewParentTitle = _aiPreviewParentTitle.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<com.anchor.adhd.ui.chat.ChatMessageUi>>(emptyList())
    val chatMessages = _chatMessages.asStateFlow()

    private val _chatBusy = MutableStateFlow(false)
    val chatBusy = _chatBusy.asStateFlow()

    private val _lastChatTaskTitle = MutableStateFlow<String?>(null)
    val lastChatTaskTitle = _lastChatTaskTitle.asStateFlow()
    private var pendingClarifyTitle: String? = null

    val chatUndoManager = ChatUndoManager()
    private val _undoCount = MutableStateFlow(0)
    val undoCount = _undoCount.asStateFlow()
    private val _collapseChatSheet = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val collapseChatSheet = _collapseChatSheet
    private val _chatSheetPanic = MutableStateFlow(false)
    val chatSheetPanic = _chatSheetPanic.asStateFlow()
    val filamentAllowed = container.gpuRam.filamentAllowed

    private val _brainDumpPreview = MutableStateFlow<AiBrainDumpResult?>(null)
    val brainDumpPreview = _brainDumpPreview.asStateFlow()

    private val _triagePreview = MutableStateFlow<AiTriageResult?>(null)
    val triagePreview = _triagePreview.asStateFlow()

    private val _replanAiPreview = MutableStateFlow<AiReplanResult?>(null)
    val replanAiPreview = _replanAiPreview.asStateFlow()

    private val _aiLoading = MutableStateFlow(false)
    val aiLoading = _aiLoading.asStateFlow()
    private val aiLoadingCount = AtomicInteger(0)
    private val activeAiJobs = ConcurrentHashMap.newKeySet<Job>()

    private val _aiError = MutableStateFlow<String?>(null)
    val aiError = _aiError.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private val _selectedCbt = MutableStateFlow<CbtCardEntity?>(null)
    val selectedCbt = _selectedCbt.asStateFlow()

    private val _pendingPostFocus = MutableStateFlow<PostFocusSummary?>(null)
    val pendingPostFocus = _pendingPostFocus.asStateFlow()

    private val _resetSeconds = MutableStateFlow(0)
    val resetSeconds = _resetSeconds.asStateFlow()
    private var resetTimerJob: Job? = null
    private val routineStepsFlows = ConcurrentHashMap<Long, StateFlow<List<RoutineStepEntity>>>()

    data class NavTarget(val tab: Int, val planTab: Int? = null, val moreRoute: com.anchor.adhd.ui.MoreRoute? = null)

    private val _pendingNav = MutableStateFlow<NavTarget?>(null)
    val pendingNav = _pendingNav.asStateFlow()

    private val _dailyStrategyCard = MutableStateFlow<CbtCardEntity?>(null)
    val dailyStrategyCard = _dailyStrategyCard.asStateFlow()

    private val _airlockState = MutableStateFlow(AirlockState())
    val airlockState: StateFlow<AirlockState> = _airlockState.asStateFlow()

    private var airlockInferenceJob: Job? = null

    val focusDurations = container.preferences.focusDurations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 20 to 5)

    init {
        viewModelScope.launch {
            container.modelDownloadManager.progress.collect { progress ->
                if (progress.done) refreshModelStatus()
            }
        }
        container.planRepository.activeFocusTaskIdProvider = {
            com.anchor.adhd.domain.FocusSessionGuard.lockedTaskId()
        }
        viewModelScope.launch {
            container.focusRepository.refreshBlockedPackages()
            scheduledShieldActive.value = FocusBlockService.scheduledShieldActive
            ReminderScheduler.rescheduleAll(appContext)
            reconcileOrphanFocusSession()
            syncSamsungHealth(notify = false)
            syncDeviceCalendarSuspended()
            _activeModelEntryId.value = container.preferences.activeModelEntryId()
            restoreUndoOps()
            container.preferences.checkDailyFreshStart()
        }
        viewModelScope.launch {
            // Restore the chat thread (persisted turns) on session start.
            val turns = container.chatTurnDao.observeRecent(200).first()
            _chatMessages.value = turns.map {
                com.anchor.adhd.ui.chat.ChatMessageUi(it.id, fromUser = it.fromUser, text = it.text, timestampMillis = it.createdAtMillis)
            }
            // Seed the id generator above the highest persisted id so restored and new
            // messages never collide as LazyColumn keys (LD1).
            chatSeq.set(turns.maxOfOrNull { it.id } ?: 0L)
        }
        viewModelScope.launch {
            timerState.collect {
                blockedAttempts.value = FocusBlockService.blockedAttempts
            }
        }
        viewModelScope.launch {
            PendingPostFocus.value.collect { summary ->
                if (summary != null && summary != _pendingPostFocus.value) {
                    _pendingPostFocus.value = summary
                    container.preferences.savePendingPostFocus(summary)
                }
            }
        }
        viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            val todayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
            container.growRepository.observeLatestCheckInSince(todayStart).collect { latest ->
                latest?.let { _checkInTags.value = CheckInCodec.parseTags(it.tags) }
            }
        }
        viewModelScope.launch {
            combine(cbtCards, checkInTags) { cards, tags -> cards to tags }.collect { (cards, tags) ->
                refreshDailyStrategy(cards, tags)
            }
        }
    }

    fun consumePendingNav() {
        _pendingNav.value = null
    }

    fun openReplanTab() {
        _pendingNav.value = NavTarget(MainActivity.TAB_MORE, planTab = MainActivity.PLAN_TAB_REPLAN, moreRoute = com.anchor.adhd.ui.MoreRoute.Plan)
    }

    fun openFocusTab() {
        _pendingNav.value = NavTarget(MainActivity.TAB_FOCUS)
    }

    fun openAiTab() {
        _pendingNav.value = NavTarget(MainActivity.TAB_AI)
    }

    fun openAppBlocker() {
        _pendingNav.value = NavTarget(MainActivity.TAB_MORE, moreRoute = com.anchor.adhd.ui.MoreRoute.Blocker)
    }

    private suspend fun refreshDailyStrategy(cards: List<CbtCardEntity>, tags: Set<CheckInTag>) {
        val today = LocalDate.now(ZoneId.systemDefault()).toString()
        val existingId = container.preferences.getTodayStrategyCardId()
        if (existingId != null) {
            cards.find { it.id == existingId }?.let {
                _dailyStrategyCard.value = it
                return
            }
        }
        val picked = StrategyPicker.pickDaily(cards, tags, StrategyPicker.dayIndex(), existingId)
        if (picked != null) {
            container.preferences.setDailyStrategy(today, picked.id)
            _dailyStrategyCard.value = picked
        }
    }

    fun setFocusDurations(workMinutes: Int, breakMinutes: Int) {
        viewModelScope.launch { container.preferences.setFocusDurations(workMinutes, breakMinutes) }
    }

    fun startDefaultFocus(taskId: Long? = null) {
        val (work, brk) = focusDurations.value
        startFocus(work, brk, taskId)
        openFocusTab()
    }

    fun startShortFocus(taskId: Long? = null) {
        startFocus(10, 3, taskId)
        openFocusTab()
    }

    fun applyReplanAi() {
        val preview = _replanAiPreview.value ?: return
        viewModelScope.launch {
            val queue = replanWithTasks.value
            val window = holeWindow()
            container.planRepository.applyReplanSuggestions(
                queue,
                preview.recommended_titles,
                preview.defer_titles,
                nowMillis = window.now,
                dayStartMillis = window.dayStart,
                shutdownMillis = window.shutdown
            )
            _replanAiPreview.value = null
            ReminderScheduler.rescheduleAll(appContext)
            _message.value = "Replan suggestions applied"
        }
    }

    fun createRoutine(name: String, cue: String, ifThen: String?, steps: List<String>) {
        viewModelScope.launch {
            container.planRepository.createRoutine(name, cue, ifThen, steps)
            _message.value = "Routine saved"
        }
    }

    fun runStrategyAction(action: StrategyAction) {
        dismissCbt()
        when (action) {
            StrategyAction.START_FOCUS_5 -> startFocus(5, 3, nowTask.value.task?.id)
            StrategyAction.START_FOCUS_10 -> startFocus(10, 3, nowTask.value.task?.id)
            StrategyAction.OPEN_REPLAN -> openReplanTab()
            StrategyAction.RUN_MORNING_REPLAN -> runMorningReplan()
        }
    }

    fun showStrategyForCheckInTags() {
        val tags = _checkInTags.value
        if (tags.isEmpty()) return
        val ids = StrategyCatalog.cardsForCheckIn(tags)
        val card = cbtCards.value.find { it.id in ids } ?: return
        _selectedCbt.value = card
    }

    fun clearMessage() { _message.value = null }
    fun clearAiError() { _aiError.value = null }

    fun setEnergy(level: EnergyLevel) {
        viewModelScope.launch {
            container.preferences.setEnergy(level)
            container.growRepository.logCheckIn(level, _checkInTags.value)
        }
    }

    fun toggleCheckInTag(tag: CheckInTag) {
        viewModelScope.launch {
            val updated = _checkInTags.value.toMutableSet().apply {
                if (contains(tag)) remove(tag) else add(tag)
            }
            _checkInTags.value = updated
            container.growRepository.logCheckIn(energy.value, updated)
            if (tag in updated) showStrategyForCheckInTags()
        }
    }

    fun addInbox(title: String, state: InboxState = InboxState.TODAY, scheduledStartMillis: Long? = null) {
        viewModelScope.launch {
            val ids = container.planRepository.captureToInbox(listOf(title), state)
            val id = ids.firstOrNull() ?: return@launch
            if (scheduledStartMillis != null) {
                val task = container.planRepository.getTask(id) ?: return@launch
                container.planRepository.scheduleTask(id, scheduledStartMillis, task.durationMinutes)
                ReminderScheduler.rescheduleAll(appContext)
            }
        }
    }

    fun addInboxFromShare(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val titles = splitCapture(text.trim())
            val ids = container.planRepository.captureToInbox(titles)
            persistUndo(ChatUndoOp.CreatedTasks(ids), ChatUndoSource.SHARE)
        }
    }

    fun moveInbox(taskId: Long, state: InboxState) {
        viewModelScope.launch { moveInboxSuspended(taskId, state) }
    }

    private suspend fun moveInboxSuspended(taskId: Long, state: InboxState) {
        container.planRepository.moveInbox(taskId, state)
        ReminderScheduler.rescheduleAll(appContext)
    }

    fun completeTask(id: Long) {
        viewModelScope.launch { completeTaskSuspended(id) }
    }

    private suspend fun completeTaskSuspended(id: Long) {
        container.planRepository.completeTaskCascade(id)
        ReminderScheduler.rescheduleAll(appContext)
    }

    fun scheduleTask(taskId: Long, hour: Int, minute: Int, durationMinutes: Int) {
        viewModelScope.launch {
            val zone = java.time.ZoneId.systemDefault()
            val start = LocalDate.now().atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
            container.planRepository.scheduleTask(taskId, start, durationMinutes)
            ReminderScheduler.rescheduleAll(appContext)
        }
    }

    fun rescheduleTask(taskId: Long, startMillis: Long, durationMinutes: Int) {
        viewModelScope.launch {
            container.planRepository.scheduleTask(taskId, startMillis, durationMinutes)
            ReminderScheduler.rescheduleAll(appContext)
        }
    }

    fun completeAssignment(id: Long) {
        viewModelScope.launch {
            container.planRepository.completeAssignment(id)
            ReminderScheduler.rescheduleAll(appContext)
        }
    }

    fun applyTriageOrder() {
        val preview = _triagePreview.value ?: return
        viewModelScope.launch {
            container.planRepository.applyTriageOrder(preview.ordered_task_titles)
            _triagePreview.value = null
            _message.value = "Inbox reordered"
        }
    }

    fun addTemptationBundle(name: String, packageName: String, unlockMinutes: Int) {
        viewModelScope.launch {
            container.focusRepository.addTemptationBundle(name, packageName, unlockMinutes)
        }
    }

    fun toggleBundle(id: Long, enabled: Boolean) {
        viewModelScope.launch { container.focusRepository.toggleBundle(id, enabled) }
    }

    fun addScheduledShield(packageName: String, startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        viewModelScope.launch {
            container.focusRepository.addScheduledShield(packageName, startHour, startMinute, endHour, endMinute)
            scheduledShieldActive.value = FocusBlockService.scheduledShieldActive
        }
    }

    fun refreshShields() {
        viewModelScope.launch {
            container.focusRepository.refreshBlockedPackages()
            scheduledShieldActive.value = FocusBlockService.scheduledShieldActive
        }
    }

    fun runMorningReplan() {
        viewModelScope.launch {
            container.planRepository.queueReplanForMissedTasks(
                LocalDate.now(),
                excludeTaskId = com.anchor.adhd.domain.FocusSessionGuard.lockedTaskId()
            )
            val count = container.planRepository.observeReplan().first().size
            ReplanNotifier.notifyReplanQueue(appContext, count)
            ReminderScheduler.rescheduleAll(appContext)
        }
    }

    fun openMorningRoutineRunner() {
        viewModelScope.launch {
            val routines = container.planRepository.observeRoutines().first()
            val routine = routines.firstOrNull { it.name.contains("morning", ignoreCase = true) }
                ?: routines.firstOrNull()
            if (routine == null) {
                _message.value = "Add a routine in Plan → More first"
            } else {
                _morningRoutineRunner.value = routine
            }
        }
    }

    fun dismissMorningRoutineRunner() {
        _morningRoutineRunner.value = null
    }

    fun replanRescheduleToday(replanId: Long, taskId: Long, minutes: Int) {
        viewModelScope.launch {
            val window = holeWindow()
            container.planRepository.replanRescheduleToday(
                replanId,
                taskId,
                minutes,
                nowMillis = window.now,
                dayStartMillis = window.dayStart,
                shutdownMillis = window.shutdown
            )
            ReminderScheduler.rescheduleAll(appContext)
        }
    }

    fun replanDeferSomeday(replanId: Long, taskId: Long) {
        viewModelScope.launch {
            container.planRepository.replanDeferSomeday(replanId, taskId)
            ReminderScheduler.rescheduleAll(appContext)
        }
    }

    fun dismissReplan(replanId: Long) {
        viewModelScope.launch {
            container.planRepository.resolveReplanItem(replanId)
            ReminderScheduler.rescheduleAll(appContext)
        }
    }

    fun addAssignment(title: String, course: String, dueAtMillis: Long) {
        viewModelScope.launch {
            container.planRepository.addAssignment(title, course, dueAtMillis)
            ReminderScheduler.rescheduleAll(appContext)
        }
    }

    private suspend fun reconcileOrphanFocusSession() {
        container.preferences.loadPendingPostFocus()?.let { restored ->
            _pendingPostFocus.value = restored
            PendingPostFocus.set(restored)
        }
        val active = container.database.focusSessionDao().getActiveSession() ?: return
        if (_pendingPostFocus.value?.sessionId == active.id) return
        val timer = FocusTimerState.state.value
        if (timer.sessionId == active.id && timer.phase != FocusTimerState.Phase.IDLE) return
        val task = active.taskId?.let { container.planRepository.getTask(it) }
        val endedAt = System.currentTimeMillis()
        val actualMin = ((endedAt - active.startedAtMillis) / 60_000L).toInt().coerceAtLeast(1)
        val completed = actualMin >= (active.plannedMinutes * 0.8).toInt()
        setPendingPostFocus(
            PostFocusSummary(
                sessionId = active.id,
                taskId = active.taskId,
                taskTitle = task?.title,
                plannedMinutes = active.plannedMinutes,
                actualMinutes = actualMin.coerceAtMost(active.plannedMinutes),
                completed = completed
            )
        )
    }

    private suspend fun setPendingPostFocus(summary: PostFocusSummary?) {
        _pendingPostFocus.value = summary
        PendingPostFocus.set(summary)
        container.preferences.savePendingPostFocus(summary)
    }

    private suspend fun clearPendingPostFocus() {
        setPendingPostFocus(null)
    }

    private suspend fun settlePendingPostFocusBeforeNewSession() {
        val pending = _pendingPostFocus.value ?: return
        val session = container.database.focusSessionDao().getById(pending.sessionId)
        if (session?.endedAtMillis == null) {
            container.focusRepository.finishSession(pending.sessionId, pending.completed, pending.actualMinutes)
            if (pending.completed) container.growRepository.rewardFocusComplete()
            else container.growRepository.rewardPartialDay()
        }
        container.focusRepository.wrapUpSession(pending.sessionId, null, pending.actualMinutes)
        clearPendingPostFocus()
    }

    private fun <T> launchAi(
        block: suspend () -> Result<T>,
        onSuccess: (T) -> Unit = {}
    ) {
        val job = viewModelScope.launch {
            if (aiLoadingCount.incrementAndGet() == 1) {
                _aiLoading.value = true
            }
            _aiError.value = null
            try {
                block()
                    .onSuccess(onSuccess)
                    .onFailure { _aiError.value = it.message ?: "AI failed" }
            } catch (e: CancellationException) {
                throw e
            } finally {
                if (aiLoadingCount.decrementAndGet() == 0) {
                    _aiLoading.value = false
                }
            }
        }
        activeAiJobs.add(job)
        job.invokeOnCompletion { activeAiJobs.remove(job) }
    }

    fun breakdownTask(title: String) {
        launchAi(
            block = { container.aiRepository.breakdownTask(title) },
            onSuccess = {
                _aiPreview.value = it.result
                _aiPreviewParentTitle.value = title
            }
        )
    }

    fun brainDump(text: String) {
        launchAi(
            block = { container.aiRepository.brainDump(text) },
            onSuccess = { _brainDumpPreview.value = it }
        )
    }

    fun triageInbox() {
        val titles = inboxToday.value.map { it.title }
        if (titles.isEmpty()) return
        launchAi(
            block = { container.aiRepository.triage(titles) },
            onSuccess = { _triagePreview.value = it }
        )
    }

    fun replanAssist() {
        val titles = replanWithTasks.value.mapNotNull { it.second?.title }
        if (titles.isEmpty()) return
        launchAi(
            block = { container.aiRepository.replanAssistant(titles) },
            onSuccess = { _replanAiPreview.value = it }
        )
    }

    fun weeklyPlan() {
        launchAi(
            block = {
                val week = weeklySummary.value
                val ctx = "Energy: ${energy.value}. Assignments: ${assignments.value.size}. Focus this week: ${week.focusSessions} sessions (${week.focusMinutes} min)."
                container.aiRepository.weeklyPlan(ctx)
            },
            onSuccess = {
                _aiPreview.value = it
                _aiPreviewParentTitle.value = "This week's plan"
            }
        )
    }

    fun applyAiPreview() {
        val preview = _aiPreview.value ?: return
        viewModelScope.launch {
            val parentTitle = _aiPreviewParentTitle.value ?: "Breakdown"
            applyBreakdownFromResult(parentTitle, preview)
            _aiPreview.value = null
            _aiPreviewParentTitle.value = null
            _message.value = "Added \"$parentTitle\" with steps"
        }
    }

    fun applyAiPreviewAndStart() {
        val preview = _aiPreview.value ?: return
        viewModelScope.launch {
            val parentTitle = _aiPreviewParentTitle.value ?: "Breakdown"
            val parentId = applyBreakdownFromResult(parentTitle, preview)
            _aiPreview.value = null
            _aiPreviewParentTitle.value = null
            startShortFocus(parentId)
        }
    }

    fun applyBrainDump() {
        val preview = _brainDumpPreview.value ?: return
        viewModelScope.launch {
            container.planRepository.captureToInbox(preview.tasks, aiGenerated = true)
            _brainDumpPreview.value = null
            _message.value = "Brain dump parsed"
        }
    }

    // ---- Anchor Chat (Wave 1) ----
    private val chatSeq = java.util.concurrent.atomic.AtomicLong(0)
    private val chatTurnKeep: Int = 500

    fun chatSuggestionChips(): List<String> {
        val last = _lastChatTaskTitle.value
        return buildList {
            add("What's today?")
            add("Pick one")
            add("Think about this")
            if (chatUndoManager.canUndo()) add("Today's changes")
            if (last != null) {
                add("Break that down")
                add("Mark that done")
            } else {
                add("Brain dump")
                add("Remind me at 5")
            }
        }
    }

    fun sendChat(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _chatBusy.value) return
        if (trimmed.equals("Today's changes", ignoreCase = true)) {
            undoChat()
            return
        }
        appendChat(com.anchor.adhd.ui.chat.ChatMessageUi(chatSeq.incrementAndGet(), fromUser = true, text = trimmed))
        viewModelScope.launch {
            _chatBusy.value = true
            try {
                if (ChatFollowUp.isAffirmative(trimmed) && pendingClarifyTitle != null) {
                    val title = pendingClarifyTitle!!
                    pendingClarifyTitle = null
                    handleChatIntent(com.anchor.adhd.domain.ChatIntent.CaptureTasks(listOf(title), trimmed))
                    return@launch
                }
                if (ChatFollowUp.isNegative(trimmed)) pendingClarifyTitle = null

                val resolved = ChatFollowUp.resolve(trimmed, _lastChatTaskTitle.value)
                val desktopRoute = com.anchor.adhd.domain.DesktopCompatibleRouter.routeQuick(resolved)
                if (handleDesktopCommand(desktopRoute)) return@launch
                var intent = if (trimmed.equals("Think about this", ignoreCase = true)) {
                    val last = _lastChatTaskTitle.value
                    if (last != null) com.anchor.adhd.domain.ChatIntent.Breakdown(last)
                    else com.anchor.adhd.domain.ChatIntent.Triage(trimmed)
                } else {
                    com.anchor.adhd.domain.ChatIntentRouter.route(resolved)
                }
                if (intent is com.anchor.adhd.domain.ChatIntent.CaptureTasks &&
                    desktopRoute is com.anchor.adhd.domain.SmartRouteResult.BrainDump) {
                    intent = com.anchor.adhd.domain.ChatIntent.BrainDump(resolved)
                } else if (intent is com.anchor.adhd.domain.ChatIntent.CaptureTasks &&
                    desktopRoute is com.anchor.adhd.domain.SmartRouteResult.TaskBreakdown &&
                    !resolved.startsWith("add ", ignoreCase = true) && !resolved.startsWith("remember ", ignoreCase = true)) {
                    intent = com.anchor.adhd.domain.ChatIntent.Breakdown(desktopRoute.taskTitle)
                }
                handleChatIntent(intent)
            } finally {
                _chatBusy.value = false
            }
        }
    }

    /** Same fast command routing as the desktop omnibar; mobile actions respect existing locks. */
    private suspend fun handleDesktopCommand(route: com.anchor.adhd.domain.SmartRouteResult): Boolean {
        when (route) {
            is com.anchor.adhd.domain.SmartRouteResult.TimerAction -> {
                replyWithAction("Ready to start ${route.durationMinutes} minutes of focus?") { id ->
                    com.anchor.adhd.ui.chat.ChatAction.StartFocus(id, findTask(route.taskTitle)?.id, route.durationMinutes)
                }
            }
            is com.anchor.adhd.domain.SmartRouteResult.BlockerAction -> {
                val installed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.anchor.adhd.domain.InstalledApps.loadLaunchable(appContext)
                }
                val exact = installed.filter { it.label.equals(route.target, true) || it.packageName.equals(route.target, true) }
                val matches = exact.ifEmpty { installed.filter { it.label.contains(route.target, true) || it.packageName.contains(route.target, true) } }
                if (matches.size != 1) {
                    reply(if (matches.isEmpty()) "I couldn't find that installed app. Choose it in App Blocker."
                    else "More than one app matches. Choose the exact app in App Blocker.")
                } else {
                    val app = matches.single()
                    val state = container.advancedBlocker.state.first()
                    val existing = state.apps.find { it.packageName == app.packageName }
                    val now = System.currentTimeMillis()
                    if (timerState.value.phase == FocusTimerState.Phase.WORK || state.lockdownUntilMillis > now ||
                        (existing?.lockedUntilMillis ?: 0) > now || app.packageName in com.anchor.adhd.domain.WhitelistBasics.essentialPackages) {
                        reply("That protection can't be changed while it's locked. Review it in App Blocker.")
                    } else {
                        if (route.enable) container.advancedBlocker.saveApp((existing ?: com.anchor.adhd.domain.AppProtection(app.packageName, app.label))
                            .copy(mode = com.anchor.adhd.domain.ProtectionMode.ALWAYS))
                        else container.advancedBlocker.removeApp(app.packageName)
                        val updated = container.advancedBlocker.state.first().apps.find { it.packageName == app.packageName }
                        reply(if (route.enable && updated?.mode == com.anchor.adhd.domain.ProtectionMode.ALWAYS)
                            "Always protection configured for ${app.label}." + (if (FocusBlockService.isEnabled()) "" else " Enable Accessibility in App Blocker to enforce it.")
                        else if (!route.enable && updated == null) "Removed ${app.label} from always protection. Other focus rules may still apply."
                        else "The protection didn't change. Check App Blocker.")
                    }
                }
            }
            is com.anchor.adhd.domain.SmartRouteResult.GroundingReset -> {
                val answer = container.aiRepository.answerChat("I'm overwhelmed. ${route.recommendedTask}", currentDaySnapshot().compactPrompt()).getOrNull()
                replyWithAction(answer ?: "Take a breath. Try one tiny step: ${route.recommendedTask}.") { id ->
                    com.anchor.adhd.ui.chat.ChatAction.StartFocus(id, null, route.suggestedMinutes)
                }
            }
            is com.anchor.adhd.domain.SmartRouteResult.ResetDataAction -> {
                reply("Open Settings → Data to review and confirm a reset. Your data hasn't changed.")
                _pendingNav.value = NavTarget(MainActivity.TAB_MORE, moreRoute = com.anchor.adhd.ui.MoreRoute.Settings)
            }
            is com.anchor.adhd.domain.SmartRouteResult.SyllabusAction -> reply("Add course deadlines in Plan → Assignments. Syllabus PDF import is currently available on desktop.")
            else -> return false
        }
        return true
    }

    private fun phaseBRoute(verb: String, text: String): com.anchor.adhd.domain.ChatIntent? = when (verb) {
        "capture_tasks" -> com.anchor.adhd.domain.ChatIntent.CaptureTasks(splitCapture(text), text)
        "whats_today" -> com.anchor.adhd.domain.ChatIntent.WhatsToday
        "remind" -> {
            val (title, at) = scanRemind(text, System.currentTimeMillis())
            com.anchor.adhd.domain.ChatIntent.Remind(title, at, text)
        }
        "mark_done" -> com.anchor.adhd.domain.ChatIntent.MarkDone(stripChatVerb(text, "mark_done"))
        "move_someday" -> com.anchor.adhd.domain.ChatIntent.MoveSomeday(stripChatVerb(text, "move_someday"))
        "breakdown" -> com.anchor.adhd.domain.ChatIntent.Breakdown(stripChatVerb(text, "breakdown"))
        "brain_dump" -> {
            val dump = text.replaceFirst(
                Regex("""^(brain\s*dump:?|dump this)\s*""", RegexOption.IGNORE_CASE),
                ""
            ).trim()
            if (dump.isBlank()) {
                com.anchor.adhd.domain.ChatIntent.Clarify("Paste the dump — errands, thoughts, anything. I'll turn it into tasks.")
            } else {
                com.anchor.adhd.domain.ChatIntent.BrainDump(dump)
            }
        }
        "schedule" -> com.anchor.adhd.domain.ChatIntent.Schedule(text)
        "triage" -> com.anchor.adhd.domain.ChatIntent.Triage(text)
        "replan" -> com.anchor.adhd.domain.ChatIntent.Replan(text)
        "start_focus" -> com.anchor.adhd.domain.ChatIntent.StartFocus(text, 20)
        "snooze" -> com.anchor.adhd.domain.ChatIntent.Snooze(text)
        "pick_one" -> com.anchor.adhd.domain.ChatIntent.PickOne(null)
        else -> null
    }

    /** Strips the leading action verb/phrase so task-title matching gets a clean query (MD3). */
    private fun stripChatVerb(text: String, kind: String): String {
        val verbs = when (kind) {
            "mark_done" -> listOf("mark done", "mark as done", "complete", "done", "finish")
            "move_someday" -> listOf("move to someday", "move to later", "someday", "move")
            "breakdown" -> listOf("break down", "break it down", "breakdown")
            else -> emptyList()
        }
        var out = text
        for (v in verbs) {
            out = out.replaceFirst(Regex(v, RegexOption.IGNORE_CASE), "").trim()
        }
        return out.ifBlank { text }
    }

    fun undoChat() {
        viewModelScope.launch {
            val entry = chatUndoManager.popEntry() ?: return@launch
            if (entry.dbId > 0) container.chatUndoDao.deleteById(entry.dbId)
            val confirmation = ChatUndoApplier.undo(entry.op, container.planRepository)
            if (confirmation != null) _message.value = confirmation
            ReminderScheduler.rescheduleAll(appContext)
            _undoCount.value = chatUndoManager.snapshot().size
        }
    }

    fun prepareChatSheet(panic: Boolean) {
        _chatSheetPanic.value = panic
    }

    fun setThinkMode() {
        _chatSheetPanic.value = false
    }

    fun onAiSurfaceOpened() = container.gpuRam.onAiSurfaceOpened()

    fun onAiSurfaceClosed() = container.gpuRam.onAiSurfaceClosed()

    fun panicDump(text: String) {
        val titles = splitCapture(text.trim()).filter { it.isNotBlank() }
        if (titles.isEmpty()) return
        viewModelScope.launch {
            val ids = container.planRepository.captureToInbox(titles)
            persistUndo(ChatUndoOp.CreatedTasks(ids), ChatUndoSource.PANIC)
            rememberChatTask(titles.firstOrNull())
            collapseAfterCommit()
            _message.value = if (ids.size == 1) {
                "Added \"${titles.first()}\" to Today."
            } else {
                "Added ${ids.size} to Today."
            }
        }
    }

    fun updateAirlockInput(input: String) {
        _airlockState.value = _airlockState.value.copy(rawInput = input)
    }

    fun setAirlockWorkMinutes(minutes: Int) {
        _airlockState.value = _airlockState.value.copy(selectedWorkMinutes = minutes)
    }

    fun resetAirlock() {
        airlockInferenceJob?.cancel()
        airlockInferenceJob = null
        LlamaBridge.abortGenerate()
        val defaultWork = focusDurations.value.first.coerceAtLeast(10)
        _airlockState.value = AirlockState(selectedWorkMinutes = defaultWork)
    }

    fun editAirlockDump() {
        _airlockState.value = _airlockState.value.copy(isReadyForFocus = false)
    }

    fun runAirlockSequencer(rawText: String) {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) return

        // Tier 1: Deterministic Heuristic (< 5ms)
        val parseResult = AirlockHeuristic.parse(trimmed)
        if (parseResult.primaryTask.isBlank()) return

        _airlockState.value = _airlockState.value.copy(
            isSequencing = true,
            primaryTaskTitle = parseResult.primaryTask,
            secondaryTaskTitles = parseResult.secondaryTasks,
            somaticStarter = parseResult.defaultSomaticStarter,
            isReadyForFocus = true
        )

        viewModelScope.launch {
            try {
                // Save secondary tasks safely to inbox (relieving mental pressure)
                if (parseResult.secondaryTasks.isNotEmpty()) {
                    container.planRepository.captureToInbox(
                        parseResult.secondaryTasks,
                        state = InboxState.TODAY
                    )
                }

                // Save primary task to inbox and retain ID
                val primaryIds = container.planRepository.captureToInbox(
                    listOf(parseResult.primaryTask),
                    state = InboxState.TODAY
                )
                val primaryId = primaryIds.firstOrNull()

                _airlockState.value = _airlockState.value.copy(
                    isSequencing = false,
                    primaryTaskId = primaryId
                )

                // Tier 2: Asynchronous on-device LLM refinement (if model ready)
                if (container.aiEngine.isModelReady) {
                    airlockInferenceJob?.cancel()
                    airlockInferenceJob = launch {
                        _airlockState.value = _airlockState.value.copy(isRefiningWithAi = true)
                        try {
                            val systemPrompt = "You are an ADHD executive function coach. Given a task, output ONLY a single, concrete, 10-second physical first step under 15 words that removes starting friction. No conversational filler, no markdown."
                            val userPrompt = "Task: \"${parseResult.primaryTask}\"\nFirst physical step:"

                            val sb = StringBuilder()
                            container.aiEngine.generate(
                                systemPrompt = systemPrompt,
                                userPrompt = userPrompt,
                                maxTokens = 28
                            ).collect { token ->
                                sb.append(token)
                                val candidate = AirlockHeuristic.extractCleanSomaticStarter(sb.toString(), parseResult.defaultSomaticStarter)
                                _airlockState.value = _airlockState.value.copy(
                                    somaticStarter = candidate
                                )
                            }
                        } catch (e: CancellationException) {
                            // Cancelled if user dropped anchor early
                        } catch (t: Throwable) {
                            // Retain Tier 1 heuristic starter on any AI error
                        } finally {
                            _airlockState.value = _airlockState.value.copy(isRefiningWithAi = false)
                        }
                    }
                }
            } catch (t: Throwable) {
                _airlockState.value = _airlockState.value.copy(isSequencing = false)
            }
        }
    }

    fun dropAnchorFromAirlock(workMinutes: Int? = null) {
        val state = _airlockState.value
        val duration = workMinutes ?: state.selectedWorkMinutes
        val taskId = state.primaryTaskId

        // Abort AI inference immediately (< 30ms abort latency)
        airlockInferenceJob?.cancel()
        airlockInferenceJob = null
        LlamaBridge.abortGenerate()

        val breakMin = focusDurations.value.second.coerceAtLeast(3)
        startFocus(duration, breakMin, taskId)

        _airlockState.value = AirlockState()
    }

    fun clearChat() {
        _chatMessages.value = emptyList()
        _lastChatTaskTitle.value = null
        pendingClarifyTitle = null
        viewModelScope.launch {
            container.chatTurnDao.clear()
            container.chatUndoDao.clear()
        }
    }

    fun clearAiMemoryAndCache() {
        _lastChatTaskTitle.value = null
        pendingClarifyTitle = null
        viewModelScope.launch {
            container.database.aiJobDao().clear()
            container.aiEngine.ensureUnloaded()
        }
    }

    fun clearFocusSessionHistory() {
        viewModelScope.launch {
            container.database.focusSessionDao().clear()
        }
    }

    fun resetApp(deleteModel: Boolean) {
        viewModelScope.launch {
            container.appResetManager.resetAll(deleteModel)
        }
    }

    private fun rememberChatTask(title: String?) {
        val clean = title?.trim()?.trim('"')?.takeIf { it.isNotBlank() } ?: return
        _lastChatTaskTitle.value = clean
        pendingClarifyTitle = null
    }

    private fun appendChat(msg: com.anchor.adhd.ui.chat.ChatMessageUi) {
        val current = _chatMessages.value
        // Newest at index 0 (bottom) to match the reverseLayout chat list + autoscroll.
        val trimmed = (listOf(msg) + current).take(200)
        _chatMessages.value = trimmed
        viewModelScope.launch {
            container.chatTurnDao.insert(
                com.anchor.adhd.data.model.ChatTurnEntity(
                    fromUser = msg.fromUser,
                    text = msg.text,
                    createdAtMillis = msg.timestampMillis,
                )
            )
            // Bound table growth so the persisted session never grows unbounded (MD2).
            container.chatTurnDao.prune(chatTurnKeep)
        }
    }

    private suspend fun handleChatIntent(intent: com.anchor.adhd.domain.ChatIntent) {
        when (intent) {
            is com.anchor.adhd.domain.ChatIntent.CaptureTasks -> {
                val now = System.currentTimeMillis()
                val titles = intent.titles
                    .map { stripSchedulingMarkers(it) }
                    .filter { it.isNotBlank() }
                val safeTitles = titles.ifEmpty { intent.titles }
                val target = scheduleTargetMillis(intent.raw, now)
                val ids = container.planRepository.captureToInbox(safeTitles)
                if (target != null) {
                    ids.forEach { id ->
                        val task = container.planRepository.getTask(id)
                        container.planRepository.scheduleTask(id, target, task?.durationMinutes ?: 20)
                    }
                }
                persistUndo(ChatUndoOp.CreatedTasks(ids), ChatUndoSource.CHAT)
                ReminderScheduler.rescheduleAll(appContext)
                rememberChatTask(safeTitles.firstOrNull())
                val n = ids.size
                if (n == 1) {
                    if (target != null) {
                        replyUndoable("Scheduled \"${safeTitles.first()}\" for ${formattedTime(target)}.")
                    } else {
                        replyUndoable("Got it — added \"${safeTitles.first()}\" to Today.")
                    }
                } else {
                    replyUndoable(if (target != null) "Added $n tasks for ${formattedTime(target)}." else "Added $n tasks to Today.")
                }
                collapseAfterCommit()
            }
            com.anchor.adhd.domain.ChatIntent.WhatsToday -> {
                reply(currentDaySnapshot().compactWhatsToday())
            }
            is com.anchor.adhd.domain.ChatIntent.Remind -> {
                val id = container.planRepository.captureToInbox(listOf(intent.title)).first()
                if (intent.atMillis != null) {
                    val task = container.planRepository.getTask(id)
                    container.planRepository.scheduleTask(id, intent.atMillis, task?.durationMinutes ?: 20)
                }
                persistUndo(ChatUndoOp.CreatedTasks(listOf(id)), ChatUndoSource.CHAT)
                ReminderScheduler.rescheduleAll(appContext)
                rememberChatTask(intent.title)
                if (intent.atMillis != null) {
                    replyUndoable("Got it — \"${intent.title}\" is scheduled for ${formattedTime(intent.atMillis)}.")
                } else {
                    replyUndoable("Got it — \"${intent.title}\" is on Today.")
                }
                collapseAfterCommit()
            }
            is com.anchor.adhd.domain.ChatIntent.MarkDone -> {
                val candidates = findTaskCandidates(intent.query)
                if (candidates.size > 1) {
                    reply("I found a few tasks matching that. Which did you mean? ${candidates.take(5).joinToString { "\"${it.title}\"" }}")
                } else {
                    val task = candidates.firstOrNull()
                    when {
                        task == null -> reply("I couldn't find a task matching \"${intent.query}\". Try the exact title.")
                        task.isCompleted -> {
                            // Already done: nothing to change, and nothing to undo.
                            reply("That task is already marked complete.")
                        }
                        else -> {
                            persistUndo(ChatUndoOp.CompletionChanged(listOf(task.id), false), ChatUndoSource.CHAT)
                            container.planRepository.completeTaskCascade(task.id)
                            ReminderScheduler.rescheduleAll(appContext)
                            rememberChatTask(task.title)
                            replyUndoable("Done — marked \"${task.title}\" complete.")
                        }
                    }
                }
            }
            is com.anchor.adhd.domain.ChatIntent.MoveSomeday -> {
                val candidates = findTaskCandidates(intent.query)
                if (candidates.size > 1) {
                    reply("I found a few tasks matching that. Which did you mean? ${candidates.take(5).joinToString { "\"${it.title}\"" }}")
                } else {
                    val task = candidates.firstOrNull()
                    if (task == null) {
                        reply("I couldn't find a task matching \"${intent.query}\".")
                    } else {
                        persistUndo(ChatUndoOp.InboxMoved(task.id, task.inboxState), ChatUndoSource.CHAT)
                        container.planRepository.moveInbox(task.id, com.anchor.adhd.data.model.InboxState.SOMEDAY)
                        rememberChatTask(task.title)
                        replyUndoable("Moved \"${task.title}\" to Someday.")
                    }
                }
            }
            is com.anchor.adhd.domain.ChatIntent.Clarify -> {
                pendingClarifyTitle = intent.pendingTitle
                reply(intent.question)
            }
            is com.anchor.adhd.domain.ChatIntent.Snooze -> {
                val now = System.currentTimeMillis()
                val at = snoozeTargetMillis(intent.query, now)
                val query = intent.query
                    .replace(Regex("""\b(snooze|defer|later today|push to night|remind again)\b""", RegexOption.IGNORE_CASE), "")
                    .trim()
                    .trim('"')
                val task = findTask(query)
                    ?: _lastChatTaskTitle.value?.let { findTask(it) }
                if (task == null) {
                    reply("Which task should I snooze? Name it, or capture one first.")
                } else {
                    persistUndo(
                        ChatUndoOp.ScheduleChanged(
                            task.id,
                            task.scheduledStartMillis,
                            task.durationMinutes,
                        ),
                        ChatUndoSource.CHAT
                    )
                    container.planRepository.scheduleTask(task.id, at, task.durationMinutes.coerceAtLeast(15))
                    ReminderScheduler.rescheduleAll(appContext)
                    rememberChatTask(task.title)
                    replyUndoable("Snoozed \"${task.title}\" until ${formattedTime(at)}.")
                }
            }
            is com.anchor.adhd.domain.ChatIntent.BrainDump -> {
                val result = container.aiRepository.brainDump(intent.text).getOrNull()
                if (result == null) {
                    reply("I couldn't parse that dump right now.")
                } else {
                    val n = result.tasks.size
                    val list = result.tasks.mapIndexed { i, t -> "${i + 1}. $t" }.joinToString("\n")
                    replyWithAction(
                        "I pulled $n task${if (n == 1) "" else "s"}:\n$list",
                    ) { id -> com.anchor.adhd.ui.chat.ChatAction.ApplyBrainDump(id, result) }
                }
            }
            is com.anchor.adhd.domain.ChatIntent.Breakdown -> {
                val query = intent.query.ifBlank { _lastChatTaskTitle.value }.orEmpty()
                if (query.isBlank()) {
                    reply("Name a task to break down, or capture one first.")
                } else if (!modelDownloaded.value) {
                    reply("I can break that down, but I need the on-device AI model first. Download it in More → Settings, then try again.")
                } else {
                    rememberChatTask(query)
                    val result = container.aiRepository.breakdownTask(query).getOrNull()
                    if (result == null) {
                        reply("I couldn't break that down right now. Is the AI model ready?")
                    } else {
                        val list = result.result.steps.mapIndexed { i, t -> "${i + 1}. $t" }.joinToString("\n")
                        replyWithAction(
                            "Here's a breakdown of \"$query\":\n$list",
                        ) { id -> com.anchor.adhd.ui.chat.ChatAction.ApplyBreakdown(id, query, result.result) }
                    }
                }
            }
            is com.anchor.adhd.domain.ChatIntent.Schedule -> {
                // Create + schedule the task at the parsed time or date. Uses the undo stack so the
                // auto-execution is recoverable (the reply carries an Undo button).
                val at = scheduleTargetMillis(intent.query, System.currentTimeMillis())
                if (at == null) {
                    reply("I didn't catch a time or date in that. Try \"put chem study tomorrow\" or \"at 3pm\".")
                } else {
                    val cleaned = stripSchedulingMarkers(intent.query)
                    val title = cleaned.ifBlank { "Scheduled task" }
                    val id = container.planRepository.captureToInbox(listOf(title)).first()
                    container.planRepository.scheduleTask(id, at, 20)
                    persistUndo(ChatUndoOp.CreatedTasks(listOf(id)), ChatUndoSource.CHAT)
                    ReminderScheduler.rescheduleAll(appContext)
                    rememberChatTask(title)
                    replyUndoable("Scheduled \"$title\" for ${formattedTime(at)}.")
                    collapseAfterCommit()
                }
            }
            is com.anchor.adhd.domain.ChatIntent.Triage -> {
                val titles = inboxTodaySorted.value.map { it.title }
                if (titles.isEmpty()) {
                    reply("Your inbox is empty — nothing to triage.")
                } else if (!modelDownloaded.value) {
                    reply("Triage needs the on-device AI model. Check installation in Settings → AI.")
                } else {
                    val result = container.aiRepository.triage(titles).getOrNull()
                    if (result == null) reply("I couldn't triage right now.")
                    else {
                        val list = result.ordered_task_titles.mapIndexed { i, t -> "${i + 1}. $t" }.joinToString("\n")
                        replyWithAction("Suggested order:\n$list") { id -> com.anchor.adhd.ui.chat.ChatAction.ApplyTriage(id, result) }
                    }
                }
            }
            is com.anchor.adhd.domain.ChatIntent.Replan -> {
                val queue = replanWithTasks.value
                val titles = queue.mapNotNull { it.second?.title }
                if (titles.isEmpty()) {
                    reply("Nothing to replan right now.")
                } else if (!modelDownloaded.value) {
                    reply("Replan needs the on-device AI model. Check installation in Settings → AI.")
                } else {
                    val result = container.aiRepository.replanAssistant(titles).getOrNull()
                    if (result == null) reply("I couldn't build a replan right now.")
                    else replyWithAction("Here's a suggestion:") { id -> com.anchor.adhd.ui.chat.ChatAction.ApplyReplan(id, result) }
                }
            }
            is com.anchor.adhd.domain.ChatIntent.StartFocus -> {
                val task = findTask(intent.query)
                task?.title?.let { rememberChatTask(it) }
                replyWithAction(
                    "Ready for ${intent.minutes} minutes of focus${task?.let { " on \"${it.title}\"" } ?: ""}?",
                ) { id -> com.anchor.adhd.ui.chat.ChatAction.StartFocus(id, task?.id, intent.minutes) }
            }
            is com.anchor.adhd.domain.ChatIntent.PickOne -> {
                handlePickOne(intent)
            }
            is com.anchor.adhd.domain.ChatIntent.Chat -> {
                val answer = if (modelDownloaded.value) {
                    container.aiRepository.answerChat(
                        intent.text,
                        inboxTodaySorted.value.filter { !it.isCompleted }.take(12).joinToString("\n") { "- ${it.title}" },
                        conversationHistory()
                    ).getOrNull()
                } else {
                    null
                }
                if (!answer.isNullOrBlank()) {
                    reply(answer)
                } else {
                    val last = _lastChatTaskTitle.value
                    val lastHint = last?.let { " Last thing we talked about: \"$it\"." } ?: ""
                    reply("I can help with your plan, but the on-device AI model is not ready yet.$lastHint Check model installation in Settings → AI.")
                }
            }
        }
    }

    private fun conversationHistory(): List<Pair<String, String>> =
        _chatMessages.value.dropLast(1).takeLast(6).map {
            (if (it.fromUser) "user" else "assistant") to it.text.take(400)
        }

    private fun findTask(query: String): com.anchor.adhd.data.model.TaskEntity? {
        val matches = findTaskCandidates(query)
        // Only auto-execute when there is exactly one clear, unambiguous match (MD1).
        return if (matches.size == 1) matches.first() else null
    }

    private fun findTaskCandidates(query: String): List<com.anchor.adhd.data.model.TaskEntity> {
        val q = query.lowercase().trim()
        if (q.isEmpty()) return emptyList()
        return inboxTodaySorted.value
            .filter { task -> task.title.lowercase().contains(q) || q.contains(task.title.lowercase()) }
    }

    private fun buildWhatsToday(): String {
        val scheduled = scheduled.value
        val inbox = inboxTodaySorted.value
        val replan = replanWithTasks.value
        val dueSoon = assignmentsDueSoon.value

        val parts = mutableListOf<String>()
        if (scheduled.isNotEmpty()) {
            val top = scheduled.take(3)
            parts.add("Planned: ${top.joinToString { it.title }}${if (scheduled.size > 3) " +${scheduled.size - 3} more" else ""}")
        }
        if (inbox.isNotEmpty()) parts.add("Inbox: ${inbox.size} task(s)")
        if (replan.isNotEmpty()) parts.add("To replan: ${replan.size}")
        if (dueSoon.isNotEmpty()) parts.add("Due soon: ${dueSoon.joinToString { it.title }}")
        return if (parts.isEmpty()) "Your day is clear — nice. Capture something or start a focus block!" else parts.joinToString("\n")
    }

    private fun reply(text: String) {
        appendChat(com.anchor.adhd.ui.chat.ChatMessageUi(chatSeq.incrementAndGet(), fromUser = false, text = text))
    }

    private fun replyUndoable(text: String) {
        appendChat(
            com.anchor.adhd.ui.chat.ChatMessageUi(chatSeq.incrementAndGet(), fromUser = false, text = text, undoable = true)
        )
    }

    private fun replyWithAction(text: String, actionBuilder: (Long) -> com.anchor.adhd.ui.chat.ChatAction) {
        val messageId = chatSeq.incrementAndGet()
        appendChat(
            com.anchor.adhd.ui.chat.ChatMessageUi(messageId, fromUser = false, text = text, action = actionBuilder(messageId))
        )
    }

    /** Executes a confirm-flow action from a chat reply card. */
    fun actOnChatAction(action: com.anchor.adhd.ui.chat.ChatAction) {
        viewModelScope.launch {
            when (action) {
                is com.anchor.adhd.ui.chat.ChatAction.ApplyBreakdown -> {
                    applyBreakdownFromResult(action.parentTitle, action.result)
                    clearChatAction(action.messageId)
                    _message.value = "Added breakdown steps"
                    collapseAfterCommit()
                }
                is com.anchor.adhd.ui.chat.ChatAction.ApplyBrainDump -> {
                    val ids = container.planRepository.captureToInbox(action.result.tasks, aiGenerated = true)
                    persistUndo(ChatUndoOp.CreatedTasks(ids), ChatUndoSource.APPLY)
                    clearChatAction(action.messageId)
                    _message.value = "Brain dump added"
                    collapseAfterCommit()
                }
                is com.anchor.adhd.ui.chat.ChatAction.ApplyTriage -> {
                    container.planRepository.applyTriageOrder(action.result.ordered_task_titles)
                    clearChatAction(action.messageId)
                    _message.value = "Inbox reordered"
                    collapseAfterCommit()
                }
                is com.anchor.adhd.ui.chat.ChatAction.ApplyReplan -> {
                    val queue = replanWithTasks.value
                    val window = holeWindow()
                    container.planRepository.applyReplanSuggestions(
                        queue,
                        action.result.recommended_titles,
                        action.result.defer_titles,
                        nowMillis = window.now,
                        dayStartMillis = window.dayStart,
                        shutdownMillis = window.shutdown
                    )
                    ReminderScheduler.rescheduleAll(appContext)
                    clearChatAction(action.messageId)
                    _message.value = "Replan applied"
                    collapseAfterCommit()
                }
                is com.anchor.adhd.ui.chat.ChatAction.StartFocus -> {
                    clearChatAction(action.messageId)
                    collapseAfterCommit()
                    startFocus(action.minutes, focusDurations.value.second, action.taskId)
                }
            }
        }
    }

    /** Removes the action from a message so an applied card can't be tapped again (idempotency). */
    private fun clearChatAction(messageId: Long) {
        _chatMessages.value = _chatMessages.value.map {
            if (it.id == messageId) it.copy(action = null) else it
        }
    }

    private fun formattedTime(millis: Long): String {
        val zone = java.time.ZoneId.systemDefault()
        val dateTime = java.time.Instant.ofEpochMilli(millis).atZone(zone)
        val today = java.time.LocalDate.now(zone)
        val time = dateTime.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
        return when {
            dateTime.toLocalDate() == today -> time
            dateTime.toLocalDate() == today.plusDays(1) -> "tomorrow at $time"
            dateTime.year == today.year -> dateTime.format(java.time.format.DateTimeFormatter.ofPattern("MMM d")) + " at " + time
            else -> dateTime.format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy")) + " at " + time
        }
    }

    fun startFocus(workMinutes: Int = 20, breakMinutes: Int = 5, taskId: Long? = null) {
        viewModelScope.launch { startFocusSuspended(workMinutes, breakMinutes, taskId) }
    }

    private suspend fun startFocusSuspended(workMinutes: Int, breakMinutes: Int, taskId: Long?) {
        val timer = FocusTimerState.state.value
        if (timer.phase != FocusTimerState.Phase.IDLE) {
            appContext.startService(
                android.content.Intent(appContext, com.anchor.adhd.service.FocusTimerService::class.java).apply {
                    action = com.anchor.adhd.service.FocusTimerService.ACTION_CANCEL
                }
            )
        }
        settlePendingPostFocusBeforeNewSession()
        container.gpuRam.unloadNowAndWait()
        container.focusRepository.startSession(taskId, workMinutes, breakMinutes, locked = true)
    }

    fun requestEndFocus(completed: Boolean) {
        viewModelScope.launch {
            val active = container.database.focusSessionDao().getActiveSession() ?: return@launch
            val timer = FocusTimerState.state.value
            val settings = container.preferences.getReminderSettings()
            if (!completed && settings.timerHardStop &&
                timer.sessionId == active.id && timer.phase == FocusTimerState.Phase.WORK
            ) {
                _message.value = "Hard stop enabled — wait for timer to finish"
                return@launch
            }
            val sessionCompleted = when {
                timer.sessionId == active.id && timer.phase == FocusTimerState.Phase.BREAK -> true
                timer.sessionId == active.id && timer.phase == FocusTimerState.Phase.WORK -> completed
                else -> completed
            }
            val actualMin = when {
                timer.sessionId == active.id && timer.phase == FocusTimerState.Phase.WORK -> {
                    val elapsed = timer.totalWorkSeconds - timer.remainingSeconds
                    (elapsed / 60).coerceAtLeast(1)
                }
                timer.sessionId == active.id && timer.phase == FocusTimerState.Phase.BREAK -> active.plannedMinutes
                else -> active.plannedMinutes
            }
            appContext.startService(
                android.content.Intent(appContext, FocusTimerService::class.java).apply {
                    action = FocusTimerService.ACTION_CANCEL
                }
            )
            val task = active.taskId?.let { container.planRepository.getTask(it) }
            val summary = PostFocusSummary(
                sessionId = active.id,
                taskId = active.taskId,
                taskTitle = task?.title,
                plannedMinutes = active.plannedMinutes,
                actualMinutes = actualMin,
                completed = sessionCompleted,
                blockedAttempts = FocusBlockService.blockedAttempts
            )
            setPendingPostFocus(summary)
        }
    }

    fun confirmPostFocus(endTag: FocusEndTag?, markDone: Boolean, actualMinutes: Int) {
        val summary = _pendingPostFocus.value ?: return
        viewModelScope.launch {
            val alreadyEnded = container.database.focusSessionDao().getById(summary.sessionId)?.endedAtMillis != null
            if (!alreadyEnded) {
                container.focusRepository.finishSession(summary.sessionId, summary.completed, actualMinutes)
                if (summary.completed) {
                    container.growRepository.rewardFocusComplete()
                    _message.value = "+25 energy for Pip"
                } else {
                    container.growRepository.rewardPartialDay()
                }
            }
            container.focusRepository.wrapUpSession(summary.sessionId, endTag, actualMinutes)
            summary.taskId?.let { container.planRepository.setTaskActualMinutes(it, actualMinutes) }
            if (markDone && summary.taskId != null) completeTaskSuspended(summary.taskId)
            endTag?.let { showCbtForTag(it) }
            clearPendingPostFocus()
            container.focusRepository.refreshBlockedPackages()
            scheduledShieldActive.value = FocusBlockService.scheduledShieldActive
            ReminderScheduler.rescheduleAll(appContext)
        }
    }

    fun dismissPostFocus() {
        viewModelScope.launch { dismissPostFocusSuspended() }
    }

    private suspend fun dismissPostFocusSuspended() {
        val summary = _pendingPostFocus.value
        if (summary != null) {
            val session = container.database.focusSessionDao().getById(summary.sessionId)
            if (session?.endedAtMillis == null) {
                container.focusRepository.finishSession(summary.sessionId, summary.completed, summary.actualMinutes)
                if (summary.completed) container.growRepository.rewardFocusComplete()
                else container.growRepository.rewardPartialDay()
            }
        }
        clearPendingPostFocus()
        container.focusRepository.refreshBlockedPackages()
        scheduledShieldActive.value = FocusBlockService.scheduledShieldActive
        ReminderScheduler.rescheduleAll(appContext)
    }

    fun postFocusShrinkNext() {
        val summary = _pendingPostFocus.value
        viewModelScope.launch {
            dismissPostFocusSuspended()
            val taskId = summary?.taskId ?: return@launch
            val task = container.planRepository.getTask(taskId) ?: return@launch
            container.planRepository.updateTask(task.copy(durationMinutes = 10))
            scheduleTaskAtNextSlotSuspended(taskId)
        }
    }

    fun postFocusParkSomeday() {
        val summary = _pendingPostFocus.value
        viewModelScope.launch {
            summary?.taskId?.let { moveInboxSuspended(it, InboxState.SOMEDAY) }
            dismissPostFocusSuspended()
        }
    }

    fun postFocusStartTen() {
        val taskId = _pendingPostFocus.value?.taskId
        viewModelScope.launch {
            dismissPostFocusSuspended()
            startFocusSuspended(10, 3, taskId)
        }
    }

    fun startResetTimer() {
        resetTimerJob?.cancel()
        resetTimerJob = viewModelScope.launch {
            _resetSeconds.value = 120
            while (_resetSeconds.value > 0) {
                delay(1000)
                _resetSeconds.value -= 1
            }
        }
    }

    fun clearResetTimer() {
        resetTimerJob?.cancel()
        _resetSeconds.value = 0
    }

    private fun showCbtForTag(tag: FocusEndTag) {
        val moment = when (tag) {
            FocusEndTag.STUCK -> CbtMomentTag.OVERWHELMED
            FocusEndTag.AVOIDING -> CbtMomentTag.AVOIDING
            FocusEndTag.TIRED -> CbtMomentTag.DIDNT_START
            FocusEndTag.INTERRUPTED -> CbtMomentTag.SCATTERED
            FocusEndTag.NONE -> CbtMomentTag.GENERAL
        }
        viewModelScope.launch {
            val card = container.database.cbtCardDao().observeForMoment(moment).first().firstOrNull()
            card?.let { _selectedCbt.value = it }
        }
    }

    fun scheduleTaskAtNextSlot(taskId: Long) {
        viewModelScope.launch { scheduleTaskAtNextSlotSuspended(taskId) }
    }

    private suspend fun scheduleTaskAtNextSlotSuspended(taskId: Long) {
        val task = container.planRepository.getTask(taskId) ?: return
        val window = holeWindow()
        val start = container.planRepository.scheduleTaskAtNextSlot(
            taskId,
            task.durationMinutes,
            nowMillis = window.now,
            dayStartMillis = window.dayStart,
            shutdownMillis = window.shutdown
        )
        if (start == null) {
            _message.value = "No gap fits before shutdown — added to Today."
        }
        ReminderScheduler.rescheduleAll(appContext)
    }

    fun syncDeviceCalendar() {
        viewModelScope.launch { syncDeviceCalendarSuspended() }
    }

    private data class HoleWindow(val now: Long, val dayStart: Long, val shutdown: Long)

    private suspend fun holeWindow(): HoleWindow {
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val shutdownHour = planningSettings.value.shutdownHour.coerceIn(17, 23)
        val shutdown = today.atTime(shutdownHour, 0).atZone(zone).toInstant().toEpochMilli()
        syncDeviceCalendarSuspended(dayStart, dayEnd)
        return HoleWindow(now, dayStart, shutdown)
    }

    private suspend fun applyBreakdownFromResult(parentTitle: String, result: AiBreakdownResult): Long {
        val blocked = container.database.blockRuleDao().observeEnabled().first().map { it.packageName }
        val clamped = BreakdownClamp.clampResult(result, blocked)
        val (parent, children) = container.aiRepository.breakdownParentAndChildren(parentTitle, clamped)
        val window = holeWindow()
        val parentId = container.planRepository.insertParentWithChildren(
            parent,
            children,
            window.now,
            window.dayStart,
            window.shutdown
        )
        persistUndo(ChatUndoOp.CreatedTasks(listOf(parentId)), ChatUndoSource.APPLY)
        ReminderScheduler.rescheduleAll(appContext)
        return parentId
    }

    private suspend fun persistUndo(op: ChatUndoOp, source: ChatUndoSource) {
        val now = System.currentTimeMillis()
        val id = container.chatUndoDao.insert(
            com.anchor.adhd.data.model.ChatUndoOpEntity(
                source = source.name.lowercase(),
                kind = ChatUndoCodec.kind(op),
                payload = ChatUndoCodec.payload(op),
                createdAtMillis = now
            )
        )
        chatUndoManager.push(op, now, source, id)
        container.chatUndoDao.pruneBefore(now - ChatUndoManager.TWELVE_HOURS)
        _undoCount.value = chatUndoManager.snapshot().size
    }

    private suspend fun restoreUndoOps() {
        val now = System.currentTimeMillis()
        val since = now - ChatUndoManager.TWELVE_HOURS
        container.chatUndoDao.pruneBefore(since)
        val rows = container.chatUndoDao.listSince(since)
        val entries = rows.mapNotNull { row ->
            val op = ChatUndoCodec.decode(row.kind, row.payload) ?: return@mapNotNull null
            val source = runCatching { ChatUndoSource.valueOf(row.source.uppercase()) }
                .getOrDefault(ChatUndoSource.CHAT)
            ChatUndoEntry(dbId = row.id, op = op, source = source, atMillis = row.createdAtMillis)
        }
        chatUndoManager.restore(entries, now)
        _undoCount.value = chatUndoManager.snapshot().size
    }

    private fun collapseAfterCommit() {
        _collapseChatSheet.tryEmit(Unit)
        _chatSheetPanic.value = false
        container.gpuRam.onAiSurfaceClosed()
    }

    private suspend fun currentDaySnapshot(): com.anchor.adhd.domain.DaySnapshot {
        val window = holeWindow()
        val occupancy = SchedulingSlots.occupancyIntervals(
            container.database.taskDao().getScheduledBlocksForDay(window.dayStart, window.shutdown),
            container.database.calendarEventDao().getOverlapping(window.dayStart, window.shutdown)
        )
        val blocked = container.database.blockRuleDao().observeEnabled().first().map { it.packageName }
        return buildDaySnapshot(
            scheduled = scheduled.value,
            inboxToday = inboxTodaySorted.value,
            energy = energy.value,
            routines = routines.value,
            checkInTags = checkInTags.value,
            replanCount = replanWithTasks.value.size,
            dueSoon = assignmentsDueSoon.value,
            occupancy = occupancy,
            nowMillis = window.now,
            dayStartMillis = window.dayStart,
            shutdownMillis = window.shutdown,
            blockedPackages = blocked,
        )
    }

    private suspend fun handlePickOne(intent: com.anchor.adhd.domain.ChatIntent.PickOne, fromModel: Boolean = false) {
        val snap = currentDaySnapshot()
        val overwhelmed = CheckInTag.OVERWHELMED in checkInTags.value
        val picked = pickOneTask(
            snap,
            intent.minutes,
            inboxTodaySorted.value,
            scheduled.value,
            energy.value,
            overwhelmed,
            System.currentTimeMillis()
        )
        val task = picked.task
        if (task != null) {
            rememberChatTask(task.title)
            val mins = intent.minutes?.let { " You've got $it minutes." } ?: ""
            reply("Pick one: \"${task.title}\".$mins")
            return
        }
        if (!fromModel && modelDownloaded.value) {
            val turn = container.aiRepository.planChatTurn(
                intent.minutes?.let { "I have $it minutes" } ?: "pick one",
                snap.compactPrompt()
            )
            if (turn != null && turn.first != "pick_one") {
                handleChatIntent(phaseBRoute(turn.first, turn.second) ?: com.anchor.adhd.domain.ChatIntent.Chat(turn.second))
                return
            }
        }
        reply("Nothing that fits right now — capture something or start a 10-minute focus.")
    }

    private suspend fun syncDeviceCalendarSuspended(fromMillis: Long? = null, toMillis: Long? = null) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val from = fromMillis ?: today.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = toMillis ?: today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val events = container.deviceCalendarSync.queryWindow(from, to)
        container.planRepository.replaceDeviceCalendarWindow(from, to, events)
    }

    fun startNowTenMinute() {
        val task = nowTask.value.task
        startShortFocus(task?.id)
    }

    fun completeNowTask() {
        nowTask.value.task?.id?.let { completeTask(it) }
    }

    fun endFocus(completed: Boolean) = requestEndFocus(completed)

    fun pauseCompanion(days: Int) {
        viewModelScope.launch { container.growRepository.pauseCompanion(days) }
    }

    fun resumeCompanion() {
        viewModelScope.launch { container.growRepository.resumeCompanion() }
    }

    fun createHabit(
        name: String,
        scheduleType: HabitScheduleType,
        scheduleDays: String,
        targetPerWeek: Int,
        gracePerWeek: Int,
        autoSource: com.anchor.adhd.data.model.HabitAutoSource = com.anchor.adhd.data.model.HabitAutoSource.NONE,
        autoThreshold: Int = 0
    ) {
        viewModelScope.launch {
            container.habitRepository.create(
                name = name,
                scheduleType = scheduleType,
                scheduleDays = scheduleDays,
                targetPerWeek = targetPerWeek,
                gracePerWeek = gracePerWeek,
                autoSource = autoSource,
                autoThreshold = autoThreshold
            ).onFailure { e ->
                _message.value = when (e) {
                    is HabitLimitException -> "Max ${e.limit} habits — archive one first"
                    else -> e.message ?: "Could not create habit"
                }
            }.onSuccess {
                _message.value = "Habit added"
            }
        }
    }

    fun updateHabit(habit: com.anchor.adhd.data.model.HabitEntity) {
        viewModelScope.launch {
            container.habitRepository.update(habit)
            _message.value = "Habit updated"
        }
    }

    fun archiveHabit(id: Long) {
        viewModelScope.launch {
            container.habitRepository.archive(id)
            _message.value = "Habit archived"
        }
    }

    fun toggleHabitCompletion(habitId: Long) {
        viewModelScope.launch { container.habitRepository.toggleCompletion(habitId) }
    }

    fun showCbt(card: CbtCardEntity) { _selectedCbt.value = card }
    fun dismissCbt() { _selectedCbt.value = null }

    fun toggleBlockRule(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            container.focusRepository.toggleBlockRule(id, enabled)
            scheduledShieldActive.value = FocusBlockService.scheduledShieldActive
        }
    }

    fun addBlockPackage(packageName: String) {
        viewModelScope.launch {
            container.focusRepository.addBlockRule(
                BlockRuleEntity(packageName = packageName.trim(), ruleType = com.anchor.adhd.data.model.BlockRuleType.SESSION)
            )
        }
    }

    fun refreshModelStatus() {
        modelDownloaded.value = container.modelDownloadManager.isDownloaded()
        viewModelScope.launch {
            _activeModelEntryId.value = container.preferences.activeModelEntryId()
        }
    }

    fun modelCatalogEntries(): List<ModelCatalogEntry> = ModelCatalog.entries

    fun isModelEntryDownloaded(entry: ModelCatalogEntry): Boolean =
        container.modelDownloadManager.isDownloaded(entry)

    fun freeDiskBytes(): Long = container.modelDownloadManager.freeDiskBytes()

    fun downloadModelEntry(entryId: String) {
        val entry = ModelCatalog.find(entryId) ?: return
        if (container.modelDownloadManager.isDownloading) return
        viewModelScope.launch {
            try {
                container.aiEngine.ensureUnloaded()
                container.modelDownloadManager.downloadEntry(entry)
                refreshModelStatus()
                _message.value = "${entry.label} downloaded"
            } catch (e: Exception) {
                _aiError.value = e.message
            }
        }
    }

    fun setActiveModelEntry(entryId: String) {
        viewModelScope.launch {
            container.preferences.setActiveModelEntry(entryId)
            _activeModelEntryId.value = container.preferences.activeModelEntryId()
            _message.value = "Active model updated"
        }
    }

    fun setGpuOffloadEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.preferences.setGpuOffloadEnabled(enabled)
        }
    }

    fun setGpuLayerCount(layers: Int) {
        viewModelScope.launch {
            container.preferences.setGpuLayerCount(layers)
        }
    }

    fun runAiBenchmark(entryId: String? = null) {
        if (_benchmarkRunning.value || container.modelDownloadManager.isDownloading) return
        viewModelScope.launch {
            _benchmarkRunning.value = true
            _benchmarkResult.value = null
            try {
                val entry = entryId?.let { ModelCatalog.find(it) }
                    ?: ModelCatalog.find(_activeModelEntryId.value)
                    ?: ModelCatalog.entries.first()
                val path = container.appContext.filesDir
                    .resolve("models/${entry.filename}")
                    .absolutePath
                if (!java.io.File(path).exists()) {
                    _aiError.value = "Download ${entry.label} first"
                    return@launch
                }
                container.aiEngine.ensureUnloaded()
                val result = container.aiBenchmarkRunner.run(path, entry.label)
                _benchmarkResult.value = result
                if (!result.success) {
                    _aiError.value = result.error ?: "Benchmark failed"
                }
            } catch (e: Exception) {
                _aiError.value = e.message
            } finally {
                _benchmarkRunning.value = false
            }
        }
    }

    fun downloadModel() = downloadModelEntry("q8")

    fun downloadQ4Fallback() = downloadModelEntry("q8")

    fun exportBackup() {
        viewModelScope.launch {
            val file = container.backupManager.exportToCache()
            _message.value = "Backup saved: ${file.name}"
        }
    }

    fun resetAllAppData(deleteModel: Boolean) {
        activeAiJobs.forEach { it.cancel() }
        activeAiJobs.clear()
        aiLoadingCount.set(0)
        _aiLoading.value = false
        resetTimerJob?.cancel()
        _resetSeconds.value = 0
        viewModelScope.launch {
            container.appResetManager.resetAll(deleteModel)
            modelDownloaded.value = container.modelDownloadManager.isDownloaded()
            clearPendingPostFocus()
            _aiPreview.value = null
            _brainDumpPreview.value = null
            _triagePreview.value = null
            _replanAiPreview.value = null
            _selectedCbt.value = null
            _checkInTags.value = emptySet()
            scheduledShieldActive.value = FocusBlockService.scheduledShieldActive
            _message.value = "App reset to defaults"
        }
    }

    fun generateIfThenForRoutine(routineName: String, cue: String, onResult: (String?) -> Unit) {
        if (routineName.isBlank()) return
        launchAi(
            block = { container.aiRepository.generateIfThen(routineName, cue) },
            onSuccess = { onResult(it.if_then) }
        )
    }

    fun importGoogleCalendar(onNeedSignIn: () -> Unit) {
        viewModelScope.launch {
            try {
                val account = com.anchor.adhd.calendar.GoogleSignInBridge.getLastSignedInAccount(appContext)
                if (account == null) {
                    onNeedSignIn()
                    return@launch
                }
                val events = container.calendarImporter.importWeek()
                container.planRepository.importCalendarEvents(events)
                _message.value = "Imported ${events.size} calendar events"
            } catch (e: Exception) {
                _aiError.value = e.message ?: "Calendar import failed"
            }
        }
    }

    fun openAccessibilitySettings() = FocusBlockService.openSettings(appContext)

    fun updateReminderSettings(settings: com.anchor.adhd.data.prefs.ReminderSettings) {
        viewModelScope.launch {
            container.preferences.updateReminderSettings(settings)
            ReminderScheduler.rescheduleAll(appContext)
        }
    }

    val reminderSettings = container.preferences.reminderSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.anchor.adhd.data.prefs.ReminderSettings())

    fun routineSteps(routineId: Long): StateFlow<List<RoutineStepEntity>> =
        routineStepsFlows.getOrPut(routineId) {
            container.planRepository.observeRoutineSteps(routineId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        }

    fun openCalendarEventDetail(event: CalendarEventEntity) {
        _selectedCalendarEvent.value = event
    }

    fun dismissCalendarEventDetail() {
        _selectedCalendarEvent.value = null
    }

    fun editCalendarEventInDeviceCalendar(event: CalendarEventEntity) {
        val eventId = com.anchor.adhd.calendar.DeviceCalendarOccupancy.eventId(event.id)
        if (eventId == null) {
            _message.value = "This imported event cannot be opened in the device calendar"
            return
        }
        runCatching {
            val intent = android.content.Intent(android.content.Intent.ACTION_EDIT).apply {
                data = android.content.ContentUris.withAppendedId(
                    android.provider.CalendarContract.Events.CONTENT_URI,
                    eventId
                )
                putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.startMillis)
                putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, event.endMillis)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
            dismissCalendarEventDetail()
        }.onFailure {
            _message.value = "No calendar app is available to edit this event"
        }
    }

    fun copyCalendarEventToAnchor(event: CalendarEventEntity) {
        viewModelScope.launch {
            val duration = ((event.endMillis - event.startMillis) / 60_000L).toInt().coerceIn(5, 240)
            val id = container.planRepository.captureToInbox(listOf(event.title)).firstOrNull()
            if (id == null) {
                _message.value = "Could not copy calendar event"
                return@launch
            }
            container.planRepository.scheduleTask(id, event.startMillis, duration)
            ReminderScheduler.rescheduleAll(appContext)
            _selectedCalendarEvent.value = null
            _message.value = "Copied as an editable Anchor task"
        }
    }

    fun openTaskDetail(task: TaskEntity) {
        _selectedTaskId.value = task.id
    }

    fun dismissTaskDetail() {
        _selectedTaskId.value = null
    }

    fun saveTask(task: TaskEntity) {
        viewModelScope.launch {
            container.planRepository.updateTask(task)
            ReminderScheduler.rescheduleAll(appContext)
            _selectedTaskId.value = null
            _message.value = "Task saved"
        }
    }

    fun deleteTask(taskId: Long) {
        viewModelScope.launch {
            container.planRepository.deleteTask(taskId)
            ReminderScheduler.rescheduleAll(appContext)
            _selectedTaskId.value = null
            _message.value = "Task deleted"
        }
    }

    fun onAppResumed() {
        openMorningRitualIfNeeded()
        syncSamsungHealth(notify = false)
        viewModelScope.launch {
            container.preferences.checkDailyFreshStart()
        }
    }

    fun openMorningRitualIfNeeded() {
        viewModelScope.launch {
            if (LocalTime.now().hour >= 12) return@launch
            val today = LocalDate.now(ZoneId.systemDefault()).toString()
            if (_morningRitualHandledDay.value == today) return@launch
            val settings = container.preferences.getPlanningSettings()
            if (settings.lastMorningRitualDay != today) {
                _showMorningRitual.value = true
            } else {
                _morningRitualHandledDay.value = today
            }
        }
    }

    fun openMorningRitual() {
        _showMorningRitual.value = true
    }

    fun completeMorningRitual() {
        val today = LocalDate.now(ZoneId.systemDefault()).toString()
        _morningRitualHandledDay.value = today
        _showMorningRitual.value = false
        viewModelScope.launch { container.preferences.markMorningRitualComplete(today) }
    }

    fun dismissMorningRitual() {
        val today = LocalDate.now(ZoneId.systemDefault()).toString()
        _morningRitualHandledDay.value = today
        _showMorningRitual.value = false
        viewModelScope.launch { container.preferences.markMorningRitualComplete(today) }
    }

    fun applyCheckInFromIntent(energy: EnergyLevel?) {
        energy?.let { setEnergy(it) }
    }

    fun openShutdown() {
        viewModelScope.launch {
            val today = LocalDate.now(ZoneId.systemDefault()).toString()
            if (planningSettings.value.lastShutdownDay == today) {
                _message.value = "Shutdown already completed today"
                return@launch
            }
            _showShutdown.value = true
        }
    }

    fun dismissShutdown() {
        _showShutdown.value = false
    }

    fun leaveShutdown() {
        _showShutdown.value = false
    }

    fun completeShutdown() {
        viewModelScope.launch {
            container.preferences.markShutdownComplete()
            _showShutdown.value = false
            _message.value = "Shutdown complete — rest well"
        }
    }

    fun shutdownMarkDone(task: TaskEntity) {
        viewModelScope.launch {
            container.planRepository.completeTask(task.id)
            ReminderScheduler.rescheduleAll(appContext)
        }
    }

    fun shutdownDeferTomorrow(task: TaskEntity) {
        viewModelScope.launch {
            container.planRepository.deferTaskToTomorrow(task.id)
            ReminderScheduler.rescheduleAll(appContext)
        }
    }

    fun shutdownMoveSomeday(task: TaskEntity) {
        viewModelScope.launch {
            container.planRepository.moveInbox(task.id, InboxState.SOMEDAY)
            ReminderScheduler.rescheduleAll(appContext)
        }
    }

    fun deferTaskToTomorrow(taskId: Long) {
        viewModelScope.launch {
            container.planRepository.deferTaskToTomorrow(taskId)
            ReminderScheduler.rescheduleAll(appContext)
        }
    }

    fun setDailyCapacity(minutes: Int) {
        viewModelScope.launch { container.preferences.setDailyCapacityMinutes(minutes) }
    }

    fun setTodayOnlyMode(enabled: Boolean) {
        viewModelScope.launch { container.preferences.setTodayOnlyMode(enabled) }
    }

    fun setShutdownHour(hour: Int) {
        viewModelScope.launch {
            container.preferences.setShutdownHour(hour)
            ReminderScheduler.rescheduleAll(appContext)
        }
    }

    fun setShutdownNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.preferences.setShutdownNotificationEnabled(enabled)
            ReminderScheduler.rescheduleAll(appContext)
        }
    }

    fun setBlockListMode(mode: com.anchor.adhd.data.model.BlockListMode) {
        viewModelScope.launch {
            container.preferences.setBlockListMode(mode)
            container.focusRepository.refreshBlockedPackages()
            scheduledShieldActive.value = FocusBlockService.scheduledShieldActive
        }
    }

    fun notifyLockedTaskDrag() {
        _message.value = "Can't reschedule while focus timer is running on this task"
    }

    fun connectSamsungHealth(activity: android.app.Activity) {
        viewModelScope.launch {
            val bridge = container.samsungHealth
            if (!bridge.isAvailable()) {
                _message.value = "Samsung Health is not available. Open or update the Samsung Health app, then try again."
                return@launch
            }
            val granted = bridge.requestPermissions(activity)
            samsungHealthConnected.value = granted
            if (!granted) {
                _message.value = "Allow steps and exercise in the Samsung Health permission sheet"
                return@launch
            }
            syncSamsungHealth()
        }
    }

    fun syncSamsungHealth(notify: Boolean = true) {
        viewModelScope.launch {
            val bridge = container.samsungHealth
            if (!bridge.isAvailable()) {
                samsungHealthConnected.value = false
                if (notify) _message.value = "Samsung Health is not available on this device"
                return@launch
            }
            if (!bridge.hasPermissions()) {
                samsungHealthConnected.value = false
                if (notify) {
                    _message.value = "Connect Samsung Health in Settings to auto-complete step and exercise habits"
                }
                return@launch
            }
            samsungHealthConnected.value = true
            val snapshot = bridge.readTodaySnapshot()
            if (snapshot == null) {
                if (notify) _message.value = "Could not read Samsung Health data"
                return@launch
            }
            container.habitRepository.syncAutoCompletions(snapshot.steps, snapshot.exerciseMinutes)
            if (notify) {
                _message.value = "Samsung Health: ${snapshot.steps} steps, ${snapshot.exerciseMinutes} min exercise today"
            }
        }
    }

    fun launchFunLink(link: FunLinkEntity, onLaunched: () -> Unit) {
        viewModelScope.launch {
            val consumed = container.preferences.consumeFunCredit()
            if (consumed) {
                onLaunched()
            } else {
                _message.value = "Nice! Complete a focus session to unlock another game 🌊"
            }
        }
    }

    fun setFunStrictMode(enabled: Boolean) {
        viewModelScope.launch {
            container.preferences.setFunStrictMode(enabled)
        }
    }

    fun saveFunLink(link: FunLinkEntity) {
        viewModelScope.launch {
            if (link.id == 0L) {
                container.funLinkDao.insert(link)
            } else {
                container.funLinkDao.update(link)
            }
        }
    }

    fun deleteFunLink(id: Long) {
        viewModelScope.launch {
            container.funLinkDao.deleteById(id)
        }
    }

    fun restoreDefaultFunLinks() {
        viewModelScope.launch {
            container.funLinkDao.insertAll(DefaultFunLinks.DEFAULTS)
            _message.value = "Default fun links restored"
        }
    }

    suspend fun tomorrowPreviewText(): String? {
        val task = container.planRepository.observeTomorrowFirstBlock() ?: return null
        return task.title
    }

    override fun onCleared() {
        airlockInferenceJob?.cancel()
        LlamaBridge.abortGenerate()
        container.gpuRam.unloadNow()
        super.onCleared()
    }
}

class AnchorViewModelFactory(
    private val container: AnchorContainer,
    private val initialInboxText: String?
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AnchorViewModel(container, initialInboxText) as T
    }
}
