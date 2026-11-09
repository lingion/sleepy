package com.lingion.sleepy.data.jw

import com.lingion.sleepy.data.migration.MigrationDatabaseCodec
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.parser.ScheduleExporter
import com.lingion.sleepy.data.parser.ScheduleParser
import com.lingion.sleepy.data.parser.SleepyNativeExporter
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Metadata is synthetic; it must survive real draft and backup codecs without acquiring defaults. */
class JwSgMetadataPersistenceTest {
    private val metadata = """{"schemaVersion":1,"adapter":{"schoolId":"UJS","adapterId":"UJS_02","name":"研究生","category":"研究生","importUrl":"https://example.edu.cn/yjs"},"config":{"semesterStartDate":null,"semesterTotalWeeks":24,"defaultClassDuration":50,"defaultBreakDuration":0,"firstDayOfWeek":7}}"""

    @Test fun `draft retains the selected entry and every config field including explicit null`() {
        val draft = JwImportDraftSnapshot(JwSchoolInfo("U", "回归学校", type = JwProtocol.TYPE_SG_ASSET),
            emptyList(), emptyList(), phase = JwImportDraftPhase.WEBVIEW_LOGIN)
        val original = JSONObject(JwImportDraftCodec.toJson(draft))
            .put("importSourceJson", metadata).put("selectedSgAdapterId", "UJS_02")
        val restored = JwImportDraftCodec.fromJson(original.toString())!!
        val serialized = JSONObject(JwImportDraftCodec.toJson(restored))
        assertEquals("UJS_02", serialized.optString("selectedSgAdapterId"))
        val config = JSONObject(serialized.getString("importSourceJson")).getJSONObject("config")
        assertTrue(config.has("semesterStartDate")); assertTrue(config.isNull("semesterStartDate"))
        assertEquals(24, config.getInt("semesterTotalWeeks"))
        assertEquals(50, config.getInt("defaultClassDuration"))
        assertEquals(0, config.getInt("defaultBreakDuration"))
        assertEquals(7, config.getInt("firstDayOfWeek"))
    }

    @Test fun `database backup and entity conversion preserve source metadata`() {
        val backup = JSONObject().put("timeTables", org.json.JSONArray().put(JSONObject()
            .put("id", 5).put("name", "回归课表").put("startDate", "2026-09-07")
            .put("timeJson", "[]").put("createdAt", 1).put("importSourceJson", metadata)))
        val decoded = MigrationDatabaseCodec.decode(backup.toString())
        val entityRoundtrip = MigrationDatabaseCodec.run { decoded.timeTables.single().toEntity().copy(name = "副本").toRow() }
        val encoded = JSONObject(MigrationDatabaseCodec.encode(decoded.copy(timeTables = listOf(entityRoundtrip))))
        val table = encoded.getJSONArray("timeTables").getJSONObject(0)
        assertEquals("副本", table.getString("name"))
        assertEquals(metadata, table.getString("importSourceJson"))
    }

    @Test fun `older drafts stay readable without source fields`() {
        val old = JwImportDraftSnapshot(JwSchoolInfo("U", "旧课表"), emptyList(), emptyList())
        assertEquals(old, JwImportDraftCodec.fromJson(JwImportDraftCodec.toJson(old)))
    }

    @Test fun `capture keeps adapter and config presence without retaining course payload`() {
        val original = JSONObject(metadata)
        val capture = original.put("sleepySgAsset", true).put("coursesJson", "private courses")
        val saved = JSONObject(JwSgSourceMetadata.capture(capture.toString(), JwProtocol.TYPE_SG_ASSET))
        assertFalse(saved.has("coursesJson")); assertFalse(saved.has("sleepySgAsset"))
        assertEquals("UJS_02", saved.getJSONObject("adapter").getString("adapterId"))
        assertTrue(saved.getJSONObject("config").isNull("semesterStartDate"))
        assertFalse(JSONObject(JwSgSourceMetadata.capture("""{"sleepySgAsset":true,"config":{}}""",
            JwProtocol.TYPE_SG_ASSET)).getJSONObject("config").has("semesterStartDate"))
        assertEquals("", JwSgSourceMetadata.capture(capture.toString(), "other"))
    }

    @Test fun `invalid source config is rejected at the draft boundary`() {
        val base = JSONObject(JwImportDraftCodec.toJson(JwImportDraftSnapshot(JwSchoolInfo("U", "旧课表"), emptyList(), emptyList())))
        for (config in listOf("[]", "{\"firstDayOfWeek\":8}", "{\"semesterStartDate\":\"2026-02-30\"}",
            "{\"defaultClassDuration\":0}", "{\"defaultBreakDuration\":null}", "{\"semesterTotalWeeks\":\"24\"}")) {
            val source = """{"schemaVersion":1,"config":$config}"""
            assertNull(config, JwSgSourceMetadata.read(source))
            assertNull(config, JwImportDraftCodec.fromJson(base.put("importSourceJson", source).toString()))
        }
    }

    @Test fun `native and JSON exports retain provenance through real parsers`() {
        val source = JSONObject(metadata).apply {
            getJSONObject("adapter").put("name", "研究生|入口\n二 Ａ１－～　𠮷")
                .put("importUrl", "https://example.edu.cn/Ａ１?name=～")
        }.toString()
        val table = TimeTableEntity(name = "回归", startDate = "2026-09-07", importSourceJson = source)
        val course = CourseEntity(groupId = "regression", tableId = 0, courseName = "离散数学", teacher = "甲", room = "A",
            day = 3, startNode = 1, step = 2, startWeek = 1, endWeek = 4, color = "#FF6750A4")
        val exported = listOf(
            SleepyNativeExporter.exportFile(table.name, table.startDate, 24, 12, table.timeJson, listOf(course), importSourceJson = source),
            SleepyNativeExporter.exportShareText(table.name, table.startDate, 24, 12, table.timeJson, listOf(course), importSourceJson = source),
            ScheduleExporter.exportWakeUpJson(table, listOf(course)),
            ScheduleExporter.exportWakeUpShareText(table, listOf(course)),
        )
        for (text in exported) {
            val result = ScheduleParser.parse(text, 0L, "#FF6750A4").getOrThrow()
            assertEquals(source, result.importSourceJson)
            assertEquals("离散数学", result.courses.single().courseName)
            assertTrue("Untampered export: ${result.warnings}", result.warnings.none { it.contains("完整性校验") })
        }
    }

    @Test fun `native integrity still detects a changed provenance record`() {
        val source = JSONObject(metadata).apply { getJSONObject("adapter").put("name", "入口Ａ") }.toString()
        val exported = SleepyNativeExporter.exportFile("回归", "2026-09-07", 24, 12, "", emptyList(),
            importSourceJson = source)
        val modified = exported.replace("入口Ａ", "入口A")
        assertNotEquals(exported, modified)
        val result = ScheduleParser.parse(modified, 0L, "#FF6750A4").getOrThrow()
        assertTrue(result.warnings.any { it.contains("完整性校验") })
    }
}
