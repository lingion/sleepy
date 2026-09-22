package com.lingion.sleepy.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/**
 * [intentional custom] 官方 MotionScheme.expressive() 已是全局默认(MD3E), 但其空间类
 * spring 带回弹(MediumBouncy) — thumb 类「跟手不弹」交互是用户实测定参
 * (高硬度+无回弹, MediumLow 拖沓不跟手), 官方 motionScheme 无对应档位。
 * 使用处: SegmentedSwitcher thumb / PillNavigationBar 贴底+Dock thumb(均为薄层组件)。
 * 注释里的「同款」即指此常量; 改参数三处同步生效。
 */
val SleepyThumbSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessHigh
)
