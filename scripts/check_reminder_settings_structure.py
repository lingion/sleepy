#!/usr/bin/env python3
"""Source contracts for settings placement and parent/child scope."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
MINE = ROOT / 'app/src/main/java/com/lingion/sleepy/ui/screen/mine'

def source(name):
    return (MINE / (name + '.kt')).read_text()

def body_at(text, start):
    opening = text.index('{', start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (text[end] == '{') - (text[end] == '}')
        end += 1
    return text[opening + 1:end - 1]

class ReminderSettingsStructureTest(unittest.TestCase):
    def test_reminder_order(self):
        text = source('ReminderScreen').split('LazyColumn(', 1)[1]
        labels = ['reminder_master_title', 'settings_table_reminder_scope',
                  'reminder_before_class_title', 'reminder_daily_title',
                  'reminder_date_rules_title', 'reminder_class_dnd_title',
                  'ReminderReliabilityCard(']
        positions = [text.index(label) for label in labels]
        self.assertEqual(positions, sorted(positions))

    def test_common_diagnostics_outside_reminder_gates(self):
        text = source('ReminderScreen')
        self.assertIn('ReminderReliabilityCard(', text)
        for gate in ['if (masterEnabled)', 'if (fluidEnabled)']:
            start = 0
            while (start := text.find(gate, start)) >= 0:
                self.assertNotIn('ReminderReliabilityCard(', body_at(text, start))
                start += len(gate)
        card = text[text.index('private fun ReminderReliabilityCard('):]
        for key in ['reminder_reliability_notification', 'reminder_reliability_exact_alarm',
                    'reminder_reliability_battery', 'reminder_reliability_widget']:
            self.assertIn(key, card)
        self.assertNotIn('reminder_reliability_vendor', card)
        self.assertNotIn('reminder_reliability_promoted', card)

    def test_date_rules_independent_and_explanation_not_repeated(self):
        text = source('ReminderScreen')
        self.assertEqual(text.count('stringResource(R.string.reminder_date_rules_advanced_subtitle)'), 1)
        start = 0
        while (start := text.find('if (masterEnabled)', start)) >= 0:
            self.assertNotIn('reminder_date_rules_title', body_at(text, start))
            start += 1

    def test_diagnostics_permission_does_not_enable_reminders(self):
        text = source('ReminderScreen')
        self.assertIn('var pendingMasterEnable by rememberSaveable', text)
        self.assertIn('requestNotificationPermission(enableReminders = true)', text)
        self.assertIn('onNotificationPermission = { requestNotificationPermission() }', text)

    def test_holiday_visuals_removed_and_scope_explicit(self):
        text = source('HolidaySettingsScreen')
        for key in ['setHolidayGreyHoliday', 'setHolidayGreyWeekend', 'setHolidayIgnoreWorkday', 'setHolidayStyle']:
            self.assertNotIn(key, text)
        self.assertIn('initialTableId: Long? = null', text)
        self.assertIn('initialTableId ?: tableId', text)
        self.assertNotIn('selectTable(', text)
        self.assertLess(text.index('settings_holiday_dates'), text.index('HolidayOrphanCard('))
        self.assertLess(text.index('holiday_add_entry'), text.index('HolidayRemovedCard('))

    def test_date_editing_is_inside_management_after_mappings(self):
        text = source('HolidaySettingsScreen')
        card = body_at(text, text.index('private fun HolidayTransferCard('))
        self.assertNotIn('onEditSegment', card)
        order = ['HolidayTransferCard(', 'settings_holiday_dates)', 'merged.active.forEach',
                 'HolidayDateManagementRow(', 'holiday_add_entry', 'settings_holiday_maintenance']
        positions = [text.index(label) for label in order]
        self.assertEqual(positions, sorted(positions))
        management = text[text.index('merged.active.forEach'):text.index('holiday_add_entry')]
        self.assertIn('resolveEditTarget(seg, userRangeIds)', management)

    def test_legacy_notification_diagnostic_opens_settings_without_enabling(self):
        text = source('ReminderScreen')
        request = body_at(text, text.index('fun requestNotificationPermission('))
        self.assertIn('else {\n                openAppNotificationSettings()', request)
        helper = body_at(text, text.index('fun openAppNotificationSettings('))
        self.assertIn('ACTION_APP_NOTIFICATION_SETTINGS', helper)
        self.assertIn('EXTRA_APP_PACKAGE', helper)
        self.assertNotIn('setReminderEnabled', helper)
        self.assertIn('permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)', request)

    def test_edit_table_link_between_slots_and_save(self):
        text = source('EditTableScreen')
        self.assertIn('onOpenHoliday: (Long) -> Unit', text)
        positions = [text.index(x) for x in ['TimeSlotEditor(', 'onOpenHoliday(table.id)', 'Text(stringResource(R.string.edit_table_save))']]
        self.assertEqual(positions, sorted(positions))

if __name__ == '__main__':
    unittest.main()
