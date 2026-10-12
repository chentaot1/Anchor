package com.anchor.adhd.ai

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Before
import org.junit.After
import java.util.UUID
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anchor.adhd.data.prefs.UserPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowStatFs
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class OptionalModelDeliveryTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var prefs: UserPreferences
    private lateinit var prefsScope: CoroutineScope
    @Before fun setUp() {
        prefsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = context.filesDir.resolve("test-${UUID.randomUUID()}.preferences_pb")
        prefs = UserPreferences(context, PreferenceDataStoreFactory.create(scope = prefsScope, produceFile = { file }))
    }
    @After fun tearDown() { prefsScope.cancel() }

    private val bytes = "GGUFverified test fixture".toByteArray()
    private fun hash(data: ByteArray) = MessageDigest.getInstance("SHA-256").digest(data)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun manager(prefs: UserPreferences): ModelDownloadManager {
        val dir = context.filesDir.resolve("models").apply { mkdirs() }
        ShadowStatFs.registerStats(dir.absolutePath, 1_000_000, 1_000_000, 1_000_000)
        return ModelDownloadManager(context, prefs)
    }

    private fun withServer(action: (ModelCatalogEntry, FixtureServer) -> Unit) {
        val server = FixtureServer()
        server.start()
        try {
            action(ModelCatalog.entries.first().copy(sizeBytes = bytes.size.toLong(),
                sha256 = hash(bytes), url = "http://127.0.0.1:${server.address.port}/model"), server)
        } finally { server.stop(0) }
    }

    @Test fun startupAndSkipNeverRequestAModel() = runBlocking {
        val manager = manager(prefs)
        prefs.initializeModelOnboarding()
        assertEquals(false, prefs.modelOnboardingComplete.first())
        manager.prepareInstalledModel()
        assertFalse(manager.isDownloaded())
        assertFalse(manager.progress.first().active)
        prefs.setSeedComplete()
        // Seeding a fresh install must not silently dismiss its persisted setup choice.
        prefs.initializeModelOnboarding()
        assertEquals(false, prefs.modelOnboardingComplete.first())
        prefs.completeModelOnboarding()
        assertEquals(true, prefs.modelOnboardingComplete.first())
        assertFalse(manager.isDownloaded())
    }

    @Test fun existingUsersSkipOnboarding() = runBlocking {
        prefs.setSeedComplete()
        prefs.initializeModelOnboarding()
        assertEquals(true, prefs.modelOnboardingComplete.first())
    }

    @Test fun explicitDownloadResumesAndActivatesOnlyVerifiedBytes() {
        withServer { entry, server ->
            val prefix = 8
            val part = context.filesDir.resolve("models/${entry.filename}.part")
            part.parentFile!!.mkdirs()
            part.writeBytes(bytes.copyOf(prefix))
            var requestedRange: String? = null
            server.createContext("/model") { exchange ->
                requestedRange = exchange.requestHeaders.getFirst("Range")
                exchange.responseHeaders.add("Content-Range", "bytes $prefix-${bytes.lastIndex}/${bytes.size}")
                exchange.sendResponseHeaders(206, (bytes.size - prefix).toLong())
                exchange.responseBody.use { it.write(bytes, prefix, bytes.size - prefix) }
            }
            runBlocking {
                prefs.applyDesktopModelProfile()
                val manager = manager(prefs)
                assertEquals(entry.sizeBytes - prefix + ModelDownloadManager.STORAGE_RESERVE, manager.requiredFreeBytes(entry))
                manager.downloadEntry(entry)
                assertEquals("bytes=$prefix-", requestedRange)
                assertArrayEquals(bytes, context.filesDir.resolve("models/${entry.filename}").readBytes())
                assertEquals(context.filesDir.resolve("models/${entry.filename}").absolutePath, prefs.getActiveModelPath())
                assertTrue(manager.progress.first().done)
                assertFalse(part.exists())
            }
        }
    }

    @Test fun serverIgnoringRangeRestartsInsteadOfAppending() {
        withServer { entry, server ->
            val part = context.filesDir.resolve("models/${entry.filename}.part")
            part.parentFile!!.mkdirs()
            part.writeBytes(bytes.copyOf(8))
            server.createContext("/model") { exchange ->
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            runBlocking { manager(prefs).downloadEntry(entry) }
            assertArrayEquals(bytes, context.filesDir.resolve("models/${entry.filename}").readBytes())
        }
    }

    @Test fun corruptPayloadIsDeletedAndNeverActivated() {
        withServer { entry, server ->
            val corrupt = bytes.copyOf().apply { this[lastIndex] = 0 }
            server.createContext("/model") { exchange ->
                exchange.sendResponseHeaders(200, corrupt.size.toLong())
                exchange.responseBody.use { it.write(corrupt) }
            }
            runBlocking {
                val manager = manager(prefs)
                assertThrows(IllegalStateException::class.java) { runBlocking { manager.downloadEntry(entry) } }
                assertFalse(context.filesDir.resolve("models/${entry.filename}").exists())
                assertFalse(context.filesDir.resolve("models/${entry.filename}.part").exists())
                assertFalse(manager.progress.first().done)
                assertTrue(manager.progress.first().error!!.contains("checksum"))
            }
        }
    }

    @Test fun wrongResumeRangeLeavesPartialUnchanged() {
        withServer { entry, server ->
            val part = context.filesDir.resolve("models/${entry.filename}.part")
            part.parentFile!!.mkdirs()
            val original = bytes.copyOf(8)
            part.writeBytes(original)
            server.createContext("/model") { exchange ->
                exchange.responseHeaders.add("Content-Range", "bytes 0-${bytes.lastIndex}/${bytes.size}")
                exchange.sendResponseHeaders(206, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            assertThrows(IllegalStateException::class.java) { runBlocking { manager(prefs).downloadEntry(entry) } }
            assertArrayEquals(original, part.readBytes())
            assertFalse(context.filesDir.resolve("models/${entry.filename}").exists())
        }
    }

    @Test fun pauseKeepsPartialBytesAndRetryCompletes() {
        withServer { base, server ->
            val payload = ByteArray(256 * 1024) { 7 }.apply { bytes.copyOf(4).copyInto(this) }
            val entry = base.copy(sizeBytes = payload.size.toLong(), sha256 = hash(payload))
            val releaseFirstRequest = CountDownLatch(1)
            var resumedFrom = 0
            server.createContext("/model") { exchange ->
                val range = exchange.requestHeaders.getFirst("Range")
                if (range == null) {
                    exchange.sendResponseHeaders(200, payload.size.toLong())
                    exchange.responseBody.write(payload, 0, 64 * 1024)
                    exchange.responseBody.flush()
                    releaseFirstRequest.await(5, TimeUnit.SECONDS)
                } else {
                    resumedFrom = range.removePrefix("bytes=").removeSuffix("-").toInt()
                    exchange.responseHeaders.add("Content-Range", "bytes $resumedFrom-${payload.lastIndex}/${payload.size}")
                    exchange.sendResponseHeaders(206, (payload.size - resumedFrom).toLong())
                    exchange.responseBody.use { it.write(payload, resumedFrom, payload.size - resumedFrom) }
                }
            }
            runBlocking {
                val manager = manager(prefs)
                try {
                    manager.startDownload(entry)
                    withTimeout(5000) { manager.progress.first { it.bytesRead > 0 } }
                    manager.pauseDownload()
                    withTimeout(5000) { manager.progress.first { it.error?.contains("paused") == true } }
                    val part = context.filesDir.resolve("models/${entry.filename}.part")
                    assertTrue(part.length() in 1 until entry.sizeBytes)
                    assertFalse(context.filesDir.resolve("models/${entry.filename}").exists())
                    releaseFirstRequest.countDown()
                    withTimeout(5000) { while (manager.isDownloading) delay(10) }
                    manager.downloadEntry(entry)
                    assertTrue(resumedFrom > 0)
                    assertArrayEquals(payload, context.filesDir.resolve("models/${entry.filename}").readBytes())
                } finally { releaseFirstRequest.countDown(); manager.pauseDownload() }
            }
        }
    }

    @Test fun lowStoragePreventsNetworkRequests() {
        withServer { entry, server ->
            var requested = false
            server.createContext("/model") { requested = true }
                val manager = manager(prefs)
            ShadowStatFs.registerStats(context.filesDir.resolve("models").absolutePath, 1, 0, 0)
            assertThrows(IllegalStateException::class.java) { runBlocking { manager.downloadEntry(entry) } }
            assertFalse(requested)
        }
    }

    /** Tiny loopback HTTP fixture: tests exercise OkHttp's real streaming/range handling. */
    private class FixtureServer {
        private val socket = ServerSocket().apply { bind(InetSocketAddress("127.0.0.1", 0)) }
        val address get() = socket.localSocketAddress as InetSocketAddress
        private val handlers = ConcurrentHashMap<String, (Exchange) -> Unit>()
        private val worker = Thread {
            while (!socket.isClosed) {
                try {
                    socket.accept().use { connection ->
                        connection.soTimeout = 5000
                        val input = connection.getInputStream().bufferedReader()
                        val request = input.readLine().split(" ")
                        val headers = Headers()
                        while (true) {
                            val line = input.readLine() ?: break
                            if (line.isEmpty()) break
                            headers.add(line.substringBefore(":"), line.substringAfter(":").trim())
                        }
                        handlers.getValue(request[1])(Exchange(connection, headers))
                    }
                } catch (_: IOException) { /* Client cancellation closes its stream. */ }
            }
        }.apply { isDaemon = true }
        fun start() = worker.start()
        fun createContext(path: String, handler: (Exchange) -> Unit) { handlers[path] = handler }
        fun stop(delay: Int) { socket.close(); worker.join(1000) }
    }

    private class Headers {
        val values = linkedMapOf<String, String>()
        fun add(name: String, value: String) { values[name] = value }
        fun getFirst(name: String): String? = values.entries.firstOrNull { it.key.equals(name, true) }?.value
    }

    private class Exchange(private val socket: Socket, val requestHeaders: Headers) {
        val responseHeaders = Headers()
        val responseBody get() = socket.getOutputStream()
        fun sendResponseHeaders(code: Int, length: Long) {
            val headers = "HTTP/1.1 $code OK\r\nContent-Length: $length\r\nConnection: close\r\n" +
                responseHeaders.values.entries.joinToString("") { "${it.key}: ${it.value}\r\n" } + "\r\n"
            responseBody.write(headers.toByteArray())
        }
    }
}
