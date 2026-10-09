package com.anchor.adhd.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import com.anchor.adhd.MainActivity
import com.anchor.adhd.data.model.BlockListMode
import com.anchor.adhd.data.model.BlockRuleEntity
import com.anchor.adhd.data.model.TemptationBundleEntity
import com.anchor.adhd.ui.focus.ShieldRescueOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.anchor.adhd.AnchorApp
import com.anchor.adhd.domain.AdvancedBlocking
import com.anchor.adhd.domain.BlockingState
import android.os.PowerManager
import android.os.SystemClock
import android.app.KeyguardManager

class FocusBlockService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var composeHost: ServiceComposeHost? = null
    private var currentBlockedPkg by mutableStateOf<String?>(null)
    private var foregroundPackage: String? = null
    private var protection by mutableStateOf(BlockingState())
    private val container get() = (application as AnchorApp).container

    private var temporaryPassPackage: String? = null
    private var temporaryPassExpiryMillis: Long = 0L

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var debounceJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager

        // Hydrate rules even when Accessibility starts before the app's UI.
        serviceScope.launch { container.advancedBlocker.state.collect { protection = it; reevaluate() } }
        serviceScope.launch { container.focusRepository.observeBlockRules().collect { container.focusRepository.refreshBlockedPackages(); reevaluate() } }
        serviceScope.launch { container.focusRepository.observeBundles().collect { container.focusRepository.refreshBlockedPackages(); reevaluate() } }
        serviceScope.launch { container.preferences.blockListModeFlow.collect { container.focusRepository.refreshBlockedPackages(); reevaluate() } }
        serviceScope.launch {
            FocusTimerState.state.collect {
                sessionActive = it.phase == FocusTimerState.Phase.WORK && it.locked
                reevaluate()
            }
        }
        // Check limits, pass expiry and schedules while the same app remains open.
        serviceScope.launch {
            var lastTick = SystemClock.elapsedRealtime()
            while (true) {
                delay(1000)
                val elapsed = SystemClock.elapsedRealtime()
                val seconds = ((elapsed - lastTick) / 1000).coerceIn(0, 5)
                lastTick = elapsed
                val awake = getSystemService(PowerManager::class.java).isInteractive &&
                    !getSystemService(KeyguardManager::class.java).isKeyguardLocked
                if (awake && overlayView == null) {
                    rootInActiveWindow?.let { root ->
                        foregroundPackage = root.packageName?.toString()
                        @Suppress("DEPRECATION")
                        root.recycle()
                    }
                }
                container.advancedBlocker.tick(if (awake && overlayView == null) foregroundPackage else null, seconds)
                if (awake) reevaluate() else removeOverlay()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        debounceJob?.cancel()

        // Ignore Anchor itself and system UI
        if (pkg == packageName || pkg == "com.android.systemui") {
            // An overlay belongs to Anchor; keep the underlying app while it is visible.
            if (event.className?.toString()?.contains("ShieldOverlayContainer") == true) return
            if (pkg == packageName && overlayView != null) {
                // Only the real activity is an app switch, not our service overlay.
                if (event.className?.toString() != MainActivity::class.java.name) return
                removeOverlay()
            }
            foregroundPackage = pkg
            return
        }
        foregroundPackage = pkg

        // 250ms debouncing to prevent flickering during rapid window transitions
        debounceJob = serviceScope.launch {
            delay(250)
            if (foregroundPackage == pkg && getSystemService(PowerManager::class.java).isInteractive &&
                !getSystemService(KeyguardManager::class.java).isKeyguardLocked) evaluateWindowChange(pkg)
        }
    }

    private fun evaluateWindowChange(pkg: String) {
        // Check temporary 60s emergency pass
        val advanced = AdvancedBlocking.decide(protection, pkg, sessionActive, System.currentTimeMillis())
        if ((advanced == null || advanced.leisureAllowed) && temporaryPassPackage == pkg && System.currentTimeMillis() < temporaryPassExpiryMillis) {
            if (overlayView != null) {
                removeOverlay()
            }
            return
        }

        if (shouldBlock(pkg)) {
            if (currentBlockedPkg != pkg) recordBlock(pkg)
            showOverlay(pkg)
        } else {
            // Unblocked application in foreground
            if (overlayView != null) {
                removeOverlay()
            }
        }
    }

    private fun reevaluate() { foregroundPackage?.takeIf { it != packageName && it != "com.android.systemui" }?.let { evaluateWindowChange(it) } }

    private fun showOverlay(pkg: String) {
        if (overlayView != null) {
            currentBlockedPkg = pkg
            return
        }

        val wm = windowManager ?: getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        windowManager = wm
        currentBlockedPkg = pkg

        val host = ServiceComposeHost(
            onBackPressed = { returnToAnchor() }
        )
        composeHost = host

        val container = ShieldOverlayContainer(this, onBackAction = { returnToAnchor() })
        val composeView = ComposeView(this).apply {
            host.attach(this)
            setContent {
                val timerState by FocusTimerState.state.collectAsState()
                val remainingMins = (timerState.remainingSeconds / 60).coerceAtLeast(1)
                val decision = AdvancedBlocking.decide(protection, currentBlockedPkg ?: pkg, sessionActive, System.currentTimeMillis())
                ShieldRescueOverlay(
                    blockedPackage = currentBlockedPkg ?: pkg,
                    taskTitle = timerState.taskTitle,
                    remainingMinutes = remainingMins,
                    onReturnToFocus = { returnToAnchor() },
                    onEmergencyPass = { grantedPkg ->
                        grantEmergencyPass(grantedPkg, 60_000L)
                    },
                    protectionReason = decision?.reason,
                    emergencyPassAllowed = decision == null || decision.leisureAllowed,
                    bankedMinutes = protection.bankedMinutes,
                    onSpendLeisure = if (decision?.leisureAllowed == true) ({
                        serviceScope.launch {
                            if (this@FocusBlockService.container.advancedBlocker.spendLeisure(15, sessionActive)) removeOverlay()
                        }
                    }) else null
                )
            }
        }
        container.addView(composeView)
        host.attach(container)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            title = "AnchorGentleShield"
        }

        try {
            wm.addView(container, params)
            overlayView = container
        } catch (e: Exception) {
            Log.e("FocusBlockService", "Failed to add WindowManager overlay", e)
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    private fun removeOverlay() {
        val view = overlayView
        if (view != null) {
            try {
                windowManager?.removeViewImmediate(view)
            } catch (e: Exception) {
                // View might already be detached
            }
        }
        overlayView = null
        currentBlockedPkg = null
        composeHost?.destroy()
        composeHost = null
    }

    fun returnToAnchor() {
        removeOverlay()
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_FOCUS)
        }
        startActivity(intent)
    }

    private fun grantEmergencyPass(pkg: String, durationMillis: Long) {
        val decision = AdvancedBlocking.decide(protection, pkg, sessionActive, System.currentTimeMillis())
        if (decision != null && !decision.leisureAllowed) return
        temporaryPassPackage = pkg
        temporaryPassExpiryMillis = System.currentTimeMillis() + durationMillis
        removeOverlay()
    }

    private fun shouldBlock(pkg: String): Boolean {
        if (pkg in com.anchor.adhd.domain.WhitelistBasics.essentialPackages) return false
        return AdvancedBlocking.decide(protection, pkg, sessionActive, System.currentTimeMillis()) != null || BlockRuleEvaluator.shouldBlock(
            packageName = pkg,
            rules = blockRules,
            bundles = temptationBundles,
            sessionActive = sessionActive,
            blockListMode = blockListMode,
            whitelistPackages = whitelistPackages
        )
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        removeOverlay()
        serviceScope.cancel()
        FocusBlockService.sessionActive = false
        instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var sessionActive: Boolean = false

        @Volatile
        var blockRules: List<BlockRuleEntity> = emptyList()

        @Volatile
        var temptationBundles: List<TemptationBundleEntity> = emptyList()

        @Volatile
        var scheduledShieldActive: Boolean = false

        @Volatile
        var blockListMode: BlockListMode = BlockListMode.BLOCKLIST

        @Volatile
        var whitelistPackages: Set<String> = emptySet()

        private val blockedAttemptsCount = java.util.concurrent.atomic.AtomicInteger(0)

        var blockedAttempts: Int
            get() = blockedAttemptsCount.get()
            set(value) { blockedAttemptsCount.set(value) }

        @Volatile
        var topBlockedPackage: String? = null

        private val packageHits = java.util.concurrent.ConcurrentHashMap<String, Int>()

        fun recordBlock(pkg: String) {
            blockedAttemptsCount.incrementAndGet()
            val n = (packageHits[pkg] ?: 0) + 1
            packageHits[pkg] = n
            topBlockedPackage = packageHits.entries.maxByOrNull { it.value }?.key
        }

        fun resetSessionBlocks() {
            blockedAttemptsCount.set(0)
            topBlockedPackage = null
            packageHits.clear()
        }

        @Volatile
        private var instance: FocusBlockService? = null

        fun isEnabled(): Boolean = instance != null

        fun openSettings(context: Context) {
            context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        }
    }
}

private class ShieldOverlayContainer(
    context: Context,
    private val onBackAction: () -> Unit
) : FrameLayout(context) {

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) {
                onBackAction()
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
