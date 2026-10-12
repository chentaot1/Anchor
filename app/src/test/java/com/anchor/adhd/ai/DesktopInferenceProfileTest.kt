package com.anchor.adhd.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anchor.adhd.data.prefs.UserPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [34])
class DesktopInferenceProfileTest {
    @Test fun conversationPromptRetainsRolesAndDisablesReasoningForTheNewTurn() {
        val prompt = DesktopInferenceProfile.prompt("Coach", "What's my first step?", listOf(
            "user" to "I need to write an essay", "assistant" to "Open a blank document",
            "system" to "Invalid history role"
        ))
        assertTrue(prompt.contains("<|im_start|>user\nI need to write an essay<|im_end|>"))
        assertTrue(prompt.contains("<|im_start|>assistant\nOpen a blank document<|im_end|>"))
        assertFalse(prompt.contains("Invalid history role"))
        assertTrue(prompt.endsWith("<|im_start|>assistant\n<think>\n\n</think>\n\n"))
    }

    @Test fun phoneUsesDesktopPromptAndContext() {
        val prompt = DesktopInferenceProfile.prompt("  System  ", "  User  ")
        assertEquals("<|im_start|>system\nSystem<|im_end|>\n<|im_start|>user\nUser<|im_end|>\n<|im_start|>assistant\n<think>\n\n</think>\n\n", prompt)
        assertEquals(2048, LocalAiEngine.CHAT_CONTEXT_SIZE)
        assertEquals(2048, LocalAiEngine.MOBILE_CONTEXT_SIZE)
        assertEquals(0, LocalAiEngine.DEFAULT_GPU_LAYERS)
        assertEquals(256, LocalAiEngine.DEFAULT_MAX_TOKENS)
        assertEquals(ModelDownloadManager.MODEL_FILENAME, ModelCatalog.entries.single().filename)
    }

    @Test fun upgradeReplacesOldModelSelectionAndGpuPreferences() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = UserPreferences(context)
        prefs.clearAll()
        val legacy = context.filesDir.resolve("models/anchor-qwen3.5-4b-q8_0.gguf").apply { parentFile!!.mkdirs(); writeText("legacy weights") }
        try {
            prefs.setActiveModel(legacy.absolutePath, legacy.name)
            prefs.setGpuOffloadEnabled(true)
            prefs.setGpuLayerCount(28)
            val chosen = prefs.getActiveModelPath()
            assertTrue(chosen.endsWith(ModelDownloadManager.MODEL_FILENAME))
            assertFalse(prefs.isGpuOffloadEnabled())
            assertEquals(0, prefs.getGpuLayerCount())
            assertFalse("Legacy Qwen files must not silently satisfy MiniCPM readiness", prefs.isModelDownloaded())
            assertTrue("Upgrade preserves old files", legacy.exists())
        } finally { legacy.delete(); prefs.clearAll() }
    }
}
