package com.lingion.sleepy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 外部打开 intent-filter 回归 — issue #6(文件管理器点 .html/.ics 文件无反应)。
 *
 * 根因: OpenJsonAlias 只注册了 application/json + text/plain,
 * 文件管理器把 .html/.ics/.csv 报成对应 MIME 时 Sleepy 根本不在"打开方式"候选里,
 * intent 根本进不了 ImportReceiverActivity。
 *
 * 纯 JVM 读 AndroidManifest.xml 断言(仓库无 Robolectric, 与 StringsKeyParityTest 同套路):
 * intent-filter 是声明式数据, 读源头文件等价于读打包产物 — merged manifest 由本文件机械复制。
 *
 * 注册面只需要保证"文件能被送进 ImportReceiverActivity"; ScheduleParser.parse 按
 * 内容嗅探分派(json → ics → html → csv → 纯文本), 与 MIME 无关 — 各格式的解析正确性
 * 由 parser 包内既有测试锁定(IcsWakeUpImportTest 等), 本文件不重复。
 */
class ManifestExternalOpenFilterTest {

    private val manifest: Element by lazy {
        val f = sequenceOf(
            File("app/src/main/AndroidManifest.xml"),
            File("src/main/AndroidManifest.xml")
        ).first { it.isFile }
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f).documentElement
    }

    /** OpenJsonAlias intent-filter 里注册的全部 mimeType(逐字面) */
    private val registeredMimes: List<String> by lazy {
        val out = mutableListOf<String>()
        val aliases = manifest.getElementsByTagName("activity-alias")
        for (i in 0 until aliases.length) {
            val alias = aliases.item(i) as Element
            if (alias.getAttribute("android:name") != ".OpenJsonAlias") continue
            val datas = alias.getElementsByTagName("data")
            for (j in 0 until datas.length) {
                out += (datas.item(j) as Element).getAttribute("android:mimeType")
            }
        }
        out
    }

    /** OpenJsonAlias intent-filter 里的全部 category 字面值 */
    private val registeredCategories: List<String> by lazy {
        val out = mutableListOf<String>()
        val aliases = manifest.getElementsByTagName("activity-alias")
        for (i in 0 until aliases.length) {
            val alias = aliases.item(i) as Element
            if (alias.getAttribute("android:name") != ".OpenJsonAlias") continue
            val cats = alias.getElementsByTagName("category")
            for (j in 0 until cats.length) {
                out += (cats.item(j) as Element).getAttribute("android:name")
            }
        }
        out
    }

    private fun hasMime(m: String) = m in registeredMimes

    @Test
    fun json_and_plain_still_registered() {
        assertTrue("application/json 不得丢(WakeUp JSON 导出)", hasMime("application/json"))
        assertTrue("text/plain 不得丢(WakeUp 分享文本/纯文本课表)", hasMime("text/plain"))
    }

    @Test
    fun html_ics_csv_registered() {
        // issue #6 主诉: 文件管理器点 .html 无反应
        assertTrue("text/html 必须注册(教务系统 HTML 表格导出)", hasMime("text/html"))
        // Sleepy 自身导出 .ics 用的就是 text/calendar(ExportScreen) — 自产自销闭环
        assertTrue("text/calendar 必须注册(ICS 日历导出)", hasMime("text/calendar"))
        assertTrue("text/x-vcalendar 必须注册(legacy vCalendar 1.0, 部分教务导出)", hasMime("text/x-vcalendar"))
        assertTrue("text/csv 必须注册(CSV 课表)", hasMime("text/csv"))
        // 部分老 ROM/提供方把 .csv 报成全称别名
        assertTrue("text/comma-separated-values 必须注册(text/csv 的 legacy 别名)", hasMime("text/comma-separated-values"))
    }

    @Test
    fun xml_family_registered() {
        // XHTML/XML 起头的页面 parse() 走 startsWithAnyTag 的 <!doctype / <?xml 分支
        assertTrue("text/xml 必须注册", hasMime("text/xml"))
        assertTrue("application/xml 必须注册", hasMime("application/xml"))
        assertTrue("application/xhtml+xml 必须注册", hasMime("application/xhtml+xml"))
    }

    @Test
    fun view_default_browsable_categories_present() {
        assertTrue("必须有 ACTION_VIEW", registeredCategories.any { it.endsWith(".category.DEFAULT") } &&
            (manifest.getElementsByTagName("action").length > 0))
        assertTrue("必须含 DEFAULT(implicit view 才能命中)", "android.intent.category.DEFAULT" in registeredCategories)
        assertTrue("必须含 BROWSABLE(浏览器/邮件附件下载打开)", "android.intent.category.BROWSABLE" in registeredCategories)
    }

    @Test
    fun schemes_content_and_file_present() {
        val schemes = mutableListOf<String>()
        val aliases = manifest.getElementsByTagName("activity-alias")
        for (i in 0 until aliases.length) {
            val alias = aliases.item(i) as Element
            if (alias.getAttribute("android:name") != ".OpenJsonAlias") continue
            val datas = alias.getElementsByTagName("data")
            for (j in 0 until datas.length) {
                val s = (datas.item(j) as Element).getAttribute("android:scheme")
                if (s.isNotEmpty()) schemes += s
            }
        }
        assertTrue("content scheme 不得丢(Scoped Storage 主路径)", "content" in schemes)
        assertTrue("file scheme 不得丢(老文件管理器直传路径, ImportReceiverActivity 有 cacheDir 复制兜底)", "file" in schemes)
    }

    @Test
    fun no_binary_mime_registered() {
        // 禁注册二进制类型: ImportReceiverActivity readText() 读二进制大文件(APK/ZIP/图片)
        // 会 OOM; 且 content:// URI 的 pathPattern 匹配不到文件名, 注册 octet-stream
        // 只会让 Sleepy 出现在所有文件的打开方式里 — 收益为零崩溃风险为实。
        val forbidden = listOf(
            "application/octet-stream",
            "application/pdf",
            "application/zip",
            "image/",
            "video/",
            "audio/"
        )
        for (f in forbidden) {
            assertFalse(
                "禁止注册 $f(二进制 readText 有 OOM 崩溃路径, 见 ManifestExternalOpenFilterTest 注释)",
                registeredMimes.any { it.startsWith(f) }
            )
        }
    }
}
