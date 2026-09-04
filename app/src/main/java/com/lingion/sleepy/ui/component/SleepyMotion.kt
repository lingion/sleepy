package com.lingion.sleepy.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/**
 * 全 app 共用弹簧动画参数 — 用户实测定参 (高硬度+无回弹, MediumLow 拖沓不跟手)。
 * 使用处: SegmentedSwitcher thumb / PillNavigationBar 贴底+Dock thumb。
 * 注释里的「同款」即指此常量; 改参数三处同步生效。
 */
val SleepyThumbSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessHigh
)
