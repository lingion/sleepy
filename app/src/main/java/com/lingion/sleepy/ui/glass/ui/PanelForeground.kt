package com.lingion.sleepy.ui.glass.ui

import com.xiaomanjun.sleepdownschedule.core.ui.theme.appUsesDarkTheme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color as ComposeColor
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity

@Composable
fun glassForegroundColor(config: ScheduleConfigEntity): ComposeColor {
    return if (glassUsesLightStyle(config)) ComposeColor.Black else ComposeColor.White
}

@Composable
fun appPanelForegroundColor(config: ScheduleConfigEntity): ComposeColor {
    return if (appUsesDarkTheme(config)) ComposeColor.White else ComposeColor(0xFF111111)
}
