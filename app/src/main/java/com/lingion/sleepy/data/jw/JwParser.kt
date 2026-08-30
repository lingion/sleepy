package com.lingion.sleepy.data.jw

import com.lingion.sleepy.data.entity.CourseEntity

/**
 * 教务系统 HTML 解析器抽象基类。
 *
 * 设计来自 dIT8Zv/WakeupSchedule_BUPT (Apache-2.0) 的 Parser.kt
 * (https://github.com/dIT8Zv/WakeupSchedule_BUPT/blob/master/app/src/main/java/com/suda/yzune/wakeupschedule/schedule_import/parser/Parser.kt)
 *
 * 简化点：
 *   - 去掉了 [saveCourse] / [convertCourse] 中对 wakeup 私有 bean 的依赖
 *   - 改成直接输出 List<JwCourse>，由 [JwImportViewModel] 统一转 [CourseEntity]
 *   - 去掉了 Context 依赖（颜色生成等放到 ViewModel 层）
 *
 * 用法：
 *   ```
 *   val courses = JwQzCrazyParser(html).generateCourseList()
 *   ```
 */
abstract class JwParser(val source: String) {

    /**
     * 解析教务 HTML 源码，输出统一结构的课程列表
     */
    abstract fun generateCourseList(): List<JwCourse>

    /**
     * T8 新增：基于 HTML 结构锚点的命中置信度（0..100）。
     *
     * 规则：
     *   - 命中协议族唯一锚点（如 Table1 / kbtable / kbxx / datagrid）：80..100
     *   - 命中协议族常见锚点（font[title=老师] 等单元格级）：50..79
     *   - 仅靠解析结果反推（无法证伪）：0..49
     *   - 兜底返回 0
     *
     * 实现要点：
     *   - confidence 不调 generateCourseList（避免 N+1），只看 HTML 静态特征
     *   - confidence 不依赖协议类型上下文（Registry 层做优先级裁决）
     */
    open fun confidence(): Int = 0

    /**
     * T8 新增：本 parser 实际命中的 HTML 锚点列表（用于诊断输出）。
     * 默认空，子类按需覆盖。
     */
    open fun matchedFeatures(): List<String> = emptyList()
}
