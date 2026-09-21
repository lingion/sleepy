package com.lingion.sleepy.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.util.DialogActionsLayoutPolicyBridge
import com.lingion.sleepy.util.DialogButtonsLayoutPolicy

/**
 * 弹窗动作按钮行 — 全 app 弹窗 confirm/dismiss 唯一入口 (2026-09-16 用户:
 * 裸 TextButton 纯文字无背景, 用户看不出是可点的色块, 也不知道按钮边界在哪)。
 *
 * 视觉 = 与全屏按钮同语言 (SleepyTheme.Buttons 高度/圆角): 确认键 primary 色块,
 * 取消/第三键 secondaryContainer 色块; 危险确认传 destructive=true 确认键变
 * errorContainer; 第三键破坏性传 thirdDestructive=true 第三键变 errorContainer
 * (教务导入退出确认: 继续/留草稿=secondary, 退出并删除草稿=error)。
 *
 * 布局自适应 (2026-09-16 用户第二轮: "文字完整 > 一排"): 先按可用宽度估
 * 行内总需求(DialogButtonsLayoutPolicy), 放得下 → 等宽 weight(1f) 一行;
 * 放不下 → 整组降级竖排全宽按钮, 任何情况下不截断文字。
 *
 * 用法 (M3 AlertDialog 的 confirm/dismiss 槽不吃全宽布局 — 放 text 末尾):
 * ```
 * AlertDialog(
 *   ...,
 *   text = { Column { ...; Spacer(20.dp); DialogActionButtons(...) } },
 *   confirmButton = {}, dismissButton = {}
 * )
 * ```
 * 也可独立用于 ModalBottomSheet 等非 AlertDialog 容器。
 */
@Composable
fun DialogActionButtons(
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String? = null,
    onDismiss: () -> Unit = {},
    thirdText: String? = null,
    onThird: () -> Unit = {},
    destructive: Boolean = false,
    thirdDestructive: Boolean = false,
    confirmEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val colors = SleepyTheme.colors

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val availableWidth = maxWidth.value
        val labels = listOfNotNull(dismissText, thirdText, confirmText)
        val horizontal = DialogActionsLayoutPolicyBridge.fit(labels, availableWidth)
        // 2026-09-18 用户: "一行能搞定的就一行" — 横排等分 weight(1f) 会把长标签
        // 键压到必需宽以下 → Text 自动换行。权重必须按各键必需宽比例分配,
        // 短键(「知道了」)少占, 长键(「导出排查全量包」)多占, 每键 ≥ 必需宽。
        val weights = DialogButtonsLayoutPolicy.normalizedWeights(
            labels, DialogActionsLayoutPolicyBridge.LABEL_LARGE_FONT_DP_PER_CJK_CHAR
        )
        var wi = 0

        if (horizontal) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DialogButtonsLayoutPolicy.ROW_GAP_DP.dp)
            ) {
                if (dismissText != null) {
                    BlockButton(
                        text = dismissText,
                        onClick = onDismiss,
                        container = colors.secondaryContainer,
                        content = colors.onSecondaryContainer,
                        modifier = Modifier.weight(weights[wi++])
                    )
                }
                if (thirdText != null) {
                    BlockButton(
                        text = thirdText,
                        onClick = onThird,
                        container = if (thirdDestructive) colors.errorContainer else colors.secondaryContainer,
                        content = if (thirdDestructive) colors.onErrorContainer else colors.onSecondaryContainer,
                        modifier = Modifier.weight(weights[wi++])
                    )
                }
                BlockButton(
                    text = confirmText,
                    onClick = onConfirm,
                    enabled = confirmEnabled,
                    container = if (destructive) colors.errorContainer else colors.primary,
                    content = if (destructive) colors.onErrorContainer else colors.onPrimary,
                    modifier = Modifier.weight(weights[wi++])
                )
            }
        } else {
            // 竖排降级: 全宽按钮上下堆叠, 文字必然完整。顺序 = 取消 → 第三 → 确认(动作在末位)。
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(DialogButtonsLayoutPolicy.ROW_GAP_DP.dp)
            ) {
                if (dismissText != null) {
                    BlockButton(
                        text = dismissText,
                        onClick = onDismiss,
                        container = colors.secondaryContainer,
                        content = colors.onSecondaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (thirdText != null) {
                    BlockButton(
                        text = thirdText,
                        onClick = onThird,
                        container = if (thirdDestructive) colors.errorContainer else colors.secondaryContainer,
                        content = if (thirdDestructive) colors.onErrorContainer else colors.onSecondaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                BlockButton(
                    text = confirmText,
                    onClick = onConfirm,
                    enabled = confirmEnabled,
                    container = if (destructive) colors.errorContainer else colors.primary,
                    content = if (destructive) colors.onErrorContainer else colors.onPrimary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun BlockButton(
    text: String,
    onClick: () -> Unit,
    container: Color,
    content: Color,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(SleepyTheme.Buttons.regularHeight),
        shape = SleepyTheme.Buttons.shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content
        )
    ) {
        // 2026-09-16 用户: 文字必须完整展示 — 竖排降级保证宽度, 不设 maxLines 截断
        // 2026-09-18 用户: "一行能搞定的就一行" — softWrap=false 强制单行; 横排已按
        // 必需宽比例分配权重(每键 ≥ 文字宽), 竖排全宽更装得下, 两者都不会截断。
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            softWrap = false,
            maxLines = 1
        )
    }
}
