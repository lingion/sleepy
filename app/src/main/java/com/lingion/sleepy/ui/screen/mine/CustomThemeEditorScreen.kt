package com.lingion.sleepy.ui.screen.mine

import androidx.activity.compose.BackHandler
import androidx.core.graphics.toColorInt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.data.CustomTheme
import com.lingion.sleepy.ui.component.ColorPickerDialog
import com.lingion.sleepy.ui.theme.CustomSchemeDeriver
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.noRippleClickable
import kotlin.random.Random

/**
 * 自定义主题创建/微调编辑器 — 外观页弹出(overlay 全屏页,样式仿现有 Screen)。
 *
 * 三动作区:随机生成 / 选主色生成整套 / 逐角色手选。
 * 草稿语义:全部改动只改内存草稿 [draft],底部「保存」才落 CustomThemeStore。
 * 主色生成整套的邻近色逻辑:secondary = 色相 +40°、tertiary = 色相 −40°
 * (M3 邻近色思想:同主题氛围内拉开间隔,避免互补色刺眼),表面用该色相低 chroma。
 * 删除区(errorContainer 整宽色块,全 app 纯色块禁描边)→ 确认对话框。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomThemeEditorScreen(
    editing: CustomTheme?,
    nextThemeNumber: Int,
    onBack: () -> Unit,
    onSaved: (CustomTheme) -> Unit,
    onDeleted: (String) -> Unit
) {
    val colors = SleepyTheme.colors

    // 系统返回手势: 编辑器是 AppearanceScreen 内部 overlay(不在 MainActivity overlayStack),
    // 不拦截的话返回键会命中外层 handler 把整个外观页弹掉。此处拦截先关编辑器回外观页
    // (Compose BackHandler 后组合者优先生效)。
    BackHandler { onBack() }

    // ── 草稿:所有改动只动这里,保存才落盘 ──
    val defaultName = stringResource(R.string.theme_custom_default_name, nextThemeNumber)
    var draft by remember(editing) {
        mutableStateOf(
            editing ?: CustomTheme(
                id = "",   // 保存时由调用方生成
                name = defaultName,
                primary = "#7C4DFF",
                secondary = "#546E7A",
                tertiary = "#EF6C00",
                surfaceHue = 265.0,
                surfaceChroma = 8.0,
                createdAt = System.currentTimeMillis() / 1000
            )
        )
    }

    // 取色器状态:null = 关闭;非 null = 正在编辑该角色的种子
    var pickingRole by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var manualExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize().background(colors.background),
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (editing == null) R.string.theme_new else R.string.theme_custom_edit)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
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
            // ── 命名 ──
            item {
                TextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it) },
                    label = { Text(stringResource(R.string.theme_custom_name_label)) },
                    colors = SleepyTheme.fieldColors(),
                    shape = SleepyTheme.fieldShape,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ── 实时预览:迷你课表样例(顶栏条 + 胶囊 + 卡片)用草稿派生 scheme 渲染 ──
            item {
                Text(
                    stringResource(R.string.theme_editor_preview),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.onSurface
                )
                Spacer(Modifier.height(8.dp))
                DraftPreview(draft)
            }

            // ── 三动作区 ──
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionEntry(
                        icon = { Icon(Icons.Outlined.AutoAwesome, null, tint = colors.primary, modifier = Modifier.size(24.dp)) },
                        title = stringResource(R.string.theme_editor_random),
                        desc = stringResource(R.string.theme_editor_random_desc),
                        onClick = { draft = randomDraft(draft) }
                    )
                    ActionEntry(
                        icon = { Icon(Icons.Outlined.Palette, null, tint = colors.primary, modifier = Modifier.size(24.dp)) },
                        title = stringResource(R.string.theme_editor_from_seed),
                        desc = stringResource(R.string.theme_editor_from_seed_desc),
                        onClick = { pickingRole = ROLE_SEED_PRIMARY }
                    )
                    ActionEntry(
                        icon = { Icon(Icons.Outlined.Tune, null, tint = colors.primary, modifier = Modifier.size(24.dp)) },
                        title = stringResource(R.string.theme_editor_manual),
                        desc = stringResource(R.string.theme_editor_manual_desc),
                        onClick = { manualExpanded = !manualExpanded }
                    )
                }
            }

            // ── 逐角色手选(展开后四个角色行) ──
            if (manualExpanded) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        RoleRow(
                            title = stringResource(R.string.theme_role_primary),
                            desc = stringResource(R.string.theme_role_primary_desc),
                            swatchHex = draft.primary,
                            onClick = { pickingRole = ROLE_PRIMARY }
                        )
                        RoleRow(
                            title = stringResource(R.string.theme_role_secondary),
                            desc = stringResource(R.string.theme_role_secondary_desc),
                            swatchHex = draft.secondary,
                            onClick = { pickingRole = ROLE_SECONDARY }
                        )
                        RoleRow(
                            title = stringResource(R.string.theme_role_tertiary),
                            desc = stringResource(R.string.theme_role_tertiary_desc),
                            swatchHex = draft.tertiary,
                            onClick = { pickingRole = ROLE_TERTIARY }
                        )
                        RoleRow(
                            title = stringResource(R.string.theme_role_surface),
                            desc = stringResource(R.string.theme_role_surface_desc),
                            swatchHex = surfacePreviewHex(draft.surfaceHue, draft.surfaceChroma),
                            onClick = { pickingRole = ROLE_SURFACE }
                        )
                    }
                }
            }

            // ── 底部动作:保存 ──
            item {
                Button(
                    onClick = {
                        val id = draft.id.ifBlank { java.util.UUID.randomUUID().toString() }
                        onSaved(draft.copy(id = id))
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary
                    ),
                    shape = SleepyTheme.Buttons.shape,
                    modifier = Modifier.fillMaxWidth().height(SleepyTheme.Buttons.ctaHeight)
                ) {
                    Text(stringResource(R.string.save), style = MaterialTheme.typography.titleMedium)
                }
            }

            // ── 删除区(仅微调既有主题时;displayed errorContainer 整宽色块,禁描边) ──
            if (editing != null) {
                item {
                    Button(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.errorContainer,
                            contentColor = colors.onErrorContainer
                        ),
                        shape = SleepyTheme.Buttons.shape,
                        modifier = Modifier.fillMaxWidth().height(SleepyTheme.Buttons.regularHeight)
                    ) {
                        Icon(Icons.Outlined.Delete, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.delete))
                    }
                }
            }
        }
    }

    // ── 取色器弹窗(公共组件) ──
    pickingRole?.let { role ->
        BackHandler { pickingRole = null }
        ColorPickerDialog(
            initialHex = when (role) {
                ROLE_PRIMARY -> draft.primary
                ROLE_SECONDARY -> draft.secondary
                ROLE_TERTIARY -> draft.tertiary
                ROLE_SURFACE -> surfacePreviewHex(draft.surfaceHue, draft.surfaceChroma)
                else -> draft.primary   // ROLE_SEED_PRIMARY:以当前主色为起点
            },
            onConfirm = { hex ->
                val (hr, gr, br) = Triple(parseHexChannel(hex, 0), parseHexChannel(hex, 2), parseHexChannel(hex, 4))
                val hsv = CustomSchemeDeriver.rgbToHsv(hr, gr, br)
                val seedHue = CustomSchemeDeriver.normalizeHue(hsv.first.toDouble()).toDouble()
                draft = when (role) {
                    ROLE_PRIMARY -> draft.copy(primary = hex)
                    ROLE_SECONDARY -> draft.copy(secondary = hex)
                    ROLE_TERTIARY -> draft.copy(tertiary = hex)
                    // 表面中性色:选完自动降饱和 — 只取色相,chroma 钳回低饱和区间(4-12 推荐,钳 0-48)
                    ROLE_SURFACE -> draft.copy(
                        surfaceHue = seedHue,
                        // 表面中性色:选完自动降饱和 — chroma 钳回低饱和推荐区间(4-12)
                        surfaceChroma = draft.surfaceChroma.coerceIn(4.0, 12.0)
                    )
                    // 选主色生成整套:secondary 色相 +40°、tertiary 色相 −40°(M3 邻近色,见文件头注释)
                    ROLE_SEED_PRIMARY -> draft.copy(
                        primary = hex,
                        secondary = hexAtHue(seedHue + 40.0, 0.45, 0.45),
                        tertiary = hexAtHue(seedHue - 40.0, 0.55, 0.50),
                        surfaceHue = seedHue,
                        surfaceChroma = 8.0
                    )
                    else -> draft
                }
                pickingRole = null
            },
            onDismiss = { pickingRole = null }
        )
    }

    // ── 删除确认 ──
    // 返回分层: 弹层开着时返回先关弹层(后组合的 BackHandler 优先), 都没开才退出编辑器
    if (showDeleteConfirm) {
        BackHandler { showDeleteConfirm = false }
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.theme_editor_delete_confirm), color = colors.onSurface) },
            text = { Text(stringResource(R.string.theme_editor_delete_confirm_body), color = colors.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = { editing?.let { onDeleted(it.id) } }) {
                    Text(stringResource(R.string.delete), color = colors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel), color = colors.primary)
                }
            }
        )
    }
}

// ── 角色常量(取色器路由) ──
private const val ROLE_SEED_PRIMARY = "seed_primary"
private const val ROLE_PRIMARY = "primary"
private const val ROLE_SECONDARY = "secondary"
private const val ROLE_TERTIARY = "tertiary"
private const val ROLE_SURFACE = "surface"


/** 动作入口卡 — 图标 + 标题 + 说明,整卡可点 */
@Composable
private fun ActionEntry(
    icon: @Composable () -> Unit,
    title: String,
    desc: String,
    onClick: () -> Unit
) {
    val colors = SleepyTheme.colors
    Surface(
        modifier = Modifier.fillMaxWidth().clip(SleepyTheme.shapes.large).noRippleClickable(onClick),
        color = colors.surfaceContainer, shape = SleepyTheme.shapes.large
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { icon() }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

/** 角色行 — 角色名 + 说明 + 当前色块,点击弹取色器 */
@Composable
private fun RoleRow(title: String, desc: String, swatchHex: String, onClick: () -> Unit) {
    val colors = SleepyTheme.colors
    val swatch = remember(swatchHex) {
        runCatching { Color(swatchHex.toColorInt()) }
            .getOrDefault(colors.surfaceVariant)
    }
    Row(
        modifier = Modifier.fillMaxWidth().clip(SleepyTheme.shapes.medium)
            .background(colors.surfaceContainer).noRippleClickable(onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.size(32.dp).clip(CircleShape).background(swatch))
    }
}

/**
 * 迷你实时预览 — 顶栏条 + 一个胶囊 + 一张卡片样例,用草稿派生 scheme 渲染。
 * isDark 跟随当前页面模式(编辑器所见即所得的深浅一致)。
 */
@Composable
private fun DraftPreview(draft: CustomTheme) {
    val pageColors = SleepyTheme.colors
    val isDark = pageColors.background.red < 0.5f
    val scheme = remember(draft, isDark) { CustomSchemeDeriver.derive(draft, isDark) }

    Column(
        modifier = Modifier.fillMaxWidth().clip(SleepyTheme.shapes.large).background(scheme.surface)
    ) {
        // 顶栏条
        Row(
            modifier = Modifier.fillMaxWidth().background(scheme.surfaceContainer).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(scheme.primary))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.theme_editor_preview),
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurface
            )
        }
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // 当前周胶囊(primary 族)
            Box(
                modifier = Modifier.clip(RoundedCornerShape(50)).background(scheme.primary).padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    stringResource(R.string.schedule_week_prefix, 8),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onPrimary
                )
            }
            // 课程卡样例(secondaryContainer 底 + tertiary 节次 chip)
            Column(
                modifier = Modifier.fillMaxWidth().clip(SleepyTheme.shapes.medium).background(scheme.secondaryContainer).padding(10.dp)
            ) {
                Text(
                    stringResource(R.string.theme_editor_preview_card_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onSecondaryContainer
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(scheme.tertiary).padding(horizontal = 8.dp, vertical = 1.dp)
                    ) {
                        Text(
                            "1-2",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onTertiary
                        )
                    }
                    Text(
                        stringResource(R.string.theme_editor_preview_card_room),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

// ── 草稿生成工具 ──

/** 随机生成:4 源角色各随机 — 色相均匀随机、primary 高饱和、表面低 chroma(4-12) */
private fun randomDraft(current: CustomTheme): CustomTheme {
    val r = Random.Default
    val pHue = r.nextFloat() * 360f
    val sHue = r.nextFloat() * 360f
    val tHue = r.nextFloat() * 360f
    return current.copy(
        primary = hexAtHue(pHue.toDouble(), 0.70, 0.55),
        secondary = hexAtHue(sHue.toDouble(), 0.40, 0.50),
        tertiary = hexAtHue(tHue.toDouble(), 0.50, 0.55),
        surfaceHue = (r.nextFloat() * 360f).toDouble(),
        surfaceChroma = 4.0 + r.nextDouble() * 8.0   // 4-12 低饱和推荐区间
    )
}

/** 表面倾向的预览 hex(编辑器里展示用)— 用当前页面浅色端 V 反推 */
private fun surfacePreviewHex(hue: Double, chroma: Double): String =
    hexAtHue(hue, chroma.coerceIn(0.0, 48.0) / 100.0, 0.92)

/** HSV → "#RRGGBB"(自定义主题编辑器专用;纯 Kotlin 实现,与派生引擎同源) */
private fun hexAtHue(hue: Double, saturation: Double, value: Double): String {
    val c = CustomSchemeDeriver.hsvToColor(hue.toFloat(), saturation.toFloat(), value.toFloat())
    return String.format("#%02X%02X%02X", (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())
}

/** "#RRGGBB" 第 i 通道 0-255;非法输入回退 0(取色器起点容错) */
private fun parseHexChannel(hex: String, index: Int): Float {
    val body = hex.removePrefix("#")
    return if (body.length >= index + 2) {
        body.substring(index, index + 2).toIntOrNull(16)?.toFloat() ?: 0f
    } else 0f
}
