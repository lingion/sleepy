package com.lingion.sleepy.ui.screen.widget

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.noRippleClickable
import com.lingion.sleepy.widget.PinResult
import com.lingion.sleepy.widget.WidgetGalleryCard
import com.lingion.sleepy.widget.WidgetGalleryData
import com.lingion.sleepy.widget.WidgetPinRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 添加小组件画廊页(设计 §3 末层): 右上角「＋」进入,把 ALL_WIDGET_VARIANTS
 * 全部渲染成预览卡片(数量随注册表走,零硬编码)。
 *
 * 点卡片 = 走唯一通路 [WidgetPinRequest.request] 发起系统「添加到主屏幕」确认;
 * 不支持/失败的桌面给手动添加指引 snackbar。点击后该卡禁用 ~2s 防连点重复 pin。
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
    val cards = remember { WidgetGalleryData.buildCards() }

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
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(cards, key = { it.receiverName }) { card ->
                GalleryCard(
                    card = card,
                    enabled = card.receiverName !in cooling.value,
                    onClick = {
                        cooling.value = cooling.value + card.receiverName
                        scope.launch {
                            when (WidgetPinRequest.request(context, card.variant)) {
                                PinResult.NOT_SUPPORTED, PinResult.FAILED ->
                                    snackbar.showSnackbar(unsupportedHint)
                                PinResult.REQUESTED -> { /* 系统确认弹窗接管,无副作用 */ }
                            }
                        }
                        scope.launch {
                            delay(2000)
                            cooling.value = cooling.value - card.receiverName
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun GalleryCard(card: WidgetGalleryCard, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.noRippleClickable(onClick) else Modifier)
            .padding(10.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(card.variant.previewImageRes),
                contentDescription = stringResource(card.variant.displayNameRes),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    // 卡片框 = 该 variant 声明尺寸的真实比例(缩略图与桌面占地等大比例),
                    // 统一 1.6f 会让 2×2(1.0)/4×4(1.2)脸在框内留白,与启动器预览不一致
                    .aspectRatio(card.variant.widthDp.toFloat() / card.variant.heightDp.toFloat())
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surfaceContainerHighest),
                alpha = if (enabled) 1f else 0.5f
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(card.variant.displayNameRes),
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) colors.onSurface else colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}
