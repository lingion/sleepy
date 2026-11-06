package com.lingion.sleepy.ui.screen.mine;

import org.junit.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.Assert.*;

/** Settings hierarchy: these assertions lock navigation and action order. */
public class WidgetMiscOrganizationContractTest {
    private String source(String relative) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("app/src/main"))) root = root.getParent();
        assertNotNull("Repository root must be available", root);
        return Files.readString(root.resolve("app/src/main/java/com/lingion/sleepy/ui/screen/" + relative));
    }

    private void ordered(String source, String... tokens) {
        int previous = -1;
        for (String token : tokens) {
            int position = source.indexOf(token, previous + 1);
            assertTrue("Missing or out-of-order setting: " + token, position > previous);
            previous = position;
        }
    }

    @Test public void widgetPageStartsWithManagementAndEndsWithConditionalRefresh() throws Exception {
        String s = source("mine/WidgetSettingsScreen.kt");
        s = s.substring(s.indexOf("LazyColumn("));
        ordered(s, "R.string.widget_manage_entry", "R.string.settings_widget_global_appearance",
            "R.string.settings_widget_colorless", "R.string.settings_widget_separator",
            "R.string.settings_widget_shared_display", "R.string.settings_widget_advanced",
            "R.string.settings_widget_vert_punct_experimental", "if (items.isNotEmpty())", "R.string.mine_refresh_widgets");
    }

    @Test public void sharedDateBehaviorHasOneOwnerAndWidgetPunctuationKeepsItsPreference() throws Exception {
        String s = source("mine/WidgetSettingsScreen.kt");
        assertTrue(s.contains("onOpenScheduleDisplay: () -> Unit"));
        assertTrue(s.contains("noRippleClickable(onOpenScheduleDisplay)"));
        assertFalse(s.contains("AppPrefs.setNearestBusyDay"));
        assertTrue(s.contains("AppPrefs.isVertPunctReplace(context)"));
        assertTrue(s.contains("AppPrefs.setVertPunctReplace(context, it)"));
        assertTrue(s.contains("WidgetUpdater.notifyDataChanged"));
    }

    @Test public void instanceLayoutOptionsPrecedeExperimentalScrolling() throws Exception {
        String s = source("widget/WidgetEditScreen.kt");
        s = s.substring(s.indexOf("val sections:"));
        ordered(s, "WidgetEditScheduleSection", "WidgetEditAliasSection", "WidgetEditCompactWindowSection",
            "WidgetEditWideGridSection", "WidgetEditScrollSection");
        String scroll = source("widget/WidgetEditScrollSection.kt");
        assertTrue(scroll.contains("titleRes: Int = R.string.settings_widget_advanced"));
        assertTrue(scroll.contains("if (scope.receiverSimpleName?.contains(\"WeekGrid\") == true) return"));
        assertTrue(scroll.contains("if (showConfirm)"));
    }

    @Test public void emptyWidgetListOffersAnAddAction() throws Exception {
        String s = source("widget/WidgetManagementScreen.kt");
        s = s.substring(s.indexOf("if (items.isEmpty())"));
        s = s.substring(0, s.indexOf("} else {"));
        assertTrue(s.contains("FilledTonalButton(onClick = onOpenWidgetGallery"));
        assertTrue(s.contains("R.string.widget_gallery_title"));
    }

    @Test public void aboutGroupsUpdateControlsThenSupportThenProjectInformation() throws Exception {
        String s = source("mine/AboutScreen.kt");
        s = s.substring(s.indexOf("// Version info card"));
        ordered(s, "stringResource(R.string.about_version)", "stringResource(R.string.about_update)", "stringResource(R.string.about_update_check)",
            "stringResource(R.string.about_feedback)", "stringResource(R.string.about_qq_title)", "stringResource(R.string.about_author)",
            "stringResource(R.string.about_source)", "stringResource(R.string.about_privacy)", "stringResource(R.string.about_license_title)");
    }

    @Test public void exportGroupsSharingBeforeCalendarActionsWithoutChangingPeriodFormats() throws Exception {
        String s = source("mine/ExportScreen.kt");
        ordered(s, "R.string.period_table_share_native_title", "R.string.period_table_share_json_title");
        s = s.substring(s.indexOf("} else if (table != null) {"));
        ordered(s, "R.string.settings_export_sharing", "R.string.settings_export_native_file_title",
            "R.string.export_share_title", "R.string.export_json_title", "R.string.settings_export_calendar_assistants",
            "R.string.calendar_import_title", "R.string.export_ics_title", "R.string.export_xiaoai_title");
        assertTrue(s.contains("SleepyNativeExporter.exportFile("));
        assertTrue(s.contains("ScheduleExporter.exportWakeUpJson("));
        assertTrue(s.contains("openCalendarImportForCurrentTarget()"));
        assertTrue(s.contains("launchXiaoaiImport("));
    }
}
