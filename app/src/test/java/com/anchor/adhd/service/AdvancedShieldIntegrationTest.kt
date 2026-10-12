package com.anchor.adhd.service

import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.MutableState
import com.anchor.adhd.domain.AppProtection
import com.anchor.adhd.domain.BlockingState
import com.anchor.adhd.domain.ProtectionMode
import com.anchor.adhd.data.model.BlockRuleEntity
import com.anchor.adhd.data.model.BlockRuleType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [34])
class AdvancedShieldIntegrationTest {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun serviceCombinesNewAndLegacyRulesAndCancelsStaleAppSwitch() {
        Dispatchers.setMain(StandardTestDispatcher())
        val controller = Robolectric.buildService(FocusBlockService::class.java).create()
        val service = controller.get()
        val app = AppProtection("social.app", "Social", ProtectionMode.ALWAYS)
        val protectionField = service.javaClass.getDeclaredField("protection\$delegate").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val protection = protectionField.get(service) as MutableState<BlockingState>
        val shouldBlock = service.javaClass.getDeclaredMethod("shouldBlock", String::class.java).apply { isAccessible = true }
        try {
            FocusBlockService.sessionActive = false
            FocusBlockService.blockRules = emptyList()
            FocusBlockService.temptationBundles = emptyList()
            FocusBlockService.blockListMode = com.anchor.adhd.data.model.BlockListMode.BLOCKLIST
            protection.value = BlockingState(apps = listOf(app))
            assertTrue(shouldBlock.invoke(service, app.packageName) as Boolean)
            protection.value = protection.value.copy(leisureUntilMillis = System.currentTimeMillis() + 60_000)
            assertFalse(shouldBlock.invoke(service, app.packageName) as Boolean)
            FocusBlockService.blockRules = listOf(BlockRuleEntity(packageName = app.packageName, ruleType = BlockRuleType.SESSION))
            FocusBlockService.sessionActive = true
            assertTrue(shouldBlock.invoke(service, app.packageName) as Boolean)
            val essential = "com.samsung.android.dialer"
            protection.value = BlockingState(apps = listOf(app.copy(packageName = essential)), lockdownUntilMillis = Long.MAX_VALUE)
            FocusBlockService.blockRules = listOf(BlockRuleEntity(packageName = essential, ruleType = BlockRuleType.SESSION))
            assertFalse(shouldBlock.invoke(service, essential) as Boolean)

            val switch = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED).apply { packageName = app.packageName }
            service.onAccessibilityEvent(switch)
            val jobField = service.javaClass.getDeclaredField("debounceJob").apply { isAccessible = true }
            val pending = jobField.get(service) as Job
            val backToAnchor = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED).apply { packageName = service.packageName }
            service.onAccessibilityEvent(backToAnchor)
            assertTrue("An old app switch must not show an overlay over Anchor", pending.isCancelled)
        } finally {
            controller.destroy()
            FocusBlockService.blockRules = emptyList()
            FocusBlockService.sessionActive = false
            Dispatchers.resetMain()
        }
    }
}
