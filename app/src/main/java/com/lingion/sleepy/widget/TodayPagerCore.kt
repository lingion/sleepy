package com.lingion.sleepy.widget

/**
 * 今日小组件 v4 手动翻页核心 (2026-09-10)。
 *
 * 前三轮 launcher ListView 滚动在 OPPO ColorOS 全灭 (v1 extent 冻结 /
 * v2 量错高 / v3 滑动即整页错乱), 静态单位图路径三轮全程正常 →
 * ColorOS 唯一稳定通道 = setImageViewBitmap 静态位图。v4 放弃 launcher
 * 滚动: overflow 内容切页, 顶栏 ‹› 翻页, 页尾跨天。
 *
 * 纯 JVM (无 Android 依赖) — 渲染/持久化之外的页几何与翻页语义单点真相。
 */
object TodayPagerCore {

    /** 顶栏高 dp — 与 NAV_HEADER_H_DP / 顶栏布局 36dp 同源。 */
    private const val HEADER_DP = 36f

    /** 内容纵轴上/下留白 dp — 与 renderTodayRegular pad(14) 同源。 */
    private const val PAD_DP = 14f

    /** 页内容高 = 视口 − 顶栏 − 上下留白 (页面位图按视口整卡渲染, 内容区此高)。 */
    fun pageHeightDp(hDp: Float): Float = (hDp - HEADER_DP - PAD_DP * 2).coerceAtLeast(1f)

    /** 页数 = ceil(内容高 / 页高), 内容 ≤ 页高 → 1 页。 */
    fun pageCount(contentHdp: Float, hDp: Float): Int {
        val page = pageHeightDp(hDp)
        return ((contentHdp + page - 1f) / page).toInt().coerceAtLeast(1)
    }

    /** 页起点 (内容纵轴 dp 偏移) — 页 N 从 N×页高起, 渲染器 clamp 到内容内。 */
    fun pageOffsetDp(page: Int, contentH: Float, hDp: Float): Float =
        (page * pageHeightDp(hDp)).coerceIn(0f, contentH.coerceAtLeast(0f))

    /** 越界页码 clamp 到 [0, totalPages-1] — 广播乱序/竞态防御。 */
    fun clampPage(page: Int, totalPages: Int): Int =
        page.coerceIn(0, (totalPages - 1).coerceAtLeast(0))

    /** ‹› 按钮动作结局: 翻页 (同一天) 或 跨天。 */
    sealed class Outcome {
        /** 同一天内换页。 */
        data class Page(val page: Int, val totalPages: Int) : Outcome()
        /** 跨天: deltaDays ±1。next 落新一天页 0; prev 落新一天末页。 */
        data class DayShift(val deltaDays: Long) : Outcome()
        /** 跨天并落到末页 (prev 从页 0 跨出时)。 */
        data class DayShiftToLastPage(val deltaDays: Long) : Outcome()
    }

    /** › 动作: 页内先翻页, 末页再跨天 (新一天从页 0 起)。 */
    fun resolveNext(page: Int, totalPages: Int): Outcome =
        if (page < totalPages - 1) Outcome.Page(clampPage(page + 1, totalPages), totalPages)
        else Outcome.DayShift(1L)

    /** ‹ 动作: 页内先回翻, 页 0 再跨天 (落新一天末页)。 */
    fun resolvePrev(page: Int, totalPages: Int): Outcome =
        if (page > 0) Outcome.Page(clampPage(page - 1, totalPages), totalPages)
        else Outcome.DayShiftToLastPage(-1L)
}
