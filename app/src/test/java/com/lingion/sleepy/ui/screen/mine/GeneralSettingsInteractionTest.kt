package com.lingion.sleepy.ui.screen.mine

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.lingion.sleepy.R
import com.lingion.sleepy.data.undo.UndoManager
import com.lingion.sleepy.ui.theme.SleepyThemeProvider
import com.lingion.sleepy.util.AppPrefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-w360dp-h640dp-port-xhdpi")
class GeneralSettingsInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun show() {
        AppPrefs.setUndoDepth(context, 7)
        UndoManager.applyMaxDepth(7)
        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                GeneralSettingsScreen(onBack = {})
            }
        }
    }

    private fun choose(label: Int) {
        val text = context.getString(label)
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
        compose.onNodeWithText(text).performClick()
    }

    private fun assertDepth(expected: Int) {
        compose.runOnIdle {
            assertEquals(expected, AppPrefs.getUndoDepth(context))
            assertEquals(expected, UndoManager.maxDepth)
        }
    }

    @After fun cleanup() { UndoManager.clear(); UndoManager.applyMaxDepth(5) }

    @Test fun modesPersistAndKeepTheLastPositiveLimit() {
        show()
        choose(R.string.settings_undo_off)
        assertDepth(0)
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        choose(R.string.settings_undo_unlimited)
        assertDepth(-1)
        choose(R.string.settings_undo_limited)
        assertDepth(7)
        compose.onNode(hasSetTextAction()).performTextReplacement("12")
        assertDepth(12)
        choose(R.string.settings_undo_off)
        choose(R.string.settings_undo_limited)
        assertDepth(12)
    }

    @Test fun invalidLimitDoesNotPersistAndReenteringLimitedShowsItsActualValue() {
        show()
        choose(R.string.settings_undo_limited)
        compose.onNode(hasSetTextAction()).performTextReplacement("0")
        assertDepth(7)
        choose(R.string.settings_undo_off)
        choose(R.string.settings_undo_limited)
        assertDepth(5)
        compose.onNode(hasSetTextAction()).assertTextContains("5")
    }
}
