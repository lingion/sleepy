package com.lingion.sleepy.ui.component

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import com.lingion.sleepy.ui.component.liquidglass.GlassAlertDialog as AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DatePicker as MaterialDatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerColors
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.DisplayMode
import com.lingion.sleepy.ui.component.liquidglass.GlassDatePickerDialog as DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton as MaterialIconButton
import androidx.compose.material3.TextButton as MaterialTextButton
import androidx.compose.material3.Surface as MaterialSurface
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ProvideTextStyle
import com.lingion.sleepy.ui.component.liquidglass.GlassIconButton as IconButton
import androidx.compose.material3.TextField
import com.lingion.sleepy.ui.component.liquidglass.GlassButton as Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.console.ui.dbgTag
import com.lingion.sleepy.ui.component.liquidglass.LocalGlassHost
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.UiStyle
import com.lingion.sleepy.ui.theme.noRippleClickable
import java.time.Instant
import java.time.LocalTime
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.text.NumberFormat
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.drop
import androidx.compose.material3.R as MaterialR
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Preserve Material behavior while managing input focus and the glass carrier. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepyDatePicker(
    state: DatePickerState,
    modifier: Modifier = Modifier,
    showModeToggle: Boolean = true,
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val glass = LocalUiStyle.current == UiStyle.LiquidGlass && LocalGlassHost.current != null
    val pickerColors = DatePickerDefaults.colors()
    val pickerState = remember(state, focusManager, keyboard) {
        object : DatePickerState by state {
            override var displayMode: DisplayMode
                get() = state.displayMode
                set(value) {
                    if (state.displayMode != value && value == DisplayMode.Picker) {
                        // Release the outgoing text field before Material starts expanding
                        // the calendar, rather than when AnimatedContent finally disposes it.
                        focusManager.clearFocus(force = true)
                        keyboard?.hide()
                    }
                    state.displayMode = value
                }
        }
    }
    val displayMode = state.displayMode
    val previousDisplayMode = remember(state) { mutableStateOf(displayMode) }
    SideEffect {
        // A caller can change its original state without going through the proxy.
        // Clean up in that composition's apply phase, before its expanding frame
        // is drawn; a launched coroutine may run after that frame has started.
        if (previousDisplayMode.value != displayMode && displayMode == DisplayMode.Picker) {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
        previousDisplayMode.value = displayMode
    }
    if (glass) {
        GlassDateEntry(pickerState, modifier, showModeToggle, pickerColors.copy(containerColor = Color.Transparent))
        return
    }
    MaterialDatePicker(
        state = pickerState,
        modifier = modifier,
        colors = pickerColors,
        showModeToggle = showModeToggle,
        // Material's delayed auto-focus survives in the outgoing AnimatedContent.
        // Explicit input taps still focus normally; no late request may reopen IME
        // after the user has already returned to the calendar.
        focusRequester = null,
    )
}

/**
 * Material overlays its year grid on a live month and masks it with an opaque
 * container. A transparent carrier needs exclusive bodies instead. Keep the
 * public Material header, input implementation, palette and motion specifications.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlassDateEntry(state: DatePickerState, modifier: Modifier, showModeToggle: Boolean, colors: DatePickerColors) {
    val formatter = remember { DatePickerDefaults.dateFormatter() }
    val inputState = remember(state) {
        object : DatePickerState by state {
            // The outgoing input must stay an input while the outer transition
            // draws it. Sharing Picker here starts a second calendar animation.
            override var displayMode: DisplayMode
                get() = DisplayMode.Input
                set(value) { state.displayMode = value }
        }
    }
    val effectsIn = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val effectsOut = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
    val sizeSpec = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntSize>()
    val parallax = with(LocalDensity.current) { -48.dp.roundToPx() }
    Column(modifier.sizeIn(minWidth = 360.dp)) {
        Column(Modifier.fillMaxWidth().heightIn(min = 120.dp), verticalArrangement = Arrangement.SpaceBetween) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                DatePickerDefaults.DatePickerTitle(
                    state.displayMode, Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp), colors.titleContentColor,
                )
            }
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ProvideTextStyle(MaterialTheme.typography.headlineLarge) {
                        DatePickerDefaults.DatePickerHeadline(
                            state.selectedDateMillis, state.displayMode, formatter,
                            Modifier.weight(1f).padding(start = 24.dp, end = 12.dp, bottom = 12.dp), colors.headlineContentColor,
                        )
                    }
                    if (showModeToggle) {
                        val calendarMode = state.displayMode == DisplayMode.Picker
                        MaterialIconButton(
                            onClick = { state.displayMode = if (calendarMode) DisplayMode.Input else DisplayMode.Picker },
                            modifier = Modifier.padding(end = 12.dp, bottom = 12.dp),
                        ) {
                            Icon(
                                if (calendarMode) Icons.Filled.Edit else Icons.Filled.DateRange,
                                stringResource(if (calendarMode) MaterialR.string.m3c_date_picker_switch_to_input_mode else MaterialR.string.m3c_date_picker_switch_to_calendar_mode),
                                tint = colors.headlineContentColor,
                            )
                        }
                    }
                }
                HorizontalDivider(color = colors.dividerColor)
            }
        }
        AnimatedContent(
            targetState = state.displayMode,
            transitionSpec = {
                if (targetState == DisplayMode.Input) {
                    slideInVertically(spatial) { it } + fadeIn(effectsIn) togetherWith
                        (fadeOut(effectsOut) + slideOutVertically(spatial) { parallax })
                } else {
                    slideInVertically(spatial) { parallax } + fadeIn(effectsIn) togetherWith
                        (slideOutVertically(spatial) { it } + fadeOut(effectsOut))
                }.using(SizeTransform(clip = true, sizeAnimationSpec = { _, _ -> sizeSpec }))
            },
            label = "GlassDatePickerDisplayMode",
        ) { mode ->
            if (mode == DisplayMode.Input) {
                // With no header or toggle this is only Material's original input
                // body, including its date parsing, errors and keyboard behavior.
                MaterialDatePicker(inputState, colors = colors, title = null, headline = null, showModeToggle = false, focusRequester = null)
            } else {
                GlassDateCalendar(state, colors)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlassDateCalendar(state: DatePickerState, colors: DatePickerColors) {
    var yearsVisible by rememberSaveable { mutableStateOf(false) }
    val firstMonth = remember(state.yearRange) { YearMonth.of(state.yearRange.first, 1) }
    val currentMonth = Instant.ofEpochMilli(state.displayedMonthMillis).atZone(ZoneOffset.UTC).toLocalDate().let(YearMonth::from)
    val monthCount = (state.yearRange.last - state.yearRange.first + 1) * 12
    fun index(month: YearMonth) = ((month.year - firstMonth.year) * 12 + month.monthValue - 1).coerceIn(0, monthCount - 1)
    val pager = rememberPagerState(initialPage = index(currentMonth), pageCount = { monthCount })
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.displayedMonthMillis) {
        val desired = index(currentMonth)
        if (!pager.isScrollInProgress && pager.currentPage != desired) pager.scrollToPage(desired)
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.drop(1).collect { page ->
            val millis = firstMonth.plusMonths(page.toLong()).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            if (state.displayedMonthMillis != millis) state.displayedMonthMillis = millis
        }
    }
    val formatter = remember { DatePickerDefaults.dateFormatter() }
    val monthLabel = formatter.formatMonthYear(state.displayedMonthMillis, state.locale).orEmpty()
    Column {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            MaterialTextButton(onClick = { yearsVisible = !yearsVisible }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)) {
                Text(monthLabel, color = colors.navigationContentColor)
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Filled.ArrowDropDown,
                    stringResource(if (yearsVisible) MaterialR.string.m3c_date_picker_switch_to_day_selection else MaterialR.string.m3c_date_picker_switch_to_year_selection),
                    Modifier.graphicsLayer { rotationZ = if (yearsVisible) 180f else 0f }, tint = colors.navigationContentColor,
                )
            }
            Spacer(Modifier.weight(1f))
            if (!yearsVisible) {
                MaterialIconButton(onClick = { scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) } }, enabled = pager.currentPage > 0) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(MaterialR.string.m3c_date_picker_switch_to_previous_month), tint = colors.navigationContentColor)
                }
                MaterialIconButton(onClick = { scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(monthCount - 1)) } }, enabled = pager.currentPage < monthCount - 1) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(MaterialR.string.m3c_date_picker_switch_to_next_month), tint = colors.navigationContentColor)
                }
            }
        }
        // Only one body is composed, including while the year grid shrinks.
        // An opaque Material mask is unnecessary and dates cannot be hit behind it.
        val yearVisibility = remember { MutableTransitionState(false) }
        yearVisibility.targetState = yearsVisible
        val revealSpec = MaterialTheme.motionScheme.defaultEffectsSpec<androidx.compose.ui.unit.IntSize>()
        Box(Modifier.fillMaxWidth().height(336.dp)) {
            androidx.compose.animation.AnimatedVisibility(
                visibleState = yearVisibility,
                modifier = Modifier.clipToBounds(),
                enter = expandVertically(animationSpec = revealSpec) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec(), initialAlpha = .6f),
                exit = shrinkVertically(animationSpec = revealSpec) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            ) {
                Column {
                    GlassDateYears(state, colors, currentMonth) { year ->
                        val month = currentMonth.withYear(year)
                        scope.launch {
                            pager.scrollToPage(index(month))
                            state.displayedMonthMillis = month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                            yearsVisible = false
                        }
                    }
                    HorizontalDivider(color = colors.dividerColor)
                }
            }
            if (!yearVisibility.currentState && !yearVisibility.targetState) {
                HorizontalPager(state = pager, beyondViewportPageCount = 0, modifier = Modifier.fillMaxWidth()) { page ->
                    GlassDateMonth(state, colors, firstMonth.plusMonths(page.toLong()))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlassDateMonth(state: DatePickerState, colors: DatePickerColors, month: YearMonth) {
    val firstDay = WeekFields.of(state.locale).firstDayOfWeek
    val offset = (month.atDay(1).dayOfWeek.value - firstDay.value + 7) % 7
    val number = remember(state.locale) { NumberFormat.getIntegerInstance(state.locale).apply { isGroupingUsed = false } }
    val formatter = remember { DatePickerDefaults.dateFormatter() }
    val today = LocalDate.now()
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().height(48.dp)) {
            repeat(7) { index ->
                val day = firstDay.plus(index.toLong())
                Box(Modifier.weight(1f).height(48.dp), contentAlignment = Alignment.Center) {
                    Text(
                        day.getDisplayName(TextStyle.NARROW_STANDALONE, state.locale),
                        Modifier.clearAndSetSemantics { contentDescription = day.getDisplayName(TextStyle.FULL_STANDALONE, state.locale) },
                        color = colors.weekdayContentColor, style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
        repeat(6) { row ->
            Row(Modifier.fillMaxWidth().height(48.dp)) {
                repeat(7) { column ->
                    val dateNumber = row * 7 + column - offset + 1
                    Box(Modifier.weight(1f).height(48.dp), contentAlignment = Alignment.Center) {
                        if (dateNumber in 1..month.lengthOfMonth()) {
                            val date = month.atDay(dateNumber)
                            val millis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                            val selected = state.selectedDateMillis == millis
                            val enabled = state.selectableDates.isSelectableYear(month.year) && state.selectableDates.isSelectableDate(millis)
                            val description = formatter.formatDate(millis, state.locale, forContentDescription = true).orEmpty()
                            val container = when {
                                selected && enabled -> colors.selectedDayContainerColor
                                selected -> colors.disabledSelectedDayContainerColor
                                else -> Color.Transparent
                            }
                            val content = when {
                                selected && enabled -> colors.selectedDayContentColor
                                selected -> colors.disabledSelectedDayContentColor
                                !enabled -> colors.disabledDayContentColor
                                date == today -> colors.todayContentColor
                                else -> colors.dayContentColor
                            }
                            MaterialSurface(
                                selected = selected, onClick = { state.selectedDateMillis = millis }, enabled = enabled,
                                modifier = Modifier.size(40.dp).semantics(mergeDescendants = true) { text = AnnotatedString(description); role = Role.Button },
                                shape = CircleShape, color = container,
                                border = if (date == today && !selected) BorderStroke(1.dp, colors.todayDateBorderColor) else null,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(number.format(dateNumber), Modifier.clearAndSetSemantics {}, color = content, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlassDateYears(state: DatePickerState, colors: DatePickerColors, month: YearMonth, onYear: (Int) -> Unit) {
    val selectedYear = month.year
    val currentYear = LocalDate.now().year
    val numbers = remember(state.locale) { NumberFormat.getIntegerInstance(state.locale).apply { isGroupingUsed = false } }
    val grid = rememberLazyGridState(initialFirstVisibleItemIndex = (selectedYear - state.yearRange.first - 3).coerceAtLeast(0))
    val pane = stringResource(MaterialR.string.m3c_date_picker_year_picker_pane_title)
    LazyVerticalGrid(
        columns = GridCells.Fixed(3), state = grid,
        modifier = Modifier.fillMaxWidth().height(335.dp).padding(horizontal = 12.dp).semantics { paneTitle = pane },
        verticalArrangement = Arrangement.spacedBy(16.dp), horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        items(state.yearRange.toList(), key = { it }) { year ->
            val enabled = state.selectableDates.isSelectableYear(year)
            val selected = year == selectedYear
            val label = numbers.format(year)
            val description = stringResource(MaterialR.string.m3c_date_picker_navigate_to_year_description, label)
            val container = if (!selected) Color.Transparent else if (enabled) colors.selectedYearContainerColor else colors.disabledSelectedYearContainerColor
            val content = when {
                selected && enabled -> colors.selectedYearContentColor
                selected -> colors.disabledSelectedYearContentColor
                !enabled -> colors.disabledYearContentColor
                year == currentYear -> colors.currentYearContentColor
                else -> colors.yearContentColor
            }
            Box(Modifier.fillMaxWidth().height(36.dp), contentAlignment = Alignment.Center) {
                MaterialSurface(
                    selected = selected, onClick = { onYear(year) }, enabled = enabled,
                    modifier = Modifier.size(width = 72.dp, height = 36.dp).semantics(mergeDescendants = true) { text = AnnotatedString(description); role = Role.Button },
                    shape = CircleShape, color = container,
                    border = if (year == currentYear && !selected) BorderStroke(1.dp, colors.todayDateBorderColor) else null,
                ) {
                    Box(contentAlignment = Alignment.Center) { Text(label, Modifier.clearAndSetSemantics {}, color = content, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center) }
                }
            }
        }
    }
}

/**
 * 日期输入字段 — 手动输入 + 点击图标弹出原生日期选择器
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    shape: CornerBasedShape = SleepyTheme.fieldShape,
    isError: Boolean = false,
    // 调试台语义身份: 传 tag → 输入框=<tag>、日历按钮=<tag>_pick、
    // 弹窗 确定/取消=<tag>_pick_confirm/_dismiss; null = 不打标, 既有调用点零影响
    dbgTag: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val nativeDialogActions = LocalUiStyle.current == UiStyle.LiquidGlass && LocalGlassHost.current != null
    var showPicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    val fieldColors = SleepyTheme.fieldColors()

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.weight(1f)
                .then(dbgTag?.let { Modifier.dbgTag(it, text = label, role = "textbox") } ?: Modifier),
            shape = shape,
            isError = isError,
            colors = fieldColors
        )
        IconButton(
            onClick = { showPicker = true },
            modifier = Modifier
                .padding(start = 4.dp)
                .then(dbgTag?.let { Modifier.dbgTag("${it}_pick", role = "button") } ?: Modifier)
        ) {
            Icon(
                Icons.Outlined.CalendarMonth,
                contentDescription = stringResource(R.string.select_date),
                tint = colors.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }

    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                // Material Design 保留原色块；玻璃动作采用日期弹窗的统一默认样式。
                Button(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val date = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.of("Asia/Shanghai"))
                                .toLocalDate()
                            onValueChange(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
                        }
                        showPicker = false
                    },
                    modifier = dbgTag?.let { Modifier.dbgTag("${it}_pick_confirm", role = "button") } ?: Modifier,
                    shape = if (nativeDialogActions) null else SleepyTheme.shapes.medium
                ) { Text(stringResource(R.string.ok), maxLines = if (nativeDialogActions) Int.MAX_VALUE else 1) }
            },
            dismissButton = {
                Button(
                    onClick = { showPicker = false },
                    modifier = dbgTag?.let { Modifier.dbgTag("${it}_pick_dismiss", role = "button") } ?: Modifier,
                    shape = if (nativeDialogActions) null else SleepyTheme.shapes.medium,
                    colors = if (nativeDialogActions) null else ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) { Text(stringResource(R.string.cancel), maxLines = if (nativeDialogActions) Int.MAX_VALUE else 1) }
            }
        ) {
            SleepyDatePicker(state = datePickerState)
        }
    }
}

/**
 * 时间输入字段 — 点击输入框直接弹时间选择器，无 clock 图标。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    shape: CornerBasedShape = SleepyTheme.fieldShape,
    // 调试台语义身份: 传 tag → 字段=<tag>、弹窗 确定/取消=<tag>_pick_confirm/_dismiss
    dbgTag: String? = null,
    compact: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    var showPicker by remember { mutableStateOf(false) }
    val timePickerState = rememberTimePickerState(
        initialHour = value.substringBefore(":").toIntOrNull() ?: 8,
        initialMinute = value.substringAfter(":").toIntOrNull() ?: 0,
        is24Hour = true
    )
    val fieldColors = SleepyTheme.fieldColors()

    // Box + clickable 包装：OutlinedTextField 内部设 enabled=false
    // 让 click 事件穿透到外层 Box 的 clickable。
    // Box 必须先 clip(shape) 再 clickable — 否则涟漪是方角、且能溢出字段圆角。
    val textMeasurer = rememberTextMeasurer()
    BoxWithConstraints(
        modifier = modifier
            .clip(shape)
            .noRippleClickable { showPicker = true }
            .then(dbgTag?.let { Modifier.dbgTag(it, text = label, role = "textbox") } ?: Modifier)
    ) {
        if (compact) {
            val horizontalPadding = if (maxWidth < 68.dp) 2.dp else 4.dp
            val regularTime = MaterialTheme.typography.bodyMedium
            val regularTimeWidth = with(LocalDensity.current) {
                textMeasurer.measure(value, style = regularTime, softWrap = false, maxLines = 1).size.width.toDp()
            }
            val timeStyle = if (regularTimeWidth + horizontalPadding * 2 > maxWidth) MaterialTheme.typography.bodySmall else regularTime
            Column(
                Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .background(colors.surfaceContainerHighest, shape)
                    .padding(horizontal = horizontalPadding, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1)
                Text(value, style = timeStyle, color = colors.onSurface, maxLines = 1)
            }
        } else TextField(
            value = value,
            onValueChange = {},
            label = { Text(label) },
            enabled = false,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            colors = fieldColors
        )
    }

    if (showPicker) {
        val nativeActions = LocalUiStyle.current == UiStyle.LiquidGlass && LocalGlassHost.current != null
        val actions: @Composable () -> Unit = {
            DialogActionButtons(
                confirmText = stringResource(R.string.ok),
                onConfirm = {
                    val h = timePickerState.hour.toString().padStart(2, '0')
                    val m = timePickerState.minute.toString().padStart(2, '0')
                    onValueChange("$h:$m")
                    showPicker = false
                },
                dismissText = stringResource(R.string.cancel),
                onDismiss = { showPicker = false },
                dbgTagPrefix = dbgTag?.let { "${it}_pick" }
            )
        }
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(stringResource(R.string.select_time), color = colors.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 默认 TimePicker 配色 — 与 ReminderScreen 时间弹窗一致, 不再单独覆写表盘色
                    TimePicker(state = timePickerState)
                    if (!nativeActions) {
                        Spacer(modifier = Modifier.height(12.dp))
                        actions()
                    }
                }
            },
            confirmButton = { if (nativeActions) actions() },
            dismissButton = {}
        )
    }
}
