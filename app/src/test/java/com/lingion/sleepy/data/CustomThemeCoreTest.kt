package com.lingion.sleepy.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自定义主题存储 Core 纯 JVM 测试 — JSON 序列化/反序列化与 upsert/delete 语义。
 *
 * 模式同 WidgetBindingCoreTest:所有逻辑在纯 JVM 的 [CustomThemeCore],
 * CustomThemeStore 只是 SharedPreferences 门面(薄壳,行为由 SDK 保证)。
 *
 * 损坏 JSON 容错语义对齐 HolidayManager.diskCache 的 try-catch 返回空列表
 * (memory: holiday 解析模式);坏行跳过、好行保留对齐 HolidayManager.parseEntries
 * 的 skip-bad-rows 行为。
 */
class CustomThemeCoreTest {

    private val sample = CustomTheme(
        id = "11111111-2222-3333-4444-555555555555",
        name = "主题 1",
        primary = "#AABBCC",
        secondary = "#112233",
        tertiary = "#445566",
        surfaceHue = 265.0,
        surfaceChroma = 8.0,
        createdAt = 1700000000L
    )

    // ── 序列化形状契约 ──

    @Test
    fun serialized_json_contains_all_documented_fields() {
        val json = CustomThemeCore.toJson(listOf(sample))
        // 设计文档定的 8 字段形状,key 名是跨版本存储契约,禁改名
        listOf("id", "name", "primary", "secondary", "tertiary", "surfaceHue", "surfaceChroma", "createdAt")
            .forEach { field ->
                assertTrue("serialized JSON must contain field \"$field\"", json.contains("\"$field\""))
            }
    }

    @Test
    fun roundtrip_preserves_all_fields() {
        val json = CustomThemeCore.toJson(listOf(sample))
        val parsed = CustomThemeCore.parse(json)
        assertEquals(1, parsed.size)
        assertEquals(sample, parsed[0])
    }

    @Test
    fun roundtrip_multiple_themes_preserves_order() {
        val second = sample.copy(id = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", name = "主题 2")
        val json = CustomThemeCore.toJson(listOf(sample, second))
        val parsed = CustomThemeCore.parse(json)
        assertEquals(listOf(sample.id, second.id), parsed.map { it.id })
    }

    // ── 容错语义 ──

    @Test
    fun malformed_json_returns_empty_list() {
        assertTrue(CustomThemeCore.parse("{not json").isEmpty())
        assertTrue(CustomThemeCore.parse("").isEmpty())
        assertTrue(CustomThemeCore.parse("null").isEmpty())
        // 对象而非数组 — 形状错同样容错为空
        assertTrue(CustomThemeCore.parse("""{"id":"x"}""").isEmpty())
    }

    @Test
    fun bad_rows_skipped_good_rows_kept() {
        val json = """
            [
              {"id":"good-1","name":"好主题","primary":"#AABBCC","secondary":"#112233","tertiary":"#445566","surfaceHue":265.0,"surfaceChroma":8.0,"createdAt":1},
              {"name":"缺 id 的坏行","primary":"#FFFFFF","secondary":"#FFFFFF","tertiary":"#FFFFFF","surfaceHue":0.0,"surfaceChroma":0.0,"createdAt":2},
              {"id":"good-2","name":"也好","primary":"#000001","secondary":"#000002","tertiary":"#000003","surfaceHue":120.0,"surfaceChroma":4.0,"createdAt":3}
            ]
        """.trimIndent()
        val parsed = CustomThemeCore.parse(json)
        assertEquals(listOf("good-1", "good-2"), parsed.map { it.id })
    }

    @Test
    fun missing_optional_fields_fall_back_to_defaults() {
        // 容错宽容:缺 surfaceHue/Chroma/createdAt 不整行丢弃(向后兼容旧版本数据)
        val json = """[{"id":"minimal","name":"M","primary":"#AABBCC","secondary":"#112233","tertiary":"#445566"}]"""
        val parsed = CustomThemeCore.parse(json)
        assertEquals(1, parsed.size)
        val theme = parsed[0]
        assertEquals(265.0, theme.surfaceHue, 0.001)
        assertEquals(8.0, theme.surfaceChroma, 0.001)
        assertEquals(0L, theme.createdAt)
    }

    // ── upsert / delete 语义 ──

    @Test
    fun upsert_existing_id_replaces_in_place() {
        val list = mutableListOf(sample)
        val renamed = sample.copy(name = "改名了", secondary = "#999999")
        CustomThemeCore.upsert(list, renamed)
        assertEquals(1, list.size)
        assertEquals("改名了", list[0].name)
        assertEquals("#999999", list[0].secondary)
    }

    @Test
    fun upsert_new_id_appends() {
        val list = mutableListOf<CustomTheme>()
        CustomThemeCore.upsert(list, sample)
        assertEquals(listOf(sample), list)
    }

    @Test
    fun delete_removes_only_target_id() {
        val other = sample.copy(id = "other-id")
        val list = mutableListOf(sample, other)
        assertTrue(CustomThemeCore.delete(list, sample.id))
        assertEquals(listOf(other), list)
    }

    @Test
    fun delete_unknown_id_returns_false_and_mutates_nothing() {
        val list = mutableListOf(sample)
        assertNull(CustomThemeCore.getById(list, "missing"))
        assertFalseDelete(list)
        assertEquals(listOf(sample), list)
    }

    private fun assertFalseDelete(list: MutableList<CustomTheme>) {
        org.junit.Assert.assertFalse(CustomThemeCore.delete(list, "missing"))
    }

    // ── getById ──

    @Test
    fun get_by_id_finds_exact_match() {
        val other = sample.copy(id = "other-id")
        assertEquals(sample, CustomThemeCore.getById(listOf(sample, other), sample.id))
        assertNotNull(CustomThemeCore.getById(listOf(sample, other), "other-id"))
    }

    @Test
    fun get_by_id_empty_list_returns_null() {
        assertNull(CustomThemeCore.getById(emptyList(), sample.id))
    }
}
