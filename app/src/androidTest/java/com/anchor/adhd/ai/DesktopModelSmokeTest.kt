package com.anchor.adhd.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anchor.adhd.data.model.AiJobType
import com.anchor.adhd.data.prefs.UserPreferences
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in device checks: run after the bundled model is installed on the target. */
@RunWith(AndroidJUnit4::class)
class DesktopModelSmokeTest {
    @Test fun realDesktopWeightsGenerateStructuredStepsAndUnicode() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = UserPreferences(context)
        assumeTrue("Requires the installed desktop model", prefs.isModelDownloaded())
        assertTrue("Native runtime is required", LlamaBridge.isNativeAvailable)
        val engine = LocalAiEngine(context, prefs)
        try {
            val raw = engine.generateJson(LocalAiEngine.BREAKDOWN_SYSTEM, "Break down this task: write an essay",
                AiGrammar.forJob(AiJobType.BREAKDOWN))
            val result = Json { ignoreUnknownKeys = true }.decodeFromString<AiBreakdownResult>(raw)
            assertTrue(result.steps.size in 2..5)
            assertTrue(result.next_action.isNotBlank())
            assertEquals(0, LlamaBridge.loadedGpuLayers())

            // Force emoji output to exercise JNI UTF-8, including split token bytes.
            val output = StringBuilder()
            engine.generate("Reply with the requested symbol", "🙂", grammar = "root ::= \"🙂\"", maxTokens = 16)
                .collect { output.append(it) }
            assertEquals("🙂", output.toString())
        } finally { engine.ensureUnloaded() }
    }
}
