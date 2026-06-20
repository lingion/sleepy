package com.lingion.sleepy.util

import android.content.Context
import android.content.res.Configuration
import com.lingion.sleepy.SleepyApp
import java.util.Locale

/**
 * 运行时切换语言 — 不依赖 AppCompat，通过 ContextWrapper 注入 locale。
 */
object LocaleHelper {

    fun getLocale(langCode: String): Locale = when (langCode) {
        "zh-CN" -> Locale.SIMPLIFIED_CHINESE
        "zh-TW" -> Locale.TRADITIONAL_CHINESE
        "en" -> Locale.ENGLISH
        "ja" -> Locale.JAPANESE
        "es" -> Locale("es")
        else -> Locale.getDefault()
    }

    /** 给 Context 注入指定语言的 Locale，返回新的 Context */
    fun wrap(ctx: Context, langCode: String): Context {
        val locale = getLocale(langCode)
        Locale.setDefault(locale)
        val config = Configuration(ctx.resources.configuration)
        config.setLocale(locale)
        // 兼容 API 24+：用 createConfigurationContext
        return ctx.createConfigurationContext(config)
    }

    /** 便捷：从 AppPrefs 读取当前语言并 wrap */
    fun wrapDefault(ctx: Context): Context {
        val lang = AppPrefs.getLanguage(SleepyApp.get())
        return if (lang == "zh-CN") ctx else wrap(ctx, lang)
    }
}
