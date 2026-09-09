package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.CourseEntity

/**
 * issue#26 课程别名 — 展示名解析。
 *
 * 语义: alias 是"展示名", 不改身份。
 *   - useAlias=false → 原名 (三场景设置默认值, 升级用户行为不变)
 *   - useAlias=true  → trim 后别名; 空串回退原名
 *
 * 别名不参与匹配/导出/通知/详情等身份场景 — 那些点直接读 course.courseName,
 * 不过本工具(编译层面保证: 详情/预览等场景调用点不引这个 util)。
 */
object CourseDisplayUtil {

    /** 展示名 = 场景开关开启时取别名(空回退原名), 否则原名 */
    fun displayName(course: CourseEntity, useAlias: Boolean): String {
        if (!useAlias) return course.courseName
        val trimmed = course.alias.trim()
        return trimmed.ifEmpty { course.courseName }
    }

    /** 便捷重载: 逐课独立判断 — 周视图两栏 "balance" 等需要按行混合场景 */
    fun displayName(course: CourseEntity, useAliasFor: (CourseEntity) -> Boolean): String =
        displayName(course, useAliasFor(course))
}
