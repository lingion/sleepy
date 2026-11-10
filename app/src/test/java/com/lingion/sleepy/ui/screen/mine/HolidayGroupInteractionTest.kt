package com.lingion.sleepy.ui.screen.mine

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.lingion.sleepy.R
import com.lingion.sleepy.ui.component.liquidglass.GlassHost
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import com.lingion.sleepy.util.DateUtils
import com.lingion.sleepy.util.HolidayManager
import com.lingion.sleepy.util.HolidayRange
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-w360dp-h800dp-mdpi", application = Application::class)
class HolidayGroupInteractionTest {
    @get:Rule val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val dayNames get() = context.resources.getStringArray(R.array.day_names)
    private fun range(id: String, name: String, day: Int) = HolidayRange(
        id, name, LocalDate.of(2026, 5, day), LocalDate.of(2026, 5, day + 1),
        HolidayManager.TYPE_PUBLIC_HOLIDAY, null,
    )

    @Test fun officialAndCustomMappingGroupsHideRowsUntilIndividuallyExpanded() {
        val official = range("official", "Official group", 1)
        val custom = range("custom", "School group", 10)
        val officialExpanded = mutableStateOf(false)
        val customExpanded = mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                Column {
                    HolidayTransferCard(official, emptyList(), emptyList(), dayNames,
                        officialExpanded.value, { officialExpanded.value = !officialExpanded.value }, { _, _ -> })
                    HolidayTransferCard(custom, emptyList(), emptyList(), dayNames,
                        customExpanded.value, { customExpanded.value = !customExpanded.value }, { _, _ -> })
                }
            }
        }
        fun dateLabel(date: LocalDate) = "${DateUtils.shortDateSlash(date)} (${dayNames[date.dayOfWeek.value - 1]})"
        val officialDate = dateLabel(official.startDate)
        val customDate = dateLabel(custom.startDate)
        compose.onNodeWithText(officialDate).assertDoesNotExist()
        compose.onNodeWithText(customDate).assertDoesNotExist()
        compose.onNodeWithText(official.name).performClick()
        compose.onNodeWithText(officialDate).assertIsDisplayed()
        compose.onNodeWithText(customDate).assertDoesNotExist()
        compose.onNodeWithText(official.name).performClick()
        compose.onNodeWithText(officialDate).assertDoesNotExist()
        compose.onNodeWithText(custom.name).performClick()
        compose.onNodeWithText(customDate).assertIsDisplayed()
    }

    // Robolectric #8460: Dialog + TextField loops during layout when the fixture
    // overrides the default 320dp width. The same interaction runs at 320dp;
    // this keeps the real glass host and dialog rather than bypassing either.
    @Config(qualifiers = "en-w320dp-h800dp-mdpi")
    @Test fun glassModeRangeEditorKeepsMaterialFieldsAndValidatesBeforeSaving() {
        val target = range("custom", "School group", 10)
        var saved: HolidayRange? = null
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass) {
                    GlassHost {
                        HolidayRangeEditDialog(target, isNew = false, onDismiss = {},
                            onSave = { saved = it }, onDelete = {})
                    }
                }
            }
        }
        val startLabel = context.getString(R.string.holiday_name_label_date)
        val endLabel = context.getString(R.string.holiday_name_label_end)
        val saveLabel = context.getString(R.string.save)
        compose.onNode(hasSetTextAction() and hasText(startLabel)).performTextReplacement("2026-05-20")
        compose.onNodeWithText(saveLabel).assertIsNotEnabled()
        compose.onNode(hasSetTextAction() and hasText(endLabel)).performScrollTo().performTextReplacement("2026-05-22")
        compose.onNodeWithText(context.getString(R.string.holiday_type_workday)).performScrollTo().performClick()
        compose.onNodeWithText(saveLabel).performClick()
        compose.runOnIdle {
            assertEquals(target.copy(startDate = LocalDate.of(2026, 5, 20),
                endDate = LocalDate.of(2026, 5, 22), type = HolidayManager.TYPE_TRANSFER_WORKDAY), saved)
        }
    }
}
