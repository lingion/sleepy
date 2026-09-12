package com.lingion.sleepy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 主题「跟随系统」会话内跟随契约 — 用户报障:
 *   ColorOS(OPPO) 系统级主题管理切深浅色, Sleepy 内「深浅色跟随系统」不跟随;
 *   通知栏/快速设置切系统深浅同样不跟随。手动点模式卡片(浅色/深色/跟随系统)则一切正常。
 *
 * 根因: MainActivity 声明 android:configChanges="uiMode"(系统切深浅时 Activity 不重建),
 *   而 setContent 里 dark 状态是无 key 的 remember { mutableStateOf(...) } —
 *   初始化只跑一次, 之后系统 uiMode 变化只会让 isSystemInDarkTheme() 的 systemDark
 *   重组出新值, 但下游 dark 未绑该 key, 永远停在首帧取值(会话内冻结),
 *   直到用户手动碰模式卡片(applyTheme 路径)或冷启动。
 *
 * 修复契约(结构锁定; 先例: BackRestoreSaveableContractTest —
 * 仓库无 Robolectric/Compose UI 测试, 声明式接线读源头文件等价于读编译产物):
 *   1. dark 状态必须 remember(systemDark) — systemDark 一变(不管来自
 *      ColorOS 主题管理注入、通知栏切换还是任何 uiMode 来源), dark 立即按
 *      AppPrefs.isDarkMode(ctx, 新systemDark) 重算;
 *   2. 手动路径保留: onThemeModeChange → applyTheme() 重算 dark 的接线不得移除
 *      (模式切换当帧即生效, 不能等 systemDark 变);
 *   3. 固定浅色/深色模式不受影响: isDarkMode 对 light/dark 返回常量,
 *      systemDark 变化重算结果不变(此为行为论证, 结构上由契约 1 天然覆盖)。
 */
class ThemeFollowSystemContractTest {

    private fun loadSource(vararg relPaths: String): String =
        sequenceOf(
            java.io.File("app/src/main/java/com/lingion/sleepy/"),
            java.io.File("src/main/java/com/lingion/sleepy/"),
            java.io.File("/Users/lingion_k/sleepy/app/src/main/java/com/lingion/sleepy/")
        ).firstOrNull { it.isDirectory }?.let { root ->
            relPaths.map { java.io.File(root, it) }.firstOrNull { it.isFile }?.readText()
        } ?: error("Unable to load sources: ${relPaths.joinToString()}")

    private val mainSource: String by lazy { loadSource("MainActivity.kt") }
    private val manifestSource: String by lazy {
        sequenceOf(
            java.io.File("app/src/main/AndroidManifest.xml"),
            java.io.File("src/main/AndroidManifest.xml"),
            java.io.File("/Users/lingion_k/sleepy/app/src/main/AndroidManifest.xml")
        ).firstOrNull { it.isFile }?.readText() ?: error("Unable to load AndroidManifest.xml")
    }

    /** 契约 1: dark 必须以 systemDark 为 remember key — 会话内系统 uiMode 变化即时重算 */
    @Test
    fun dark_state_is_keyed_on_systemDark() {
        assertTrue(
            "dark must be remember(systemDark) { ... } — unkeyed remember freezes dark at its " +
                "first-frame value for the whole session when the activity handles uiMode " +
                "itself (configChanges=\"uiMode\", no recreation)",
            Regex("""var\s+dark\s+by\s+remember\s*\(\s*systemDark\s*\)\s*\{""")
                .containsMatchIn(mainSource)
        )
    }

    /** 契约 1 佐证: dark 的重算必须吃当前 systemDark(而非捕获旧值), 断言初始化体形如
     *  remember(systemDark) { mutableStateOf(AppPrefs.isDarkMode(ctx, systemDark)) } */
    @Test
    fun dark_rederivation_reads_current_systemDark() {
        val m = Regex(
            """remember\s*\(\s*systemDark\s*\)\s*\{\s*mutableStateOf\(\s*AppPrefs\.isDarkMode\(this@MainActivity,\s*systemDark\)\s*\)\s*\}"""
        ).find(mainSource)
        assertTrue(
            "dark initializer must call AppPrefs.isDarkMode(this@MainActivity, systemDark) " +
                "so each systemDark flip re-derives from the live system value",
            m != null
        )
    }

    /** 契约 2: 手动路径保留 — applyTheme() 重算接线不得移除 */
    @Test
    fun manual_mode_switch_path_is_preserved() {
        assertTrue(
            "applyTheme() must remain and reassign dark from AppPrefs.isDarkMode",
            Regex("""fun\s+applyTheme\(\)\s*\{\s*dark\s*=\s*AppPrefs\.isDarkMode\(this@MainActivity,\s*systemDark\)\s*\}""")
                .containsMatchIn(mainSource)
        )
        assertTrue(
            "onThemeModeChange must still call applyTheme() so a manual card tap takes effect " +
                "in the same frame (not waiting for a systemDark change)",
            Regex("""onThemeModeChange[\s\S]{0,400}?applyTheme\(\)""").containsMatchIn(mainSource)
        )
    }

    /** 根因锚: MainActivity 确实自带 uiMode 处理(不重建) — 若有人移除该声明,
     *  契约 1 虽不再必要但保持正确; 此断言防的是"误以为会重建而不修 Compose 侧"的回归误判 */
    @Test
    fun mainActivity_handles_uiMode_itself() {
        val activityTag = Regex("""<activity[^>]*android:name="\.MainActivity"[^>]*>""").find(manifestSource)
        assertTrue("MainActivity <activity> tag not found in manifest", activityTag != null)
        val nextActivity = manifestSource.indexOf("<activity", activityTag!!.range.last + 1)
        val body = manifestSource.substring(activityTag.range.first,
            if (nextActivity > 0) nextActivity else manifestSource.length)
        assertTrue(
            "MainActivity must keep declaring android:configChanges containing uiMode; " +
                "this test anchors the root-cause premise (no recreation on system dark toggle)",
            Regex("""android:configChanges="[^"]*\buiMode\b[^"]*"""").containsMatchIn(body)
        )
        assertFalse(
            "sanity: activity tag body must not be confused with other activities",
            body.contains("JwImportActivity")
        )
    }
}
