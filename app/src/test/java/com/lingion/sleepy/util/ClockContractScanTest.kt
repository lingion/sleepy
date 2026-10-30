package com.lingion.sleepy.util

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Task 4 契约扫描（brief Step 1）：app/src/main/java 全部 .kt 源码里,
 * 裸 `LocalDate.now()` / `LocalTime.now()` 无参调用必须 0 处 —
 * 全 app 业务时间源一律收敛 AppClock, 虚拟时钟才能整体生效。
 *
 * AppClock.kt 自身豁免（它是真实时间的唯一合法入口）。
 * 测试跑在工作目录 = app 模块根（Gradle 单测默认）, src/main/java 可达。
 */
class ClockContractScanTest {

    private fun mainSourceRoot(): File {
        val candidates = listOf(
            File("src/main/java"),                       // cwd = app/
            File("app/src/main/java"),                   // cwd = repo 根
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("找不到 app main 源码目录, cwd=${File("").absolutePath}")
    }

    @Test
    fun noBareDateOrTimeNow_callsRemain() {
        val root = mainSourceRoot()
        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.name != "AppClock.kt" }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { lineNo, line ->
                    val hit = BareNow.pattern.containsMatchIn(line)
                    if (hit) "${file.relativeTo(root)}:${lineNo + 1}: ${line.trim()}" else null
                }
            }
            .toList()
        assertTrue(
            "裸 LocalDate.now()/LocalTime.now() 必须收敛为 (AppClock), 违例 ${offenders.size} 处:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    /** 只抓无参形态: `LocalDate.now()` 紧跟右括号; 带参 now(AppClock)/now(clock) 放行。 */
    private object BareNow {
        val pattern = Regex("""Local(?:Date|Time|DateTime)\.now\(\s*\)""")
    }
}
