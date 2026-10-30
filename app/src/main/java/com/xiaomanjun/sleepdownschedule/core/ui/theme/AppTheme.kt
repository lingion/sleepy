@file:Suppress("unused")

package com.xiaomanjun.sleepdownschedule.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity

@Composable
fun appUsesDarkTheme(config: ScheduleConfigEntity): Boolean {
    return if (config.followSystemDarkMode) isSystemInDarkTheme() else config.darkMode
}
