package com.lingion.sleepy.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.util.SizeF
import android.widget.RemoteViews
import com.lingion.sleepy.MainActivity
import com.lingion.sleepy.R

/**
 * 同步 RemoteViews 小组件的共享渲染+推送逻辑。
 *
 * 3 个移植自 Glance 的小组件(Today/WeekList/TwoDay) + WeekGrid 全走这条路:
 * goAsync 续命 → 后台加载+画 Canvas bitmap → awm.updateAppWidget 同步推送。
 * 全程在 OPPO OplusHansManager 冻结窗口(~5s)前完成 → 不卡 loading 布局。
 *
 * 各 Receiver 只需提供 [loadData] (同步数据加载) 和 [renderBitmap] (Canvas 画图)。
 */
object RemoteViewsWidgetHelper {

    private const val TAG = "RVWidgetHelper"

    /**
     * 从 AppWidgetOptions 算出 widget 当前真实尺寸(dp)。
     * API31+: OPTION_APPWIDGET_SIZES 取最大 SizeF(真实当前尺寸)。
     * 回退: MIN_W × MAX_H 近似默认窄高容器。
     */
    fun computeSizeDp(opts: android.os.Bundle): Pair<Int, Int> {
        var wDp = 0
        var hDp = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            opts.getParcelableArrayList(
                AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java
            )?.maxByOrNull { it.width * it.height }
                ?.let { wDp = it.width.toInt(); hDp = it.height.toInt() }
        }
        if (wDp <= 0 || hDp <= 0) {
            wDp = (opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: 250)
            hDp = (opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: 180)
        }
        return wDp to hDp
    }

    /**
     * 同步加载 + 渲染 + 推送。在 IO 协程里调。
     *
     * @param loadData 同步返回数据(runBlocking DB 读)
     * @param renderBitmap 把数据画成 Bitmap
     */
    fun <T> renderAndPush(
        context: Context,
        awm: AppWidgetManager,
        widgetId: Int,
        tag: String,
        loadData: () -> T,
        renderBitmap: (data: T, wDp: Float, hDp: Float) -> Bitmap
    ) {
        val data = loadData()
        val opts = awm.getAppWidgetOptions(widgetId)
        val (wDp, hDp) = computeSizeDp(opts)
        val density = context.resources.displayMetrics.density
        val wPx = (wDp * density).toInt().coerceAtLeast((180 * density).toInt())
        val hPx = (hDp * density).toInt().coerceAtLeast((150 * density).toInt())

        val bmp = renderBitmap(data, wDp.toFloat(), hDp.toFloat())
        val views = RemoteViews(context.packageName, R.layout.widget_bitmap_container)
        views.setImageViewBitmap(R.id.widget_bitmap, bmp)
        val pi = PendingIntent.getActivity(
            context, widgetId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_bitmap, pi)
        awm.updateAppWidget(widgetId, views)
        bmp.recycle()
        Log.d(tag, "renderAndPush id=$widgetId ${wDp}x${hDp}dp → ${wPx}x${hPx}px")
    }
}
