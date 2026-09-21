package com.lingion.sleepy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 锁定 Material 3 主题统一契约 —— 阶段 1: SleepyTheme.colors 双桥下线。
 *
 * 不变量:
 * 1. Compose UI 层**禁止**直接读 SleepyTheme.colors;必须走 MaterialTheme.colorScheme。
 * 2. WakeUpColorScheme 数据类/LocalWakeUpColors CompositionLocal 在 UI 层零引用,
 *    仅在 theme/ 内(Preset/Deriver)作为派生输入保留。
 * 3. Theme.kt 同时构造 m3Scheme 并喂 MaterialExpressiveTheme,保证两份同源;
 *    UI 屏拿到的就是官方 MaterialTheme.colorScheme。
 *
 * 与 WakeUpColorSchemeRemovalContractTest 互补:
 * - 那个锁 Compose UI 层零 SleepyTheme.colors 引用
 * - 这个锁 UI 层零 WakeUpColorScheme 签名引用
 *
 * 测试面全部用字符串扫描 —— 契约锁的是源码契约,不是运行时行为。
 */
class ThemeUnificationContractTest {

    private val mainSrcRoot = File("src/main/java")

    private val themeSrc by lazy {
        File("src/main/java/com/lingion/sleepy/ui/theme/Theme.kt").readText()
    }

    /** 列出 main 源集内所有 .kt 文件路径, 相对 mainSrcRoot */
    private val ktFiles: List<File> by lazy {
        mainSrcRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
    }

    /**
     * 「Compose UI 层」 = theme/ 包外 + theme/ 包内除 Theme.kt 之外的辅助文件
     * (Theme.kt 自身持有桥代码,被本契约豁免)。
     * 进一步: 持有 fun 派生 WakeUpColorScheme 的文件(CustomSchemeDeriver / ThemePresets)
     * 也豁免, 因签名是派生输入,不是 UI 消费。
     */
    private val uiLayerFiles: List<File> by lazy {
        ktFiles.filter { f ->
            val rel = f.relativeTo(mainSrcRoot).invariantSeparatorsPath
            // 排除 theme 包: 仅 theme/ 内部仍持桥代码(阶段 9 清理)
            !rel.startsWith("com/lingion/sleepy/ui/theme/")
        }
    }

    private fun readRel(rel: String): String =
        File("src/main/java/$rel").readText()

    // ─── Compose UI 层禁止 SleepyTheme.colors 直读 ───

    @Test
    fun compose_ui_layer_has_zero_sleepyTheme_colors_references() {
        // 锁契约: ui 包(theme/ 外) + widget/ 包 0 处 SleepyTheme.colors 引用。
        // 阶段 1 目标; 阶段 9 收敛到 widget/preview 内也不再用。
        val offenders = mutableListOf<String>()
        for (f in uiLayerFiles) {
            val rel = f.relativeTo(mainSrcRoot).invariantSeparatorsPath
            val src = f.readText()
            val hits = Regex("""SleepyTheme\.colors""").findAll(src).count()
            if (hits > 0) offenders += "$rel: $hits"
        }
        assertEquals(
            "Compose UI 层必须零 SleepyTheme.colors 引用(违规:\n  ${offenders.joinToString("\n  ")}\n)",
            0,
            offenders.size
        )
    }

    // ─── UI 层零 WakeUpColorScheme 签名引用 ───

    @Test
    fun compose_ui_layer_has_zero_WakeUpColorScheme_type_references() {
        // 锁: Compose UI 层 = ui/ 包(theme/ 外)。WakeUpColorScheme 类型只允许在
        // theme/(Presets/Deriver/Theme 桥) + widget/(Widget 桥, 阶段 7 收敛)出现。
        // util/data 里的注释提及不算类型引用, 不在本契约扫描面内。
        val offenders = mutableListOf<String>()
        for (f in uiLayerFiles) {
            val rel = f.relativeTo(mainSrcRoot).invariantSeparatorsPath
            if (!rel.startsWith("com/lingion/sleepy/ui/")) continue
            val src = f.readText()
            val hits = Regex("""WakeUpColorScheme""").findAll(src).count()
            if (hits > 0) offenders += "$rel: $hits"
        }
        assertEquals(
            "Compose UI 层(ui/ 除 theme/)必须零 WakeUpColorScheme 类型引用(违规:\n  ${offenders.joinToString("\n  ")}\n)",
            0,
            offenders.size
        )
    }

    @Test
    fun compose_ui_layer_has_zero_LocalWakeUpColors_references() {
        // 锁: UI 层零 LocalWakeUpColors CompositionLocal 出现(只在 theme/Theme.kt 内)。
        val offenders = mutableListOf<String>()
        for (f in uiLayerFiles) {
            val rel = f.relativeTo(mainSrcRoot).invariantSeparatorsPath
            val src = f.readText()
            val hits = Regex("""LocalWakeUpColors""").findAll(src).count()
            if (hits > 0) offenders += "$rel: $hits"
        }
        assertEquals(
            "UI 层必须零 LocalWakeUpColors 引用(违规:\n  ${offenders.joinToString("\n  ")}\n)",
            0,
            offenders.size
        )
    }

    // ─── UI 层官方 MaterialTheme.colorScheme 引用存在 ───

    @Test
    fun compose_ui_layer_uses_MaterialTheme_colorScheme_instead() {
        // 反向验证: UI 层出现 MaterialTheme.colorScheme 的文件数应 >= 10。
        // (44 文件原本用 SleepyTheme.colors, 阶段 1 替换后至少有 10 个文件在屏上用
        //  MaterialTheme.colorScheme 直读 + 其余走 LocalWakeUpColors → MaterialTheme.colorScheme
        //  链)
        val hits = uiLayerFiles.count { f ->
            Regex("""MaterialTheme\.colorScheme""").containsMatchIn(f.readText())
        }
        assertTrue(
            "UI 层必须有文件用 MaterialTheme.colorScheme(实测: $hits 个文件)",
            hits >= 10
        )
    }

    // ─── 测试/Instrumentation 不直接拿 SleepyTheme.colors ───

    @Test
    fun test_sources_have_zero_sleepyTheme_colors_references() {
        // 测试套件不应碰 UI 主题桥; UI 主题行为锁在本契约与 CustomSchemeDeriverTest。
        // 契约测试自身(本文件)含断言字符串, 豁免。
        val testRoot = File("src/test/java")
        if (!testRoot.isDirectory) return // 该模块无单测目录时跳过
        val offenders = mutableListOf<String>()
        testRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { f ->
                val rel = f.relativeTo(File("src")).invariantSeparatorsPath
                // 契约测试自身含被扫描字符串; CustomSchemeDeriverTest 允许派生结果类型。
                if (rel.endsWith("ThemeUnificationContractTest.kt")) return@forEach
                if (rel.endsWith("CustomSchemeDeriverTest.kt")) return@forEach
                val src = f.readText()
                val hits = Regex("""SleepyTheme\.colors""").findAll(src).count()
                if (hits > 0) offenders += "$rel: $hits"
            }
        assertEquals(
            "测试代码不应引用 SleepyTheme.colors(违规:\n  ${offenders.joinToString("\n  ")}\n)",
            0,
            offenders.size
        )
    }

    // ─── Theme.kt 自身仍维持同源派生(直到阶段 9 才删 WakeUpColorScheme) ───

    @Test
    fun Theme_kt_constructs_m3Scheme_and_feeds_ExpressiveTheme() {
        // 阶段 1 阶段 9 中间: Theme.kt 同时保留 WakeUpColorScheme(供 widget/preview 用)
        // 与 m3Scheme(供 MaterialTheme.colorScheme 用);两条必须同源派生。
        assertTrue(
            "Theme.kt 必须保留 WakeUpColorScheme 派生作为 widget/preview 输入",
            themeSrc.contains("WakeUpColorScheme(")
        )
        assertTrue(
            "Theme.kt 必须用 m3Scheme 喂 MaterialExpressiveTheme",
            Regex(
                """MaterialExpressiveTheme\(\s*[\s\S]{0,400}?colorScheme\s*=\s*m3Scheme"""
            ).containsMatchIn(themeSrc)
        )
    }
}