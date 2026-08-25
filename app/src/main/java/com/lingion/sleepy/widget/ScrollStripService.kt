package com.lingion.sleepy.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.lingion.sleepy.R
import kotlin.math.ceil

/**
 * ★ 可滚动小组件条带工厂 (v1.0.36 第二次实现, 2026-08-25)。
 *
 * 第一次实现(已回滚)重写了一套逐行渲染函数 → 视觉与主分支完全不像, 三个组件全废。
 * 本次原则: **零新渲染逻辑** — 完全调用主分支原渲染函数
 * (renderToday / renderTwoDay / renderWeekList), 以"内容全展开高度"画一张长图,
 * 再横切成等高条带喂 ListView:
 *   - 滚动位置 0 = 原渲染器输出像素, 与主分支静态 bitmap 同源同坐标, 不存在画得不像;
 *   - 条带无间隙拼接 (divider=0, 行高=条带高) → 视觉是连续长图, 滚动即平移。
 * 内容装得下时 Receiver 直接走原 renderAndPush 静态路径, 不进本服务。
 */
class ScrollStripService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        StripFactory(applicationContext, intent)

    class StripFactory(
        private val context: Context,
        intent: Intent
    ) : RemoteViewsFactory {

        companion object {
            const val EXTRA_WIDGET_ID = "widget_id"
            const val EXTRA_SCOPE = "scope"
            const val SCOPE_TODAY = "today"
            const val SCOPE_TWODAY = "twoday"
            const val SCOPE_WEEKLIST = "weeklist"

            /** 条带高度 dp — 行布局 widget_scroll_row.xml layout_height 必须同值 */
            const val STRIP_DP = 48
        }

        private val widgetId = intent.getIntExtra(EXTRA_WIDGET_ID, -1)
        private val scope = intent.getStringExtra(EXTRA_SCOPE) ?: SCOPE_TODAY
        private var strips: List<Bitmap> = emptyList()

        override fun onCreate() {}

        override fun onDestroy() {
            // ★ 条带经 createBitmap(src,…) 与源图共享像素缓冲, 严禁 recycle
            //   (回收源图缓冲会连带撕碎全部条带), 交 GC 统一回收。
        }

        override fun onDataSetChanged() {
            val awm = AppWidgetManager.getInstance(context)
            val opts = awm.getAppWidgetOptions(widgetId)
            val (wDp, hDp) = RemoteViewsWidgetHelper.computeSizeDp(opts)
            val density = context.resources.displayMetrics.density
            val stripPx = (STRIP_DP * density).toInt()

            // 原渲染器 + 内容全展开高度 → 渲染高度向上取整到条带整数倍(末条带不缺角)
            val contentHdp: Float
            val full: Bitmap
            when (scope) {
                SCOPE_TODAY -> {
                    val d = TodayWidgetReceiver.loadDataSync(context)
                    contentHdp = WidgetBitmapRenderers.todayContentHeightDp(d)
                    val renderH = ceil(contentHdp / STRIP_DP) * STRIP_DP
                    full = WidgetBitmapRenderers.renderToday(context, d, wDp.toFloat(), renderH)
                }
                SCOPE_TWODAY -> {
                    val d = TwoDayWidgetReceiver.loadDataSync(context)
                    contentHdp = WidgetBitmapRenderers.twoDayContentHeightDp(d)
                    val renderH = ceil(contentHdp / STRIP_DP) * STRIP_DP
                    full = WidgetBitmapRenderers.renderTwoDay(context, d, wDp.toFloat(), renderH)
                }
                SCOPE_WEEKLIST -> {
                    val d = WeekListWidgetReceiver.loadDataSync(context)
                    contentHdp = WidgetBitmapRenderers.weekListContentHeightDp(context, d)
                    val renderH = ceil(contentHdp / STRIP_DP) * STRIP_DP
                    full = WidgetBitmapRenderers.renderWeekList(context, d, wDp.toFloat(), renderH)
                }
                else -> return
            }

            // 横切条带 — 共享像素缓冲, 不复制
            val count = full.height / stripPx
            strips = (0 until count).map { i ->
                Bitmap.createBitmap(full, 0, i * stripPx, full.width, stripPx)
            }
            android.util.Log.d("ScrollStrip",
                "scope=$scope id=$widgetId ${wDp}x${hDp}dp content=${contentHdp}dp render=${full.height / density}dp strips=$count")
        }

        override fun getCount(): Int = strips.size

        override fun getViewAt(position: Int): RemoteViews =
            RemoteViews(context.packageName, R.layout.widget_scroll_row).apply {
                setImageViewBitmap(R.id.widget_row_bitmap, strips[position])
                // 空 Intent 合并进 ListView 的 PendingIntentTemplate (打开 app)
                setOnClickFillInIntent(R.id.widget_row_bitmap, Intent())
            }

        override fun getLoadingView(): RemoteViews? = null

        override fun getViewTypeCount(): Int = 1

        override fun getItemId(position: Int): Long = position.toLong()

        override fun hasStableIds(): Boolean = false
    }
}
