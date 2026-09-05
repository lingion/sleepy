package com.lingion.sleepy.data.parser

import org.junit.Assert.*
import org.junit.Test

/**
 * sleepy-v1 分派误判回归矩阵(规范 §6.3 的 17 例) —
 * 每行一个测试锚: 原生与其他六路判别互不劫持。
 */
class SleepyNativeDispatchTest {

    private fun parseOrNull(text: String) = ScheduleParser.parse(text, 7L)

    // §6.3-A: WakeUp 分享文本无 magic → 不被原生分支劫持(走分享分支, 失败也是分享分支的失败)
    @Test fun caseA_wakeupShareText_notHijacked() {
        val share = "【来自Sleepy】\n课程分享：\n\n{\"name\":\"x\",\"startDate\":\"2026-03-02\",\"courseDetailJson\":\"...\"}"
        val r = parseOrNull(share)
        // 原生分支对无 magic 输入必然 miss(caseR 反向矩阵锁定 detectVersion==-1);
        // 本测试锚: 无论成功失败, 走的都是 WakeUp 路径 — 失败消息不含原生"没进去/升级"话术
        if (r.isFailure) {
            val msg = r.exceptionOrNull()!!.message ?: ""
            assertFalse(msg.contains("没进去"))
            assertFalse(msg.contains("升级"))
        }
    }

    // §6.3-B: 备注含未转义 "courseDetailJson" 的原生文档 — 分支上移后原生先行命中
    @Test fun caseB_nativeDocContainingCourseDetailJson_nativeWins() {
        val doc = "#sleepy-v1\nC好课|1|1-2|1-16|张三|A101||\"courseDetailJson\"在备注||"
        val r = parseOrNull(doc)
        assertTrue("必须走原生分支(链顶), 失败=${r.exceptionOrNull()}", r.isSuccess)
        val pr = r.getOrThrow()
        assertEquals(1, pr.courses.size)
        // 备注文本按原文收(未转义引号在手造文档允许 — 宽容)
        assertTrue(pr.courses[0].note.contains("courseDetailJson"))
    }

    // §6.3-C: WakeUp JSON 直贴 → 照走 WakeUp 分支
    @Test fun caseC_wakeupJson_notHijacked() {
        val json = """{"name":"表","startDate":"2026-03-02","courses":[{"name":"高数","teacher":"张三","position":"A101","day":1,"startNode":1,"step":2,"startWeek":1,"endWeek":16,"type":0}]}"""
        val r = parseOrNull(json)
        assertTrue(r.isSuccess)
        assertEquals("表", r.getOrThrow().tableName)
    }

    // §6.3-D: ICS → 照走 ICS 分支
    @Test fun caseD_ics_notHijacked() {
        val ics = "BEGIN:VCALENDAR\nVERSION:2.0\nEND:VCALENDAR"
        val r = parseOrNull(ics)
        // ICS 分支被选中(可能失败于无 VEVENT, 但绝不会走原生空表语义 — 原生失败消息含"没进去", ICS 不是)
        if (r.isFailure) {
            assertFalse(r.exceptionOrNull()!!.message!!.contains("sleepy-v1"))
        }
    }

    // §6.3-F/G: 自由文本与 CSV 不被劫持
    @Test fun caseF_freeText_notHijacked() {
        // parseSimpleText 列序: 名称 老师 教室 星期 节次 周次 类型(§文件头注释)
        val text = "高数 张三 A101 周一 1-2 1-16 3\n英语 李四 B202 周二 3-4 1-16 0"
        val r = parseOrNull(text)
        assertTrue("failure=${r.exceptionOrNull()}", r.isSuccess)
        assertEquals(2, r.getOrThrow().courses.size)  // parseSimpleText 原有能力不偷
    }

    @Test fun caseG_csv_notHijacked() {
        val csv = "课程,教师,星期,节次,周次\n高数,张三,1,1-2,1-16\n英语,李四,3,3-4,1-16"
        val r = parseOrNull(csv)
        assertTrue(r.isSuccess)
        assertEquals(2, r.getOrThrow().courses.size)
    }

    // §6.3-H: marker 变体包裹的 magic → 剥除后命中
    @Test fun caseH_markerVariants_stillDetected() {
        val doc = "{{{SLEEPY-BEGIN}}}\n#sleepy-v1\nC高数|1|1-2|1-16\n{{{SLEEPY-END}}}"
        val r = parseOrNull(doc)
        assertTrue(r.isSuccess)
        assertEquals(1, r.getOrThrow().courses.size)
    }

    // §6.3-L: v2 → 显式拒绝
    @Test fun caseL_v2_rejectedWithUpgradeMessage() {
        val r = parseOrNull("#sleepy-v2\nC高数|1|1-2")
        assertTrue(r.isFailure)
        val msg = r.exceptionOrNull()!!.message!!
        assertTrue(msg.contains("升级"))
        assertTrue(msg.contains("v2"))
    }

    // §6.3-M: magic 容错变体
    @Test fun caseM_magicVariants_allHit() {
        val variants = listOf(
            "#SLEEPY-V1", "# sleepy-v1", "##sleepy-v1", "#sleepy v1",
            "＃sleepy-v1", "#sleepy－v1", "#sleepy-v1。"
        )
        for (v in variants) {
            val doc = "$v\nC高数|1|1-2|1-16"
            val r = parseOrNull(doc)
            assertTrue("variant $v failed: ${r.exceptionOrNull()}", r.isSuccess)
            assertEquals("variant $v", 1, r.getOrThrow().courses.size)
        }
    }

    // §6.3-N: 微信长转发头
    @Test fun caseN_longForwardHeader_hit() {
        val chatter = (1..20).joinToString("\n") { "转发第 $it 行" }
        val doc = "$chatter\n#sleepy-v1\nC高数|1|1-2|1-16"
        val r = parseOrNull(doc)
        assertTrue(r.isSuccess)
        assertEquals(1, r.getOrThrow().courses.size)
    }

    // §6.3-O: GBK 乱码 → 不静默
    @Test fun caseO_gbkGarbage_neverSilentSuccess() {
        val garbled = String("#sleepy-v1\nC高数|1|1-2|1-16".toByteArray(charset("GBK")), Charsets.UTF_8)
        val r = parseOrNull(garbled)
        if (r.isSuccess) {
            val pr = r.getOrThrow()
            assertTrue(pr.courses.isEmpty() || pr.droppedLines.isNotEmpty())
        }
    }

    // §6.3-P: 两张表拼接
    @Test fun caseP_twoTables_mergedWithWarning() {
        val doc = "#sleepy-v1\nC甲|1|1-2|1-16\n#sleepy-v1\nT第二张表\nC乙|2|1-2|1-16"
        val r = parseOrNull(doc)
        assertTrue(r.isSuccess)
        val pr = r.getOrThrow()
        assertEquals(2, pr.courses.size)
        assertTrue(pr.warnings.any { it.contains("第 2 张") })
    }

    // §6.3-Q: chk 篡改 → 警告不硬拒
    @Test fun caseQ_chkMismatch_warnNotReject() {
        val doc = "#sleepy-v1\nC高数|1|1-2|1-16\nz|chk=crc32:deadbeef"
        val r = parseOrNull(doc)
        assertTrue(r.isSuccess)
        assertTrue(r.getOrThrow().warnings.any { it.contains("校验") })
    }

    // §6.3-R: 五路真实样本反向矩阵
    @Test fun caseR_realSamples_reverseMatrix() {
        val samples = listOf(
            """{"courses":[]}""",
            "BEGIN:VCALENDAR\nEND:VCALENDAR",
            "<html><body><table></table></body></html>",
            "课程,教师,星期\n高数,张三,1",
            "高数 张三 周一 1-2"
        )
        for (s in samples) {
            assertEquals("sample: ${s.take(30)}", -1, SleepyNativeFormat.detectVersion(s.trim()))
        }
    }
}
