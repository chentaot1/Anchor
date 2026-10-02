package com.anchor.adhd.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anchor.adhd.domain.*
import com.anchor.adhd.service.FocusTimerState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [34])
class AdvancedBlockerRepositoryTest {
    @Test fun persistedLimitsLocksAndAtomicLeisureSpending() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = AdvancedBlockerRepository(context)
        repository.clear()
        FocusTimerState.reset()
        val app = AppProtection("social.app", "Social", ProtectionMode.ALWAYS)
        try {
            repository.saveApp(app)
            repository.tick(app.packageName, 5)
            assertEquals(5L, AdvancedBlockerRepository(context).state.first().usageSeconds[app.packageName])
            repository.lockApp(app.packageName)
            repository.removeApp(app.packageName)
            repository.saveApp(app.copy(mode = ProtectionMode.TRACK_ONLY))
            assertEquals(ProtectionMode.ALWAYS, repository.state.first().apps.single().mode)
            repository.creditSession(1, 60)
            repository.configure { it.copy(curfewEnabled = true, curfewStartMinute = 0, curfewEndMinute = 0) }
            assertFalse(repository.spendLeisure(15, false))
            assertEquals(30, repository.state.first().bankedMinutes)
            repository.configure { it.copy(curfewEnabled = false) }
            assertTrue(repository.spendLeisure(15, false))
            assertFalse(repository.spendLeisure(15, false))
            assertEquals(15, repository.state.first().bankedMinutes)
            repository.lockdown()
            repository.configure { it.copy(standingShield = true) }
            assertFalse(repository.state.first().standingShield)
            assertFalse(repository.spendLeisure(15, false))
            assertEquals(0L, repository.state.first().leisureUntilMillis)
        } finally { repository.clear(); FocusTimerState.reset() }
    }
}
