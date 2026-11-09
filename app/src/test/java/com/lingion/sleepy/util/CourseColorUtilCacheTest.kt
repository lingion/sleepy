package com.lingion.sleepy.util

import androidx.compose.ui.graphics.Color
import com.lingion.sleepy.data.entity.CourseColorMode
import com.lingion.sleepy.data.entity.CourseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CourseColorUtilCacheTest {

    private fun makeCourse(
        id: Long = 1L,
        groupId: String = "group-math",
        color: String = "",
        colorMode: Int = CourseColorMode.GROUP
    ) = CourseEntity(
        id = id,
        tableId = 1L,
        courseName = "高等数学",
        room = "A101",
        teacher = "张老师",
        day = 1,
        startNode = 1,
        step = 2,
        startWeek = 1,
        endWeek = 16,
        type = 0,
        color = color,
        colorMode = colorMode,
        groupId = groupId,
    )

    @Test
    fun pickCourseColor_returns_identical_instances_for_repeated_calls() {
        val course = makeCourse()
        val neutral = Color.LightGray

        val c1 = CourseColorUtil.pickCourseColorComposeWithGroupRows(
            row = course,
            groupRows = listOf(course),
            isDark = false,
            neutralColor = neutral,
            colorless = false,
            persistedHue = null
        )
        val c2 = CourseColorUtil.pickCourseColorComposeWithGroupRows(
            row = course,
            groupRows = listOf(course),
            isDark = false,
            neutralColor = neutral,
            colorless = false,
            persistedHue = null
        )

        assertEquals("两次计算颜色值必须相等", c1, c2)
    }

    @Test
    fun customColor_parse_is_cached_and_safe() {
        val course = makeCourse(color = "#FF3366")
        val neutral = Color.LightGray

        val c1 = CourseColorUtil.pickCourseColorComposeWithGroupRows(
            row = course,
            groupRows = listOf(course),
            isDark = false,
            neutralColor = neutral,
            colorless = false,
            persistedHue = null
        )
        val c2 = CourseColorUtil.pickCourseColorComposeWithGroupRows(
            row = course,
            groupRows = listOf(course),
            isDark = false,
            neutralColor = neutral,
            colorless = false,
            persistedHue = null
        )

        assertEquals(Color(0xFFFF3366), c1)
        assertEquals(c1, c2)
    }

    @Test
    fun textColorOn_adapts_correctly() {
        val darkBg = Color(0xFF101010)
        val lightBg = Color(0xFFEEEEEE)
        val onSurface = Color(0xFF333333)

        val textOnDark = CourseColorUtil.textColorOn(darkBg, isDark = false, onSurface = onSurface)
        val textOnLight = CourseColorUtil.textColorOn(lightBg, isDark = false, onSurface = onSurface)

        assertEquals("深色底必须用白字", Color.White, textOnDark)
        assertEquals("浅色底浅色模式用 onSurface", onSurface, textOnLight)
    }
}
