package com.anchor.adhd.desktop

import com.anchor.adhd.desktop.blocker.ActiveWindowInfo
import com.anchor.adhd.desktop.blocker.DesktopProcessMonitor
import com.anchor.adhd.desktop.blocker.DesktopScopeSentinel
import com.anchor.adhd.desktop.db.DesktopBlockRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DesktopBlockerIdentityTest {
    private lateinit var monitor: DesktopProcessMonitor

    @Before
    fun setup() {
        DesktopScopeSentinel.clearCache()
        monitor = DesktopProcessMonitor(CoroutineScope(Dispatchers.Unconfined)) {}
        monitor.updateRules(emptyList())
    }

    private fun window(executable: String, title: String) =
        ActiveWindowInfo("C:/Apps/$executable", executable, title, 123)

    @Test
    fun studyOnlyApps_areAllowedWhenIdleEvenWithEmptyLeisureBank() {
        monitor.updateRules(listOf(
            DesktopBlockRule(1, "chatgpt.exe", "APP", true),
            DesktopBlockRule(2, "antigravity.exe", "APP", true),
        ))
        assertNull(monitor.evaluateWindow(window("ChatGPT.exe", "ChatGPT")))
        assertNull(monitor.evaluateWindow(window("Antigravity.exe", "Python project - Antigravity")))
        assertNull(monitor.evaluateWindow(window("chrome.exe", "ChatGPT - Google Chrome")))

        monitor.isFocusActive = true
        monitor.updateRules(monitor.ruleDefinitions)
        assertNotNull(monitor.evaluateWindow(window("ChatGPT.exe", "ChatGPT")))
        assertNotNull(monitor.evaluateWindow(window("Antigravity.exe", "Python project - Antigravity")))
    }

    @Test
    fun websiteRule_matchesBrowserButNotDesktopAppOrDocument() {
        monitor.isScopeSentinelEnabled = false
        monitor.updateRules(listOf(DesktopBlockRule(1, "chatgpt", "WEB", true, "ALWAYS_24_7")))

        assertNull(monitor.evaluateWindow(window("ChatGPT.exe", "ChatGPT")))
        assertNull(monitor.evaluateWindow(window("WINWORD.EXE", "Notes about ChatGPT")))
        val browserDetection = monitor.evaluateWindow(window("msedge.exe", "ChatGPT - Microsoft Edge"))
        assertNotNull(browserDetection)
        assertTrue(browserDetection!!.isWebDistraction)
    }

    @Test
    fun scopeDetection_usesExecutableToDistinguishAppsFromWebsites() {
        monitor.isFocusActive = true
        val appDetection = monitor.evaluateWindow(window("ChatGPT.exe", "ChatGPT"))
        assertNotNull(appDetection)
        assertFalse(appDetection!!.isWebDistraction)
        assertTrue(appDetection.reason.contains("Focus Mode"))
        val browserDetection = monitor.evaluateWindow(window("chrome.exe", "ChatGPT - Google Chrome"))
        assertNotNull(browserDetection)
        assertTrue(browserDetection!!.isWebDistraction)
    }

    @Test
    fun appAndWebUsage_areRecordedSeparatelyRegardlessOfRuleOrder() {
        monitor.isScopeSentinelEnabled = false
        monitor.updateRules(listOf(
            DesktopBlockRule(1, "chatgpt", "WEB", true, "TRACK_ONLY"),
            DesktopBlockRule(2, "chatgpt.exe", "APP", true, "TRACK_ONLY"),
        ))
        val field = DesktopProcessMonitor::class.java.getDeclaredField("_currentForeground")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val foreground = field.get(monitor) as MutableStateFlow<ActiveWindowInfo?>

        foreground.value = window("ChatGPT.exe", "ChatGPT")
        monitor.trackActiveWindowUsage()
        assertEquals(1, monitor.dailyUsageMap["chatgpt.exe"])
        assertNull(monitor.dailyUsageMap["chatgpt"])

        foreground.value = window("chrome.exe", "ChatGPT - Google Chrome")
        monitor.trackActiveWindowUsage()
        assertEquals(1, monitor.dailyUsageMap["chatgpt.exe"])
        assertEquals(1, monitor.dailyUsageMap["chatgpt"])
    }

    @Test
    fun appQuota_doesNotBorrowWebsiteUsageOrDoubleCountSharedPool() {
        val web = DesktopBlockRule(1, "chatgpt", "WEB", true, "SHARED_POOL", 210, quotaGroup = "AI_BROWSER")
        val app = DesktopBlockRule(2, "chatgpt.exe", "APP", true, "SHARED_POOL", 210, quotaGroup = "AI_BROWSER")
        monitor.updateRules(listOf(web, app))
        monitor.setUsageMap(mapOf("chatgpt" to 60))
        assertEquals(60, monitor.getEffectiveDailyUsageSeconds(app))
        monitor.setUsageMap(mapOf("chatgpt" to 60, "chatgpt.exe" to 120))
        assertEquals(180, monitor.getEffectiveDailyUsageSeconds(web))

        val studyApp = app.copy(scheduleMode = "FOCUS_ONLY", quotaGroup = null)
        monitor.updateRules(listOf(web, studyApp))
        assertEquals(120, monitor.getEffectiveDailyUsageSeconds(studyApp))
    }
}
