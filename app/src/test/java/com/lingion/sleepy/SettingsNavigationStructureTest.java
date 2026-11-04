package com.lingion.sleepy;

import org.junit.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.Assert.*;

/** User-approved settings ownership and navigation contracts. */
public class SettingsNavigationStructureTest {
    private String source(String relative) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("app/src/main"))) root = root.getParent();
        assertNotNull("repository root", root);
        return Files.readString(root.resolve("app/src/main/java/com/lingion/sleepy/" + relative));
    }
    private String screen(String name) throws Exception { return source("ui/screen/mine/" + name + ".kt"); }
    private void ordered(String text, String... tokens) {
        int cursor = -1;
        for (String token : tokens) {
            int next = text.indexOf(token, cursor + 1);
            assertTrue("Missing or misplaced " + token, next > cursor);
            cursor = next;
        }
    }
    @Test public void generalOnlyContainsLanguagePerformanceAndEditing() throws Exception {
        String s = screen("GeneralSettingsScreen");
        ordered(s.substring(s.indexOf("LazyColumn(")), "R.string.settings_language", "R.string.settings_high_refresh", "R.string.settings_undo_depth");
        for (String token : new String[]{"setNavDock", "setStartView", "setWeekTwoColumn", "setVertPunctReplace", "MigrationExecutor", "onOpenHoliday"})
            assertFalse("Moved setting remains in General: " + token, s.contains(token));
        assertTrue(s.contains("AppPrefs.setUndoDepth"));
        assertTrue(s.contains("UndoManager.applyMaxDepth"));
    }
    @Test public void dockStyleAndThemeShareAppearance() throws Exception {
        String s = screen("AppearanceScreen");
        ordered(s.substring(s.indexOf("LazyColumn(")), "R.string.settings_nav_style", "R.string.appearance_section_dock_theme");
        assertTrue(s.contains("AppPrefs.setNavDock"));
        assertTrue(s.contains("onNavDockChange"));
    }
    @Test public void navigationProvidesDisplayBackupAndScopedHoliday() throws Exception {
        String routes = source("ui/nav/SleepyRoutes.kt");
        String host = source("ui/nav/SleepyNavHost.kt");
        for (String name : new String[]{"ScheduleDisplay", "Backup", "TableHoliday"}) {
            assertTrue(routes.contains(" " + name));
            assertTrue(host.contains("entry<SleepyRoute." + name + ">"));
        }
        assertTrue(host.contains("initialTableId = key.tableId"));
        assertTrue(host.contains("onOpenScheduleDisplay = { navigator.openScheduleDisplay() }"));
    }
    @Test public void mineEntriesFollowApprovedOrder() throws Exception {
        String s = screen("MineScreen");
        ordered(s.substring(s.indexOf("LazyColumn(")), "R.string.all_tables", "R.string.mine_period_tables", "R.string.settings_holidays", "R.string.reminder_title", "R.string.mine_appearance", "R.string.mine_general", "R.string.mine_widget", "R.string.settings_backup", "R.string.about_title");
        String between = s.substring(s.indexOf("R.string.mine_general"), s.indexOf("R.string.mine_widget"));
        assertEquals(1, between.split("SettingsItem\\(", -1).length - 1);
    }
    @Test public void backupRetainsFullModulesAndBothRestoreModes() throws Exception {
        String s = screen("BackupScreen");
        for (String token : new String[]{"MigrationModule.DATABASE", "MigrationModule.PREFERENCES", "MigrationModule.WIDGETS", "ImportMode.OVERWRITE", "ImportMode.MERGE", "settings_migration_import_dialog_text", ".sleepybackup"})
            assertTrue("Backup lost " + token, s.contains(token));
    }
    @Test public void editingDraftSurvivesTheHolidayChildPage() throws Exception {
        String s = screen("EditTableScreen");
        for (String token : new String[]{"var name by rememberSaveable", "var startDate by rememberSaveable", "var maxWeekText by rememberSaveable", "var pendingBind by rememberSaveable", "var editSessionStarted by rememberSaveable", "viewModel.editStateFor(", "saver = TimeSlotRowsSaver", "stateSaver = SmartPeriodConfigSaver"})
            assertTrue("Edit draft must survive child navigation: " + token, s.contains(token));
    }
    @Test public void immutablePreferenceKeysRemainUnchanged() throws Exception {
        String prefs = source("util/AppPrefs.kt");
        for (String key : new String[]{"nav_dock", "start_view", "undo_depth", "grid_show_separators", "grid_long_break_spacing"})
            assertTrue(prefs.contains("\"" + key + "\""));
    }
}
