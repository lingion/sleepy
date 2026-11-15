package com.lingion.sleepy.baselineprofile

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SleepyMacrobenchmarks {
    @get:Rule
    val macrobenchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartupTiming() = macrobenchmarkRule.measureRepeated(
        packageName = SLEEPY_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = 10,
        startupMode = StartupMode.COLD,
        setupBlock = { pressHome() },
        measureBlock = { startActivityAndWait() },
    )

    @Test
    fun navigationFrameTiming() = macrobenchmarkRule.measureRepeated(
        packageName = SLEEPY_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = 10,
        startupMode = StartupMode.WARM,
        setupBlock = {
            startActivityAndWait()
            selectTab("Today", "今日", "Hoy")
        },
        measureBlock = {
            selectTab("Schedule", "课表", "課表", "時間割", "Horario")
            selectTab("Manage", "课表管理", "管理", "Gestionar")
            selectTab("Me", "我的", "マイ", "Ajustes")
            selectTab("Schedule", "课表", "課表", "時間割", "Horario")
        },
    )
}

private fun androidx.benchmark.macro.MacrobenchmarkScope.selectTab(vararg labels: String) {
    val tab = labels.firstNotNullOfOrNull { device.findObject(By.text(it)) }
        ?: error("Could not find a Sleepy navigation tab for ${labels.joinToString()}")
    tab.click()
    device.waitForIdle()
}
