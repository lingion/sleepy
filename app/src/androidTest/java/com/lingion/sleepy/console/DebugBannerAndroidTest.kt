package com.lingion.sleepy.console

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.lingion.sleepy.console.ui.TagRegistry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Task 7 Step 3（设备侧降级面）：红条出现/消失 + 一键失活语义。
 * 截图眼验归档 Task 8 验收清单；这里用 Compose 语义断言钉形态 —
 * DebugModeBanner 直接渲染（生产接入点是 MainActivity Box 顶层一行）。
 */
class DebugBannerAndroidTest {

    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun tearDown() {
        DebugGate.reset()
        TokenStore.reset()
    }

    @Test
    fun releaseUnlocked_bannerVisible_andDeactivateTagged() {
        DebugGate.activate(debugBuild = false, releaseUnlocked = true)
        composeRule.setContent {
            Box(Modifier.fillMaxSize()) { DebugModeBanner() }
        }
        composeRule.onNodeWithText("DEBUG UNLOCKED").assertExists()
        composeRule.onNodeWithTag("debug_banner_deactivate").assertExists()
        assertNotNull(TagRegistry.snapshot().firstOrNull { it.tag == "debug_banner_deactivate" })
    }

    @Test
    fun lockedOrDebug_bannerAbsent() {
        DebugGate.activate(debugBuild = false, releaseUnlocked = false)
        composeRule.setContent {
            Box(Modifier.fillMaxSize()) { DebugModeBanner() }
        }
        composeRule.onNodeWithText("DEBUG UNLOCKED").assertDoesNotExist()
    }

    @Test
    fun debugBuild_bannerNeverShown_debugAlwaysOnIsNormal() {
        DebugGate.activate(debugBuild = true, releaseUnlocked = true)
        composeRule.setContent {
            Box(Modifier.fillMaxSize()) { DebugModeBanner() }
        }
        composeRule.onNodeWithText("DEBUG UNLOCKED").assertDoesNotExist()
    }

    /** 失活纯语义在 JVM ReleaseGateUnitTest 已锁; 这里锁"按钮可点且打过标"。 */
    @Test
    fun deactivateButton_isClickableRegisteredNode() {
        DebugGate.activate(debugBuild = false, releaseUnlocked = true)
        composeRule.setContent {
            Box(Modifier.fillMaxSize()) { DebugModeBanner() }
        }
        val node = TagRegistry.snapshot().firstOrNull { it.tag == "debug_banner_deactivate" }
        assertNotNull("失活按钮必须注册进语义表", node)
        assertTrue("按钮 clickable=true", node!!.clickable)
    }
}
