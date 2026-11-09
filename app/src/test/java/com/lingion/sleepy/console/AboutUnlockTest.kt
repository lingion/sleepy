package com.lingion.sleepy.console

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.lingion.sleepy.BuildConfig
import com.lingion.sleepy.console.handlers.UnlockTapCounter
import com.lingion.sleepy.ui.screen.mine.AboutScreen
import com.lingion.sleepy.ui.theme.SleepyThemeProvider
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "zh-rCN-w360dp-h800dp")
class AboutUnlockTest {
    @get:Rule val compose = createComposeRule()

    @Before fun reset() {
        ConsoleBootstrap.stop()
        DebugGate.reset()
        UnlockTapCounter.reset()
    }

    @After fun cleanUp() {
        ConsoleBootstrap.stop()
        DebugGate.reset()
        UnlockTapCounter.reset()
    }

    private fun screen() {
        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                AboutScreen(onBack = {})
            }
        }
    }

    @Test fun seventhVersionTapShowsPasswordDialogAndCancelAllowsRetry() {
        screen()
        repeat(6) { compose.onNodeWithTag("about_version").performClick() }
        compose.onNodeWithTag("debug_unlock_dialog").assertDoesNotExist()
        compose.onNodeWithTag("about_version").performClick()
        compose.onNodeWithTag("debug_unlock_dialog").assertIsDisplayed()
        compose.onNodeWithTag("debug_unlock_key").assertExists()
        compose.onNodeWithTag("debug_unlock_cancel").performClick()
        compose.onNodeWithTag("debug_unlock_dialog").assertDoesNotExist()
        repeat(7) { compose.onNodeWithTag("about_version").performClick() }
        compose.onNodeWithTag("debug_unlock_dialog").assertIsDisplayed()
    }

    @Test fun versionInformationCardAlsoOpensPasswordDialog() {
        screen()
        repeat(7) { compose.onNodeWithTag("about_version_card").performClick() }
        compose.onNodeWithTag("debug_unlock_dialog").assertIsDisplayed()
    }
}
