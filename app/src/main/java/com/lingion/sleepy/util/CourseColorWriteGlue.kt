package com.lingion.sleepy.util

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.Color
import com.lingion.sleepy.data.dao.CourseColorDao
import com.lingion.sleepy.data.entity.CourseColorEntity
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.ui.theme.CustomSchemeDeriver
import com.lingion.sleepy.ui.theme.ThemePresets

/**
 * 派生档写侧胶水（spec §2.4「新课按当前模式算 hue 写表」）。
 *
 * 渲染层保持纯读（persistedHue ?: stableHue）; course_colors 写入只发生在
 * 课程组**诞生**时刻: 教务导入 / 文件导入 / 覆盖导入 / 单课新增。
 *
 * 红线（spec §2.1/§2.3）:
 *   - CLASSIC 档（anchor=null）绝不写表 — 升级零变化, course_colors 保持空;
 *   - 钉死性: 既有组 hue 永不被本胶水移动, 只给「表中查无」的新组补色;
 *   - 配色是增值数据: 读写异常一律 runCatching 吞掉降级为不写,
 *     绝不允许影响课程落库主流程（查无 hue → 渲染回落 stableHue 老路径,
 *     step-5 主题重算/手动重排可自愈）。
 *
 * 调用点若已在 Room 事务内（JwImportViewModel importAsNewTable）, upsertAll
 * 自动并入该事务; 事务外调用则语句级原子。
 */
object CourseColorWriteGlue {

    /** 为 entities 中的新组补持久 hue（既有组只读跳过）; 返回本次写入的 gid→hue。 */
    suspend fun persistNewGroupHues(
        ctx: Context,
        dao: CourseColorDao,
        entities: List<CourseEntity>
    ): Map<String, Float> = runCatching {
        val mode = AppPrefs.getCourseColorMode(ctx)
        if (mode == AppPrefs.COURSE_COLOR_MODE_CLASSIC) return emptyMap()
        val anchor = resolveAnchor(ctx, mode) ?: return emptyMap()
        val tableKey = CourseColorEntity.GLOBAL_TABLE_KEY
        val persisted = dao.getHueMap(tableKey)
        val newGids = entities.asSequence()
            .map { it.groupId }
            .filter { it.isNotBlank() && it !in persisted }
            .distinct()
            .toList()
        if (newGids.isEmpty()) return emptyMap()
        val plan = CourseColorUtil.planHuesForGroups(anchor, persisted.values, newGids)
        if (plan.isNotEmpty()) {
            dao.upsertAll(plan.map { (gid, hue) ->
                CourseColorEntity(tableKey = tableKey, groupId = gid, hue = hue)
            })
            CourseHueStore.invalidate() // 渲染快照即时跟表（step-5 重算完成后同理）
        }
        plan
    }.getOrElse { emptyMap() }

    /** 当前深浅档主题 primary 的 anchor 解析 — 与 Compose 渲染路径同一派生函数（spec §2.2 契约4）。 */
    fun resolveAnchor(ctx: Context, mode: Int): Float? {
        val argb = themePrimaryArgb(ctx) ?: return null
        return CourseColorUtil.resolveAnchor(mode, argb, AppPrefs.getCourseColorSeedHue(ctx))
    }

    /**
     * 主题 primary → ARGB。解析规则逐条对齐 App 端 SleepyThemeProvider 与 widget 端
     * resolveSchemePublic（system→动态取色在此不可达: 胶水只用于写侧兜底,
     * uiMode 判深浅; custom:<id>→CustomSchemeDeriver 同一函数; 已删 id 回落默认淡紫）。
     */
    private fun themePrimaryArgb(ctx: Context): Int? {
        val key = AppPrefs.getThemeKey(ctx)
        val systemDark = (ctx.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val dark = AppPrefs.isDarkMode(ctx, systemDark)
        val primary: Color = when {
            key == ThemePresets.KEY_SYSTEM &&
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S -> {
                // 与 widget resolveSchemePublic / App SleepyThemeProvider 同一动态取色（spec §2.2 契约4 三路一致）
                val dyn = if (dark) androidx.compose.material3.dynamicDarkColorScheme(ctx)
                else androidx.compose.material3.dynamicLightColorScheme(ctx)
                dyn.primary
            }
            key.startsWith(ThemePresets.CUSTOM_KEY_PREFIX) -> {
                val custom = com.lingion.sleepy.data.CustomThemeStore.getById(
                    ctx, key.removePrefix(ThemePresets.CUSTOM_KEY_PREFIX)
                )
                    ?: com.lingion.sleepy.data.CustomTheme(
                        id = "", name = "", primary = "#6750A4", secondary = "#625B71",
                        tertiary = "#7D5260", surfaceHue = 265.0, surfaceChroma = 8.0, createdAt = 0L
                    )
                CustomSchemeDeriver.derive(custom, dark).primary
            }
            else -> ThemePresets.byKey(key).let { if (dark) it.dark.primary else it.light.primary }
        }
        return toArgb(primary)
    }

    /** 手拼 ARGB — 不用 android.graphics.Color.rgb(纯 JVM 单测里是恒 0 桩, 同 hueOf(Color) 先例)。 */
    private fun toArgb(c: Color): Int =
        (0xFF shl 24) or
            ((c.red * 255).toInt().coerceIn(0, 255) shl 16) or
            ((c.green * 255).toInt().coerceIn(0, 255) shl 8) or
            (c.blue * 255).toInt().coerceIn(0, 255)
}
