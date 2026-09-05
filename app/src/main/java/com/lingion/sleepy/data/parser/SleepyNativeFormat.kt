package com.lingion.sleepy.data.parser

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.zip.CRC32

/**
 * sleepy-v1 原生格式 — 纯函数层(规范: sleepy-v1-终稿规范.md §2/§3/§5/§6.1)。
 *
 * 行式竖线分列: magic 行 `#sleepy-v1` + T(表) / N(作息) / Nd(预设) / C(课程, 恒10列) / z(校验) 行。
 * 本对象只放无状态的常量与纯函数; 解析见 [SleepyNativeParser], 导出见 [SleepyNativeExporter]。
 */
object SleepyNativeFormat {

    // ---- magic 识别 (§6.1) ----

    /**
     * 行首锚定: ≤4 字符引用/空白前缀 + 1-2 个井号(半/全角) + sleepy + 可选拼缝 + v + 数字。
     * 整模式大小写不敏感(#SLEEPY-V1 命中且版本=1)。行尾多余内容由 $ 前的宽容处理忽略——
     * 我们用 find + 范围前缀判定而非严格 fullMatch, 允许尾随标点(§6.3-M「#sleepy-v1。」)。
     */
    private val MAGIC_REGEX = Regex(
        """^[>\s]{0,4}[#＃]{1,2}\s*sleepy[\s\-_－]*v(\d+)""",
        RegexOption.IGNORE_CASE
    )

    /** magic 必须位于前 32 个非空行之一(微信长转发头) */
    private const val MAGIC_WINDOW = 32

    /** 识别 sleepy-v* 家族。返回版本号(1=本格式), 未命中 -1。作用于归一后的 trimmed 文本。 */
    fun detectVersion(trimmed: String): Int {
        var seen = 0
        for (line in trimmed.lineSequence()) {
            if (line.isBlank()) continue
            seen++
            if (seen > MAGIC_WINDOW) return -1
            val t = line.trim().trimEnd('\r')
            val m = MAGIC_REGEX.find(t) ?: continue
            return m.groupValues[1].toIntOrNull() ?: -1
        }
        return -1
    }

    // ---- 转义 (§3.3) ----

    /** 导出端转义 8 字符: \ | " \n \t < { ( */
    fun escape(s: String): String {
        if (s.none { it in RESERVED }) return s
        val sb = StringBuilder(s.length + 8)
        for (c in s) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '|' -> sb.append("\\|")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\t' -> sb.append("\\t")
                '<', '{', '(' -> sb.append('\\').append(c)
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    /** 导入端: \n→换行 \t→制表; 其余 \x → 字面 x; 尾部孤立 \ → 字面 \ */
    fun unescape(s: String): String {
        if ('\\' !in s) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    else -> sb.append(s[i + 1])
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    private val RESERVED = charArrayOf('\\', '|', '"', '\n', '\t', '<', '{', '(')

    // ---- 调色板 (§3.2, 9 色冻结 8 位规范形) ----

    const val AUTO_COLOR = "#FF6750A4"

    val PALETTE: Map<Int, String> = mapOf(
        1 to "#FFEADDFF", 2 to "#FFFFD8E4", 3 to "#FFFFDCC4",
        4 to "#FFFFF2B8", 5 to "#FFD4F7C5", 6 to "#FFC5F2E3",
        7 to "#FFC9E8FF", 8 to "#FFCDD7FF", 9 to "#FFF2C4DE"
    )

    private val PALETTE_BY_VALUE: Map<String, Int> =
        PALETTE.entries.associate { (k, v) -> v.uppercase() to k }

    /** 导出: 课程色 → token。自动色哨兵→空; 调色板命中→索引; FF alpha 其他→#RRGGBB; 非 FF→#AARRGGBB; 垃圾→空 */
    fun colorToToken(color: String): String {
        val norm = normalizeColor(color) ?: return ""
        if (norm == AUTO_COLOR.uppercase()) return ""
        PALETTE_BY_VALUE[norm]?.let { return it.toString() }
        return if (norm.startsWith("#FF")) "#${norm.substring(3)}" else norm
    }

    /** 导入: token → 课程色。索引→规范形; #6 位→补 FF; #9 位→原样; 空/非法→自动色 */
    fun colorFromToken(token: String): String {
        val t = token.trim()
        if (t.isEmpty()) return AUTO_COLOR
        t.toIntOrNull()?.let { idx -> return PALETTE[idx] ?: AUTO_COLOR }
        if (t.startsWith("#")) {
            val hex = t.substring(1).uppercase()
            return when (hex.length) {
                6 -> "#FF$hex"
                8 -> if (hex.all { it.isDigit() || it in 'A'..'F' }) "#$hex" else AUTO_COLOR
                else -> AUTO_COLOR
            }
        }
        return AUTO_COLOR
    }

    /** 归一为 8 位 #AARRGGBB 大写; 非法 → null */
    private fun normalizeColor(color: String): String? {
        val t = color.trim().uppercase()
        if (!t.startsWith("#")) return null
        val hex = t.substring(1)
        if (hex.any { !(it.isDigit() || it in 'A'..'F') }) return null
        return when (hex.length) {
            6 -> "#FF$hex"
            8 -> "#$hex"
            else -> null
        }
    }

    // ---- lenient 基元解析 (§2 文法, §7 容错) ----

    /** `H:mm` / `HH:mm` / 全角冒号 → LocalTime; 越界/非法 → null */
    fun parseClock(s: String): LocalTime? {
        val t = s.trim().replace('：', ':')
        val m = Regex("""^(\d{1,2}):(\d{2})$""").find(t) ?: return null
        val h = m.groupValues[1].toIntOrNull() ?: return null
        val min = m.groupValues[2].toIntOrNull() ?: return null
        return runCatching { LocalTime.of(h, min) }.getOrNull()
    }

    /** `YYYY-MM-DD`(/ . 或紧凑) → 归一到所在周一的 `YYYY-MM-DD`; 非法 → null */
    fun parseDate(s: String): String? {
        val t = s.trim()
        val m = Regex("""^(\d{4})[-/.]?(\d{1,2})[-/.]?(\d{1,2})$""").find(t) ?: return null
        val d = runCatching {
            LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        }.getOrNull() ?: return null
        return d.with(DayOfWeek.MONDAY).toString()
    }

    /** `S` / `S-E` → (startNode, endNode); 形状非法 → null */
    fun parseNodeSpan(s: String): Pair<Int, Int>? {
        val t = s.trim()
        if (t.isEmpty()) return null
        val parts = t.split("-")
        return when (parts.size) {
            1 -> parts[0].toIntOrNull()?.let { it to it }
            2 -> {
                val a = parts[0].toIntOrNull() ?: return null
                val b = parts[1].toIntOrNull() ?: return null
                a to b
            }
            else -> null
        }
    }

    /** 周次五形态 + 容忍后缀 → WeekSpec; 形状非法 → null。反写区间 (E<S) 交调用方钳制。 */
    data class WeekSpec(val start: Int, val end: Int, val type: Int)

    fun parseWeekSpec(s: String): WeekSpec? {
        val t = s.trim()
        if (t.isEmpty()) return null
        // 未知后缀判定: 形如 数字+区间+单字符后缀但后缀不在词表 → null(形状非法, 上报)
        val m = Regex("""^(\d+)(?:([-~–—〜至])(\d+))?(单|双|定|散|奇|偶|o|odd|e|even)?$""").find(t)
            ?: return null
        val start = m.groupValues[1].toIntOrNull() ?: return null
        val end = m.groupValues[3]?.toIntOrNull() ?: start
        // groupValues 对未参与组返回 ""(非 null), 须用 groups[..] 判参与
        val type = when (m.groupValues[4]) {
            "", null -> if (m.groups[3] == null) 3 else 0  // 裸单数字=只上这周; 区间无后缀=每周
            "单", "奇", "o", "odd" -> 1
            "双", "偶", "e", "even" -> 2
            "定", "散" -> 3
            else -> return null
        }
        return WeekSpec(start, end, type)
    }

    // region 周次导出(§4 规范形)

    /** (S,E,type) → 规范形 token。type 0 恒带横线(8-8); type 3 恒带后缀(单值写 S定)。 */
    fun weekSpecToToken(startWeek: Int, endWeek: Int, type: Int): String {
        val range = "$startWeek-$endWeek"
        return when (type) {
            1 -> "${range}单"
            2 -> "${range}双"
            3 -> if (startWeek == endWeek) "${startWeek}定" else "${range}定"
            else -> range
        }
    }

    // endregion

    // ---- 星期 (§3.1 day 列) ----

    private val DAY_NAMES = mapOf(
        "一" to 1, "二" to 2, "三" to 3, "四" to 4,
        "五" to 5, "六" to 6, "日" to 7, "天" to 7
    )

    /** `1..7` / `周X` / `星期X` / `礼拜X` / 裸`三`; 非法形状 → null。越界单数字(0/8/9)交调用方钳制。 */
    fun parseDay(s: String): Int? {
        val t = s.trim()
        if (t.isEmpty()) return null
        // §2 文法 day = DIGIT{1}|周X…: 形状层只认单数字, 多位(13/2026)属形状非法
        if (t.length == 1 && t[0].isDigit()) return t[0] - '0'
        val stripped = t.removePrefix("礼拜").removePrefix("星期").removePrefix("周")
        DAY_NAMES[stripped]?.let { return it }
        // 周X/星期X 后跟单数字 (§2: ...|"天"|DIGIT{1})
        if (stripped.length == 1 && stripped[0].isDigit()) return stripped[0] - '0'
        return null
    }

    // ---- Nd 冻结预设 (§5; 规范常量而非应用当前默认) ----

    /** (start, end) 对, 节号 = 下标 + 1。与 TimeTableUtils.DEFAULT_TIME_JSON 逐值一致(测试锁定)。 */
    val ND_PRESET: List<Pair<LocalTime, LocalTime>> = listOf(
        LocalTime.of(8, 0) to LocalTime.of(8, 45),
        LocalTime.of(8, 55) to LocalTime.of(9, 40),
        LocalTime.of(10, 0) to LocalTime.of(10, 45),
        LocalTime.of(10, 55) to LocalTime.of(11, 40),
        LocalTime.of(14, 0) to LocalTime.of(14, 45),
        LocalTime.of(14, 55) to LocalTime.of(15, 40),
        LocalTime.of(16, 0) to LocalTime.of(16, 45),
        LocalTime.of(16, 55) to LocalTime.of(17, 40),
        LocalTime.of(19, 0) to LocalTime.of(19, 45),
        LocalTime.of(19, 55) to LocalTime.of(20, 40),
        LocalTime.of(20, 50) to LocalTime.of(21, 35),
        LocalTime.of(21, 45) to LocalTime.of(22, 30)
    )

    /** 作息逐值等于冻结预设? (导出端决定写 Nd 还是逐节 N 行) */
    fun matchesNdPreset(timeJson: String): Boolean {
        val nodes = com.lingion.sleepy.util.TimeTableUtils.parseNodes(timeJson)
        if (nodes.size != ND_PRESET.size) return false
        return nodes.allIndexed(nodes)
    }

    private fun List<com.lingion.sleepy.util.TimeTableUtils.NodeTime>.allIndexed(
        nodes: List<com.lingion.sleepy.util.TimeTableUtils.NodeTime>
    ): Boolean {
        for (i in nodes.indices) {
            val n = nodes[i]
            if (n.node != i + 1) return false
            if (n.start != ND_PRESET[i].first || n.end != ND_PRESET[i].second) return false
        }
        return true
    }

    /** HH:mm */
    fun fmtTime(t: LocalTime): String = String.format("%02d:%02d", t.hour, t.minute)

    // ---- crc32 (§8.1-3, 8 位小写 hex) ----

    fun crc32(bytes: ByteArray): String {
        val c = CRC32()
        c.update(bytes)
        return String.format("%08x", c.value)
    }
}
