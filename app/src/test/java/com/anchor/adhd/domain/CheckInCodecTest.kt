package com.anchor.adhd.domain

import com.anchor.adhd.data.model.CheckInTag
import com.anchor.adhd.data.model.EnergyLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckInCodecTest {

    @Test
    fun formatAndParseTags_roundTrip() {
        val tags = setOf(CheckInTag.FOGGY, CheckInTag.OVERWHELMED)
        val raw = CheckInCodec.formatTags(tags)
        assertEquals(setOf(CheckInTag.FOGGY, CheckInTag.OVERWHELMED), CheckInCodec.parseTags(raw))
    }

    @Test
    fun parseTags_mapsLegacyRestlessToScattered() {
        assertEquals(setOf(CheckInTag.SCATTERED), CheckInCodec.parseTags("RESTLESS"))
    }

    @Test
    fun parseTags_blankReturnsEmpty() {
        assertTrue(CheckInCodec.parseTags("").isEmpty())
    }

    @Test
    fun formatCheckInDetail_includesActivationAndTags() {
        val detail = CheckInCodec.formatCheckInDetail(
            EnergyLevel.LOW,
            setOf(CheckInTag.FOGGY)
        )
        assertEquals("Drained · Foggy", detail)
    }
}
