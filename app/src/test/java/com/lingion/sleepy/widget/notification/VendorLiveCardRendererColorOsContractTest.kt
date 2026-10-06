package com.lingion.sleepy.widget.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ColorOS(OPPO/OnePlus/realme) 流体云 payload 契约测试。
 * 真机(RMX6688/ColorOS 16)验证过的行为:
 *   - 胶囊只渲染 small icon 位 + shortCriticalText(限宽约 6 字), 标题位不渲染;
 *   - ProgressStyle 才能渲染卡片底部倒计时进度条;
 *   - 小图标可用进度环位图(系统只取 alpha 通道着色)。
 */
class VendorLiveCardRendererColorOsContractTest {

    private val rendererSource: String
        get() = readRepoFile("app/src/main/java/com/lingion/sleepy/widget/notification/VendorLiveCardRenderer.kt")

    private val prefsSource: String
        get() = readRepoFile("app/src/main/java/com/lingion/sleepy/util/AppPrefs.kt")

    private fun readRepoFile(relative: String): String {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val file = File(dir, relative)
            if (file.isFile) return file.readText()
            dir = dir.parentFile
        }
        error("$relative not found")
    }

    @Test
    fun `coloros branch covers oppo oneplus and realme`() {
        val source = rendererSource
        assertTrue(
            "ColorOS branch must gate on all three OPPO-family vendors",
            source.contains("vendor == LiveCardVendor.OPPO") &&
                source.contains("vendor == LiveCardVendor.ONEPLUS") &&
                source.contains("vendor == LiveCardVendor.REALME")
        )
    }

    @Test
    fun `coloros card keeps progress style for the countdown bar`() {
        val source = rendererSource
        assertTrue(
            "ProgressStyle (not BigText) renders the bottom countdown bar on ColorOS",
            source.contains("setProgress(100, state.progress, false)") &&
                source.contains("setStyle(progressStyleFor(state.progress))")
        )
    }

    @Test
    fun `coloros card shows countdown subtext and middot-joined detail`() {
        val source = rendererSource
        assertTrue(
            "subText must carry the N-minutes countdown string",
            source.contains("R.string.fluid_coloros_countdown") &&
                source.contains("setSubText(countdown)")
        )
        assertTrue(
            "contentText must use the middot-joined time/room/teacher line",
            source.contains("setContentText(state.colorOsDetailText)")
        )
    }

    @Test
    fun `coloros capsule follows the user primary-field setting`() {
        val source = rendererSource
        assertTrue(
            "capsule text must reuse the shared primaryText slot (user setting), capped to 7 chars",
            source.contains("setShortCriticalText(primaryText.take(7))")
        )
        assertTrue(
            "countdown must be a selectable capsule field",
            source.contains("\"countdown\" -> context.getString(R.string.fluid_coloros_capsule, state.minutesLeft)")
        )
    }

    @Test
    fun `coloros small icon is the countdown progress ring`() {
        val source = rendererSource
        assertTrue(
            "ColorOS small icon must be the progress-ring bitmap",
            source.contains("progressRingIcon(state.progress)?.let { builder.setSmallIcon(it) }")
        )
        assertFalse(
            "the old fully-transparent icon helper must be gone",
            source.contains("transparentSmallIcon")
        )
    }

    @Test
    fun `fluid primary pref accepts the countdown value`() {
        // 白名单漏掉 countdown 会让设置页点击直接 require() 崩溃(真机复现过)。
        assertTrue(
            "setBeforeClassFluidPrimary must whitelist the countdown option",
            prefsSource.contains("value == \"countdown\"")
        )
    }
}
