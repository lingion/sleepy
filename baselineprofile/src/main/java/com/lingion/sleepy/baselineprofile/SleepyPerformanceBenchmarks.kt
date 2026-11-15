package com.lingion.sleepy.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

internal const val SLEEPY_PACKAGE = "com.lingion.sleepy"

@RunWith(AndroidJUnit4::class)
class SleepyPerformanceBenchmarks {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startupProfile() = baselineProfileRule.collect(
        packageName = SLEEPY_PACKAGE,
        includeInStartupProfile = true,
    ) {
        startActivityAndWait()
    }

    @Test
    fun navigationProfile() = baselineProfileRule.collect(
        packageName = SLEEPY_PACKAGE,
    ) {
        startActivityAndWait()
        selectTab("Today", "今日", "Hoy")
        selectTab("Schedule", "课表", "課表", "時間割", "Horario")
        selectTab("Manage", "课表管理", "管理", "Gestionar")
        selectTab("Me", "我的", "マイ", "Ajustes")
    }

}

private fun androidx.benchmark.macro.MacrobenchmarkScope.selectTab(vararg labels: String) {
    val tab = labels.firstNotNullOfOrNull { device.findObject(By.text(it)) }
        ?: error("Could not find a Sleepy navigation tab for ${labels.joinToString()}")
    tab.click()
    device.waitForIdle()
}
