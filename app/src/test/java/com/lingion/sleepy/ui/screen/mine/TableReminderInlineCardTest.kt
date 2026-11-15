package com.lingion.sleepy.ui.screen.mine

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.lingion.sleepy.R
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.ui.component.liquidglass.GlassHost
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rCN-w360dp-h640dp-mdpi")
class TableReminderInlineCardTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val title get() = context.getString(R.string.settings_table_reminder_scope)
    private val beforeTitle get() = context.getString(R.string.reminder_master_title)
    private val afterTitle get() = context.getString(R.string.reminder_before_class_title)
    private val off = TimeTableEntity(id = 7L, name = "本学期课表", startDate = "2026-09-01", reminderEnabled = false)
    private val on = off.copy(reminderEnabled = true)
    private val other = off.copy(id = 9L, name = "备用课表")
    private val source = MutableStateFlow(listOf(off))
    private val saved = CompletableDeferred<Unit>()
    private val writes = mutableListOf<Pair<Long, Boolean>>()
    private val vm = TableReminderViewModel(source) { id, enabled ->
        writes += id to enabled
        saved.await()
    }
    private var masterEnabled by mutableStateOf(true)

    private fun show(model: TableReminderViewModel = vm, style: UiStyle = UiStyle.Classic) {
        compose.setContent {
            MaterialExpressiveTheme {
                CompositionLocalProvider(LocalUiStyle provides style) {
                    GlassHost(enabled = style == UiStyle.LiquidGlass) {
                        Column(
                            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
                                .padding(16.dp).testTag("reminder-inline-preview"),
                        ) {
                            Text(beforeTitle)
                            TableReminderCard(masterEnabled = masterEnabled, vm = model)
                            Text(afterTitle)
                        }
                    }
                }
            }
        }
    }

    private fun open() { compose.onNodeWithText(title).performClick() }

    @Test fun materialCardStartsCollapsedAndExpandsInPlace() = verifyExpansion(UiStyle.Classic)

    @Test fun glassCardStartsCollapsedAndExpandsInPlace() = verifyExpansion(UiStyle.LiquidGlass)

    private fun verifyExpansion(style: UiStyle) {
        show(style = style)
        compose.onNodeWithText(off.name).assertDoesNotExist()
        compose.onNodeWithText(title).assert(SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription, context.getString(R.string.accordion_collapsed),
        ))
        capture(style, "collapsed")
        val top = compose.onNodeWithText(title).fetchSemanticsNode().boundsInRoot.top
        open()
        compose.onNodeWithText(off.name).assertIsDisplayed().assertIsOff().assertIsEnabled()
        compose.onNodeWithText(title).assert(SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription, context.getString(R.string.accordion_expanded),
        ))
        assertEquals(top, compose.onNodeWithText(title).fetchSemanticsNode().boundsInRoot.top, 0.5f)
        val before = compose.onNodeWithText(beforeTitle).fetchSemanticsNode().boundsInRoot
        val header = compose.onNodeWithText(title).fetchSemanticsNode().boundsInRoot
        val row = compose.onNodeWithText(off.name).fetchSemanticsNode().boundsInRoot
        val after = compose.onNodeWithText(afterTitle).fetchSemanticsNode().boundsInRoot
        assertTrue(before.bottom <= header.top && header.bottom <= row.top && row.bottom <= after.top)
        capture(style, "expanded")
        open()
        compose.onNodeWithText(off.name).assertDoesNotExist()
        compose.onNodeWithText(afterTitle).assertIsDisplayed()
        assertTrue(writes.isEmpty())
    }

    private fun capture(style: UiStyle, state: String) {
        val directory = File(System.getenv("SLEEPY_REMINDER_CAPTURE_DIR") ?: "build/test-snapshots/reminder-inline")
        check(directory.isDirectory || directory.mkdirs())
        val bitmap = compose.onNodeWithTag("reminder-inline-preview").captureToImage().asAndroidBitmap()
        File(directory, "reminder-inline-${style.name.lowercase()}-$state.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    @Test fun rowWritesOnAndOffWithoutCollapsingTheSection() {
        val model = TableReminderViewModel(source) { id, enabled ->
            writes += id to enabled
            source.value = source.value.map { if (it.id == id) it.copy(reminderEnabled = enabled) else it }
        }
        show(model)
        open()
        compose.onNodeWithText(off.name).performClick().assertIsOn().assertIsEnabled()
        compose.onNodeWithText(off.name).performClick().assertIsOff().assertIsEnabled()
        compose.onNodeWithText(title).assert(SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription, context.getString(R.string.accordion_expanded),
        ))
        compose.onNodeWithText(beforeTitle).assertIsDisplayed()
        compose.onNodeWithText(afterTitle).assertIsDisplayed()
        assertEquals(listOf(off.id to true, off.id to false), writes)
    }

    @Test fun masterOffHintTracksTheParentToggleAndKeepsEmptyState() {
        source.value = emptyList()
        show()
        open()
        compose.onNodeWithText(context.getString(R.string.table_reminder_subtitle)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.table_reminder_empty)).assertIsDisplayed()
        compose.runOnIdle { masterEnabled = false }
        compose.onNodeWithText(context.getString(R.string.table_reminder_master_off)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.table_reminder_empty)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.table_reminder_subtitle)).assertDoesNotExist()
    }

    @Test fun loadingDoesNotPretendToBeAnEmptyTableList() {
        val loading = MutableSharedFlow<List<TimeTableEntity>>(replay = 1)
        val model = TableReminderViewModel(loading) { _, _ -> }
        show(model)
        open()
        compose.onNodeWithText(context.getString(R.string.table_reminder_empty)).assertDoesNotExist()
        compose.runOnIdle { assertTrue(loading.tryEmit(emptyList())) }
        compose.onNodeWithText(context.getString(R.string.table_reminder_empty)).assertIsDisplayed()
    }

    @Test fun echoBeforeWriterReturnsOnlyUnlocksAfterTheWritePhaseChanges() {
        show()
        open()
        compose.onNodeWithText(off.name).performClick().assertIsNotEnabled()
        compose.runOnIdle { source.value = listOf(on) }
        compose.onNodeWithText(off.name).assertIsOn().assertIsNotEnabled()
        compose.runOnIdle { saved.complete(Unit) }
        compose.onNodeWithText(off.name).assertIsOn().assertIsEnabled()
        compose.runOnIdle { assertTrue(vm.pending.isEmpty()) }
        assertEquals(listOf(off.id to true), writes)
    }

    @Test fun writerReturningBeforeEchoKeepsOnlyThatTablePendingAcrossCollapse() {
        source.value = listOf(off, other)
        masterEnabled = false
        show()
        open()
        compose.onNodeWithText(context.getString(R.string.table_reminder_master_off)).assertIsDisplayed()
        compose.onNodeWithText(off.name).performClick().assertIsNotEnabled()
        compose.onNodeWithText(other.name).assertIsEnabled()
        compose.runOnIdle { saved.complete(Unit) }
        repeat(3) {
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithText(off.name).assertIsOff().assertIsNotEnabled()
        }
        // A stale UI event is also blocked by the shared ViewModel.
        compose.runOnIdle { vm.setEnabled(off.id, false) { error("Unexpected failure") } }
        open()
        compose.onNodeWithText(off.name).assertDoesNotExist()
        compose.runOnIdle { source.value = listOf(other, on) }
        open()
        compose.onNodeWithText(off.name).assertIsOn().assertIsEnabled()
        compose.onNodeWithText(other.name).assertIsOff().assertIsEnabled()
        compose.runOnIdle { assertTrue(vm.pending.isEmpty()) }
        assertEquals(listOf(off.id to true), writes)
    }

    @Test fun failedSaveKeepsDatabaseValueShowsFeedbackAndAllowsRetry() {
        var attempts = 0
        val model = TableReminderViewModel(source) { _, _ ->
            attempts++
            error("Save rejected")
        }
        show(model)
        open()
        repeat(2) {
            compose.onNodeWithText(off.name).performClick()
            compose.onNodeWithText(off.name).assertIsOff().assertIsEnabled()
        }
        compose.runOnIdle {
            assertEquals(2, attempts)
            assertTrue(model.pending.isEmpty())
            assertEquals(context.getString(R.string.table_reminder_save_failed), ShadowToast.getTextOfLatestToast())
        }
    }
}
