package com.lingion.sleepy.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import com.lingion.sleepy.SleepyApp
import java.util.Locale

/**
 * 运行时切换语言 — 不依赖 AppCompat，通过 ContextWrapper 注入 locale。
 *
 * 关键点：除了 setLocale，还要显式 setLocales(LocaleList) 和 setLayoutDirection，
 * 否则在某些 API / Compose 路径上，资源解析会用系统 LocaleList 而非单一 locale。
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

        // 复制现有 Configuration，避免丢失其他维度（屏幕密度等）
        val config = Configuration(ctx.resources.configuration)
        config.setLocale(locale)
        // 显式设置 LocaleList —— 现代 Android 资源解析优先看 LocaleList
        config.setLocales(LocaleList(locale))
        // 同步 layout direction（RTL 语言需要）
        config.setLayoutDirection(locale)

        // createConfigurationContext 在 API 24+ 是官方推荐做法
        val wrapped = ctx.createConfigurationContext(config)

        // 兜底：在某些 OEM ROM 上 createConfigurationContext 的 Resources
        // 仍然继承父 context 的 locale。强制刷新一次。
        return try {
            val res = wrapped.resources
            val cfg = res.configuration
            cfg.setLocale(locale)
            cfg.setLocales(LocaleList(locale))
            cfg.setLayoutDirection(locale)
            // updateConfiguration 已 deprecated 但仍是最稳的强制刷新方式
            @Suppress("DEPRECATION")
            res.updateConfiguration(cfg, res.displayMetrics)
            wrapped
        } catch (_: Throwable) {
            wrapped
        }
    }

    /** 便捷：从 AppPrefs 读取当前语言并 wrap — 无论哪种语言都强制 wrap，防止系统 locale 覆盖 */
    fun wrapDefault(ctx: Context): Context {
        val lang = try {
            AppPrefs.getLanguage(SleepyApp.get())
        } catch (_: Throwable) {
            // SleepyApp.onCreate 可能尚未执行（极早期阶段），用默认 zh-CN
            "zh-CN"
        }
        return wrap(ctx, lang)
    }
}
