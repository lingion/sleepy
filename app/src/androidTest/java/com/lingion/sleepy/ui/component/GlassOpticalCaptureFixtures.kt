package com.lingion.sleepy.ui.component

import android.app.Activity
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.Window
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** These inputs are identical in light/dark mode so material changes remain separable. */
internal enum class OpticalBackground {
    fine_grid, color_stripes, tonal_blocks, synthetic_landscape;

    fun metadata() = JSONObject().put("id", name).put("input", when (this) {
        fine_grid -> "Opaque #F0F0F5, 8dp black/white fine grid, 0.5dp stroke"
        color_stripes -> "Opaque 18dp high-contrast red/yellow/green/cyan/blue/magenta stripes"
        tonal_blocks -> "Opaque 112dp alternating #101018/#F5F5F0 large blocks"
        synthetic_landscape -> "Procedural 720x1280 landscape v1, seed 0x51EE9: textured clouds, ridgelines, lake reflections and near shore; synthetic, not a photograph"
    })
}

@Composable
internal fun OpticalCaptureBackground(background: OpticalBackground) {
    if (background == OpticalBackground.synthetic_landscape) {
        val bitmap = remember { landscapeBitmap.asImageBitmap() }
        Image(bitmap, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        return
    }
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Color(0xFFF0F0F5))
        when (background) {
            OpticalBackground.fine_grid -> {
                val step = 8.dp.toPx()
                for (x in 0..ceil(size.width / step).toInt()) drawLine(
                    if (x % 2 == 0) Color.Black.copy(alpha = .55f) else Color.White,
                    Offset(x * step, 0f), Offset(x * step, size.height), .5f.dp.toPx())
                for (y in 0..ceil(size.height / step).toInt()) drawLine(
                    if (y % 2 == 0) Color.Black.copy(alpha = .55f) else Color.White,
                    Offset(0f, y * step), Offset(size.width, y * step), .5f.dp.toPx())
            }
            OpticalBackground.color_stripes -> {
                val palette = listOf(Color(0xFFF51D39), Color(0xFFFFD51C), Color(0xFF00CB69),
                    Color(0xFF00DCED), Color(0xFF1552F3), Color(0xFFF218DD))
                val step = 18.dp.toPx()
                for (x in 0..ceil(size.width / step).toInt()) drawRect(palette[x % palette.size],
                    Offset(x * step, 0f), Size(step, size.height))
            }
            OpticalBackground.tonal_blocks -> {
                val step = 112.dp.toPx()
                for (y in 0..ceil(size.height / step).toInt()) for (x in 0..ceil(size.width / step).toInt())
                    drawRect(if ((x + y) % 2 == 0) Color(0xFF101018) else Color(0xFFF5F5F0),
                        Offset(x * step, y * step), Size(step, step))
            }
            OpticalBackground.synthetic_landscape -> Unit
        }
    }
}

// The immutable input is reused across all fixtures rather than regenerated per scene.
private val landscapeBitmap by lazy { syntheticLandscape() }

/** A deterministic textured scene, not a gradient relabelled as a photo. */
private fun syntheticLandscape(): Bitmap {
    val width = 720
    val height = 1280
    val pixels = IntArray(width * height)
    fun noise(x: Int, y: Int): Double {
        var n = x * 374761393 + y * 668265263 + 0x51EE9
        n = (n xor (n ushr 13)) * 1274126177
        return ((n xor (n ushr 16)) and 65535) / 65535.0
    }
    fun ridge(u: Double, phase: Double) = .36 + .035 * sin(u * 15 + phase) +
        .045 * sin(u * 5.7 + phase) + .012 * sin(u * 43 - phase)
    for (y in 0 until height) for (x in 0 until width) {
        val u = x.toDouble() / width
        val v = y.toDouble() / height
        val grain = (noise(x, y) - .5) * .035
        val skyY = if (v < .53) v else (1.06 - v + .003 * sin(v * 550 + u * 45)).coerceAtLeast(0.0)
        val cloud = (sin(u * 17 + skyY * 13) + cos(u * 37 - skyY * 19) + sin(u * 71 + skyY * 46)) / 3
        val cloudMask = ((cloud + .3) * 1.8).coerceIn(0.0, 1.0) * exp(-((skyY - .22) / .14).pow(2))
        var r = .19 + skyY * .95 + cloudMask * .42
        var g = .43 + skyY * .63 + cloudMask * .31
        var b = .72 + skyY * .2 + cloudMask * .13
        if (skyY > ridge(u, .8)) {
            r = .23 + skyY * .20; g = .32 + skyY * .17; b = .38 + skyY * .15
        }
        if (skyY > ridge(u, 3.6) + .07) {
            val crag = noise(x / 3, (skyY * height).toInt() / 2) * .12
            r = .11 + crag; g = .22 + crag; b = .23 + crag * .7
        }
        if (v >= .53) {
            val ripple = sin(v * 900 + u * 38) * .025 + noise(x / 5, y) * .035
            r = r * .62 + ripple; g = g * .80 + ripple; b = b * .84 + ripple
        }
        val shore = .86 + .028 * sin(u * 8) + .02 * sin(u * 29)
        if (v > shore) {
            val rock = noise(x / 4, y / 4) * .16 + .055 * sin(u * 91 + v * 71)
            r = .21 + rock; g = .19 + rock * .9; b = .13 + rock * .75
        }
        // Near foreground trees contain fine silhouettes as well as broad tonal areas.
        val tree = (u < .16 || u > .89) && v > .50 && v < .89 &&
            abs((u * 34 % 1) - .5) < ((v * 37 % 1) * .28 + .08)
        if (tree) { r = .045; g = .09; b = .065 }
        fun channel(value: Double) = ((value + grain).coerceIn(0.0, 1.0) * 255).toInt()
        pixels[y * width + x] = android.graphics.Color.rgb(channel(r), channel(g), channel(b))
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}

internal data class OpticalCaptureWindow(val view: View, val root: View, val window: Window?) {
    companion object {
        fun from(view: View): OpticalCaptureWindow {
            var parent = view.parent
            while (parent != null) {
                if (parent is DialogWindowProvider) return OpticalCaptureWindow(view, view.rootView, parent.window)
                parent = parent.parent
            }
            var context = view.context
            while (context !is Activity && context is ContextWrapper) context = context.baseContext
            return OpticalCaptureWindow(view, view.rootView, (context as Activity).window)
        }

        /** PopupLayout has an attached surface but does not expose an android.view.Window. */
        fun fromPopup(view: View): OpticalCaptureWindow {
            check(Build.VERSION.SDK_INT >= 34) { "Actual Popup PixelCopy requires API 34" }
            return OpticalCaptureWindow(view, view.rootView, null)
        }
    }

    fun origin(): IntArray = IntArray(2).also { result ->
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.getLocationInWindow(result) }
    }

    fun screenOrigin(): IntArray = IntArray(2).also { result ->
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.getLocationOnScreen(result) }
    }

    fun capture(): Bitmap {
        val committed = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            root.viewTreeObserver.registerFrameCommitCallback { committed.countDown() }
            root.postInvalidateOnAnimation()
        }
        check(committed.await(5, TimeUnit.SECONDS)) { "HWUI did not commit the optical capture" }
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        val copied = CountDownLatch(1)
        var result = PixelCopy.ERROR_UNKNOWN
        if (window != null) {
            PixelCopy.request(window, bitmap, { result = it; copied.countDown() }, Handler(Looper.getMainLooper()))
        } else {
            check(Build.VERSION.SDK_INT >= 34) { "Actual Popup PixelCopy requires API 34" }
            // Public API uses this attached View's own ViewRoot surface, never its Activity surface.
            val request = PixelCopy.Request.Builder.ofWindow(root).setDestinationBitmap(bitmap).build()
            PixelCopy.request(request, { command -> Handler(Looper.getMainLooper()).post(command) }) {
                result = it.status
                copied.countDown()
            }
        }
        check(copied.await(5, TimeUnit.SECONDS) && result == PixelCopy.SUCCESS) { "Optical PixelCopy failed: $result" }
        return bitmap
    }
}

internal fun Bitmap.saveOpticalPng(file: java.io.File) {
    file.outputStream().use { check(compress(Bitmap.CompressFormat.PNG, 100, it)) { "PNG encoding failed: $file" } }
}

internal fun opticalScreenCapture(): Bitmap = checkNotNull(
    InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot(),
) { "Composited display screenshot unavailable" }

internal fun writeOpticalMetricsCsv(file: java.io.File, captures: JSONArray) {
    file.bufferedWriter().use { csv ->
        csv.appendLine("scene,background,theme,phase,tag,source,region,baseline_comparable,samples,mean_r,mean_g,mean_b,linear_luminance,luminance_stddev,hsv_saturation,horizontal_luminance_gradient,channel_gradient_separation,mean_abs_srgb_delta,saturation_delta")
        for (i in 0 until captures.length()) {
            val capture = captures.getJSONObject(i)
            val measurements = capture.getJSONArray("measurements")
            for (j in 0 until measurements.length()) {
                val measurement = measurements.getJSONObject(j)
                for (source in listOf("window", "composited_display", "optical_observations")) {
                    val observation = measurement.optJSONObject(source) ?: continue
                    for (region in listOf("center_half_size_rect", "edge_12_percent_rect_band")) {
                        val values = observation.getJSONObject(region)
                        val rgb = values.getJSONArray("mean_srgb")
                        val cells = listOf(capture.optString("scene"), capture.optString("background"),
                            capture.optString("theme"), capture.optString("phase"), measurement.getString("tag"),
                            source, region, observation.getBoolean("baseline_comparable"), values.getInt("sample_count"),
                            rgb.get(0), rgb.get(1), rgb.get(2), values.get("mean_linear_luminance"),
                            values.get("luminance_stddev"), values.get("mean_hsv_saturation"),
                            values.get("horizontal_luminance_gradient"), values.get("channel_gradient_separation"),
                            values.get("mean_abs_srgb_delta_from_baseline"), values.get("saturation_delta_from_baseline"))
                        csv.appendLine(cells.joinToString(",") { "\"${it.toString().replace("\"", "\"\"")}\"" })
                    }
                }
            }
        }
    }
}

/** Descriptive image statistics, never a pass threshold for matching Apple's optics. */
internal fun opticalRegionMetrics(bitmap: Bitmap, baseline: Bitmap?, region: Rect): JSONObject {
    val left = floor(region.left).toInt().coerceIn(0, bitmap.width - 1)
    val top = floor(region.top).toInt().coerceIn(0, bitmap.height - 1)
    val right = ceil(region.right).toInt().coerceIn(left + 1, bitmap.width)
    val bottom = ceil(region.bottom).toInt().coerceIn(top + 1, bitmap.height)
    val reference = baseline?.takeIf { it.width == bitmap.width && it.height == bitmap.height }
    val band = max(1, (min(right - left, bottom - top) * .12).toInt())
    val center = Rect(left + (right - left) * .25f, top + (bottom - top) * .25f,
        right - (right - left) * .25f, bottom - (bottom - top) * .25f)
    fun sample(edge: Boolean): JSONObject {
        var count = 0; var brightness = 0.0; var squares = 0.0; var saturation = 0.0
        var baselineSaturation = 0.0; var delta = 0.0; var gradient = 0.0; var channelSeparation = 0.0
        val rgb = DoubleArray(3)
        fun channels(color: Int) = doubleArrayOf(((color shr 16) and 255) / 255.0,
            ((color shr 8) and 255) / 255.0, (color and 255) / 255.0)
        fun hsvSaturation(c: DoubleArray): Double { val hi = c.max(); return if (hi == 0.0) 0.0 else (hi - c.min()) / hi }
        fun luma(c: DoubleArray): Double {
            fun linear(v: Double) = if (v <= .04045) v / 12.92 else ((v + .055) / 1.055).pow(2.4)
            return .2126 * linear(c[0]) + .7152 * linear(c[1]) + .0722 * linear(c[2])
        }
        for (y in top until bottom) for (x in left until right) {
            val included = if (edge) min(min(x - left, right - 1 - x), min(y - top, bottom - 1 - y)) < band
                else center.contains(Offset(x.toFloat(), y.toFloat()))
            if (!included) continue
            val c = channels(bitmap.getPixel(x, y)); val light = luma(c)
            count++; brightness += light; squares += light * light; saturation += hsvSaturation(c)
            for (i in 0..2) rgb[i] += c[i]
            val adjacent = channels(bitmap.getPixel(min(x + 1, right - 1), y))
            val gradients = DoubleArray(3) { abs(c[it] - adjacent[it]) }
            gradient += abs(light - luma(adjacent))
            channelSeparation += gradients.max() - gradients.min()
            if (reference != null) {
                val b = channels(reference.getPixel(x, y)); baselineSaturation += hsvSaturation(b)
                delta += c.indices.sumOf { abs(c[it] - b[it]) } / 3
            }
        }
        val n = count.coerceAtLeast(1)
        return JSONObject().put("sample_count", count).put("mean_srgb", JSONArray(rgb.map { it / n }))
            .put("mean_linear_luminance", brightness / n)
            .put("luminance_stddev", sqrt(max(0.0, squares / n - (brightness / n).pow(2))))
            .put("mean_hsv_saturation", saturation / n).put("horizontal_luminance_gradient", gradient / n)
            .put("channel_gradient_separation", channelSeparation / n)
            .put("mean_abs_srgb_delta_from_baseline", if (reference == null) JSONObject.NULL else delta / n)
            .put("saturation_delta_from_baseline", if (reference == null) JSONObject.NULL else (saturation - baselineSaturation) / n)
    }
    return JSONObject().put("window_rect_px", JSONArray(listOf(left, top, right, bottom)))
        .put("center_half_size_rect", sample(false)).put("edge_12_percent_rect_band", sample(true))
        .put("baseline_comparable", reference != null)
        .put("limits", "Rectangular observed regions include content, masks, shadows or touch insets. Color gradients are descriptive and do not prove chromatic dispersion, transparency, or parity. PixelCopy reads the named window surface, not a physical display or composited screen.")
}
