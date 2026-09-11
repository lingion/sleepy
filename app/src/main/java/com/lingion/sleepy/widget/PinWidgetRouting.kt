package com.lingion.sleepy.widget

/**
 * PinWidgetActivity 的 `widget_type` 字符串 → receiver Class 解析（纯函数）。
 *
 * 设计动机（跨厂商 pin 路径统一）: 大陆主流启动器（HyperOS / ColorOS / OriginOS /
 * MagicOS / OneUI / Flyme）对 `AppWidgetManager.requestPinAppWidget` 的行为差异
 * 见 `docs/widget-vendor-specs/INDEX.md` — Sleepy 侧唯一能做的是保证 pin 入口
 * 对**全部变体**都可路由，而不是只接了历史 4 个。
 *
 * 路由表从 [ALL_WIDGET_VARIANTS] 派生（single source of truth）：接受两类 key —
 * 1) 历史 4 个手写短 key（weekgrid / today / twoday / weeklist，兼容旧 adb 脚本）；
 * 2) receiver simpleName 全小写（weekgridwidgetprovider / todaysmallwidgetreceiver …）。
 * 未知或空 type 一律回落 WeekGrid（与旧行为一致）。
 *
 * 返回 Kotlin Class 而非 android ComponentName：本仓库 JVM 单测对 android.jar
 * 走 returnDefaultValues mock（WidgetInfoXmlContractTest 同理），ComponentName
 * 的 getter 会返回 null — 纯 Kotlin 类型让解析逻辑完全 JVM 可测（PinWidgetRoutingTest）。
 */
object PinWidgetRouting {

    /** type key → receiver class（含历史短 key + simpleName 全小写双入口）。 */
    private val typeKeyToClass: Map<String, Class<out android.appwidget.AppWidgetProvider>> =
        buildMap {
            ALL_WIDGET_VARIANTS.forEach { variant ->
                put(variant.receiverClass.simpleName.lowercase(), variant.receiverClass)
                // 历史 adb 手写 key 规则: 去 Receiver/Provider 后缀 + 去 Widget 中缀
                // (TodayWidgetReceiver → today; WeekGridWidgetProvider → weekgrid;
                //  WeekGridSmallWidgetProvider → weekgridsmall, 与历史 4 key 无冲突)
                put(
                    variant.receiverClass.simpleName
                        .removeSuffix("Receiver")
                        .removeSuffix("Provider")
                        .removeSuffix("Small")
                        .replace("Widget", "")
                        .lowercase(),
                    variant.receiverClass
                )
            }
        }

    /** type 字符串 → receiver Class；未知/空/null 一律回落 WeekGrid（永不 null）。 */
    @JvmStatic
    fun resolveClass(type: String?): Class<out android.appwidget.AppWidgetProvider> =
        typeKeyToClass[type?.trim()?.lowercase()] ?: WeekGridWidgetProvider::class.java

    /** 暴露路由表 key 集合（测试断言用）。 */
    internal fun routableTypes(): Set<String> = typeKeyToClass.keys
}
