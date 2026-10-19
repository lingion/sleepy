package com.lingion.sleepy.golden

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.ui.component.ConflictClusterCard
import com.lingion.sleepy.ui.theme.SleepyThemeProvider
import com.lingion.sleepy.util.ConflictLayoutEngine
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Compose-path pixel golden for the timetable conflict overlay card — the
 * stacked-card treatment shown when two courses occupy overlapping nodes on
 * the same day. Cluster is computed by the production ConflictLayoutEngine,
 * then rendered standalone at fixed grid geometry.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rUS-w360dp-h640dp-port-xhdpi")
class ConflictCardGoldenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun conflictClusterCardMatchesGolden() {
        // Wednesday overlap: 大学物理 nodes 1-2 vs 体育 mechanics 2-3 → 1 cluster, 2 layers.
        val courses = listOf(
            course(1, "大学物理", "g-physics", 3, 1, "A203"),
            course(2, "体育", "g-pe", 3, 2, "体育馆"),
        )
        val cluster = ConflictLayoutEngine.findClusters(courses).single()

        composeRule.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                Box(modifier = Modifier.size(220.dp, 200.dp)) {
                    ConflictClusterCard(
                        cluster = cluster,
                        style = "rail",
                        topOverrideId = null,
                        onPickTop = {},
                        onCourseClick = {},
                        colW = 90.dp,
                        rowH = 60.dp,
                        maxNode = 10,
                        timeW = 40.dp,
                        gapW = 4.dp,
                        gapH = 2.dp,
                        isGrey = false,
                    )
                }
            }
        }
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        GoldenCompare.assertGolden(bitmap, "conflict_card_rail.png")
        bitmap.recycle()
    }

    private fun course(id: Long, name: String, groupId: String, day: Int, node: Int, room: String) =
        CourseEntity(
            id = id,
            groupId = groupId,
            tableId = 1L,
            courseName = name,
            day = day,
            startNode = node,
            step = 2,
            startWeek = 1,
            endWeek = 16,
            room = room,
            color = "#FF6750A4",
        )
}
