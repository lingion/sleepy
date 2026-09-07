package com.lingion.sleepy.data.diff

import com.lingion.sleepy.data.entity.CourseEntity

/**
 * 行级 diff 结果 — 落到数据库的三类操作。
 *
 * 替代旧 `replaceGroup(tableId, groupId, newCourses)` 整组覆盖模式;
 * editor 保存时把"现况"和"草稿"喂给 [RowKeyDiffer.diff] 即可。
 *
 * @property toInsert 新行(id=0 让 Room 自增; 若携带非零 id 走 [CourseDao.insertKeepId])
 * @property toUpdate 字段变动但 RowKey 不变(包含 server 原 id, 落库按 id 覆盖)
 * @property toDelete 被删行的 server id
 */
data class DiffResult(
    val toInsert: List<CourseEntity>,
    val toUpdate: List<CourseEntity>,
    val toDelete: List<Long>
)
