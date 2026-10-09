package com.anchor.adhd.desktop.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okio.Path.Companion.toPath
import java.io.File

/**
 * Windows 11 persistent preferences using Multiplatform DataStore.
 * Saves settings to %APPDATA%\Anchor\anchor_prefs.preferences_pb.
 */
class DesktopUserPreferences {
    private val dataStore: DataStore<Preferences>
        get() = sharedDataStore

    companion object {
        private val dataStoreFile: File by lazy {
            val appData = System.getenv("APPDATA") ?: (System.getProperty("user.home") + "/AppData/Roaming")
            val dir = File(appData, "Anchor")
            if (!dir.exists()) dir.mkdirs()
            File(dir, "anchor_prefs.preferences_pb")
        }

        val sharedDataStore: DataStore<Preferences> by lazy {
            PreferenceDataStoreFactory.createWithPath(
                produceFile = { dataStoreFile.absolutePath.toPath() },
            )
        }

        val FOCUS_WORK_MINUTES = intPreferencesKey("focus_work_minutes")
        val FOCUS_BREAK_MINUTES = intPreferencesKey("focus_break_minutes")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val AIRLOCK_HOTKEY_ENABLED = booleanPreferencesKey("airlock_hotkey_enabled")
        val BLOCK_DISCORD = booleanPreferencesKey("block_discord")
        val BLOCK_STEAM = booleanPreferencesKey("block_steam")
        val BLOCK_YOUTUBE = booleanPreferencesKey("block_youtube")
        val BLOCK_REDDIT = booleanPreferencesKey("block_reddit")
        val AI_ENABLED = booleanPreferencesKey("ai_enabled")
        val AI_MODEL_PATH = stringPreferencesKey("ai_model_path")
        val AI_BINARY_PATH = stringPreferencesKey("ai_binary_path")
        val AI_THREADS = intPreferencesKey("ai_threads")
        val AI_PORT = intPreferencesKey("ai_port")
        val STANDING_SHIELD_ENABLED = booleanPreferencesKey("standing_shield_enabled")
        val STRICT_LOCKDOWN_UNTIL_EPOCH = longPreferencesKey("strict_lockdown_until_epoch")
        val EARNED_LEISURE_BASE_STUDY_MINUTES = intPreferencesKey("earned_leisure_base_study_minutes")
        val EARNED_LEISURE_SPENT_MINUTES_TODAY = intPreferencesKey("earned_leisure_spent_minutes_today")
        val EARNED_LEISURE_EXPIRY_EPOCH = longPreferencesKey("earned_leisure_expiry_epoch")
        val SCOPE_SENTINEL_ENABLED = booleanPreferencesKey("scope_sentinel_enabled")
        val SCOPE_TAX_MINUTES_TODAY = intPreferencesKey("scope_tax_minutes_today")
        val AUTOSTART_ON_BOOT = booleanPreferencesKey("autostart_on_boot")
        val START_MINIMIZED = booleanPreferencesKey("start_minimized")

        fun calculateNext4AmEpoch(nowMillis: Long = System.currentTimeMillis()): Long {
            val zone = java.time.ZoneId.systemDefault()
            val now = java.time.ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), zone)
            var next4Am = now.toLocalDate().atTime(4, 0).atZone(zone)
            if (!now.isBefore(next4Am)) {
                next4Am = next4Am.plusDays(1)
            }
            return next4Am.toInstant().toEpochMilli()
        }
    }

    fun getDefaultBinaryPath(): String {
        val appData = System.getenv("APPDATA") ?: (System.getProperty("user.home") + "/AppData/Roaming")
        val appDataBin = File(appData, "Anchor/bin/llama/llama-server.exe")
        return if (appDataBin.exists()) appDataBin.absolutePath else "desktopApp/bin/llama/llama-server.exe"
    }

    fun getDefaultModelPath(): String {
        val userHome = System.getProperty("user.home")
        val downloadsDir = File(userHome, "Downloads")
        val appData = System.getenv("APPDATA") ?: "$userHome/AppData/Roaming"
        val candidates =
            listOf(
                File(downloadsDir, "MiniCPM5-1B-Claude-Opus-Fable5-Thinking-Q8_0.gguf"),
                File(downloadsDir, "MiniCPM5-1B-Claude-Opus-Fable5-Thinking-Q5_K_M.gguf"),
                File(downloadsDir, "MiniCPM5-1B-Claude-Opus-Fable5-Thinking-Q4_K_M.gguf"),
                File(downloadsDir, "MiniCPM5-1B-Q5_K_M.gguf"),
                File(appData, "Anchor/models/MiniCPM5-1B-Claude-Opus-Fable5-Thinking-Q8_0.gguf"),
                File(appData, "Anchor/models/MiniCPM5-1B-Claude-Opus-Fable5-Thinking-Q5_K_M.gguf"),
                File(downloadsDir, "Ling-3.0-tiny-Q5_K_M.gguf"),
            )
        return candidates.firstOrNull { it.exists() && it.isFile }?.absolutePath
            ?: File(downloadsDir, "MiniCPM5-1B-Claude-Opus-Fable5-Thinking-Q8_0.gguf").absolutePath
    }

    val focusWorkMinutes: Flow<Int> = dataStore.data.map { it[FOCUS_WORK_MINUTES] ?: 25 }
    val focusBreakMinutes: Flow<Int> = dataStore.data.map { it[FOCUS_BREAK_MINUTES] ?: 5 }
    val soundEnabled: Flow<Boolean> = dataStore.data.map { it[SOUND_ENABLED] ?: true }
    val aiEnabled: Flow<Boolean> = dataStore.data.map { it[AI_ENABLED] ?: true }
    val aiModelPath: Flow<String> =
        dataStore.data.map { prefs ->
            val stored = prefs[AI_MODEL_PATH]
            if (stored != null && File(stored).exists()) {
                stored
            } else {
                getDefaultModelPath()
            }
        }
    val aiBinaryPath: Flow<String> = dataStore.data.map { it[AI_BINARY_PATH] ?: getDefaultBinaryPath() }
    val aiThreads: Flow<Int> = dataStore.data.map { it[AI_THREADS] ?: (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1) }
    val aiPort: Flow<Int> = dataStore.data.map { it[AI_PORT] ?: 58321 }
    val standingShieldEnabled: Flow<Boolean> = dataStore.data.map { it[STANDING_SHIELD_ENABLED] ?: false }
    val strictLockdownUntilEpoch: Flow<Long> = dataStore.data.map { it[STRICT_LOCKDOWN_UNTIL_EPOCH] ?: 0L }
    val earnedLeisureBaseStudyMinutes: Flow<Int> = dataStore.data.map { it[EARNED_LEISURE_BASE_STUDY_MINUTES] ?: 60 }
    val earnedLeisureSpentMinutesToday: Flow<Int> = dataStore.data.map { it[EARNED_LEISURE_SPENT_MINUTES_TODAY] ?: 0 }
    val earnedLeisureExpiryEpoch: Flow<Long> = dataStore.data.map { it[EARNED_LEISURE_EXPIRY_EPOCH] ?: 0L }
    val scopeSentinelEnabled: Flow<Boolean> = dataStore.data.map { it[SCOPE_SENTINEL_ENABLED] ?: true }
    val scopeTaxMinutesToday: Flow<Int> = dataStore.data.map { it[SCOPE_TAX_MINUTES_TODAY] ?: 0 }
    val autostartOnBoot: Flow<Boolean> = dataStore.data.map { it[AUTOSTART_ON_BOOT] ?: true }
    val startMinimized: Flow<Boolean> = dataStore.data.map { it[START_MINIMIZED] ?: true }

    suspend fun setFocusWorkMinutes(minutes: Int) {
        dataStore.edit { it[FOCUS_WORK_MINUTES] = minutes }
    }

    suspend fun setFocusBreakMinutes(minutes: Int) {
        dataStore.edit { it[FOCUS_BREAK_MINUTES] = minutes }
    }

    suspend fun setAiEnabled(enabled: Boolean) {
        dataStore.edit { it[AI_ENABLED] = enabled }
    }

    suspend fun setAiModelPath(path: String) {
        dataStore.edit { it[AI_MODEL_PATH] = path }
    }

    suspend fun setAiBinaryPath(path: String) {
        dataStore.edit { it[AI_BINARY_PATH] = path }
    }

    suspend fun setAiThreads(threads: Int) {
        dataStore.edit { it[AI_THREADS] = threads }
    }

    suspend fun setAiPort(port: Int) {
        dataStore.edit { it[AI_PORT] = port }
    }

    suspend fun setStandingShieldEnabled(enabled: Boolean) {
        dataStore.edit { it[STANDING_SHIELD_ENABLED] = enabled }
    }

    suspend fun getFocusWorkMinutesSnapshot(): Int = dataStore.data.first()[FOCUS_WORK_MINUTES] ?: 25

    suspend fun getAiEnabledSnapshot(): Boolean = dataStore.data.first()[AI_ENABLED] ?: true

    suspend fun getAiModelPathSnapshot(): String {
        val stored = dataStore.data.first()[AI_MODEL_PATH]
        return if (stored != null && File(stored).exists()) {
            stored
        } else {
            getDefaultModelPath()
        }
    }

    suspend fun getAiBinaryPathSnapshot(): String = dataStore.data.first()[AI_BINARY_PATH] ?: getDefaultBinaryPath()

    suspend fun getAiThreadsSnapshot(): Int = dataStore.data.first()[AI_THREADS] ?: 8

    suspend fun getAiPortSnapshot(): Int = dataStore.data.first()[AI_PORT] ?: 58321

    suspend fun getStandingShieldEnabledSnapshot(): Boolean = dataStore.data.first()[STANDING_SHIELD_ENABLED] ?: false

    suspend fun setStrictLockdownUntil(epochMillis: Long) {
        dataStore.edit { it[STRICT_LOCKDOWN_UNTIL_EPOCH] = epochMillis }
    }

    suspend fun getStrictLockdownUntilSnapshot(): Long = dataStore.data.first()[STRICT_LOCKDOWN_UNTIL_EPOCH] ?: 0L

    suspend fun setEarnedLeisureBaseStudyMinutes(minutes: Int) {
        dataStore.edit { it[EARNED_LEISURE_BASE_STUDY_MINUTES] = minutes }
    }

    suspend fun setEarnedLeisureSpentMinutesToday(minutes: Int) {
        dataStore.edit { it[EARNED_LEISURE_SPENT_MINUTES_TODAY] = minutes }
    }

    suspend fun setEarnedLeisureExpiryEpoch(epoch: Long) {
        dataStore.edit { it[EARNED_LEISURE_EXPIRY_EPOCH] = epoch }
    }

    suspend fun getEarnedLeisureBaseStudyMinutesSnapshot(): Int = dataStore.data.first()[EARNED_LEISURE_BASE_STUDY_MINUTES] ?: 60

    suspend fun getEarnedLeisureSpentMinutesTodaySnapshot(): Int = dataStore.data.first()[EARNED_LEISURE_SPENT_MINUTES_TODAY] ?: 0

    suspend fun getEarnedLeisureExpiryEpochSnapshot(): Long = dataStore.data.first()[EARNED_LEISURE_EXPIRY_EPOCH] ?: 0L
    suspend fun setScopeSentinelEnabled(enabled: Boolean) {
        dataStore.edit { it[SCOPE_SENTINEL_ENABLED] = enabled }
    }

    suspend fun setScopeTaxMinutesToday(minutes: Int) {
        dataStore.edit { it[SCOPE_TAX_MINUTES_TODAY] = minutes }
    }

    suspend fun getScopeSentinelEnabledSnapshot(): Boolean = dataStore.data.first()[SCOPE_SENTINEL_ENABLED] ?: true

    suspend fun getScopeTaxMinutesTodaySnapshot(): Int = dataStore.data.first()[SCOPE_TAX_MINUTES_TODAY] ?: 0

    suspend fun setAutostartOnBoot(enabled: Boolean) {
        dataStore.edit { it[AUTOSTART_ON_BOOT] = enabled }
    }

    suspend fun setStartMinimized(enabled: Boolean) {
        dataStore.edit { it[START_MINIMIZED] = enabled }
    }

    suspend fun getAutostartOnBootSnapshot(): Boolean = dataStore.data.first()[AUTOSTART_ON_BOOT] ?: true

    suspend fun getStartMinimizedSnapshot(): Boolean = dataStore.data.first()[START_MINIMIZED] ?: true

    fun getDiscoveredModels(): List<File> {
        val candidates = mutableListOf<File>()
        val downloads = File(System.getProperty("user.home"), "Downloads")
        if (downloads.exists() && downloads.isDirectory) {
            downloads.listFiles { f -> f.isFile && f.extension.equals("gguf", ignoreCase = true) }?.let {
                candidates.addAll(it)
            }
        }
        val appData = System.getenv("APPDATA") ?: (System.getProperty("user.home") + "/AppData/Roaming")
        val appDataModels = File(appData, "Anchor/models")
        if (appDataModels.exists() && appDataModels.isDirectory) {
            appDataModels.listFiles { f -> f.isFile && f.extension.equals("gguf", ignoreCase = true) }?.let {
                candidates.addAll(it)
            }
        }
        return candidates.distinctBy { it.absolutePath }.sortedWith(
            compareByDescending<File> { it.name.contains("Fable5-Thinking-Q8_0", ignoreCase = true) }
                .thenByDescending { it.name.contains("Ling-3.0", ignoreCase = true) }
                .thenByDescending { it.lastModified() }
        )
    }
}
