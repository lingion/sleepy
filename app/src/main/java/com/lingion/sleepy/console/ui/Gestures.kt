package com.lingion.sleepy.console.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * ui-ops 手势派发 — SPEC §4：语义操作优先（tag 命中注册 View 直接派发），坐标兜底；
 * back/home/recents 走按键事件语义。**不用 AccessibilityService**（SPEC §4 裁决：
 * 需用户态授权且无法全自动开启）。
 *
 * 线程模型：所有输入注入收敛到主线程（[runMain] + latch），HTTP worker 等结果；
 * 超时/异常 = false，调用方 handler 报 500，绝不无声吞掉。
 *
 * 坐标约定：请求与 [Bounds] 同为窗口坐标；派发前换算成目标 View 局部坐标。
 * MotionEvent 时间轴刻意合成（downTime + 递增 eventTime），让滚动具备真实
 * 速度语义（fling 依赖事件时间差）。
 */
class Gestures(private val activity: Activity) {

    private val main = Handler(Looper.getMainLooper())

    fun tap(node: UiNode?, x: Int, y: Int, longPressMs: Long?, doubleTap: Boolean): Boolean {
        // 语义优先：tag 命中且带回注册 View → 直接对该 View 派发；
        // 否则 hitTest 找最深可见视图，再退 decor。
        val view = node?.let { TagRegistry.clickableOf(it) }
            ?: hitTest(activity.window.decorView, x, y)
            ?: activity.window.decorView
        val (px, py) = toLocal(view, x, y)
        return runMain(2500) {
            when {
                doubleTap -> {
                    downUp(view, px, py, 0L, 60L)
                    downUp(view, px, py, 120L, 180L)
                }
                longPressMs != null && longPressMs > 0 ->
                    downUp(view, px, py, 0L, longPressMs + 30L)
                else -> downUp(view, px, py, 0L, 40L)
            }
            true
        }
    }

    /** 一次按下-抬起；holdMs 用事件时间轴表达（长按不阻塞主线程）。 */
    private fun downUp(view: View, lx: Float, ly: Float, startDelayMs: Long, holdMs: Long) {
        val down = System.currentTimeMillis() + startDelayMs
        fun post(at: Long, action: Int) {
            val e = MotionEvent.obtain(down, at, action, lx, ly, 0)
            view.postDelayed({ view.dispatchTouchEvent(e) }, at - System.currentTimeMillis())
        }
        post(down, MotionEvent.ACTION_DOWN)
        post(down + holdMs, MotionEvent.ACTION_UP)
    }

    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long): Boolean =
        runMain(durationMs + 3000) {
            val decor = activity.window.decorView
            val dur = durationMs.coerceIn(50L, 1500L)
            val steps = (dur / 16L).coerceAtLeast(4L).toInt()
            val down = System.currentTimeMillis()
            val send: (Long, Int, Float, Float) -> Unit = { t, action, mx, my ->
                val e = MotionEvent.obtain(down, t, action, mx, my, 0)
                decor.dispatchTouchEvent(e)
                e.recycle()
            }
            send(down, MotionEvent.ACTION_DOWN, x1.toFloat(), y1.toFloat())
            for (i in 1..steps) {
                val f = i.toFloat() / steps
                send(down + (dur * f).toLong(), MotionEvent.ACTION_MOVE,
                    x1 + (x2 - x1) * f, y1 + (y2 - y1) * f)
            }
            send(down + dur + 10, MotionEvent.ACTION_UP, x2.toFloat(), y2.toFloat())
            true
        }

    /**
     * 滚动 = 目标区域中心的方向性 swipe。
     * 手指方向与内容方向相反：内容向下滚 = 手指向上划。
     */
    fun scroll(direction: String, amount: Int, node: UiNode?): Boolean {
        val decor = activity.window.decorView
        val b = node?.bounds ?: Bounds(0, 0, decor.width, decor.height)
        val cx = b.centerX
        val cy = b.centerY
        val dist = amount.coerceIn(50, 2000)
        return when (direction) {
            "down" -> swipe(cx, cy + dist / 2, cx, cy - dist / 2, 220L)
            "up" -> swipe(cx, cy - dist / 2, cx, cy + dist / 2, 220L)
            "left" -> swipe(cx + dist / 2, cy, cx - dist / 2, cy, 220L)
            else -> swipe(cx - dist / 2, cy, cx + dist / 2, cy, 220L) // right
        }
    }

    /** 文本输入：命中 EditText 直接追加；否则对焦点视图逐字符送按键事件。 */
    fun inputText(text: String, node: UiNode?): Boolean {
        if (text.isEmpty()) return false
        val decor = activity.window.decorView
        val target: View? = node?.let {
            hitTest(decor, it.bounds.centerX, it.bounds.centerY)
        } ?: decor.findFocus()
        if (target == null) return false
        if (target is EditText) {
            return runMain(1500) {
                target.append(text)
                true
            }
        }
        return runMain(3000) {
            for (ch in text) {
                val kc = keyCodeForChar(ch) ?: continue
                val meta = if (ch.isLetter() && ch.isUpperCase()) KeyEvent.META_SHIFT_ON else 0
                target.dispatchKeyEvent(KeyEvent(0L, System.currentTimeMillis(), KeyEvent.ACTION_DOWN, kc, 0, meta))
                target.dispatchKeyEvent(KeyEvent(0L, System.currentTimeMillis(), KeyEvent.ACTION_UP, kc, 0, meta))
            }
            true
        }
    }

    /** 逐字符按键码（keyCodeFromChars 是 API 34，minSdk 26 不可用 → 本地常用字符表）。 */
    private fun keyCodeForChar(ch: Char): Int? {
        val upper = ch.uppercaseChar()
        return when {
            upper in 'A'..'Z' -> KeyEvent.KEYCODE_A + (upper - 'A')
            ch in '0'..'9' -> KeyEvent.KEYCODE_0 + (ch - '0')
            ch == ' ' -> KeyEvent.KEYCODE_SPACE
            ch == '.' -> KeyEvent.KEYCODE_PERIOD
            ch == ',' -> KeyEvent.KEYCODE_COMMA
            ch == '\n' -> KeyEvent.KEYCODE_ENTER
            ch == '\t' -> KeyEvent.KEYCODE_TAB
            ch == '-' -> KeyEvent.KEYCODE_MINUS
            ch == '_' -> KeyEvent.KEYCODE_MINUS
            else -> null
        }
    }

    /**
     * 系统键：back 走 OnBackPressedDispatcher（导航栈语义正确，含预测式返回入口）；
     * home/recents 向窗口派发系统级 KeyEvent（KEYCODE_HOME/APP_SWITCH 由窗口派发
     * 即触达系统行为，无需更高权限）。其余 key 用显式 keyCode。
     */
    fun systemKey(key: String, keyCode: Int): Boolean = when (key) {
        "back" -> runMain(1500) {
            // ComponentActivity（本 App 的 MainActivity 即是）走 dispatcher，导航栈语义正确；
            // 非 ComponentActivity 退化为窗口派发 BACK 键（等价旧 onBackPressed）。
            val comp = activity as? androidx.activity.ComponentActivity
            if (comp != null) comp.onBackPressedDispatcher.onBackPressed()
            else {
                val now = System.currentTimeMillis()
                val decor = activity.window.decorView
                decor.dispatchKeyEvent(KeyEvent(0L, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0))
                decor.dispatchKeyEvent(KeyEvent(0L, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0))
            }
            true
        }
        else -> runMain(1500) {
            val kc = when (key) {
                "home" -> KeyEvent.KEYCODE_HOME
                "recents" -> KeyEvent.KEYCODE_APP_SWITCH
                else -> keyCode
            }
            val now = System.currentTimeMillis()
            val decor = activity.window.decorView
            decor.dispatchKeyEvent(KeyEvent(0L, now, KeyEvent.ACTION_DOWN, kc, 0))
            decor.dispatchKeyEvent(KeyEvent(0L, now, KeyEvent.ACTION_UP, kc, 0))
            true
        }
    }

    // ---- 内部 ---------------------------------------------------------------

    private fun toLocal(view: View, wx: Int, wy: Int): Pair<Float, Float> {
        val loc = IntArray(2)
        view.getLocationInWindow(loc)
        return Pair((wx - loc[0]).toFloat(), (wy - loc[1]).toFloat())
    }

    /** 命中测试：最深可见视图（坐标兜底与 input 目标定位共用）。 */
    private fun hitTest(root: View, wx: Int, wy: Int): View? {
        val rect = Rect()
        fun walk(v: View): View? {
            if (v.visibility != View.VISIBLE || v.alpha < 0.1f) return null
            v.getGlobalVisibleRect(rect)
            if (!rect.contains(wx, wy)) return null
            if (v is ViewGroup) {
                for (i in v.childCount - 1 downTo 0) {
                    val hit = walk(v.getChildAt(i))
                    if (hit != null) return hit
                }
            }
            if (v.isClickable || v is EditText) return v
            return null
        }
        return walk(root)
    }

    private fun runMain(timeoutMs: Long, block: () -> Boolean): Boolean {
        val latch = CountDownLatch(1)
        var ok = false
        main.post {
            try {
                ok = block()
            } catch (_: Throwable) {
                ok = false
            } finally {
                latch.countDown()
            }
        }
        return latch.await(timeoutMs, TimeUnit.MILLISECONDS) && ok
    }
}

/**
 * 截图：PixelCopy（minSdk 26 = 全设备主路，含系统层合成）→ 失败回退
 * View#draw（应用内容完整，丢系统覆盖层）。输出降采样长边 ≤1568（SPEC §3）。
 */
object ScreenCapture {

    private const val MAX_SIDE = 1568

    fun capture(activity: Activity): ByteArray? {
        val decor = activity.window.decorView
        val w = decor.width
        val h = decor.height
        if (w <= 0 || h <= 0) return null
        val bitmap = runCatching { Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888) }.getOrNull() ?: return null
        val copied = pixelCopy(activity, bitmap) || drawFallback(decor, bitmap)
        if (!copied) {
            bitmap.recycle()
            return null
        }
        val scaled = downscale(bitmap)
        bitmap.recycle()
        val out = ByteArrayOutputStream()
        return if (scaled.compress(Bitmap.CompressFormat.PNG, 100, out)) {
            if (scaled !== bitmap) scaled.recycle()
            out.toByteArray()
        } else null
    }

    private fun pixelCopy(activity: Activity, bitmap: Bitmap): Boolean {
        val latch = CountDownLatch(1)
        var success = false
        val rect = Rect(0, 0, activity.window.decorView.width, activity.window.decorView.height)
        try {
            PixelCopy.request(activity.window, rect, bitmap, { result ->
                success = result == PixelCopy.SUCCESS
                latch.countDown()
            }, Handler(Looper.getMainLooper()))
        } catch (_: Throwable) {
            return false
        }
        return latch.await(2500, TimeUnit.MILLISECONDS) && success
    }

    private fun drawFallback(decor: View, bitmap: Bitmap): Boolean {
        val latch = CountDownLatch(1)
        var drew = false
        Handler(Looper.getMainLooper()).post {
            try {
                decor.draw(Canvas(bitmap))
                drew = true
            } catch (_: Throwable) {
                drew = false
            } finally {
                latch.countDown()
            }
        }
        return latch.await(1500, TimeUnit.MILLISECONDS) && drew
    }

    private fun downscale(src: Bitmap): Bitmap {
        val maxSide = maxOf(src.width, src.height)
        if (maxSide <= MAX_SIDE) return src
        val ratio = MAX_SIDE.toFloat() / maxSide
        return Bitmap.createScaledBitmap(
            src,
            (src.width * ratio).roundToInt().coerceAtLeast(1),
            (src.height * ratio).roundToInt().coerceAtLeast(1),
            true,
        )
    }
}
