package com.lingion.sleepy.console

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Task 8 Step 3 — AGENT-SKILL.md 与端点注册表双向一致性（防文档漂移）：
 *  - 前向: 源码里每一处 ConsoleBootstrap.addHandler("path","METHOD") 的字面
 *    对必须出现在文档端点表;
 *  - 反向: 文档端点表里每一对 path+method 必须能在源码找到注册点。
 *
 * 扫源码字面而非 CommandRouter 运行期表: JVM 单测装配不到 debug 源集与
 * Android 运行时; 设备侧运行期真值表由 ConsoleAcceptanceAndroidTest 兜底
 * （hello paths vs 同一文档）。
 */
class ConsoleDocContractTest {

    private fun sourceRoot(): File {
        val candidates = listOf(File("src/main/java"), File("app/src/main/java"))
        return candidates.firstOrNull { it.isDirectory } ?: error("main 源码目录不可达")
    }

    private fun debugRoot(): File {
        val candidates = listOf(File("src/debug/java"), File("app/src/debug/java"))
        return candidates.firstOrNull { it.isDirectory } ?: error("debug 源码目录不可达")
    }

    private fun doc(): File {
        val candidates = listOf(
            File("../docs/agent-debug-console/AGENT-SKILL.md"),       // cwd=app/
            File("docs/agent-debug-console/AGENT-SKILL.md"),         // cwd=repo 根
        )
        return candidates.firstOrNull { it.isFile } ?: error("AGENT-SKILL.md 不可达")
    }

    private val addHandlerCall = Regex("""addHandler\(\s*"(/v1/[a-z0-9/_-]+)"\s*,\s*"([A-Z]+)"""")

    private fun registeredPairs(): Set<String> {
        val pairs = mutableSetOf<String>()
        listOf(sourceRoot(), debugRoot()).forEach { root ->
            root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
                addHandlerCall.findAll(f.readText()).forEach { m ->
                    pairs += "${m.groupValues[2]} ${m.groupValues[1]}"
                }
            }
        }
        return pairs
    }

    /** 文档表行 "| METHOD | /v1/path |" 提取。 */
    private fun documentedPairs(): Set<String> {
        val row = Regex("""^\|\s*([A-Z]+)\s*\|\s*(/v1/[a-z0-9/_-]+)\s*\|""")
        return doc().readLines().mapNotNull { l ->
            row.find(l)?.let { "${it.groupValues[1]} ${it.groupValues[2]}" }
        }.toSet()
    }

    @Test
    fun everyRegisteredEndpoint_isDocumented() {
        val missing = registeredPairs() - documentedPairs()
        assertTrue("注册了但文档缺行(改端点必须同批改 AGENT-SKILL.md): $missing", missing.isEmpty())
    }

    @Test
    fun everyDocumentedEndpoint_isRegistered() {
        val ghost = documentedPairs() - registeredPairs()
        assertTrue("文档有但源码无注册(幽灵行): $ghost", ghost.isEmpty())
    }

    @Test
    fun registrySize_pinsSolemnity() {
        // 与 hello paths 数同量级的锚: 21 = 20 main 面 + 1 debug 面(trigger)
        assertEquals(21, registeredPairs().size)
    }
}
