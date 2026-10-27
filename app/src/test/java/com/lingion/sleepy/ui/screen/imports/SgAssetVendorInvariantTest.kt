package com.lingion.sleepy.ui.screen.imports

import com.lingion.sleepy.testutil.projectSourceFile
import com.lingion.sleepy.testutil.readProjectSource
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * sg_asset 通路跨语言 invariant — 上游协议形态: shiguang_warehouse (MIT, 星河欲转)。
 *
 * 锁死三类事实 (任何一条漂移都必须显式重跑 vendor 脚本 + 修订本测试):
 *  1. 桥接 shim 的方法名集合 = vendor d.ts (scripts/types/shiguang-bridge.d.ts)
 *     两个接口的全部 9 个方法, 一个不多一个不少; 四个 window 挂载名 (含两个
 *     deprecated 别名) 全部就位。
 *  2. shim 只做传输与无头自动应答, 禁解码课程字段 — 课程 JSON 唯一解码落点
 *     = JwSgAssetParser (Kotlin 侧)。
 *  3. manifest 内部自洽: 每条 jsPath 落盘存在且 sha256 相符、schema/来源钉死;
 *     目录里所有 type=sg_asset 的校必须有 manifest 条目 (运行前置判定 hasAsset);
 *     本地 gap-rows 264 校全集 100% 被 manifest 覆盖 (仅本地断言, CI 无该文件时跳过)。
 */
class SgAssetVendorInvariantTest {

    private val bridgeSource: String = readProjectSource(
        "app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwSgAssetBridge.kt"
    )

    private val dts: String = projectSourceFile("scripts/types/shiguang-bridge.d.ts").readText()

    private val manifestJson: JSONObject = JSONObject(
        projectSourceFile("app/src/main/assets/third_party/shiguang/manifest.json").readText()
    )

    /** 去注释后提取接口体内的方法名集合。 */
    private fun interfaceMethodNames(body: String): Set<String> {
        val noComments = body
            .replace(Regex("/\\*\\*?[\\s\\S]*?\\*/"), "")
            .replace(Regex("//[^\n]*"), "")
        return Regex("""^\s{4}([A-Za-z]\w*)\(""", RegexOption.MULTILINE)
            .findAll(noComments)
            .map { it.groupValues[1] }
            .toSet()
    }

    private fun dtsPromiseMethods(): Set<String> {
        val start = dts.indexOf("interface ShiguangBridgePromise {")
        val end = dts.indexOf("interface ShiguangBridge {")
        assertTrue("d.ts must declare ShiguangBridgePromise", start in 0 until end)
        return interfaceMethodNames(dts.substring(start, end))
    }

    private fun dtsSyncMethods(): Set<String> {
        val start = dts.indexOf("interface ShiguangBridge {")
        val end = dts.indexOf("declare global")
        assertTrue("d.ts must declare ShiguangBridge", start in 0 until end)
        return interfaceMethodNames(dts.substring(start, end))
    }

    /** shim 字符串里 promiseApi / syncApi 两个块的 `name: function(` 集合。 */
    private fun shimMethods(api: String): Set<String> {
        val start = bridgeSource.indexOf("var $api = {")
        assertTrue("shim must declare $api", start >= 0)
        val rest = bridgeSource.substring(start)
        val end = rest.indexOf("\n  };")
        assertTrue("shim $api block must close", end > 0)
        return Regex("""(\w+):\s*function\(""").findAll(rest.substring(0, end))
            .map { it.groupValues[1] }
            .toSet()
    }

    @Test
    fun shim_covers_every_dts_promise_method_exactly() {
        val declared = dtsPromiseMethods()
        assertEquals("d.ts promiseApi 应为 7 方法", 7, declared.size)
        assertEquals(
            "shim promiseApi 方法名必须与 d.ts 逐名相等",
            declared,
            shimMethods("promiseApi")
        )
    }

    @Test
    fun shim_covers_every_dts_sync_method_exactly() {
        val declared = dtsSyncMethods()
        assertEquals("d.ts syncApi 应为 2 方法", 2, declared.size)
        assertEquals(
            "shim syncApi 方法名必须与 d.ts 逐名相等",
            declared,
            shimMethods("syncApi")
        )
    }

    @Test
    fun shim_mounts_all_four_window_names_including_deprecated_aliases() {
        for (name in listOf("shiguangBridgePromise", "shiguangBridge", "AndroidBridgePromise", "AndroidBridge")) {
            assertTrue("shim 必须挂载 window.$name", bridgeSource.contains("window.$name ="))
        }
    }

    @Test
    fun shim_never_decodes_course_fields_parser_is_the_only_sink() {
        // 课程字段 (d.ts saveImportedCourses 合同) 一律透传字符串, shim 禁按字段解码。
        for (field in listOf("startSection", "endSection", "isCustomTime", "\"day\"", "customStartTime")) {
            assertFalse("shim 禁解码课程字段 $field", bridgeSource.contains(field))
        }
    }

    @Test
    fun manifest_is_internally_consistent_with_vendored_files() {
        assertEquals("sleepy-sg-asset-manifest-v1", manifestJson.getString("schema"))
        val source = manifestJson.getJSONObject("source")
        assertEquals("shiguang_warehouse", source.getString("repo").substringAfterLast('/'))
        assertEquals("MIT", source.getString("license"))
        val scripts = manifestJson.getJSONArray("scripts")
        val schools = manifestJson.getJSONArray("schools")
        val jsDir = projectSourceFile("app/src/main/assets/third_party/shiguang/js")
        assertEquals(
            "落盘 .js 文件数必须等于 manifest scripts 数",
            scripts.length(),
            jsDir.walkTopDown().count { it.isFile && it.name.endsWith(".js") }
        )
        val scriptPaths = (0 until scripts.length())
            .associateBy({ scripts.getJSONObject(it).getString("path") }, { scripts.getJSONObject(it) })
        for (i in 0 until schools.length()) {
            val adapters = schools.getJSONObject(i).getJSONArray("adapters")
            assertTrue("每校至少一个适配器", adapters.length() > 0)
            for (j in 0 until adapters.length()) {
                val a = adapters.getJSONObject(j)
                // 适配器 jsPath 带 assets 内完整前缀, scripts.path 相对 manifest 目录
                val rel = a.getString("jsPath").removePrefix("third_party/shiguang/")
                assertTrue("jsPath 必须在 scripts 清单: ${a.getString("jsPath")}", scriptPaths.containsKey(rel))
                val file = projectSourceFile("app/src/main/assets/third_party/shiguang/$rel")
                assertEquals(
                    "asset 字节漂移: $rel",
                    scriptPaths.getValue(rel).getString("sha256"),
                    MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                        .joinToString("") { "%02x".format(it) }
                )
                assertTrue("适配器必须声明 importUrl", a.getString("importUrl").isNotBlank())
            }
        }
    }

    @Test
    fun every_catalog_sg_asset_entry_has_a_manifest_adapter() {
        val schoolsJson = JSONArray(
            projectSourceFile("app/src/main/assets/schools.json").readText()
        )
        val manifestSchools = manifestJson.getJSONArray("schools")
        val matchKeys = (0 until manifestSchools.length())
            .map { manifestSchools.getJSONObject(it).getString("matchKey") }
            .toSet()
        val orphans = (0 until schoolsJson.length())
            .map { schoolsJson.getJSONObject(it) }
            .filter { it.optString("type") == "sg_asset" }
            .map { it.getString("name").trim() }
            .filter { JwSgAssetNfk(it) !in matchKeys }
        assertEquals("目录 sg_asset 条目缺 manifest 适配器: $orphans", 0, orphans.size)
    }

    @Test
    fun manifest_covers_full_shiguang_gap_locally() {
        // gap-rows.json 属本地调研产物 (不入仓), CI 上没有即跳过, 本地必查。
        val gap = projectSourceFileOrNull("~/sleepy-worktrees/shiguang-gap-triage/docs/shiguang-gap-triage-2026-10-27/gap-rows.json")
        assumeTrue(gap != null)
        val rows = JSONArray(gap!!.readText())
        val schools = manifestJson.getJSONArray("schools")
        val matchKeys = (0 until schools.length())
            .map { schools.getJSONObject(it).getString("matchKey") }
            .toSet()
        val missing = (0 until rows.length())
            .map { rows.getJSONObject(it).getString("name").trim() }
            .filter { JwSgAssetNfk(it) !in matchKeys }
        assertEquals("gap 264 校必须全被 manifest 覆盖, 缺: $missing", 0, missing.size)
    }

    @Test
    fun webview_dispatch_routes_sg_asset_before_generic_wisedu_with_wide_timeout() {
        val screen = readProjectSource(
            "app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt"
        )
        assertTrue(
            "CaptureBar 必须有 sg_asset 专用分支",
            Regex("""school\.type\s*==\s*JwProtocol\.TYPE_SG_ASSET""").containsMatchIn(screen)
        )
        assertTrue("拾光脚本逐周可跑 60s+, 必须用放宽闸", screen.contains("SG_ASSET_TIMEOUT_MS"))
        assertTrue("sg_asset 分支必须经 bridgeJs 装配", screen.contains("JwSgAssetBridge.bridgeJs"))
        val sgAt = screen.indexOf("JwProtocol.TYPE_SG_ASSET")
        val wiseduAt = screen.indexOf("WISEDU_FETCH_JS")
        assertTrue("sg_asset 分支必须排在通用 Wisedu 兜底之前", sgAt in 0 until wiseduAt)
    }

    /** 与 JwSgAssetBridge.nfk / vendor 脚本同口径的 NFKC 归一 (测试侧独立实现, 防实现侧改坏)。 */
    private fun JwSgAssetNfk(name: String): String =
        java.text.Normalizer.normalize(name.trim(), java.text.Normalizer.Form.NFKC)

    private fun projectSourceFileOrNull(pathTilde: String): java.io.File? {
        val expanded = if (pathTilde.startsWith("~/")) {
            System.getProperty("user.home") + pathTilde.removePrefix("~")
        } else pathTilde
        return java.io.File(expanded).takeIf { it.isFile }
    }
}
