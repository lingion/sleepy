"""Source contracts for the approved settings hierarchy; run with python3."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
MINE = ROOT / 'app/src/main/java/com/lingion/sleepy/ui/screen/mine'

def ordered(source, *tokens):
    positions = [source.index(token) for token in tokens]
    return positions == sorted(positions)

class AppearanceOrganizationContract(unittest.TestCase):
    def test_appearance_groups_and_dock_pair(self):
        source = (MINE / 'AppearanceScreen.kt').read_text()
        self.assertTrue(ordered(source, 'R.string.theme_appearance', 'R.string.appearance_section_theme',
            'R.string.settings_appearance_navigation', 'R.string.settings_nav_style',
            'R.string.appearance_section_dock_theme', 'R.string.appearance_section_course_color'))
        self.assertNotIn('onOpenScheduleDisplay', source)
        self.assertIn('AppPrefs.setNavDock(context, on)', source)
        self.assertIn('onNavDockChange(on)', source)
        self.assertNotIn('ScheduleDisplayContent(', source)
    def test_schedule_settings_is_a_first_level_mine_entry(self):
        source = (MINE / 'MineScreen.kt').read_text()
        body = source[source.index('LazyColumn('):]
        self.assertTrue(ordered(body, 'R.string.mine_appearance', 'R.string.mine_schedule_settings',
            'R.string.mine_general', 'R.string.mine_widget'))
        self.assertIn('onClick = onOpenScheduleDisplay', body)
        self.assertIn('R.string.mine_schedule_settings', (MINE / 'ScheduleDisplayScreen.kt').read_text())
        for filename in ('MainActivity.kt', 'ui/nav/SleepyNavHost.kt'):
            navigation = (MINE.parents[2] / filename).read_text()
            self.assertIn('onOpenScheduleDisplay = { navigator.openScheduleDisplay() }', navigation)
    def test_display_group_order_and_header_navigation(self):
        source = (MINE / 'ScheduleDisplayScreen.kt').read_text()
        body = source[source.index('fun ScheduleDisplayScreen('):]
        self.assertTrue(ordered(body, 'CommonDisplaySettings(', 'WeekDisplaySettings(',
            'GridDisplaySettings(', 'onClick = onOpenPeriodHeaderSettings',
            'DisplayStyleSection.CONFLICT', 'HolidayDisplaySettings('))
        self.assertIn('verticalScroll(rememberScrollState())', body)
        self.assertIn('LocalNavExtraBottomPadding', source)
    def test_common_controls_are_ordered(self):
        source = (MINE / 'ScheduleDisplayGroups.kt').read_text()
        source = source[source.index('fun CommonDisplaySettings('):source.index('fun WeekDisplaySettings(')]
        self.assertTrue(ordered(source, 'settings_appearance_default_view', 'settings_show_view_switcher',
            'settings_visible_days', 'settings_appearance_header_date', 'settings_display_mode',
            'settings_nearest_busy_day'))
        self.assertIn('next.isNotEmpty()', source)
        self.assertIn('AppPrefs.setNearestBusyDay(context, it)', source)
    def test_week_controls_keep_the_two_column_dependency(self):
        source = (MINE / 'ScheduleDisplayGroups.kt').read_text()
        source = source[source.index('fun WeekDisplaySettings('):source.index('fun GridDisplaySettings(')]
        self.assertTrue(ordered(source, 'DisplayStyleSection.WEEK', 'settings_week_two_column',
            'if (twoColumn)', 'settings_week_two_column_days', 'settings_week_two_column_balance',
            'settings_week_hide_empty', 'settings_week_alias'))
    def test_grid_controls_and_evening_dependency(self):
        source = (MINE / 'ScheduleDisplayGroups.kt').read_text()
        source = source[source.index('fun GridDisplaySettings('):source.index('fun HolidayDisplaySettings(')]
        self.assertTrue(ordered(source, 'DisplayStyleSection.GRID', 'settings_grid_sub_info',
            'settings_grid_alias', 'settings_appearance_advanced_experimental', 'settings_grid_adaptive_height',
            'settings_grid_pinch_zoom', 'settings_grid_auto_hide_evening', 'if (autoHideEvening)',
            'settings_grid_evening_start', 'settings_grid_show_separators', 'settings_grid_long_break_spacing'))
    def test_holiday_visual_options_stay_together(self):
        source = (MINE / 'ScheduleDisplayGroups.kt').read_text()
        source = source[source.index('fun HolidayDisplaySettings('):]
        self.assertTrue(ordered(source, 'settings_holiday_holiday', 'settings_holiday_weekend',
            'if (weekendGrey)', 'settings_appearance_workday_visible', 'settings_holiday_style'))
        for setter in ('setHolidayGreyHoliday', 'setHolidayGreyWeekend', 'setHolidayIgnoreWorkday', 'setHolidayStyle'):
            self.assertIn('AppPrefs.' + setter, source)
    def test_time_alignment_hidden_when_time_is_hidden(self):
        source = (MINE / 'PeriodHeaderSettingsScreen.kt').read_text()
        self.assertTrue(ordered(source, 'if (layout == "legacy")', 'if (!hideTime)', 'appearance_header_time_alignment'))
    def test_theme_editor_keeps_save_before_delete(self):
        source = (MINE / 'CustomThemeEditorScreen.kt').read_text()
        self.assertTrue(ordered(source, 'R.string.theme_editor_from_seed', 'R.string.theme_editor_random',
            'R.string.theme_editor_manual', 'Text(stringResource(R.string.save)', 'Text(stringResource(R.string.delete)'))

if __name__ == '__main__':
    unittest.main()
