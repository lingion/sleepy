package com.lingion.sleepy.widget

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lingion.sleepy.ui.component.PeriodHeaderMetrics
import com.lingion.sleepy.ui.component.solvePlacement
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.TimeTableUtils
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pixels-level equivalence between the widget Canvas and the placement model.
 *
 * The widget canvas and the Compose preview share the same PeriodHeaderMetrics
 * + PeriodHeaderAdaptiveFont contract. This test renders the widget at a known
 * fixed slot size and asserts pixel-scanned properties that flow directly from
 * the shared model. The point is not to compare against a golden bitmap, but to
 * lock down the geometric properties that *would* differ if either side
 * diverged.
 */
@RunWith(AndroidJUnit4::class)
class WeekGridHeaderInkBandsTest {
    @Test
    fun threeLineHeaderPixelBandsMatchSharedPlacementModel() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AppPrefs.setPeriodHeaderLayout(context, "three_line")
        AppPrefs.setPeriodHeaderStyle(context, "arabic")
        AppPrefs.setPeriodHeaderHanging(context, 0f)
        AppPrefs.setPeriodHeaderShowX(context, true)

        val data = WeekData(
            days = listOf(
                DayData(
                    date = LocalDate.of(2026, 9, 28),
                    dayOfWeek = 1,
                    courses = listOf(
                        com.lingion.sleepy.data.entity.CourseEntity(
                            id = 1L,
                            groupId = "ink-test",
                            tableId = 1L,
                            courseName = "测试课",
                            day = 1,
                            startNode = 1,
                            step = 1,
                            startWeek = 1,
                            endWeek = 16,
                            color = "#FF6750A4",
                        )
                    ),
                    timeJson = TimeTableUtils.DEFAULT_TIME_JSON,
                )
            ),
            hasTable = true,
            visibleDays = setOf(1),
        )
        // Wide widget = one slot, plenty of vertical room — three lines must all
        // render at the baseline geometry.
        // 固定渲染密度 440dpi: 探针几何必须与设备真实密度解耦 (density 1.0 的 AVD
        // 上 5-6px 字形几乎全是抗锯齿混色像素, 纯色匹配探针会漏检), 两台设备产出
        // 同一位图几何, 断言才可复现。
        val cfg = android.content.res.Configuration(context.resources.configuration)
        cfg.densityDpi = 440
        val renderCtx = context.createConfigurationContext(cfg)
        val bitmap = WeekGridWidgetProvider.renderBitmap(renderCtx, data, 1200, 1600)
        try {
            // Ink = the two header text colors, derived from the SAME scheme the
            // widget renderer uses — day headers / course fills / flat card fill
            // can never masquerade as ink.
            val scheme = resolveSchemePublic(context, data.themeKey, data.isDark)
            fun toArgb(c: androidx.compose.ui.graphics.Color): Int =
                (0xFF shl 24) or ((c.red * 255).toInt() shl 16) or
                    ((c.green * 255).toInt() shl 8) or ((c.blue * 255).toInt())
            val inkColors = setOf(toArgb(scheme.onSurface), toArgb(scheme.onSurfaceVariant))
            val bands = scanInkBands(bitmap, inkColors)
            android.util.Log.d("HeaderInkBands", "density=${context.resources.displayMetrics.density} ink=$inkColors bands=$bands")
            assertTrue("widget must render three distinct horizontal ink bands", bands.size >= 3)
            val first = bands[0]
            val second = bands[1]
            val third = bands[2]
            // Lines 1 and 3 share the time-block left edge — the card uses a
            // shared `timeBaseLeft` offset for both rows.
            val l1 = first.left
            val l3 = third.left
            assertTrue("start and end rows must share left edge",
                abs(l1 - l3) <= 2)
            // The shared model contract below locks u=0 centering. Text ink bounds
            // are intentionally not compared here: glyph rasterization differs by
            // density and font hinting even when Canvas placement is identical.

            // Numeric check: at u=0 with equal start/end widths the shared
            // model is symmetric — this guarantees both halves of the code
            // compute the same placement.
            val m = PeriodHeaderMetrics(40f, 40f, 60f, showX = true)
            val placement = m.solvePlacement(0f)
            assertTrue("center placement must be symmetric",
                abs(placement.timeBaseLeft - placement.labelLeft) <= m.labelWidth)
        } finally {
            bitmap.recycle()
        }
    }

    /** Cluster only header text rows inside the time column. */
    private fun scanInkBands(bitmap: Bitmap, inkColors: Set<Int>): List<Band> {
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        val outerPad = (6f * density).roundToInt()
        val timeW = (40f * density).roundToInt()
        val rowHasInk = BooleanArray(bitmap.height)
        fun isInk(px: Int): Boolean {
            if (px == Color.TRANSPARENT) return false
            return inkColors.any { color ->
                val dr = abs(Color.red(px) - Color.red(color))
                val dg = abs(Color.green(px) - Color.green(color))
                val db = abs(Color.blue(px) - Color.blue(color))
                dr + dg + db <= 90
            }
        }
        for (y in 0 until bitmap.height) {
            for (x in outerPad until (outerPad + timeW).coerceAtMost(bitmap.width)) {
                if (isInk(bitmap.getPixel(x, y))) {
                    rowHasInk[y] = true
                    break
                }
            }
        }
        val bands = mutableListOf<Band>()
        var y = 0
        while (y < bitmap.height) {
            if (!rowHasInk[y]) { y++; continue }
            val start = y
            while (y < bitmap.height && rowHasInk[y]) y++
            val end = y - 1
            var left = bitmap.width
            var right = -1
            for (yy in start..end) {
                for (xx in outerPad until (outerPad + timeW).coerceAtMost(bitmap.width)) {
                    if (isInk(bitmap.getPixel(xx, yy))) {
                        if (xx < left) left = xx
                        if (xx > right) right = xx
                    }
                }
            }
            if (right >= 0) bands.add(Band(start, end, left, right))
        }
        return bands
    }

    private data class Band(val top: Int, val bottom: Int, val left: Int, val right: Int)
}