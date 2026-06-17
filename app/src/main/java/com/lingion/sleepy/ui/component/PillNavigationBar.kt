package com.lingion.sleepy.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.theme.SleepyTheme

/**
 * Pill Navigation Bar — 仿 switchable.html .bottom-nav
 *
 *  ┌──────────────────────────────────────────┐
 *  │  [📅]   [📝]   [🔔]   [👤]              │  ← 圆角 pill indicator
 *  │  课表   任务   提醒   我的                │
 *  └──────────────────────────────────────────┘
 *
 * 容器: surface-container, 顶部 1dp outline-variant border
 * 选中: secondary-container pill, on-secondary-container 文字
 */
@Composable
fun PillNavigationBar(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colors = SleepyTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(82.dp)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(top = 8.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            content()
        }
    }
}

@Composable
fun PillNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SleepyTheme.colors
    val pillBg by animateColorAsState(
        targetValue = if (selected) colors.secondaryContainer else colors.surfaceContainer,
        label = "pill-bg"
    )
    val labelColor = if (selected) colors.onSurface else colors.onSurfaceVariant

    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 64.dp, height = 32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(pillBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = labelColor,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            ),
            color = labelColor
        )
    }
}