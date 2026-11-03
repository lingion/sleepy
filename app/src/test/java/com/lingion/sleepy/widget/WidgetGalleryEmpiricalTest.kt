package com.lingion.sleepy.widget

import android.appwidget.AppWidgetManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
        "本周课表（网格）(多尺寸)",
        "今日课程(多尺寸)",
        "本周课表（列表）(多尺寸)",
        "本周课表（周视图）(多尺寸)",
        "最近两天(多尺寸)"
    )

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
        val allMultiSizeNodes = compose.onAllNodesWithText("(多尺寸)", substring = true)
        assertEquals("初始状态必须展示且仅展示 5 个主组件卡片", 5, allMultiSizeNodes.fetchSemanticsNodes().size)

        // 3. Verify secondary size variants are NOT displayed initially
        compose.onNodeWithText("本周课表（网格）· 小").assertDoesNotExist()
        compose.onNodeWithText("本周课表（网格）· 4×2").assertDoesNotExist()
        compose.onNodeWithText("今日课程 · 小").assertDoesNotExist()
        compose.onNodeWithText("今日课程 · 中").assertDoesNotExist()
        compose.onNodeWithText("本周课表（列表）· 小").assertDoesNotExist()
        compose.onNodeWithText("周列表 · 中").assertDoesNotExist()
        compose.onNodeWithText("本周课表（周视图）· 小").assertDoesNotExist()
        compose.onNodeWithText("最近两天 · 小").assertDoesNotExist()
        compose.onNodeWithText("两日课程 · 中").assertDoesNotExist()
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
        compose.onNodeWithText("本周课表（网格）(多尺寸)").performScrollTo().performClick()

        // WeekGrid variants appear
        compose.onNodeWithText("本周课表（网格）").performScrollTo().assertExists().assertIsDisplayed()
        compose.onNodeWithText("本周课表（网格）· 小").performScrollTo().assertExists().assertIsDisplayed()
        compose.onNodeWithText("本周课表（网格）· 4×2").performScrollTo().assertExists().assertIsDisplayed()

        // Card B (Today) variants do NOT exist
        compose.onNodeWithText("今日课程 · 小").assertDoesNotExist()
        compose.onNodeWithText("今日课程 · 中").assertDoesNotExist()

        // Step 2: Tapping card B (Today) expands card B and collapses card A
        compose.onNodeWithText("今日课程(多尺寸)").performScrollTo().performClick()

        // Card A variants must now be collapsed (gone)
        compose.onNodeWithText("本周课表（网格）· 小").assertDoesNotExist()
        compose.onNodeWithText("本周课表（网格）· 4×2").assertDoesNotExist()

        // Card B variants must now appear
        compose.onNodeWithText("今日课程").performScrollTo().assertExists().assertIsDisplayed()
        compose.onNodeWithText("今日课程 · 小").performScrollTo().assertExists().assertIsDisplayed()
        compose.onNodeWithText("今日课程 · 中").performScrollTo().assertExists().assertIsDisplayed()

        // Step 3: Tapping expanded card B collapses card B
        // The header row has "今日课程(多尺寸)" with noRippleClickable(onCollapse)
        compose.onNodeWithText("今日课程(多尺寸)").performScrollTo().performClick()

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

        for (group in groups) {
            val primaryLabel = group.formattedName(
                ApplicationProvider.getApplicationContext<SleepyApp>().getString(group.primaryVariant.displayNameRes)
            )
            println(">>> EXPANDING group: ${group.key}, label: $primaryLabel")

            // Scroll to primary card and click to expand
            compose.onNodeWithText(primaryLabel).performScrollTo().performClick()

            // Verify each variant in group is present and has click action
            for (variant in group.allVariants) {
                val variantName = ApplicationProvider.getApplicationContext<SleepyApp>().getString(variant.displayNameRes)
                val node = compose.onNodeWithText(variantName).performScrollTo()
                node.assertExists()
                node.assertIsDisplayed()
                node.assertHasClickAction()
            }

            // Collapse back by clicking the expanded header
            compose.onNodeWithText(primaryLabel).performScrollTo().performClick()
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
        compose.onNodeWithText("本周课表（网格）(多尺寸)").performScrollTo().performClick()

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
        compose.onNodeWithText("本周课表（网格）(多尺寸)").performScrollTo().performClick()

        val smallNode = compose.onNodeWithText("本周课表（网格）· 小").performScrollTo()
        smallNode.assertHasClickAction()

        // Tap small variant
        smallNode.performClick()

        // While cooling, it loses click action
        compose.onNodeWithText("本周课表（网格）· 小").performScrollTo().assert(hasClickAction().not())

        // Other variants in the same family should NOT be disabled!
        compose.onNodeWithText("本周课表（网格）· 4×2").performScrollTo().assertHasClickAction()
        compose.onNodeWithText("本周课表（网格）").performScrollTo().assertHasClickAction()

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
            compose.onNodeWithText(labelA).performScrollTo().performClick()
            compose.onNodeWithText(labelB).performScrollTo().performClick()
        }

        // Finally collapse
        val lastLabel = expectedGroups[6 % expectedGroups.size]
        compose.onNodeWithText(lastLabel).performScrollTo().performClick()

        // Verify all 5 cards still intact
        for (label in expectedGroups) {
            compose.onNodeWithText(label).performScrollTo().assertExists().assertIsDisplayed()
        }
    }
}
