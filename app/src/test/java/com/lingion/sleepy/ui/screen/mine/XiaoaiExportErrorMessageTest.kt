package com.lingion.sleepy.ui.screen.mine

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.lingion.sleepy.R
import com.lingion.sleepy.data.parser.XiaoaiPresetData
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class XiaoaiExportErrorMessageTest {
    private fun context(language: String): Context {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val config = Configuration(app.resources.configuration)
        config.setLocale(Locale.forLanguageTag(language))
        return app.createConfigurationContext(config)
    }

    @Test
    fun missingTimeMessage_identifiesCourseAndPeriodInEverySupportedLanguage() {
        val error = XiaoaiPresetData.MissingPeriodTimeException("模拟电子技术实验", 13)
        for (language in listOf("zh-CN", "zh-TW", "en", "es", "ja")) {
            val ctx = context(language)
            val message = xiaoaiExportErrorMessage(ctx, error)
            assertTrue(message, message.contains("模拟电子技术实验"))
            assertTrue(message, message.contains("13"))
            assertFalse(message, message.contains("%"))
            assertFalse(message, message == ctx.getString(R.string.export_xiaoai_invalid))
        }
        assertEquals(
            "“模拟电子技术实验”用到第 13 节，但作息表未设置该节时间。请补全作息表或核对课程节次后重试",
            xiaoaiExportErrorMessage(context("zh-CN"), error),
        )
    }

    @Test
    fun otherValidationFailures_keepLocalizedFallbackMessage() {
        for (language in listOf("zh-CN", "zh-TW", "en", "es", "ja")) {
            val ctx = context(language)
            assertEquals(
                ctx.getString(R.string.export_xiaoai_invalid),
                xiaoaiExportErrorMessage(ctx, IllegalArgumentException()),
            )
        }
    }
}
