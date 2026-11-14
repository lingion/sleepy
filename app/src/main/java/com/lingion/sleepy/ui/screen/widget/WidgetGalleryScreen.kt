package com.lingion.sleepy.ui.screen.widget

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import com.lingion.sleepy.ui.component.liquidglass.GlassIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import com.lingion.sleepy.ui.component.liquidglass.GlassTopAppBar as TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.ui.component.AccordionHeader
import com.lingion.sleepy.ui.component.AccordionChevron
import com.lingion.sleepy.ui.component.accordionToggle
import com.lingion.sleepy.ui.component.accordionSpatialSpec
import com.lingion.sleepy.ui.component.accordionEffectsSpec
import com.lingion.sleepy.ui.theme.noRippleClickable
import com.lingion.sleepy.widget.PinResult
import com.lingion.sleepy.widget.WidgetGalleryData
import com.lingion.sleepy.widget.WidgetGalleryGroup
import com.lingion.sleepy.widget.WidgetPinRequest
import com.lingion.sleepy.widget.WidgetVariantInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 画廊每排卡片数: 行高基准(该排大卡)按此分排, 与 2 列同源。 */
private const val GALLERY_COLUMNS = 2

/**
 * 从完整标签提取基础名称，去除尺寸后缀（如"课程表 · 大" → "课程表"）
 */
private fun extractBaseName(fullName: String): String {
    return fullName.split(" · ").firstOrNull() ?: fullName
}

/** 画廊行项建模: 折叠状态以 2 列行排布, 展开项独占整行。 */
private sealed interface GalleryRowItem {
    val key: String

    data class CollapsedRow(
        val first: WidgetGalleryGroup,
        val second: WidgetGalleryGroup? = null,
        val rowKey: String,
    ) : GalleryRowItem {
        override val key: String get() = rowKey
    }

    data class ExpandedRow(
        val group: WidgetGalleryGroup,
    ) : GalleryRowItem {
        override val key: String get() = "expanded_${group.key}"
    }
}

private fun buildGalleryRowItems(
    groups: List<WidgetGalleryGroup>,
    expandedGroupKey: String?
): List<GalleryRowItem> {
    val expandedGroup = if (expandedGroupKey != null) groups.firstOrNull { it.key == expandedGroupKey } else null
    if (expandedGroup == null) {
        return groups.chunked(GALLERY_COLUMNS).map { chunk ->
            GalleryRowItem.CollapsedRow(
                first = chunk[0],
                second = chunk.getOrNull(1),
                rowKey = "collapsed_row_${chunk[0].key}_${chunk.getOrNull(1)?.key ?: "none"}"
            )
        }
    }
    val before = groups.takeWhile { it.key != expandedGroupKey }
    val after = groups.dropWhile { it.key != expandedGroupKey }.drop(1)
    return buildList {
        before.chunked(GALLERY_COLUMNS).forEach { chunk ->
            add(
                GalleryRowItem.CollapsedRow(
                    first = chunk[0],
                    second = chunk.getOrNull(1),
                    rowKey = "collapsed_row_${chunk[0].key}_${chunk.getOrNull(1)?.key ?: "none"}"
                )
            )
        }
        add(GalleryRowItem.ExpandedRow(expandedGroup))
        after.chunked(GALLERY_COLUMNS).forEach { chunk ->
            add(
                GalleryRowItem.CollapsedRow(
                    first = chunk[0],
                    second = chunk.getOrNull(1),
                    rowKey = "collapsed_row_${chunk[0].key}_${chunk.getOrNull(1)?.key ?: "none"}"
                )
            )
        }
    }
}

/**
 * 添加小组件画廊页 (PR 295 / 设计 §3 末层):
 *
 * 1. 默认展示 5 个主组件卡片 (最大尺寸 4×4 档位)。
 * 2. 多尺寸组件标题追加 "(多尺寸)", 单尺寸只显示原名。
 * 3. 手风琴折叠展开: 点击已折叠卡片展开露出全部尺寸变体 (主变体 + 展开的次级变体),
 *    其它卡片让出空间下移; 展开一个自动收起前一个; 点击已展开卡片收起回主卡。
 * 4. 点击任意尺寸变体卡片均走唯一通路 [WidgetPinRequest.request] 发起系统添加确认。
 */
@Composable
fun WidgetGalleryScreen(onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val unsupportedHint = stringResource(R.string.widget_add_unsupported_hint)
    // 每张卡 pin 后的冷却(防连点): receiverClass 名 → 冷却中
    val cooling = remember { mutableStateOf(setOf<String>()) }
    val groups = remember { WidgetGalleryData.buildGroups() }
    var expandedGroupKey by rememberSaveable { mutableStateOf<String?>(null) }

    fun pin(variant: WidgetVariantInfo) {
        val receiverName = variant.receiverClass.name
        cooling.value = cooling.value + receiverName
        scope.launch {
            when (WidgetPinRequest.request(context, variant)) {
                PinResult.NOT_SUPPORTED, PinResult.FAILED ->
                    snackbar.showSnackbar(unsupportedHint)
                PinResult.REQUESTED -> { /* 系统确认弹窗接管,无副作用 */ }
            }
        }
        scope.launch {
            delay(2000)
            cooling.value = cooling.value - receiverName
        }
    }

    // 5 个主组件卡片在折叠状态下的行框宽高比: 与 2 列同源
    val primaryVariants = remember(groups) { groups.map { it.primaryVariant } }
    val primaryRowAspectRatios = remember(primaryVariants) {
        WidgetGalleryData.rowBoxAspectRatios(primaryVariants, GALLERY_COLUMNS)
    }

    val rowItems = remember(groups, expandedGroupKey) {
        buildGalleryRowItems(groups, expandedGroupKey)
    }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.widget_gallery_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.onBackground,
                    navigationIconContentColor = colors.onBackground
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = rowItems,
                key = { it.key }
            ) { item ->
                val animatedItem = Modifier.animateItem(
                    fadeInSpec = accordionEffectsSpec(),
                    placementSpec = accordionSpatialSpec(),
                    fadeOutSpec = accordionEffectsSpec(),
                )
                when (item) {
                    is GalleryRowItem.ExpandedRow -> {
                        ExpandedGroupItem(
                            group = item.group,
                            modifier = animatedItem,
                            cooling = cooling.value,
                            onCollapse = { expandedGroupKey = null },
                            onPin = { pin(it) }
                        )
                    }
                    is GalleryRowItem.CollapsedRow -> {
                        Row(
                            modifier = animatedItem.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val firstIndex = groups.indexOf(item.first)
                            val firstRatio = if (firstIndex in primaryRowAspectRatios.indices) primaryRowAspectRatios[firstIndex] else 1.2f
                            Box(modifier = Modifier.weight(1f)) {
                                CollapsedGroupItem(
                                    group = item.first,
                                    boxAspectRatio = firstRatio,
                                    enabled = item.first.primaryVariant.receiverClass.name !in cooling.value,
                                    onClick = {
                                        if (item.first.isMultiSize) {
                                            expandedGroupKey = item.first.key
                                        } else {
                                            pin(item.first.primaryVariant)
                                        }
                                    }
                                )
                            }
                            if (item.second != null) {
                                val secondIndex = groups.indexOf(item.second)
                                val secondRatio = if (secondIndex in primaryRowAspectRatios.indices) primaryRowAspectRatios[secondIndex] else 1.2f
                                Box(modifier = Modifier.weight(1f)) {
                                    CollapsedGroupItem(
                                        group = item.second,
                                        boxAspectRatio = secondRatio,
                                        enabled = item.second.primaryVariant.receiverClass.name !in cooling.value,
                                        onClick = {
                                            if (item.second.isMultiSize) {
                                                expandedGroupKey = item.second.key
                                            } else {
                                                pin(item.second.primaryVariant)
                                            }
                                        }
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollapsedGroupItem(
    group: WidgetGalleryGroup,
    boxAspectRatio: Float,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    // 收起状态：去掉尺寸后缀，显示"(多尺寸)"
    val fullName = stringResource(group.primaryVariant.displayNameRes)
    val baseName = extractBaseName(fullName)
    val displayName = if (group.isMultiSize) "$baseName（多尺寸）" else baseName
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (group.isMultiSize) {
                Modifier.accordionToggle(expanded = false, onToggle = onClick, enabled = enabled)
            } else if (enabled) Modifier.noRippleClickable(onClick) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(group.primaryVariant.previewImageRes),
            contentDescription = displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                // 行框 = 该排大卡自身比例: 大卡正常撑满, 小卡等比缩放到同高后居中(Fit)。
                .aspectRatio(boxAspectRatio),
            alpha = if (enabled) 1f else 0.5f
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) colors.onSurface else colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (group.isMultiSize) AccordionChevron(expanded = false)
        }
    }
}

@Composable
private fun ExpandedGroupItem(
    group: WidgetGalleryGroup,
    modifier: Modifier = Modifier,
    cooling: Set<String>,
    onCollapse: () -> Unit,
    onPin: (WidgetVariantInfo) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    // 展开状态：标题不带"(多尺寸)"后缀，只显示基础名称
    val baseName = stringResource(group.primaryVariant.displayNameRes)
    val displayName = extractBaseName(baseName)
    // 展开区域内部各变体也按 2 列排布, 计算行框宽高比
    val variantRatios = remember(group.allVariants) {
        WidgetGalleryData.rowBoxAspectRatios(group.allVariants, GALLERY_COLUMNS)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceContainer)
            .border(1.dp, colors.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        AccordionHeader(
            expanded = true,
            onToggle = onCollapse,
            contentPadding = PaddingValues(0.dp),
        ) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 展开列出所有尺寸变体 (包含主变体 4×4 与展开的次级变体 4×2, 2×2 等)
        val chunkedVariants = remember(group.allVariants) { group.allVariants.chunked(GALLERY_COLUMNS) }
        chunkedVariants.forEachIndexed { rowIndex, rowVariants ->
            if (rowIndex > 0) {
                Spacer(modifier = Modifier.height(12.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowVariants.forEachIndexed { colIndex, variant ->
                    val variantIndex = rowIndex * GALLERY_COLUMNS + colIndex
                    val boxRatio = if (variantIndex in variantRatios.indices) variantRatios[variantIndex] else 1.2f
                    Box(modifier = Modifier.weight(1f)) {
                        VariantItem(
                            variant = variant,
                            boxAspectRatio = boxRatio,
                            enabled = variant.receiverClass.name !in cooling,
                            onClick = { onPin(variant) }
                        )
                    }
                }
                // 奇数项补齐空占位, 维持 2 列表格对齐
                if (rowVariants.size < GALLERY_COLUMNS) {
                    repeat(GALLERY_COLUMNS - rowVariants.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun VariantItem(
    variant: WidgetVariantInfo,
    boxAspectRatio: Float,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.noRippleClickable(onClick) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(variant.previewImageRes),
            contentDescription = stringResource(variant.displayNameRes),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(boxAspectRatio),
            alpha = if (enabled) 1f else 0.5f
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(variant.displayNameRes),
            style = MaterialTheme.typography.titleSmall,
            color = if (enabled) colors.onSurface else colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}
