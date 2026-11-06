package com.lingion.sleepy.ui.component.liquidglass

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * 触觉反馈门面, 100% 对齐 Flutter 的 [HapticFeedback.selectionClick]。
 *
 * 在 Android 上映射至最清脆的段落触感:
 * - API 34+: `SEGMENT_TICK` (系统最细小微振)
 * - API 21+: `CLOCK_TICK` (转盘/钟摆微振)
 * - 若系统禁用 View 触觉或厂商阻断, 则兜底至系统 Vibrator `EFFECT_TICK`。
 */
object LiquidGlassHaptics {

    /** 触发选择微点击触感 (tab 跨界滑动或松手命中) */
    fun selectionClick(view: View) {
        val feedbackConstant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            HapticFeedbackConstants.SEGMENT_TICK
        } else {
            HapticFeedbackConstants.CLOCK_TICK
        }

        val performed = view.performHapticFeedback(
            feedbackConstant,
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
        )

        if (!performed) {
            // 兜底至系统硬件振子
            tryPerformVibratorTick(view.context)
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun tryPerformVibratorTick(context: Context) {
        try {
            if (context.checkSelfPermission(android.Manifest.permission.VIBRATE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return
            }
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(10L)
                }
            }
        } catch (_: Throwable) {
            // 忽略触觉权限或设备不支持异常
        }
    }
}
