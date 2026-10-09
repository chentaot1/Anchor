package com.anchor.adhd.desktop.update

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.system.exitProcess

/**
 * State of the Anchor ADHD update system.
 */
sealed interface UpdateState {
    data object Idle : UpdateState

    data object Checking : UpdateState

    data class UpToDate(
        val currentVersion: String,
        val commitHash: String,
        val branch: String,
        val lastCheckedMillis: Long = System.currentTimeMillis(),
    ) : UpdateState

    data class UpdateAvailable(
        val currentVersion: String,
        val currentCommit: String,
        val latestCommit: String,
        val commitsBehind: Int,
        val changelog: List<String>,
        val latestTag: String? = null,
        val remoteUrl: String = "https://github.com/ilovephos/anchor-adhd",
    ) : UpdateState

    data class Updating(
        val stage: String,
        val progress: Float = -1f,
        val logs: List<String> = emptyList(),
    ) : UpdateState

    data class UpdateSuccess(
        val message: String,
        val updatedCommit: String,
        val requiresRestart: Boolean = true,
    ) : UpdateState

    data class Error(
        val message: String,
        val details: String? = null,
    ) : UpdateState
}

/**
 * Desktop Update Manager for Anchor ADHD.
 * Provides safe in-app checking, commit diffing, auto-stashing,
 * git pulling, and seamless restart without manual deleting and redownloading.
 */
object DesktopUpdateManager {
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _hasUpdateBadge = MutableStateFlow(false)
    val hasUpdateBadge: StateFlow<Boolean> = _hasUpdateBadge.asStateFlow()

    val projectRoot: File by lazy { findProjectRoot() }
    val isGitEnvironment: Boolean by lazy { File(projectRoot, ".git").exists() }

    var currentCommit: String = "unknown"
        private set
    var currentVersionTag: String = "v1.0.4"
        private set
    var currentBranch: String = "main"
        private set

    fun initialize(
        scope: CoroutineScope,
        checkImmediately: Boolean = true,
    ) {
        scope.launch(Dispatchers.IO) {
            refreshLocalInfo()
            if (checkImmediately) {
                checkForUpdates(silent = true)
            }
        }
    }

    suspend fun refreshLocalInfo() =
        withContext(Dispatchers.IO) {
            if (isGitEnvironment) {
                val commitRes = runCommand("git", "rev-parse", "--short", "HEAD")
                if (commitRes.exitCode == 0 && commitRes.stdout.isNotBlank()) {
                    currentCommit = commitRes.stdout.trim()
                }
                val tagRes = runCommand("git", "describe", "--tags", "--always")
                if (tagRes.exitCode == 0 && tagRes.stdout.isNotBlank()) {
                    currentVersionTag = tagRes.stdout.trim()
                }
                val branchRes = runCommand("git", "rev-parse", "--abbrev-ref", "HEAD")
                if (branchRes.exitCode == 0 && branchRes.stdout.isNotBlank()) {
                    val resolvedBranch = branchRes.stdout.trim()
                    currentBranch = if (resolvedBranch == "HEAD" || resolvedBranch.isBlank()) "main" else resolvedBranch
                }
            } else {
                currentVersionTag = "v1.0.4 (Standalone)"
            }
        }

    suspend fun checkForUpdates(silent: Boolean = false) =
        withContext(Dispatchers.IO) {
            if (!silent) {
                _updateState.value = UpdateState.Checking
            }

            refreshLocalInfo()

            if (!isGitEnvironment) {
                _updateState.value =
                    UpdateState.UpToDate(
                        currentVersion = currentVersionTag,
                        commitHash = currentCommit,
                        branch = "release",
                        lastCheckedMillis = System.currentTimeMillis(),
                    )
                return@withContext
            }

            try {
                // 1. Fetch remote origin
                val fetchRes = runCommand("git", "fetch", "origin", timeoutSeconds = 25)
                if (fetchRes.exitCode != 0) {
                    if (!silent) {
                        _updateState.value =
                            UpdateState.Error(
                                message = "Could not reach remote GitHub repository.",
                                details = fetchRes.stderr.ifBlank { fetchRes.stdout },
                            )
                    }
                    return@withContext
                }

                // 2. Count commits behind origin/<branch>
                val revCountRes = runCommand("git", "rev-list", "HEAD..origin/$currentBranch", "--count")
                val commitsBehind = revCountRes.stdout.trim().toIntOrNull() ?: 0

                // 3. Check latest remote tag if available
                val remoteTagRes = runCommand("git", "describe", "--tags", "origin/$currentBranch")
                val latestTag = if (remoteTagRes.exitCode == 0) remoteTagRes.stdout.trim() else null

                if (commitsBehind > 0) {
                    val logRes = runCommand("git", "log", "HEAD..origin/$currentBranch", "--oneline", "-n", "10")
                    val changelog =
                        logRes.stdout
                            .lines()
                            .map { it.trim() }
                            .filter { it.isNotBlank() }

                    val latestCommitRes = runCommand("git", "rev-parse", "--short", "origin/$currentBranch")
                    val latestCommit = latestCommitRes.stdout.trim().ifBlank { "origin/$currentBranch" }

                    _hasUpdateBadge.value = true
                    _updateState.value =
                        UpdateState.UpdateAvailable(
                            currentVersion = currentVersionTag,
                            currentCommit = currentCommit,
                            latestCommit = latestCommit,
                            commitsBehind = commitsBehind,
                            changelog = changelog,
                            latestTag = latestTag,
                        )
                } else {
                    _hasUpdateBadge.value = false
                    _updateState.value =
                        UpdateState.UpToDate(
                            currentVersion = currentVersionTag,
                            commitHash = currentCommit,
                            branch = currentBranch,
                            lastCheckedMillis = System.currentTimeMillis(),
                        )
                }
            } catch (e: Exception) {
                if (!silent) {
                    _updateState.value =
                        UpdateState.Error(
                            message = "Update check failed: ${e.message}",
                            details = e.stackTraceToString(),
                        )
                }
            }
        }

    suspend fun performUpdate(onLog: (String) -> Unit = {}) =
        withContext(Dispatchers.IO) {
            if (!isGitEnvironment) {
                _updateState.value = UpdateState.Error("Automated in-app updates are enabled for Git-managed workspaces.")
                return@withContext
            }

            val logLines = mutableListOf<String>()

            fun addLog(msg: String) {
                logLines.add(msg)
                onLog(msg)
            }

            try {
                _updateState.value =
                    UpdateState.Updating(
                        stage = "Preserving local state and pulling updates from GitHub...",
                        progress = 0.2f,
                        logs = logLines.toList(),
                    )
                addLog("Anchor repository root: ${projectRoot.absolutePath}")

                // Step 1: git pull --rebase --autostash origin <branch>
                addLog("Pulling latest commits (git pull --rebase --autostash origin $currentBranch)...")
                val pullRes = runCommand("git", "pull", "--rebase", "--autostash", "origin", currentBranch, timeoutSeconds = 60)
                if (pullRes.stdout.isNotBlank()) addLog(pullRes.stdout)
                if (pullRes.stderr.isNotBlank()) addLog(pullRes.stderr)

                if (pullRes.exitCode != 0) {
                    // Fallback attempt with standard pull
                    addLog("Rebase returned non-zero. Attempting standard git pull origin $currentBranch...")
                    val fallbackRes = runCommand("git", "pull", "origin", currentBranch, timeoutSeconds = 60)
                    if (fallbackRes.stdout.isNotBlank()) addLog(fallbackRes.stdout)
                    if (fallbackRes.stderr.isNotBlank()) addLog(fallbackRes.stderr)

                    if (fallbackRes.exitCode != 0) {
                        _updateState.value =
                            UpdateState.Error(
                                message = "Update pull encountered a conflict.",
                                details = (pullRes.stderr + "\n" + fallbackRes.stderr).trim(),
                            )
                        return@withContext
                    }
                }

                // Refresh current info after pull
                refreshLocalInfo()

                _updateState.value =
                    UpdateState.Updating(
                        stage = "Update completed successfully!",
                        progress = 1.0f,
                        logs = logLines.toList(),
                    )
                addLog("Updated successfully! Current version is now $currentVersionTag ($currentCommit)")

                _hasUpdateBadge.value = false
                _updateState.value =
                    UpdateState.UpdateSuccess(
                        message = "Anchor updated successfully to $currentVersionTag ($currentCommit)!",
                        updatedCommit = currentCommit,
                        requiresRestart = true,
                    )
            } catch (e: Exception) {
                _updateState.value =
                    UpdateState.Error(
                        message = "Failed to apply update: ${e.message}",
                        details = e.stackTraceToString(),
                    )
            }
        }

    fun restartApplication() {
        try {
            val isWindows = System.getProperty("os.name").contains("Windows", ignoreCase = true)
            if (isWindows) {
                val launchBat = File(projectRoot, "Launch-Anchor.bat")
                if (launchBat.exists()) {
                    ProcessBuilder("cmd.exe", "/c", "start", "", launchBat.absolutePath)
                        .directory(projectRoot)
                        .start()
                } else {
                    val gradlewBat = File(projectRoot, "gradlew.bat")
                    if (gradlewBat.exists()) {
                        ProcessBuilder("cmd.exe", "/c", "start", "", gradlewBat.absolutePath, ":desktopApp:run")
                            .directory(projectRoot)
                            .start()
                    }
                }
            }
            exitProcess(0)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun dismissBadge() {
        _hasUpdateBadge.value = false
    }

    private fun findProjectRoot(): File {
        var dir = File(System.getProperty("user.dir")).canonicalFile
        while (dir.parentFile != null) {
            if (File(dir, ".git").exists() || File(dir, "settings.gradle.kts").exists()) {
                return dir
            }
            dir = dir.parentFile ?: break
        }
        return File(System.getProperty("user.dir")).canonicalFile
    }

    private suspend fun runCommand(
        vararg command: String,
        timeoutSeconds: Long = 30,
    ): CommandResult =
        withContext(Dispatchers.IO) {
            try {
                val pb =
                    ProcessBuilder(*command)
                        .directory(projectRoot)
                        .redirectErrorStream(false)
                val proc = pb.start()

                val stdoutDeferred =
                    async(Dispatchers.IO) {
                        proc.inputStream.bufferedReader().use { it.readText() }
                    }
                val stderrDeferred =
                    async(Dispatchers.IO) {
                        proc.errorStream.bufferedReader().use { it.readText() }
                    }

                val completed =
                    withTimeoutOrNull(timeoutSeconds * 1000L) {
                        proc.waitFor()
                        val stdout = stdoutDeferred.await()
                        val stderr = stderrDeferred.await()
                        CommandResult(proc.exitValue(), stdout, stderr)
                    }

                if (completed == null) {
                    proc.destroyForcibly()
                    CommandResult(-1, "", "Command timed out after ${timeoutSeconds}s")
                } else {
                    completed
                }
            } catch (e: Exception) {
                CommandResult(-1, "", e.message ?: "Execution error")
            }
        }

    data class CommandResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )
}
