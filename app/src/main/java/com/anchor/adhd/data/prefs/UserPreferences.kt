package com.anchor.adhd.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.anchor.adhd.ai.ModelCatalog
import com.anchor.adhd.ai.ModelDownloadManager
import com.anchor.adhd.ai.LocalAiEngine
import com.anchor.adhd.data.model.BreakdownGranularity
import com.anchor.adhd.data.model.BlockListMode
import com.anchor.adhd.data.model.EnergyLevel
import com.anchor.adhd.data.model.PostFocusSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "anchor_prefs")

class UserPreferences(private val context: Context) {
    private val modelDownloaded = booleanPreferencesKey("model_downloaded")
    private val modelPath = stringPreferencesKey("model_path")
    private val energyToday = stringPreferencesKey("energy_today")
    private val energyDay = stringPreferencesKey("energy_day")
    private val focusWorkMinutes = intPreferencesKey("focus_work_minutes")
    private val focusBreakMinutes = intPreferencesKey("focus_break_minutes")

    private val remindersEnabled = booleanPreferencesKey("reminders_enabled")
    private val blockReminders = booleanPreferencesKey("block_reminders")
    private val assignmentReminders = booleanPreferencesKey("assignment_reminders")
    private val morningReplanReminder = booleanPreferencesKey("morning_replan_reminder")
    private val morningReplanHour = intPreferencesKey("morning_replan_hour")
    private val quietHoursStart = intPreferencesKey("quiet_hours_start")
    private val quietHoursEnd = intPreferencesKey("quiet_hours_end")
    private val timer5MinWarning = booleanPreferencesKey("timer_5min_warning")
    private val timerHardStop = booleanPreferencesKey("timer_hard_stop")
    private val seedComplete = booleanPreferencesKey("seed_complete")
    private val strategiesSeedVersion = intPreferencesKey("strategies_seed_version")
    private val dailyStrategyDay = stringPreferencesKey("daily_strategy_day")
    private val dailyStrategyCardId = stringPreferencesKey("daily_strategy_card_id")
    private val lastStrategyCardId = stringPreferencesKey("last_strategy_card_id")
    private val pendingPostFocusJson = stringPreferencesKey("pending_post_focus_json")
    private val lastMorningRitualDay = stringPreferencesKey("last_morning_ritual_day")
    private val lastShutdownDay = stringPreferencesKey("last_shutdown_day")
    private val dailyCapacityMinutes = intPreferencesKey("daily_capacity_minutes")
    private val todayOnlyMode = booleanPreferencesKey("today_only_mode")
    private val shutdownHour = intPreferencesKey("shutdown_hour")
    private val shutdownNotificationEnabled = booleanPreferencesKey("shutdown_notification_enabled")
    private val breakdownGranularity = stringPreferencesKey("breakdown_granularity")
    private val blockListMode = stringPreferencesKey("block_list_mode")
    private val companionCosmeticsJson = stringPreferencesKey("companion_cosmetics_json")
    private val activeModelFilename = stringPreferencesKey("active_model_filename")
    private val aiGpuEnabled = booleanPreferencesKey("ai_gpu_enabled")
    private val aiGpuLayers = intPreferencesKey("ai_gpu_layers")
    private val desktopAiProfileVersion = intPreferencesKey("desktop_ai_profile_version")
    private val lastFunLinkDate = stringPreferencesKey("last_fun_link_date")
    private val funCredits = intPreferencesKey("fun_credits")
    private val funStrictMode = booleanPreferencesKey("fun_links_strict_mode")

    private val json = Json { ignoreUnknownKeys = true }

    val planningSettings: Flow<PlanningSettings> = context.dataStore.data.map { prefs ->
        PlanningSettings(
            dailyCapacityMinutes = prefs[dailyCapacityMinutes] ?: 360,
            todayOnlyMode = prefs[todayOnlyMode] ?: false,
            shutdownHour = prefs[shutdownHour] ?: 21,
            shutdownNotificationEnabled = prefs[shutdownNotificationEnabled] ?: false,
            lastMorningRitualDay = prefs[lastMorningRitualDay],
            lastShutdownDay = prefs[lastShutdownDay]
        )
    }

    val breakdownGranularityFlow: Flow<BreakdownGranularity> = context.dataStore.data.map { prefs ->
        prefs[breakdownGranularity]?.let { runCatching { BreakdownGranularity.valueOf(it) }.getOrNull() }
            ?: BreakdownGranularity.NORMAL
    }

    val blockListModeFlow: Flow<BlockListMode> = context.dataStore.data.map { prefs ->
        prefs[blockListMode]?.let { runCatching { BlockListMode.valueOf(it) }.getOrNull() }
            ?: BlockListMode.BLOCKLIST
    }

    suspend fun getPlanningSettings(): PlanningSettings = planningSettings.first()

    suspend fun setDailyCapacityMinutes(minutes: Int) {
        context.dataStore.edit { it[dailyCapacityMinutes] = minutes.coerceIn(120, 720) }
    }

    suspend fun setTodayOnlyMode(enabled: Boolean) {
        context.dataStore.edit { it[todayOnlyMode] = enabled }
    }

    suspend fun setShutdownHour(hour: Int) {
        context.dataStore.edit { it[shutdownHour] = hour.coerceIn(17, 23) }
    }

    suspend fun setShutdownNotificationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[shutdownNotificationEnabled] = enabled }
    }

    suspend fun markMorningRitualComplete(day: String = java.time.LocalDate.now().toString()) {
        context.dataStore.edit { it[lastMorningRitualDay] = day }
    }

    suspend fun markShutdownComplete(day: String = java.time.LocalDate.now().toString()) {
        context.dataStore.edit { it[lastShutdownDay] = day }
    }

    suspend fun setBreakdownGranularity(granularity: BreakdownGranularity) {
        context.dataStore.edit { it[breakdownGranularity] = granularity.name }
    }

    suspend fun setBlockListMode(mode: BlockListMode) {
        context.dataStore.edit { it[blockListMode] = mode.name }
    }

    suspend fun getCompanionCosmeticsJson(): String? =
        context.dataStore.data.first()[companionCosmeticsJson]

    suspend fun setCompanionCosmeticsJson(value: String?) {
        context.dataStore.edit { prefs ->
            if (value == null) prefs.remove(companionCosmeticsJson) else prefs[companionCosmeticsJson] = value
        }
    }

    val funCreditsFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[funCredits] ?: 0
    }

    val funStrictModeFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[funStrictMode] ?: false
    }

    suspend fun addFunCredit(amount: Int = 1) {
        context.dataStore.edit { prefs ->
            val current = prefs[funCredits] ?: 0
            prefs[funCredits] = (current + amount).coerceAtLeast(0)
        }
    }

    suspend fun consumeFunCredit(): Boolean {
        var consumed = false
        context.dataStore.edit { prefs ->
            val current = prefs[funCredits] ?: 0
            if (current > 0) {
                prefs[funCredits] = current - 1
                consumed = true
            }
        }
        return consumed
    }

    suspend fun setFunStrictMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[funStrictMode] = enabled
        }
    }

    suspend fun checkDailyFreshStart(todayStr: String = java.time.LocalDate.now().toString()) {
        context.dataStore.edit { prefs ->
            val lastDate = prefs[lastFunLinkDate]
            if (lastDate != todayStr) {
                prefs[lastFunLinkDate] = todayStr
                val isStrict = prefs[funStrictMode] ?: false
                if (!isStrict) {
                    val current = prefs[funCredits] ?: 0
                    if (current < 1) {
                        prefs[funCredits] = 1
                    }
                }
            }
        }
    }

    val energyTodayFlow: Flow<EnergyLevel> = context.dataStore.data.map { prefs ->
        val today = java.time.LocalDate.now().toString()
        val storedDay = prefs[energyDay]
        if (storedDay != null && storedDay != today) {
            EnergyLevel.OK
        } else {
            prefs[energyToday]?.let { EnergyLevel.valueOf(it) } ?: EnergyLevel.OK
        }
    }

    val reminderSettings: Flow<ReminderSettings> = context.dataStore.data.map { prefs ->
        ReminderSettings(
            enabled = prefs[remindersEnabled] ?: true,
            blockReminders = prefs[blockReminders] ?: true,
            assignmentReminders = prefs[assignmentReminders] ?: true,
            morningReplan = prefs[morningReplanReminder] ?: true,
            morningReplanHour = prefs[morningReplanHour] ?: 8,
            quietHoursStart = prefs[quietHoursStart] ?: 22,
            quietHoursEnd = prefs[quietHoursEnd] ?: 7,
            timer5MinWarning = prefs[timer5MinWarning] ?: true,
            timerHardStop = prefs[timerHardStop] ?: false
        )
    }

    suspend fun getReminderSettings(): ReminderSettings = reminderSettings.first()

    suspend fun setEnergy(level: EnergyLevel) {
        val today = java.time.LocalDate.now().toString()
        context.dataStore.edit {
            it[energyToday] = level.name
            it[energyDay] = today
        }
    }

    suspend fun updateReminderSettings(update: ReminderSettings) {
        context.dataStore.edit { prefs ->
            prefs[remindersEnabled] = update.enabled
            prefs[blockReminders] = update.blockReminders
            prefs[assignmentReminders] = update.assignmentReminders
            prefs[morningReplanReminder] = update.morningReplan
            prefs[morningReplanHour] = update.morningReplanHour
            prefs[quietHoursStart] = update.quietHoursStart
            prefs[quietHoursEnd] = update.quietHoursEnd
            prefs[timer5MinWarning] = update.timer5MinWarning
            prefs[timerHardStop] = update.timerHardStop
        }
    }

    fun isModelDownloaded(): Boolean =
        ModelCatalog.entries.any { entry -> modelFile(entry.filename).exists() }

    suspend fun getActiveModelPath(): String {
        applyDesktopModelProfile()
        val prefs = context.dataStore.data.first()
        val stored = prefs[modelPath]
        if (!stored.isNullOrBlank() && ModelCatalog.findByFilename(java.io.File(stored).name) != null && java.io.File(stored).exists()) return stored
        val preferred = prefs[activeModelFilename] ?: ModelDownloadManager.Q4_FILENAME
        val preferredFile = modelFile(preferred)
        if (preferredFile.exists()) return preferredFile.absolutePath
        return ModelCatalog.entries
            .map { modelFile(it.filename) }
            .firstOrNull { it.exists() }
            ?.absolutePath
            ?: modelFile(ModelDownloadManager.Q4_FILENAME).absolutePath
    }

    suspend fun getModelPath(): String = getActiveModelPath()

    private fun modelFile(filename: String): java.io.File =
        context.filesDir.resolve("models/$filename")

    val aiGpuSettings: Flow<AiGpuSettings> = context.dataStore.data.map { prefs ->
        AiGpuSettings(
            enabled = prefs[aiGpuEnabled] ?: false,
            layers = prefs[aiGpuLayers] ?: LocalAiEngine.DEFAULT_GPU_LAYERS
        )
    }

    suspend fun isGpuOffloadEnabled(): Boolean = false

    suspend fun getGpuLayerCount(): Int = com.anchor.adhd.ai.DesktopInferenceProfile.GPU_LAYERS

    suspend fun applyDesktopModelProfile() {
        context.dataStore.edit { prefs ->
            if ((prefs[desktopAiProfileVersion] ?: 0) < 1) {
                prefs[activeModelFilename] = ModelDownloadManager.MODEL_FILENAME
                prefs[modelPath] = modelFile(ModelDownloadManager.MODEL_FILENAME).absolutePath
                prefs[aiGpuEnabled] = false
                prefs[aiGpuLayers] = 0
                prefs[desktopAiProfileVersion] = 1
            }
        }
    }

    suspend fun setGpuOffloadEnabled(enabled: Boolean) {
        context.dataStore.edit { it[aiGpuEnabled] = enabled }
    }

    suspend fun setGpuLayerCount(layers: Int) {
        context.dataStore.edit { it[aiGpuLayers] = layers.coerceIn(0, 99) }
    }

    suspend fun setActiveModel(path: String, filename: String) {
        context.dataStore.edit {
            it[modelDownloaded] = true
            it[modelPath] = path
            it[activeModelFilename] = filename
        }
    }

    suspend fun setActiveModelEntry(entryId: String) {
        val entry = ModelCatalog.find(entryId) ?: return
        val file = modelFile(entry.filename)
        if (file.exists()) {
            setActiveModel(file.absolutePath, entry.filename)
        }
    }

    suspend fun setModelDownloaded(path: String) {
        val filename = java.io.File(path).name
        setActiveModel(path, filename)
    }

    suspend fun activeModelEntryId(): String {
        val filename = context.dataStore.data.first()[activeModelFilename]
            ?: ModelDownloadManager.Q4_FILENAME
        return ModelCatalog.findByFilename(filename)?.id ?: "q8"
    }

    val focusDurations: Flow<Pair<Int, Int>> = context.dataStore.data.map { prefs ->
        Pair(prefs[focusWorkMinutes] ?: 20, prefs[focusBreakMinutes] ?: 5)
    }

    suspend fun setFocusDurations(workMinutes: Int, breakMinutes: Int) {
        context.dataStore.edit {
            it[focusWorkMinutes] = workMinutes.coerceIn(5, 90)
            it[focusBreakMinutes] = breakMinutes.coerceIn(1, 30)
        }
    }

    suspend fun getTodayStrategyCardId(): String? {
        val prefs = context.dataStore.data.first()
        val today = java.time.LocalDate.now().toString()
        return if (prefs[dailyStrategyDay] == today) prefs[dailyStrategyCardId] else null
    }

    suspend fun setDailyStrategy(day: String, cardId: String) {
        context.dataStore.edit {
            it[dailyStrategyDay] = day
            it[dailyStrategyCardId] = cardId
            it[lastStrategyCardId] = cardId
        }
    }

    suspend fun getStrategiesSeedVersion(): Int =
        context.dataStore.data.first()[strategiesSeedVersion] ?: 0

    suspend fun setStrategiesSeedVersion(version: Int) {
        context.dataStore.edit { it[strategiesSeedVersion] = version }
    }

    suspend fun isSeedComplete(): Boolean = context.dataStore.data.first()[seedComplete] == true

    suspend fun setSeedComplete() {
        context.dataStore.edit { it[seedComplete] = true }
    }

    suspend fun resetSeedFlagForTests() {
        context.dataStore.edit { it.remove(seedComplete) }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }

    fun deleteDownloadedModel() {
        ModelCatalog.entries.forEach { modelFile(it.filename).delete() }
        context.filesDir.resolve("models").listFiles()?.filter { it.name.endsWith(".part") }?.forEach { it.delete() }
    }

    suspend fun savePendingPostFocus(summary: PostFocusSummary?) {
        context.dataStore.edit { prefs ->
            if (summary == null) {
                prefs.remove(pendingPostFocusJson)
            } else {
                prefs[pendingPostFocusJson] = json.encodeToString(summary)
            }
        }
    }

    suspend fun loadPendingPostFocus(): PostFocusSummary? {
        val raw = context.dataStore.data.first()[pendingPostFocusJson] ?: return null
        return runCatching { json.decodeFromString<PostFocusSummary>(raw) }.getOrNull()
    }
}

data class ReminderSettings(
    val enabled: Boolean = true,
    val blockReminders: Boolean = true,
    val assignmentReminders: Boolean = true,
    val morningReplan: Boolean = true,
    val morningReplanHour: Int = 8,
    val quietHoursStart: Int = 22,
    val quietHoursEnd: Int = 7,
    val timer5MinWarning: Boolean = true,
    val timerHardStop: Boolean = false
)

data class PlanningSettings(
    val dailyCapacityMinutes: Int = 360,
    val todayOnlyMode: Boolean = false,
    val shutdownHour: Int = 21,
    val shutdownNotificationEnabled: Boolean = false,
    val lastMorningRitualDay: String? = null,
    val lastShutdownDay: String? = null
)

data class AiGpuSettings(
    val enabled: Boolean = true,
    val layers: Int = LocalAiEngine.DEFAULT_GPU_LAYERS
)
