package com.lingion.sleepy.widget

/**
 * 课程颜色规则的公共定义 — 单一事实来源。
 *
 * 之前课程色规则在三个文件各自硬编码(WidgetContent / WeekGridWidgetProvider / WidgetBitmapRenderers),
 * 关键词列表和 hash 回退逻辑极易不一致。本文件抽出公共部分, 三处调用方各自提供色值映射。
 *
 * 关键词顺序 = 匹配优先级, 与首页 CourseTableView 保持一致。
 */

/** 课程色类别 key — 对应调色板中的一个槽位 */
enum class CourseColorKey {
    ENGLISH, MILITARY, PHYSICS, HISTORY, PSYCHOLOGY, PRACTICE, PRIMARY, TERTIARY,
    /** hash 回退用的 6 色集合 */
    SECONDARY;

    companion object {
        /** hash 回退调色板顺序(与原三处 hashPalette 一致) */
        val HASH_FALLBACK = listOf(PRIMARY, SECONDARY, TERTIARY, ENGLISH, PHYSICS, PSYCHOLOGY)
    }
}

/**
 * 关键词 → 类别 的有序规则表。匹配第一个命中的规则, 否则走 hash 回退。
 * 输入: 课程名; 输出: 类别 key。
 */
private val KEYWORD_RULES = listOf(
    CourseColorKey.ENGLISH    to listOf("英语"),
    CourseColorKey.MILITARY   to listOf("军事", "国防"),
    CourseColorKey.PHYSICS    to listOf("物理"),
    CourseColorKey.HISTORY    to listOf("历史", "史纲", "近代史"),
    CourseColorKey.PSYCHOLOGY to listOf("心理"),
    CourseColorKey.PRACTICE   to listOf("实践", "实习", "实验"),
    CourseColorKey.PRIMARY    to listOf("高数", "数学", "电路"),
    CourseColorKey.TERTIARY   to listOf("思政", "马原", "毛概", "形势")
)

/**
 * 按 [KEYWORD_RULES] 匹配课程名; 命中返回对应 key, 否则按名字 hash 落到 [CourseColorKey.HASH_FALLBACK]。
 * 纯函数, 无副作用, 适合直接单测。
 */
fun resolveCourseColorKey(name: String): CourseColorKey {
    KEYWORD_RULES.firstOrNull { (_, kws) -> kws.any { it in name } }
        ?.let { (key, _) -> return key }
    val h = name.hashCode() and 0x7FFFFFFF
    return CourseColorKey.HASH_FALLBACK[h % CourseColorKey.HASH_FALLBACK.size]
}
