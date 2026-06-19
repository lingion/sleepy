package com.lingion.sleepy.data.jw

/**
 * 强智教务系统解析器（Crazy 变体，HEU 等学校使用）。
 *
 * 基于 dIT8Zv/WakeupSchedule_BUPT (Apache-2.0) QzCrazyParser.kt
 * 简化而来：唯一区别是 [tableName] 为 "kbcontent1" 而非 "kbcontent"。
 *
 * 源仓库：https://github.com/dIT8Zv/WakeupSchedule_BUPT/blob/master/app/src/main/java/com/suda/yzune/wakeupschedule/schedule_import/parser/qz/QzCrazyParser.kt
 */
class JwQzCrazyParser(source: String) : JwQzParser(source) {
    override val tableName: String
        get() = "kbcontent1"
}
