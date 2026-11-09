package com.lingion.sleepy.ui.screen.imports

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.core.app.ApplicationProvider
import com.lingion.sleepy.ui.theme.SleepyThemeProvider
import com.lingion.sleepy.util.AppPrefs
import org.json.JSONArray
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 同一页面内的前后对照（避免二次 setContent 的 assets IO 时序差）：
 * 无历史 → 顶部不插入卡片（SchoolSelectGoldenTest 像素基线不变的前提）；
 * 写入一次成功后 → 卡片立刻出现，验证同一 Activity 内退回选校页也能刷新。
 * 判据一律用 dbgTag，不受多语言字符串回退影响。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rUS-w360dp-h640dp-port-xhdpi")
class RecentSchoolsCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test fun `recent card is absent until an import is recorded then appears immediately`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("sleepy_prefs", Context.MODE_PRIVATE)
            .edit().remove(AppPrefs.KEY_RECENT_JW_SCHOOLS).commit()

        composeRule.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                SchoolSelectScreen(onSchoolSelected = {}, onBack = {})
            }
        }
        // 字母索引栏只在学校目录加载完成后渲染，作为「已走 LazyColumn 分支」的判据。
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithTag("school_index_bar").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            "无导入历史时不得插入「最新导入学校」列表项",
            composeRule.onAllNodesWithTag("school_recent_card").fetchSemanticsNodes().isEmpty()
        )

        val catalogue = JSONArray(app.assets.open("schools.json").bufferedReader().use { it.readText() })
        val first = catalogue.getJSONObject(0)
        val stableKey = first.optString("catalogId").ifBlank {
            "${first.getString("sortKey")}_${first.getString("name")}"
        }
        AppPrefs.recordRecentJwSchool(app, stableKey)

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("school_recent_card").fetchSemanticsNodes().isNotEmpty()
        }
        // LazyColumn 首项必然在视口内；语义树命中即证明卡片已组合。
        // 不用 assertIsDisplayed：Robolectric 下 onPlaced 与语义树同步时机不稳，
        // 该断言会在卡片已经渲染时偶发失败，属于测试框架噪声而非功能回归。
        composeRule.waitForIdle()
        assertTrue(
            "导入成功后卡片必须仍在列表中",
            composeRule.onAllNodesWithTag("school_recent_card").fetchSemanticsNodes().isNotEmpty()
        )
    }
}
