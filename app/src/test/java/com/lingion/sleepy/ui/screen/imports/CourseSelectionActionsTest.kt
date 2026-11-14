package com.lingion.sleepy.ui.screen.imports

import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.parser.ScheduleParser
import com.lingion.sleepy.ui.component.liquidglass.GlassHost
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rCN-w360dp-h640dp-mdpi")
class CourseSelectionActionsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun glassConfirmFitsToolbarAndPreservesSelectedGroups() = checkActions(UiStyle.LiquidGlass)
    @Test fun materialConfirmPreservesSelectedGroups() = checkActions(UiStyle.Classic)

    private fun checkActions(style: UiStyle) {
        var result: Set<String>? = null
        val courses = listOf("math", "english").map { id ->
            CourseEntity(tableId = 1, groupId = id, courseName = id, day = 1,
                startNode = 1, step = 1, startWeek = 1, endWeek = 16, color = "#FF6750A4")
        }
        compose.setContent {
            MaterialExpressiveTheme {
                CompositionLocalProvider(LocalUiStyle provides style) {
                    GlassHost {
                        CourseSelectionSheet(
                            ScheduleParser.ParseResult("Table", "2026-09-07", courses),
                            onConfirm = { result = it }, onDismiss = {},
                        )
                    }
                }
            }
        }
        val confirm = compose.onNodeWithTag("import_select_confirm")
        val all = compose.onNodeWithTag("import_select_all")
        confirm.assertIsDisplayed().assertIsEnabled()
        val confirmBounds = confirm.fetchSemanticsNode().boundsInRoot
        val allBounds = all.fetchSemanticsNode().boundsInRoot
        assertTrue("the native action must not fill the toolbar", confirmBounds.width < 180f)
        assertTrue("the two toolbar controls remain separate", confirmBounds.left >= allBounds.right)
        all.performClick()
        confirm.assertIsNotEnabled()
        compose.onNodeWithTag("select_course#0").performClick()
        confirm.assertIsEnabled().performClick()
        assertEquals(setOf("english"), result)
    }
}
