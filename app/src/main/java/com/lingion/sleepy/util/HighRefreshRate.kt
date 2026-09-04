package com.lingion.sleepy.util

import android.app.Activity
import android.view.Display

/**
 * 高刷新率(流畅优先) — 用户 2026-09-04 指令: 能上高刷就上高刷。
 *
 * 背景: app 不表态时 SurfaceFlinger 默认投 60Hz 票(FrameRateCompatibility 省电逻辑),
 * 120Hz 屏上动画只有 60 帧。这里用窗口级 preferredDisplayModeId 把刷率钉到
 * 屏幕支持的最高档 — 比 preferredRefreshRate(软请求常被忽略)可靠。
 *
 * 只挑与当前模式同分辨率的档位, 防止为刷率切换分辨率; 无匹配/无高刷档则不动(跟随系统)。
 * 关闭开关后恢复: 清掉 preferredDisplayModeId, 交回系统省电调度。
 */
object HighRefreshRate {

    /** 按开关状态应用; 返回实际生效的刷率(Hz, 0=未设置/不可用) */
    fun apply(activity: Activity, enabled: Boolean): Float {
        val display: Display = activity.display ?: return 0f
        val attrs = activity.window.attributes
        if (!enabled) {
            if (attrs.preferredDisplayModeId != 0) {
                attrs.preferredDisplayModeId = 0
                activity.window.attributes = attrs
            }
            return 0f
        }
        val current = display.mode
        val best = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return 0f
        // 显式钉档 (即使 best==current 也申请): 系统省电调度随时可能自己降档,
        // 只有声明 preferredDisplayModeId 才锁得住; 写同一 modeId 是幂等的。
        attrs.preferredDisplayModeId = best.modeId
        activity.window.attributes = attrs
        return best.refreshRate
    }
}
