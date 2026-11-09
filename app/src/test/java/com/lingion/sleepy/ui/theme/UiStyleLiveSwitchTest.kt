package com.lingion.sleepy.ui.theme

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.lingion.sleepy.ui.component.liquidglass.GlassTextButton
import com.lingion.sleepy.util.AppPrefs
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class UiStyleLiveSwitchTest {
    @get:Rule val compose = createComposeRule()

    @Test fun livePreferenceChangesDoNotRemountOrLoseEditingState() {
        val context: Context = ApplicationProvider.getApplicationContext()
        AppPrefs.setUiStyle(context, UiStyle.Classic)
        var mounts = 0
        var observed = UiStyle.Classic
        compose.setContent {
            SleepyThemeProvider {
                observed = LocalUiStyle.current
                LaunchedEffect(Unit) { mounts++ }
                var draft by rememberSaveable { mutableStateOf("original") }
                Column {
                    Text(draft)
                    GlassTextButton(onClick = { draft = "unsaved edit" }) { Text("Edit") }
                }
            }
        }
        compose.onNodeWithText("Edit").performClick()
        compose.runOnIdle { AppPrefs.setUiStyle(context, UiStyle.LiquidGlass) }
        compose.waitUntil { observed == UiStyle.LiquidGlass }
        compose.onNodeWithText("unsaved edit").assertExists()
        compose.runOnIdle { AppPrefs.setUiStyle(context, UiStyle.Classic) }
        compose.waitUntil { observed == UiStyle.Classic }
        compose.onNodeWithText("unsaved edit").assertExists()
        compose.runOnIdle { assertEquals("theme content stays in one composition position", 1, mounts) }
    }
}
