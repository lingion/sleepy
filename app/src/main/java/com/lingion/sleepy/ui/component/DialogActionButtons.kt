package com.lingion.sleepy.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import com.lingion.sleepy.ui.component.liquidglass.GlassButton as Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.console.ui.dbgTag
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.util.DialogActionsLayoutPolicyBridge
import com.lingion.sleepy.util.DialogButtonsLayoutPolicy
import com.lingion.sleepy.ui.component.liquidglass.LocalGlassHost
import com.lingion.sleepy.ui.component.liquidglass.NativeGlassAlertActionButton
import com.lingion.sleepy.ui.component.liquidglass.NativeGlassAlertActionGroup
import com.lingion.sleepy.ui.component.liquidglass.NativeGlassAlertActionMetrics
import com.lingion.sleepy.ui.component.liquidglass.nativeGlassAlertActionTextStyle
import com.lingion.sleepy.ui.component.liquidglass.nativeGlassAlertActionGroupLayout
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle

/**
 * Material Design 保留 2026-09-16 的应用色块动作布局。
 * Liquid Glass 使用同一个原生 alert 动作组，包括放在正文中的显式动作组；
 * 2026-11-14 的统一要求覆盖旧的玻璃色块按钮要求，不影响正文里的普通控件。
 *
 * 官方 AlertDialog 的 textButton 槽 = 裸文字按钮; 用户 2026-09-16
 * 明确「裸 TextButton 无背景看不出可点」→ 色块按钮 + 宽度自适应换行是 Sleepy 领域需求,
 * 官方无等价组合件, 保留薄层。内部消费官方 Button, 全部官方 token。
 *
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
    // 调试台语义身份: 传前缀则三键自动得 <prefix>_confirm / <prefix>_dismiss / <prefix>_third
    // (复刻 SegmentedSwitcher 的 dbgTagPrefix 形态; null = 不打标, 全部既有调用点零影响)
    dbgTagPrefix: String? = null,
    modifier: Modifier = Modifier,
    // Naming forms can grow vertically with font scaling; existing dialogs keep their fixed row height.
    growWithContent: Boolean = false,
    dismissEnabled: Boolean = true,
    thirdEnabled: Boolean = true,
    confirmDbgTag: String? = null,
    dismissDbgTag: String? = null,
) {
    if (LocalUiStyle.current == UiStyle.LiquidGlass && LocalGlassHost.current != null) {
        NativeDialogActionButtons(
            confirmText, onConfirm, dismissText, onDismiss, thirdText, onThird,
            destructive, thirdDestructive, confirmEnabled, dismissEnabled, thirdEnabled, dbgTagPrefix, modifier,
            confirmDbgTag, dismissDbgTag,
        )
        return
    }
    val colors = MaterialTheme.colorScheme

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
                        enabled = dismissEnabled,
                        container = colors.secondaryContainer,
                        content = colors.onSecondaryContainer,
                        dbgTag = dismissDbgTag ?: dbgTagPrefix?.let { "${it}_dismiss" },
                        growWithContent = growWithContent,
                        modifier = Modifier.weight(weights[wi++])
                    )
                }
                if (thirdText != null) {
                    BlockButton(
                        text = thirdText,
                        onClick = onThird,
                        enabled = thirdEnabled,
                        container = if (thirdDestructive) colors.errorContainer else colors.secondaryContainer,
                        content = if (thirdDestructive) colors.onErrorContainer else colors.onSecondaryContainer,
                        dbgTag = dbgTagPrefix?.let { "${it}_third" },
                        growWithContent = growWithContent,
                        modifier = Modifier.weight(weights[wi++])
                    )
                }
                BlockButton(
                    text = confirmText,
                    onClick = onConfirm,
                    enabled = confirmEnabled,
                    container = if (destructive) colors.errorContainer else colors.primary,
                    content = if (destructive) colors.onErrorContainer else colors.onPrimary,
                    dbgTag = confirmDbgTag ?: dbgTagPrefix?.let { "${it}_confirm" },
                    growWithContent = growWithContent,
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
                        enabled = dismissEnabled,
                        container = colors.secondaryContainer,
                        content = colors.onSecondaryContainer,
                        dbgTag = dismissDbgTag ?: dbgTagPrefix?.let { "${it}_dismiss" },
                        growWithContent = growWithContent,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (thirdText != null) {
                    BlockButton(
                        text = thirdText,
                        onClick = onThird,
                        enabled = thirdEnabled,
                        container = if (thirdDestructive) colors.errorContainer else colors.secondaryContainer,
                        content = if (thirdDestructive) colors.onErrorContainer else colors.onSecondaryContainer,
                        dbgTag = dbgTagPrefix?.let { "${it}_third" },
                        growWithContent = growWithContent,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                BlockButton(
                    text = confirmText,
                    onClick = onConfirm,
                    enabled = confirmEnabled,
                    container = if (destructive) colors.errorContainer else colors.primary,
                    content = if (destructive) colors.onErrorContainer else colors.onPrimary,
                    dbgTag = confirmDbgTag ?: dbgTagPrefix?.let { "${it}_confirm" },
                    growWithContent = growWithContent,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun NativeDialogActionButtons(
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String?,
    onDismiss: () -> Unit,
    thirdText: String?,
    onThird: () -> Unit,
    destructive: Boolean,
    thirdDestructive: Boolean,
    confirmEnabled: Boolean,
    dismissEnabled: Boolean,
    thirdEnabled: Boolean,
    dbgTagPrefix: String?,
    modifier: Modifier,
    confirmDbgTag: String?,
    dismissDbgTag: String?,
) {
    val textMeasurer = rememberTextMeasurer()
    val textStyle = nativeGlassAlertActionTextStyle()
    val density = LocalDensity.current
    BoxWithConstraints(modifier.nativeGlassAlertActionGroupLayout().fillMaxWidth()) {
        val labels = listOfNotNull(dismissText, thirdText, confirmText)
        val labelSpacePx = with(density) {
            ((maxWidth - NativeGlassAlertActionMetrics.inset * 2 -
                NativeGlassAlertActionMetrics.gap * (labels.size - 1)) / labels.size -
                NativeGlassAlertActionMetrics.horizontalContentPadding * 2).toPx()
        }
        // The reference covers two short actions. Three actions or labels that do
        // not fit use full-width cells; measure real shaped text at the current font scale.
        val stacked = labels.size > 2 || labels.any { label ->
            textMeasurer.measure(AnnotatedString(label), textStyle, softWrap = false).size.width > labelSpacePx
        }
        NativeGlassAlertActionGroup(
            stacked = stacked,
            dismissButton = dismissText?.let { label -> {
                NativeDialogActionButton(
                    label, onDismiss, enabled = dismissEnabled,
                    dbgTag = dismissDbgTag ?: dbgTagPrefix?.let { "${it}_dismiss" },
                )
            } },
            thirdButton = thirdText?.let { label -> {
                NativeDialogActionButton(
                    label, onThird, enabled = thirdEnabled, destructive = thirdDestructive,
                    dbgTag = dbgTagPrefix?.let { "${it}_third" },
                )
            } },
            confirmButton = {
                NativeDialogActionButton(
                    confirmText, onConfirm, enabled = confirmEnabled, destructive = destructive,
                    dbgTag = confirmDbgTag ?: dbgTagPrefix?.let { "${it}_confirm" },
                )
            },
        )
    }
}

@Composable
private fun NativeDialogActionButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false,
    dbgTag: String? = null,
) {
    NativeGlassAlertActionButton(
        onClick = onClick,
        modifier = dbgTag?.let { Modifier.dbgTag(it, text = text, role = "button") } ?: Modifier,
        enabled = enabled,
        destructive = destructive,
    ) { Text(text) }
}

@Composable
private fun BlockButton(
    text: String,
    onClick: () -> Unit,
    container: Color,
    content: Color,
    enabled: Boolean = true,
    dbgTag: String? = null,
    growWithContent: Boolean = false,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .then(dbgTag?.let { Modifier.dbgTag(it, text = text, role = "button") } ?: Modifier)
            .then(if (growWithContent) Modifier.heightIn(min = SleepyTheme.Buttons.regularHeight)
                else Modifier.height(SleepyTheme.Buttons.regularHeight)),
        shape = SleepyTheme.Buttons.shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content
        )
    ) {
        // Existing callers retain single-line labels; adaptive forms allow wrapping and a taller button.
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            softWrap = growWithContent,
            maxLines = if (growWithContent) Int.MAX_VALUE else 1
        )
    }
}
