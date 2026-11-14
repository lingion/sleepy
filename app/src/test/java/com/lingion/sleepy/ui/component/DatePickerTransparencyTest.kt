package com.lingion.sleepy.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import com.lingion.sleepy.ui.component.liquidglass.GlassHost
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale
import androidx.compose.material3.R as MaterialR

/** Render the app's actual picker over distinct carriers, without relying on glass shaders. */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w400dp-h800dp-mdpi")
class DatePickerTransparencyTest {
    @get:Rule val compose = createComposeRule()
    private val backdrop = mutableStateOf(BackdropColors.first())
    private var officialContainer = Color.Unspecified
    private var selectedDayContainer = Color.Unspecified
    private var selectedYearContainer = Color.Unspecified
    private lateinit var state: DatePickerState

    @Test fun glassExposesItsCarrierInCalendarInputAndYearSelection() {
        show(UiStyle.LiquidGlass, hostEnabled = true)
        checkAllModes(transparent = true)
    }

    @Test fun materialKeepsItsOfficialContainerInEveryMode() {
        show(UiStyle.Classic, hostEnabled = true)
        checkAllModes(transparent = false)
    }

    @Test fun glassWithoutAHostKeepsTheOfficialFallbackContainer() {
        show(UiStyle.LiquidGlass, hostEnabled = false)
        checkAllModes(transparent = false)
    }

    @Test fun glassYearGridReplacesDaysThroughoutItsTransitionAndPreservesTheSelectedDate() {
        show(UiStyle.LiquidGlass, hostEnabled = true, yearRange = 2024..2028)
        val selectedDay = dayLabel(JanuaryFirst2026)
        compose.onNodeWithText(selectedDay).assertExists()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("January 2026").performClick()

        // The former transparent Material overlay left the entire calendar
        // visible and clickable beneath the year list, including at rest.
        for (duration in listOf(16L, 48L, 120L, 400L)) {
            compose.mainClock.advanceTimeBy(duration)
            compose.onAllNodesWithText(selectedDay).assertCountEquals(0)
            compose.onAllNodesWithContentDescription("Thursday").assertCountEquals(0)
            compose.onNodeWithText(yearLabel(2026)).assertExists()
        }

        compose.onNodeWithText(yearLabel(2025)).performClick()
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("January 2025").assertExists()
        compose.onNodeWithText(dayLabel(dateMillis(2025, 1, 1))).assertExists()
        compose.onNodeWithContentDescription("Thursday").assertExists()
        compose.onAllNodesWithText(yearLabel(2025)).assertCountEquals(0)
        compose.runOnIdle {
            assertEquals(JanuaryFirst2026, state.selectedDateMillis)
            assertEquals(dateMillis(2025, 1, 1), state.displayedMonthMillis)
        }
    }

    @Test fun glassHonorsSelectableDaysYearsAndBothMonthBoundaries() {
        val blockedDate = dateMillis(2026, 1, 2)
        show(
            UiStyle.LiquidGlass, hostEnabled = true, yearRange = 2026..2027,
            selectableDates = object : SelectableDates {
                override fun isSelectableYear(year: Int) = year != 2027
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis != blockedDate
            },
        )
        compose.onNodeWithContentDescription("Change to previous month").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Change to next month").assertIsEnabled()
        compose.onNodeWithText(dayLabel(blockedDate)).assertIsNotEnabled()
        compose.onNodeWithText(dayLabel(dateMillis(2026, 1, 3))).assertIsEnabled().performClick()
        compose.onNodeWithText("January 2026").performClick()
        compose.onNodeWithText(yearLabel(2027)).assertIsNotEnabled()
        compose.onNodeWithText(yearLabel(2026)).assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(dateMillis(2026, 1, 3), state.selectedDateMillis)
            state.displayedMonthMillis = dateMillis(2027, 12, 1)
        }
        compose.onNodeWithContentDescription("Change to next month").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Change to previous month").assertIsEnabled()
        compose.onNodeWithText(dayLabel(dateMillis(2027, 12, 1))).assertIsNotEnabled()
    }

    @Test fun glassUsesTheStateLocaleAndMirrorsItsCalendarInRtl() {
        val locale = Locale.forLanguageTag("ar-EG")
        show(
            UiStyle.LiquidGlass, hostEnabled = true, yearRange = 2024..2028,
            locale = locale, direction = LayoutDirection.Rtl,
        )
        val first = compose.onNodeWithText(dayLabel(JanuaryFirst2026)).getUnclippedBoundsInRoot()
        val second = compose.onNodeWithText(dayLabel(dateMillis(2026, 1, 2))).getUnclippedBoundsInRoot()
        assertTrue("later weekdays must appear to the left in RTL", first.left > second.left)
        val saturday = DayOfWeek.SATURDAY.getDisplayName(TextStyle.FULL_STANDALONE, locale)
        val sunday = DayOfWeek.SUNDAY.getDisplayName(TextStyle.FULL_STANDALONE, locale)
        val firstWeekday = compose.onNodeWithContentDescription(saturday).getUnclippedBoundsInRoot()
        val secondWeekday = compose.onNodeWithContentDescription(sunday).getUnclippedBoundsInRoot()
        assertTrue("locale first day remains first in RTL", firstWeekday.left > secondWeekday.left)
        compose.onNodeWithText(dayLabel(dateMillis(2026, 1, 2))).performClick()
        val monthLabel = compose.runOnIdle {
            requireNotNull(DatePickerDefaults.dateFormatter().formatMonthYear(state.displayedMonthMillis, locale))
        }
        compose.onNodeWithText(monthLabel).performClick()
        compose.onNodeWithText(yearLabel(2025)).performClick()
        compose.runOnIdle {
            assertEquals(dateMillis(2026, 1, 2), state.selectedDateMillis)
            assertEquals(dateMillis(2025, 1, 1), state.displayedMonthMillis)
        }
    }

    private fun show(
        style: UiStyle,
        hostEnabled: Boolean,
        yearRange: IntRange = DatePickerDefaults.YearRange,
        selectableDates: SelectableDates = DatePickerDefaults.AllDates,
        locale: Locale? = null,
        direction: LayoutDirection = LayoutDirection.Ltr,
    ) {
        compose.setContent {
            MaterialExpressiveTheme {
                val officialColors = DatePickerDefaults.colors()
                officialContainer = officialColors.containerColor
                selectedDayContainer = officialColors.selectedDayContainerColor
                selectedYearContainer = officialColors.selectedYearContainerColor
                CompositionLocalProvider(LocalUiStyle provides style, LocalLayoutDirection provides direction) {
                    GlassHost(enabled = hostEnabled) {
                        Box(Modifier.background(backdrop.value).testTag(CarrierTag)) {
                            state = if (locale == null) {
                                rememberDatePickerState(
                                    initialSelectedDateMillis = JanuaryFirst2026,
                                    initialDisplayedMonthMillis = JanuaryFirst2026,
                                    yearRange = yearRange, selectableDates = selectableDates,
                                )
                            } else {
                                remember(locale) {
                                    DatePickerState(
                                        locale = locale,
                                        initialSelectedDateMillis = JanuaryFirst2026,
                                        initialDisplayedMonthMillis = JanuaryFirst2026,
                                        yearRange = yearRange, selectableDates = selectableDates,
                                    )
                                }
                            }
                            SleepyDatePicker(state)
                        }
                    }
                }
            }
        }
    }

    private fun dayLabel(millis: Long): String = compose.runOnIdle {
        requireNotNull(DatePickerDefaults.dateFormatter().formatDate(millis, state.locale, forContentDescription = true))
    }

    private fun yearLabel(year: Int): String = compose.runOnIdle {
        val localizedYear = NumberFormat.getIntegerInstance(state.locale).apply { isGroupingUsed = false }.format(year)
        RuntimeEnvironment.getApplication().getString(MaterialR.string.m3c_date_picker_navigate_to_year_description, localizedYear)
    }

    private fun dateMillis(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun checkAllModes(transparent: Boolean) {
        compose.waitForIdle()
        checkCarriers("calendar", transparent, selectedDayContainer)

        compose.onNodeWithContentDescription("Switch to text input mode").performClick()
        checkCarriers("input", transparent)

        compose.onNodeWithContentDescription("Switch to calendar input mode").performClick()
        compose.onNodeWithText("January 2026").performClick()
        checkCarriers("years", transparent, selectedYearContainer)
    }

    private fun checkCarriers(mode: String, transparent: Boolean, selection: Color? = null) {
        for (carrierColor in BackdropColors) {
            compose.runOnIdle { backdrop.value = carrierColor }
            val pixels = compose.onNodeWithTag(CarrierTag).captureToImage().toPixelMap()
            val expected = if (transparent) carrierColor else officialContainer
            assertEquals("$mode header over $carrierColor", expected, pixels[8, 8])
            // These margins are outside labels and selection pills. The lower
            // point also lies inside the year grid's own background, so clearing
            // only the outer DateEntryContainer would not satisfy this check.
            assertEquals("$mode body over $carrierColor", expected, pixels[16, pixels.height - 24])
            selection?.let {
                assertTrue("$mode must retain its official selected color", pixels.contains(it))
            }
        }
    }

    private fun PixelMap.contains(color: Color): Boolean {
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (this[x, y] == color) return true
            }
        }
        return false
    }

    private companion object {
        const val CarrierTag = "date-carrier"
        const val JanuaryFirst2026 = 1767225600000L
        val BackdropColors = listOf(Color(0xFFFF4F98), Color(0xFF19DBB6))
    }
}
