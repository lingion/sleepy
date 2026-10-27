package com.lingion.sleepy.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.ui.theme.SleepyThemeProvider
import com.lingion.sleepy.util.TimeTableUtils
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Contract for the long-press float interaction: the card lifts above the grid with a blurred
 * backdrop and a popup (edit / delete), may be resized by its edges (pending until confirmed,
 * contract D), drags to a new cell where the drop asks before moving (contract C, resolved via
 * the onResult callback), and one continuous press can lift-then-drag without lifting the
 * finger (contract A). Empty cells open the add capsule only on long-press in float mode
 * (contract B); legacy tap-open stays for the non-float grid.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rUS-w360dp-h640dp-port-xhdpi")
class CourseOccurrenceFloatDragTest {
    @get:Rule val compose = createComposeRule()
    private val course = CourseEntity(id = 7, tableId = 1, groupId = "math", courseName = "FloatMath",
        day = 1, startNode = 1, step = 2, startWeek = 1, endWeek = 16, color = "#FF6750A4")
    // 置灰「同组其他格不动」的对照卡: 不同星期不同节次的第二条课。
    private val other = CourseEntity(id = 8, tableId = 1, groupId = "eng", courseName = "OtherCourse",
        day = 3, startNode = 4, step = 1, startWeek = 1, endWeek = 16, color = "#FF00A000")

    private val slots = TimeTableUtils.timeSlotsFor(TimeTableUtils.DEFAULT_TIME_JSON)
    private val moves = mutableListOf<Move>()
    private val deleted = mutableListOf<Long>()
    private val longPressed = mutableListOf<Long>()
    private var clicks = 0
    // C 契约: 落库回调 — true=已保存收层, false=否决弹回。测试按需翻转。
    private var saved = true

    data class Move(val id: Long, val day: Int, val node: Int, val step: Int, val resizeOnly: Boolean)

    private fun grid(
        state: CourseFloatState = CourseFloatState(),
        courses: List<CourseEntity> = listOf(course),
        onMove: ((CourseEntity, Int, Int, Int, Boolean, (Boolean) -> Unit) -> Unit)? =
            { c, d, n, s, resizeOnly, onResult ->
                moves.add(Move(c.id, d, n, s, resizeOnly)); onResult(saved)
            },
        onDelete: ((CourseEntity) -> Unit)? = { deleted.add(it.id) },
        onLongPress: (CourseEntity) -> Unit = { longPressed.add(it.id) },
        onOccurrenceEdit: ((CourseEntity) -> Unit)? = null,
        onQuickAdd: ((Int, Int, Int) -> Unit)? = { _, _, _ -> },
    ) {
        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                Box(Modifier.size(360.dp, 640.dp)) {
                    CardsGridView(courses = courses, timeSlots = slots,
                        floatState = state,
                        onQuickAdd = onQuickAdd,
                        onOccurrenceMove = onMove, onOccurrenceDelete = onDelete,
                        onOccurrenceEdit = onOccurrenceEdit,
                        onCourseClick = { clicks++ }, onCourseLongClick = onLongPress)
                }
            }
        }
    }

    /** 浮动态把手拉伸出 pending: 底边下拖一行 → endNode 3, 不落库 (D 前半)。 */
    private fun stretchBottomHandle(state: CourseFloatState) {
        val rowHeight = compose.onNodeWithTag("add-course-1-3").fetchSemanticsNode().boundsInRoot.top -
            compose.onNodeWithTag("add-course-1-2").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("float-bottom", useUnmergedTree = true).performTouchInput {
            swipe(center, center + Offset(0f, rowHeight), 300)
        }
        compose.runOnIdle {
            assertEquals(3, state.endNode)
            assertTrue(state.spanChanged)
            assertTrue(moves.isEmpty())
        }
    }

    @Test fun longPressWithFloatEnabledLiftsTheCardWithPopupAndKeepsTheEditorClosed() {
        grid()
        compose.onNodeWithText("FloatMath").performTouchInput { longClick() }
        compose.onNodeWithTag("float-backdrop").assertIsDisplayed()
        compose.onNodeWithTag("float-popup").assertIsDisplayed()
        compose.onNodeWithTag("float-edit").assertIsDisplayed()
        compose.onNodeWithTag("float-delete").assertIsDisplayed()
        compose.onNodeWithTag("float-card").assertIsDisplayed()
        compose.runOnIdle { assertTrue(longPressed.isEmpty()); assertEquals(0, clicks); assertTrue(moves.isEmpty()) }
    }

    @Test fun longPressWithoutMoveCallbackKeepsTheLegacyEditorShortcut() {
        grid(onMove = null)
        compose.onNodeWithText("FloatMath").performTouchInput { longClick() }
        compose.onNodeWithTag("float-popup").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf(7L), longPressed) }
    }

    @Test fun popupEditOpensTheOccurrenceEditorAndDismissesTheFloat() {
        grid()
        compose.onNodeWithText("FloatMath").performTouchInput { longClick() }
        compose.onNodeWithTag("float-edit").performClick()
        compose.runOnIdle { assertEquals(listOf(7L), longPressed); assertTrue(deleted.isEmpty()) }
        compose.onNodeWithTag("float-popup").assertDoesNotExist()
        compose.onNodeWithTag("float-card").assertDoesNotExist()
        compose.onNodeWithTag("float-backdrop").assertDoesNotExist()
    }

    /** 2026-10-27 用户裁决: 浮窗上已点「调整本次」, 编辑器不得再要求二次点击。
     *  onOccurrenceEdit 提供时浮窗调整走该直达回调, 不再借用长按通道;
     *  未提供时退回长按通道(上一条用例锁定) — 存量调用方契约不变。 */
    @Test fun popupEditUsesTheDedicatedAdjustCallbackWhenProvided() {
        val edited = mutableListOf<Long>()
        grid(onOccurrenceEdit = { edited.add(it.id) })
        compose.onNodeWithText("FloatMath").performTouchInput { longClick() }
        compose.onNodeWithTag("float-edit").performClick()
        compose.runOnIdle {
            assertEquals(listOf(7L), edited)
            assertTrue(longPressed.isEmpty()); assertTrue(deleted.isEmpty())
        }
        compose.onNodeWithTag("float-popup").assertDoesNotExist()
        compose.onNodeWithTag("float-backdrop").assertDoesNotExist()
    }

    @Test fun popupDeleteAsksForThatOccurrenceAndDismissesTheFloat() {
        grid()
        compose.onNodeWithText("FloatMath").performTouchInput { longClick() }
        compose.onNodeWithTag("float-delete").performClick()
        compose.runOnIdle { assertEquals(listOf(7L), deleted); assertTrue(longPressed.isEmpty()) }
        compose.onNodeWithTag("float-popup").assertDoesNotExist()
        compose.onNodeWithTag("float-card").assertDoesNotExist()
    }

    @Test fun pressingTheBackdropAnywhereElseDismissesTheFloatWithoutSideEffects() {
        grid()
        compose.onNodeWithText("FloatMath").performTouchInput { longClick() }
        compose.onNodeWithTag("add-course-3-2").performTouchInput { click() }
        compose.onNodeWithTag("float-popup").assertDoesNotExist()
        compose.onNodeWithTag("float-card").assertDoesNotExist()
        compose.onNodeWithTag("quick-add-capsule").assertDoesNotExist()
        compose.runOnIdle { assertTrue(moves.isEmpty()); assertTrue(longPressed.isEmpty()); assertEquals(0, clicks) }
    }

    @Test fun draggingTheFloatingCardHidesThePopupAndALegalDropMovesTheOccurrence() {
        val state = CourseFloatState()
        grid(state)
        compose.onNodeWithText("FloatMath").performTouchInput { longClick() }
        val card = compose.onNodeWithTag("float-card").fetchSemanticsNode().boundsInRoot
        val target = compose.onNodeWithTag("add-course-3-4").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("float-card").performTouchInput {
            // down() 用节点局部坐标(scope.center); 位移量才是根坐标差
            down(center)
            moveBy(Offset(target.center.x - card.center.x, target.top - card.top), 300)
        }
        assertTrue(state.dragging)
        compose.onNodeWithTag("float-card").performTouchInput { up() }
        compose.runOnIdle {
            assertEquals(listOf(Move(7L, 3, 4, 2, false)), moves)
            assertNull(state.course)
        }
        compose.onNodeWithTag("float-popup").assertDoesNotExist()
    }

    @Test fun vetoedDropSpringsBackAndKeepsTheFloatUp() {
        // C 契约否决路径: onResult(false) → 弹回原位, 浮层与浮框仍在, 未落库。
        saved = false
        val state = CourseFloatState()
        grid(state)
        compose.onNodeWithText("FloatMath").performTouchInput { longClick() }
        val card = compose.onNodeWithTag("float-card").fetchSemanticsNode().boundsInRoot
        val target = compose.onNodeWithTag("add-course-3-4").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("float-card").performTouchInput {
            down(center)
            moveBy(Offset(target.center.x - card.center.x, target.top - card.top), 300)
            up()
        }
        compose.runOnIdle {
            assertEquals(listOf(Move(7L, 3, 4, 2, false)), moves)
            assertNotNull(state.course)
            assertFalse(state.dragging)
        }
        compose.onNodeWithTag("float-popup").assertIsDisplayed()
        assertEquals(card.top, compose.onNodeWithTag("float-card").fetchSemanticsNode().boundsInRoot.top, 0.5f)
    }

    @Test fun resizingTheFloatingCardThenDroppingMovesTheExpandedSpan() {
        val state = CourseFloatState()
        grid(state)
        compose.onNodeWithText("FloatMath").performTouchInput { longClick() }
        stretchBottomHandle(state)
        assertFalse(state.dragging)
        val card = compose.onNodeWithTag("float-card").fetchSemanticsNode().boundsInRoot
        val target = compose.onNodeWithTag("add-course-3-1").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("float-card").performTouchInput {
            down(center)
            moveBy(Offset(target.center.x - card.center.x, target.top - card.top), 300)
            up()
        }
        compose.runOnIdle { assertEquals(listOf(Move(7L, 3, 1, 3, false)), moves) }
    }

    @Test fun droppingOutsideTheGridSpringsBackAndCancelsTheMove() {
        val state = CourseFloatState()
        grid(state)
        compose.onNodeWithText("FloatMath").performTouchInput { longClick() }
        val card = compose.onNodeWithTag("float-card").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("float-card").performTouchInput {
            down(center)
            moveBy(Offset(700f, 150f), 300)
            up()
        }
        compose.runOnIdle {
            assertTrue(moves.isEmpty())
            assertFalse(state.dragging)
            assertNotNull(state.course)
        }
        compose.onNodeWithTag("float-popup").assertIsDisplayed()
        assertEquals(card.top, compose.onNodeWithTag("float-card").fetchSemanticsNode().boundsInRoot.top, 0.5f)
    }

    // ---- A 契约: 一次按下 长按浮起 → 不松手即拖 ----

    @Test fun longPressAndDragWithoutLiftingTheFingerMovesTheOccurrence() {
        val state = CourseFloatState()
        grid(state)
        // 位移量按格顶锚定: add-course-1-1 空格的 root 顶边 = 浮起卡 overlayY 顶边
        // (headH+gapH 同口径), 而卡内文本节点顶边有内衬偏置 — 用它量竖直位移会少算。
        val origin = compose.onNodeWithTag("add-course-1-1").fetchSemanticsNode().boundsInRoot
        val card = compose.onNodeWithText("FloatMath").fetchSemanticsNode().boundsInRoot
        val target = compose.onNodeWithTag("add-course-3-4").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("FloatMath").performTouchInput {
            down(center)
            // 原地按过长按超时 → 浮起; 不松手继续位移 → 同指进入拖拽
            moveTo(center, delayMillis = 900)
            moveBy(Offset(target.center.x - card.center.x, target.top - origin.top), 300)
            up()
        }
        compose.runOnIdle {
            assertEquals(listOf(Move(7L, 3, 4, 2, false)), moves)
            assertNull(state.course)
        }
    }

    @Test fun longPressThenReleaseWithoutMovingKeepsTheCardLifted() {
        val state = CourseFloatState()
        grid(state)
        compose.onNodeWithText("FloatMath").performTouchInput {
            down(center)
            moveTo(center, delayMillis = 900)
            up()
        }
        compose.runOnIdle {
            assertNotNull(state.course)
            assertTrue(moves.isEmpty())
        }
        compose.onNodeWithTag("float-popup").assertIsDisplayed()
    }

    /** 2026-10-27 用户反馈: 长按浮起后手指微抖约 0.1cm, 模糊与浮框瞬间消失。
     *  浮起死区: 自浮起点累计位移未超过 2×touchSlop 前不得进入拖拽 —
     *  参照官方 DragGestureDetector 的 TouchSlopDetector(累计判据, 非逐帧增量)。 */
    @Test fun jitterAfterLiftKeepsTheBlurPopupAndOriginPosition() {
        val state = CourseFloatState()
        grid(state)
        compose.onNodeWithText("FloatMath").performTouchInput {
            down(center)
            moveTo(center, delayMillis = 900)
            // 30px 对角 < 死区(xhdpi: touchSlop 8dp=16px, 2×=32px): 只许抖动, 不许进拖拽
            moveBy(Offset(24f, 18f), 100)
            up()
        }
        compose.onNodeWithTag("float-backdrop").assertIsDisplayed()
        compose.onNodeWithTag("float-popup").assertIsDisplayed()
        compose.runOnIdle {
            assertNotNull(state.course)
            assertFalse(state.dragging)
            assertEquals(state.originX, state.x)
            assertEquals(state.originY, state.y)
            assertTrue(moves.isEmpty())
        }
    }

    /** 超过死区 → 进入拖拽, 模糊浮框让位, 卡片跟手; 与抖动用例共用一段手势证明判据是累计量。 */
    @Test fun crossingTheLiftSlopStartsTheDragAndHidesThePopup() {
        val state = CourseFloatState()
        grid(state)
        compose.onNodeWithText("FloatMath").performTouchInput {
            down(center)
            moveTo(center, delayMillis = 900)
            moveBy(Offset(24f, 18f), 100) // 30px 对角, 死区内
        }
        compose.runOnIdle { assertFalse(state.dragging) }
        compose.onNodeWithTag("float-popup").assertIsDisplayed()
        compose.onNodeWithTag("float-card").performTouchInput {
            moveBy(Offset(80f, 80f), 200) // 自浮起点累计约 134px, 越死区 → 进拖拽
        }
        compose.runOnIdle { assertTrue(state.dragging) }
        compose.onNodeWithTag("float-popup").assertDoesNotExist()
        compose.onNodeWithTag("float-card").performTouchInput { up() }
    }

    @Test fun quickPressOnCardStillOpensTheCourseDetail() {
        grid()
        compose.onNodeWithText("FloatMath").performTouchInput {
            down(center)
            up()
        }
        compose.runOnIdle { assertEquals(1, clicks); assertTrue(moves.isEmpty()) }
    }

    // ---- B 契约: 浮动态空白格 = 长按开胶囊, 轻点无动作; 旧态轻点照开 ----

    @Test fun inFloatModeTappingAnEmptyCellDoesNothingAndLongPressOpensTheCapsule() {
        grid()
        compose.onNodeWithTag("add-course-3-2").performTouchInput { click() }
        compose.runOnIdle { } // 无崩溃即可: 轻点不再开胶囊
        compose.onNodeWithTag("quick-add-capsule").assertDoesNotExist()
        compose.onNodeWithTag("add-course-3-2").performTouchInput { longClick() }
        compose.onNodeWithTag("quick-add-capsule").assertIsDisplayed()
    }

    @Test fun legacyGridStillOpensTheCapsuleOnATap() {
        grid(onMove = null)
        compose.onNodeWithTag("add-course-3-2").performTouchInput { click() }
        compose.onNodeWithTag("quick-add-capsule").assertIsDisplayed()
    }

    // ---- D 契约: 把手拉伸不即时落库, 空白点击走「调整持续节次」提交 ----

    @Test fun stretchedSpanSurvivesTheDragAndBlankTapSubmitsTheResizeForConfirmation() {
        val state = CourseFloatState()
        grid(state)
        compose.onNodeWithText("FloatMath").performTouchInput { longClick() }
        stretchBottomHandle(state)
        // 空白格轻点 → ancestor 消费 → pending 拉伸进同一管线 (resizeOnly=true)
        compose.onNodeWithTag("add-course-3-2").performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(listOf(Move(7L, 1, 1, 3, true)), moves)
            assertNull(state.course) // saved=true → 确认通过收层
        }
    }

    @Test fun vetoedResizeRestoresTheOriginalSpanAndKeepsTheFloatUp() {
        saved = false
        val state = CourseFloatState()
        grid(state)
        compose.onNodeWithText("FloatMath").performTouchInput { longClick() }
        stretchBottomHandle(state)
        compose.onNodeWithTag("add-course-3-2").performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(listOf(Move(7L, 1, 1, 3, true)), moves)
            assertNotNull(state.course)       // 浮层仍在
            assertEquals(2, state.endNode)    // revertSpan 复原原跨度
            assertFalse(state.spanChanged)
            assertEquals(state.originY, state.y)
        }
    }

    @Test fun blankTapWithoutAnyStretchJustDismissesTheFloat() {
        val state = CourseFloatState()
        grid(state)
        compose.onNodeWithText("FloatMath").performTouchInput { longClick() }
        compose.onNodeWithTag("add-course-3-2").performTouchInput { click() }
        compose.runOnIdle {
            assertTrue(moves.isEmpty()) // 无 pending → 不走确认管线
            assertNull(state.course)
        }
    }

    // ---- 拖拽置灰 (2026-10-27 用户裁决: 原占位格纯降透明度, 范围=仅拖拽中仅原格) ----

    @Test fun draggingDimsOnlyTheOriginPlaceholderAndSparesOtherCells() {
        val state = CourseFloatState()
        grid(state, courses = listOf(course, other))
        compose.onNodeWithText("FloatMath").performTouchInput { longClick() }
        compose.runOnIdle { assertFalse(state.dragging) }
        // 未进拖拽 → 原格不置灰 (浮起态只浮卡离开原位, 置灰语义从属拖拽)
        compose.onNodeWithTag("float-dim").assertDoesNotExist()
        compose.onNodeWithTag("float-card").performTouchInput {
            down(center)
            moveBy(Offset(80f, 80f), 200) // 越浮起死区 → 进拖拽
        }
        compose.runOnIdle { assertTrue(state.dragging) }
        // 有且仅有一张置灰卡 — 被拖起的原占位; 同组其他格不动。
        compose.onAllNodesWithTag("float-dim").assertCountEquals(1)
    }

    @Test fun releasingTheDragRestoresThePlaceholderToFullOpacity() {
        val state = CourseFloatState()
        grid(state)
        compose.onNodeWithText("FloatMath").performTouchInput { longClick() }
        compose.onNodeWithTag("float-card").performTouchInput {
            down(center)
            moveBy(Offset(80f, 80f), 200)
        }
        compose.onAllNodesWithTag("float-dim").assertCountEquals(1)
        // 合法落点(第3天第4节) → 落库收层: 置灰随浮动态整体消失
        val card = compose.onNodeWithTag("float-card").fetchSemanticsNode().boundsInRoot
        val target = compose.onNodeWithTag("add-course-3-4").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("float-card").performTouchInput {
            moveBy(Offset(target.center.x - card.center.x, target.top - card.top), 300)
            up()
        }
        compose.runOnIdle {
            assertEquals(listOf(Move(7L, 3, 4, 2, false)), moves)
            assertNull(state.course)
        }
        compose.onNodeWithTag("float-dim").assertDoesNotExist()
    }
}
