package com.lingion.sleepy.ui.screen.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import com.lingion.sleepy.ui.theme.noRippleClickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.ui.component.PERIOD_HEADER_CARD_PAD_DP
import com.lingion.sleepy.ui.component.PeriodHeaderAdaptiveFont
import com.lingion.sleepy.ui.component.PeriodHeaderCellContent
import com.lingion.sleepy.ui.component.SegmentedSwitcher
import com.lingion.sleepy.ui.component.TimeSlot
import com.lingion.sleepy.ui.component.threeLineWidthDp
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.util.AppPrefs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodHeaderSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var layout by remember { mutableStateOf(AppPrefs.getPeriodHeaderLayout(context)) }
    var style by remember { mutableStateOf(AppPrefs.getPeriodHeaderStyle(context)) }
    var hangingUnits by remember { mutableStateOf(AppPrefs.getPeriodHeaderHanging(context)) }
    var showX by remember { mutableStateOf(AppPrefs.isPeriodHeaderShowX(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.appearance_period_header)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                HeaderStylePreviews(
                    layout = layout,
                    selectedStyle = style,
                    hangingUnits = hangingUnits,
                    showX = showX,
                    onStyleSelected = {
                        style = it
                        AppPrefs.setPeriodHeaderStyle(context, it)
                    },
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.appearance_header_layout),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SegmentedSwitcher(
                        options = listOf(
                            "legacy" to stringResource(R.string.appearance_layout_legacy),
                            "three_line" to stringResource(R.string.appearance_layout_three_line),
                        ).mapIndexed { index, (_, label) -> index to label },
                        selected = if (layout == "three_line") 1 else 0,
                        onSelect = { index ->
                            layout = if (index == 1) "three_line" else "legacy"
                            AppPrefs.setPeriodHeaderLayout(context, layout)
                        },
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            stringResource(R.string.appearance_header_show_x),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Switch(
                            checked = showX,
                            onCheckedChange = {
                                showX = it
                                AppPrefs.setPeriodHeaderShowX(context, it)
                            },
                        )
                    }
                    if (layout == "three_line") {
                        Text(
                            stringResource(R.string.appearance_header_hanging, String.format("%+.1f", hangingUnits)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Slider(
                            value = hangingUnits,
                            onValueChange = { hangingUnits = (it * 10f).roundToInt() / 10f },
                            onValueChangeFinished = { AppPrefs.setPeriodHeaderHanging(context, hangingUnits) },
                            valueRange = -1f..1f,
                            steps = 19,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderStylePreviews(
    layout: String,
    selectedStyle: String,
    hangingUnits: Float,
    showX: Boolean,
    onStyleSelected: (String) -> Unit,
) {
    val previewSlot = remember {
        TimeSlot(
            label = "12", start = java.time.LocalTime.of(8, 0), end = java.time.LocalTime.of(8, 45),
            displayStart = "08:00", displayEnd = "08:45", nodeStart = 12, nodeEnd = 12,
        )
    }
    val styles = listOf(
        "arabic" to "1  2  3",
        "chinese" to "一  二  三",
        "financial" to "壹  贰  叁",
        "circled" to "①  ②  ③",
        "roman" to "Ⅰ  Ⅱ  Ⅲ",
    )
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    // 每种样式按自己的文字包络定宽; 最长样式有最长卡片, 禁截断.
    val styleWidths = styles.map { style ->
        threeLineWidthDp(
            slots = listOf(previewSlot),
            headerStyle = style.first,
            scale = 1f,
            measurer = measurer,
            density = density,
            hangingUnits = hangingUnits,
            showX = showX,
        )
    }
    // 字号统一 = 同一算法、同一输入 (52dp 卡高 → 高度驱动, 可读区间钳制):
    // 预览与网格共用 forPreview/forColumn 的同一 compute 核心, 圈圈/罗马/汉字全同字号。
    // 谁的内容更长谁的卡片变宽 (contentWidths 已按样式实测), 禁止缩字号迁就。
    val previewFont = if (layout == "three_line") {
        PeriodHeaderAdaptiveFont.forPreview(
            cardWidthSp = with(density) { styleWidths.max().toPx() } / density.density,
            cardHeightSp = 52f,
        )
    } else null
    val contentWidths = styleWidths.map { if (layout == "three_line") it.coerceAtLeast(46.dp) else 68.dp }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.appearance_header_preview), style = MaterialTheme.typography.titleSmall)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            styles.forEachIndexed { styleIndex, (value, label) ->
                Surface(
                    modifier = Modifier
                        .widthIn(min = 76.dp)
                        .clip(SleepyTheme.shapes.medium)
                        .noRippleClickable { onStyleSelected(value) },
                    color = if (value == selectedStyle) colors.primaryContainer else colors.surfaceContainer,
                    shape = SleepyTheme.shapes.medium,
                ) {
                    Column(
                        modifier = Modifier.padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Surface(
                            modifier = Modifier.width(contentWidths[styleIndex]).height(52.dp),
                            shape = SleepyTheme.shapes.small,
                            color = colors.surfaceContainerLow,
                        ) {
                            // 与 SingleTimeHeadCell 的 3dp 内边距对齐 — 三行模式的
                            // padding 只由卡片层负责, 两条链必须逐层相等。
                            // 与 SingleTimeHeadCell 的内边距对齐 — 三行模式的
                            // padding 只由卡片层负责, 两条链必须逐层相等 (2026-09-28 令)。
                            Box(modifier = Modifier.padding(PERIOD_HEADER_CARD_PAD_DP.dp)) {
                            PeriodHeaderCellContent(
                                slot = previewSlot,
                                layout = layout,
                                style = value,
                                scale = 1f,
                                hangingUnitsOverride = hangingUnits,
                                showXOverride = showX,
                                sharedFont = previewFont,
                            )
                            }
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (value == selectedStyle) colors.onPrimaryContainer else colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
