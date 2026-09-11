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
     * API31+: OPTION_APPWIDGET_SIZES 定向选择 (纯函数见 [WidgetSizeCore.pickSizeDp] —
     * 横竖两份时面积最大 ≠ 当前方向; 以 MIN_WIDTH/MIN_HEIGHT (当前 cell 口径) 为
     * 摆放 hint 解析方向, 旧"宽度优先"在真方向对上永远取横份)。
     * 回退: MIN_W × MIN_H 同源边界 (API29/30 javadoc: MIN=当前下界; 旧 MIN_W×MAX_H
     * 混拼上下界会把 1 行 widget 画成 5 行长图)。
     */
    fun computeSizeDp(opts: android.os.Bundle): Pair<Int, Int> {
        var wDp = 0
        var hDp = 0
        // 摆放 hint: MIN_WIDTH/MIN_HEIGHT = 当前 cell 宽高下界 (dp), 方向判据同源
        val hintW = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val hintH = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // 类型化重载 getParcelableArrayList(key, Class) 是 API 33 新增,
            //   API 31/32 调用会 NoSuchMethodError → 守卫必须用 TIRAMISU 而非 S
            opts.getParcelableArrayList(
                AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java
            )?.map { it.width to it.height }
                ?.let { picked -> WidgetSizeCore.pickSizeDp(picked, hintW.toFloat() to hintH.toFloat())
                    ?.let { wDp = it.first.toInt(); hDp = it.second.toInt() } }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // API 31/32: OPTION_APPWIDGET_SIZES 已存在但只有无类型重载(开发期过时警告, 运行时安全)
            @Suppress("DEPRECATION", "UncheckedCast")
            val legacy = opts.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            legacy?.map { it.width to it.height }
                ?.let { picked -> WidgetSizeCore.pickSizeDp(picked, hintW.toFloat() to hintH.toFloat())
                    ?.let { wDp = it.first.toInt(); hDp = it.second.toInt() } }
        }
        if (wDp <= 0 || hDp <= 0) {
            val fb = WidgetSizeCore.fallbackSizeDp(
                hintW,
                hintH,
                opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH),
                opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
            )
            wDp = fb.first
            hDp = fb.second
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
        renderBitmap: (data: T, wDp: Float, hDp: Float) -> Bitmap,
        layoutRes: Int = R.layout.widget_bitmap_container,
        configureViews: ((RemoteViews) -> Unit)? = null,
        pushGen: Long = 0L
    ) {
        val data = loadData()
        val opts = awm.getAppWidgetOptions(widgetId)
        val (wDp, hDp) = computeSizeDp(opts)
        val density = context.resources.displayMetrics.density
        val wPx = (wDp * density).toInt().coerceAtLeast((180 * density).toInt())
        val hPx = (hDp * density).toInt().coerceAtLeast((150 * density).toInt())

        val bmp = renderBitmap(data, wDp.toFloat(), hDp.toFloat())
        val views = RemoteViews(context.packageName, layoutRes)
        views.setImageViewBitmap(R.id.widget_bitmap, bmp)
        val pi = PendingIntent.getActivity(
            context, WidgetRoutes.tapRequestCode(widgetId),
            WidgetRoutes.tapIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_bitmap, pi)
        configureViews?.invoke(views)
        // 世代号校验: pushGen>0 时 (由 receiver bump 后传入), 渲染期间落了更新的触发
        // → 本结果作废 (新触发的任务会提交最新状态); 0 = 不过关, 既有调用方零改动。
        if (pushGen > 0 && WidgetResizeCore.isStale(widgetId, pushGen)) {
            Log.d(tag, "renderAndPush skip stale id=$widgetId gen=$pushGen")
            return
        }
        awm.updateAppWidget(widgetId, views)
        // NOTE: 不能 bmp.recycle()!
        // RemoteViews.setImageViewBitmap 把 bitmap 放进 RemoteViews.mBitmapCache,
        // 通过 binder 传给系统 AppWidgetService; 大尺寸 bitmap 在系统进程内常以 ashmem
        // 共享方式持有, 本进程 recycle 会立刻释放 native pixel memory →
        // 启动器渲染时 setImageBitmap 抛 "trying to use a recycled bitmap" →
        // RemoteViews.apply() 失败 → AppWidgetHostView 回落到 "无法加载微件" 错误视图。
        // 改成 next onUpdate 推送新 RemoteViews 时旧 bitmap 自然随 mBitmapCache 一起被 GC。
        Log.d(tag, "renderAndPush id=$widgetId ${wDp}x${hDp}dp → ${wPx}x${hPx}px")
    }

    /**
     * 可滚动推送 (v1.0.36 第二次实现) — 内容超出容器时启用。
     *
     * 结构: 壳图(原渲染器按容器尺寸画 = 圆角背景+首屏内容, 与主分支静态渲染同一次调用)
     * + ListView(ScrollStripService 条带, 原渲染器按全展开高度画长图后横切)。
     * 条带与壳同源 → 滚动位置 0 与主分支静态 widget 像素一致。
     *
     * @param shellBitmap 壳图 (调用方用原渲染器按 wDp×hDp 渲染); null = 布局无壳
     *        (v3 今日导航滚动布局已删壳图层, 整图行自带背景 — 双图层重影根因)
     * @param layoutRes 可滚动容器布局 (含 widget_shell + widget_strip_list)
     * @param configureViews 推送前对 RemoteViews 的追加配置钩子(挂导航区 PendingIntent),
     *        null = 不追加 → 既有调用方零改动
     * @param stripHeaderless 条带长图渲染是否去头 (issue #24 顶栏导航: 今日导航版条带长图
     *        不画 bitmap 标题行 — 真实视图顶栏覆盖在 ListView 之上, 条带再画标题 = 双重标题;
     *        false = 旧行为, WeekGrid 最小档 overflow 条带仍带头) — 缺省 false, 既有调用方零改动
     */
    fun pushScrollable(
        context: Context,
        awm: AppWidgetManager,
        widgetId: Int,
        tag: String,
        layoutRes: Int,
        shellBitmap: Bitmap?,
        scopeExtra: String,
        configureViews: ((RemoteViews) -> Unit)? = null,
        stripHeaderless: Boolean = false,
        pushGen: Long = 0L
    ) {
        val views = RemoteViews(context.packageName, layoutRes)
        // null 壳 (v3 导航布局无壳层): 对不存在 id 的 action 会炸整次 apply, 必须跳过
        if (shellBitmap != null) {
            views.setImageViewBitmap(R.id.widget_shell, shellBitmap)
        }

        val svcIntent = Intent(context, ScrollStripService::class.java).apply {
            putExtra(ScrollStripService.StripFactory.EXTRA_WIDGET_ID, widgetId)
            putExtra(ScrollStripService.StripFactory.EXTRA_SCOPE, scopeExtra)
            putExtra(ScrollStripService.StripFactory.EXTRA_EMPTY_HEADER, stripHeaderless)
            data = android.net.Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }
        views.setRemoteAdapter(R.id.widget_strip_list, svcIntent)

        val template = PendingIntent.getActivity(
            context, WidgetRoutes.tapRequestCode(widgetId),
            WidgetRoutes.tapIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setPendingIntentTemplate(R.id.widget_strip_list, template)

        configureViews?.invoke(views)
        // 世代号校验 (同 renderAndPush): 壳图按触发时的旧尺寸画, 尺寸已变则丢弃,
        // 由最新触发的 push 提交新壳 + 触发新条带 — 防 resize 拖拽期间旧结果覆盖新结果。
        if (pushGen > 0 && WidgetResizeCore.isStale(widgetId, pushGen)) {
            Log.d(tag, "pushScrollable skip stale id=$widgetId gen=$pushGen")
            return
        }
        // updateAppWidget 与 notifyAppWidgetViewDataChanged 是两次独立 binder 调用,
        // 中间 launcher 可能拿到"新壳+旧条带"。顺序不可原子化, 但把 notify 紧跟 update
        // 且都在世代校验后执行, 把错配窗口压到最小 (launcher 端同帧应用时视觉无感)。
        awm.updateAppWidget(widgetId, views)
        awm.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_strip_list)
        // 同样不能 recycle: 壳图经 setImageViewBitmap 持有, 由 RemoteViews.mBitmapCache 引用,
        // 启动器渲染期间 native pixel 必须有效 (见 renderAndPush 同注释)。
        Log.d(tag, "pushScrollable id=$widgetId scope=$scopeExtra")
    }
}
