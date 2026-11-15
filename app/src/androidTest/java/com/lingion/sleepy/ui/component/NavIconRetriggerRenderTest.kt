package com.lingion.sleepy.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavIconRetriggerRenderTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var driver: NavIconDriver
    private lateinit var scope: CoroutineScope

    @Test
    fun retriggerDuringPulse_keepsCurrentPhaseAndLetsItSettle() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            driver = remember { NavIconDriver(NavIconMotionKey.PageFlip) }
            scope = rememberCoroutineScope()
            Box(Modifier.size(1.dp))
        }

        compose.runOnIdle { scope.launch { driver.activate() } }
        compose.mainClock.advanceTimeBy(220)
        val beforeRetrigger = compose.runOnIdle { driver.pulsePhase() }
        assertTrue("first pulse should be in flight", beforeRetrigger in 0.1f..0.95f)

        compose.runOnIdle { scope.launch { driver.activate() } }
        compose.mainClock.advanceTimeByFrame()
        val afterRetrigger = compose.runOnIdle { driver.pulsePhase() }
        assertTrue(
            "retrigger must continue the current phase instead of jumping to its peak",
            afterRetrigger < beforeRetrigger,
        )

        compose.mainClock.advanceTimeBy(700)
        val settled = compose.runOnIdle { driver.pulsePhase() }
        assertTrue("pulse should return to rest", settled < 0.001f)
    }
}
