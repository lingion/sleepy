package com.lingion.sleepy.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 主题切换缓动管线冒烟 (MD3E motion 全量补齐 Task 8):
 * SleepyThemeProvider 的 transitioning() 色彩包装必须同时满足三条:
 *   1. 存在性/终态: dark 翻true/false 落定后, MaterialTheme.colorScheme
 *      各槽到达目标 scheme 的值且往返可复现 (切回亮色 = 初始亮色);
 *   2. 缓动真实存在: 翻档后短泵 5 拍 (≈80ms) 取中间帧 — 颜色既不等于旧档也不
 *      等于新档 (硬切实现下此断言必红, 这是本测试的红绿分界);
 *   3. 零重挂载: content 根上的 LaunchedEffect(Unit) 只进一次 — 整树
 *      组合位置稳定, remember/NavDisplay/rememberSaveable 类状态在切档
 *      时存活 (否决整树 Crossfade 的选型理由的行为面证明)。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rUS-w360dp-h640dp-port-xhdpi")
class ThemeSwitchCrossfadeSmokeTest {

    @get:Rule val compose = createComposeRule()

    private class Holder {
        var dark by mutableStateOf(false)
        val mountCount = mutableStateOf(0)
        // 组合期直写 (普通 var, 非快照态): 手动时钟下 LaunchedEffect 协程
        // 派发滞后一拍, 中段帧探针会读到陈旧值; 组合捕获即本帧真值。
        var lastSurface: Color? = null
    }

    private fun page(holder: Holder) {
        compose.setContent {
            SleepyThemeProvider(darkTheme = holder.dark, themeKey = "default") {
                LaunchedEffect(Unit) { holder.mountCount.value += 1 }
                // 探针取 surface: 默认淡紫预设的 primary 深浅同值 (品牌色不随
                // 档变), surface/background 才是切档换色槽 — 断言面必须落在
                // 真实换色的槽上, 否则 0 距离插值永远等于两端。
                holder.lastSurface = MaterialTheme.colorScheme.surface
                Box(modifier = Modifier)
            }
        }
    }

    @Test
    fun theme_switch_fades_to_target_without_remounting() {
        val holder = Holder()
        page(holder)
        compose.waitForIdle()
        val lightSurface = holder.lastSurface
        assertNotNull(lightSurface)
        assertEquals("初始挂载一次", 1, holder.mountCount.value)

        // 中段帧: 关自动推进, 每 16ms 一拍、拍间 waitForIdle 泵协程 (动画协程
        // 需要显式派发, 纯 advanceTimeBy 不泵 → 永远停在 fraction 0)。
        // 取样窗口 5 拍 ≈80ms: 弹簧必有非零 fraction (≥ 首拍后一帧的松弛),
        // 又远不到收敛 — 15 拍后可能已贴终色, "mid ≠ 终态" 断言就会假红。
        compose.mainClock.autoAdvance = false
        holder.dark = true
        repeat(5) {
            compose.mainClock.advanceTimeBy(16)
            compose.waitForIdle()
        }
        val midSurface = holder.lastSurface
        assertNotNull(midSurface)
        assertNotEquals("翻档后中间帧不得仍是旧档色 (动画必须在跑)", lightSurface, midSurface)

        // 落定: Expressive defaultEffects 走完, 到达 dark preset 终态且 ≠ 亮色
        compose.mainClock.advanceTimeBy(1500)
        compose.waitForIdle()
        val darkSurface = holder.lastSurface
        assertNotEquals("切暗后终态必须换色", lightSurface, darkSurface)
        assertNotEquals("中间帧不得已是新档终态色 (硬切=0ms 到档, 即红)", midSurface, darkSurface)

        // 往返: 切回亮色终态与初始逐位相等 (插值管线无漂移)。
        // 必须先短泵再长进: 手动时钟下 state 写的 invalidation 不在 advanceTimeBy
        // 里首帧落地 (首轮翻档靠中段帧循环泵了 5 拍才起飞, 直写直进 1500ms
        // 动画根本没启动 — 上一版此处假红即此因)。
        holder.dark = false
        repeat(5) {
            compose.mainClock.advanceTimeBy(16)
            compose.waitForIdle()
        }
        compose.mainClock.advanceTimeBy(1500)
        compose.waitForIdle()
        assertEquals("往返切档终态必须复现初始亮色", lightSurface, holder.lastSurface)

        assertEquals("全程零重挂载 (组合树形状稳定)", 1, holder.mountCount.value)
    }
}
