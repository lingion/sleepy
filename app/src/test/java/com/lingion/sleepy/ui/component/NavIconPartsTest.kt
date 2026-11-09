package com.lingion.sleepy.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

/**
 * 部件级动效契约 — 锁"每个图标拆成部件、各自动作"这件事本身。
 *
 * 拆分铁律(由原图标非零填充 raster 证据定): 洞子路径与承载它的外框同部件、
 * 同变换, 否则部件分开变换 → 洞被重新填满 → 静息外观偏离原图标。共享外框的
 * 分区(今日头带)用 clip 互裁, 两件子路径集合仍逐字节覆盖原 pathData。
 *
 * 真源 = [navIconPartsFor] 部件表 + 每部件 motion(pulse, spinRad) → [PartTransform]
 * 纯函数。几何单位 = vector 视口 24: pivot 在 0..24 域, 位移以视口单位计。
 * 静止态(pulse=0, spin=0)全部件恒等 → 与单 path 原图标逐位一致。
 */
class NavIconPartsTest {

    private val animatedKeys = listOf(
        NavIconMotionKey.PageFlip,
        NavIconMotionKey.Hop,
        NavIconMotionKey.GearSpin,
        NavIconMotionKey.Jelly,
    )

    private fun partsOf(key: NavIconMotionKey) = navIconPartsFor(key)!!

    @Test
    fun onlyAnimatedKeysHaveParts() {
        for (key in animatedKeys) assertNotNull("$key 应有部件表", partsOf(key))
        assertNull("None 键不应有部件表", navIconPartsFor(NavIconMotionKey.None))
    }

    @Test
    fun eachIconSplitsIntoIndependentParts() {
        for (key in animatedKeys) assertTrue("$key 部件数<2", partsOf(key).size >= 2)
        assertEquals("课表: 框体(+双洞)/6个日期格子", 7, partsOf(NavIconMotionKey.PageFlip).size)
        assertEquals("今日: 框体(+双洞)/中间方格", 2, partsOf(NavIconMotionKey.Hop).size)
        assertEquals("我的: 头(+内孔)/身(+内弧)", 2, partsOf(NavIconMotionKey.Jelly).size)
        assertEquals("管理: 齿轮环(+内齿孔)/核(+圆孔)", 2, partsOf(NavIconMotionKey.GearSpin).size)
    }

    @Test
    fun subpathsCoverOriginalExactly() {
        // 拆而不丢、不增: 部件子路径集合必须与原图标相等。
        for ((key, original) in mapOf(
            NavIconMotionKey.PageFlip to originalCal,
            NavIconMotionKey.Hop to originalToday,
            NavIconMotionKey.Jelly to originalPerson,
            NavIconMotionKey.GearSpin to originalSettings,
        )) {
            assertEquals(
                "$key 子路径集合 ≠ 原图标",
                splitSubpaths(original).toSet(),
                partsOf(key).flatMap { splitSubpaths(it.pathData) }.toSet(),
            )
        }
    }

    @Test
    fun holesWeldedToTheirFrame() {
        // 洞-框焊接铁律: 同向(加法)子路径不得与给它挖洞的反向子路径分离到不同部件。
        val cal = partsOf(NavIconMotionKey.PageFlip)
        assertTrue("课表外框应含上洞", cal[0].pathData.contains("M19,8"))
        assertTrue("课表外框应含下洞", cal[0].pathData.contains("M19,20"))

        val hop = partsOf(NavIconMotionKey.Hop)
        assertTrue("今日外框应含外廓", hop[0].pathData.contains("M19,3"))
        assertTrue("今日外框应含底板洞", hop[0].pathData.contains("M19,19"))
        assertTrue("今日外框应含头带洞", hop[0].pathData.contains("M19,7"))

        val gear = partsOf(NavIconMotionKey.GearSpin)
        assertTrue("齿环件应含内齿孔", gear[0].pathData.contains("M17.45"))
        assertTrue("核件应含圆孔", gear[1].pathData.contains("M12,14"))

        val jelly = partsOf(NavIconMotionKey.Jelly)
        assertTrue("人头件应含内圆孔", jelly[0].pathData.contains("M12,6"))
        assertTrue("身体件应含内下弧孔", jelly[1].pathData.contains("M12,16"))
    }

    @Test
    fun restState_everyPartIdentity() {
        // pulse=0, spin=0 → 所有部件恒等 → 静息图标逐位等于原图形。
        for (key in animatedKeys) {
            partsOf(key).forEachIndexed { i, p ->
                val t = p.motion(0f, 0f)
                assertEquals("$key[$i] scaleX", 1f, t.scaleX, 1e-4f)
                assertEquals("$key[$i] scaleY", 1f, t.scaleY, 1e-4f)
                assertEquals("$key[$i] tx", 0f, t.translationUnitsX, 1e-4f)
                assertEquals("$key[$i] ty", 0f, t.translationUnitsY, 1e-4f)
                assertEquals("$key[$i] rot", 0f, t.rotationZ, 1e-4f)
                assertEquals("$key[$i] alpha", 1f, t.alpha, 1e-4f)
            }
        }
    }

    @Test
    fun pivotsInsideViewport() {
        for (key in animatedKeys) {
            partsOf(key).forEachIndexed { i, p ->
                assertTrue("$key[$i] pivotX", p.pivotX in 0f..24f)
                assertTrue("$key[$i] pivotY", p.pivotY in 0f..24f)
            }
        }
    }

    @Test
    fun pathDataNonEmptyAndAbsoluteStart() {
        // 拆分自真值 XML 的子路径必须以绝对 M 开头(相对 m 拆开即断)。
        for (key in animatedKeys) {
            partsOf(key).forEachIndexed { i, p ->
                assertTrue("$key[$i] 空路径", p.pathData.isNotEmpty())
                assertTrue("$key[$i] 应以 M 开头", p.pathData.trimStart().startsWith("M"))
            }
        }
    }

    @Test
    fun pageFlip_frameSteady_dotsPopInSequentially() {
        val parts = partsOf(NavIconMotionKey.PageFlip)
        val frame = parts[0].motion(1f, 0f)
        // 框体恒定不动
        assertEquals(1f, frame.scaleX, 1e-4f)
        assertEquals(1f, frame.scaleY, 1e-4f)
        assertEquals(1f, frame.alpha, 1e-4f)

        // 峰值 p=1 (起始瞬间): 6 个格子全不可见
        for (i in 1..6) {
            val dot = parts[i].motion(1f, 0f)
            assertEquals(0f, dot.alpha, 1e-4f)
            assertEquals(0f, dot.scaleX, 1e-4f)
        }

        // 回落过程: 前面的点比后面的点先出现 (按索引顺序闪出)
        // p=0.7 (progress = 0.3): dot1 (start=0.0) 已出现，dot6 (start=0.5) 仍未出现
        val midDot1 = parts[1].motion(0.7f, 0f)
        val midDot6 = parts[6].motion(0.7f, 0f)
        assertTrue("前排点先出现", midDot1.alpha > midDot6.alpha)
        assertEquals(0f, midDot6.alpha, 1e-4f)

        // 静息 p=0: 全部点恢复 1f
        for (i in 1..6) {
            val dot = parts[i].motion(0f, 0f)
            assertEquals(1f, dot.alpha, 1e-4f)
            assertEquals(1f, dot.scaleX, 1e-4f)
        }
    }

    @Test
    fun hop_frameSteady_centerSquarePopsIn() {
        val parts = partsOf(NavIconMotionKey.Hop)
        val frame = parts[0].motion(1f, 0f)
        val squarePeak = parts[1].motion(1f, 0f)
        val squareMid = parts[1].motion(0.5f, 0f)
        val squareRest = parts[1].motion(0f, 0f)

        // 外框稳定不动
        assertEquals(1f, frame.scaleX, 1e-4f)
        assertEquals(1f, frame.scaleY, 1e-4f)
        assertEquals(0f, frame.translationUnitsY, 1e-4f)

        // 峰值 p=1: 中间方格隐去
        assertEquals(0f, squarePeak.alpha, 1e-4f)
        assertEquals(0f, squarePeak.scaleX, 1e-4f)

        // 中间过程 p=0.5: 正在闪出
        assertTrue("中间过程方格出现", squareMid.alpha > 0f)
        assertTrue("中间过程方格缩放生长", squareMid.scaleX > 0f)

        // 静息 p=0: 完整恒等显示
        assertEquals(1f, squareRest.alpha, 1e-4f)
        assertEquals(1f, squareRest.scaleX, 1e-4f)
    }

    @Test
    fun jelly_bodyFixed_headWigglesLeftAndRight() {
        val parts = partsOf(NavIconMotionKey.Jelly)
        // 任意脉冲下，下半身绝对固定不动
        for (p in floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            val body = parts[1].motion(p, 0f)
            assertEquals(1f, body.scaleX, 1e-4f)
            assertEquals(1f, body.scaleY, 1e-4f)
            assertEquals(0f, body.translationUnitsX, 1e-4f)
            assertEquals(0f, body.translationUnitsY, 1e-4f)
            assertEquals(0f, body.rotationZ, 1e-4f)
        }

        // 人头左右摇晃，不形变 (scaleX == scaleY == 1f)
        val headPeak = parts[0].motion(0.5f, 0f)
        assertEquals(1f, headPeak.scaleX, 1e-4f)
        assertEquals(1f, headPeak.scaleY, 1e-4f)

        // 运动过程中存在正向与反向摇晃角度
        val rotations = listOf(0.875f, 0.625f, 0.375f, 0.125f).map { parts[0].motion(it, 0f).rotationZ }
        assertTrue("人头产生正向摇晃", rotations.any { it > 0.5f })
        assertTrue("人头产生反向摇晃", rotations.any { it < -0.5f })

        // 静息态复位
        val headRest = parts[0].motion(0f, 0f)
        assertEquals(0f, headRest.rotationZ, 1e-4f)
        assertEquals(0f, headRest.translationUnitsX, 1e-4f)
    }

    @Test
    fun gearSpin_ringRotates_corePumps() {
        val parts = partsOf(NavIconMotionKey.GearSpin)
        val ring = parts[0].motion(0f, PI.toFloat() / 2f)
        val core = parts[1].motion(0f, PI.toFloat() / 2f)
        // 环跟累计弧度转(不取模保圈数), 核不转(圆转不可见) — 核的戏在脉冲泵。
        assertEquals(90f, ring.rotationZ, 1e-3f)
        assertEquals(0f, core.rotationZ, 1e-4f)
        val corePump = parts[1].motion(0.5f, 0f)
        assertTrue("核应泵", corePump.scaleX > 1f && corePump.scaleY > 1f)
        // 核等向缩放(泵圆不椭)。
        assertEquals(corePump.scaleX, corePump.scaleY, 1e-4f)
    }

    @Test
    fun partsDifferWithinSameIcon() {
        // "各自动作"在部件粒度成立: 同图标内部件在运动中段变换两两不同。
        for (key in animatedKeys) {
            val ts = partsOf(key).map { it.motion(0.5f, PI.toFloat() / 2f) }
            for (i in ts.indices) for (j in i + 1 until ts.size) {
                assertTrue("$key 部件 $i/$j 动作撞车", ts[i] != ts[j])
            }
        }
    }

    companion object {
        // 用户给定真值 (Tab 图标的 android:pathData, viewport 24)。
        const val originalCal =
            "M19,4h-1V2h-2v2H8V2H6v2H5C3.89,4 3.01,4.9 3.01,6L3,20c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2V6C21,4.9 20.1,4 19,4z" +
                "M19,20H5V10h14V20zM19,8H5V6h14V8z" +
                "M9,14H7v-2h2V14zM13,14h-2v-2h2V14zM17,14h-2v-2h2V14z" +
                "M9,18H7v-2h2V18zM13,18h-2v-2h2V18zM17,18h-2v-2h2V18z"
        const val originalToday =
            "M19,3h-1V1h-2v2H8V1H6v2H5c-1.11,0 -2,0.9 -2,2v14c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2z" +
                "M19,19H5V9h14v10z" +
                "M19,7H5V5h14v2z" +
                "M7,11h5v5H7z"
        const val originalPerson =
            "M12,4C9.79,4 8,5.79 8,8s1.79,4 4,4 4,-1.79 4,-4 -1.79,-4 -4,-4z" +
                "M12,6c1.1,0 2,0.9 2,2s-0.9,2 -2,2 -2,-0.9 -2,-2 0.9,-2 2,-2z" +
                "M12,14c-2.67,0 -8,1.34 -8,4v2h16v-2c0,-2.66 -5.33,-4 -8,-4z" +
                "M12,16c2.7,0 5.8,1.29 6,2L6,18c0.23,-0.72 3.31,-2 6,-2z"
        const val originalSettings =
            "M19.43,12.98c0.04,-0.32 0.07,-0.64 0.07,-0.98 0,-0.34 -0.03,-0.66 -0.07,-0.98l2.11,-1.65c0.19,-0.15 0.24,-0.42 0.12,-0.64" +
                "l-2,-3.46c-0.09,-0.16 -0.26,-0.25 -0.44,-0.25 -0.06,0 -0.12,0.01 -0.17,0.03l-2.49,1c-0.52,-0.4 -1.08,-0.73 -1.69,-0.98l-0.38,-2.65" +
                "C14.46,2.18 14.25,2 14,2h-4c-0.25,0 -0.46,0.18 -0.49,0.42l-0.38,2.65c-0.61,0.25 -1.17,0.59 -1.69,0.98l-2.49,-1c-0.06,-0.02 -0.12,-0.03" +
                " -0.18,-0.03 -0.17,0 -0.34,0.09 -0.43,0.25l-2,3.46c-0.13,0.22 -0.07,0.49 0.12,0.64l2.11,1.65c-0.04,0.32 -0.07,0.65 -0.07,0.98" +
                " 0,0.33 0.03,0.66 0.07,0.98l-2.11,1.65c-0.19,0.15 -0.24,0.42 -0.12,0.64l2,3.46c0.09,0.16 0.26,0.25 0.44,0.25 0.06,0 0.12,-0.01" +
                " 0.17,0.03l2.49,-1c0.52,0.4 1.08,0.73 1.69,0.98l0.38,2.65c0.03,0.24 0.24,0.42 0.49,0.42h4c0.25,0 0.46,-0.18 0.49,-0.42l0.38,-2.65" +
                "c0.61,-0.25 1.17,-0.59 1.69,-0.98l2.49,1c0.06,0.02 0.12,0.03 0.18,0.03 0.17,0 0.34,-0.09 0.43,-0.25l2,-3.46c0.12,-0.22 0.07,-0.49" +
                " -0.12,-0.64l-2.11,-1.65z" +
                "M17.45,11.27c0.04,0.31 0.05,0.52 0.05,0.73 0,0.21 -0.02,0.43 -0.05,0.73l-0.14,1.13 0.89,0.7 1.08,0.84" +
                " -0.7,1.21 -1.27,-0.51 -1.04,-0.42 -0.9,0.68c-0.43,0.32 -0.84,0.56 -1.25,0.73l-1.06,0.43 -0.16,1.13 -0.2,1.35h-1.4l-0.19,-1.35" +
                " -0.16,-1.13 -1.06,-0.43c-0.43,-0.18 -0.83,-0.41 -1.23,-0.71l-0.91,-0.7 -1.06,0.43 -1.27,0.51 -0.7,-1.21 1.08,-0.84 0.89,-0.7" +
                " -0.14,-1.13c-0.03,-0.31 -0.05,-0.54 -0.05,-0.74s0.02,-0.43 0.05,-0.73l0.14,-1.13 -0.89,-0.7 -1.08,-0.84 0.7,-1.21 1.27,0.51" +
                " 1.04,0.42 0.9,-0.68c0.43,-0.32 0.84,-0.56 1.25,-0.73l1.06,-0.43 0.16,-1.13 0.2,-1.35h1.39l0.19,1.35 0.16,1.13 1.06,0.43" +
                "c0.43,0.18 0.83,0.41 1.23,0.71l0.91,0.7 1.06,-0.43 1.27,-0.51 0.7,1.21 -1.07,0.85 -0.89,0.7 0.14,1.13z" +
                "M12,8c-2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4 -1.79,-4 -4,-4z" +
                "M12,14c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2 2,0.9 2,2 -0.9,2 -2,2z"
    }
}
