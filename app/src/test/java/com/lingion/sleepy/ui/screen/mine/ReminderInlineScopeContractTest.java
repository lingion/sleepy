package com.lingion.sleepy.ui.screen.mine;

import org.junit.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.Assert.*;

/** Locks the parent page's render order and navigation boundary, without starting Android. */
public class ReminderInlineScopeContractTest {
    private String source(String relative) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("app/src/main"))) root = root.getParent();
        assertNotNull("Repository root must be available", root);
        return Files.readString(root.resolve("app/src/main/java/com/lingion/sleepy/" + relative));
    }

    private String body(String source, String function) {
        int declaration = source.indexOf("fun " + function + "(");
        assertTrue("Missing function: " + function, declaration >= 0);
        // Start after the signature: default callback arguments can contain braces.
        int opening = source.indexOf(") {", declaration) + 2;
        assertTrue("Missing body: " + function, opening > declaration);
        int depth = 1;
        for (int i = opening + 1; i < source.length(); i++) {
            if (source.charAt(i) == '{') depth++;
            if (source.charAt(i) == '}' && --depth == 0) return source.substring(opening + 1, i);
        }
        throw new AssertionError("Unclosed function: " + function);
    }

    private void ordered(String source, String... tokens) {
        int previous = -1;
        for (String token : tokens) {
            int current = source.indexOf(token, previous + 1);
            assertTrue("Missing or out-of-order setting: " + token, current > previous);
            previous = current;
        }
    }

    @Test public void scopeKeepsItsPositionAndIsAvailableWithMasterOff() throws Exception {
        String page = body(source("ui/screen/mine/ReminderScreen.kt"), "ReminderScreen");
        page = page.substring(page.indexOf("LazyColumn("));
        ordered(page, "R.string.reminder_master_title", "R.string.settings_reminder_permissions_attention",
            "TableReminderCard(", "if (masterEnabled)", "R.string.reminder_before_class_title");
        assertEquals("There must be one scope card", 1, page.split("TableReminderCard\\(", -1).length - 1);
        assertTrue("The card must receive the live master toggle", page.contains("masterEnabled = masterEnabled"));
    }

    @Test public void reminderPageOpensScopeInlineWithoutCallingLegacyNavigation() throws Exception {
        String page = body(source("ui/screen/mine/ReminderScreen.kt"), "ReminderScreen");
        assertTrue("Scope must be composed in the current page", page.contains("TableReminderCard("));
        assertFalse("The former navigation callback must not be invoked or passed to a click target",
            page.contains("onOpenTableReminder"));
    }

    @Test public void collapsedCardAndLegacyDestinationShareTheSameContent() throws Exception {
        String tables = source("ui/screen/mine/TableReminderScreen.kt");
        String card = body(tables, "TableReminderCard");
        assertTrue("Expansion must survive recomposition and start collapsed",
            card.contains("rememberSaveable { mutableStateOf(false) }"));
        assertTrue("Reuse the existing accordion card", card.contains("SettingsCard("));
        assertTrue(card.contains("TableReminderContent("));
        assertTrue(body(tables, "TableReminderScreen").contains("TableReminderContent("));
        String navigation = source("ui/nav/SleepyNavHost.kt");
        assertTrue(navigation.contains("entry<SleepyRoute.TableReminder>"));
        assertTrue(navigation.contains("TableReminderScreen(onBack = { navigator.pop() })"));
    }

    @Test public void sharedContentAcknowledgesBothObservedTablesAndWriteReturn() throws Exception {
        String content = body(source("ui/screen/mine/TableReminderScreen.kt"), "TableReminderContent");
        ordered(content, "val tables = vm.tables.collectAsStateWithLifecycle(initialValue = null).value",
            "val pending = vm.pending", "SideEffect { vm.acknowledgeObservedTables(tables) }", "Column(");
        assertTrue(content.contains("key(table.id)"));
        assertTrue(content.contains("table.id !in pending"));
        assertTrue(content.contains("vm.setEnabled(table.id, checked)"));
        assertTrue(content.contains("R.string.table_reminder_save_failed"));
    }
}
