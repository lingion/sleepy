package com.lingion.sleepy.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.graphics.toArgb
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.ConflictLayoutEngine
import com.lingion.sleepy.util.CourseColorUtil
import com.lingion.sleepy.util.CourseDisplayUtil
import com.lingion.sleepy.util.CourseHueStore
import com.lingion.sleepy.util.DateUtils
import com.lingion.sleepy.util.TimeTableUtils
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.json.JSONArray
import kotlin.math.floor

internal data class WideText(val text: String, val bounds: RectF, val baseline: Float, val size: Float)
internal data class WideCard(val course: CourseEntity, val date: LocalDate, val bounds: RectF, val title: WideText, val room: WideText)
internal data class WideGridLayout(
    val dates: List<LocalDate>, val periods: List<WidePeriod>, val window: PeriodWindow,
    val body: RectF, val cards: List<WideCard>, val dayExtent: Float, val periodExtent: Float,
)

/** This renderer belongs only to the explicit 4x2 grid receiver. */
internal object WeekGridWideRenderer {
    fun periods(timeJson: String): List<WidePeriod> = try {
        val rows = JSONArray(timeJson)
        (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            fun minute(key: String): Int? = runCatching {
                LocalTime.parse(row.optString(key, "").trim()).toSecondOfDay() / 60
            }.getOrNull()
            WidePeriod(row.optInt("node", index + 1), minute("start"), minute("end"))
        }.sortedBy { it.node }
    } catch (_: Exception) { emptyList() }

    fun capacity(context: Context, heightPx: Int): Int {
        val density = context.resources.displayMetrics.density
        val font = context.resources.configuration.fontScale.coerceAtLeast(1f)
        return floor((heightPx / density - 12f) / (36f * font)).toInt().coerceAtLeast(4)
    }

    fun layout(context: Context, data: WeekData, settings: WeekGridWideSettings, now: LocalDateTime, widthPx: Int, heightPx: Int): WideGridLayout {
        val d = context.resources.displayMetrics.density
        val font = context.resources.configuration.fontScale.coerceAtLeast(1f)
        val pad = 6f * d
        val dates = WeekGridWideWindowCore.visibleDates(now.toLocalDate(), settings.mode)
        val periods = periods(data.days.firstOrNull()?.timeJson.orEmpty())
        val vertical = settings.mode == WeekGridWideMode.VERTICAL
        val window = if (vertical) WeekGridWideWindowCore.visiblePeriods(periods, now.hour * 60 + now.minute, capacity(context, heightPx))
            else PeriodWindow(0, periods.size)
        val labelWidth = (if (vertical) 22f else 34f) * d
        val header = if (vertical) 0f else 34f * d
        val leftLabels = vertical || settings.labelSide == WeekGridWideLabelSide.LEFT
        val body = RectF(pad + if (leftLabels) labelWidth else 0f, pad + header,
            (widthPx - pad - if (leftLabels) 0f else labelWidth).coerceAtLeast(pad + labelWidth + d),
            (heightPx - pad).coerceAtLeast(pad + header + d))
        val dayExtent = (if (vertical) body.width() else body.height()) / dates.size
        val periodExtent = (if (vertical) body.height() else body.width()) / window.count.coerceAtLeast(1)
        val cards = mutableListOf<WideCard>()
        for ((dayIndex, date) in dates.withIndex()) {
            val day = data.days.firstOrNull { it.date == date } ?: continue
            for (lane in ConflictLayoutEngine.gridDayLanes(day.courses, day.timeJson)) {
                val course = lane.course
                val first = periods.indexOfFirst { it.node >= course.startNode }
                val end = periods.indexOfLast { it.node < course.startNode + course.step.coerceAtLeast(1) } + 1
                if (first < 0 || end <= first) continue
                val fractional = if (course.ownTime || course.isIrregularTime) {
                    TimeTableUtils.timeToFractionalRows(course.startTime, course.endTime, day.timeJson)
                } else null
                val start = maxOf(fractional?.first ?: first.toFloat(), window.startIndex.toFloat()) - window.startIndex
                val finish = minOf(fractional?.second ?: end.toFloat(), (window.startIndex + window.count).toFloat()) - window.startIndex
                if (finish <= start) continue
                val dayStart = dayIndex + lane.laneStartFraction
                val dayEnd = dayStart + lane.laneWidthFraction
                val rect = if (vertical) RectF(body.left + dayStart * dayExtent, body.top + start * periodExtent,
                    body.left + dayEnd * dayExtent, body.top + finish * periodExtent)
                else RectF(body.left + start * periodExtent, body.top + dayStart * dayExtent,
                    body.left + finish * periodExtent, body.top + dayEnd * dayExtent)
                rect.inset(d, d)
                if (rect.width() <= 0f || rect.height() <= 0f) continue
                val name = CourseDisplayUtil.displayName(course, AppPrefs.isWidgetUseAlias(context))
                val (title, room) = layoutText(rect, name, course.room, d, font)
                cards += WideCard(course, date, rect, title, room)
            }
        }
        return WideGridLayout(dates, periods, window, body, cards, dayExtent, periodExtent)
    }

    /** Always measured against the intersected card, never against its original full span. */
    private fun layoutText(rect: RectF, name: String, room: String, density: Float, font: Float): Pair<WideText, WideText> {
        val inset = minOf(3f * density, rect.width() * .08f, rect.height() * .08f)
        val inner = RectF(rect).apply { inset(inset, inset) }
        val roomHeight = if (room.isNotBlank()) inner.height() * .36f else 0f
        val titleArea = RectF(inner.left, inner.top, inner.right, inner.bottom - roomHeight)
        val roomArea = RectF(inner.left, inner.bottom - roomHeight, inner.right, inner.bottom)
        return textLine(name, titleArea, 11f * density * font) to textLine(room, roomArea, 8f * density * font)
    }

    private fun textLine(value: String, bounds: RectF, preferred: Float): WideText {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        paint.textSize = minOf(preferred, bounds.height() * .8f).coerceAtLeast(.1f)
        val firstGlyph = value.trimStart().let { if (it.isEmpty()) "" else it.substring(0, Character.charCount(it.codePointAt(0))) }
        if (firstGlyph.isNotEmpty()) {
            val glyphWidth = paint.measureText(firstGlyph)
            if (glyphWidth > bounds.width() && glyphWidth > 0f) paint.textSize *= bounds.width() / glyphWidth
        }
        val metrics = paint.fontMetrics
        val lineHeight = metrics.descent - metrics.ascent
        val maxLines = (bounds.height() / lineHeight).toInt().coerceAtLeast(1)
        var remaining = value.replace('\n', ' ')
        val lines = mutableListOf<String>()
        while (remaining.isNotEmpty() && lines.size < maxLines && bounds.width() > 0f) {
            if (lines.size == maxLines - 1) {
                val shortened = TextUtils.ellipsize(remaining, paint, bounds.width(), TextUtils.TruncateAt.END).toString()
                lines += if (shortened.any { it != '…' && !it.isWhitespace() }) shortened
                    else remaining.take(paint.breakText(remaining, true, bounds.width(), null))
                break
            }
            val length = paint.breakText(remaining, true, bounds.width(), null)
            if (length <= 0) break
            lines += remaining.take(length)
            remaining = remaining.drop(length)
        }
        val baseline = bounds.centerY() - lines.size.coerceAtLeast(1) * lineHeight / 2f - metrics.ascent
        return WideText(lines.joinToString("\n"), bounds, baseline, paint.textSize)
    }

    fun renderBitmap(context: Context, data: WeekData, settings: WeekGridWideSettings, now: LocalDateTime, widthPx: Int, heightPx: Int): Bitmap {
        if (settings.mode == WeekGridWideMode.ORIGINAL) {
            return WeekGridWidgetProvider.renderBitmap(context, data, widthPx, heightPx)
        }
        val geometry = layout(context, data, settings, now, widthPx, heightPx)
        val bitmap = Bitmap.createBitmap(widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val d = context.resources.displayMetrics.density
        val scheme = resolveSchemePublic(context, data.themeKey, data.isDark)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = scheme.surface.toArgb()
        canvas.drawRoundRect(RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat()), 16f * d, 16f * d, paint)
        val vertical = settings.mode == WeekGridWideMode.VERTICAL
        paint.color = scheme.onSurfaceVariant.toArgb()
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 9f * d
        if (!vertical) {
            for ((i, date) in geometry.dates.withIndex()) {
                val x = if (settings.labelSide == WeekGridWideLabelSide.LEFT) geometry.body.left / 2f else (geometry.body.right + widthPx) / 2f
                val y = geometry.body.top + (i + .5f) * geometry.dayExtent
                canvas.drawText(DateUtils.localizedDay(date.dayOfWeek.value, context), x, y - 2f * d, paint)
                paint.textSize = 8f * d
                canvas.drawText("${date.monthValue}/${date.dayOfMonth}", x, y + 9f * d, paint)
                paint.textSize = 9f * d
            }
        }
        for (i in 0 until geometry.window.count) {
            val period = geometry.periods[geometry.window.startIndex + i]
            if (vertical) {
                canvas.drawText(period.node.toString(), geometry.body.left / 2f, geometry.body.top + (i + .5f) * geometry.periodExtent + 3f * d, paint)
            } else {
                val x = geometry.body.left + (i + .5f) * geometry.periodExtent
                val cell = RectF(x - geometry.periodExtent / 2f, 6f * d, x + geometry.periodExtent / 2f, geometry.body.top)
                canvas.save(); canvas.clipRect(cell)
                canvas.drawText(period.node.toString(), x, 15f * d, paint)
                paint.textSize = minOf(7f * d, geometry.periodExtent / 3.3f)
                fun time(min: Int?) = min?.let { "%02d:%02d".format(it / 60, it % 60) }.orEmpty()
                canvas.drawText(time(period.startMinute), x, 24f * d, paint)
                canvas.drawText(time(period.endMinute), x, 33f * d, paint)
                paint.textSize = 9f * d; canvas.restore()
            }
        }
        if (AppPrefs.isGridShowSeparators(context)) {
            paint.color = scheme.surfaceVariant.toArgb(); paint.strokeWidth = .5f * d
            val days = geometry.dates.size
            for (i in 1 until days) {
                if (vertical) {
                    val x = geometry.body.left + i * geometry.dayExtent
                    canvas.drawLine(x, geometry.body.top, x, geometry.body.bottom, paint)
                } else {
                    val y = geometry.body.top + i * geometry.dayExtent
                    canvas.drawLine(geometry.body.left, y, geometry.body.right, y, paint)
                }
            }
        }
        val courses = data.days.flatMap { it.courses }
        for (card in geometry.cards) {
            val base = CourseColorUtil.pickCourseColorIntWithGroupRows(card.course, courses.filter { it.groupId == card.course.groupId },
                data.isDark, scheme.surfaceVariant.toArgb(), AppPrefs.isWidgetColorless(context), persistedHue = CourseHueStore.hueFor(card.course.groupId))
            val grey = data.days.firstOrNull { it.date == card.date }?.isGrey == true
            paint.color = base; paint.alpha = if (grey) 120 else 220
            canvas.drawRoundRect(card.bounds, minOf(6f * d, card.bounds.height() / 4), minOf(6f * d, card.bounds.height() / 4), paint)
            val luminance = Color.red(base) * .299 + Color.green(base) * .587 + Color.blue(base) * .114
            paint.color = if (luminance < 145) Color.WHITE else Color.rgb(29, 27, 32)
            paint.alpha = if (grey) 153 else 255
            if (grey && AppPrefs.getHolidayStyle(context) == "strikethrough") paint.flags = paint.flags or Paint.STRIKE_THRU_TEXT_FLAG
            for (line in listOf(card.title, card.room)) {
                canvas.save(); canvas.clipRect(line.bounds)
                paint.textSize = line.size
                val advance = paint.fontMetrics.descent - paint.fontMetrics.ascent
                line.text.split('\n').forEachIndexed { index, text ->
                    canvas.drawText(text, line.bounds.centerX(), line.baseline + index * advance, paint)
                }
                canvas.restore()
            }
            paint.flags = paint.flags and Paint.STRIKE_THRU_TEXT_FLAG.inv(); paint.alpha = 255
        }
        if (!data.hasTable) {
            paint.color = scheme.onSurface.toArgb(); paint.textSize = 12f * d
            canvas.drawText(context.getString(com.lingion.sleepy.R.string.widget_create_schedule), widthPx / 2f, heightPx / 2f, paint)
        }
        return bitmap
    }
}
