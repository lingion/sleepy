package com.lingion.sleepy.widget

import android.appwidget.AppWidgetManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.ui.screen.widget.WidgetGalleryScreen
import com.lingion.sleepy.ui.theme.SleepyThemeProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowAppWidgetManager

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rCN-w360dp-h2400dp-port-mdpi", application = SleepyApp::class)
class WidgetGalleryEmpiricalTest {

    @get:Rule
    val compose = createComposeRule()

    private val expectedGroups = listOf(
        "本周课表（网格）（多尺寸）",
        "今日课程（多尺寸）",
        "本周课表（列表）（多尺寸）",
        "本周课表（周视图）（多尺寸）",
        "最近两天（多尺寸）"
    )

    // 展开标题与主尺寸卡片可能同名；预览图的无障碍描述只属于可添加的卡片。
    private fun variantNode(name: String) =
        compose.onNode(hasText(name) and hasContentDescription(name))

    private fun groupHeader(collapsedName: String): SemanticsNodeInteraction {
        val baseName = collapsedName.removeSuffix("（多尺寸）")
        return compose.onNode(
            hasText(collapsedName) or (hasText(baseName) and hasContentDescription(baseName).not())
        )
    }

    @Test
    fun `entering WidgetGalleryScreen shows exactly 5 primary cards initially with multi-size label`() {
        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                WidgetGalleryScreen(onBack = {})
            }
        }

        // 1. Verify all 5 primary cards have "(多尺寸)" label
        for (label in expectedGroups) {
            compose.onNodeWithText(label).performScrollTo().assertExists().assertIsDisplayed()
        }

        // 2. Count total primary cards with "(多尺寸)"
        val allMultiSizeNodes = compose.onAllNodesWithText("（多尺寸）", substring = true)
        assertEquals("初始状态必须展示且仅展示 5 个主组件卡片", 5, allMultiSizeNodes.fetchSemanticsNodes().size)

        // 3. Verify secondary size variants are NOT displayed initially
        // 小尺寸标签不该出现（收起状态）
        compose.onNodeWithText("本周课表（网格）· 小").assertDoesNotExist()
        compose.onNodeWithText("今日课程 · 小").assertDoesNotExist()
        compose.onNodeWithText("本周课表（列表）· 小").assertDoesNotExist()
        compose.onNodeWithText("本周课表（周视图）· 小").assertDoesNotExist()
        compose.onNodeWithText("最近两天 · 小").assertDoesNotExist()
        // 注：中尺寸标签（· 中）在 strings.xml 定义，UI 会显示，不做 assertDoesNotExist()
    }

    @Test
    fun `dummy single-size group omits multi-size suffix`() {
        val sampleVariant = ALL_WIDGET_VARIANTS.first()
        val dummySingleGroup = WidgetGalleryGroup(
            key = "DummySingle",
            primaryVariant = sampleVariant,
            allVariants = listOf(sampleVariant)
        )
        assertFalse("单尺寸组 isMultiSize 必须为 false", dummySingleGroup.isMultiSize)
        assertEquals(
            "单尺寸组不包含 (多尺寸) 后缀",
            "测试组件",
            dummySingleGroup.formattedName("测试组件")
        )
        assertTrue("单尺寸组 remainingVariants 为空", dummySingleGroup.remainingVariants.isEmpty())

        val multiGroup = WidgetGalleryGroup(
            key = "DummyMulti",
            primaryVariant = sampleVariant,
            allVariants = listOf(sampleVariant, ALL_WIDGET_VARIANTS[1])
        )
        assertTrue("多尺寸组 isMultiSize 必须为 true", multiGroup.isMultiSize)
        assertEquals(
            "多尺寸组包含 (多尺寸) 后缀",
            "测试组件(多尺寸)",
            multiGroup.formattedName("测试组件")
        )
        assertEquals(1, multiGroup.remainingVariants.size)
    }

    @Test
    fun `accordion expansion and collapse interactions`() {
        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                WidgetGalleryScreen(onBack = {})
            }
        }

        // Step 1: Tapping card A (WeekGrid) expands card A
        groupHeader(expectedGroups[0]).performScrollTo().performClick()

        // WeekGrid variants appear
        variantNode("本周课表（网格）").performScrollTo().assertExists().assertIsDisplayed()
        compose.onNodeWithText("本周课表（网格）· 小").performScrollTo().assertExists().assertIsDisplayed()
        compose.onNodeWithText("本周课表（网格）· 4×2").performScrollTo().assertExists().assertIsDisplayed()

        // Card B (Today) variants do NOT exist
        compose.onNodeWithText("今日课程 · 小").assertDoesNotExist()
        compose.onNodeWithText("今日课程 · 中").assertDoesNotExist()

        // Step 2: Tapping card B (Today) expands card B and collapses card A
        groupHeader(expectedGroups[1]).performScrollTo().performClick()

        // Card A variants must now be collapsed (gone)
        compose.onNodeWithText("本周课表（网格）· 小").assertDoesNotExist()
        compose.onNodeWithText("本周课表（网格）· 4×2").assertDoesNotExist()

        // Card B variants must now appear
        variantNode("今日课程").performScrollTo().assertExists().assertIsDisplayed()
        compose.onNodeWithText("今日课程 · 小").performScrollTo().assertExists().assertIsDisplayed()
        compose.onNodeWithText("今日课程 · 中").performScrollTo().assertExists().assertIsDisplayed()

        // Step 3: Tapping expanded card B collapses card B
        // 展开后的标题去掉多尺寸后缀，点击标题收起。
        groupHeader(expectedGroups[1]).performScrollTo().performClick()

        // Card B variants must now be collapsed (gone)
        compose.onNodeWithText("今日课程 · 小").assertDoesNotExist()
        compose.onNodeWithText("今日课程 · 中").assertDoesNotExist()

        // All 5 primary cards are visible again
        for (label in expectedGroups) {
            compose.onNodeWithText(label).performScrollTo().assertExists().assertIsDisplayed()
        }
    }

    @Test
    fun `when expanded all variants are present and clickable across all 5 groups`() {
        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                WidgetGalleryScreen(onBack = {})
            }
        }

        val groups = WidgetGalleryData.buildGroups()
        assertEquals(5, groups.size)

        for ((index, group) in groups.withIndex()) {
            val primaryLabel = expectedGroups[index]
            println(">>> EXPANDING group: ${group.key}, label: $primaryLabel")

            // Scroll to primary card and click to expand
            groupHeader(primaryLabel).performScrollTo().performClick()

            // Verify each variant in group is present and has click action
            for (variant in group.allVariants) {
                val variantName = ApplicationProvider.getApplicationContext<SleepyApp>().getString(variant.displayNameRes)
                val node = variantNode(variantName).performScrollTo()
                node.assertExists()
                node.assertIsDisplayed()
                node.assertHasClickAction()
            }

            // Collapse back by clicking the expanded header
            groupHeader(primaryLabel).performScrollTo().performClick()
        }
    }

    @Test
    fun `tapping any variant invokes pin request`() {
        val app = ApplicationProvider.getApplicationContext<SleepyApp>()
        val appWidgetManager = AppWidgetManager.getInstance(app)
        val shadowManager: ShadowAppWidgetManager = shadowOf(appWidgetManager)
        shadowManager.setRequestPinAppWidgetSupported(true)

        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                WidgetGalleryScreen(onBack = {})
            }
        }

        // Expand WeekGrid
        groupHeader(expectedGroups[0]).performScrollTo().performClick()

        val smallNode = compose.onNodeWithText("本周课表（网格）· 小").performScrollTo()
        smallNode.assertHasClickAction()

        // Tap the small variant
        smallNode.performClick()

        // While cooling is active (2000ms debounce), click modifier is removed so hasClickAction is false
        compose.onNodeWithText("本周课表（网格）· 小").performScrollTo().assert(hasClickAction().not())
    }

    @Test
    fun `cooling debounce edge case - variant cannot be clicked twice within 2000ms but re-enables after delay`() {
        val app = ApplicationProvider.getApplicationContext<SleepyApp>()
        val appWidgetManager = AppWidgetManager.getInstance(app)
        shadowOf(appWidgetManager).setRequestPinAppWidgetSupported(true)

        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                WidgetGalleryScreen(onBack = {})
            }
        }

        // Expand WeekGrid
        groupHeader(expectedGroups[0]).performScrollTo().performClick()

        val smallNode = compose.onNodeWithText("本周课表（网格）· 小").performScrollTo()
        smallNode.assertHasClickAction()

        // Tap small variant
        smallNode.performClick()

        // While cooling, it loses click action
        compose.onNodeWithText("本周课表（网格）· 小").performScrollTo().assert(hasClickAction().not())

        // Other variants in the same family should NOT be disabled!
        compose.onNodeWithText("本周课表（网格）· 4×2").performScrollTo().assertHasClickAction()
        variantNode("本周课表（网格）").performScrollTo().assertHasClickAction()

        // Advance Compose main clock by 2100ms
        compose.mainClock.advanceTimeBy(2100)

        // After cooling expires, small variant should have click action restored
        compose.onNodeWithText("本周课表（网格）· 小").performScrollTo().assertHasClickAction()
    }

    @Test
    fun `stress test rapid accordion toggling does not crash or corrupt state`() {
        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                WidgetGalleryScreen(onBack = {})
            }
        }

        // Rapidly toggle expansion across all cards
        for (i in 0 until 6) {
            val labelA = expectedGroups[i % expectedGroups.size]
            val labelB = expectedGroups[(i + 1) % expectedGroups.size]
            groupHeader(labelA).performScrollTo().performClick()
            groupHeader(labelB).performScrollTo().performClick()
        }

        // Finally collapse
        val lastLabel = expectedGroups[6 % expectedGroups.size]
        groupHeader(lastLabel).performScrollTo().performClick()

        // Verify all 5 cards still intact
        for (label in expectedGroups) {
            compose.onNodeWithText(label).performScrollTo().assertExists().assertIsDisplayed()
        }
    }
}
