package com.anchor.adhd.ai

import android.content.Context
import android.os.StatFs
import com.anchor.adhd.data.prefs.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

class ModelDownloadManager(
    private val context: Context,
    private val preferences: UserPreferences
) {
    data class Progress(
        val bytesRead: Long,
        val totalBytes: Long,
        val done: Boolean,
        val error: String? = null,
        val filename: String? = null
    )

    private val _progress = MutableStateFlow(Progress(0, 0, false))
    val progress: Flow<Progress> = _progress.asStateFlow()

    private val downloadMutex = Mutex()
    @Volatile
    var isDownloading: Boolean = false
        private set

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    fun freeDiskBytes(): Long {
        val dir = context.filesDir.resolve("models")
        if (!dir.exists()) dir.mkdirs()
        val stat = StatFs(dir.absolutePath)
        return stat.availableBytes
    }

    fun isDownloaded(entry: ModelCatalogEntry): Boolean =
        modelFile(entry.filename).exists()

    fun downloadedEntries(): List<ModelCatalogEntry> =
        ModelCatalog.entries.filter { isDownloaded(it) }

    suspend fun downloadEntry(entry: ModelCatalogEntry) =
        downloadModel(entry.url, entry.filename)

    suspend fun downloadModel(url: String = DEFAULT_MODEL_URL, targetFilename: String = MODEL_FILENAME) =
        downloadMutex.withLock {
            withContext(Dispatchers.IO) {
                isDownloading = true
                val dir = context.filesDir.resolve("models").apply { mkdirs() }
                val target = dir.resolve(targetFilename)
                val temp = File(target.parent, "${target.name}.part")
                try {
                    var existing = if (temp.exists()) temp.length() else 0L
                    _progress.value = Progress(existing, 0, false, filename = targetFilename)

                    val requestBuilder = Request.Builder().url(url)
                    if (existing > 0) {
                        requestBuilder.header("Range", "bytes=$existing-")
                    }
                    client.newCall(requestBuilder.build()).execute().use { response ->
                        if (!response.isSuccessful && response.code != 206) {
                            if (existing > 0 && response.code == 416) {
                                temp.delete()
                                error("Partial download invalid — restart download")
                            }
                            error("Download failed: ${response.code}")
                        }
                        val body = response.body ?: error("Empty response")
                        if (existing > 0 && response.code == 200) existing = 0
                        val contentLen = body.contentLength().coerceAtLeast(0)
                        val total = when {
                            response.code == 206 -> existing + contentLen
                            contentLen > 0 -> contentLen
                            else -> existing
                        }
                        var totalRead = existing
                        body.byteStream().use { input ->
                            RandomAccessFile(temp, "rw").use { output ->
                                if (existing == 0L) output.setLength(0)
                                if (existing > 0) output.seek(existing)
                                val buffer = ByteArray(8192)
                                var read: Int
                                while (input.read(buffer).also { read = it } != -1) {
                                    output.write(buffer, 0, read)
                                    totalRead += read
                                    _progress.value = Progress(totalRead, total, false, filename = targetFilename)
                                }
                            }
                        }
                        if (total > 0 && totalRead < total) {
                            error("Download incomplete: $totalRead of $total bytes")
                        }
                        if (!isValidGguf(temp)) {
                            error("Downloaded file is not a valid GGUF model")
                        }
                        verifyModel(temp)
                        java.nio.file.Files.move(temp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                        preferences.setActiveModel(target.absolutePath, targetFilename)
                        val bytes = target.length()
                        _progress.value = Progress(bytes, total.coerceAtLeast(bytes), true, filename = targetFilename)
                    }
                } catch (e: Exception) {
                    _progress.value = Progress(0, 0, true, e.message, targetFilename)
                    throw e
                } finally {
                    isDownloading = false
                }
            }
        }

    suspend fun downloadQ4Fallback() = downloadModel(Q4_MODEL_URL, Q4_FILENAME)

    fun isDownloaded(): Boolean = preferences.isModelDownloaded()

    /** Install the exact desktop weights from this APK; partial copies never become active. */
    suspend fun installBundledModel() = downloadMutex.withLock {
        withContext(Dispatchers.IO) {
            preferences.applyDesktopModelProfile()
            val target = modelFile(MODEL_FILENAME)
            if (target.exists() && runCatching { verifyModel(target) }.isSuccess) {
                preferences.setActiveModel(target.absolutePath, MODEL_FILENAME)
                _progress.value = Progress(target.length(), target.length(), true, filename = MODEL_FILENAME)
                return@withContext
            }
            if (!context.assets.list("models").orEmpty().contains(MODEL_FILENAME)) return@withContext
            isDownloading = true
            val temporary = File(target.parentFile, "$MODEL_FILENAME.installing")
            try {
                target.parentFile?.mkdirs()
                var copied = 0L
                _progress.value = Progress(0, DesktopInferenceProfile.MODEL_BYTES, false, filename = MODEL_FILENAME)
                context.assets.open("models/$MODEL_FILENAME").use { input ->
                    temporary.outputStream().use { output ->
                        val buffer = ByteArray(1024 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            copied += count
                            _progress.value = Progress(copied, DesktopInferenceProfile.MODEL_BYTES, false, filename = MODEL_FILENAME)
                        }
                    }
                }
                verifyModel(temporary)
                java.nio.file.Files.move(temporary.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                preferences.setActiveModel(target.absolutePath, MODEL_FILENAME)
                _progress.value = Progress(copied, copied, true, filename = MODEL_FILENAME)
            } catch (e: Exception) {
                _progress.value = Progress(0, DesktopInferenceProfile.MODEL_BYTES, true, e.message, MODEL_FILENAME)
                throw e
            } finally {
                temporary.delete()
                isDownloading = false
            }
        }
    }

    private fun verifyModel(file: File) {
        check(file.length() == DesktopInferenceProfile.MODEL_BYTES && isValidGguf(file)) { "Desktop model file is incomplete" }
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        check(hash == DesktopInferenceProfile.MODEL_SHA256) { "Model checksum does not match the desktop's weights" }
    }

    private fun modelFile(filename: String) =
        context.filesDir.resolve("models/$filename")

    private fun isValidGguf(file: File): Boolean {
        if (!file.exists() || file.length() < 4) return false
        return file.inputStream().use { input ->
            val magic = ByteArray(4)
            input.read(magic) == 4 && magic.contentEquals(byteArrayOf(0x47, 0x47, 0x55, 0x46))
        }
    }

    companion object {
        const val MODEL_FILENAME = "MiniCPM5-1B-Claude-Opus-Fable5-Thinking-Q8_0.gguf"
        const val Q4_FILENAME = MODEL_FILENAME

        const val DEFAULT_MODEL_URL =
            "https://huggingface.co/GnLOLot/MiniCPM5-1B-Claude-Opus-Fable5-Thinking-GGUF/resolve/main/MiniCPM5-1B-Claude-Opus-Fable5-Thinking-Q8_0.gguf"

        const val Q4_MODEL_URL =
            DEFAULT_MODEL_URL
    }
}
