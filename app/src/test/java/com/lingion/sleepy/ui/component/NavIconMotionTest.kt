package com.lingion.sleepy.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/**
 * 图标动效契约 — 锁"每个图标各自动作"这件事本身(非实现细节)。
 *
 * 真源 = [transformFor] 纯函数(key × pulse/spin → 变换), applyTo 只灌进
 * GraphicsLayerScope。因此本测试无需帧时钟/协程/组合, 逐值确定可复现:
 * - 静止态(pulse=0, spin 不变)四键全恒等 → 静息图标零位移;
 * - 四键峰值动作互不相同 → "各自动作"契约(而非四个都缩放);
 * - 各键按其剧本峰值校验具体几何(PageFlip 压扁/Hop 跃起/GearSpin 90 度/Jelly 鼓瘪)。
 */
class NavIconMotionTest {

    private val h = 28f // NavDockSpec.iconSize = 28dp 基准高

    @Test
    fun restState_allKeysIdentity() {
        // pulse=0, spin=0 → 每键都必须是零变换(静息图标不许有任何偏移)。
        for (key in NavIconMotionKey.entries) {
            val t = transformFor(key, pulse = 0f, spinRad = 0f, heightPx = h)
            assertEquals("$key scaleX", 1f, t.scaleX, 1e-4f)
            assertEquals("$key scaleY", 1f, t.scaleY, 1e-4f)
            assertEquals("$key translationY", 0f, t.translationY, 1e-4f)
            assertEquals("$key rotationZ", 0f, t.rotationZ, 1e-4f)
        }
    }

    @Test
    fun fourKeys_peakActionsAreDistinct() {
        // "每个图标各自动作"的核心: 四键在各自剧本峰值下变换必须两两不同。
        val pageFlip = transformFor(NavIconMotionKey.PageFlip, 1f, 0f, h)
        val hop = transformFor(NavIconMotionKey.Hop, 1f, 0f, h)
        val gear = transformFor(NavIconMotionKey.GearSpin, 0f, PI.toFloat() / 2f, h)
        val jelly = transformFor(NavIconMotionKey.Jelly, 0.5f, 0f, h) // sin 峰在 0.5
        val all = listOf(pageFlip, hop, gear, jelly)
        all.forEachIndexed { i, a ->
            all.forEachIndexed { j, b ->
                if (i < j) assertNotEquals("键 $i 与 $j 动作撞车", a, b)
            }
        }
    }

    @Test
    fun pageFlip_peakSquashesVertically_stretchesHorizontally() {
        val t = transformFor(NavIconMotionKey.PageFlip, pulse = 1f, spinRad = 0f, heightPx = h)
        assertEquals(1f - 0.22f, t.scaleY, 1e-4f)
        assertEquals(1f + 0.08f, t.scaleX, 1e-4f)
        assertEquals(0f, t.rotationZ, 1e-4f)
        assertEquals(0f, t.translationY, 1e-4f)
    }

    @Test
    fun hop_peakLiftsByNineOfTwentyEightHeight() {
        val t = transformFor(NavIconMotionKey.Hop, pulse = 1f, spinRad = 0f, heightPx = h)
        // -9dp 位移随 28 基准高缩放 → 高 28 时正好 -9px 单位。
        assertEquals(-9f, t.translationY, 1e-4f)
        // 跃起是纯位移, 不缩放不旋转。
        assertEquals(1f, t.scaleX, 1e-4f)
        assertEquals(1f, t.scaleY, 1e-4f)
        assertEquals(0f, t.rotationZ, 1e-4f)
    }

    @Test
    fun gearSpin_rotationFollowsCumulativeRadians_notPulse() {
        val quarter = transformFor(NavIconMotionKey.GearSpin, 0f, PI.toFloat() / 2f, h)
        assertEquals(90f, quarter.rotationZ, 1e-3f)
        // 累计两圈 = 180 度(不取模): 圈数必须线性累加。
        val half = transformFor(NavIconMotionKey.GearSpin, 0f, PI.toFloat(), h)
        assertEquals(180f, half.rotationZ, 1e-3f)
        // 齿轮只转不缩放不位移。
        assertEquals(1f, half.scaleX, 1e-4f)
        assertEquals(0f, half.translationY, 1e-4f)
    }

    @Test
    fun jelly_peakBulgesWideSquatsShort_sineEnvelope() {
        // 相位 0.5 = sin 峰: 横向鼓 +0.16、纵向瘪 -0.14。
        val t = transformFor(NavIconMotionKey.Jelly, pulse = 0.5f, spinRad = 0f, heightPx = h)
        val s = sin(0.5f * PI.toFloat()) // = 1
        assertEquals(1f + 0.16f * s, t.scaleX, 1e-4f)
        assertEquals(1f - 0.14f * s, t.scaleY, 1e-4f)
        // 鼓与瘪反向(面积近似守恒): 一增一减。
        assertTrue(t.scaleX > 1f)
        assertTrue(t.scaleY < 1f)
    }

    @Test
    fun noneKey_alwaysIdentity_regardlessOfPhase() {
        // None 不参与动效: 任意 pulse/spin 都恒等(其它 tab 形态共用同一表时不误动)。
        for (pulse in floatArrayOf(0f, 0.5f, 1f)) {
            val t = transformFor(NavIconMotionKey.None, pulse, pulse, h)
            assertEquals(1f, t.scaleX, 0f)
            assertEquals(1f, t.scaleY, 0f)
            assertEquals(0f, t.translationY, 0f)
            assertEquals(0f, t.rotationZ, 0f)
        }
    }

    @Test
    fun selectionEdge_onlyNewlySelectedItemFires() {
        // shouldActivate = 边沿判定唯一真源(与 effect 内联同一函数, 防双实现漂移)。
        assertFalse(shouldActivate(selectedNow = false, wasSelected = false))
        assertFalse(shouldActivate(selectedNow = true, wasSelected = true))
        assertTrue(shouldActivate(selectedNow = true, wasSelected = false))
        assertFalse(shouldActivate(selectedNow = false, wasSelected = true))
    }
}
