package com.lingion.sleepy.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
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
    private val edgeCalls = mutableListOf<Int>()
    // 边缘翻页测试的 pager 替身: 生产里 week bump → 新页 currentWeek 与之相等;
    // 单网格用可变态跟住 floatState.week, 模拟换宿主后的排他渲染与逐页重计。
    private val hostWeek = androidx.compose.runtime.mutableIntStateOf(1)
    private var clicks = 0
    // C 契约: 落库回调 — true=已保存收层, false=否决弹回。测试按需翻转。
    private var saved = true

    data class Move(val id: Long, val day: Int, val node: Int, val step: Int, val resizeOnly: Boolean)

    private fun grid(
        state: CourseFloatState = CourseFloatState(),
        courses: List<CourseEntity> = listOf(course),
        currentWeek: Int? = null,
        onMove: ((CourseEntity, Int, Int, Int, Boolean, (Boolean) -> Unit) -> Unit)? =
            { c, d, n, s, resizeOnly, onResult ->
                moves.add(Move(c.id, d, n, s, resizeOnly)); onResult(saved)
            },
        onDelete: ((CourseEntity) -> Unit)? = { deleted.add(it.id) },
        onLongPress: (CourseEntity) -> Unit = { longPressed.add(it.id) },
        onOccurrenceEdit: ((CourseEntity) -> Unit)? = null,
        onQuickAdd: ((Int, Int, Int) -> Unit)? = { _, _, _ -> },
        floatMaxWeek: Int = 0,
    ) {
        val week = currentWeek
        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                Box(Modifier.size(360.dp, 640.dp)) {
                    CardsGridView(courses = courses, timeSlots = slots,
                        floatState = state,
                        // 读在组合内: hostWeek 变更 → 重组换 currentWeek, 模拟 pager 换宿主。
                        currentWeek = week ?: hostWeek.value,
                        onQuickAdd = onQuickAdd,
                        onOccurrenceMove = onMove, onOccurrenceDelete = onDelete,
                        onOccurrenceEdit = onOccurrenceEdit,
                        // 翻页回调 = pager 替身: 记方向 + 宿主周号跟住 bump 后的 floatState.week
                        onEdgeDrag = { dir ->
                            edgeCalls.add(dir)
                            hostWeek.value = state.week
                        },
                        floatMaxWeek = floatMaxWeek,
                        onCourseClick = { clicks++ }, onCourseLongClick = onLongPress)
                }
            }
        }
    }

    /**
     * 场景C 替身 (2026-10-29): 真 HorizontalPager(beyondViewportPageCount=1) 的物理平移
     * 在 JVM 复现 — 两页各 360dp 并排, 起拖页(第1页, 带被跟踪的网格卡节点)随 week bump
     * 整页左移一屏(offset 布局位移, 指针命中与本地坐标据此换算, 与真 Pager 同构),
     * 新宿主(第2页)落回 root x=0 静止; 指针流仍由被捕获的起拖页节点持有。
     * 既有单页替身翻页只换 currentWeek、从不平移节点 — 正是场景C「翻页后横向失控」
     * 在 JVM 全绿的盲区。第2页课程留空: 避免 FloatMath 双节点歧义, 跨周几何本就同构。
     */
    @Composable
    private fun FloatPager(state: CourseFloatState) {
        val shift = 360.dp * (state.week - 1)
        Box(Modifier.size(360.dp, 640.dp)) {
            Box(Modifier.offset(x = -shift)) {
                CardsGridView(
                    courses = listOf(course),
                    timeSlots = slots,
                    floatState = state,
                    currentWeek = 1,
                    onQuickAdd = { _, _, _ -> },
                    onOccurrenceMove = { c, d, n, s, resizeOnly, onResult ->
                        moves.add(Move(c.id, d, n, s, resizeOnly)); onResult(saved)
                    },
                    onOccurrenceDelete = { deleted.add(it.id) },
                    onOccurrenceEdit = null,
                    onEdgeDrag = { dir -> edgeCalls.add(dir) },
                    floatMaxWeek = 16,
                    onCourseClick = { clicks++ },
                    onCourseLongClick = { longPressed.add(it.id) },
                )
            }
            Box(Modifier.offset(x = 360.dp - shift)) {
                CardsGridView(
                    courses = emptyList(),
                    timeSlots = slots,
                    floatState = state,
                    currentWeek = 2,
                    onQuickAdd = { _, _, _ -> },
                    onOccurrenceMove = { c, d, n, s, resizeOnly, onResult ->
                        moves.add(Move(c.id, d, n, s, resizeOnly)); onResult(saved)
                    },
                    onOccurrenceDelete = { deleted.add(it.id) },
                    onOccurrenceEdit = null,
                    onEdgeDrag = { dir -> edgeCalls.add(dir) },
                    floatMaxWeek = 16,
                    onCourseClick = { clicks++ },
                    onCourseLongClick = { longPressed.add(it.id) },
                )
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
        // 2026-10-28 钳位契约: 水平出界一律吸最近合法列(见右缘提交例), 弹回只剩一种 —
        // 跨度越出末节 (endNode > maxNode)。首段位移照搬通过的右缘例(横向为主,
        // 确保浮卡手势被拖拽接管), 第二段再纵推越过末行: snapped=12, step=2 →
        // endNode 13 非法 → 弹回。(纯纵向起手会被网格 verticalScroll 劫走, 探针实证。)
        val state = CourseFloatState()
        grid(state)
        compose.onNodeWithText("FloatMath").performTouchInput { longClick() }
        val card = compose.onNodeWithTag("float-card").fetchSemanticsNode().boundsInRoot
        val target = compose.onNodeWithTag("add-course-7-4").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("float-card").performTouchInput {
            down(center)
            moveBy(Offset(target.center.x - card.center.x, target.top - card.top), 300)
            moveBy(Offset(0f, 900f), 200) // 卡顶越到末行(第12节)以下 → endNode 13
            up()
        }
        compose.runOnIdle {
            assertEquals(emptyList<Move>(), moves)
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

    // ---- 边缘驻留翻页 (判据 2026-10-30 通性修正: 外壳缘镜像 10dp · 每满 500ms 翻一页;
    // 旧「容器缘内缩 72dp」吞掉周日列, 落子/翻页手势域重叠, 见 EdgePeek.kt KDoc) ----

    /** 计时器以 dragging 为键 — 翻真后需一次重组重启, 新实例的首步轮询要额外放行;
     *  week bump 同样重启(宿主换页)。断言方向/驻留前先 flush 这段调度开销。 */
    private fun flushEdgePolling(steps: Int = 2) {
        compose.runOnIdle { }
        compose.mainClock.advanceTimeBy(FLOAT_EDGE_DWELL_STEP_MS * steps)
    }

    /** 压缘导航一律基于 state.x 实测值 (非卡 bounds — 磁吸渲染/插帧会吃掉部分位移,
     *  2026-10-30 诊断实测: 按 bounds 算的 delta 到达 floatState.x 时少 ≈15dp)。
     *  把卡中心精确开到 targetCx.dp 处: 轮询判据看的正是 state.x+width/2。 */
    private fun driveCxTo(state: CourseFloatState, targetCx: Int) {
        // 首程 move 会被拖拽 slop 吃掉一段 (实测 ≈16dp) → 收敛式两轮: 重读 state 真值补差。
        repeat(3) {
            var deltaPx = 0f
            compose.runOnIdle {
                val cxDp = (state.x + state.width / 2f).value
                deltaPx = with(compose.density) { (targetCx - cxDp).dp.toPx() }
            }
            if (kotlin.math.abs(deltaPx) <= 1f) return
            compose.onNodeWithTag("float-card").performTouchInput { moveBy(Offset(deltaPx, 0f), 20) }
        }
    }

    @Test fun dwellingOnTheRightEdgeFlipsToNextWeekAndPeeksFirst() {
        val state = CourseFloatState()
        grid(state, floatMaxWeek = 16)
        compose.onNodeWithText("FloatMath").performTouchInput { longClick() }
        // 磁吸渲染后 float-card 的 bounds = 吸附投影位 (≠ 手指位); 导航一律基于
        // 「lift 原点 + 累计位移」的手指真值 — 手指落在哪, 投影才吸到哪。
        val lift = compose.onNodeWithTag("float-card").fetchSemanticsNode().boundsInRoot
        val liftCx = (lift.left + lift.right) / 2f
        val edgeDx = with(compose.density) { 330.dp.toPx() } // 中心压过外壳右缘触发线(350dp 处)
        compose.onNodeWithTag("float-card").performTouchInput {
            down(center)
            moveBy(Offset(edgeDx, 40f), 100)
        }
        val fingerCx = liftCx + edgeDx
        flushEdgePolling()
        compose.runOnIdle {
            assertTrue(state.dragging)
            assertEquals(1, state.week)
            assertEquals(1, state.edgeDir)     // 方向已判出
            assertTrue(edgeCalls.isEmpty())    // 驻留未过半: 不许翻
        }
        compose.onNodeWithTag("float-peek-right").assertExists()
        compose.mainClock.advanceTimeBy(FLOAT_EDGE_DWELL_MS) // 越 500ms → 翻一周
        compose.runOnIdle {
            assertEquals(2, state.week)
            assertEquals(listOf(1), edgeCalls)
            // 换页重计: 旧页驻留不得结转(结转即满 1.0 → 首步就连翻) — 行为锁在
            // holdingTheEdgeFlips…(逐页满整驻留才再翻); 此处只钉 peek 未越界。
            assertTrue(state.peekProgress < 1f)
        }
        // 回到格内放手 → 落点在目标周(currentWeek 已换 2)照常提交。
        // (旧写法按 float-card bounds 导航 — 磁吸后那是渲染位而非手指位,
        //  位移被整体带偏一列, 2026-10-29 f8a4e336 回归 day=5 的根因。
        //  投影读手指真值: x 对齐目标列中心(down(center) 抓中心, 卡中心=手指x);
        //  y 按「卡顶跟手值」对齐目标顶 — 卡顶=lift.top+首移 40px。)
        val target = compose.onNodeWithTag("add-course-3-4").fetchSemanticsNode().boundsInRoot
        val cardTopFollow = lift.top + 40f
        compose.onNodeWithTag("float-card").performTouchInput {
            moveBy(Offset(target.center.x - fingerCx, target.top - cardTopFollow), 100)
        }
        compose.onNodeWithTag("float-card").performTouchInput { up() }
        compose.runOnIdle {
            assertEquals(listOf(Move(7L, 3, 4, 2, false)), moves)
            assertNull(state.course)
        }
    }

    /**
     * 2026-10-28 旧模型 (一次驻留翻一页后须回格重武装) 被 2026-10-30 用户裁决推翻:
     * 「能往后翻一周，不能往后翻两周。那我后面往后翻 100 周，你修 100 次是吗?」
     * 通性模型 = 压住即每满一个 500ms 翻一页, 方向不限、周数不限, 学期界自然挡停;
     * 停的手段是 500ms 节奏本身(收回格内即停表), 不是消耗锁。
     */
    @Test fun holdingTheEdgeFlipsOnePagePerFullDwellInBothDirections() {
        val state = CourseFloatState()
        grid(state, floatMaxWeek = 16)
        compose.onNodeWithText("FloatMath").performTouchInput { longClick() }
        compose.onNodeWithTag("float-card").performTouchInput {
            down(center)
            moveBy(Offset(330.dp.toPx(), 40f), 100)
        }
        flushEdgePolling()
        compose.mainClock.advanceTimeBy(FLOAT_EDGE_DWELL_MS)
        compose.runOnIdle { assertEquals(2, state.week) }
        // 不松手、不回格, 继续压住: 每个整驻留(含换宿主重启开销 ≈50ms)再翻一页。
        compose.mainClock.advanceTimeBy(FLOAT_EDGE_DWELL_MS + 100)
        compose.runOnIdle {
            assertEquals(3, state.week)
            assertEquals(listOf(1, 1), edgeCalls)
        }
        compose.mainClock.advanceTimeBy(FLOAT_EDGE_DWELL_MS + 100)
        compose.runOnIdle {
            assertEquals(4, state.week)
            assertEquals(listOf(1, 1, 1), edgeCalls)
        }
        // 第 5 页: 换页后本实例 el=450 (换宿主首步在翻页同窗内已计), 再满一个驻留即翻。
        compose.mainClock.advanceTimeBy(FLOAT_EDGE_DWELL_MS - 100)
        compose.runOnIdle {
            assertEquals(5, state.week)
            assertEquals(listOf(1, 1, 1, 1), edgeCalls)
        }
        // 收回格内 → 表停: dir=0 每步复位驻留, 压再久不再翻。旧模型「翻过即消耗」
        // 的停止语义由 500ms/页 节奏取代 (2026-10-30 用户裁决)。
        driveCxTo(state, 180)
        flushEdgePolling()
        compose.mainClock.advanceTimeBy(FLOAT_EDGE_DWELL_MS * 3)
        compose.runOnIdle {
            assertEquals(5, state.week)
            assertEquals(listOf(1, 1, 1, 1), edgeCalls)
        }
        compose.onNodeWithTag("float-card").performTouchInput { up() }
    }

    /**
     * 2026-10-30 用户裁决: 「往下翻页要这个页边距, 往上翻页也得在这个页边距」—
     * 触发线锚定**可视表格外壳缘**同一 10dp 容差, 左右镜像。旧判据锚列带缘:
     * 左线在首列缘(时间列整条被吞, ≈61dp 内缩), 右线却贴屏缘(15dp) — 同屏不同边距。
     * 本测试钉对称: 距屏缘 20dp 处左右都**不**翻 (旧模型左翻右不翻 = 红),
     * 压进 5dp 处左右都翻 (镜像)。
     */
    @Test fun triggerZonesAreMirrorSymmetricAtTheContainerEdge() {
        val state = CourseFloatState()
        hostWeek.value = 2 // 从第 2 周起: 左缘翻上一周不得被学期头挡停 (那是另一条契约)
        grid(state, floatMaxWeek = 16)
        compose.onNodeWithText("FloatMath").performTouchInput { longClick() }
        compose.onNodeWithTag("float-card").performTouchInput { down(center) }
        // 左: 中心停在容器左缘内 20dp。旧判据锚列带左缘 ≈61dp → 必翻 (红点);
        // 新判据锚外壳缘 10dp 容差 → 20dp 在区外, 不翻。
        driveCxTo(state, 20)
        flushEdgePolling()
        compose.mainClock.advanceTimeBy(FLOAT_EDGE_DWELL_MS * 2)
        compose.runOnIdle {
            assertEquals("距缘 20dp 不许翻页", 0, state.edgeDir)
            assertEquals(2, state.week)
            assertTrue(edgeCalls.isEmpty())
        }
        // 左: 压进 5dp (<10dp 触发线) → 满驻留翻上一周。
        driveCxTo(state, 5)
        flushEdgePolling()
        compose.mainClock.advanceTimeBy(FLOAT_EDGE_DWELL_MS)
        compose.runOnIdle {
            assertEquals(1, state.week)
            assertEquals(listOf(-1), edgeCalls)
        }
        // 右镜像: 压进右缘 (中心 > 360-10=350dp) → 同样满驻留翻回下一周。
        driveCxTo(state, 355)
        flushEdgePolling()
        compose.mainClock.advanceTimeBy(FLOAT_EDGE_DWELL_MS)
        compose.runOnIdle {
            assertEquals(2, state.week)
            assertEquals(listOf(-1, 1), edgeCalls)
        }
        compose.onNodeWithTag("float-card").performTouchInput { up() }
    }

    /** 2026-10-28 用户反馈: 压右缘松手曾整段静默弹回 — 水平出界不得吞掉拖拽,
     *  必须钳位吸附最近合法列并提交 (纵向出格才弹回)。 */
    @Test fun releasingWhilePinnedToTheRightEdgeCommitsTheLastVisibleColumn() {
        val state = CourseFloatState()
        grid(state)
        compose.onNodeWithText("FloatMath").performTouchInput { longClick() }
        val card = compose.onNodeWithTag("float-card").fetchSemanticsNode().boundsInRoot
        val target = compose.onNodeWithTag("add-course-7-4").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("float-card").performTouchInput {
            down(center)
            moveBy(Offset(target.center.x - card.center.x, target.top - card.top), 300)
            moveBy(Offset(120f, 0f), 100) // 越拖越出右缘 → 中心落在网格外 (dayIdx 7)
            up()
        }
        compose.runOnIdle {
            // 钳到末列 (day=7), 行向照常吸附 — 松手必须是一次真实提交, 不是静默弹回。
            assertEquals(listOf(Move(7L, 7, 4, 2, false)), moves)
            assertNull(state.course)
        }
    }

    /** 2026-10-29 用户反馈: 把课拖到周日列(落子意图)被缘区吞了 — 中心一进周日列
     *  就踩翻页雷, 驻停 500ms 非但落不成周日反而翻了周; 翻完仍贴缘, 横向微调全程
     *  在缘区内 edgeDir 恒 1 不放手 → 看着像「只能上下拖」。
     *  对齐 Lawnchair 语义(Workspace.verifyInsidePage: 中心悬在本页可见列 = 落子,
     *  永不翻页; 翻页只属 checkDragObjectIsOverNeighbourPages 的邻页命中):
     *  周日列中心驻停 2×500ms 不许翻, 松手落 day=7、week 不变。 */
    @Test fun dwellingOverTheSundayColumnDropsOnSundayInsteadOfFlipping() {
        val state = CourseFloatState()
        grid(state, floatMaxWeek = 16)
        compose.onNodeWithText("FloatMath").performTouchInput { longClick() }
        val card = compose.onNodeWithTag("float-card").fetchSemanticsNode().boundsInRoot
        val target = compose.onNodeWithTag("add-course-7-4").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("float-card").performTouchInput {
            down(center)
            moveBy(Offset(target.center.x - card.center.x, target.top - card.top), 300)
        }
        flushEdgePolling()
        compose.runOnIdle {
            assertTrue(state.dragging)
            assertEquals(0, state.edgeDir) // 页内合法列 = 落子, 不是翻页候选
        }
        compose.mainClock.advanceTimeBy(FLOAT_EDGE_DWELL_MS * 2) // 驻停两倍时长
        compose.runOnIdle {
            assertEquals(1, state.week)
            assertTrue(edgeCalls.isEmpty())
        }
        compose.onNodeWithTag("float-card").performTouchInput { up() }
        compose.runOnIdle {
            assertEquals(listOf(Move(7L, 7, 4, 2, false)), moves)
            assertNull(state.course)
        }
    }

    /** 场景C 用双页替身挂载 (不平移节点的单页 grid 是盲区, 见 FloatPager KDoc)。 */
    private fun pager(state: CourseFloatState) {
        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") { FloatPager(state) }
        }
    }

    /**
     * 场景C (2026-10-29 用户反馈「翻页之后只能上下拖」): 同一无松手按压流里 长按浮起 →
     * 压右缘驻留翻周 → 继续按住向左回拉。真 HorizontalPager 把起拖页(被捕获指针流的
     * 网格卡节点)物理左移一屏, continuousFloatLift 的逐帧 delta 是节点本地坐标 =
     * root 位移 − 节点平移 → 换页那一帧多送 +360dp 污染, 卡钉死右缘: edgeDir 恒 1、
     * edgeDir 恒 1、横向输入全丢(纵向不受水平平移影响 = 「只能上下拖」)。
     * 修复 = delta 换算到 root 坐标 (captured LayoutCoordinates.localToRoot)。
     * 修复前本测试必须红: 回拉 150dp 后 x 不减反增、edgeDir 不得归零。
     * 选择器口径: 浮起后 grid 与浮卡各有一个 FloatMath 文本节点 — 首块须在浮起前
     * 锁网格节点(此刻唯一), 后续块走 float-card: 它在换页后随宿主平移仍唯一稳定,
     * 注入的 MOVE 经已消费指针持续送达被捕获的起拖页节点 (与真机同构)。 */
    @Test fun dragAfterThePageFlipKeepsHorizontalControl() {
        val state = CourseFloatState()
        pager(state)
        val edgeDx = with(compose.density) { 330.dp.toPx() }
        compose.onNodeWithText("FloatMath").performTouchInput {
            down(center)
            moveTo(center, delayMillis = 900) // 长按超时 → 浮起
            moveBy(Offset(24f, 18f), 100)     // 死区内小抖, 不进拖拽
        }
        compose.runOnIdle { assertTrue("替身须能浮起", state.active) }
        compose.onNodeWithTag("float-card").performTouchInput {
            moveBy(Offset(edgeDx, 40f), 100) // 越死区进拖拽, 中心压过列带右缘触发线
        }
        flushEdgePolling()
        compose.runOnIdle {
            assertTrue(state.dragging)
            assertEquals("驻留未过半不许翻", 1, state.week)
            assertEquals(1, state.edgeDir)
        }
        compose.mainClock.advanceTimeBy(FLOAT_EDGE_DWELL_MS) // 越 500ms → 翻第 2 页(起拖页平移)
        compose.runOnIdle {
            assertEquals(2, state.week)
            assertEquals(listOf(1), edgeCalls)
        }
        var xAtFlip = 0.dp
        compose.runOnIdle { xAtFlip = state.x }
        // 翻页后手指向左回拉 150dp (root 坐标)。修复后卡随指左移、回到格内重新武装;
        // 修复前换页帧的 +360dp 本地坐标污染压过回拉, 卡钉死缘带、横向失控。
        compose.onNodeWithTag("float-card").performTouchInput {
            moveBy(Offset(-with(compose.density) { 150.dp.toPx() }, 0f), 100)
        }
        flushEdgePolling(steps = 4)
        compose.runOnIdle {
            assertTrue(
                "翻页后横向控制须恢复: 回拉 150dp 后 x 应显著小于翻页瞬间的 x (实际 $xAtFlip → ${state.x})",
                state.x < xAtFlip - 50.dp,
            )
            assertEquals("卡心回到格内 → 不再压缘", 0, state.edgeDir)
        }
        compose.onNodeWithTag("float-card").performTouchInput { up() }
    }

    @Test fun semesterBoundaryKeepsTheWeekAndBlocksTheFlip() {
        val state = CourseFloatState()
        grid(state, floatMaxWeek = 16) // 第 1 周左缘 = 学期头, 禁翻
        compose.onNodeWithText("FloatMath").performTouchInput { longClick() }
        compose.onNodeWithTag("float-card").performTouchInput { down(center) }
        // 卡中心原在 day1 列; 精确压到距外壳左缘 5dp 的触发区内 (判据 cx < 10dp)。
        driveCxTo(state, 5)
        flushEdgePolling()
        compose.runOnIdle {
            assertEquals(1, state.week)
            assertTrue(edgeCalls.isEmpty())
            assertEquals(-1, state.edgeBlocked)
            assertEquals(1f, state.peekProgress) // 边界 peek 静止示形
        }
        compose.mainClock.advanceTimeBy(FLOAT_EDGE_DWELL_MS * 2) // 驻留再久也不翻
        compose.runOnIdle {
            assertEquals(1, state.week)
            assertTrue(edgeCalls.isEmpty())
        }
        compose.onNodeWithTag("float-peek-left").assertExists()
        compose.onNodeWithTag("float-card").performTouchInput { up() }
    }

    @Test fun semesterTailBlocksTheForwardFlip() {
        val state = CourseFloatState()
        grid(state, currentWeek = 16, floatMaxWeek = 16)
        compose.onNodeWithText("FloatMath").performTouchInput { longClick() }
        compose.onNodeWithTag("float-card").performTouchInput {
            down(center)
            moveBy(Offset(330.dp.toPx(), 40f), 100)
        }
        flushEdgePolling()
        compose.runOnIdle {
            assertEquals(1, state.edgeBlocked)
            assertEquals(16, state.week)
            assertTrue(edgeCalls.isEmpty())
        }
        compose.mainClock.advanceTimeBy(FLOAT_EDGE_DWELL_MS * 2)
        compose.runOnIdle {
            assertEquals(16, state.week)
            assertTrue(edgeCalls.isEmpty())
        }
        compose.onNodeWithTag("float-card").performTouchInput { up() }
    }

    @Test fun directionChangeRestartsTheDwellForTheNewEdge() {
        val state = CourseFloatState()
        grid(state, floatMaxWeek = 16)
        compose.onNodeWithText("FloatMath").performTouchInput { longClick() }
        compose.onNodeWithTag("float-card").performTouchInput {
            down(center)
            moveBy(Offset(330.dp.toPx(), 40f), 100) // 右缘 → 驻留满 → 翻到第 2 周
        }
        flushEdgePolling()
        compose.mainClock.advanceTimeBy(FLOAT_EDGE_DWELL_MS)
        compose.runOnIdle {
            assertEquals(2, state.week)
            assertEquals(listOf(1), edgeCalls)
        }
        flushEdgePolling() // 换页重启开销
        compose.onNodeWithTag("float-card").performTouchInput { moveBy(Offset(-160.dp.toPx(), 0f), 100) }
        compose.mainClock.advanceTimeBy(FLOAT_EDGE_DWELL_STEP_MS * 2)
        compose.runOnIdle { assertEquals(0, state.edgeDir) } // 回到格内: 不驻留
        // 再压左缘: 换向即复位 — 上一方向的驻留时长不得计入新方向。
        // (新触发线锚外壳缘: 中心须 < 10dp 才算压左缘 — 72+330−160−245 = −3dp。)
        compose.onNodeWithTag("float-card").performTouchInput { moveBy(Offset(-245.dp.toPx(), 0f), 100) }
        compose.mainClock.advanceTimeBy(FLOAT_EDGE_DWELL_MS - 100)
        compose.runOnIdle {
            assertEquals(2, state.week)
            assertEquals(listOf(1), edgeCalls) // 未满 500ms → 不许翻
        }
        compose.mainClock.advanceTimeBy(200)
        compose.runOnIdle {
            assertEquals(1, state.week) // 满 → 翻回上一周
            assertEquals(listOf(1, -1), edgeCalls)
        }
        compose.onNodeWithTag("float-card").performTouchInput { up() }
    }
}
