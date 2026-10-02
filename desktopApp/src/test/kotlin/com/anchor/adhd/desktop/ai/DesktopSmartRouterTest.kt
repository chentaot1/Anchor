package com.anchor.adhd.desktop.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopSmartRouterTest {
    @Test
    fun breakdown_preservesConjunctionAndCourseDetails() {
        val title = "Read chapter 4 on classical conditioning and make revision notes"
        val result = DesktopSmartRouter.routeQuick(title) as SmartRouteResult.TaskBreakdown
        assertEquals(title, result.taskTitle)
        assertTrue(result.detail.contains(result.step1Starter))
    }

    @Test
    fun explicitBreakdown_preservesMultilineInstructionsInsteadOfOpeningOtherTools() {
        val title = "Review the syllabus and complete Chin 103 written assignments\nInclude vocabulary, grammar, and translation"
        val result = DesktopSmartRouter.routeQuick("Break down: $title")
        assertTrue(result is SmartRouteResult.TaskBreakdown)
        assertEquals(title, (result as SmartRouteResult.TaskBreakdown).taskTitle)
    }

    @Test
    fun testTimerStandaloneMinutes() {
        val r25 = DesktopSmartRouter.routeQuick("25m")
        assertTrue(r25 is SmartRouteResult.TimerAction)
        assertEquals(25, (r25 as SmartRouteResult.TimerAction).durationMinutes)

        val r15 = DesktopSmartRouter.routeQuick("15m")
        assertTrue(r15 is SmartRouteResult.TimerAction)
        assertEquals(15, (r15 as SmartRouteResult.TimerAction).durationMinutes)

        val r3 = DesktopSmartRouter.routeQuick("3m")
        assertTrue(r3 is SmartRouteResult.TimerAction)
        assertEquals(3, (r3 as SmartRouteResult.TimerAction).durationMinutes)

        val r50min = DesktopSmartRouter.routeQuick("50 min")
        assertTrue(r50min is SmartRouteResult.TimerAction)
        assertEquals(50, (r50min as SmartRouteResult.TimerAction).durationMinutes)

        val r10mins = DesktopSmartRouter.routeQuick("10 minutes")
        assertTrue(r10mins is SmartRouteResult.TimerAction)
        assertEquals(10, (r10mins as SmartRouteResult.TimerAction).durationMinutes)
    }

    @Test
    fun testTimerKeywordsWithNumbers() {
        val r1 = DesktopSmartRouter.routeQuick("25m timer")
        assertTrue(r1 is SmartRouteResult.TimerAction)
        assertEquals(25, (r1 as SmartRouteResult.TimerAction).durationMinutes)

        val r2 = DesktopSmartRouter.routeQuick("25 min timer")
        assertTrue(r2 is SmartRouteResult.TimerAction)
        assertEquals(25, (r2 as SmartRouteResult.TimerAction).durationMinutes)

        val r3 = DesktopSmartRouter.routeQuick("timer 25")
        assertTrue(r3 is SmartRouteResult.TimerAction)
        assertEquals(25, (r3 as SmartRouteResult.TimerAction).durationMinutes)

        val r4 = DesktopSmartRouter.routeQuick("timer 15m")
        assertTrue(r4 is SmartRouteResult.TimerAction)
        assertEquals(15, (r4 as SmartRouteResult.TimerAction).durationMinutes)

        val r5 = DesktopSmartRouter.routeQuick("set a timer for 15m")
        assertTrue(r5 is SmartRouteResult.TimerAction)
        assertEquals(15, (r5 as SmartRouteResult.TimerAction).durationMinutes)

        val r6 = DesktopSmartRouter.routeQuick("set timer 30m")
        assertTrue(r6 is SmartRouteResult.TimerAction)
        assertEquals(30, (r6 as SmartRouteResult.TimerAction).durationMinutes)

        val r7 = DesktopSmartRouter.routeQuick("set a timer for 20 minutes")
        assertTrue(r7 is SmartRouteResult.TimerAction)
        assertEquals(20, (r7 as SmartRouteResult.TimerAction).durationMinutes)
    }

    @Test
    fun testGenericTimerAndSparkKeywords() {
        val rTimer = DesktopSmartRouter.routeQuick("timer")
        assertTrue(rTimer is SmartRouteResult.TimerAction)
        assertEquals(25, (rTimer as SmartRouteResult.TimerAction).durationMinutes)

        val rStartTimer = DesktopSmartRouter.routeQuick("start timer")
        assertTrue(rStartTimer is SmartRouteResult.TimerAction)
        assertEquals(25, (rStartTimer as SmartRouteResult.TimerAction).durationMinutes)

        val rPomodoro = DesktopSmartRouter.routeQuick("pomodoro")
        assertTrue(rPomodoro is SmartRouteResult.TimerAction)
        assertEquals(25, (rPomodoro as SmartRouteResult.TimerAction).durationMinutes)

        val rSpark = DesktopSmartRouter.routeQuick("spark")
        assertTrue(rSpark is SmartRouteResult.TimerAction)
        assertEquals(3, (rSpark as SmartRouteResult.TimerAction).durationMinutes)

        val r3Spark = DesktopSmartRouter.routeQuick("3m spark")
        assertTrue(r3Spark is SmartRouteResult.TimerAction)
        assertEquals(3, (r3Spark as SmartRouteResult.TimerAction).durationMinutes)
    }

    @Test
    fun testHourTimers() {
        val r1h = DesktopSmartRouter.routeQuick("1 hour")
        assertTrue(r1h is SmartRouteResult.TimerAction)
        assertEquals(60, (r1h as SmartRouteResult.TimerAction).durationMinutes)

        val r2h = DesktopSmartRouter.routeQuick("2 hours")
        assertTrue(r2h is SmartRouteResult.TimerAction)
        assertEquals(120, (r2h as SmartRouteResult.TimerAction).durationMinutes)

        val r1hrTimer = DesktopSmartRouter.routeQuick("1 hr timer")
        assertTrue(r1hrTimer is SmartRouteResult.TimerAction)
        assertEquals(60, (r1hrTimer as SmartRouteResult.TimerAction).durationMinutes)
    }

    @Test
    fun testTaskAnchoredTimers() {
        val r = DesktopSmartRouter.routeQuick("write essay for 25m")
        assertTrue(r is SmartRouteResult.TimerAction)
        val timer = r as SmartRouteResult.TimerAction
        assertEquals(25, timer.durationMinutes)
        assertEquals("Write essay", timer.taskTitle)

        val r2 = DesktopSmartRouter.routeQuick("code project 45 min")
        assertTrue(r2 is SmartRouteResult.TimerAction)
        val timer2 = r2 as SmartRouteResult.TimerAction
        assertEquals(45, timer2.durationMinutes)
        assertEquals("Code project", timer2.taskTitle)
    }

    @Test
    fun testBlockerActions() {
        val rBlock = DesktopSmartRouter.routeQuick("block discord")
        assertTrue(rBlock is SmartRouteResult.BlockerAction)
        val blocker = rBlock as SmartRouteResult.BlockerAction
        assertEquals("discord", blocker.target)
        assertTrue(blocker.enable)

        val rUnblock = DesktopSmartRouter.routeQuick("unblock steam")
        assertTrue(rUnblock is SmartRouteResult.BlockerAction)
        val unblocker = rUnblock as SmartRouteResult.BlockerAction
        assertEquals("steam", unblocker.target)
        assertTrue(!unblocker.enable)
    }

    @Test
    fun testResetDataActions() {
        val rTasks = DesktopSmartRouter.routeQuick("reset data")
        assertTrue(rTasks is SmartRouteResult.ResetDataAction)
        assertEquals(ResetType.CLEAR_TASKS, (rTasks as SmartRouteResult.ResetDataAction).resetType)

        val rStreak = DesktopSmartRouter.routeQuick("reset streak")
        assertTrue(rStreak is SmartRouteResult.ResetDataAction)
        assertEquals(ResetType.RESET_STREAK, (rStreak as SmartRouteResult.ResetDataAction).resetType)

        val rFactory = DesktopSmartRouter.routeQuick("factory reset")
        assertTrue(rFactory is SmartRouteResult.ResetDataAction)
        assertEquals(ResetType.FACTORY_RESET, (rFactory as SmartRouteResult.ResetDataAction).resetType)
    }

    @Test
    fun testOverwhelmGrounding() {
        val r = DesktopSmartRouter.routeQuick("I've been scrolling reddit for 2 hours and feel overwhelmed and ruined my day")
        assertTrue(r is SmartRouteResult.GroundingReset)
    }

    @Test
    fun testBrainDump() {
        val r = DesktopSmartRouter.routeQuick("buy milk, call dentist, review report, check email")
        assertTrue(r is SmartRouteResult.BrainDump)
        val dump = r as SmartRouteResult.BrainDump
        assertTrue(dump.tasks.size >= 4)
    }

    @Test
    fun testTaskBreakdown() {
        val r = DesktopSmartRouter.routeQuick("Prepare slides for board meeting")
        assertTrue(r is SmartRouteResult.TaskBreakdown)
        val breakdown = r as SmartRouteResult.TaskBreakdown
        assertNotNull(breakdown.step1Starter)
    }
}
