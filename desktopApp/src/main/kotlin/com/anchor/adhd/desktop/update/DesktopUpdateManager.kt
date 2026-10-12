package com.anchor.adhd.desktop.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.system.exitProcess

/**
 * State of the Anchor ADHD update system.
 */
sealed interface UpdateState {
    data object Idle : UpdateState

    data object Checking : UpdateState

    data class Unavailable(val message: String) : UpdateState

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
        val remoteUrl: String = "https://github.com/chentaot1/Anchor",
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
 * Checks source updates, applies fast-forward changes, verifies the build,
 * and restarts from that source instead of an older application image.
 */
object DesktopUpdateManager {
    private val operationMutex = Mutex()
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _hasUpdateBadge = MutableStateFlow(false)
    val hasUpdateBadge: StateFlow<Boolean> = _hasUpdateBadge.asStateFlow()

    val projectRoot: File by lazy { findProjectRoot() }
    val isGitEnvironment: Boolean by lazy { File(projectRoot, ".git").exists() }

    var currentCommit: String = "unknown"
        private set
    var currentVersionTag: String = "v1.0.6"
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
                currentVersionTag = "v1.0.6 (Standalone)"
            }
        }

    suspend fun checkForUpdates(silent: Boolean = false) = operationMutex.withLock {
        checkForUpdatesLocked(silent)
    }

    private suspend fun checkForUpdatesLocked(silent: Boolean) =
        withContext(Dispatchers.IO) {
            if (!silent) {
                _updateState.value = UpdateState.Checking
            }

            refreshLocalInfo()

            if (!isGitEnvironment) {
                _updateState.value = UpdateState.Unavailable(
                    "This installation cannot check source updates. Install a newer Windows package when one is available.",
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
                val commitsBehind = revCountRes.stdout.trim().toIntOrNull()
                if (revCountRes.exitCode != 0 || commitsBehind == null) {
                    _updateState.value = UpdateState.Error(
                        "Could not compare this checkout with origin/$currentBranch.",
                        revCountRes.stderr.ifBlank { revCountRes.stdout },
                    )
                    return@withContext
                }

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
            } catch (e: CancellationException) {
                if (!silent) _updateState.value = UpdateState.Error("Update check cancelled. You can check again.")
                throw e
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

    suspend fun performUpdate(onLog: (String) -> Unit = {}) = operationMutex.withLock {
        performUpdateLocked(onLog)
    }

    private suspend fun performUpdateLocked(onLog: (String) -> Unit) =
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
                refreshLocalInfo()
                val branchRes = runCommand("git", "symbolic-ref", "--quiet", "--short", "HEAD")
                if (branchRes.exitCode != 0 || branchRes.stdout.isBlank()) {
                    _updateState.value = UpdateState.Error("Select a local branch before updating this checkout.")
                    return@withContext
                }
                currentBranch = branchRes.stdout.trim()
                _updateState.value =
                    UpdateState.Updating(
                        stage = "Pulling source updates...",
                        progress = 0.2f,
                        logs = logLines.toList(),
                    )
                addLog("Anchor repository root: ${projectRoot.absolutePath}")

                // Fast-forward only: Git refuses conflicting local changes without stashing or rebasing them.
                addLog("Pulling latest commits (git pull --ff-only origin $currentBranch)...")
                val pullRes = runCommand("git", "pull", "--ff-only", "origin", currentBranch, timeoutSeconds = 60)
                if (pullRes.stdout.isNotBlank()) addLog(pullRes.stdout)
                if (pullRes.stderr.isNotBlank()) addLog(pullRes.stderr)

                if (pullRes.exitCode != 0) {
                    _updateState.value = UpdateState.Error(
                        "Source update failed. Resolve local changes or branch divergence before trying again.",
                        pullRes.stderr.ifBlank { pullRes.stdout },
                    )
                    return@withContext
                }

                // Refresh current info after pull
                refreshLocalInfo()

                _updateState.value = UpdateState.Updating(
                    stage = "Building the updated Windows app...",
                    progress = 0.6f,
                    logs = logLines.toList(),
                )
                val wrapper = File(projectRoot, if (isWindows()) "gradlew.bat" else "gradlew")
                val buildCommand = if (isWindows()) {
                    arrayOf("cmd.exe", "/d", "/c", "gradlew.bat", ":desktopApp:classes", "--console=plain")
                } else {
                    arrayOf(wrapper.absolutePath, ":desktopApp:classes", "--console=plain")
                }
                val buildRes = runCommand(*buildCommand, timeoutSeconds = 600)
                if (buildRes.stdout.isNotBlank()) addLog(buildRes.stdout)
                if (buildRes.stderr.isNotBlank()) addLog(buildRes.stderr)
                if (buildRes.exitCode != 0) {
                    _updateState.value = UpdateState.Error(
                        "Source downloaded, but the Windows build failed. The running app has not changed.",
                        buildRes.stderr.ifBlank { buildRes.stdout },
                    )
                    return@withContext
                }

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
                        message = "Updated source built successfully ($currentCommit). Restart to use it.",
                        updatedCommit = currentCommit,
                        requiresRestart = true,
                    )
            } catch (e: CancellationException) {
                _updateState.value = UpdateState.Error(
                    "Source update interrupted. The running app has not changed; review the checkout and retry.",
                )
                throw e
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
            if (!isWindows() || !File(projectRoot, "gradlew.bat").isFile) {
                _updateState.value = UpdateState.Error("Could not find the Windows source launcher. Anchor is still running.")
                return
            }
            // Wait for this process to exit and release the single-instance lock before launching.
            val script = restartScript(projectRoot, ProcessHandle.current().pid())
            ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-Command", script)
                .directory(projectRoot)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            exitProcess(0)
        } catch (e: Exception) {
            _updateState.value = UpdateState.Error("Could not restart Anchor. The app is still running.", e.message)
        }
    }

    internal fun restartScript(root: File, processId: Long): String {
        val escapedRoot = root.absolutePath.replace("'", "''")
        return "Wait-Process -Id $processId -ErrorAction SilentlyContinue; " +
            "Set-Location -LiteralPath '$escapedRoot'; & .\\gradlew.bat :desktopApp:run --console=plain"
    }

    private fun isWindows(): Boolean = System.getProperty("os.name").contains("Windows", ignoreCase = true)

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
    ): CommandResult = DesktopCommandRunner(projectRoot).run(*command, timeoutSeconds = timeoutSeconds)

    data class CommandResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )
}
