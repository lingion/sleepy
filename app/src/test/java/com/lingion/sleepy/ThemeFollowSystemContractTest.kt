package com.lingion.sleepy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 主题「跟随系统」会话内跟随契约 — 用户报障:
 *   ColorOS(OPPO) 系统级主题管理切深浅色, 通知栏/快速设置切系统深浅,
 *   widget 能跟随变色但软件界面不变色。手动点模式卡片(浅色/深色/跟随系统)则一切正常。
 *
 * 根因: MainActivity 声明 android:configChanges="uiMode", 系统切深浅时 Activity
 *   不重建。Compose 的 isSystemInDarkTheme() 依赖 Activity 的 Configuration 快照,
 *   但 configChanges=uiMode 时 Android 更新 Resources.configuration 而不触发
 *   Compose recomposition — systemDark 永远停在首帧值, 即使 remember(systemDark)
 *   也无果(key 从未变化)。widget 能随是因为 Application.onConfigurationChanged
 *   回调通知远程视图重绘。
 *
 * 修复: 用 mutableStateOf(uiMode) 承接 Activity.onConfigurationChanged 的推送 →
 *   该 State 变化触发 Compose recomposition → systemDark 派生新值 →
 *   remember(systemDark) 重新计算 dark。
 *
 * 契约(结构锁定; 先例: BackRestoreSaveableContractTest —
 * 仓库无 Robolectric/Compose UI 测试, 声明式接线读源头文件等价于读编译产物):
 *   1. dark 状态必须 remember(systemDark) — systemDark 一变, dark 立即按
 *      AppPrefs.isDarkMode(ctx, 新systemDark) 重算;
 *   2. systemDark 必须由 onConfigurationChanged 可观察的 State 驱动, 而非
 *      isSystemInDarkTheme() 直接调用(后者在 configChanges=uiMode 下不会 recomposition);
 *   3. 手动路径保留: onThemeModeChange → applyTheme() 重算接线不得移除;
 *   4. 固定浅色/深色模式不受影响: isDarkMode 对 light/dark 返回常量.
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

    /** 契约 1 新增: systemDark 必须由 onConfigurationChanged 可观察的 State 驱动 —
     * isSystemInDarkTheme() 在 configChanges="uiMode" 下不会 recomposition, 故必须
     * 通过 State 承接 onConfigurationChanged 的推送。断言:
     *   a) 存在 mutableStateOf<uiMode> 字段 (composition-observed)
     *   b) onConfigurationChanged 覆写更新该 State
     *   c) systemDark 派生自该 State (非直接调用 isSystemInDarkTheme())
     *   d) 初始值赋值在 onCreate 内 — 属性初始化器读 resources 在构造函数阶段
     *      执行, attachBaseContext 未调, resources 访问 NPE → 启动秒崩
     *      (v1.0.55 测试包翻车点, 禁止回归) */
    @Test
    fun systemDark_is_driven_by_configuration_change_state() {
        // a) State 字段存在且类型为 Int (uiMode mask)
        val stateField = Regex("""private\s+val\s+\w+:\s*androidx\.compose\.runtime\.MutableState<Int>\s*=""")
        assertTrue(
            "MainActivity must hold a MutableState<Int> tracking uiMode for recomposition " +
                "when configChanges=\"uiMode\" prevents Activity recreation / Compose recomposition",
            stateField.containsMatchIn(mainSource)
        )
        // b) onConfigurationChanged 覆写存在且更新 State
        assertTrue(
            "MainActivity must override onConfigurationChanged",
            Regex("""override\s+fun\s+onConfigurationChanged\(""").containsMatchIn(mainSource)
        )
        val onConfigBody = Regex("""override\s+fun\s+onConfigurationChanged\([^)]*\)[^}]*\}""")
            .find(mainSource)?.value
            ?: ""
        assertTrue(
            "onConfigurationChanged must write to the uiMode state (e.g. .value = ... uiMode)",
            Regex("""\.value\s*=\s*\w+\.uiMode\s+and\s+Configuration\.UI_MODE_NIGHT_MASK""")
                .containsMatchIn(onConfigBody)
        )
        // c) systemDark 派生自 State, 而非直接 isSystemInDarkTheme()
        assertFalse(
            "systemDark must NOT use isSystemInDarkTheme() directly — it never recompositions " +
                "under configChanges=\"uiMode\"; must derive from the observed state instead",
            Regex("""systemDark\s*=\s*androidx\.compose\.foundation\.isSystemInDarkTheme\(\)""")
                .containsMatchIn(mainSource)
        )
        assertTrue(
            "systemDark must be derived from the configuration-change-tracked state",
            Regex("""val\s+systemDark\s*=\s*\(.*==\s*Configuration\.UI_MODE_NIGHT_YES\)""")
                .containsMatchIn(mainSource)
        )
        // d) 初始值必须在 onCreate 内赋值: 属性初始化器读 resources = 构造函数阶段
        //    访问 = attachBaseContext 未调 = NPE 秒崩。断言 onCreate 体内有
        //    uiNightModeState.value = resources.configuration.uiMode 赋值,
        //    且字段声明体不含 resources 读取。
        val onCreateBody = Regex("""override\s+fun\s+onCreate\(savedInstanceState:\s*Bundle\?\)[\s\S]{0,2000}""")
            .find(mainSource)?.value ?: ""
        assertTrue(
            "onCreate must seed uiNightModeState from resources.configuration (after " +
                "attachBaseContext) — property initializers run in the constructor where " +
                "resources is not yet attached (v1.0.55 launch crash)",
            Regex("""uiNightModeState\.value\s*=\s*\n?\s*resources\.configuration\.uiMode\s+and\s+Configuration\.UI_MODE_NIGHT_MASK""")
                .containsMatchIn(onCreateBody)
        )
    }

    /** 契约 5 (JwImportActivity 同修): 教务导入页同款 configChanges="uiMode" 声明,
     * 同款 State 驱动 + onCreate 种值, 禁止属性初始化器读 resources。 */
    @Test
    fun jwImportActivity_follows_same_contract() {
        val jwSource: String = loadSource("ui/screen/imports/JwImportActivity.kt")
        assertTrue(
            "JwImportActivity must override onConfigurationChanged writing the uiMode state",
            Regex("""override\s+fun\s+onConfigurationChanged\([^)]*\)[\s\S]{0,300}?\.value\s*=\s*newConfig\.uiMode""")
                .containsMatchIn(jwSource)
        )
        assertFalse(
            "JwImportActivity must NOT read resources in a property initializer (constructor " +
                "runs before attachBaseContext → NPE)",
            Regex("""private\s+val\s+uiNightModeState\s*=\s*mutableStateOf\(\s*\n?\s*resources\.""")
                .containsMatchIn(jwSource)
        )
        assertTrue(
            "JwImportActivity onCreate must seed uiNightModeState from resources.configuration",
            Regex("""uiNightModeState\.value\s*=\s*\n?\s*resources\.configuration\.uiMode""")
                .containsMatchIn(jwSource)
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
