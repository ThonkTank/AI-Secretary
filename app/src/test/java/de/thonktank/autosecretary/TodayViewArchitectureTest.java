package de.thonktank.autosecretary;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public final class TodayViewArchitectureTest {
    @Test public void focusListDoesNotOwnCanonicalModelsOrPersistence() throws Exception {
        String source = source("FocusStepListLayout.java");

        assertFalse(source.contains("boundModel"));
        assertFalse(source.contains("boundEvents"));
        assertFalse(source.contains("dropped"));
        assertFalse(source.contains("persistDrop"));
        assertFalse(source.contains("DashboardEvent"));
        assertFalse(source.contains("private final List<FocusStepUiModel>"));
        assertTrue(source.contains("TodayAction.beginReorder"));
        assertTrue(source.contains("TodayAction.previewReorder"));
        assertTrue(source.contains("TodayAction.dropReorder"));
    }

    @Test public void todayRowsAndHistoryEmitOnlyTypedTodayActions() throws Exception {
        String row = source("FocusStepRowView.java");
        String history = source("CompletedTodayView.java");

        assertFalse(row.contains("DashboardEvent"));
        assertFalse(history.contains("DashboardEvent"));
        assertTrue(row.contains("StepExecutionUiAction"));
        assertTrue(history.contains("TodayAction.undoOccurrence"));
    }

    @Test public void reorderTestsUsePublicViewsAndActionsWithoutReflection() throws Exception {
        String tests = new String(Files.readAllBytes(Path.of(
                "src/test/java/de/thonktank/autosecretary/FocusTaskViewTest.java")),
                StandardCharsets.UTF_8);
        assertFalse(tests.contains("ReflectionHelpers"));
        assertFalse(tests.contains("callInstanceMethod"));
        assertTrue(tests.contains("performLongClick()"));
        assertTrue(tests.contains("performAccessibilityAction"));
    }

    private static String source(String name) throws Exception {
        return new String(Files.readAllBytes(Path.of(
                "src/main/java/de/thonktank/autosecretary", name)), StandardCharsets.UTF_8);
    }
}
