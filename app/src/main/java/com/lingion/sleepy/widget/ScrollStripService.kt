package com.lingion.sleepy.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.util.TypedValue
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.lingion.sleepy.R
import kotlin.math.ceil

/**
 * 可滚动小组件条带工厂 (v3, 2026-09-10 第三次实现)。
 *
 * 历史翻车 (真机 OPPO PKX110 实证, 坐标+标签取证):
 * v1 多 child 48dp 横切 — launcher ListView 滚动 extent 冻结于首屏 fill 数,
 *   extent = ceil(V/H)*H − V (789: 4×125.5−386=116px 实测吻合) → 尾条带不可达;
 * v2 单 child wrap_content+adjustViewBounds — launcher 量出错高 (等比应 626px
 *   量成 270px) → fitXY 压扁 + 渲染错乱 (巨型 prev 三角糊满整页)。
 * v3 契约: **单 child 整张长图 + setViewLayoutHeight 显式钉行高**。
 *   冻结公式在单 child (H ≥ V) 时退化为 extent = H − V = 完整可滚距离;
 *   行高由本服务按位图真实 dp 推给 launcher — 剥夺 launcher 量测自由,
 *   wrap_content 翻车根因不复发。零新渲染逻辑: 仍调主分支原渲染函数
 *   (renderToday / renderTwoDay / renderWeekList) 画全展开长图。
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
            const val EXTRA_EMPTY_HEADER = "intent_extra_key_empty_header"
            const val SCOPE_TODAY = "today"
            const val SCOPE_TWODAY = "twoday"
            const val SCOPE_WEEKLIST = "weeklist"
        }

        private val widgetId = intent.getIntExtra(EXTRA_WIDGET_ID, -1)
        private val scope = intent.getStringExtra(EXTRA_SCOPE) ?: SCOPE_TODAY
        // 长图是否去头 (今日导航版 true: 真实视图顶栏覆盖 ListView 之上, 长图再画标题=双重标题)
        private val emptyHeader = intent.getBooleanExtra(EXTRA_EMPTY_HEADER, false)

        /**
         * 条带页位图 — onDataSetChanged (binder 线程 A) 整表重赋值, getViewAt
         * (binder 线程 B) 按下标读。RemoteViewsService 对两者无互斥 → 读取侧必须
         * 先取局部 snapshot 再下标 (v9.3: 裸 strips[position] 撞上收缩重赋值 =
         * ArrayIndexOutOfBounds 杀进程根因)。
         */
        @Volatile
        private var strips: List<Bitmap> = emptyList()

        override fun onCreate() {}

        override fun onDestroy() {
            // 长图经 createBitmap 共享像素缓冲时严禁 recycle, 交 GC 统一回收。
        }

        /**
         * 空白安全回退行 — binder 线程防御边界: 任何渲染异常/越界都退到这里,
         * 绝不向 binder 线程抛 (RemoteViewsFactory 未捕获异常 = 杀进程)。
         * 不 recycle 共享像素 (同 onDestroy 注释)。
         */
        private fun blankRowViews(position: Int, total: Int): RemoteViews =
            RemoteViews(context.packageName, R.layout.widget_scroll_row).apply {
                setOnClickFillInIntent(R.id.widget_row_bitmap, Intent())
                setContentDescription(
                    R.id.widget_row_bitmap, "whole ${position + 1}/$total $scope id=$widgetId blank"
                )
            }

        override fun onDataSetChanged() {
            try {
                onDataSetChangedInner()
            } catch (e: Throwable) {
                // binder 线程防御边界 (v9.3): loadDataSync / createBitmap(0px) / OOM
                // 任何 throw 裸抛 = 杀进程。落空白单页, launcher 端至少不闪旧内容之外的东西。
                android.util.Log.e("ScrollStrip", "onDataSetChanged failed id=$widgetId", e)
                strips = emptyList()
            }
        }

        private fun onDataSetChangedInner() {
            val awm = AppWidgetManager.getInstance(context)
            val opts = awm.getAppWidgetOptions(widgetId)
            val (wDp, hDp) = RemoteViewsWidgetHelper.computeSizeDp(opts)
            val density = context.resources.displayMetrics.density

            // 世代闸第一道: 读尺寸前快照。渲染期间又落了新 resize/更新触发
            // → 本次重算的是旧尺寸长图, 直接丢弃 (保留旧 strips 继续显示, 新触发会再刷)。
            val genBefore = WidgetResizeCore.current(widgetId)
            if (genBefore > 0 && WidgetResizeCore.isStale(widgetId, genBefore)) {
                return
            }

            // 原渲染器 + 内容全展开高度 (ceil 到整数 dp, 长图不缺行)
            val contentHdp: Float
            var full: Bitmap? = null
            var rowCount = -1
            when (scope) {
                SCOPE_TODAY -> {
                    val d = TodayWidgetReceiver.loadDataSync(context, widgetId)
                    // v11: 撤回 v10 逐行子项 — 在 OPPO 真机上三症状同根(无法拖动 +
                    // 双层错位叠影 + TopBar 被覆盖), 实锤 v1 多 child extent 冻结
                    // 复发。回归 v9.1 形态 = 单 child 整张不透明长图,与 TwoDay/
                    // WeekList 同构 (同台 OPPO 一直正常)。条带与壳图同源坐标系 →
                    // 滚动位 0 首屏与壳图逐像素一致。
                    contentHdp = WidgetBitmapRenderers.todayContentHeightDp(d, headerSpace = emptyHeader)
                    rowCount = TodayRowGeometry.rowSpans(d.courses, emptyHeader).size
                    val renderH = ceil(contentHdp)
                    full = WidgetBitmapRenderers.renderToday(
                        context, d, wDp.toFloat(), renderH,
                        emptyHeader = emptyHeader, headerSpace = emptyHeader
                    )
                }
                SCOPE_TWODAY -> {
                    val d = TwoDayWidgetReceiver.loadDataSync(context, widgetId)
                    contentHdp = WidgetBitmapRenderers.twoDayContentHeightDp(d)
                    val renderH = ceil(contentHdp)
                    full = WidgetBitmapRenderers.renderTwoDay(context, d, wDp.toFloat(), renderH)
                }
                SCOPE_WEEKLIST -> {
                    val d = WeekListWidgetReceiver.loadDataSync(context, widgetId)
                    contentHdp = WidgetBitmapRenderers.weekListContentHeightDp(context, d)
                    val renderH = ceil(contentHdp)
                    full = WidgetBitmapRenderers.renderWeekList(context, d, wDp.toFloat(), renderH)
                }
                else -> return
            }

            // 世代闸第二道: 渲染是重活, commit 前再验一次 — 期间落了新触发就丢弃
            // (旧 strips 原地保留 = launcher 端 ListView 继续显示上一份完整内容, 不闪空)。
            if (WidgetResizeCore.isStale(widgetId, genBefore)) {
                android.util.Log.d("ScrollStrip", "skip stale strips id=$widgetId gen=$genBefore")
                return
            }
            strips = listOf(full!!)
            android.util.Log.d("ScrollStrip",
                "scope=$scope id=$widgetId ${wDp}x${hDp}dp content=${contentHdp}dp render=${full!!.height / density}dp rows=$rowCount wholeImage=1")
        }

        /**
         * count 恒等 strips.size — stale/异常路径空 adapter (count=0) 绝不触
         * getViewAt 越界; 读法与 getViewAt 同 pattern (局部 snapshot, 同源无撕裂)。
         */
        override fun getCount(): Int {
            val snapshot = strips
            return snapshot.size
        }

        override fun getViewAt(position: Int): RemoteViews {
            // v9.3: onDataSetChanged 与 getViewAt 是独立 binder 池线程 — 先取局部
            // snapshot, 越界回退空白行, 绝不在 binder 线程 throw (杀进程)。
            val snapshot = strips
            if (position < 0 || position >= snapshot.size) {
                return blankRowViews(position, snapshot.size)
            }
            val bmp = snapshot[position]
            return RemoteViews(context.packageName, R.layout.widget_scroll_row).apply {
                setImageViewBitmap(R.id.widget_row_bitmap, bmp)
                // v3 核心: 行高显式钉死 = 位图真实 dp 高。launcher 端 ListView 直接
                // 拿到确定值, 无 wrap_content 量测 (v2 量错高根因), 无 48dp 切片
                // (v1 extent 冻结根因)。单 child (H≥V) → extent = H−V 完整可滚。
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setViewLayoutHeight(
                        R.id.widget_row_bitmap,
                        bmp.height / context.resources.displayMetrics.density,
                        TypedValue.COMPLEX_UNIT_DIP
                    )
                }
                // 真机取证标签 (adb uiautomator 可读): 位图实际像素高也带上
                setContentDescription(
                    R.id.widget_row_bitmap, "whole ${position + 1}/${snapshot.size} $scope id=$widgetId bmp=${bmp.width}x${bmp.height}"
                )
                // 空 Intent 合并进 ListView 的 PendingIntentTemplate (打开 app)
                setOnClickFillInIntent(R.id.widget_row_bitmap, Intent())
            }
        }

        override fun getLoadingView(): RemoteViews? = null

        override fun getViewTypeCount(): Int = 1

        override fun getItemId(position: Int): Long = position.toLong()

        override fun hasStableIds(): Boolean = false
    }
}
