package com.lingion.sleepy.ui.component

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.scale as scaleScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * 玻璃 Dock — FlClash `navigation_dock.dart` build (1129-1288) 的 Compose 复刻。
 *
 * 引擎 = [LiquidGlassEngine](`Paint.setShader` 路线, API 31 全功能):
 * 三层 pane 栈各持一个独立 shader 实例(HWUI 实例隔离, 见引擎类头), backdrop
 * 采样喂 [backdrop] —— 内容层在 SleepyNavHost 用 GraphicsLayer.record→
 * toImageBitmap 捕获的**上一帧**(FlClash `LiquidGlassHost._snapshot` 合成期
 * 捕获语义, 一帧滞后同源)。atlas 原点 = 记录节点在 root 的实测位置
 * ([GlassBackdropHolder.originRootPx], SleepyNavHost 的 onGloballyPositioned),
 * 每 pane 采样偏移 padDp = pane 在 root 的 dp 位置 ÷ density
 * ([GlassUniforms.sampling])。
 *
 * ```
 * _DockSurface(pill pane+glow+dockGlassShadows)   ← 外层盒 drawWithContent(先画 pane)
 *  Material(transparent)
 *   Listener(raw pointer)                          ← 挂 padding 后内容 Row(坐标等价)
 *    Padding(_barPadding)
 *     Stack(clip none) {
 *       _Lens 托盘: secondary tone4/90 α.85·(1−lift)
 *       _HoverHighlight α.08·hoverShow             ← 桌面指针, 触屏恒 0
 *       _GlassLens pane: lens.scale(lift)+_lensShadows  ← lift>0 才存在(结构位)
 *       items(outside|both) + _LensContent{items(inside)+pane}
 *     }
 * ```
 *
 * backdrop 次序 = 绘制次序 = FlClash `_Replay` 断链语义: pill 只见屏幕内容;
 * glassLens 见内容+pill+托盘; 内圈 items 不进页面 backdrop。_LensContent 走
 * Dart warp 分支(1441-1465, ultra 质量默认在场): 内圈副本 record 进独立
 * GraphicsLayer(= `Clip.antiAliasWithSaveLayer` 离屏 saveLayer 等价, "they are
 * drawn offscreen, so that pane reads them and not the bar" 1411-1412),
 * lensContent pane 采样**这份内圈纹理**盖在 lens rect 上 —— 图标/文字像
 * FlClash 一样被折射弯折(而非只折射页面背景)。非 warp 分支(1433-1440)仅作
 * warp pane/内圈纹理未就绪帧的瞬态底(一帧滞后语义)。
 *
 * pill 的 haze 双槽 mix = 两遍 drawPath(pillPane sharp + hazePane 自糊叠层)。
 *
 * 整条压弹 _PressTransform(lift=_swell, pull=(_stretch,0), 718-730) = 外层盒
 * graphicsLayer 中心缩放+平移, 绘制帧直读弹簧。
 *
 * 引擎不可用(API<31/AGSL 失败/backdrop 未就绪)→ pane 短路 + pill 退 classic
 * 实底(floatingOf), 同一栈骨架其余层照常。
 *
 * pop 重建守卫(2026-09-21): 玻璃分支高亮=lens 弹簧, remember(layoutKey) 组合
 * 销毁即新建 → 构造初值=selectedIndex, 首帧钉死原位不滑入。
 */
@Composable
internal fun DockGlassBar(
    items: List<PillNavItemSpec>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    colors: androidx.compose.material3.ColorScheme,
    density: Density,
    isDark: Boolean,
    darkStrength: Float,
    barH: androidx.compose.ui.unit.Dp,
    bottomDp: androidx.compose.ui.unit.Dp,
    /** 身后内容上一帧捕获(atlas, 原点=holder.originRootPx)。null = 未就绪 → classic 底。 */
    backdropHolder: GlassBackdropHolder?,
    modifier: Modifier = Modifier,
    onBarGeometry: (Float, Float, Boolean) -> Unit,
    widestLabelPx: Float,
    onWidestLabel: (Int, Float) -> Unit,
    geo: DockGeo?,
    onGeo: (DockGeo?) -> Unit,
) {
    val count = items.size
    val lastIndex = count - 1
    val engine = LiquidGlassEngine.rememberEngine()
    val padPx = with(density) { NavDockSpec.barPadding.toPx() }
    val minLensPx = with(density) { NavDockSpec.minLens.toPx() }
    val lensGrowthPx = with(density) { NavDockSpec.lensGrowth.toPx() }
    val innerHPx = with(density) { (barH - NavDockSpec.barPadding * 2).toPx() }
    val outerHPx = with(density) { barH.toPx() }
    val haptic = LocalHapticFeedback.current
    // 审计 G10: CLOCK_TICK 需平台 View 直发(Compose 封装只暴露
    // HapticFeedbackType 档位, 无 selectionClick 档)。
    val anchorView = androidx.compose.ui.platform.LocalView.current

    val layoutKey = DockLayoutKey(
        spanDp = with(density) { (geo?.span ?: 0f).toDp() }.value,
        count = count,
        barHeightDp = barH.value,
        density = density.density,
    )
    val motion = rememberDockMotion(
        key = layoutKey,
        selectedIndex = selectedIndex,
        onSelection = onSelect,
        // 审计 G10: Dart selectionClick(1083/1108) → Android
        // HapticFeedbackConstants.CLOCK_TICK(Flutter Android embedder
        // PlatformChannel.handleVibrate: "selectionClick"=CLOCK_TICK)。
        // 新版 Compose performHapticFeedback 只收 HapticFeedbackType
        // (LongPress=LONG_PRESS 更重更黏) → 走平台 View 直发, 质感一致。
        onHaptic = {
            anchorView?.performHapticFeedback(
                android.view.HapticFeedbackConstants.CLOCK_TICK,
                android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
            )
        },
        restPosition = { selectedIndex.toFloat() },
        restIndex = { selectedIndex },
    )
    LaunchedEffect(selectedIndex) {
        // didUpdateWidget 对位: 先重灌活读闭包, 再走 lens 归位判定(两者共用
        // 新选中; 冻结旧值 = release/cancel/leanLens 全对比失真 → 松手落错座)。
        motion.bindSelected(selectedIndex)
        motion.onSelectedChanged(selectedIndex)
    }

    val pillPane = remember(engine) { engine?.newPane("DockPill") }
    val hazePane = remember(engine) { engine?.newPane("DockPillHaze") }
    val lensPane = remember(engine) { engine?.newPane("DockLens") }
    // 带重放专用实例: 同一 RuntimeShader 一帧内两组 uniform 会被 HWUI 快照
    // 串写(屏幕遍与带遍 uniform 不同 → 后者覆盖前者), 类头实例隔离同因。
    val bandPillPane = remember(engine) { engine?.newPane("DockBandPill") }
    val bandHazePane = remember(engine) { engine?.newPane("DockBandPillHaze") }
    // warp 分支(Dart 1441-1465)的 lensContent pane: 采样内圈离屏副本,
    // 折射 dock 自身元素(refraction 14 / bevel 11.2, 预设已在 NavDockGlass
    // 对齐 liquid_glass.dart 211-223 并有契约测试)。
    val lensContentPane = remember(engine) { engine?.newPane("DockLensContent") }
    // ══ 条体合成带捕获(FlClash _Replay 语义, 症状①修复核心) ══
    // Dart: _GlassLens 的 capture 按 paint-order 重放 host root 里**先于自己
    // 画完的所有层**(page 内容 + pill 玻璃 + haze + 托盘 + hover), 不含 items
    // (画在其后), 不含自身(层级 level 排序, liquid_glass_capture.dart
    // 320-349/618-645)。旧实现只喂页面 atlas → 透镜折射不到条体自身。
    // Compose 无 picture replay → 按住期间每帧把同一批 pane(bandPillPane/
    // bandHazePane 独立实例, 防一帧内同 shader 双 uniform 快照串写)按
    // **变换后屏幕位置**重画进一块与设备像素 1:1 的 GraphicsLayer(带原点 =
    // bar 屏幕矩形左上 − margin, 录制在内圈 draw pass 顶部), 帧末回读
    // bandTex(一帧滞后 = 快照语义, 与页面 atlas/innerTex 同一已验证通路)。
    // 带 px = 设备 px → uSample/uFrame 换算与页面 atlas 完全同式, 仅原点不同;
    // 内容烘入 press 变换 → 带 = "本帧屏幕的条体区域", 透镜对条体相对采样
    // 与实屏一致(静置 s=1 时带即页面+条体逐位; 缩放时形状场有 ≤4% 近似 —
    // 实屏是 shader 出图后整体缩放, 带内是输入缩放, 真机拖动越界才可见)。
    // hover 层触屏恒 α0 → 不重放。
    var bandTex by remember { mutableStateOf<Bitmap?>(null) }
    val bandLayer = rememberGraphicsLayer()
    // 录制遍写(普通数组: 绘制期状态写=每帧重组, pillUniformsRef 同一教训),
    // 回读协程消费。
    val bandOrigin = remember { FloatArray(2) }
    val bandArmed = remember { BooleanArray(1) }
    // 与 bandTex **同次快照**的带原点: 拖动时带原点逐帧移动, 纹理与原点分读
    // 两次 = 旧带+新原点错位。写侧=回读协程(atomic replace), 读侧=绘制帧。
    var bandOriginPair by remember { mutableStateOf<FloatArray?>(null) }
    val bandStale = remember { ArrayDeque<Bitmap>() }
    DisposableEffect(Unit) {
        onDispose {
            bandTex?.recycle(); bandTex = null
            bandOriginPair = null
            while (bandStale.isNotEmpty()) bandStale.removeFirst().recycle()
        }
    }
    // 回读环: 录制遍(bar draw pass)→ withFrameMillis → 回读 → bandTex。
    // 只按住期间转(退出即停, 防常亮全屏拷贝)。旧位图延后 3 swap 回收:
    // draw pass 可能仍在采样它, 立即 recycle = 采样已释放内存。
    LaunchedEffect(motion.lifted) {
        if (!motion.lifted) {
            // 释放: 断供(绘制帧 bd=null → classic 路径), 缓存剪到 ≤1 份
            // (最新一份留着防当帧采样, 其余立即回收)。
            bandTex = null
            bandOriginPair = null
            while (bandStale.size > 1) bandStale.removeFirst().recycle()
            return@LaunchedEffect
        }
        while (isActive) {
            androidx.compose.runtime.withFrameMillis { }
            if (bandArmed[0] == false) continue
            bandArmed[0] = false
            val bmp = runCatching { bandLayer.toImageBitmap().asAndroidBitmap() }.getOrNull()
            if (bmp != null) {
                val old = bandTex
                bandTex = bmp
                bandOriginPair = floatArrayOf(bandOrigin[0], bandOrigin[1])
                if (old != null) {
                    bandStale.addLast(old)
                    if (bandStale.size > 3) bandStale.removeFirst().recycle()
                }
            }
        }
    }
    DisposableEffect(pillPane, hazePane, lensPane, lensContentPane, bandPillPane, bandHazePane) {
        onDispose {
            pillPane?.dispose(); hazePane?.dispose(); lensPane?.dispose()
            lensContentPane?.dispose(); bandPillPane?.dispose(); bandHazePane?.dispose()
        }
    }

    val pillStyle = remember(isDark) { DockGlassStyles.pill(isDark) }
    val lensStyle = remember(isDark) { DockGlassStyles.lens(isDark) }
    // 带外扩边距(px): bevel 位移 + rimBlur + 光晕 reach 的采样余量, 透镜折射
    // 越出 pill 盒沿时带内仍有内容(= FlClash capture 的 pad 概念)。
    val bandMarginPx = with(density) {
        (pillStyle.bevel + pillStyle.rimBlur + NavDockSpec.glowReach * 2f + 8f).dp.toPx()
    }
    val pillTint = remember(colors, isDark) {
        if (isDark) colors.surfaceContainerHighest.copy(alpha = pillStyle.tintAlpha)
        else Color(0x93F4F4F4.toInt())
    }
    val capsule = RoundedCornerShape(percent = 50)

    // bar 在 root 的 px 位置(drawWithContent 绘制帧直读, 不重组) — backdrop padDp 基准。
    val barRootX = remember { mutableFloatStateOf(0f) }
    val barRootY = remember { mutableFloatStateOf(0f) }
    val barRootW = remember { mutableFloatStateOf(0f) }

    /**
     * bar 局部 px → root px(屏幕位置): 折进 _PressTransform 的 scale+pull。
     * Compose graphicsLayer 绕 pivot 缩放(translation 只加 pull), 故
     * screen = c + s(p−c) + pull —— 与 Flutter `_transform`(718-730)同式。
     * 绘制帧调用(读弹簧=订阅重绘)。backdrop 采样 pad 必须用它: atlas 是
     * 屏幕内容, 不随本节点变形; 透镜平移/缩放时采样跟着走。
     */
    fun barLocalToRootPx(x: Float, y: Float): Offset {
        val g = geo
        val w = barRootW.floatValue
        val h = outerHPx
        if (g == null || w <= 0f) return Offset(barRootX.floatValue + x, barRootY.floatValue + y)
        val t = motion.pressTransform(g.pitch, count, g.inset, padPx, outerHPx)
        return t.screenRootPx(x, y, w / 2f, h / 2f, barRootX.floatValue, barRootY.floatValue)
    }

    /** pane root px → sampling 8-float(纹理原点/尺寸显式给, 分轴)。 */
    fun samplingForTexture(
        rootX: Float, rootY: Float,
        texW: Float, texH: Float,
        originX: Float, originY: Float,
    ): FloatArray = GlassUniforms.sampling(
        padDp = (rootX - originX) / density.density,
        texelsPerDp = density.density,
        texW = texW,
        texH = texH,
    ).also { it[1] = -(rootY - originY) / density.density }

    /** pane root px → sampling 8-float(减去页面 atlas 原点)。 */
    fun samplingForRoot(rootX: Float, rootY: Float, bd: Bitmap): FloatArray {
        val holder = backdropHolder
        return samplingForTexture(
            rootX, rootY,
            bd.width.toFloat(), bd.height.toFloat(),
            holder?.originRootPx?.x ?: 0f, holder?.originRootPx?.y ?: 0f,
        )
    }

    /** pill 触点光晕(pane 本地 dp) — 实屏遍与带重放同源(= Dart _shine 监听器)。
     *  审计 G2 根修: Dart `_shine` 在 _glow/_glowSpread **每 tick** 重算
     *  strength=peak·g² 与 reach=28·(1+3·spread)(866-874; 监听器 817-818)。
     *  旧实现读机器**事件时**缓存值 → down 瞬亮、up 瞬灭、reach 冻结 28。
     *  绘制遍直读两弹簧值(读即订阅=与 tick 监听同一节拍), 中心仍取机器
     *  最后触点(Dart _glowAt 语义, up 后不重置 1013/1022)。 */
    fun pillGlow(): GlassUniforms.Glow = GlassUniforms.Glow(
        centerX = motion.machine.glowCenterXDp + NavDockSpec.barPadding.value,
        centerY = motion.machine.glowCenterYDp + NavDockSpec.barPadding.value,
        strength = DockGlassLogic.glowStrength(
            motion.glow.value.coerceIn(0f, 1f), DockSpringConsts.GLOW_PEAK,
        ),
        reach = DockGlassLogic.glowReach(
            DockSpringConsts.GLOW_REACH,
            motion.glowSpread.value.coerceIn(0f, 1f),
            DockSpringConsts.GLOW_GROWTH,
        ),
        wash = DockSpringConsts.GLOW_WASH,
    )

    /**
     * bar 局部 pill 外盒 → root px 合成带矩形(外扩 bevel+blur 边距, 取整到
     * 设备像素网格, 折入 press 变换后的**屏幕**位置)。null = bar 未测量或
     * backdrop 未就绪。带原点 = 矩形左上; 带 px = 设备 px → 与页面 atlas
     * 同一 uFrame/uTexels 换算, 仅纹理原点不同。
     */
    fun bandRectRootPx(): Rect? {
        val w = barRootW.floatValue
        if (w <= 0f || outerHPx <= 0f || backdropHolder?.bitmap == null) return null
        val tl = barLocalToRootPx(0f, 0f)
        val br = barLocalToRootPx(w, outerHPx)
        val r = Rect(
            floor(minOf(tl.x, br.x) - bandMarginPx), floor(minOf(tl.y, br.y) - bandMarginPx),
            ceil(maxOf(tl.x, br.x) + bandMarginPx), ceil(maxOf(tl.y, br.y) + bandMarginPx),
        )
        return if (r.width < 2f || r.height < 2f) null else r
    }

    /**
     * 每录制帧(bar 盒 draw pass 顶部, lift>0): 把 pill 玻璃 + haze + 托盘重画
     * 进 [bandLayer](= FlClash `_Replay`, capture 320-349/618-645 的 paint-order
     * 重放; 不含 items — Dart 画在 _GlassLens 之后, 不含 hover — 触屏恒 α0)。
     * 录制层尺寸 = 带矩形, 层内坐标 = 带局部 px(带左上 = bandOrigin)。
     * 内容按 **变换后** 屏幕位置落位(barLocalToRootPx 映射, 与实屏同 geometry
     * Layer 变换)→ 带 = "本帧若无 items/透镜, 屏幕会显示的条体区域"。
     * FlClash 重放走当帧 Picture 无滞后; Compose 通路(record→帧末回读)与
     * 页面 atlas/innerTex 同为**一帧滞后快照** — 已验证的既有模式, 快速拖动
     * 时带内容与实屏差一帧(≈16ms), 静置/按座零差。
     */
    fun DrawScope.recordDockBand(br: Rect) {
        val bd = backdropHolder?.bitmap ?: return
        bandOrigin[0] = br.left
        bandOrigin[1] = br.top
        bandArmed[0] = true
        // FlClash _Replay = paint-order 重放 dock 之前的**整幅画面**: 页面内容
        // + 条体玻璃。旧实现漏了页面底 → 带内 pill 矩形外全透明 → 透镜溢出条
        // 上沿的部分折射不到任何内容 = 色散无对比源(症状1)、浮起无内容抬升
        // (症状2)、条沿亮边在带外截断成灰斑(症状3)的同一根因。
        // 页面 atlas(root px, 左上=originRootPx)按带矩形 1:1 铺底。
        val org = backdropHolder?.originRootPx ?: Offset.Zero
        drawIntoCanvas { c ->
            // 页面左上 org(root px) → 层局部 = org − 带左上。写反会把整页
            // 推到带下方 ~1800px 处(层外), 带底恒透明 = 色散无对比源。
            c.nativeCanvas.drawBitmap(bd, org.x - br.left, org.y - br.top, null)
        }
        val wBar = barRootW.floatValue
        fun cornerOf(x: Float, y: Float): Offset {
            val r = barLocalToRootPx(x, y)
            return Offset(r.x - br.left, r.y - br.top)
        }
        val pillRect = Rect(cornerOf(0f, 0f), cornerOf(wBar, outerHPx))
        if (pillRect.width < 2f || pillRect.height < 2f) return
        val radiusPx = minOf(pillRect.width, pillRect.height) / 2f
        val pillClip = LiquidGlassOutline.path(
            Rect(0f, 0f, pillRect.width, pillRect.height), radiusPx,
        )
        val pill = bandPillPane
        val haze = bandHazePane
        if (pill != null && haze != null) {
            // 采样点 = 变换后的屏幕角(= 实屏 pill pass 同一 corner)→ 带内
            // pill 折射到的页面内容与实屏逐点一致。
            // 形状场与 patch 实际尺寸取**带内(已烘变换)的 dp 尺寸**——
            // uShape/uCurves/edge 场与 patch 自洽(契约: uShape = rect 尺寸,
            // 单位 dp, 与 rectOrigin 折算后的点场同域)。与实屏 uniform 仅
            // 在 scale≠1 时有差: 实屏 = 出图后整体缩放, 带 = 形状场缩放;
            // 静置 s=1 逐位一致, 拖动越界为 ≤4% 近似(块头注)。
            val corner = barLocalToRootPx(0f, 0f)
            val smp = samplingForRoot(corner.x, corner.y, bd)
            val sharpUniforms = GlassUniforms.build(
                rectWdp = pillRect.width / density.density,
                rectHdp = pillRect.height / density.density,
                cornerRadiusDp = radiusPx / density.density,
                style = pillStyle,
                tintArgb = pillTint.toArgb(),
                glow = pillGlow(),
                density = density.density,
                stretch = 1f,
                sampling = smp,
                heldFrostTexelPx = 0f,
                hazePadSampling = null,
                rectOriginXPx = pillRect.left,
                rectOriginYPx = pillRect.top,
                tintAlphaF = pillStyle.tintAlpha,
            )
            pill.drawBackdropShader(
                scope = this,
                backdropBitmap = bd,
                uniforms = sharpUniforms,
                rect = pillRect,
                clip = pillClip,
            )
            haze.drawBlurBackdrop(
                scope = this,
                backdropBitmap = bd,
                uniforms = sharpUniforms,
                sigmaDp = pillStyle.hazeBlur,
                alpha = pillStyle.haze,
                rect = pillRect,
                clip = pillClip,
            )
        } else {
            // classic 降级: 带内画与实屏同一实底(声明的降级一致性)。
            // 注意 clip 用带坐标路径(pane-local 的 pillClip 只对 shader 遍
            // 成立 — drawPatch 自带 translate; clipPath 无 translate)。
            clipPath(LiquidGlassOutline.path(pillRect, radiusPx)) {
                drawRect(
                    if (isDark) colors.surfaceContainerHigh else colors.surfaceBright,
                    topLeft = pillRect.topLeft, size = pillRect.size,
                )
            }
        }
        // 托盘(= 层1 同一 outline/alpha, 按屏幕位置落进带坐标)。
        val g = geo
        val l = motion.lift.value.coerceIn(0f, 1f)
        val alpha = DockGlassStyles.platterAlpha(l)
        if (g != null && alpha > 0.001f) {
            val r = motion.lensRectColumnPx(g.pitch, g.lens, innerHPx, g.inset, lensGrowthPx)
            val bandOuter = Rect(
                cornerOf(padPx + r.left, padPx + r.top),
                cornerOf(padPx + r.right, padPx + r.bottom),
            )
            val tone = if (isDark) 4f else 90f
            drawPath(
                LiquidGlassOutline.path(
                    bandOuter, minOf(bandOuter.width, bandOuter.height) / 2f,
                ),
                Color(LabTone.tone(colors.secondary.toArgb(), tone)).copy(alpha = alpha),
            )
        }
    }

    Box(        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = NavDockSpec.edgeMargin)
            .padding(top = NavDockSpec.shadowRoom, bottom = bottomDp)
    ) {
        val barMaxW = if (count < NavDockSpec.fullWidthCount)
            NavDockSpec.maxItemExtent * count + NavDockSpec.barPadding * 4
        else null
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .then(if (barMaxW != null) Modifier.widthIn(max = barMaxW) else Modifier)
                // dockGlassShadows (237-243): α.013 blur3.8 y6.5 — Compose elevation
                // 无双影独立 blur/offset, 3.25≈6.5/2, 真机校准项(spec §7)
                .shadow(
                    elevation = 3.25.dp,
                    shape = capsule,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = DockGlassStyles.dockGlassShadowAlpha),
                    spotColor = Color.Black.copy(alpha = DockGlassStyles.dockGlassShadowAlpha),
                )
                // 不 clip 本盒: FlClash Stack clipBehavior=none + _DockSurface 不裁子级,
                // 抬起的透镜(高 = innerH + 2·lensGrowth)溢出条上下沿是设计意图。
                // 在此 clip(capsule) 会把胶囊切成矩形补丁 —— 症状②根因。
                .height(barH)
                .onGloballyPositioned { c ->
                    val p = c.positionInRoot()
                    barRootX.floatValue = p.x
                    barRootY.floatValue = p.y
                    barRootW.floatValue = c.size.width.toFloat()
                    onBarGeometry(p.x, p.y, true)
                }
                // _PressTransform 1279-1287: lift=_swell pull=Offset(_stretch,0)。
                // Compose graphicsLayer 默认 pivot=(0.5,0.5) 已实现 c(1−s) 项 →
                // translation 只写 pull(再折 c(1−s) = 双重平移, 按住时整条错位)。
                .graphicsLayer {
                    val g = geo
                    if (g != null) {
                        val t = motion.pressTransform(g.pitch, count, g.inset, padPx, outerHPx)
                        translationX = t.pullXPx
                        translationY = 0f
                        scaleX = t.scaleX
                        scaleY = t.scaleY
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
                    }
                }
                // ══ pill pane(_DockSurface glass 分支, ValueListenableBuilder glow) ══
                // graphicsLayer 强制 HW 画布 — RuntimeShader 需要 isHardwareAccelerated=true
                .graphicsLayer { }
                .drawWithContent {
                    val wPx = size.width
                    val hPx = size.height
                    val radiusPx = minOf(wPx, hPx) / 2f
                    val pillClip = LiquidGlassOutline.path(Rect(0f, 0f, wPx, hPx), radiusPx)
                    val bd = backdropHolder?.bitmap
                    val glassOn = engine != null && pillPane != null && hazePane != null &&
                        bd != null && wPx >= 1f && hPx >= 1f &&
                        bd.width >= 1 && bd.height >= 1
                    if (glassOn && bd != null && pillPane != null && hazePane != null) {
                        val glow = pillGlow()
                        // pad = pane(bar)左上**屏幕** root 位置(折 press transform)
                        // 减 atlas 原点, ÷density。atlas 是屏幕内容, 条体缩放时采样跟走。
                        val barCorner = barLocalToRootPx(0f, 0f)
                        val pad = samplingForRoot(barCorner.x, barCorner.y, bd)
                        // U-9/审计3 P1-3: Dart _publish 的 uTexels.x 分母含
                        // storage[0]/[5] = _PressTransform 实时缩放(Dart capture:640,
                        // dock:721-723), 非恒 1。scaleX/scaleY 与外层 graphicsLayer
                        // 同一 pressTransform(pivot 缩放即 storage 对角元)。
                        val gNow = geo
                        val press = gNow?.let {
                            motion.pressTransform(it.pitch, count, it.inset, padPx, outerHPx)
                        }
                        val stretchNow = if (press == null) 1f
                        else maxOf(kotlin.math.abs(press.scaleX), kotlin.math.abs(press.scaleY))
                        val sharpUniforms = GlassUniforms.build(
                            rectWdp = wPx / density.density,
                            rectHdp = hPx / density.density,
                            cornerRadiusDp = radiusPx / density.density,
                            style = pillStyle,
                            tintArgb = pillTint.toArgb(),
                            glow = glow,
                            density = density.density,
                            stretch = stretchNow,
                            sampling = pad,
                            heldFrostTexelPx = 0f,
                            hazePadSampling = null, // blur 成分由 haze 叠层承担 → shader uHaze=0
                            // 审计2 P0: α 走 float 直通(深色 .48/.62 经 8bit
                            // toArgb 量化成 .4784314 ≠ Dart 常量)。
                            tintAlphaF = pillStyle.tintAlpha,
                        )
                        // 1) pill shader pass1: sharp(uHaze=0, tint/rim/glow 在场, α1)
                        pillPane.drawBackdropShader(
                            scope = this,
                            backdropBitmap = bd,
                            uniforms = sharpUniforms,
                            rect = Rect(0f, 0f, wPx, hPx),
                            clip = pillClip,
                        )
                        // 2) haze pass2 = shader 自糊叠层(σ=hazeBlur dp), SRC_OVER
                        //    α=share → share·haze+(1−share)·sharp = mix 等价
                        hazePane.drawBlurBackdrop(
                            scope = this,
                            backdropBitmap = bd,
                            uniforms = sharpUniforms,
                            sigmaDp = pillStyle.hazeBlur,
                            alpha = pillStyle.haze,
                            rect = Rect(0f, 0f, wPx, hPx),
                            clip = pillClip,
                        )
                    } else {
                        // 引擎/backdrop 未就绪: classic floatingOf 实底(声明的降级)。
                        // 盒级 clip 已移除(透镜需要溢出), 降级底自行裁胶囊。
                        clipPath(pillClip) {
                            drawRect(if (isDark) colors.surfaceContainerHigh else colors.surfaceBright)
                        }
                    }
                    // ══ 带录制遍(_Replay 对位; 仅按住, lift>0) ══
                    // 录进独立 GraphicsLayer(带尺寸, 设备 px 1:1), 帧末由
                    // 回读环 toImageBitmap → bandTex → 透镜采样。与实屏遍
                    // 同帧同弹簧值(读同一 Animatable)→ 无额外滞后差。
                    if (motion.lift.value > 0f) {
                        bandRectRootPx()?.let { br ->
                            bandLayer.record(
                                this, layoutDirection,
                                IntSize(br.width.toInt(), br.height.toInt()),
                            ) { recordDockBand(br) }
                        }
                    }
                    drawContent()
                }
        ) {
            // ══ 层 1+3 合并: _Lens 托盘 + _GlassLens pane (1327-1395) ══
            // 同一绘制帧、同一个 lens rect、同一条 clip 路径 —— FlClash 里
            // _Lens 与 _GlassLens 都吃 lensRect()(1242/1252), 不存在第二条
            // 坐标通路。旧实现层3用组合期 offset/size(弹簧快照)→ 按住动画
            // 期间托盘/透镜错位 = 症状①根因; 现全部绘制帧直读。
            Box(
                Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        val g = geo
                        if (g != null) {
                            val l = motion.lift.value.coerceIn(0f, 1f)
                            val r = motion.lensRectColumnPx(g.pitch, g.lens, innerHPx, g.inset, lensGrowthPx)
                            val outer = Rect(padPx + r.left, padPx + r.top, padPx + r.right, padPx + r.bottom)
                            val radiusPx = minOf(outer.width, outer.height) / 2f
                            val outline = LiquidGlassOutline.path(outer, radiusPx)
                            // ── 层1: 托盘 (1327-1365) ──
                            // G9 精确化: tone-4/90 托盘属 Dart **玻璃**分支
                            // (1342-1344 secondary.setTone); engine==null 走
                            // glass:false 分支(1354-1359) — 只有 floatingOf
                            // .next 底 + onSurface α.08·lift, 无 setTone 托盘。
                            val alpha = DockGlassStyles.platterAlpha(l)
                            if (alpha > 0.001f && engine != null) {
                                val tone = if (isDark) 4f else 90f
                                val platter = Color(LabTone.tone(colors.secondary.toArgb(), tone))
                                    .copy(alpha = alpha)
                                drawPath(outline, platter)
                            }
                            // engine 不可用 = Dart `glass:false` 分支的 _Lens
                            // (1354-1359): floatingOf.next 底 + onSurface
                            // α.08·lift 叠色(经典分支 lensBg 同源)。缺这层时
                            // 玻璃栈降级期按住只剩 secondary 淡出, 无任何
                            // 浮出色块 = 「按住滑块不浮出」的可见性根因之一。
                            if (engine == null && l > 0.001f) {
                                val lensBg = if (isDark) colors.surfaceContainerHighest else colors.surfaceContainer
                                drawPath(outline, lensBg)
                                drawPath(outline, colors.onSurface.copy(alpha = NavDockSpec.hoverAlpha * l))
                            }
                            // ── 层3: _GlassLens pane (1369-1395; lift>0 才存在) ──
                            // 纹理优先 = 合成带(_Replay 对位: 页面+条体, 不含
                            // items/自身); 带未就绪(按住首帧/降级)回退页面 atlas
                            // — FlClash capture 缺 slot 时同 fall-through(旧行为)。
                            val band = bandTex
                            val bandOrg = bandOriginPair
                            val bandReady = band != null && bandOrg != null &&
                                band.width >= 1 && band.height >= 1
                            val bd = if (bandReady) band else backdropHolder?.bitmap
                            if (l > 0f && engine != null && lensPane != null && bd != null &&
                                bd.width >= 1 && bd.height >= 1 &&
                                outer.width >= 1f && outer.height >= 1f
                            ) {
                                // _lensShadows(1397-1408): blur5 y8 α.055·strength·lift。
                                // BlurMaskFilter 逐字等价: Skia sigma=0.28481·r−0.58268
                                // 与 Flutter blurRadiusToSigma 同式 → 传 blurRadius 原值。
                                if (l > 0.001f) {
                                    val blurPx = with(density) { DockGlassStyles.lensShadowBlurDp.dp.toPx() }
                                    val offsetY = with(density) { DockGlassStyles.lensShadowOffsetYDp.dp.toPx() }
                                    drawIntoCanvas { canvas ->
                                        val nc = canvas.nativeCanvas
                                        val sp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                                        sp.color = Color.Black
                                            .copy(alpha = DockGlassStyles.lensShadowAlpha(l, darkStrength))
                                            .toArgb()
                                        sp.maskFilter = android.graphics.BlurMaskFilter(
                                            blurPx, android.graphics.BlurMaskFilter.Blur.NORMAL,
                                        )
                                        nc.save()
                                        nc.translate(0f, offsetY)
                                        nc.drawPath(outline.asAndroidPath(), sp)
                                        nc.restore()
                                    }
                                }
                                val scaled = lensStyle.scale(l)
                                val corner = barLocalToRootPx(outer.left, outer.top)
                                val smp = if (bandReady && band != null && bandOrg != null) {
                                    samplingForTexture(
                                        corner.x, corner.y,
                                        band.width.toFloat(), band.height.toFloat(),
                                        bandOrg[0], bandOrg[1],
                                    )
                                } else {
                                    samplingForRoot(corner.x, corner.y, bd)
                                }
                                val uniforms = GlassUniforms.build(
                                    rectWdp = outer.width / density.density,
                                    rectHdp = outer.height / density.density,
                                    cornerRadiusDp = radiusPx / density.density,
                                    style = scaled,
                                    tintArgb = 0, // transparent(196)
                                    glow = GlassUniforms.Glow.NONE, // IgnorePointer → _press=0(906)
                                    density = density.density,
                                    sampling = smp,
                                    heldFrostTexelPx = 0f,
                                    // 审计 U-3 P0: fragCoord 是本节点(内圈 Row)局部
                                    // px, drawPatch translate(outer.left, outer.top)
                                    // → uFrame.xy 必须 = −outer.xy/density 折回 pane
                                    // 本地, 否则 shape/normal/rim 场整体偏移一个 outer 距离。
                                    rectOriginXPx = outer.left,
                                    rectOriginYPx = outer.top,
                                )
                                lensPane.drawBackdropShader(
                                    scope = this,
                                    backdropBitmap = bd,
                                    uniforms = uniforms,
                                    rect = outer,
                                    clip = LiquidGlassOutline.path(
                                        Rect(0f, 0f, outer.width, outer.height), radiusPx,
                                    ),
                                )
                            }
                        }
                        drawContent()
                    },
            )
            // ══ 层 2: _HoverHighlight (1294-1325; 桌面指针, 触屏 hoverAt 恒 null) ══
            if (geo != null) {
                val g = geo
                Box(
                    Modifier
                        .size(
                            width = with(density) { g.lens.toDp() },
                            height = barH - NavDockSpec.barPadding * 2,
                        )
                        .offset(
                            x = with(density) { (padPx + g.inset).toDp() },
                            y = NavDockSpec.barPadding,
                        )
                        .graphicsLayer {
                            val start = g.inset + (motion.hover.value + 0.5f) * g.pitch - g.lens / 2f
                            translationX = start - g.inset
                            alpha = NavDockSpec.hoverAlpha * motion.hoverShow.value.coerceIn(0f, 1f)
                        }
                        .clip(capsule)
                        .background(colors.onSurface),
                )
            }
            // ══ 层 4+5: items 双拷贝 (1596-1992) ══
            DockGlassItemsRow(
                items = items,
                count = count,
                selectedIndex = selectedIndex,
                colors = colors,
                density = density,
                motion = motion,
                engine = engine,
                lensContentPane = lensContentPane,
                isDark = isDark,
                barH = barH,
                padPx = padPx,
                minLensPx = minLensPx,
                lensGrowthPx = lensGrowthPx,
                innerHPx = innerHPx,
                geo = geo,
                onGeo = onGeo,
                widestLabelPx = widestLabelPx,
                onWidestLabel = onWidestLabel,
                // 绘制帧现算(读弹簧) —— 与层1+3同一 rect 通路, 杜绝组合期快照
                lensRectProvider = { g ->
                    motion.lensRectColumnPx(g.pitch, g.lens, innerHPx, g.inset, lensGrowthPx)
                },
                onSelect = onSelect,
            )
        }
    }
}

/**
 * items 双拷贝(= Dart items(_LensPart) 1196-1224 画两遍):
 *  - 外圈: 原色 onSurfaceVariant, 绘制期 even-odd 裁「lens 外」(1936-1947);
 *  - 内圈: srcIn primary 整层滤镜(1975) + 随 lift 逐项放大 lensMagnify
 *    (1907 magnified motion) + ClipPath(lens) + **warp pane**(Dart
 *    _LensContent warp 分支 1441-1465, ultra 默认在场 1264): 内圈副本 record
 *    进独立 GraphicsLayer(= `Clip.antiAliasWithSaveLayer` 离屏语义, "they are
 *    drawn offscreen, so that pane reads them and not the bar" 1411-1412),
 *    lensContent pane(refraction 14/bevel 11.2, .scale(lift), tint 透明,
 *    rim 0) 采样这份内圈纹理, live 合成 srcOver(liquid_glass_capture.dart
 *    645: identical(root) 分支; src 仅用于快照捕获场景) → 透镜内图标/文字
 *    随玻璃一起折射, 位移边缘留直影底衬与 Flutter 一致。
 * 静息(!lifted)只画整圈 both(_LensPart.both 1257)。
 * 指针状态机挂本 Row(raw pointer, opaque 命中 = Dart Listener behavior 1165)。
 */
@Composable
private fun DockGlassItemsRow(
    items: List<PillNavItemSpec>,
    count: Int,
    selectedIndex: Int,
    colors: androidx.compose.material3.ColorScheme,
    density: Density,
    motion: NavDockMotion,
    engine: LiquidGlassEngine?,
    lensContentPane: LiquidGlassEngine.Pane?,
    isDark: Boolean,
    barH: androidx.compose.ui.unit.Dp,
    padPx: Float,
    minLensPx: Float,
    lensGrowthPx: Float,
    innerHPx: Float,
    geo: DockGeo?,
    onGeo: (DockGeo?) -> Unit,
    widestLabelPx: Float,
    onWidestLabel: (Int, Float) -> Unit,
    /** 绘制帧现算 lens rect(列坐标, 含 inset 平移) —— 与层1+3同一通路。 */
    lensRectProvider: (DockGeo) -> Rect,
    onSelect: (Int) -> Unit,
) {
    val g = geo
    // = Dart `lensed = glass && _lifted`(1225): warp 内圈(放大+折射)只在抬起时。
    // engine==null(降级 classic 底)时内外两份文字同坐标叠画 = 双倍抗锯齿
    // 边缘, 也是静置视觉噪声; 降级路径由托盘 blend 分支承担选中反馈。
    val lensed = g != null && engine != null && motion.lifted
    // 审计 G1 P0(症状①根因): Dart `_RenderLensTint`(1900-1966) 的 `both`
    // **也是双遍**(outside=part!=inside && inside=part!=outside 同时为真):
    // 外圈 even-odd 裁 + 内圈 clip(lens) srcIn primary(mag=1)。旧 Kotlin 把
    // Both 折成单遍未裁未染 → 静息选中永不变 primary, 松手即褪色, 且色块
    // 与 tray 底无内容区分。双遍门闸 = 玻璃在场(与 lifted 无关)。
    val dual = g != null && engine != null
    val insetDp = if (g == null) 0.dp else with(density) { g.inset.toDp() }
    // 同域比较(= Dart 逻辑像素单域, 1184-1187): widest/onWidestLabel 上报 px,
    // pitch/inset 也必须 px — 混 dp 会让 room 缩 1/density, scale 恒钉地板,
    // 且地板判定随行重测摆动(文字粗细抖)。与经典分支同式(572-575)。
    val labelScale = DockDragLogic.labelScale(
        widestLabelPx,
        g?.pitch ?: 0f,
        with(density) { NavDockSpec.labelInset.toPx() },
        NavDockSpec.labelMinSize.value / NavDockSpec.labelSize.value,
    )
    // ══ warp 通路: 内圈离屏副本(纹理) → lensContent pane ══
    // G3 审计结论(2026-10-28): toImageBitmap 仅 suspend 可调, Compose 绘制
    // 帧内无同步回读 API(android.graphics.Picture→bitmap 在 HWUI 下失效)。
    // warp 纹理 = 1 帧回读(与 bandTex 同一已验证通路): 滞后只影响内容快照,
    // pane 几何/采样每帧现算 → 折射位置跟手。Dart 用同帧 picture 无此滞后,
    // 差异仅在 250ms lift 爬升期的倍率过渡一帧, 真机复测未列为可见症状 →
    // 保留回读环。旧位图延后 3 张回收(当帧 HWUI 可能仍在采样)。
    val innerLayer = rememberGraphicsLayer()
    var innerTex by remember { mutableStateOf<Bitmap?>(null) }
    val innerStale = remember { ArrayDeque<Bitmap>() }
    LaunchedEffect(lensed) {
        if (!lensed) {
            innerTex = null
            while (innerStale.isNotEmpty()) innerStale.removeFirst().recycle()
            return@LaunchedEffect
        }
        while (isActive) {
            androidx.compose.runtime.withFrameMillis { }
            val bmp = runCatching { innerLayer.toImageBitmap().asAndroidBitmap() }
                .getOrNull()
            if (bmp != null) {
                val old = innerTex
                innerTex = bmp
                if (old != null) {
                    innerStale.addLast(old)
                    if (innerStale.size > 3) innerStale.removeFirst().recycle()
                }
            }
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(barH)
            .padding(NavDockSpec.barPadding)
            .onGloballyPositioned { c ->
                val span = c.size.width.toFloat()
                val layout = DockDragLogic.dockLayout(span, count, padPx, minLensPx)
                val centers = List(count) { i -> padPx + layout.inset + layout.pitch * (i + 0.5f) }
                onGeo(DockGeo(pitch = layout.pitch, lens = layout.lens, inset = layout.inset, centers = centers, span = span))
            }
            .glassDockPointerInput(motion),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).fillMaxHeight()) {
            // 外圈 / 静息整圈
            ItemsSeatColumn(
                items = items,
                selectedIndex = selectedIndex,
                colors = colors,
                motion = motion,
                part = if (dual) LensPart.Outside else LensPart.Both,
                lensColor = colors.primary,
                insetDp = insetDp,
                labelScale = labelScale,
                onWidestLabel = onWidestLabel,
                onSelect = onSelect,
                lensRectProvider = lensRectProvider,
                geo = g,
            )
            // 内圈 + warp pane(审计 G1: Dart 静息 both=同 render 内外双遍
            // 1936-1966 → 这里 dual(=glass 在场)时内圈常驻, lifted 才加 warp)
            if (dual && g != null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            // Dart _LensContent warp 分支(1441-1465, ultra 在场
                            // 1264) 逐层等价: ClipPath(antiAliasWithSaveLayer) {
                            //   Stack: child(内圈副本) ; LiquidGlass pane }。
                            // ① record 内圈副本进独立 GraphicsLayer(离屏等价),
                            //    记录时即裁 lens —— pane 采样源不得越出透镜。
                            // ② 合成 = 独立 saveLayer 内的 BlendMode.SRC 替换
                            //    (下方分支注释给三处源码断言)。
                            // ③ 纹理未就绪(一帧滞后) → 非 warp 分支瞬态底
                            //    (1433-1440 clip+副本直显)。
                            val r = lensRectProvider(g)
                            val radiusPx = minOf(r.width, r.height) / 2f
                            val lensClip = LiquidGlassOutline.path(r, radiusPx)
                            val paneClip = LiquidGlassOutline.path(
                                Rect(0f, 0f, r.width, r.height), radiusPx,
                            )
                            // 静息(dual 非 lensed)= Dart both 内遍(1952-1966
                            // mag=_motion(1)): 只 clip(lens)+tint 副本直显,
                            // 不 record 不 pane —— 与 Dart 一样, _LensContent
                            // 与 warp 通路只在 lifted 存在(1264)。
                            if (!lensed) {
                                drawIntoCanvas { canvas ->
                                    val nc = canvas.nativeCanvas
                                    nc.save()
                                    nc.clipPath(lensClip.asAndroidPath())
                                    this@drawWithContent.drawContent()
                                    nc.restore()
                                }
                                return@drawWithContent
                            }
                            // pane rect 左上的 Box 本地坐标 = r 左上(本 canvas
                            // 即内圈空间, 副本与采样点同在, 随条体变形, 无 root
                            // /press 换算 —— 与页面 atlas 通路不同的自洽闭环)。
                            // 必须走 4 参显式 record(density, layoutDirection, size, block):
                            // 无 size 重载在图层首次 record 时尚未建立自身尺寸 → 0×0 纹理,
                            // warp pane 采样全空 = 透镜内只剩直显副本(滑块"消失")。
                            innerLayer.record(density, layoutDirection, IntSize(size.width.toInt(), size.height.toInt())) {
                                clipPath(lensClip) {
                                    this@drawWithContent.drawContent()
                                }
                            }
                            val tex = innerTex
                            val pane = lensContentPane
                            val warped = pane != null && tex != null &&
                                tex.width >= 1 && tex.height >= 1 &&
                                r.width >= 1f && r.height >= 1f
                            if (warped && pane != null && tex != null) {
                                val l = motion.lift.value.coerceIn(0f, 1f)
                                val smp = GlassUniforms.sampling(
                                    padDp = r.left / density.density,
                                    texelsPerDp = density.density,
                                    texW = tex.width.toFloat(),
                                    texH = tex.height.toFloat(),
                                ).also { it[1] = -r.top / density.density }
                                val uniforms = GlassUniforms.build(
                                    rectWdp = r.width / density.density,
                                    rectHdp = r.height / density.density,
                                    cornerRadiusDp = radiusPx / density.density,
                                    style = DockGlassStyles.lensContent(isDark).scale(l),
                                    tintArgb = 0, // transparent(Dart 212)
                                    glow = GlassUniforms.Glow.NONE, // IgnorePointer → press 0
                                    density = density.density,
                                    sampling = smp,
                                    heldFrostTexelPx = 0f,
                                    // 审计 U-3 P0: fragCoord = 本节点局部 px, drawPatch
                                    // translate(r.left, r.top) → uFrame.xy 折回 pane 本地。
                                    rectOriginXPx = r.left,
                                    rectOriginYPx = r.top,
                                )
                                // Dart _LensContent(1441-1465): ClipPath(antiAlias-
                                // WithSaveLayer){ child; pane } —— **独立合成层**内
                                // ①本帧副本直显 ②lensContent pane 以 BlendMode.SRC
                                // 替换(capture _publish 646: pane root 是 _LensContent
                                // 的离屏层 → identical(root) 不成立、ancestors 含之 →
                                // src; 586 carry-blit 亦 SRC; Modifier 类头"SRC"同断言)。
                                // saveLayer 与 SRC 分工: SRC 精确覆盖层内副本的折射
                                // 位移边缘(无半透明底衬混叠), saveLayer 保证不碰主
                                // canvas 下层 _GlassLens/pill —— 二者缺一即错。
                                drawIntoCanvas { canvas ->
                                    val nc = canvas.nativeCanvas
                                    nc.saveLayer(null, null)
                                    nc.clipPath(lensClip.asAndroidPath())
                                    this@drawWithContent.drawContent()
                                    pane.drawBackdropShader(
                                        scope = this,
                                        backdropBitmap = tex,
                                        uniforms = uniforms,
                                        rect = r,
                                        clip = paneClip,
                                        replace = true,
                                    )
                                    nc.restore()
                                }
                            } else {
                                // 纹理未就绪(首帧滞后) → 非 warp 分支瞬态底
                                // (1433-1440: clip + 副本直显, 无折射层)。
                                drawIntoCanvas { canvas ->
                                    val nc = canvas.nativeCanvas
                                    nc.save()
                                    nc.clipPath(lensClip.asAndroidPath())
                                    this@drawWithContent.drawContent()
                                    nc.restore()
                                }
                            }
                        },
                ) {
                    ItemsSeatColumn(
                        items = items,
                        selectedIndex = selectedIndex,
                        colors = colors,
                        motion = motion,
                        part = LensPart.Inside,
                        lensColor = colors.primary,
                        insetDp = insetDp,
                        labelScale = labelScale,
                        onWidestLabel = { _, _ -> },
                        onSelect = onSelect,
                        lensRectProvider = lensRectProvider,
                        geo = g,
                    )
                }
            }
        }
    }
}

private enum class LensPart { Both, Outside, Inside }

/**
 * 座位列 = Dart items(): Padding(inset) + Expanded 均分(1196-1224);
 * 单项 = GlyphIcon 28 + labelGap 2 + label 10·scale(9sp 地板, 1 行省略号)。
 * Inside 分支: 逐项随 lift 放大 1+0.16·lift(中心缩放, 1901-1971 magnified
 * motion) + 整层 srcIn primary(1975)。
 */
@Composable
private fun ItemsSeatColumn(
    items: List<PillNavItemSpec>,
    selectedIndex: Int,
    colors: androidx.compose.material3.ColorScheme,
    motion: NavDockMotion,
    part: LensPart,
    lensColor: Color,
    insetDp: androidx.compose.ui.unit.Dp,
    labelScale: Float,
    onWidestLabel: (Int, Float) -> Unit,
    onSelect: (Int) -> Unit,
    /** 绘制帧现算 lens rect(列坐标含 inset; 本列 canvas 已 padding(inset) → 减 inset)。 */
    lensRectProvider: (DockGeo) -> Rect,
    geo: DockGeo?,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = insetDp)
            .let { m ->
                if (part == LensPart.Inside) {
                    m.graphicsLayer {
                        colorFilter = ColorFilter.tint(lensColor, BlendMode.SrcIn)
                    }
                } else if (part == LensPart.Outside && geo != null) {
                    // even-odd 外裁: lens 像素之外才落笔(1936-1947)。
                    // canvas 已 padding(inset) → 列坐标 rect 减 inset 即本地坐标
                    // (旧实现再 +padPx = 双重偏移, 裁出区域与透镜错开 → 症状④)。
                    // 门闸 = 组合期 part(由 lensed 单源决定), **不再读 motion.lifted**:
                    // 内圈存在性(lensed)与外圈裁切此前分属组合期/绘制期两个门,
                    // 松手瞬间 lifted 翻 false 而内圈未拆 → 未裁全拷贝+主拷贝同显
                    // = 症状①文字重叠(Dart 单次 paint 一个 lensed 同时驱动两遍,
                    // _RenderLensTint.paint 1920-1966)。
                    m.drawWithContent {
                        val g = geo
                        val r = lensRectProvider(g)
                        val local = Rect(r.left - g.inset, r.top, r.right - g.inset, r.bottom)
                        val evenOdd = Path().apply {
                            fillType = PathFillType.EvenOdd
                            addRect(Rect(0f, 0f, size.width, size.height))
                            addPath(
                                LiquidGlassOutline.path(local, minOf(local.width, local.height) / 2f),
                                Offset.Zero,
                            )
                        }
                        drawIntoCanvas { canvas ->
                            canvas.save()
                            canvas.clipPath(evenOdd)
                            drawContent()
                            canvas.restore()
                        }
                    }
                } else m
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { i, item ->
            val isSel = i == selectedIndex
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = NavDockSpec.labelInset)
                    .graphicsLayer {
                        // _motion(magnified): 单项中心缩放 + 悬停视差(触屏=0)。
                        // lift/parallax **绘制帧直读**(类头约定) — 在组合期读
                        // motion.lift.value 会订阅状态: lift 弹簧每 tick 整行
                        // 重组 → 每帧重测文本 = 颤抖放大器。
                        val liftNow = motion.lift.value
                        val mag = if (part == LensPart.Inside)
                            1f + NavDockSpec.lensMagnify * liftNow.coerceIn(0f, 1f) else 1f
                        scaleX = mag; scaleY = mag
                        // Dart `_motion`: shift = _parallax.value * size.width
                        // (1868) — 弹簧值是**座位分数**, 必须 ×pitch 换算 px;
                        // 旧实现把分数直当 px(≈0 位移, 桌面悬停视差静默丢失)。
                        translationX = (motion.parallax.getOrNull(i)?.value ?: 0f) *
                            (geo?.pitch ?: 0f)
                    }
                    .semantics {
                        role = Role.Tab
                        selected = isSel
                        contentDescription = item.label
                        if (part != LensPart.Inside) {
                            onClick(label = null) { onSelect(i); true }
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(NavDockSpec.iconSize),
                    )
                    if (item.badge) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .size(7.dp)
                                .background(colors.primary, CircleShape),
                        )
                    }
                }
                Spacer(Modifier.height(NavDockSpec.labelGap))
                Text(
                    text = item.label,
                    onTextLayout = { result ->
                        // 10sp 基准归一(开环防反馈振荡, 同经典分支)
                        val applied = result.layoutInput.style.fontSize.value
                        val ratio = if (applied > 0f) applied / NavDockSpec.labelSize.value else 1f
                        onWidestLabel(i, result.size.width / ratio)
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = NavDockSpec.labelSize * labelScale,
                        // 审计 G6: Dart style leading=14.5 逻辑 px(navigation_dock
                        // 1131-1136, 与 size=10 比值 1.45), 不随 labelScale 缩。
                        // 旧实现继承 labelSmall 的 15.sp 行高 → 文本块更高且随
                        // scale 缩, 反哺 widest↔labelScale 反馈(症状①粗细抖)。
                        lineHeight = NavDockSpec.labelLineHeight,
                        fontWeight = FontWeight.Medium, // AppTextStyle w500(1131-1136)
                        color = colors.onSurfaceVariant,
                    ),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * raw pointer 状态机(handler 挂 padding 后内容 Row → 事件=内容列坐标, pad=0 契约)。
 *
 * Dart Listener 的 onPointerUp/onPointerCancel 双通道都进 `_handlePointerEnd`
 * (1166-1169, 1028-1047): Compose 把 ACTION_CANCEL(系统边缘手势抢占/父级拦截)
 * 编码为 [PointerEventType.Exit](change.status=Canceled)。必须与 Release 同权重
 * 退出本循环, 否则循环永久挂起 → 机器 pointer 永不清零 → 后续
 * onDown(1004 `_pointer != null` 早退)全被吞 = 按住不浮出/拖动不跟手/
 * 松手高亮卡死三个症状同一根因。
 */
private fun Modifier.glassDockPointerInput(motion: NavDockMotion): Modifier =
    pointerInput(motion) {
        val padPx = NavDockSpec.barPadding.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            // ══ G5 精确化(用户: 100% 对齐): 规则移植到共享式
            // [DockDragLogic.hitTestSlop] — 条矩形内直通(含弧外透明角,
            // Flutter Material 不裁 hit), 矩形外 8dp 环内 _onSpine 投影,
            // 环外不认领不消费。节点=padding 后内容 Row: bar-outer 坐标
            // = 本地 + pad, 投影结果折回 Row 系(−pad)。
            val barW = size.width + padPx * 2f
            val barH = size.height + padPx * 2f
            val hit = DockDragLogic.hitTestSlop(
                barW, barH,
                down.position.x + padPx, down.position.y + padPx,
                NavDockSpec.hitSlop.toPx(),
            ) ?: return@awaitEachGesture
            var lx = hit.first - padPx
            var ly = hit.second - padPx
            val id = down.id
            var lt = down.uptimeMillis
            motion.onDown(id.value.toInt(), lx, ly, lt)
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val change = event.changes.firstOrNull { it.id == id }
                    if (event.type == PointerEventType.Release) {
                        // Dart 1029: `event.pointer != _pointer` 的 up 直接忽略 —
                        // 本事件不含我方指针时绝不能代发 release(他指抬起会
                        // 提前落座/切页)。
                        if (change == null) continue
                        motion.onUp(id.value.toInt(), change.position.x, change.position.y, change.uptimeMillis)
                        change.consume()
                        return@awaitEachGesture
                    }
                    if (event.type == PointerEventType.Move && change != null) {
                        lx = change.position.x
                        ly = change.position.y
                        lt = change.uptimeMillis
                        motion.onMove(id.value.toInt(), lx, ly, lt)
                        if (change.positionChange() != Offset.Zero) change.consume()
                        continue
                    }
                    // Exit 双分支(Dart Listener 黏性路由): 仅 pressed=false 的
                    // Exit(= ACTION_CANCEL: 系统手势抢占/父级拦截)终结本循环走
                    // cancel 路径(1169); 手指未抬起滑出边界(pressed=true)不中止
                    // —— 越界拖拽受支持(Flutter hit-test 捕获后跨边界保持)。
                    if (event.type == PointerEventType.Exit && change != null &&
                        !change.pressed
                    ) {
                        motion.onCancel(id.value.toInt())
                        return@awaitEachGesture
                    }
                }
            } finally {
                // D3 fix: gesture cancel (e.g. parent interception) resets state machine.
                // 正常 up/cancel 后 pointer 已清, onCancel 幂等 no-op 保险。
                motion.onCancel(id.value.toInt())
            }
        }
    }
