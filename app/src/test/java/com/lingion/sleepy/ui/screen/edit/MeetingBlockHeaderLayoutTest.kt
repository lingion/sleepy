package com.lingion.sleepy.ui.screen.edit

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.lingion.sleepy.R
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import com.lingion.sleepy.ui.component.liquidglass.GlassHost
import com.lingion.sleepy.ui.theme.SleepyThemeProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 上课时段卡卡头布局、顺序不变量与几何防挤压回归测试 (PR #368):
 *
 * 顺序不变量: 标题和摘要 [删除叉] [折叠箭头]
 * 1. 标题与摘要位于最左侧，拥有 weight(1f) 自适应宽度；
 * 2. 删除叉位于折叠箭头左邻一位；
 * 3. 折叠箭头位于整行最右侧；
 * 4. 文字区域与右侧动作按钮群严格无交叠 (boundsInRoot 互斥)；
 * 5. canRemove 为 false 时删除叉不渲染，箭头仍稳居最右；
 * 6. 点击文字区或折叠箭头均可切换展开/收起态，点击删除叉触发 onRemove；
 * 7. 极限窄屏 (280dp)、超长标题、大字体 (fontScale 1.5x) 下，右侧两个 48dp 按钮尺寸完整，不被挤压变形。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rCN-w360dp-h800dp-port-xhdpi", application = SleepyApp::class)
class MeetingBlockHeaderLayoutTest {

    @get:Rule val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun renderCard(
        width: Int = 360,
        fontScale: Float = 1f,
        title: String = "时段 1",
        canRemove: Boolean = true,
        expanded: Boolean = false,
        onRemove: () -> Unit = {},
        blockModifier: (MeetingBlockDraft.() -> Unit)? = null
    ): MeetingBlockDraft {
        val block = MeetingBlockDraft(
            id = 1,
            days = mutableStateListOf(1),
            startNode = 1,
            step = 2,
            startTime = "08:00",
            endTime = "09:40",
            expanded = expanded
        ).apply { blockModifier?.invoke(this) }

        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass) {
                GlassHost {
                    Surface(
                        modifier = Modifier
                            .width(width.dp)
                            .testTag("meeting-block-card-root")
                    ) {
                        MeetingBlockEditor(
                            title = title,
                            block = block,
                            groupSourceColorHex = "#FF6750A4",
                            canRemove = canRemove,
                            issues = emptyList(),
                            fieldShape = SleepyTheme.shapes.medium,
                            fieldColors = TextFieldDefaults.colors(),
                            maxStd = 12,
                            maxWeek = 20,
                            timeJson = "",
                            candidates = emptyList(),
                            onRemove = onRemove,
                            onPickEdge = {},
                            onEditSlot = { _, _, _ -> },
                            onDeselectEdge = {}
                        )
                    }
                }
            }
        }
        }
        }
        compose.waitForIdle()
        return block
    }

    private fun captureScreenshotIfEnabled(name: String) {
        val shouldCapture = System.getProperty("sleepy.test.meetingBlockScreenshots", "false").toBoolean() ||
            System.getProperty("sleepy.test.settingsScreenshots", "false").toBoolean()
        if (!shouldCapture) return
        val dir = File("build/reports/meeting-block-layout").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { out ->
            val bitmap = compose.onNodeWithTag("meeting-block-card-root").captureToImage().asAndroidBitmap()
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out))
        }
    }

    @Test
    fun header_order_and_bounds_title_delete_chevron_no_overlap() {
        renderCard(width = 360, canRemove = true, expanded = false)

        val textNode = compose.onNodeWithText("时段 1")
        val deleteNode = compose.onNodeWithContentDescription(context.getString(R.string.delete_slot))
        val chevronNode = compose.onNodeWithContentDescription(context.getString(R.string.expand))

        textNode.assertIsDisplayed()
        deleteNode.assertIsDisplayed()
        chevronNode.assertIsDisplayed()

        val textBounds = textNode.fetchSemanticsNode().boundsInRoot
        val deleteBounds = deleteNode.fetchSemanticsNode().boundsInRoot
        val chevronBounds = chevronNode.fetchSemanticsNode().boundsInRoot

        // 1. 顺序不变量: 标题在最左，删除叉居中，折叠箭头最右
        assertTrue("标题必须在删除叉左侧: textLeft=${textBounds.left}, deleteLeft=${deleteBounds.left}",
            textBounds.left < deleteBounds.left)
        assertTrue("删除叉必须在折叠箭头左侧: deleteLeft=${deleteBounds.left}, chevronLeft=${chevronBounds.left}",
            deleteBounds.left < chevronBounds.left)

        // 2. 几何无重叠: 文字右边界 <= 删除叉左边界，删除叉右边界 <= 箭头左边界
        assertTrue("文字区域与删除叉不能有交叠: textRight=${textBounds.right}, deleteLeft=${deleteBounds.left}",
            textBounds.right <= deleteBounds.left)
        assertTrue("删除叉与折叠箭头不能有交叠: deleteRight=${deleteBounds.right}, chevronLeft=${chevronBounds.left}",
            deleteBounds.right <= chevronBounds.left)

        // 3. 按钮尺寸统一规范: 触控区 >= 48dp，视觉布局尺寸一致（液态主题36dp盘面）
        val density = compose.density.density
        val minTouchPx = 48f * density - 1f
        val deleteTouch = deleteNode.fetchSemanticsNode().touchBoundsInRoot
        val chevronTouch = chevronNode.fetchSemanticsNode().touchBoundsInRoot

        assertTrue("删除叉触控区宽度必须达到 48dp 标准: touchWidth=${deleteTouch.width}", deleteTouch.width >= minTouchPx)
        assertTrue("删除叉触控区高度必须达到 48dp 标准: touchHeight=${deleteTouch.height}", deleteTouch.height >= minTouchPx)
        assertTrue("折叠箭头触控区宽度必须达到 48dp 标准: touchWidth=${chevronTouch.width}", chevronTouch.width >= minTouchPx)
        assertTrue("折叠箭头触控区高度必须达到 48dp 标准: touchHeight=${chevronTouch.height}", chevronTouch.height >= minTouchPx)

        assertEquals("删除叉与折叠箭头布局尺寸必须统一", deleteBounds.width, chevronBounds.width, 0.5f)
        assertEquals("删除叉与折叠箭头布局高度必须统一", deleteBounds.height, chevronBounds.height, 0.5f)

        captureScreenshotIfEnabled("header_order_and_bounds_normal")
    }

    @Test
    fun header_single_block_cannot_remove_shows_only_chevron_at_right() {
        renderCard(width = 360, canRemove = false, expanded = false)

        compose.onNodeWithContentDescription(context.getString(R.string.delete_slot)).assertDoesNotExist()
        val chevronNode = compose.onNodeWithContentDescription(context.getString(R.string.expand))
        chevronNode.assertIsDisplayed()

        val textBounds = compose.onNodeWithText("时段 1").fetchSemanticsNode().boundsInRoot
        val chevronBounds = chevronNode.fetchSemanticsNode().boundsInRoot

        assertTrue("单时段无删除叉时，标题仍在折叠箭头左侧: textRight=${textBounds.right}, chevronLeft=${chevronBounds.left}",
            textBounds.right <= chevronBounds.left)

        val density = compose.density.density
        val minTouchPx = 48f * density - 1f
        val chevronTouch = chevronNode.fetchSemanticsNode().touchBoundsInRoot
        assertTrue("折叠箭头触控区宽度保持 48dp: width=${chevronTouch.width}", chevronTouch.width >= minTouchPx)

        captureScreenshotIfEnabled("header_single_block_no_delete")
    }

    @Test
    fun header_interactivity_text_and_chevron_toggle_collapse_delete_removes() {
        var removeCalled = false
        val block = renderCard(width = 360, canRemove = true, expanded = false, onRemove = { removeCalled = true })

        assertFalse("初始状态必须为收起", block.expanded)

        // 点击文字区域可展开
        compose.onNodeWithText("时段 1").performClick()
        assertTrue("点击文字区域必须展开时段卡", block.expanded)
        compose.onNodeWithContentDescription(context.getString(R.string.collapse)).assertIsDisplayed()

        // 点击折叠箭头可收起
        compose.onNodeWithContentDescription(context.getString(R.string.collapse)).performClick()
        assertFalse("点击折叠箭头必须收起时段卡", block.expanded)
        compose.onNodeWithContentDescription(context.getString(R.string.expand)).assertIsDisplayed()

        // 点击删除叉触发删除回调
        compose.onNodeWithContentDescription(context.getString(R.string.delete_slot)).performClick()
        assertTrue("点击删除叉必须触发 onRemove 回调", removeCalled)

        captureScreenshotIfEnabled("header_interactivity_verified")
    }

    @Test
    fun header_narrow_screen_and_long_title_and_large_font_never_squeezes_buttons() {
        val longTitle = "长标题超长课程上课时段排版核验长文本换行不能挤压右侧按钮"
        renderCard(width = 280, fontScale = 1.5f, title = longTitle, canRemove = true, expanded = false)

        val textNode = compose.onNodeWithText(longTitle)
        val deleteNode = compose.onNodeWithContentDescription(context.getString(R.string.delete_slot))
        val chevronNode = compose.onNodeWithContentDescription(context.getString(R.string.expand))

        textNode.assertIsDisplayed()
        deleteNode.assertIsDisplayed()
        chevronNode.assertIsDisplayed()

        val textBounds = textNode.fetchSemanticsNode().boundsInRoot
        val deleteBounds = deleteNode.fetchSemanticsNode().boundsInRoot
        val chevronBounds = chevronNode.fetchSemanticsNode().boundsInRoot

        // 极限条件下依然保持无交叠
        assertTrue("极限条件下文字不与删除叉交叠: textRight=${textBounds.right}, deleteLeft=${deleteBounds.left}",
            textBounds.right <= deleteBounds.left)
        assertTrue("极限条件下删除叉不与折叠箭头交叠: deleteRight=${deleteBounds.right}, chevronLeft=${chevronBounds.left}",
            deleteBounds.right <= chevronBounds.left)

        // 按钮尺寸不能被挤压缩窄
        val density = compose.density.density
        val minTouchPx = 48f * density - 1f
        val deleteTouch = deleteNode.fetchSemanticsNode().touchBoundsInRoot
        val chevronTouch = chevronNode.fetchSemanticsNode().touchBoundsInRoot

        assertTrue("极限条件下删除叉触控区不小于 48dp", deleteTouch.width >= minTouchPx)
        assertTrue("极限条件下折叠箭头触控区不小于 48dp", chevronTouch.width >= minTouchPx)
        assertEquals("窄屏大字下删除叉与箭头布局尺寸保持一致", deleteBounds.width, chevronBounds.width, 0.5f)

        captureScreenshotIfEnabled("header_narrow_large_font_anti_squeeze")
    }
}
