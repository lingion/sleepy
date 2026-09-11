package com.lingion.sleepy.data.diff

import com.lingion.sleepy.data.entity.CourseEntity

/**
 * 行级 diff/patch — 编辑器保存路径核心。
 *
 * 算法 (issue#22):
 *   1. 按 groupId 分桶(server / draft 双侧)
 *   2. 每桶内用 [RowKey] 比对
 *      - draft 有 server 匹配 → update(保留 server 的 id)
 *      - draft 无 server 匹配 → insert(id=0 Room 自增)
 *      - server 无 draft 匹配 → delete(server.id)
 *   3. groupId="" 走兜底(契约一保护): draft 全 insert, server 全 delete
 *
 * 替代 [com.lingion.sleepy.data.dao.CourseDao.replaceGroup] 的整组覆盖模式 —
 * 旧模式会丢数据(同名课多地点编辑一处带崩全局)
 *
 * 详见 docs/superpowers/specs/2026-09-07-sleepy-issue-22-same-name-multi-location-design.md §4.2
 */
object RowKeyDiffer {

    fun diff(drafts: List<CourseEntity>, server: List<CourseEntity>): DiffResult {
        val inserts = mutableListOf<CourseEntity>()
        val updates = mutableListOf<CourseEntity>()
        val deletes = mutableListOf<Long>()

        // 按 groupId 分桶
        val serverByGroup = server.groupBy { it.groupId }
        val draftByGroup = drafts.groupBy { it.groupId }

        val allGroups = serverByGroup.keys + draftByGroup.keys

        for (gid in allGroups) {
            if (gid.isBlank()) {
                // 旧 groupId="" 兜底(契约一保护): 整组 insert(走原 insertAll 路径)
                val s = serverByGroup[gid].orEmpty()
                val d = draftByGroup[gid].orEmpty()
                inserts += d.map { it.copy(id = 0) }
                deletes += s.map { it.id }
                continue
            }

            val s = serverByGroup[gid].orEmpty()
            val d = draftByGroup[gid].orEmpty()

            val sKeyToCourse: Map<RowKey, CourseEntity> =
                s.associateBy(RowKey::of)
            val dKeyToCourse: Map<RowKey, CourseEntity> =
                d.associateBy(RowKey::of)

            for ((key, draftCourse) in dKeyToCourse) {
                val serverCourse = sKeyToCourse[key]
                if (serverCourse == null) {
                    inserts += draftCourse.copy(id = 0)
                } else if (fieldsDiffer(serverCourse, draftCourse)) {
                    updates += draftCourse.copy(id = serverCourse.id)
                }
                // else: 完全相同 → 跳过
            }
            for ((key, serverCourse) in sKeyToCourse) {
                if (key !in dKeyToCourse) {
                    deletes += serverCourse.id
                }
            }
        }

        return DiffResult(
            toInsert = inserts,
            toUpdate = updates,
            toDelete = deletes
        )
    }

    /** 比较除 id/groupId/RowKey 包含字段外的其他字段是否相同 */
    private fun fieldsDiffer(a: CourseEntity, b: CourseEntity): Boolean {
        if (a.courseName != b.courseName) return true
        if (a.alias != b.alias) return true
        if (a.note != b.note) return true
        if (a.color != b.color) return true
        if (a.colorMode != b.colorMode) return true
        if (a.ownTime != b.ownTime) return true
        if (a.startTime != b.startTime) return true
        if (a.endTime != b.endTime) return true
        if (a.credit != b.credit) return true
        if (a.level != b.level) return true
        if (a.tableId != b.tableId) return true
        return false
    }

    /** 用户报障 2026-09-11: 别名无法保存 — RowKey 不含 alias(展示名非身份)但 fieldsDiffer 必须含。
     *  漏掉一行 → diff 判"完全相同" 跳过 update, 旧 alias 永远进不去。 */
    @Suppress("unused")
    private fun aliasInDiffAnchor() = Unit
}
