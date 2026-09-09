package com.lingion.sleepy.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.LruCache
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.lingion.sleepy.R
import java.time.LocalDate

/**
 * 今日课程小组件 StackView 卡片工厂 (issue #24 交互改造: 竖滑翻页 + 高亮按钮条)。
 *
 * 平台事实 (AOSP 实锤): StackView = AppWidget 白名单唯一自带手势的集合视图, 手势轴=竖直;
 * 显示窗口从 adapter position 0 起、只进不退 → 卡序=[锚定日, +1, …, +13]
 * (TodayStackCore.dayForPosition), 上滑=后一天, 向过去翻走 ◀ / 回今天按钮。
 *
 * 卡片 = renderToday 整卡渲染 (suppressHeaderNavAffordances=true), 点击卡面合并
 * PendingIntentTemplate 打开 App。内存: 惰性渲染 + LruCache(4), 不预渲 14 张。
 */
class TodayStackService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        StackFactory(applicationContext, intent)

    class StackFactory(
        private val context: Context,
        intent: Intent
    ) : RemoteViewsFactory {
        companion object {
            const val EXTRA_WIDGET_ID = "widget_id"
            const val EXTRA_VARIANT = "variant"
        }

        private val widgetId = intent.getIntExtra(EXTRA_WIDGET_ID, -1)
        private val variant = runCatching {
            WidgetVariant.valueOf(intent.getStringExtra(EXTRA_VARIANT) ?: WidgetVariant.REGULAR.name)
        }.getOrDefault(WidgetVariant.REGULAR)
        private var anchorEpochDay = Long.MIN_VALUE
        private val cache = LruCache<Long, Bitmap>(4)

        private fun sizeDp(): Pair<Float, Float> {
            val awm = AppWidgetManager.getInstance(context)
            val (wDp, hDp) = RemoteViewsWidgetHelper.computeSizeDp(awm.getAppWidgetOptions(widgetId))
            return wDp.toFloat() to hDp.toFloat()
        }

        override fun onCreate() {}

        override fun onDestroy() {
            // 卡片进 RemoteViews mBitmapCache, 严禁 recycle, 交 GC (见 RemoteViewsWidgetHelper 注释)
        }

        override fun onDataSetChanged() {
            // 锚点每次重读 (factory 跨 notify 存活): 点击导航 → receiver 持久化 shift/remove
            // → 重推容器 + notifyAppWidgetViewDataChanged → factory 重取锚点 → 卡序重建。
            val target = TodayDateNavStore.target(context, widgetId, LocalDate.now())
            val newAnchor = target.toEpochDay()
            if (newAnchor == anchorEpochDay) return
            anchorEpochDay = newAnchor
            cache.evictAll()
        }

        override fun getCount(): Int = TodayStackCore.ITEM_COUNT

        override fun getViewAt(position: Int): RemoteViews {
            val day = TodayStackCore.dayForPosition(anchorEpochDay, position)
            val bmp = cache.get(day) ?: renderCard(day).also { cache.put(day, it) }
            return RemoteViews(context.packageName, R.layout.widget_today_stack_item).apply {
                setImageViewBitmap(R.id.widget_stack_card, bmp)
                setOnClickFillInIntent(R.id.widget_stack_card, Intent())
            }
        }

        /** 单卡渲染 — loadDataForDate(该日) + renderToday(容器尺寸, 导航可用性收进按钮条)。 */
        private fun renderCard(day: Long): Bitmap {
            val (wDp, hDp) = sizeDp()
            val d = TodayWidgetReceiver.loadDataForDate(context, widgetId, LocalDate.ofEpochDay(day))
            return WidgetBitmapRenderers.renderToday(context, d, wDp, hDp, variant, true)
        }

        override fun getLoadingView(): RemoteViews? = null

        override fun getViewTypeCount(): Int = 1

        override fun getItemId(position: Int): Long =
            TodayStackCore.dayForPosition(anchorEpochDay, position)

        override fun hasStableIds(): Boolean = true
    }
}
