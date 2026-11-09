package com.lingion.sleepy.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * 部件级动效 (Route A) — 图标拆成部件、各自动作。
 *
 * 拆分铁律 (不守即破图): 原图标是单 path 非零填充图形, 洞子路径靠反向绕数在
 * 外框内"挖空"。洞与外框分属两个部件、分开变换 → 洞被重新填满 → 静息外观偏离
 * 原图标。故 洞必须与承载它的外框同部件。today 的头带与框体共享同一外框子路径,
 * 无法拆 → 用 clip 分区(切线 y=10 落在透明洞带内, 无 AA 缝), 两件各画
 * "外框+洞" 全集再互裁。
 *
 * 单位: pivot/位移均在 vector 视口 24 域; 渲染层按 layerPx/24 换算。
 * pulse∈[0,1](激活脉冲相位, 峰值随剧本 1 或 sin 包络), spinRad=累计弧度 —
 * 与 [NavIconDriver] 动画剧本同步驱动。
 */
internal data class PartTransform(
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val translationUnitsX: Float = 0f,
    val translationUnitsY: Float = 0f,
    val rotationZ: Float = 0f,
    val alpha: Float = 1f,
)

internal class NavIconPart(
    val pathData: String,
    val pivotX: Float,
    val pivotY: Float,
    /** 视口坐标裁剪盒(共享外框的分区件用); null = 整子路径集直接画。 */
    val clipViewport: Rect? = null,
    val motion: (pulse: Float, spinRad: Float) -> PartTransform,
)

// ═══════ 课表 PageFlip: 框体稳定不动，中间 6 个日期格子按顺序依次闪出 ═══════

private const val calFrameAndHoles =
    "M19,4h-1V2h-2v2H8V2H6v2H5C3.89,4 3.01,4.9 3.01,6L3,20c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2V6C21,4.9 20.1,4 19,4z" +
        "M19,20H5V10h14V20zM19,8H5V6h14V8z"

// 6 个独立日期格子 (行1: (7..9, 12..14), (11..13, 12..14), (15..17, 12..14); 行2: (7..9, 16..18), (11..13, 16..18), (15..17, 16..18))
private const val calDot1 = "M9,14H7v-2h2V14z"
private const val calDot2 = "M13,14h-2v-2h2V14z"
private const val calDot3 = "M17,14h-2v-2h2V14z"
private const val calDot4 = "M9,18H7v-2h2V18z"
private const val calDot5 = "M13,18h-2v-2h2V18z"
private const val calDot6 = "M17,18h-2v-2h2V18z"

// 顺序错峰闪现：progress = 1 - pulse 从 0 到 1。每格按索引延迟逐个弹出
private fun calStaggeredDot(index: Int): (Float, Float) -> PartTransform = { pulse, _ ->
    if (pulse <= 0f) {
        PartTransform()
    } else {
        val progress = (1f - pulse).coerceIn(0f, 1f)
        val start = index * 0.10f
        if (progress < start) {
            PartTransform(scaleX = 0f, scaleY = 0f, alpha = 0f)
        } else {
            val u = ((progress - start) / (1f - start)).coerceIn(0f, 1f)
            val pop = sin(u * PI.toFloat())
            val scale = u + 0.25f * pop
            val alpha = (u * 2.5f).coerceIn(0f, 1f)
            PartTransform(scaleX = scale, scaleY = scale, alpha = alpha)
        }
    }
}

// ═══════ 今日 Hop: 外框保持稳定，中间代表今日的方格从无到有闪出 ═══════

private const val todayFrameAndHoles =
    "M19,3h-1V1h-2v2H8V1H6v2H5c-1.11,0 -2,0.9 -2,2v14c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2z" +
        "M19,19H5V9h14v10z" +
        "M19,7H5V5h14v2z"

private const val todayCenterSquare = "M7,11h5v5H7z"

private fun todayCenterSquareMotion(pulse: Float): PartTransform {
    if (pulse <= 0f) return PartTransform()
    val progress = (1f - pulse).coerceIn(0f, 1f)
    val pop = sin(progress * PI.toFloat())
    val scale = progress + 0.35f * pop
    val alpha = (progress * 2.5f).coerceIn(0f, 1f)
    return PartTransform(scaleX = scale, scaleY = scale, alpha = alpha)
}

// ═══════ 我的 Jelly: 固定下半身，人头左右摇晃 ═══════

private const val personHeadRing =
    "M12,4C9.79,4 8,5.79 8,8s1.79,4 4,4 4,-1.79 4,-4 -1.79,-4 -4,-4z" +
        "M12,6c1.1,0 2,0.9 2,2s-0.9,2 -2,2 -2,-0.9 -2,-2 0.9,-2 2,-2z"
private const val personBody =
    "M12,14c-2.67,0 -8,1.34 -8,4v2h16v-2c0,-2.66 -5.33,-4 -8,-4z" +
        "M12,16c2.7,0 5.8,1.29 6,2L6,18c0.23,-0.72 3.31,-2 6,-2z"

private fun personHeadMotion(pulse: Float): PartTransform {
    if (pulse <= 0f) return PartTransform()
    val progress = (1f - pulse).coerceIn(0f, 1f)
    val decay = pulse
    val rot = 18f * decay * sin(progress * 4f * PI.toFloat())
    val tx = 1.2f * decay * sin(progress * 4f * PI.toFloat())
    return PartTransform(rotationZ = rot, translationUnitsX = tx)
}

// ═══════ 管理 GearSpin: 齿环(内齿孔焊死)跟累计弧度转, 核(圆+孔)随脉冲泵 ═══════

private const val settingsRing =
    "M19.43,12.98c0.04,-0.32 0.07,-0.64 0.07,-0.98 0,-0.34 -0.03,-0.66 -0.07,-0.98l2.11,-1.65c0.19,-0.15 0.24,-0.42 0.12,-0.64" +
        "l-2,-3.46c-0.09,-0.16 -0.26,-0.25 -0.44,-0.25 -0.06,0 -0.12,0.01 -0.17,0.03l-2.49,1c-0.52,-0.4 -1.08,-0.73 -1.69,-0.98l-0.38,-2.65" +
        "C14.46,2.18 14.25,2 14,2h-4c-0.25,0 -0.46,0.18 -0.49,0.42l-0.38,2.65c-0.61,0.25 -1.17,0.59 -1.69,0.98l-2.49,-1c-0.06,-0.02 -0.12,-0.03" +
        " -0.18,-0.03 -0.17,0 -0.34,0.09 -0.43,0.25l-2,3.46c-0.13,0.22 -0.07,0.49 0.12,0.64l2.11,1.65c-0.04,0.32 -0.07,0.65 -0.07,0.98" +
        " 0,0.33 0.03,0.66 0.07,0.98l-2.11,1.65c-0.19,0.15 -0.24,0.42 -0.12,0.64l2,3.46c0.09,0.16 0.26,0.25 0.44,0.25 0.06,0 0.12,-0.01" +
        " 0.17,0.03l2.49,-1c0.52,0.4 1.08,0.73 1.69,0.98l0.38,2.65c0.03,0.24 0.24,0.42 0.49,0.42h4c0.25,0 0.46,-0.18 0.49,-0.42l0.38,-2.65" +
        "c0.61,-0.25 1.17,-0.59 1.69,-0.98l2.49,1c0.06,0.02 0.12,0.03 0.18,0.03 0.17,0 0.34,-0.09 0.43,-0.25l2,-3.46c0.12,-0.22 0.07,-0.49" +
        " -0.12,-0.64l-2.11,-1.65z" +
        "M17.45,11.27c0.04,0.31 0.05,0.52 0.05,0.73 0,0.21 -0.02,0.43 -0.05,0.73l-0.14,1.13 0.89,0.7 1.08,0.84" +
        " -0.7,1.21 -1.27,-0.51 -1.04,-0.42 -0.9,0.68c-0.43,0.32 -0.84,0.56 -1.25,0.73l-1.06,0.43 -0.16,1.13 -0.2,1.35h-1.4l-0.19,-1.35" +
        " -0.16,-1.13 -1.06,-0.43c-0.43,-0.18 -0.83,-0.41 -1.23,-0.71l-0.91,-0.7 -1.06,0.43 -1.27,0.51 -0.7,-1.21 1.08,-0.84 0.89,-0.7" +
        " -0.14,-1.13c-0.03,-0.31 -0.05,-0.54 -0.05,-0.74s0.02,-0.43 0.05,-0.73l0.14,-1.13 -0.89,-0.7 -1.08,-0.84 0.7,-1.21 1.27,0.51" +
        " 1.04,0.42 0.9,-0.68c0.43,-0.32 0.84,-0.56 1.25,-0.73l1.06,-0.43 0.16,-1.13 0.2,-1.35h1.39l0.19,1.35 0.16,1.13 1.06,0.43" +
        "c0.43,0.18 0.83,0.41 1.23,0.71l0.91,0.7 1.06,-0.43 1.27,-0.51 0.7,1.21 -1.07,0.85 -0.89,0.7 0.14,1.13z"
private const val settingsCore =
    "M12,8c-2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4 -1.79,-4 -4,-4z" +
        "M12,14c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2 2,0.9 2,2 -0.9,2 -2,2z"

/**
 * 部件表真源。null = 无部件拆分(None 键, 行为与改造前逐位一致)。
 * 各键峰值时刻部件变换互不相同 = "各自动作"契约 (NavIconPartsTest 锁)。
 */
internal fun navIconPartsFor(key: NavIconMotionKey): List<NavIconPart>? = when (key) {
    NavIconMotionKey.PageFlip -> listOf(
        NavIconPart(calFrameAndHoles, pivotX = 12f, pivotY = 12f) { _, _ -> PartTransform() },
        NavIconPart(calDot1, pivotX = 8f, pivotY = 13f, motion = calStaggeredDot(0)),
        NavIconPart(calDot2, pivotX = 12f, pivotY = 13f, motion = calStaggeredDot(1)),
        NavIconPart(calDot3, pivotX = 16f, pivotY = 13f, motion = calStaggeredDot(2)),
        NavIconPart(calDot4, pivotX = 8f, pivotY = 17f, motion = calStaggeredDot(3)),
        NavIconPart(calDot5, pivotX = 12f, pivotY = 17f, motion = calStaggeredDot(4)),
        NavIconPart(calDot6, pivotX = 16f, pivotY = 17f, motion = calStaggeredDot(5)),
    )

    NavIconMotionKey.Hop -> listOf(
        NavIconPart(todayFrameAndHoles, pivotX = 12f, pivotY = 12f) { _, _ -> PartTransform() },
        NavIconPart(todayCenterSquare, pivotX = 9.5f, pivotY = 13.5f) { p, _ -> todayCenterSquareMotion(p) },
    )

    NavIconMotionKey.GearSpin -> listOf(
        NavIconPart(settingsRing, pivotX = 12f, pivotY = 12f) { _, s ->
            PartTransform(rotationZ = s * (180f / PI.toFloat()))
        },
        NavIconPart(settingsCore, pivotX = 12f, pivotY = 12f) { p, _ ->
            val k = sin(p * PI.toFloat())
            PartTransform(scaleX = 1f + 0.12f * k, scaleY = 1f + 0.12f * k)
        },
    )

    NavIconMotionKey.Jelly -> listOf(
        NavIconPart(personHeadRing, pivotX = 12f, pivotY = 10f) { p, _ -> personHeadMotion(p) },
        NavIconPart(personBody, pivotX = 12f, pivotY = 20f) { _, _ -> PartTransform() },
    )

    NavIconMotionKey.None -> null
}

/** 子路径拆分 (M/m 开新段) — 供契约测试做集合覆盖比对。 */
internal fun splitSubpaths(pathData: String): List<String> {
    val out = ArrayList<String>()
    val cur = StringBuilder()
    for (c in pathData) {
        if ((c == 'M' || c == 'm') && cur.isNotBlank()) {
            out += cur.toString()
            cur.setLength(0)
        }
        cur.append(c)
    }
    if (cur.isNotBlank()) out += cur.toString()
    return out
}

// ═══════ 渲染层: 部件表 → Canvas 逐件变换绘制 ═══════

/**
 * 部件级图标(替代静态 Icon)。路由规则:
 * - driver 有部件表([navIconPartsFor]) → 逐件 Canvas 绘制, 动效来自 part.motion;
 *   整层 [navIconMotion] 变换**不再叠加**(部件动作取代整体动作, 防双重变换)。
 * - 其余(None / 无 driver) → 原 [Icon] + [navIconMotion], 逐位与改造前一致。
 *
 * 调用方约定: [modifier] 不含 [navIconMotion](本函数按分支决定是否叠加);
 * [iconSize] 同时给出 Icon 分支与 Canvas 分支的渲染盒, 两分支逐位同尺寸。
 *
 * 变换数学: 部件几何在 24 视口域, 画布尺寸 layerPx → k=layerPx/24。
 * 每件 CTM(外→内) = 整体 k 缩放 → 平移(视口单位) → 旋转(pivot) → 缩放(pivot);
 * clip 盒在部件局部 24 域给出, 随该件平移/缩放 → 裁窗跟着部件走(Hop 撕开感来源)。
 * pulse/spin 在 draw lambda 内直读 driver(Animatable.value) → 仅本 Canvas 节点逐帧重绘。
 */
@Composable
internal fun NavPartIcon(
    driver: NavIconDriver?,
    fallback: ImageVector,
    iconSize: Dp,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    contentDescription: String? = null,
) {
    val parts = driver?.let { navIconPartsFor(it.key) }
    if (parts.isNullOrEmpty()) {
        Icon(
            imageVector = fallback,
            contentDescription = contentDescription,
            tint = tint,
            modifier = modifier
                .navIconMotion(driver)
                .size(iconSize),
        )
        return
    }
    val paths = remember(parts) { parts.map { PathParser().parsePathString(it.pathData).toPath() } }
    Box(
        modifier = modifier
            .size(iconSize)
            .then(
                if (contentDescription == null) Modifier
                else Modifier.semantics { this.contentDescription = contentDescription }
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val k = size.minDimension / 24f
            val pulse = driver.pulsePhase()
            val spin = driver.spinRadians()
            for ((i, part) in parts.withIndex()) {
                val t = part.motion(pulse, spin)
                if (t.alpha <= 0f) continue
                val c = drawContext.canvas
                c.save()
                // androidx Canvas 只有绕原点 API → pivot 版用 translate(±p) 包夹合成。
                // clip 在部件变换之后给出 → 裁窗刚性跟随部件。
                c.scale(k, k)
                if (t.translationUnitsX != 0f || t.translationUnitsY != 0f) {
                    c.translate(t.translationUnitsX, t.translationUnitsY)
                }
                if (t.rotationZ != 0f) {
                    c.translate(part.pivotX, part.pivotY)
                    c.rotate(t.rotationZ)
                    c.translate(-part.pivotX, -part.pivotY)
                }
                if (t.scaleX != 1f || t.scaleY != 1f) {
                    c.translate(part.pivotX, part.pivotY)
                    c.scale(t.scaleX, t.scaleY)
                    c.translate(-part.pivotX, -part.pivotY)
                }
                part.clipViewport?.let { r -> c.clipRect(r.left, r.top, r.right, r.bottom) }
                drawPath(paths[i], tint, alpha = t.alpha)
                c.restore()
            }
        }
    }
}
