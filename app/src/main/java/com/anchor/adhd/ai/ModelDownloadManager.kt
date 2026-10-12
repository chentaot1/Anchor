package com.anchor.adhd.ai

import android.content.Context
import android.os.StatFs
import com.anchor.adhd.data.prefs.UserPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit

/** Explicit downloads belong to the application and survive leaving the setup screen. */
class ModelDownloadManager(
    private val context: Context,
    private val preferences: UserPreferences,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).build(),
) {
    data class Progress(
        val bytesRead: Long,
        val totalBytes: Long,
        val done: Boolean,
        val error: String? = null,
        val filename: String? = null,
        val active: Boolean = false,
    )

    private val _progress = MutableStateFlow(Progress(0, 0, false))
    val progress: Flow<Progress> = _progress.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null
    private val downloadMutex = Mutex()
    @Volatile private var activeCall: Call? = null
    @Volatile var isDownloading: Boolean = false
        private set

    @Synchronized
    fun startDownload(entry: ModelCatalogEntry) {
        if (downloadJob?.isActive == true || isDownloading) return
        downloadJob = scope.launch {
            try { downloadEntry(entry) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { android.util.Log.w("ModelDownload", "Model download interrupted", e) }
        }
    }

    @Synchronized
    fun pauseDownload() {
        downloadJob?.cancel()
        activeCall?.cancel()
    }

    fun freeDiskBytes(): Long {
        val dir = context.filesDir.resolve("models").apply { mkdirs() }
        return StatFs(dir.absolutePath).availableBytes
    }

    fun requiredFreeBytes(entry: ModelCatalogEntry): Long =
        (entry.sizeBytes - partialFile(entry.filename).length().coerceIn(0, entry.sizeBytes)) + STORAGE_RESERVE

    fun isDownloaded(entry: ModelCatalogEntry): Boolean =
        modelFile(entry.filename).let { it.length() == entry.sizeBytes && ModelFileVerifier.isGguf(it) }

    fun downloadedEntries(): List<ModelCatalogEntry> = ModelCatalog.entries.filter { isDownloaded(it) }
    fun isDownloaded(): Boolean = downloadedEntries().isNotEmpty()

    suspend fun downloadEntry(entry: ModelCatalogEntry) = downloadMutex.withLock {
        withContext(Dispatchers.IO) {
            isDownloading = true
            val target = modelFile(entry.filename)
            target.parentFile?.mkdirs()
            val temp = partialFile(entry.filename)
            try {
                if (temp.length() > entry.sizeBytes) temp.delete()
                var existing = temp.length()
                _progress.value = Progress(existing, entry.sizeBytes, false, filename = entry.filename, active = true)
                check(freeDiskBytes() >= requiredFreeBytes(entry)) { "Not enough free storage. Free space and retry." }
                // Recheck a completed partial file without downloading it twice.
                if (existing < entry.sizeBytes) {
                    val request = Request.Builder().url(entry.url).apply {
                        if (existing > 0) header("Range", "bytes=$existing-")
                    }.build()
                    val call = client.newCall(request)
                    activeCall = call
                    currentCoroutineContext().ensureActive()
                    call.execute().use { response ->
                        if (response.code == 416) {
                            temp.delete()
                            error("Partial download is no longer available. Retry to start again.")
                        }
                        check(response.code == 200 || response.code == 206) { "Download failed: ${response.code}" }
                        if (response.code == 206) {
                            val range = Regex("bytes (\\d+)-(\\d+)/(\\d+)")
                                .matchEntire(response.header("Content-Range").orEmpty())
                            check(range != null && range.groupValues[1].toLong() == existing &&
                                range.groupValues[3].toLong() == entry.sizeBytes &&
                                range.groupValues[2].toLong() == entry.sizeBytes - 1) {
                                "Server returned an unexpected download range. Retry later."
                            }
                        } else {
                            existing = 0
                            check(freeDiskBytes() >= entry.sizeBytes + STORAGE_RESERVE) {
                                "Server cannot resume this file. Free storage for a full download and retry."
                            }
                        }
                        val body = response.body ?: error("Empty response")
                        check(body.contentLength() < 0 || body.contentLength() == entry.sizeBytes - existing) {
                            "Server returned an unexpected model size"
                        }
                        var totalRead = existing
                        body.byteStream().use { input ->
                            RandomAccessFile(temp, "rw").use { output ->
                                if (existing == 0L) output.setLength(0)
                                output.seek(existing)
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    check(totalRead + read <= entry.sizeBytes) { "Downloaded model is larger than expected" }
                                    output.write(buffer, 0, read)
                                    totalRead += read
                                    _progress.value = Progress(totalRead, entry.sizeBytes, false, filename = entry.filename, active = true)
                                }
                            }
                        }
                        check(totalRead == entry.sizeBytes) { "Download interrupted. Retry to resume." }
                    }
                }
                currentCoroutineContext().ensureActive()
                try { ModelFileVerifier.verify(temp, entry.sizeBytes, entry.sha256) }
                catch (e: IllegalStateException) { temp.delete(); throw e }
                currentCoroutineContext().ensureActive()
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                preferences.setActiveModel(target.absolutePath, entry.filename)
                _progress.value = Progress(entry.sizeBytes, entry.sizeBytes, true, filename = entry.filename)
            } catch (e: Exception) {
                val paused = !currentCoroutineContext().isActive
                _progress.value = Progress(temp.length(), entry.sizeBytes, false,
                    if (paused) "Download paused. Retry to resume." else e.message ?: "Download failed. Retry to resume.", entry.filename)
                if (paused) throw CancellationException("Model download paused", e)
                throw e
            } finally {
                activeCall = null
                isDownloading = false
            }
        }
    }

    suspend fun downloadModel(url: String = DEFAULT_MODEL_URL, targetFilename: String = MODEL_FILENAME) =
        downloadEntry(ModelCatalog.entries.first().copy(url = url, filename = targetFilename))

    suspend fun downloadQ4Fallback() = downloadModel(Q4_MODEL_URL, Q4_FILENAME)

    /** Upgrade compatibility: verify an existing model without opening APK assets or a URL. */
    suspend fun prepareInstalledModel() = downloadMutex.withLock {
        withContext(Dispatchers.IO) {
            preferences.applyDesktopModelProfile()
            val entry = ModelCatalog.entries.first()
            val target = modelFile(entry.filename)
            if (!target.exists()) return@withContext
            ModelFileVerifier.verify(target, entry.sizeBytes, entry.sha256)
            preferences.setActiveModel(target.absolutePath, entry.filename)
            _progress.value = Progress(entry.sizeBytes, entry.sizeBytes, true, filename = entry.filename)
        }
    }

    private fun modelFile(filename: String) = context.filesDir.resolve("models/$filename")
    private fun partialFile(filename: String) = context.filesDir.resolve("models/$filename.part")

    companion object {
        const val STORAGE_RESERVE = 500_000_000L
        const val MODEL_FILENAME = "MiniCPM5-1B-Claude-Opus-Fable5-Thinking-Q8_0.gguf"
        const val Q4_FILENAME = MODEL_FILENAME
        const val DEFAULT_MODEL_URL = "https://huggingface.co/GnLOLot/MiniCPM5-1B-Claude-Opus-Fable5-Thinking-GGUF/resolve/main/MiniCPM5-1B-Claude-Opus-Fable5-Thinking-Q8_0.gguf"
        const val Q4_MODEL_URL = DEFAULT_MODEL_URL
    }
}
