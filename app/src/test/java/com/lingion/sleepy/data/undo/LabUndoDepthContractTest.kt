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
        val start = source.indexOf("R.string.settings_lab")
            .takeIf { it >= 0 } ?: error("GeneralSettingsScreen 缺少实验室分组 — 契约锚点需同步")
        val end = source.indexOf("R.string.settings_migration", start)
            .takeIf { it >= 0 } ?: error("GeneralSettingsScreen 缺少数据迁移分组 — 契约锚点需同步")
        source.substring(start, end)
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
    fun `lab input filter allows minus sign for the unlimited tier`() {
        // -1 是法定输入; 照抄 KeyboardType.Number 无负号键的翻车先例
        assertTrue(
            "输入过滤正则必须含 -? — Number 键盘无负号键, 否则 -1 无法键入",
            "-?" in labSegment
        )
    }

    @Test
    fun `lab row rejects blank and unparsable input without persisting`() {
        // 空白/非法输入不落库: isBlank 早退 + toIntOrNull ?: return 双守卫
        assertTrue(
            "空白与非法输入必须早退不落库",
            "isBlank" in labSegment && "toIntOrNull() ?: return" in labSegment
        )
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
