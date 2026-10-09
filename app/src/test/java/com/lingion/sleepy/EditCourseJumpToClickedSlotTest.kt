package com.lingion.sleepy

import androidx.compose.runtime.mutableStateListOf
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.ui.screen.edit.MeetingBlockDraft
import com.lingion.sleepy.ui.screen.edit.findTargetBlockIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 编辑页 "自动跳到用户点击的时段" 接线契约。
 *
 * 行为：用户从周/网格/今日视图点某门课的某颗胶囊 → 课程详情 sheet → 编辑 →
 * 编辑页的 LazyColumn 必须 animateScrollToItem 到匹配 editingCourse.id 的卡。
 *
 * 仓库目前没有 Compose UI 测试环境，源码契约锁最容易回退的接线点（sourceIds /
 * state / size-key / >0 才滚），JVM 纯函数测锁匹配逻辑本身的歧义场景
 * (用户报障 2026-10-08: 跨 step/week/ownTime/room 的两组可共享 (day, startNode),
 * 旧匹配 (day+startNode) 命中错组)。
 */
class EditCourseJumpToClickedSlotTest {

    private val source: String by lazy {
        sequenceOf(
            File("app/src/main/java/com/lingion/sleepy/ui/screen/edit/AddCourseScreen.kt"),
            File("src/main/java/com/lingion/sleepy/ui/screen/edit/AddCourseScreen.kt")
        ).first { it.isFile }.readText()
    }

    @Test
    fun `LazyColumn state is bound to rememberLazyListState`() {
        assertTrue("必须 import rememberLazyListState", source.contains("import androidx.compose.foundation.lazy.rememberLazyListState"))
        assertTrue("必须有 val listState = rememberLazyListState()", source.contains("rememberLazyListState()"))
        // LazyColumn 形参 state = listState — 唯一决定滚动接管权归此 state
        assertTrue("LazyColumn 必须接 state = listState", source.contains("state = listState"))
    }

    @Test
    fun `scroll target is matched by editing course id against sourceIds`() {
        // v2 匹配: editingCourse.id in block.sourceIds (CourseEntity 主键唯一)。
        // v1 的 day+startNode 模糊匹配在跨组歧义下错, 此处禁 v1 公式回归。
        assertTrue(
            "必须 findTargetBlockIndex 用 editingCourse.id in block.sourceIds 匹配",
            source.contains("findTargetBlockIndex(meetingBlocks, editingCourse)")
        )
        assertTrue(
            "MeetingBlockDraft 必须带 sourceIds 字段",
            source.contains("val sourceIds: List<Long>")
        )
        assertTrue(
            "group 填充处必须把 courses.map { it.id } 写进 sourceIds",
            source.contains("sourceIds = courses.map { it.id }")
        )
    }

    @Test
    fun `scroll is triggered when target index is valid`() {
        // 即使 idx=0, header 也占屏幕空间, 必须滚到块；目标还要加上前置 item 偏移。
        assertTrue("必须 targetIdx >= 0 才滚", source.contains("if (targetIdx >= 0)"))
        assertTrue(
            "必须按 LazyColumn 的前置 item 数修正目标下标",
            source.contains("listState.animateScrollToItem(headerItemCount + targetIdx)")
        )
        assertTrue(
            "无 groupId 的编辑课程也必须把初始块绑定到课程 id",
            source.contains("sourceIds = listOf(course.id)")
        )
    }

    @Test
    fun `scroll effect waits for group blocks to finish loading`() {
        // meetingBlocks 初始只有 1 个 initialMeetingBlock, group 加载完才到全量。
        // 用 meetingBlocks.size 当 key 让 effect 在 size 跳变后再算 targetIdx,
        // 否则拿到的是临时首块的 0 → 不滚 → 后续 group 加载完也不再触发。
        val launchEffectKeys = Regex(
            "LaunchedEffect\\([^)]*editingCourse\\?\\.id[^)]*meetingBlocks\\.size[^)]*\\)"
        )
        assertTrue(
            "必须 LaunchedEffect(editingCourse?.id, meetingBlocks.size) 等 group 加载",
            launchEffectKeys.containsMatchIn(source)
        )
    }
}

/**
 * findTargetBlockIndex 纯函数 JVM 直测 — 锁匹配公式正确性, 重点覆盖歧义场景。
 */
class FindTargetBlockIndexTest {

    private fun block(id: Int, sourceIds: List<Long>, days: List<Int> = listOf(1), startNode: Int = 1, step: Int = 2) =
        MeetingBlockDraft(
            id = id,
            sourceIds = sourceIds,
            days = mutableStateListOf<Int>().apply { addAll(days) },
            startNode = startNode,
            step = step,
            startTime = "08:00",
            endTime = "09:40"
        )

    private fun course(id: Long, day: Int = 1, startNode: Int = 1, step: Int = 2) =
        CourseEntity(
            id = id,
            groupId = "g1", tableId = 1, courseName = "n", alias = "",
            teacher = "", room = "", note = "",
            day = day, startNode = startNode, step = step,
            startWeek = 1, endWeek = 16, type = 0,
            color = "", colorMode = com.lingion.sleepy.data.entity.CourseColorMode.GROUP,
            isIrregularNode = false, isIrregularTime = false, ownTime = false,
            startTime = "", endTime = ""
        )

    @Test
    fun `null editing course returns -1`() {
        val blocks = listOf(block(1, listOf(1L)))
        assertEquals(-1, findTargetBlockIndex(blocks, null))
    }

    @Test
    fun `editing course id matches sourceIds returns its index`() {
        val blocks = listOf(
            block(1, listOf(10L, 11L)),
            block(2, listOf(20L)),
            block(3, listOf(30L, 31L, 32L))
        )
        // 不同位置都能命中
        assertEquals(0, findTargetBlockIndex(blocks, course(id = 11L)))
        assertEquals(1, findTargetBlockIndex(blocks, course(id = 20L)))
        assertEquals(2, findTargetBlockIndex(blocks, course(id = 32L)))
    }

    @Test
    fun `editing course id not in any block returns -1`() {
        val blocks = listOf(block(1, listOf(10L)), block(2, listOf(20L)))
        assertEquals(-1, findTargetBlockIndex(blocks, course(id = 999L)))
    }

    /**
     * 用户报障 2026-10-08 — 同 (day, startNode) 跨组歧义:
     * - 课程 A: 周一 1-2节, step=2
     * - 课程 B: 周一 1节,   step=1
     * groupSlotsForEdit 按 (ownTime, startNode, step, ...) 切分, A 与 B 落入不同
     * 块, 但 (day=1, startNode=1) 共享。v1 用 (day+startNode) 匹配时 indexOfFirst
     * 命中靠前的块, 用户点 B 错跳 A。v2 用 CourseEntity.id 区分, 必命中 B。
     */
    @Test
    fun `ambiguous (day, startNode) across groups resolves by id`() {
        val courseAId = 100L  // 周一 1-2节, step=2
        val courseBId = 200L  // 周一 1节,   step=1
        val blocks = listOf(
            block(id = 1, sourceIds = listOf(courseAId), days = listOf(1), startNode = 1, step = 2),
            block(id = 2, sourceIds = listOf(courseBId), days = listOf(1), startNode = 1, step = 1)
        )
        // 用户点 A → 必返回 0
        assertEquals(0, findTargetBlockIndex(blocks, course(id = courseAId, day = 1, startNode = 1, step = 2)))
        // 用户点 B → 必返回 1 (v1 错返 0, 这条测试即用户报障的反例回归锁)
        assertEquals(1, findTargetBlockIndex(blocks, course(id = courseBId, day = 1, startNode = 1, step = 1)))
    }

    /**
     * 同 (day, startNode) 跨周次: 课程 A 第 1-8 周, 课程 B 第 9-16 周。
     * groupSlotsForEdit 按 startWeek/endWeek 切组, (day, startNode) 同样共享。
     */
    @Test
    fun `ambiguous (day, startNode) across week ranges resolves by id`() {
        val courseAId = 300L  // 第 1-8 周
        val courseBId = 400L  // 第 9-16 周
        val blocks = listOf(
            block(id = 1, sourceIds = listOf(courseAId)),
            block(id = 2, sourceIds = listOf(courseBId))
        )
        val editingA = course(id = courseAId).copy(startWeek = 1, endWeek = 8)
        val editingB = course(id = courseBId).copy(startWeek = 9, endWeek = 16)
        assertEquals(0, findTargetBlockIndex(blocks, editingA))
        assertEquals(1, findTargetBlockIndex(blocks, editingB))
    }
}
