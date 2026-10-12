package com.anchor.adhd.desktop

import com.anchor.adhd.desktop.blocker.BlockerDetection
import com.anchor.adhd.desktop.blocker.DesktopProcessMonitor
import com.anchor.adhd.desktop.blocker.DesktopScopeSentinel
import com.anchor.adhd.desktop.blocker.ScaledTimeBank
import com.anchor.adhd.desktop.blocker.ScopeVerdict
import com.anchor.adhd.desktop.blocker.TimeBankStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import com.anchor.adhd.desktop.db.DesktopBlockRule
import com.anchor.adhd.desktop.db.DesktopClassSchedule
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DesktopProcessMonitorTest {
    private lateinit var monitor: DesktopProcessMonitor
    private var detected: BlockerDetection? = null

    @Before
    fun setup() {
        detected = null
        monitor =
            DesktopProcessMonitor(CoroutineScope(Dispatchers.Unconfined)) {
                detected = it
            }
    }

    @Test
    fun emergencyPass_suppressesBlockForDuration() {
        monitor.grantEmergencyPass("discord.exe", durationMillis = 5000L)
        assertTrue(monitor.isEmergencyPassActive("discord.exe"))
        assertTrue(monitor.isEmergencyPassActive("discord"))
    }

    @Test
    fun emergencyPass_expiresCorrectly() {
        monitor.grantEmergencyPass("telegram.exe", durationMillis = -100L)
        assertFalse(monitor.isEmergencyPassActive("telegram.exe"))
    }

    @Test
    fun customBlockedRules_updatedAccurately() {
        monitor.setBlockedExecutables(listOf("customgame.exe", "netflix.exe"))
        monitor.grantEmergencyPass("customgame.exe", durationMillis = 5000L)
        assertTrue(monitor.isEmergencyPassActive("customgame.exe"))
    }

    @Test
    fun updateRules_separatesAppAndWebRules() {
        monitor.isFocusActive = true
        val rules =
            listOf(
                com.anchor.adhd.desktop.db
                    .DesktopBlockRule(id = 1, target = "game.exe", ruleType = "APP", enabled = true),
                com.anchor.adhd.desktop.db
                    .DesktopBlockRule(id = 2, target = "youtube", ruleType = "WEB", enabled = true),
                com.anchor.adhd.desktop.db
                    .DesktopBlockRule(id = 3, target = "disabled.exe", ruleType = "APP", enabled = false),
            )
        monitor.updateRules(rules)
        assertTrue(monitor.activeBlockedExecutables.contains("game.exe"))
        assertTrue(monitor.activeBlockedTitleKeywords.contains("youtube"))
        assertFalse(monitor.activeBlockedExecutables.contains("disabled.exe"))
    }

    @Test
    fun updateRules_whenIdle_onlyEnforces247Rules() {
        monitor.isFocusActive = false
        val rules =
            listOf(
                com.anchor.adhd.desktop.db.DesktopBlockRule(
                    id = 1,
                    target = "discord.exe",
                    ruleType = "APP",
                    enabled = true,
                    scheduleMode = "ALWAYS_24_7",
                ),
                com.anchor.adhd.desktop.db.DesktopBlockRule(
                    id = 2,
                    target = "spotify.exe",
                    ruleType = "APP",
                    enabled = true,
                    scheduleMode = "FOCUS_ONLY",
                ),
                com.anchor.adhd.desktop.db.DesktopBlockRule(
                    id = 3,
                    target = "reddit",
                    ruleType = "WEB",
                    enabled = true,
                    scheduleMode = "ALWAYS_24_7",
                ),
                com.anchor.adhd.desktop.db.DesktopBlockRule(
                    id = 4,
                    target = "youtube",
                    ruleType = "WEB",
                    enabled = true,
                    scheduleMode = "FOCUS_ONLY",
                ),
            )
        monitor.updateRules(rules)

        // Only 24/7 rules should be active outside of focus time
        assertTrue(monitor.activeBlockedExecutables.contains("discord.exe"))
        assertFalse(monitor.activeBlockedExecutables.contains("spotify.exe"))
        assertTrue(monitor.activeBlockedTitleKeywords.contains("reddit"))
        assertFalse(monitor.activeBlockedTitleKeywords.contains("youtube"))
    }

    @Test
    fun updateRules_whenFocusActive_enforcesAllEnabledRules() {
        monitor.isFocusActive = true
        val rules =
            listOf(
                com.anchor.adhd.desktop.db.DesktopBlockRule(
                    id = 1,
                    target = "discord.exe",
                    ruleType = "APP",
                    enabled = true,
                    scheduleMode = "ALWAYS_24_7",
                ),
                com.anchor.adhd.desktop.db.DesktopBlockRule(
                    id = 2,
                    target = "spotify.exe",
                    ruleType = "APP",
                    enabled = true,
                    scheduleMode = "FOCUS_ONLY",
                ),
                com.anchor.adhd.desktop.db.DesktopBlockRule(
                    id = 3,
                    target = "reddit",
                    ruleType = "WEB",
                    enabled = true,
                    scheduleMode = "ALWAYS_24_7",
                ),
                com.anchor.adhd.desktop.db.DesktopBlockRule(
                    id = 4,
                    target = "youtube",
                    ruleType = "WEB",
                    enabled = true,
                    scheduleMode = "FOCUS_ONLY",
                ),
                com.anchor.adhd.desktop.db.DesktopBlockRule(
                    id = 5,
                    target = "steam.exe",
                    ruleType = "APP",
                    enabled = false,
                    scheduleMode = "ALWAYS_24_7",
                ),
            )
        monitor.updateRules(rules)

        // All enabled rules (both 24/7 and FOCUS_ONLY) should be blocked during study time
        assertTrue(monitor.activeBlockedExecutables.contains("discord.exe"))
        assertTrue(monitor.activeBlockedExecutables.contains("spotify.exe"))
        assertTrue(monitor.activeBlockedTitleKeywords.contains("reddit"))
        assertTrue(monitor.activeBlockedTitleKeywords.contains("youtube"))

        // Disabled rules should remain unblocked
        assertFalse(monitor.activeBlockedExecutables.contains("steam.exe"))
    }

    @Test
    fun standingShield_stateFlagIsIndependent() {
        assertFalse(monitor.isFocusActive)
        assertFalse(monitor.isStandingShieldActive)

        monitor.isStandingShieldActive = true
        assertTrue(monitor.isStandingShieldActive)
        assertFalse(monitor.isFocusActive)

        monitor.isFocusActive = true
        assertTrue(monitor.isStandingShieldActive)
        assertTrue(monitor.isFocusActive)
    }

    @Test
    fun emergencyPass_matchesVariousFormats() {
        monitor.grantEmergencyPass("Discord", durationMillis = 10_000L)
        assertTrue(monitor.isEmergencyPassActive("discord.exe"))
        assertTrue(monitor.isEmergencyPassActive("Discord.exe"))
        assertTrue(monitor.isEmergencyPassActive("discord"))
        assertTrue(monitor.isEmergencyPassActive("DISCORD"))
        assertFalse(monitor.isEmergencyPassActive("steam.exe"))
    }

    @Test
    fun clearDetection_resetsBlockerState() {
        monitor.grantEmergencyPass("spotify.exe", durationMillis = 5000L)
        monitor.clearDetection()
        // clearDetection clears current detection and lastBlockedHwnd
        assertNull(monitor.currentDetection.value)
        assertNull(monitor.lastBlockedHwnd)
    }

    @Test
    fun classSchedule_detectsActiveLectureAccurately() {
        val schedules =
            listOf(
                DesktopClassSchedule(
                    courseCode = "SCI 101",
                    courseName = "Introductory Science",
                    sessionType = "Lecture",
                    dayOfWeek = 1, // Monday
                    startMinuteOfDay = 540, // 9:00 AM
                    endMinuteOfDay = 590, // 9:50 AM
                ),
                DesktopClassSchedule(
                    courseCode = "ART 202",
                    courseName = "Sample Arts",
                    sessionType = "Discussion",
                    dayOfWeek = 5, // Friday
                    startMinuteOfDay = 900, // 3:00 PM
                    endMinuteOfDay = 950, // 3:50 PM
                ),
            )
        monitor.classSchedules = schedules

        // Monday 9:30 AM (2026-09-28 is a Monday)
        val duringScience = LocalDateTime.of(2026, 9, 28, 9, 30)
        val active1 = monitor.getCurrentlyActiveClass(duringScience)
        assertNotNull(active1)
        assertEquals("SCI 101", active1?.courseCode)

        // Monday 10:05 AM (after class)
        val afterScience = LocalDateTime.of(2026, 9, 28, 10, 5)
        assertNull(monitor.getCurrentlyActiveClass(afterScience))

        // Friday 3:20 PM (2026-10-02 is a Friday)
        val duringArts = LocalDateTime.of(2026, 10, 2, 15, 20)
        val active2 = monitor.getCurrentlyActiveClass(duringArts)
        assertNotNull(active2)
        assertEquals("ART 202", active2?.courseCode)
    }

    @Test
    fun dailyAllowance_exhaustionTriggersBlock() {
        monitor.isFocusActive = false
        val daytime = LocalDateTime.of(2026, 9, 28, 14, 0)
        val rule =
            DesktopBlockRule(
                id = 1,
                target = "cursor.exe",
                ruleType = "APP",
                enabled = true,
                scheduleMode = "LEISURE_QUOTA",
                dailyAllowanceMinutes = 75,
            )

        // Usage at 30 minutes (1800s): under quota, should not be blocked
        monitor.setUsageMap(mapOf("cursor.exe" to 1800))
        monitor.updateRules(listOf(rule), daytime)
        assertFalse(monitor.activeBlockedExecutables.contains("cursor.exe"))

        // Usage at 75 minutes (4500s): quota exhausted, should be blocked
        monitor.setUsageMap(mapOf("cursor.exe" to 4500))
        monitor.updateRules(listOf(rule), daytime)
        assertTrue(monitor.activeBlockedExecutables.contains("cursor.exe"))

        // Usage at 80 minutes (4800s): still blocked
        monitor.setUsageMap(mapOf("cursor.exe" to 4800))
        monitor.updateRules(listOf(rule), daytime)
        assertTrue(monitor.activeBlockedExecutables.contains("cursor.exe"))
    }

    @Test
    fun evaluateWindow_refreshesAppProtectionAcrossCurfewBoundaries() {
        monitor.isScopeSentinelEnabled = false
        val rule = DesktopBlockRule(
            id = 1, target = "cursor.exe", ruleType = "APP", enabled = true,
            scheduleMode = "LEISURE_QUOTA", dailyAllowanceMinutes = 75,
        )
        val beforeCurfew = LocalDateTime.of(2026, 9, 28, 0, 59)
        monitor.updateRules(listOf(rule), beforeCurfew)
        val window = com.anchor.adhd.desktop.blocker.ActiveWindowInfo(
            "C:/Apps/cursor.exe", "cursor.exe", "Project - Cursor", 1234,
        )
        assertNull(monitor.evaluateWindow(window, beforeCurfew))
        val blocked = monitor.evaluateWindow(window, beforeCurfew.withHour(1).withMinute(0))
        assertNotNull(blocked)
        assertEquals("Night Curfew Active (1:00 AM - 7:00 AM)", blocked!!.reason)
        assertNull(monitor.evaluateWindow(window, beforeCurfew.withHour(7).withMinute(0)))
    }

    @Test
    fun quickPass_suppressesBlockAndDecrementsCounter() {
        assertEquals(5, monitor.remainingQuickPasses)

        // Grant first quick pass
        val granted = monitor.grantQuickPass("discord.exe", durationMillis = 120_000L)
        assertTrue(granted)
        assertTrue(monitor.isQuickPassActive("discord.exe"))
        assertTrue(monitor.isQuickPassActive("discord"))
        assertEquals(4, monitor.remainingQuickPasses)

        // Grant remaining 4 passes
        assertTrue(monitor.grantQuickPass("discord.exe", durationMillis = 120_000L))
        assertTrue(monitor.grantQuickPass("discord.exe", durationMillis = 120_000L))
        assertTrue(monitor.grantQuickPass("discord.exe", durationMillis = 120_000L))
        assertTrue(monitor.grantQuickPass("discord.exe", durationMillis = 120_000L))
        assertEquals(0, monitor.remainingQuickPasses)

        // 6th attempt must be rejected (capped at 5)
        val rejected = monitor.grantQuickPass("discord.exe", durationMillis = 120_000L)
        assertFalse(rejected)
        assertEquals(0, monitor.remainingQuickPasses)
    }

    @Test
    fun nightCurfew_blocksLeisureOutsideWorkHours() {
        // 11:00 PM (23:00) -> Daytime/evening, curfew now starts at 1:00 AM
        val evening = LocalTime.of(23, 0)
        assertFalse(monitor.isNightCurfewActive(evening))

        // 12:45 AM (00:45) -> Before 1:00 AM curfew
        val midnightWork = LocalTime.of(0, 45)
        assertFalse(monitor.isNightCurfewActive(midnightWork))

        // 1:05 AM (01:05) -> In night curfew
        val lateNight = LocalTime.of(1, 5)
        assertTrue(monitor.isNightCurfewActive(lateNight))

        // 2:00 AM (02:00) -> In night curfew
        val earlyMorning = LocalTime.of(2, 0)
        assertTrue(monitor.isNightCurfewActive(earlyMorning))

        // 6:59 AM -> Still in night curfew
        val justBeforeSeven = LocalTime.of(6, 59)
        assertTrue(monitor.isNightCurfewActive(justBeforeSeven))

        // 7:00 AM -> Morning curfew lifted
        val morning = LocalTime.of(7, 0)
        assertFalse(monitor.isNightCurfewActive(morning))

        // 2:30 PM (14:30) -> Daytime, not in night curfew
        val afternoon = LocalTime.of(14, 30)
        assertFalse(monitor.isNightCurfewActive(afternoon))
    }

    @Test
    fun sharedAiBrowserPool_tracksAndCapsCombinedUsage() {
        val daytime = LocalDateTime.of(2026, 9, 28, 14, 0)
        val geminiRule = DesktopBlockRule(
            id = 10,
            target = "gemini",
            ruleType = "WEB",
            enabled = true,
            scheduleMode = "SHARED_POOL",
            dailyAllowanceMinutes = 210, // 3.5 hours
            quotaGroup = "AI_BROWSER",
        )
        val chatGptRule = DesktopBlockRule(
            id = 11,
            target = "chatgpt",
            ruleType = "WEB",
            enabled = true,
            scheduleMode = "SHARED_POOL",
            dailyAllowanceMinutes = 210,
            quotaGroup = "AI_BROWSER",
        )
        val claudeRule = DesktopBlockRule(
            id = 12,
            target = "claude",
            ruleType = "WEB",
            enabled = true,
            scheduleMode = "SHARED_POOL",
            dailyAllowanceMinutes = 210,
            quotaGroup = "AI_BROWSER",
        )

        val rules = listOf(geminiRule, chatGptRule, claudeRule)
        monitor.ruleDefinitions = rules
        monitor.isFocusActive = true // Doing homework

        // 1. Under quota: 60m Gemini + 60m Claude + 60m ChatGPT = 180m < 210m
        monitor.setUsageMap(
            mapOf(
                "gemini" to 60 * 60,
                "claude" to 60 * 60,
                "chatgpt" to 60 * 60,
            ),
        )
        monitor.updateRules(rules)

        monitor.updateRules(rules, daytime)
        // Allowed for homework during study session
        assertFalse(monitor.activeBlockedTitleKeywords.contains("gemini"))
        assertFalse(monitor.activeBlockedTitleKeywords.contains("claude"))
        assertFalse(monitor.activeBlockedTitleKeywords.contains("chatgpt"))

        // 2. Shared quota reached 210m: 60m + 90m + 60m = 210m
        monitor.setUsageMap(
            mapOf(
                "gemini" to 60 * 60,
                "claude" to 90 * 60,
                "chatgpt" to 60 * 60,
            ),
        )
        monitor.updateRules(rules)

        monitor.updateRules(rules, daytime)
        // ALL 3 tools in the shared pool are now locked!
        assertTrue(monitor.activeBlockedTitleKeywords.contains("gemini"))
        assertTrue(monitor.activeBlockedTitleKeywords.contains("claude"))
        assertTrue(monitor.activeBlockedTitleKeywords.contains("chatgpt"))
    }

    @Test
    fun logicalDate_resetsAt4AmNotMidnight() {
        // Tuesday Sep 29 at 03:59 AM -> Counts as Monday Sep 28
        val lateNightSession = LocalDateTime.of(2026, 9, 29, 3, 59, 59)
        assertEquals(LocalDate.of(2026, 9, 28), monitor.getLogicalDate(lateNightSession))
        assertEquals(LocalDate.of(2026, 9, 28), com.anchor.adhd.desktop.db.AnchorDesktopDatabase.getLogicalDate(lateNightSession))

        // Tuesday Sep 29 at 04:00 AM -> Daily reset rolls over to Sep 29
        val resetMoment = LocalDateTime.of(2026, 9, 29, 4, 0, 0)
        assertEquals(LocalDate.of(2026, 9, 29), monitor.getLogicalDate(resetMoment))
        assertEquals(LocalDate.of(2026, 9, 29), com.anchor.adhd.desktop.db.AnchorDesktopDatabase.getLogicalDate(resetMoment))

        // Tuesday Sep 29 at 11:59 PM -> Sep 29
        val endOfDay = LocalDateTime.of(2026, 9, 29, 23, 59, 59)
        assertEquals(LocalDate.of(2026, 9, 29), monitor.getLogicalDate(endOfDay))

        // Wednesday Sep 30 at 12:01 AM (midnight past) -> Still counts as Sep 29
        val pastMidnight = LocalDateTime.of(2026, 9, 30, 0, 1, 0)
        assertEquals(LocalDate.of(2026, 9, 29), monitor.getLogicalDate(pastMidnight))
    }

    @Test
    fun dailyRollover_resetsQuickPassesAndUsageMapAt4Am() {
        var rolloverCallbackFired = false
        monitor.onDailyRollover = {
            rolloverCallbackFired = true
        }

        // Set state as of Monday Sep 28
        monitor.currentLogicalDate = LocalDate.of(2026, 9, 28)
        monitor.quickPassesUsedToday = 5
        assertEquals(0, monitor.remainingQuickPasses)

        // Set rule with daily quota exhausted
        val rule = DesktopBlockRule(
            id = 1,
            target = "cursor.exe",
            ruleType = "APP",
            enabled = true,
            scheduleMode = "LEISURE_QUOTA",
            dailyAllowanceMinutes = 75,
        )
        monitor.ruleDefinitions = listOf(rule)
        monitor.setUsageMap(mapOf("cursor.exe" to 5000))
        monitor.updateRules(listOf(rule))
        assertTrue(monitor.activeBlockedExecutables.contains("cursor.exe"))

        // At 3:59 AM on Sep 29 -> Still logical date Sep 28, NO rollover
        val before4Am = LocalDateTime.of(2026, 9, 29, 3, 59, 0)
        val rolledOverBefore = monitor.checkDailyRollover(before4Am)
        assertFalse(rolledOverBefore)
        assertFalse(rolloverCallbackFired)
        assertEquals(5, monitor.quickPassesUsedToday)
        assertEquals(0, monitor.remainingQuickPasses)
        assertEquals(5000, monitor.dailyUsageMap["cursor.exe"])
        assertTrue(monitor.activeBlockedExecutables.contains("cursor.exe"))

        // At 4:00 AM on Sep 29 -> 4 AM reset fires!
        val at4Am = LocalDateTime.of(2026, 9, 29, 4, 0, 0)
        val rolledOverAt4 = monitor.checkDailyRollover(at4Am)
        assertTrue(rolledOverAt4)
        assertTrue(rolloverCallbackFired)
        assertEquals(LocalDate.of(2026, 9, 29), monitor.currentLogicalDate)
        assertEquals(0, monitor.quickPassesUsedToday)
        assertEquals(5, monitor.remainingQuickPasses)
        assertTrue(monitor.dailyUsageMap.isEmpty())

        // The quota clears at 4 AM, but night curfew still protects until 7 AM.
        assertTrue(monitor.activeBlockedExecutables.contains("cursor.exe"))
        monitor.updateRules(listOf(rule), LocalDateTime.of(2026, 9, 29, 7, 0))
        assertFalse(monitor.activeBlockedExecutables.contains("cursor.exe"))
    }

    @Test
    fun strictDailyLockdown_enforcesAllRulesEvenIfDisabled() {
        val rules = listOf(
            DesktopBlockRule(
                id = 1,
                target = "discord.exe",
                ruleType = "APP",
                enabled = false, // Disabled in UI
                scheduleMode = "ALWAYS_24_7",
            ),
            DesktopBlockRule(
                id = 2,
                target = "reddit",
                ruleType = "WEB",
                enabled = false, // Disabled in UI
                scheduleMode = "ALWAYS_24_7",
            ),
        )
        monitor.updateRules(rules)
        // Without strict lockdown, disabled rules are NOT blocked
        assertFalse(monitor.activeBlockedExecutables.contains("discord.exe"))
        assertFalse(monitor.activeBlockedTitleKeywords.contains("reddit"))

        // Engaging Strict Daily Lockdown forces rules active to prevent dopamine bypass
        monitor.setStrictDailyLockdown(true)
        assertTrue(monitor.isStrictDailyLockdownActive)
        assertTrue(monitor.activeBlockedExecutables.contains("discord.exe"))
        assertTrue(monitor.activeBlockedTitleKeywords.contains("reddit"))

        // Releasing lockdown restores normal rule state
        monitor.setStrictDailyLockdown(false)
        assertFalse(monitor.isStrictDailyLockdownActive)
        assertFalse(monitor.activeBlockedExecutables.contains("discord.exe"))
        assertFalse(monitor.activeBlockedTitleKeywords.contains("reddit"))
    }

    @Test
    fun classSchedule_forcesRulesActiveEvenIfDisabled() {
        val rules = listOf(
            DesktopBlockRule(
                id = 1,
                target = "steam.exe",
                ruleType = "APP",
                enabled = false, // User tried to disable Steam before lecture
                scheduleMode = "FOCUS_ONLY",
            ),
        )
        val schedule = listOf(
            DesktopClassSchedule(
                courseCode = "SCI 101",
                courseName = "Introductory Science",
                sessionType = "Lecture",
                dayOfWeek = 1, // Monday
                startMinuteOfDay = 840, // 2:00 PM
                endMinuteOfDay = 890, // 2:50 PM
            ),
        )
        monitor.classSchedules = schedule
        monitor.updateRules(rules)

        // Outside class hours on Monday at 10:00 AM
        val outsideClass = LocalDateTime.of(2026, 9, 28, 10, 0)
        assertNull(monitor.getCurrentlyActiveClass(outsideClass))

        // During class hours on Monday at 2:30 PM
        val duringClass = LocalDateTime.of(2026, 9, 28, 14, 30)
        val active = monitor.getCurrentlyActiveClass(duringClass)
        assertNotNull(active)
        assertEquals("SCI 101", active?.courseCode)
    }

    @Test
    fun extractWebKeywords_extractsDomainAndBrandNamesAccurately() {
        val urlKeywords = DesktopProcessMonitor.extractWebKeywords("https://example.com/")
        assertTrue("Must contain brand name example", urlKeywords.contains("example"))
        assertTrue("Must contain domain example.com", urlKeywords.contains("example.com"))

        val exampleKeywords = DesktopProcessMonitor.extractWebKeywords("example.org")
        assertTrue("Must contain brand name example", exampleKeywords.contains("example"))
        assertTrue("Must contain domain example.org", exampleKeywords.contains("example.org"))

        val youtubeKeywords = DesktopProcessMonitor.extractWebKeywords("https://www.youtube.com/watch?v=123")
        assertTrue("Must contain youtube", youtubeKeywords.contains("youtube"))
        assertTrue("Must contain youtube.com", youtubeKeywords.contains("youtube.com"))

        val twitterKeywords = DesktopProcessMonitor.extractWebKeywords("twitter / x")
        assertTrue("Must contain twitter", twitterKeywords.contains("twitter"))
        assertTrue("Must contain x.com", twitterKeywords.contains("x.com"))
        assertTrue("Must contain ' / x'", twitterKeywords.contains(" / x"))

        val subKeywords = DesktopProcessMonitor.extractWebKeywords("https://old.reddit.com/r/all")
        assertTrue("Must contain reddit", subKeywords.contains("reddit"))
    }

    @Test
    fun updateRules_247WebRulesWithUrls_properlyEnforcedOutsideFocus() {
        monitor.isFocusActive = false
        val rules = listOf(
            DesktopBlockRule(
                id = 126,
                target = "https://example.com/",
                ruleType = "WEB",
                enabled = true,
                scheduleMode = "ALWAYS_24_7",
            ),
            DesktopBlockRule(
                id = 348,
                target = "example.org",
                ruleType = "WEB",
                enabled = true,
                scheduleMode = "ALWAYS_24_7",
            ),
            DesktopBlockRule(
                id = 32,
                target = "youtube",
                ruleType = "WEB",
                enabled = true,
                scheduleMode = "FOCUS_ONLY",
            ),
        )

        monitor.updateRules(rules)

        // 24/7 web rules must extract brand and domain keywords outside focus
        assertTrue("Must include example", monitor.activeBlockedTitleKeywords.contains("example"))
        assertTrue("Must include example.com", monitor.activeBlockedTitleKeywords.contains("example.com"))
        assertTrue("Must include example", monitor.activeBlockedTitleKeywords.contains("example"))
        assertTrue("Must include example.org", monitor.activeBlockedTitleKeywords.contains("example.org"))

        // Focus-only rules must NOT be in active keywords when idle
        assertFalse("Focus-only youtube should NOT be active when idle", monitor.activeBlockedTitleKeywords.contains("youtube"))
    }

    @Test
    fun sanitizeTarget_normalizesWebAndAppTargets() {
        assertEquals("example.com", com.anchor.adhd.desktop.db.AnchorDesktopDatabase.sanitizeTarget("https://example.com/", "WEB"))
        assertEquals("example.org", com.anchor.adhd.desktop.db.AnchorDesktopDatabase.sanitizeTarget("https://www.example.org/comic/1", "WEB"))
        assertEquals("discord.exe", com.anchor.adhd.desktop.db.AnchorDesktopDatabase.sanitizeTarget("discord", "APP"))
        assertEquals("spotify.exe", com.anchor.adhd.desktop.db.AnchorDesktopDatabase.sanitizeTarget("spotify.exe", "APP"))
    }

    @Test
    fun tabSwitchGrace_suppressesEvaluationDuringGracePeriod() {
        assertFalse("Grace should initially be inactive", monitor.isTabSwitchGraceActive())
        monitor.grantTabSwitchGrace(durationMillis = 10_000L)
        assertTrue("Grace should be active after grant", monitor.isTabSwitchGraceActive())
    }

    @Test
    fun tabSwitchGrace_expiresCorrectly() {
        monitor.grantTabSwitchGrace(durationMillis = -100L)
        assertFalse("Expired grace period should return false", monitor.isTabSwitchGraceActive())
    }

    @Test
    fun blockerDetection_differentiatesWebAndAppDistraction() {
        val webDetection = BlockerDetection(
            isBlocked = true,
            appOrSiteName = "YouTube",
            reason = "Distracting Website (YouTube)",
            isWebDistraction = true,
        )
        val appDetection = BlockerDetection(
            isBlocked = true,
            appOrSiteName = "Discord",
            reason = "Distracting Application (Discord.exe)",
            isWebDistraction = false,
        )
        assertTrue(webDetection.isWebDistraction)
        assertFalse(appDetection.isWebDistraction)
    }

    @Test
    fun scaledTimeBank_progressiveTiers_requireIncreasingStudyTime() {
        val base = 60
        // Tier 1 (index 0): 60 min study required (1.0x)
        assertEquals(60, ScaledTimeBank.getTierRequiredMinutes(0, base))
        // Tier 2 (index 1): 75 min study required (1.25x)
        assertEquals(75, ScaledTimeBank.getTierRequiredMinutes(1, base))
        // Tier 3 (index 2): 90 min study required (1.5x)
        assertEquals(90, ScaledTimeBank.getTierRequiredMinutes(2, base))
        // Tier 4+ (index 3+): 120 min study required (2.0x)
        assertEquals(120, ScaledTimeBank.getTierRequiredMinutes(3, base))
        assertEquals(120, ScaledTimeBank.getTierRequiredMinutes(4, base))
    }

    @Test
    fun scaledTimeBank_earnsThirtyMinuteBlocksPerTier() {
        val base = 60
        // 0 min study -> 0m earned
        var status = ScaledTimeBank.calculateStatus(focusMinutesToday = 0, spentMinutesToday = 0, baseMinutes = base)
        assertEquals(0, status.totalEarnedMinutesToday)
        assertEquals(0, status.availableBankedMinutes)
        assertEquals(1, status.currentTier)
        assertEquals(0, status.minutesIntoCurrentTier)
        assertEquals(60, status.minutesRequiredForNextTier)
        assertEquals(0f, status.nextTierProgress, 0.001f)

        // 30 min study -> 0m earned, 50% into Tier 1
        status = ScaledTimeBank.calculateStatus(focusMinutesToday = 30, spentMinutesToday = 0, baseMinutes = base)
        assertEquals(0, status.totalEarnedMinutesToday)
        assertEquals(0, status.availableBankedMinutes)
        assertEquals(1, status.currentTier)
        assertEquals(30, status.minutesIntoCurrentTier)
        assertEquals(0.5f, status.nextTierProgress, 0.001f)

        // 60 min study -> 30m earned (Tier 1 complete, now on Tier 2 requiring 75m)
        status = ScaledTimeBank.calculateStatus(focusMinutesToday = 60, spentMinutesToday = 0, baseMinutes = base)
        assertEquals(30, status.totalEarnedMinutesToday)
        assertEquals(30, status.availableBankedMinutes)
        assertEquals(2, status.currentTier)
        assertEquals(0, status.minutesIntoCurrentTier)
        assertEquals(75, status.minutesRequiredForNextTier)

        // 60 + 75 = 135 min study -> 60m earned (Tier 2 complete, now on Tier 3 requiring 90m)
        status = ScaledTimeBank.calculateStatus(focusMinutesToday = 135, spentMinutesToday = 0, baseMinutes = base)
        assertEquals(60, status.totalEarnedMinutesToday)
        assertEquals(60, status.availableBankedMinutes)
        assertEquals(3, status.currentTier)
        assertEquals(0, status.minutesIntoCurrentTier)
        assertEquals(90, status.minutesRequiredForNextTier)
    }

    @Test
    fun scaledTimeBank_reservoirCappedAtSixtyMinutes_toPreventIdleHoarding() {
        val base = 60
        // Total study: 60 (T1) + 75 (T2) + 90 (T3) = 225 min -> 90 min earned
        // But reservoir is capped at 60 min max held at once!
        val status = ScaledTimeBank.calculateStatus(focusMinutesToday = 225, spentMinutesToday = 0, baseMinutes = base)
        assertEquals(90, status.totalEarnedMinutesToday)
        assertEquals(60, status.availableBankedMinutes)
        assertTrue(status.isCapped)

        // If user has spent 60 minutes, raw available = 90 - 60 = 30m, un-capped
        val spentStatus = ScaledTimeBank.calculateStatus(focusMinutesToday = 225, spentMinutesToday = 60, baseMinutes = base)
        assertEquals(30, spentStatus.availableBankedMinutes)
        assertFalse(spentStatus.isCapped)
    }

    @Test
    fun earnedLeisurePass_bypassesAlways247RulesWhenActive() {
        monitor.isFocusActive = false
        val rules = listOf(
            DesktopBlockRule(
                id = 1,
                target = "discord.exe",
                ruleType = "APP",
                enabled = true,
                scheduleMode = "ALWAYS_24_7",
            ),
            DesktopBlockRule(
                id = 2,
                target = "youtube",
                ruleType = "WEB",
                enabled = true,
                scheduleMode = "ALWAYS_24_7",
            ),
        )
        // Initially, 24/7 rules are enforced when idle
        monitor.updateRules(rules)
        assertTrue(monitor.activeBlockedExecutables.contains("discord.exe"))
        assertTrue(monitor.activeBlockedTitleKeywords.contains("youtube"))

        // Activate 30-minute Earned Leisure Pass
        monitor.activateEarnedLeisure(durationMinutes = 30)
        assertTrue(monitor.isEarnedLeisureActive())
        assertTrue(monitor.getRemainingEarnedLeisureSeconds() > 1700)

        // With pass active, 24/7 rules are bypassed
        monitor.updateRules(rules)
        assertFalse(monitor.activeBlockedExecutables.contains("discord.exe"))
        assertFalse(monitor.activeBlockedTitleKeywords.contains("youtube"))

        // Canceling leisure immediately restores 24/7 rules
        monitor.cancelEarnedLeisure()
        assertFalse(monitor.isEarnedLeisureActive())
        monitor.updateRules(rules)
        assertTrue(monitor.activeBlockedExecutables.contains("discord.exe"))
        assertTrue(monitor.activeBlockedTitleKeywords.contains("youtube"))
    }

    @Test
    fun lectureShield_strictlyOverridesEarnedLeisure() {
        val now = LocalTime.now()
        val currentDayInt = LocalDate.now().dayOfWeek.value // 1 (Mon) to 7 (Sun)
        val nowMin = now.hour * 60 + now.minute
        val activeLecture = DesktopClassSchedule(
            id = 1,
            courseCode = "CS101",
            courseName = "Algorithms",
            sessionType = "Lecture",
            dayOfWeek = currentDayInt,
            startMinuteOfDay = (nowMin - 10).coerceAtLeast(0),
            endMinuteOfDay = (nowMin + 50).coerceAtMost(1439),
        )
        monitor.classSchedules = listOf(activeLecture)
        monitor.isFocusActive = false

        val rules = listOf(
            DesktopBlockRule(
                id = 1,
                target = "discord.exe",
                ruleType = "APP",
                enabled = true,
                scheduleMode = "ALWAYS_24_7",
            ),
        )

        // Even with Earned Leisure active, lecture hours strictly block distractions!
        monitor.activateEarnedLeisure(durationMinutes = 30)
        assertTrue(monitor.isEarnedLeisureActive())
        monitor.updateRules(rules)
        assertTrue("Lecture shield must override earned leisure", monitor.activeBlockedExecutables.contains("discord.exe"))
    }

    @Test
    fun scopeSentinel_supportsAnyCurrentSubject() {
        DesktopScopeSentinel.clearCache()
        for (subject in listOf("Physics", "Philosophy", "Programming", "Biology", "Design")) {
            assertEquals(ScopeVerdict.IN_SCOPE,
                DesktopScopeSentinel.evaluateHeuristics("$subject tutorial", "Study $subject").verdict)
        }
    }

    @Test
    fun scopeSentinel_doesNotBlockSubjectsByDefault() {
        DesktopScopeSentinel.clearCache()
        for (title in listOf("Physics tutorial", "Philosophy essay", "Programming tutorial", "Biology review")) {
            assertEquals(ScopeVerdict.UNKNOWN, DesktopScopeSentinel.evaluateHeuristics(title).verdict)
        }
    }

    @Test
    fun scopeSentinel_studyWorkspaceIsAllowed() {
        DesktopScopeSentinel.clearCache()
        assertEquals(ScopeVerdict.IN_SCOPE, DesktopScopeSentinel.evaluateHeuristics("Course syllabus - Google Docs").verdict)
    }

    @Test
    fun scopeSentinel_unrelatedTitlesNeedClassification() {
        DesktopScopeSentinel.clearCache()
        assertEquals(ScopeVerdict.UNKNOWN, DesktopScopeSentinel.evaluateHeuristics("Unknown activity", "Write an essay").verdict)
    }

    @Test
    fun scopeSentinel_honorsManualBlock() {
        DesktopScopeSentinel.clearCache()
        DesktopScopeSentinel.recordManualClassification("Example activity", ScopeVerdict.OUT_OF_SCOPE)
        assertEquals(ScopeVerdict.OUT_OF_SCOPE, DesktopScopeSentinel.evaluateHeuristics("Example activity").verdict)
    }

    @Test
    fun scopeSentinel_consecutiveLingering_triggersTaxCallback() {
        DesktopScopeSentinel.clearCache()
        monitor.isFocusActive = true
        monitor.isScopeSentinelEnabled = true
        monitor.currentTaskTitle = "Write a course essay"
        DesktopScopeSentinel.recordManualClassification("Unrelated example activity - Google Chrome", ScopeVerdict.OUT_OF_SCOPE)

        var taxedMinutes = 0
        monitor.onOutOfScopeTaxApplied = { _, mins ->
            taxedMinutes += mins
        }

        // Simulate a minute on a user-marked unrelated window
        // Set foreground window info
        val outOfScopeWindow = com.anchor.adhd.desktop.blocker.ActiveWindowInfo(
            executablePath = "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
            executableName = "chrome.exe",
            windowTitle = "Unrelated example activity - Google Chrome",
            pid = 1234,
        )

        // Reflection or setter to trigger trackActiveWindowUsage
        val currentFgField = DesktopProcessMonitor::class.java.getDeclaredField("_currentForeground")
        currentFgField.isAccessible = true
        val stateFlow = currentFgField.get(monitor) as kotlinx.coroutines.flow.MutableStateFlow<com.anchor.adhd.desktop.blocker.ActiveWindowInfo?>
        stateFlow.value = outOfScopeWindow

        // Run 60 ticks (60 seconds)
        for (i in 1..60) {
            monitor.trackActiveWindowUsage()
        }

        assertEquals("Should trigger 1 minute tax after 60 seconds", 1, taxedMinutes)
        assertEquals(60, monitor.consecutiveOutOfScopeSeconds)

        // Switching back to in-scope window resets consecutive seconds
        val inScopeWindow = com.anchor.adhd.desktop.blocker.ActiveWindowInfo(
            executablePath = "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
            executableName = "chrome.exe",
            windowTitle = "Course essay - Google Docs",
            pid = 1234,
        )
        stateFlow.value = inScopeWindow
        monitor.trackActiveWindowUsage()

        assertEquals("Returning to in-scope resets distraction seconds to 0", 0, monitor.consecutiveOutOfScopeSeconds)
    }

    @Test
    fun scopeSentinel_honorsManualAllowanceAcrossTasks() {
        DesktopScopeSentinel.clearCache()
        DesktopScopeSentinel.recordManualClassification("Example resource", ScopeVerdict.IN_SCOPE)
        assertEquals(ScopeVerdict.IN_SCOPE, DesktopScopeSentinel.evaluateHeuristics("Example resource", "First task").verdict)
        assertEquals(ScopeVerdict.IN_SCOPE, DesktopScopeSentinel.evaluateHeuristics("Example resource", "Second task").verdict)
    }

    @Test
    fun scopeSentinel_evaluateWindowWaitsFor60SecondsBeforeHardBlock() {
        DesktopScopeSentinel.clearCache()
        monitor.isFocusActive = true
        monitor.isScopeSentinelEnabled = true
        monitor.currentTaskTitle = "Write a course essay"
        DesktopScopeSentinel.recordManualClassification("Unrelated example activity - Google Chrome", ScopeVerdict.OUT_OF_SCOPE)

        val outOfScopeWindow =
            com.anchor.adhd.desktop.blocker.ActiveWindowInfo(
                executablePath = "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
                executableName = "chrome.exe",
                windowTitle = "Unrelated example activity - Google Chrome",
                pid = 4321,
            )

        val daytime = LocalDateTime.of(2026, 9, 28, 14, 0)

        // At 15s and 45s (< 60s): should NOT trigger hard BlockerDetection yet (allows 30s warning and 60s tax first)
        monitor.consecutiveOutOfScopeSeconds = 15
        assertNull(monitor.evaluateWindow(outOfScopeWindow, now = daytime))

        monitor.consecutiveOutOfScopeSeconds = 45
        assertNull(monitor.evaluateWindow(outOfScopeWindow, now = daytime))

        // At 60s: hard BlockerDetection fires
        monitor.consecutiveOutOfScopeSeconds = 60
        val detection = monitor.evaluateWindow(outOfScopeWindow, now = daytime)
        assertNotNull("Should trigger hard block at >= 60s out-of-scope", detection)
        assertTrue(detection!!.isBlocked)
        assertTrue(detection.reason.contains("Out-of-Scope in Focus Mode"))
    }

    @Test
    fun nightCurfew_reasonStringMatches1AmTo7Am() {
        val discordRule =
            DesktopBlockRule(
                id = 1,
                target = "discord.exe",
                ruleType = "APP",
                enabled = true,
                scheduleMode = "ALWAYS_24_7",
            )
        monitor.updateRules(listOf(discordRule))

        val discordWindow =
            com.anchor.adhd.desktop.blocker.ActiveWindowInfo(
                executablePath = "C:\\TestApps\\Discord\\Discord.exe",
                executableName = "discord.exe",
                windowTitle = "Discord",
                pid = 9999,
            )

        val curfewTime = LocalDateTime.of(2026, 9, 28, 2, 15)
        val detection = monitor.evaluateWindow(discordWindow, now = curfewTime)
        assertNotNull(detection)
        assertEquals("Night Curfew Active (1:00 AM - 7:00 AM)", detection!!.reason)
    }
}
