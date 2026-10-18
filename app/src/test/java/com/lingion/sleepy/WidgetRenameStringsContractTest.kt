package com.lingion.sleepy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 「添加或管理桌面小组件」改名文案闸（docs/widget-add-gallery-design.md §4 不变量④）。
 *
 * 6 locale × {widget_manage_entry, widget_manage_title} 必须同步新文案;
 * 任何语言残留旧「管理桌面小组件」孤串即红。同时锁定新增 key:
 * mine_widget(我的页入口)、widget_gallery_title(画廊页标题)、
 * widget_add_unsupported_hint(桌面不支持 pin 的手动添加指引)。
 *
 * 与 StringsKeyParityTest 同族: 纯资源文件扫描,无 Robolectric。
 */
class WidgetRenameStringsContractTest {

    private val locales = listOf(
        "values", "values-zh-rCN", "values-zh-rTW", "values-en", "values-ja", "values-es"
    )

    private fun findUpward(rel: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, rel)
            if (f.exists()) return f
            dir = dir.parentFile
        }
        error("$rel not found")
    }

    private fun stringsFor(loc: String): String =
        findUpward("app/src/main/res/$loc/strings.xml").readText()

    private fun stringOf(loc: String, key: String): String? =
        Regex("""<string name="\Q$key\E">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .find(stringsFor(loc))
            ?.groupValues?.get(1)

    // ---- 改名条目 + 页标题: 6 语言全部就位,且必须含「添加」语义 ----

    @Test
    fun `entry and title carry add-or-manage wording in all 6 locales`() {
        // 各语言「添加」词根(小写匹配): 简/繁直接看字,译语看动词词干
        val addStems = mapOf(
            "values" to "添加", "values-zh-rCN" to "添加", "values-zh-rTW" to "新增",
            "values-en" to "add", "values-ja" to "追加", "values-es" to "añad"
        )
        locales.forEach { loc ->
            listOf("widget_manage_entry", "widget_manage_title").forEach { key ->
                val v = stringOf(loc, key)
                assertTrue("$loc 缺 $key", v != null && v!!.isNotBlank())
                val stem = addStems.getValue(loc).lowercase()
                assertTrue(
                    "$loc 的 $key 必须表达「添加或管理」语义(应含词根「$stem」), 实际: 「$v」",
                    v!!.lowercase().contains(stem)
                )
            }
        }
    }

    @Test
    fun `no locale retains the old manage-only entry string`() {
        // 旧文案 = 纯「管理桌面小组件」(zh)/Manage Widgets(en)/ホームウィジェットを管理(ja)/
        // Administrar widgets del escritorio(es)/管理桌面小工具(tw) —— 一字不差的旧值禁残留
        val oldExact = mapOf(
            "values" to "管理桌面小组件", "values-zh-rCN" to "管理桌面小组件",
            "values-zh-rTW" to "管理桌面小工具", "values-en" to "Manage Widgets",
            "values-ja" to "ホームウィジェットを管理", "values-es" to "Administrar widgets del escritorio"
        )
        locales.forEach { loc ->
            listOf("widget_manage_entry", "widget_manage_title").forEach { key ->
                val v = stringOf(loc, key)
                assertFalse(
                    "$loc 的 $key 仍是旧「仅管理」文案: 「$v」",
                    v == oldExact[loc]
                )
            }
        }
    }

    // ---- 新增 key 6 语言齐全 ----

    @Test
    fun `new keys exist in all 6 locales`() {
        listOf("mine_widget", "widget_gallery_title", "widget_manage_empty_v2", "widget_add_unsupported_hint")
            .forEach { key ->
                locales.forEach { loc ->
                    val v = stringOf(loc, key)
                    assertTrue("$loc 缺新 key $key", v != null && v!!.isNotBlank())
                }
            }
    }

    @Test
    fun `mine widget entry label is widget word in all locales`() {
        // 我的页新入口文案 = 「小组件」板块词(与 appearance_section_widget 同源词,不要求同 key)
        val expects = mapOf(
            "values" to "小组件", "values-zh-rCN" to "小组件", "values-zh-rTW" to "小工具",
            "values-en" to "Widget", "values-ja" to "ウィジェット", "values-es" to "Widget"
        )
        locales.forEach { loc ->
            val v = stringOf(loc, "mine_widget")
            assertTrue("mine_widget($loc) 应含「${expects[loc]}」, 实际: 「$v」", v!!.contains(expects[loc]!!))
        }
    }
}
