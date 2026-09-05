package com.lingion.sleepy.data.parser

import org.junit.Assert.*
import org.junit.Test
import com.lingion.sleepy.data.parser.SleepyNativeFormat.WeekSpec

/**
 * sleepy-v1 原生格式 — 纯函数层测试(锚定 Desktop/sleepy-io-design/sleepy-v1-终稿规范.md):
 * magic 识别 / 转义往返 / 调色板 / lenient 时钟·日期·周次·节次 / Nd 预设 / crc32。
 */
class SleepyNativeFormatTest {

    // ---- magic 识别 (规范 §6.1) ----

    @Test fun magic_plain() {
        assertEquals(1, SleepyNativeFormat.detectVersion("#sleepy-v1\nC高数|1|1-2"))
    }

    @Test fun magic_caseInsensitive_fullPattern() {
        // 规范 §6.1: 整模式大小写不敏感 — sleepy 与 v 均不限大小写
        assertEquals(1, SleepyNativeFormat.detectVersion("#SLEEPY-V1"))
        assertEquals(1, SleepyNativeFormat.detectVersion("#Sleepy-V1"))
    }

    @Test fun magic_variants_hit() {
        // §6.3-M: 双井号/空格/无横线/下划线/全角井号/尾标点 全命中
        assertEquals(1, SleepyNativeFormat.detectVersion("##sleepy-v1"))
        assertEquals(1, SleepyNativeFormat.detectVersion("# sleepy-v1"))
        assertEquals(1, SleepyNativeFormat.detectVersion("#sleepy v1"))
        assertEquals(1, SleepyNativeFormat.detectVersion("#sleepy_v1"))
        assertEquals(1, SleepyNativeFormat.detectVersion("＃sleepy-v1"))
        assertEquals(1, SleepyNativeFormat.detectVersion("#sleepy-v1。"))
        assertEquals(1, SleepyNativeFormat.detectVersion("#sleepy－v1"))
    }

    @Test fun magic_quotePrefix_and_latePosition() {
        // §6.3-N: 微信引用前缀 + 20 行客套后 magic → 32 非空行窗内命中
        assertEquals(1, SleepyNativeFormat.detectVersion("> > #sleepy-v1"))
        val chatter = (1..20).joinToString("\n") { "转发语第 $it 行" }
        assertEquals(1, SleepyNativeFormat.detectVersion("$chatter\n#sleepy-v1\nC高数|1|1-2"))
    }

    @Test fun magic_outsideWindow_miss() {
        val chatter = (1..40).joinToString("\n") { "第 $it 行" }
        assertEquals(-1, SleepyNativeFormat.detectVersion("$chatter\n#sleepy-v1"))
    }

    @Test fun magic_futureVersion_detected() {
        assertEquals(2, SleepyNativeFormat.detectVersion("#sleepy-v2"))
    }

    @Test fun magic_miss_onOtherFormats() {
        // §6.3-R 反向矩阵: 五路判别子串不命中
        assertEquals(-1, SleepyNativeFormat.detectVersion("""{"name":"x","courses":[]}"""))
        assertEquals(-1, SleepyNativeFormat.detectVersion("BEGIN:VCALENDAR"))
        assertEquals(-1, SleepyNativeFormat.detectVersion("<html><body></body></html>"))
        assertEquals(-1, SleepyNativeFormat.detectVersion("课程,教师,星期\n高数,张三,1"))
        assertEquals(-1, SleepyNativeFormat.detectVersion("高数 张三 周一 1-2 1-16 3"))
        assertEquals(-1, SleepyNativeFormat.detectVersion(""))
    }

    // ---- 转义往返 (规范 §3.3, §1.3-4) ----

    @Test fun escape_unescape_roundTrip_allReserved() {
        val cases = listOf(
            "A|B候选", "C:\\fs\\A101", "带\"引号", "多行\n备注", "制表\t符",
            "<frameset", "{花括号", "(圆括号", "全角｜不必转", "纯中文", "English Name", "データベース", "Física"
        )
        for (s in cases) {
            assertEquals("roundtrip: $s", s, SleepyNativeFormat.unescape(SleepyNativeFormat.escape(s)))
        }
    }

    @Test fun escape_exportNeverEmitsDangerousLiteral() {
        // 导出物永不出现"未转义"的 " / <<<SLEEPY-END>>> 图案 / <frameset(转义符本身合法)
        val nasty = "\"courseDetailJson\" <<<SLEEPY-END>>> <frameset | { ( \\"
        val escaped = SleepyNativeFormat.escape(nasty)
        // 每个危险字符前都紧贴反斜杠
        assertFalse(Regex("(?<!\\\\)\"").containsMatchIn(escaped))
        assertFalse(Regex("(?<!\\\\)<").containsMatchIn(escaped))
        assertFalse(Regex("(?<!\\\\)\\{").containsMatchIn(escaped))
        assertFalse(Regex("(?<!\\\\)\\(").containsMatchIn(escaped))
        assertFalse(Regex("(?<!\\\\)\\|").containsMatchIn(escaped))
        // \n 是两字符
        assertTrue(SleepyNativeFormat.escape("a\nb").contains("\\n"))
    }

    @Test fun unescape_nAndT_pairs() {
        assertEquals("a\nb", SleepyNativeFormat.unescape("a\\nb"))
        assertEquals("a\tb", SleepyNativeFormat.unescape("a\\tb"))
        // 其余 \x → 字面 x
        assertEquals("aXb", SleepyNativeFormat.unescape("a\\Xb"))
        assertEquals("a\\b", SleepyNativeFormat.unescape("a\\\\b"))
        // 尾部孤立反斜杠 → 字面反斜杠
        assertEquals("a\\", SleepyNativeFormat.unescape("a\\"))
    }

    // ---- 调色板 (规范 §3.2) ----

    @Test fun palette_exact8BitForms() {
        assertEquals("#FFEADDFF", SleepyNativeFormat.PALETTE[1])
        assertEquals("#FFF2C4DE", SleepyNativeFormat.PALETTE[9])
        assertEquals(9, SleepyNativeFormat.PALETTE.size)
    }

    @Test fun colorToToken_paletteIndex() {
        assertEquals("1", SleepyNativeFormat.colorToToken("#FFEADDFF"))
        // 6 位补 FF 后命中 → 索引
        assertEquals("1", SleepyNativeFormat.colorToToken("#EADDFF"))
        // 大小写归一
        assertEquals("9", SleepyNativeFormat.colorToToken("#f2c4de"))
    }

    @Test fun colorToToken_sentinel_empty_autoColor() {
        assertEquals("", SleepyNativeFormat.colorToToken(SleepyNativeFormat.AUTO_COLOR))
        assertEquals("", SleepyNativeFormat.colorToToken("#FF6750A4".lowercase()))
    }

    @Test fun colorToToken_literalFallback() {
        // 非 FF alpha → 9 位 AARRGGBB
        assertEquals("#80388E3C", SleepyNativeFormat.colorToToken("#80388E3C"))
        // alpha FF 其他色 → 6 位
        assertEquals("#388E3C", SleepyNativeFormat.colorToToken("#FF388E3C"))
        // 垃圾值 → 空(自动)
        assertEquals("", SleepyNativeFormat.colorToToken("not-a-color"))
    }

    @Test fun colorFromToken_allForms() {
        assertEquals("#FFEADDFF", SleepyNativeFormat.colorFromToken("1"))
        assertEquals("#FFEADDFF", SleepyNativeFormat.colorFromToken("#EADDFF"))
        assertEquals("#FFEADDFF", SleepyNativeFormat.colorFromToken("#FFEADDFF"))
        assertEquals("#80388E3C", SleepyNativeFormat.colorFromToken("#80388E3C"))
        // 非法索引/垃圾 → 自动色
        assertEquals(SleepyNativeFormat.AUTO_COLOR, SleepyNativeFormat.colorFromToken(""))
        assertEquals(SleepyNativeFormat.AUTO_COLOR, SleepyNativeFormat.colorFromToken("99"))
        assertEquals(SleepyNativeFormat.AUTO_COLOR, SleepyNativeFormat.colorFromToken("xyz"))
    }

    // ---- lenient 时钟 / 日期 / 节次 / 周次 (规范 §2 文法) ----

    @Test fun parseClock_lenient() {
        assertEquals(java.time.LocalTime.of(8, 0), SleepyNativeFormat.parseClock("8:00"))
        assertEquals(java.time.LocalTime.of(8, 0), SleepyNativeFormat.parseClock("08:00"))
        assertEquals(java.time.LocalTime.of(8, 0), SleepyNativeFormat.parseClock("08：00")) // 全角冒号
        assertEquals(null, SleepyNativeFormat.parseClock("25:00"))
        assertEquals(null, SleepyNativeFormat.parseClock("abc"))
        assertEquals(null, SleepyNativeFormat.parseClock("8:5"))
    }

    @Test fun parseDate_lenient_normToMonday() {
        // 2026-03-02 是周一
        assertEquals("2026-03-02", SleepyNativeFormat.parseDate("2026-03-02"))
        assertEquals("2026-03-02", SleepyNativeFormat.parseDate("2026/03/02"))
        assertEquals("2026-03-02", SleepyNativeFormat.parseDate("2026.3.2"))
        assertEquals("2026-03-02", SleepyNativeFormat.parseDate("20260302"))
        // 非周一 → 归到所在周一(2026-03-04 是周三)
        assertEquals("2026-03-02", SleepyNativeFormat.parseDate("2026-03-04"))
        // 非法 → null
        assertEquals(null, SleepyNativeFormat.parseDate("abc"))
        assertEquals(null, SleepyNativeFormat.parseDate("2026-13-40"))
    }

    @Test fun parseNodeSpan() {
        assertEquals(1 to 1, SleepyNativeFormat.parseNodeSpan("1"))
        assertEquals(3 to 4, SleepyNativeFormat.parseNodeSpan("3-4"))
        assertEquals(null, SleepyNativeFormat.parseNodeSpan("abc"))
        assertEquals(null, SleepyNativeFormat.parseNodeSpan("1-2-3"))
    }

    @Test fun parseWeekSpec_fiveShapes() {
        assertEquals(WeekSpec(1, 16, 0), SleepyNativeFormat.parseWeekSpec("1-16"))
        assertEquals(WeekSpec(1, 15, 1), SleepyNativeFormat.parseWeekSpec("1-15单"))
        assertEquals(WeekSpec(2, 16, 2), SleepyNativeFormat.parseWeekSpec("2-16双"))
        assertEquals(WeekSpec(3, 4, 3), SleepyNativeFormat.parseWeekSpec("3-4定"))
        assertEquals(WeekSpec(8, 8, 3), SleepyNativeFormat.parseWeekSpec("8定"))
        // 导入额外容忍
        assertEquals(WeekSpec(1, 15, 1), SleepyNativeFormat.parseWeekSpec("1-15奇"))
        assertEquals(WeekSpec(1, 15, 1), SleepyNativeFormat.parseWeekSpec("1-15odd"))
        assertEquals(WeekSpec(2, 16, 2), SleepyNativeFormat.parseWeekSpec("2-16even"))
        assertEquals(WeekSpec(2, 16, 2), SleepyNativeFormat.parseWeekSpec("2-16e"))
        assertEquals(WeekSpec(3, 4, 3), SleepyNativeFormat.parseWeekSpec("3-4散"))
        // 单数字无后缀 = 只上这周 (type 3)
        assertEquals(WeekSpec(8, 8, 3), SleepyNativeFormat.parseWeekSpec("8"))
        // 未知后缀 → null(形状非法, 交给调用方上报)
        assertEquals(null, SleepyNativeFormat.parseWeekSpec("1-16x"))
        assertEquals(null, SleepyNativeFormat.parseWeekSpec("张三"))
        // 反写区间原样返回, 方向钳制在调用方(§3.1: E<S → 交换+上报)
        assertEquals(WeekSpec(16, 1, 0), SleepyNativeFormat.parseWeekSpec("16-1"))
    }

    @Test fun parseDay_lenient() {
        assertEquals(1, SleepyNativeFormat.parseDay("1"))
        assertEquals(3, SleepyNativeFormat.parseDay("周三"))
        assertEquals(7, SleepyNativeFormat.parseDay("日"))
        assertEquals(7, SleepyNativeFormat.parseDay("天"))
        assertEquals(5, SleepyNativeFormat.parseDay("星期5"))
        assertEquals(2, SleepyNativeFormat.parseDay("礼拜二"))
        // 非法形状 → null
        assertEquals(null, SleepyNativeFormat.parseDay("张三"))
        assertEquals(null, SleepyNativeFormat.parseDay("13"))
    }

    // ---- Nd 冻结预设 (规范 §5, = TimeTableUtils.DEFAULT_TIME_JSON) ----

    @Test fun ndPreset_matchesDefaultTimeJson() {
        val defaults = com.lingion.sleepy.util.TimeTableUtils.parseNodes(
            com.lingion.sleepy.util.TimeTableUtils.DEFAULT_TIME_JSON
        )
        assertEquals(12, SleepyNativeFormat.ND_PRESET.size)
        assertEquals(defaults.size, SleepyNativeFormat.ND_PRESET.size)
        defaults.forEachIndexed { i, n ->
            assertEquals("node ${n.node} start", n.start, SleepyNativeFormat.ND_PRESET[i].first)
            assertEquals("node ${n.node} end", n.end, SleepyNativeFormat.ND_PRESET[i].second)
            assertEquals(i + 1, n.node)
        }
    }

    // ---- crc32 ----

    @Test fun crc32_knownVector() {
        // 标准 CRC32 校验向量: "123456789" → 0xCBF43926
        assertEquals("cbf43926", SleepyNativeFormat.crc32("123456789".toByteArray(Charsets.UTF_8)))
    }
}
