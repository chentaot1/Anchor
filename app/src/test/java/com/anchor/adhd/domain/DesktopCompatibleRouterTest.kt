package com.anchor.adhd.domain

import org.junit.Assert.*
import org.junit.Test

class DesktopCompatibleRouterTest {
    @Test fun naturalTimerCommandsKeepTheirRequestedDuration() {
        mapOf("start a 10m timer" to 10, "timer 25" to 25, "3m" to 3,
            "focus 45 min" to 45, "1 hour timer" to 60, "2 hours" to 120,
            "spark" to 3, "start timer" to 25).forEach { (input, minutes) ->
            val route = DesktopCompatibleRouter.routeQuick(input)
            assertTrue("$input should configure a timer instead of creating a task", route is SmartRouteResult.TimerAction)
            assertEquals(input, minutes, (route as SmartRouteResult.TimerAction).durationMinutes)
        }
    }

    @Test fun embeddedTimerRetainsTheTask() {
        val route = DesktopCompatibleRouter.routeQuick("write essay for 25m") as SmartRouteResult.TimerAction
        assertEquals(25, route.durationMinutes)
        assertEquals("Write essay", route.taskTitle)
    }

    @Test fun explicitBlockerCommandWinsOverTaskCapture() {
        val block = DesktopCompatibleRouter.routeQuick("block youtube") as SmartRouteResult.BlockerAction
        assertTrue(block.enable)
        assertEquals("youtube", block.target)
        assertFalse((DesktopCompatibleRouter.routeQuick("unblock youtube") as SmartRouteResult.BlockerAction).enable)
    }

    @Test fun overwhelmOffersShortResetInsteadOfCapturingTheComplaintAsATask() {
        val route = DesktopCompatibleRouter.routeQuick("I'm overwhelmed and can't focus") as SmartRouteResult.GroundingReset
        assertEquals(3, route.suggestedMinutes)
    }

    @Test fun destructiveRequestsAreRoutedToReview() {
        assertTrue(DesktopCompatibleRouter.routeQuick("factory reset") is SmartRouteResult.ResetDataAction)
    }

    @Test fun explicitBreakdownCueDistinguishesPlainTasksFromBreakdownRequests() {
        assertFalse(DesktopCompatibleRouter.hasExplicitBreakdownCue("buy milk"))
        assertFalse(DesktopCompatibleRouter.hasExplicitBreakdownCue("call dentist tomorrow"))
        assertFalse(DesktopCompatibleRouter.hasExplicitBreakdownCue("write essay"))
        assertTrue(DesktopCompatibleRouter.hasExplicitBreakdownCue("break down my biology paper"))
        assertTrue(DesktopCompatibleRouter.hasExplicitBreakdownCue("breakdown my taxes"))
        assertTrue(DesktopCompatibleRouter.hasExplicitBreakdownCue("steps for cleaning the kitchen"))
        assertTrue(DesktopCompatibleRouter.hasExplicitBreakdownCue("how to start my lab report"))
    }
}
