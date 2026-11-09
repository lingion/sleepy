package com.lingion.sleepy.console

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.lingion.sleepy.console.handlers.EnableFlow
import com.lingion.sleepy.ui.theme.SleepyThemeProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "zh-rCN-w360dp-h800dp")
class DebugUnlockDialogTest {
    @get:Rule val compose = createComposeRule()
    @After fun reset() { ConsoleBootstrap.stop(); DebugGate.reset(); TokenStore.reset(); CommandRouter.reset() }

    @Test fun wrongKeyThenCorrectKeyUsesReleaseValidation() {
        DebugGate.reset()
        TokenStore.reset(passphrase = "test-only-key")
        DebugGate.armUnlock()
        var unlocked = false
        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                DebugUnlockDialog(onDismiss = {}, onUnlocked = { unlocked = true },
                    prepare = { true }, unlock = { EnableFlow.attempt(it) })
            }
        }
        compose.onNodeWithTag("debug_unlock_key").performTextInput("wrong")
        compose.onNodeWithTag("debug_unlock_confirm").performClick()
        compose.waitUntil { compose.onAllNodesWithTag("debug_unlock_error").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("debug_unlock_error").assertTextEquals("密钥错误，请重试。")
        assertFalse(DebugGate.isActive)
        compose.onNodeWithTag("debug_unlock_key").performTextReplacement("test-only-key")
        compose.onNodeWithTag("debug_unlock_confirm").performClick()
        compose.waitUntil { unlocked }
        assertTrue(DebugGate.isReleaseUnlocked)
        assertNotNull(TokenStore.issue())
    }

    @Test fun releaseBannerReactsToActivationAndInvalidationWithoutNavigation() {
        DebugGate.reset()
        compose.setContent { DebugModeBanner() }
        compose.onNodeWithTag("debug_banner").assertDoesNotExist()
        compose.runOnIdle { DebugGate.activate(debugBuild = false, releaseUnlocked = true) }
        compose.onNodeWithTag("debug_banner").assertIsDisplayed()
        compose.runOnIdle { DebugGate.invalidate() }
        compose.onNodeWithTag("debug_banner").assertDoesNotExist()
    }

    @Test fun httpUnlockCompletesAnAlreadyOpenLocalDialog() {
        DebugGate.reset()
        TokenStore.reset(passphrase = "test-only-key")
        DebugGate.armUnlock()
        ConsoleBootstrap.registerDefaultHandlers()
        var unlocked = false
        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                DebugUnlockDialog(onDismiss = {}, onUnlocked = { unlocked = true }, prepare = { true })
            }
        }
        compose.waitForIdle()
        val response = runBlocking {
            CommandRouter.dispatch("/v1/enable", "POST", ConsoleRequest("""{"passphrase":"test-only-key"}"""))
        }
        assertTrue(response.contains("\"result\":\"OK\""))
        assertTrue(DebugGate.isActive)
        compose.waitForIdle()
        assertTrue("HTTP 已解锁时，本地弹窗必须完成而非继续等待密钥", unlocked)
    }
}
