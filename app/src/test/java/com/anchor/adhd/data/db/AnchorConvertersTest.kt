package com.anchor.adhd.data.db

import com.anchor.adhd.data.model.CbtMomentTag
import com.anchor.adhd.data.model.EnergyLevel
import com.anchor.adhd.data.model.FocusEndTag
import com.anchor.adhd.data.model.InboxState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnchorConvertersTest {
    private val converters = AnchorConverters()

    @Test
    fun unknownEnumValues_fallBackSafely() {
        assertEquals(InboxState.TODAY, converters.toInboxState("NOT_A_REAL_STATE"))
        assertEquals(EnergyLevel.OK, converters.toEnergy("???"))
        assertNull(converters.toFocusEndTag("INVALID_TAG"))
    }

    @Test
    fun knownEnumValues_roundTrip() {
        assertEquals(FocusEndTag.STUCK, converters.toFocusEndTag(FocusEndTag.STUCK.name))
        assertEquals(CbtMomentTag.AVOIDING, converters.toCbtMomentTag(CbtMomentTag.AVOIDING.name))
    }
}
