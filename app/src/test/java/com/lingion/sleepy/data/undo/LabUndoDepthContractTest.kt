package com.lingion.sleepy.data.undo

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 实验室撤回步数设置行契约 — 多步撤回 spec Task 3。
 *
 * 设置行是"真开关"而非装饰: UI 必须把输入写进 AppPrefs 并同步调用
 * UndoManager.applyMaxDepth; 冷启动 SleepyApp.onCreate 必须把持久值应用到
 * UndoManager, 否则改完重启失效。照 UndoCaptureCoverageTest 的"读源头文件
 * 当契约"双路径 File 先例(仓库无 Robolectric, Compose 段不可 JVM 渲染)。
 */
class LabUndoDepthContractTest {

    private fun readSource(vararg paths: String): String =
        paths.asSequence().map { File(it) }
            .first { it.isFile }.readText()

    /** GeneralSettingsScreen 源码: 实验室分组起点 → settings_migration 分组之间。 */
    private val labSegment: String by lazy {
        val source = readSource(
            "app/src/main/java/com/lingion/sleepy/ui/screen/mine/GeneralSettingsScreen.kt",
            "src/main/java/com/lingion/sleepy/ui/screen/mine/GeneralSettingsScreen.kt"
        )
        assertTrue("Editing group must exist", source.contains("R.string.settings_editing"))
        assertTrue("Experimental status must remain visible", source.contains("R.string.settings_undo_experiment"))
        source
    }

    private val sleepyAppSource: String by lazy {
        readSource(
            "app/src/main/java/com/lingion/sleepy/SleepyApp.kt",
            "src/main/java/com/lingion/sleepy/SleepyApp.kt"
        )
    }

    @Test
    fun `lab row wires string resource, prefs write and manager apply`() {
        // 缺一即设置行是装饰不是开关
        assertTrue(
            "设置行必须引用标题资源、落库并立即应用 — 缺一即 UI 是装饰",
            "stringResource(R.string.settings_undo_depth)" in labSegment &&
                "AppPrefs.setUndoDepth" in labSegment &&
                "UndoManager.applyMaxDepth" in labSegment
        )
    }

    @Test
    fun `undo mode exposes off limited and unlimited without magic number entry`() {
        listOf("settings_undo_off", "settings_undo_limited", "settings_undo_unlimited").forEach {
            assertTrue("Explicit undo mode missing: $it", labSegment.contains(it))
        }
        assertTrue(labSegment.contains("0 -> 0; 2 -> -1"))
    }

    @Test
    fun `limited undo depth rejects empty zero and malformed input`() {
        assertTrue(labSegment.contains("value.length <= 6 && value.all { it.isDigit() }"))
        assertTrue(labSegment.contains("value.toIntOrNull()?.takeIf { it > 0 }?.let { setUndoDepth(it) }"))
        assertTrue(labSegment.contains("isError = customDepth.toIntOrNull()?.let { it > 0 } != true"))
    }

    @Test
    fun `cold start applies persisted undo depth`() {
        assertTrue(
            "SleepyApp 必须同时读偏好并应用, 冷启动不应用则设置改完重启失效",
            "getUndoDepth" in sleepyAppSource && "applyMaxDepth" in sleepyAppSource
        )
    }

    @Test
    fun `all six locales carry the undo depth strings`() {
        // values-night 仅有 colors.xml/themes.xml, 无 UI 文案, 故不进清单
        val locales = listOf(
            "values", "values-zh-rCN", "values-zh-rTW", "values-en", "values-es", "values-ja"
        )
        val missing = locales.filter { dir ->
            val text = readSource(
                "app/src/main/res/$dir/strings.xml",
                "src/main/res/$dir/strings.xml"
            )
            "name=\"settings_undo_depth\"" !in text || "name=\"settings_undo_depth_sub\"" !in text
        }
        assertTrue("locale 缺撤回步数文案: $missing", missing.isEmpty())
    }

    @Test
    fun `lab segment references the title exactly once`() {
        val occurrences = Regex("""R\.string\.settings_undo_depth\b""").findAll(labSegment).count()
        assertTrue(
            "标题引用应恰一次(输入框 contentDescription 用 settings_undo_depth_label, 不同名), 出现 $occurrences 次 — 防复制粘贴双行",
            occurrences == 1
        )
    }
}
