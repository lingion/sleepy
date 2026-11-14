package com.lingion.sleepy.ui.screen.imports

import com.lingion.sleepy.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 锁定 AI 截图转换提示词的资源原文。
 *
 * 资源里的 XML 实体与反斜杠转义由 aapt 解码: 写成 &lt; 才会得到字符 <, 写成 &amp;lt; 会把字面量
 * "&lt;" 发给 AI; 写成 \n 会变成真实换行, 写成 \\n 才是给 AI 看的两个字符。
 * 这些错误肉眼难察, 故用测试锁住。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rCN")
class AiPromptTextTest {

    private val prompt: String
        get() = RuntimeEnvironment.getApplication().getString(R.string.ai_prompt_text)

    @Test
    fun promptContainsNoLeftoverXmlEntities() {
        assertFalse("提示词不得含字面量 &lt;", prompt.contains("&lt;"))
        assertFalse("提示词不得含字面量 &gt;", prompt.contains("&gt;"))
        assertFalse("提示词不得含字面量 &amp;", prompt.contains("&amp;"))
    }

    @Test
    fun escapeListShowsRealCharacters() {
        // 解析器 SleepyNativeFormat.RESERVED 的字符集: \ | " \n \t < { (
        // 提示词以「字符前加反斜杠」的说明列出, 显示文本为: | \ \" < { (
        assertTrue(prompt.contains("| \\ \\\" < { ("))
    }

    @Test
    fun escapeInstructionKeepsLiteralBackslashSequences() {
        assertTrue("换行说明必须是两个字符 \\n", prompt.contains("写成 \\n 两个字符"))
        assertTrue("制表符说明必须是两个字符 \\t", prompt.contains("制表符写成 \\t\n"))
    }

    @Test
    fun promptStartsWithSleepyV1Contract() {
        assertTrue(prompt.startsWith("任务："))
        assertTrue(prompt.contains("输出第一行必须是 #sleepy-v1"))
    }
}
