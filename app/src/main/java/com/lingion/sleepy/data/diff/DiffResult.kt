package com.lingion.sleepy.data.diff

import com.lingion.sleepy.data.entity.CourseEntity

/**
 * 行级 diff 结果 — 落到数据库的三类操作。
 *
 * 课程编辑保存时，由 [RowKeyDiffer.diff] 比较现况和草稿，再在保存事务内应用。
 *
 * @property toInsert 新行(id=0 让 Room 自增)
 * @property toUpdate 字段变动但 RowKey 不变(包含 server 原 id, 落库按 id 覆盖)
 * @property toDelete 被删行的 server id
 */
data class DiffResult(
    val toInsert: List<CourseEntity>,
    val toUpdate: List<CourseEntity>,
    val toDelete: List<Long>
)
