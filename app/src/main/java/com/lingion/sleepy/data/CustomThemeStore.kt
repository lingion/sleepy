package com.lingion.sleepy.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 自定义主题 — 用户在编辑器里配出的主题(primary/secondary/tertiary 种子色 + 表面中性色倾向)。
 *
 * 不存最终颜色,存 4 个"源角色"种子:完整 WakeUpColorScheme 由
 * [com.lingion.sleepy.ui.theme.CustomSchemeDeriver] 按 M3 角色关系派生,
 * 深浅两套 scheme 同源生成 — 与 ThemePresets 静态预设"每套带 light/dark 一对"对齐。
 *
 * 存储:SharedPreferences JSON 数组(模式参考节假日覆盖层/WidgetBindingStore,
 * 禁碰 Room — 主题配置不是结构化业务数据)。序列化形状见 [CustomThemeCore]:
 * `[{"id","name","primary","secondary","tertiary","surfaceHue","surfaceChroma","createdAt"}]`。
 *
 * 深浅色中性色倾向:表面族不存具体色,存 surfaceHue(色相 0-360)+ surfaceChroma
 * (低饱和度 4-12 推荐),明度结构由派生引擎按模板给 — 同一倾向深浅模式各自成立。
 */
data class CustomTheme(
    val id: String,
    val name: String,
    /** 主交互色种子(按钮/选中态/当前周胶囊/链接色),"#RRGGBB" */
    val primary: String,
    /** 次级强调种子(芯片/次级容器底色),"#RRGGBB" */
    val secondary: String,
    /** 第三强调种子(节次 chip 等点缀),"#RRGGBB" */
    val tertiary: String,
    /** 表面中性色色相倾向 0.0-360.0(与 primary 同相即主题氛围一致性来源) */
    val surfaceHue: Double,
    /** 表面中性色饱和度倾向,推荐 4-12(0 = 纯灰,过高 = 彩色表面) */
    val surfaceChroma: Double,
    val createdAt: Long
)

/**
 * 纯 JVM 序列化/集合操作核心 — 所有逻辑集中在此以便单测
 * (模式同 widget/WidgetBindingCore:Core 纯逻辑 + Store 薄 Android 门面)。
 *
 * 解析容错语义对齐 HolidayManager:整文档损坏 → 空列表;坏行(缺 id/形状错)
 * 跳过、好行保留;缺可选数值字段(surfaceHue/surfaceChroma/createdAt)落到默认值
 * 而非整行丢弃(向后兼容)。
 */
object CustomThemeCore {

    /** SharedPreferences key — 存 JSON 数组字符串 */
    const val KEY_THEMES = "custom_themes"

    /** SharedPreferences 文件名 */
    const val PREFS_NAME = "custom_themes"

    /** 缺省表面色相 — 与默认淡紫模板的紫相一致 */
    const val DEFAULT_SURFACE_HUE = 265.0

    /** 缺省表面饱和度 — 低 chroma 中性色推荐区间(4-12)中值 */
    const val DEFAULT_SURFACE_CHROMA = 8.0

    fun newId(): String = UUID.randomUUID().toString()

    fun toJson(themes: List<CustomTheme>): String {
        val arr = JSONArray()
        themes.forEach { t ->
            arr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("name", t.name)
                    .put("primary", t.primary)
                    .put("secondary", t.secondary)
                    .put("tertiary", t.tertiary)
                    .put("surfaceHue", t.surfaceHue)
                    .put("surfaceChroma", t.surfaceChroma)
                    .put("createdAt", t.createdAt)
            )
        }
        return arr.toString()
    }

    fun parse(json: String): List<CustomTheme> {
        return try {
            val arr = JSONArray(json)
            val out = mutableListOf<CustomTheme>()
            for (i in 0 until arr.length()) {
                val row = arr.optJSONObject(i) ?: continue
                val id = row.optString("id", "")
                // id 是唯一键(upsert/delete/getById 都靠它),缺失行不可救 — 跳过
                if (id.isBlank()) continue
                out.add(
                    CustomTheme(
                        id = id,
                        name = row.optString("name", ""),
                        primary = row.optString("primary", "#6750A4"),
                        secondary = row.optString("secondary", "#625B71"),
                        tertiary = row.optString("tertiary", "#7D5260"),
                        surfaceHue = row.optDouble("surfaceHue", DEFAULT_SURFACE_HUE),
                        surfaceChroma = row.optDouble("surfaceChroma", DEFAULT_SURFACE_CHROMA),
                        createdAt = row.optLong("createdAt", 0L)
                    )
                )
            }
            out
        } catch (_: Exception) {
            // 损坏 JSON 容错 — 参考 HolidayManager.diskCache 的 try-catch 空列表语义
            emptyList()
        }
    }

    /** upsert by id:存在即原位替换,不存在追加尾部 */
    fun upsert(list: MutableList<CustomTheme>, theme: CustomTheme) {
        val idx = list.indexOfFirst { it.id == theme.id }
        if (idx >= 0) list[idx] = theme else list.add(theme)
    }

    /** 删除指定 id;@return 是否真的删了(未知 id = false,列表不动) */
    fun delete(list: MutableList<CustomTheme>, id: String): Boolean =
        list.removeAll { it.id == id }

    fun getById(list: List<CustomTheme>, id: String): CustomTheme? =
        list.firstOrNull { it.id == id }
}

/**
 * Android 门面 — SharedPreferences 读写。
 * 全部逻辑在 [CustomThemeCore],此处只做 Context 桥接。
 */
object CustomThemeStore {

    private fun loadAll(ctx: Context): MutableList<CustomTheme> {
        val json = ctx.getSharedPreferences(CustomThemeCore.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(CustomThemeCore.KEY_THEMES, null) ?: return mutableListOf()
        return CustomThemeCore.parse(json).toMutableList()
    }

    private fun saveAll(ctx: Context, themes: List<CustomTheme>) {
        ctx.getSharedPreferences(CustomThemeCore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(CustomThemeCore.KEY_THEMES, CustomThemeCore.toJson(themes))
            .apply()
    }

    fun getAll(ctx: Context): List<CustomTheme> = loadAll(ctx)

    fun getById(ctx: Context, id: String): CustomTheme? =
        CustomThemeCore.getById(loadAll(ctx), id)

    /** upsert by id — 编辑既有主题复用同入口 */
    fun save(ctx: Context, theme: CustomTheme) {
        val list = loadAll(ctx)
        CustomThemeCore.upsert(list, theme)
        saveAll(ctx, list)
    }

    /** 删除;@return 是否真的删了 */
    fun delete(ctx: Context, id: String): Boolean {
        val list = loadAll(ctx)
        val removed = CustomThemeCore.delete(list, id)
        if (removed) saveAll(ctx, list)
        return removed
    }
}
