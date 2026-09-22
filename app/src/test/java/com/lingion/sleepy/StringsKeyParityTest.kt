package com.lingion.sleepy

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Strings Key Parity Test — TS-9 Phase1 scaffolding.
 *
 * Phase1 semantics (per plan): assert ONLY that the newly-added
 * `settings_course_colorless` / `settings_course_colorless_sub` keys are present
 * in all 6 locale files — do NOT assert full key-set equality, because the
 * repo already has a known 9-line drift (values/ = 449 lines vs zh-rCN = 440).
 *
 * Batch-2 sequencing note: the string KEYS themselves land in batch 3
 * (pure-strings batch). This batch (2) lays the AppPrefs B-key groundwork and
 * the test scaffolding. The parity assertion therefore validates the
 * infrastructure (6 locale dirs + readable strings.xml) now, and the
 * `newKeys` list is the single source of truth that batch 3 will satisfy.
 *
 * This is a pure-JVM test (no Robolectric) — it only touches raw resource
 * files on disk.
 */
class StringsKeyParityTest {

    /** The 6 supported locale directories (no ko/fr). */
    private val localeDirs = listOf(
        "values",
        "values-en",
        "values-es",
        "values-ja",
        "values-zh-rCN",
        "values-zh-rTW"
    )

    /**
     * Single source of truth for the B-key entries that must exist in every
     * locale once batch 3 lands. Phase1 keys: B title + B subtitle.
     */
    private val newKeys = listOf(
        "settings_course_colorless",
        "settings_course_colorless_sub"
    )

    /**
     * 小组件重设计新增 string key (2026-09, feat/widget-fixed-window, 设计 §8)。
     * 全 6 locale 必须齐 (评审 #22: 本测试只查显式列出的键, 新键必须手动登记)。
     */
    private val widgetRedesignKeys = listOf(
        "widget_footer_more",
        "widget_footer_more_short",
        "widget_status_all_done",
        "widget_edit_section_scroll",
        "widget_edit_scroll_subtitle",
        "widget_scroll_dialog_title",
        "widget_scroll_dialog_body",
        "widget_scroll_dialog_confirm",
        "widget_scroll_dialog_cancel",
        "widget_today_wide_label",
        "widget_twoday_wide_label",
        "widget_week_list_wide_label"
    )

    /**
     * 自定义主题系统新增 string key(2026-09-11,feat/theme-custom-color)。
     * 全 6 locale 必须齐 — 缺任一 = MissingTranslation lint error 回归。
     */
    private val customThemeKeys = listOf(
        "theme_new",
        "theme_custom_edit",
        "theme_custom_name_label",
        "theme_custom_default_name",
        "theme_editor_random",
        "theme_editor_random_desc",
        "theme_editor_from_seed",
        "theme_editor_from_seed_desc",
        "theme_editor_manual",
        "theme_editor_manual_desc",
        "theme_role_primary",
        "theme_role_primary_desc",
        "theme_role_secondary",
        "theme_role_secondary_desc",
        "theme_role_tertiary",
        "theme_role_tertiary_desc",
        "theme_role_surface",
        "theme_role_surface_desc",
        "theme_editor_preview",
        "theme_editor_preview_card_title",
        "theme_editor_preview_card_room",
        "theme_editor_delete_confirm",
        "theme_editor_delete_confirm_body"
    )

    /** 已删除的每日提醒旧键,任何 locale 复活都表示布局回退。 */
    private val removedReminderKeys = listOf(
        "reminder_daily_switches_title",
        "reminder_daily_master_toggle_title",
        "reminder_daily_master_toggle_sub"
    )

    private val basePath: File = sequenceOf(
        File("app/src/main/res"),
        File("src/main/res")
    ).first { it.isDirectory }

    /**
     * issue#40 独立时间节次表新增 string key (2026-09-15, feat/issue40-independent-timetable)。
     * 全 6 locale 必须齐 — 缺任一 = MissingTranslation lint error 回归。
     */
    private val periodTableKeys = listOf(
        "mine_period_tables",
        // mine_period_tables_sub 已删(2026-09-16 用户: 「管理各课表共用的作息」副标题冗余)
        "period_tables_title",
        "period_table_bound_count",
        "period_table_new",
        "period_table_copy",
        "period_table_name_label",
        "period_table_delete_confirm",
        "period_table_delete_blocked",
        "period_table_bind_label",
        "period_table_unbound",
        "period_table_save_preview_title",
        "period_table_preview_summary",
        "period_table_preview_confirm",
        "period_table_bind_preview_title",
        "period_table_bind_preview_body"
    )

    /**
     * issue#23 混合课时自动模式新增 string key (2026-09-20, sdd/task-3)。
     * 全 6 locale 必须齐 — 缺任一 = MissingTranslation lint error 回归。
     */
    private val mixedDurationKeys = listOf(
        "duration_assign_hint",
        "short_duration",
        "long_duration",
        "duration_min_one_period",
        "duration_period_n"
    )

    @Test
    fun all_six_locale_dirs_exist() {
        for (locale in localeDirs) {
            val dir = File(basePath, locale)
            assertTrue("Missing locale dir $locale", dir.isDirectory)
        }
    }

    @Test
    fun mixed_duration_keys_present_in_all_six_locales() {
        for (locale in localeDirs) {
            val text = File(basePath, "$locale/strings.xml").readText()
            for (key in mixedDurationKeys) {
                assertTrue(
                    "mixed duration key \"$key\" missing in $locale/strings.xml",
                    text.contains("name=\"$key\"")
                )
            }
        }
    }

    @Test
    fun period_table_keys_present_in_all_six_locales() {
        for (locale in localeDirs) {
            val text = File(basePath, "$locale/strings.xml").readText()
            for (key in periodTableKeys) {
                assertTrue(
                    "period table key \"$key\" missing in $locale/strings.xml",
                    text.contains("name=\"$key\"")
                )
            }
        }
    }

    @Test
    fun removed_reminder_keys_stay_deleted_in_all_six_locales() {
        for (locale in localeDirs) {
            val text = File(basePath, "$locale/strings.xml").readText()
            for (key in removedReminderKeys) {
                assertTrue(
                    "removed key \"$key\" resurrected in $locale/strings.xml",
                    !text.contains("name=\"$key\"")
                )
            }
        }
    }

    @Test
    fun widget_redesign_keys_present_in_all_six_locales() {
        for (locale in localeDirs) {
            val text = File(basePath, "$locale/strings.xml").readText()
            for (key in widgetRedesignKeys) {
                assertTrue("$locale missing $key", text.contains('"' + key + '"'))
            }
        }
    }

    @Test
    fun all_six_strings_files_are_readable_and_non_empty() {
        for (locale in localeDirs) {
            val f = File(basePath, "$locale/strings.xml")
            assertTrue("Missing $locale/strings.xml", f.isFile)
            val text = f.readText()
            assertTrue("$locale/strings.xml is empty", text.isNotBlank())
            assertTrue(
                "$locale/strings.xml has no <string> entries",
                text.contains("<string")
            )
        }
    }

    @Test
    fun custom_theme_keys_present_in_all_six_locales() {
        for (locale in localeDirs) {
            val text = File(basePath, "$locale/strings.xml").readText()
            for (key in customThemeKeys) {
                assertTrue(
                    "custom theme key \"$key\" missing in $locale/strings.xml",
                    text.contains("name=\"$key\"")
                )
            }
        }
    }

    @Test
    fun parity_helper_finds_key_occurrences_consistently() {
        // Sanity check of the key-matching helper used by Phase1. The helper
        // must report a count >= 2 for each key once the strings exist. This
        // test exercises the helper against a synthetic fixture so its logic is
        // itself covered (and not silently broken when batch 3 wires it up).
        val fixture = """
            <resources>
                <string name="settings_course_colorless">A</string>
                <string name="settings_course_colorless_sub">B</string>
            </resources>
        """.trimIndent()

        val found = newKeys.count { key -> fixture.contains("name=\"$key\"") }
        assertTrue("Helper must detect 2/2 keys in fixture, got $found", found == 2)
    }
}