package com.lingion.sleepy.ui.screen.mine

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.lingion.sleepy.ui.component.liquidglass.GlassHost
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-w360dp-h640dp-mdpi")
class HolidayLoadingLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun materialLoadingDoesNotShiftSourceOrFollowingCards() = checkLayout(UiStyle.Classic)

    @Test fun glassLoadingDoesNotShiftSourceOrFollowingCards() = checkLayout(UiStyle.LiquidGlass)

    private fun checkLayout(style: UiStyle) {
        val loading = mutableStateOf(true)
        var refreshes = 0
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides style) {
                    GlassHost(enabled = style == UiStyle.LiquidGlass) {
                        Column {
                            Row(Modifier.fillMaxWidth().testTag("source"), verticalAlignment = Alignment.CenterVertically) {
                                Text("Holiday source", Modifier.weight(1f).testTag("label"))
                                HolidayDataSourceAction(loading.value) { refreshes++ }
                            }
                            Text("Following card", Modifier.testTag("following"))
                        }
                    }
                }
            }
        }
        val tags = listOf("source", "label", "following")
        val initialBounds = tags.map { compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }
        compose.runOnIdle { loading.value = false }
        tags.forEachIndexed { index, tag ->
            assertEquals("$tag must stay in place when loading finishes", initialBounds[index],
                compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot)
        }
        compose.onNode(hasClickAction()).performClick()
        compose.runOnIdle { assertEquals(1, refreshes); loading.value = true }
        compose.onNode(hasClickAction()).assertDoesNotExist()
        tags.forEachIndexed { index, tag ->
            assertEquals("$tag must stay in place during refresh", initialBounds[index],
                compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot)
        }
    }
}
