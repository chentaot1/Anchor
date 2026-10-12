package com.anchor.adhd

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.anchor.adhd.data.model.EnergyLevel
import com.anchor.adhd.ui.AnchorRoot
import com.anchor.adhd.ui.theme.AnchorTheme

class MainActivity : ComponentActivity() {
    private var launchParams by mutableStateOf(LaunchParams())
    private var intentVersion by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        launchParams = readIntent(intent)
        intentVersion = 0

        val container = (application as AnchorApp).container

        setContent {
            val params = launchParams
            AnchorTheme {
                AnchorRoot(
                    container = container,
                    initialInboxText = params.sharedText,
                    startFocusOnLaunch = params.startFocus,
                    focusTaskId = params.focusTaskId,
                    initialTab = params.openTab,
                    initialPlanTab = params.planTab,
                    openMorningRitual = params.openMorningRitual,
                    openShutdown = params.openShutdown,
                    openCheckIn = params.openCheckIn,
                    checkInEnergy = params.checkInEnergy,
                    intentVersion = intentVersion
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchParams = readIntent(intent)
        intentVersion++
    }

    private fun readIntent(intent: Intent?): LaunchParams {
        val sharedText = when {
            intent?.action == Intent.ACTION_SEND &&
                intent.type == "text/plain" -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        }
        val openTab = when {
            intent?.action == ACTION_OPEN_REPLAN -> TAB_MORE
            intent?.action == ACTION_CHECK_IN -> TAB_GROVE
            intent?.action == ACTION_SHUTDOWN -> TAB_GROVE
            intent?.hasExtra(EXTRA_OPEN_TAB) == true -> {
                val raw = intent.getIntExtra(EXTRA_OPEN_TAB, TAB_GROVE)
                when (raw) {
                    4 -> TAB_AI // legacy AI tab
                    3 -> TAB_MORE // legacy Grow tab
                    else -> raw.coerceIn(TAB_GROVE, TAB_MORE)
                }
            }
            else -> null
        }
        val planTab = when {
            intent?.action == ACTION_OPEN_REPLAN -> PLAN_TAB_REPLAN
            intent?.hasExtra(EXTRA_PLAN_TAB) == true -> intent.getIntExtra(EXTRA_PLAN_TAB, 0)
            else -> null
        }
        val checkInEnergy = intent?.getStringExtra(EXTRA_ENERGY)?.let { raw ->
            runCatching { EnergyLevel.valueOf(raw.uppercase()) }.getOrNull()
        }
        return LaunchParams(
            sharedText = sharedText,
            startFocus = intent?.getBooleanExtra(EXTRA_START_FOCUS, false) == true ||
                intent?.action == ACTION_START_FOCUS,
            focusTaskId = intent?.getLongExtra(EXTRA_TASK_ID, -1L)?.takeIf { it >= 0 },
            openTab = openTab,
            planTab = planTab,
            openMorningRitual = intent?.action == ACTION_OPEN_MORNING_RITUAL,
            openShutdown = intent?.action == ACTION_SHUTDOWN,
            openCheckIn = intent?.action == ACTION_CHECK_IN,
            checkInEnergy = checkInEnergy
        )
    }

    data class LaunchParams(
        val sharedText: String? = null,
        val startFocus: Boolean = false,
        val focusTaskId: Long? = null,
        val openTab: Int? = null,
        val planTab: Int? = null,
        val openMorningRitual: Boolean = false,
        val openShutdown: Boolean = false,
        val openCheckIn: Boolean = false,
        val checkInEnergy: EnergyLevel? = null
    )

    companion object {
        const val ACTION_START_FOCUS = "com.anchor.adhd.START_FOCUS"
        const val ACTION_OPEN_REPLAN = "com.anchor.adhd.OPEN_REPLAN"
        const val ACTION_OPEN_MORNING_RITUAL = "com.anchor.adhd.OPEN_MORNING_RITUAL"
        const val ACTION_CHECK_IN = "com.anchor.adhd.CHECK_IN"
        const val ACTION_SHUTDOWN = "com.anchor.adhd.SHUTDOWN"
        const val EXTRA_START_FOCUS = "start_focus"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_OPEN_TAB = "open_tab"
        const val EXTRA_PLAN_TAB = "plan_tab"
        const val EXTRA_ENERGY = "energy"
        const val TAB_GROVE = 0
        const val TAB_TODAY = 0
        const val TAB_AI = 1
        @Deprecated("Planning now lives in More")
        const val TAB_PLAN = TAB_AI
        const val TAB_FOCUS = 2
        const val TAB_MORE = 3
        const val TAB_GROW = 3
        const val PLAN_TAB_REPLAN = 2
    }
}
