package com.anchor.adhd.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatIntentRouterTest {

    @Test
    fun capture_plainList_returnsMultipleTasks() {
        val intent = ChatIntentRouter.route("buy milk, call mom, finish lab writeup")
        assertTrue(intent is ChatIntent.CaptureTasks)
        val capture = intent as ChatIntent.CaptureTasks
        assertEquals(listOf("buy milk", "call mom", "finish lab writeup"), capture.titles)
    }

    @Test
    fun capture_withAnd_splits() {
        val intent = ChatIntentRouter.route("email prof and buy snacks")
        assertTrue(intent is ChatIntent.CaptureTasks)
        assertEquals(2, (intent as ChatIntent.CaptureTasks).titles.size)
    }

    @Test
    fun whatsToday_returnsIntent() {
        assertTrue(ChatIntentRouter.route("what's on my plate today?") is ChatIntent.WhatsToday)
        assertTrue(ChatIntentRouter.route("what is next") is ChatIntent.WhatsToday)
    }

    @Test
    fun remind_returnsRemindWithTime() {
        val intent = ChatIntentRouter.route("remind me at 5pm to submit the form")
        assertTrue(intent is ChatIntent.Remind)
        val remind = intent as ChatIntent.Remind
        assertEquals("submit the form", remind.title)
        assertTrue(remind.atMillis != null)
    }

    @Test
    fun remind_withoutTime_leavesTitle() {
        val intent = ChatIntentRouter.route("remind me to drink water")
        assertTrue(intent is ChatIntent.Remind)
        assertEquals("drink water", (intent as ChatIntent.Remind).title)
    }

    @Test
    fun markDone_returnsQuery() {
        val intent = ChatIntentRouter.route("mark done the essay")
        assertTrue(intent is ChatIntent.MarkDone)
    }

    @Test
    fun moveSomeday_returnsQuery() {
        assertTrue(ChatIntentRouter.route("move laundry to someday") is ChatIntent.MoveSomeday)
    }

    @Test
    fun breakdown_returnsQuery() {
        val intent = ChatIntentRouter.route("break down my essay")
        assertTrue(intent is ChatIntent.Breakdown)
    }

    @Test
    fun triage_returns() {
        assertTrue(ChatIntentRouter.route("triage my inbox") is ChatIntent.Triage)
    }

    @Test
    fun replan_returns() {
        assertTrue(ChatIntentRouter.route("I missed everything today") is ChatIntent.Replan)
    }

    @Test
    fun empty_returnsChat() {
        assertTrue(ChatIntentRouter.route("   ") is ChatIntent.Chat)
    }

    @Test
    fun ambiguousShort_returnsClarifyWithPendingTitle() {
        val intent = ChatIntentRouter.route("essay")
        assertTrue(intent is ChatIntent.Clarify)
        val clarify = intent as ChatIntent.Clarify
        assertEquals("essay", clarify.pendingTitle)
    }

    @Test
    fun brainDump_empty_asksToPaste() {
        val intent = ChatIntentRouter.route("brain dump")
        assertTrue(intent is ChatIntent.Clarify)
    }

    @Test
    fun brainDump_withText_returnsDump() {
        val intent = ChatIntentRouter.route("brain dump: milk, call mom, finish lab")
        assertTrue(intent is ChatIntent.BrainDump)
        assertEquals("milk, call mom, finish lab", (intent as ChatIntent.BrainDump).text)
    }

    @Test
    fun snooze_returnsSnoozeQuery() {
        val intent = ChatIntentRouter.route("snooze that")
        assertTrue(intent is ChatIntent.Snooze)
    }

    @Test
    fun help_isNotCapture() {
        val intent = ChatIntentRouter.route("help")
        assertTrue(intent is ChatIntent.Chat)
        assertTrue(intent !is ChatIntent.CaptureTasks)
    }

    @Test
    fun overwhelmed_isNotCapture() {
        val intent = ChatIntentRouter.route("I'm overwhelmed")
        assertTrue(intent !is ChatIntent.CaptureTasks)
        assertTrue(intent is ChatIntent.Chat)
    }

    @Test
    fun stuckOnEssay_isChatNotCapture() {
        val intent = ChatIntentRouter.route("I'm stuck on my essay")
        assertTrue(intent is ChatIntent.Chat)
        assertTrue(ChatIntentRouter.shouldWakeModel("I'm stuck on my essay"))
    }

    @Test
    fun completedWorkout_isNotMarkDone() {
        val intent = ChatIntentRouter.route("I completed my workout")
        assertTrue(intent !is ChatIntent.MarkDone)
        assertTrue(intent !is ChatIntent.CaptureTasks)
    }

    @Test
    fun laterTodayClass_isNotSnooze() {
        val intent = ChatIntentRouter.route("later today I have class")
        assertTrue(intent !is ChatIntent.Snooze)
        assertTrue(intent !is ChatIntent.MoveSomeday)
        assertTrue(intent !is ChatIntent.CaptureTasks)
    }

    @Test
    fun delimiterList_stillCaptures() {
        val intent = ChatIntentRouter.route("buy milk, call mom")
        assertTrue(intent is ChatIntent.CaptureTasks)
        assertEquals(listOf("buy milk", "call mom"), (intent as ChatIntent.CaptureTasks).titles)
    }

    @Test
    fun imperativeSingle_stillCaptures() {
        val intent = ChatIntentRouter.route("buy milk")
        assertTrue(intent is ChatIntent.CaptureTasks)
        assertEquals(listOf("buy milk"), (intent as ChatIntent.CaptureTasks).titles)
    }

    @Test
    fun completeTheEssay_stillMarkDone() {
        assertTrue(ChatIntentRouter.route("complete the essay") is ChatIntent.MarkDone)
    }

    @Test
    fun ok_doesNotWakeModel() {
        val intent = ChatIntentRouter.route("ok")
        assertTrue(intent is ChatIntent.Chat)
        assertTrue(!ChatIntentRouter.shouldWakeModel("ok"))
        assertTrue(!ChatIntentRouter.shouldWakeModel("thanks"))
        assertTrue(!ChatIntentRouter.shouldWakeModel("thank you"))
        assertTrue(!ChatIntentRouter.shouldWakeModel("   "))
    }

    @Test
    fun pickOne_andHaveMinutes_routeWithoutModel() {
        assertTrue(ChatIntentRouter.route("pick one") is ChatIntent.PickOne)
        val thirty = ChatIntentRouter.route("I have 30 minutes")
        assertTrue(thirty is ChatIntent.PickOne)
        assertEquals(30, (thirty as ChatIntent.PickOne).minutes)
    }
    @Test
    fun datedImperatives_routeToSchedule() {
        val zone = java.time.ZoneId.systemDefault()
        val base = java.time.LocalDate.of(2026, 9, 9)
        val now = base.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

        assertTrue(ChatIntentRouter.route("finish lab tomorrow", now) is ChatIntent.Schedule)
        assertTrue(ChatIntentRouter.route("add submit report on 9/12", now) is ChatIntent.Schedule)
    }

}
