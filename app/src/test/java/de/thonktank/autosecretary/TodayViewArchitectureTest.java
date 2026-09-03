package de.thonktank.autosecretary;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public final class TodayViewArchitectureTest {
    @Test public void focusListDoesNotOwnCanonicalModelsOrPersistence() throws Exception {
        String source = source("ui/today/FocusStepListLayout.java");

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
        String row = source("ui/today/FocusStepRowView.java");
        String history = source("ui/today/CompletedTodayView.java");

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

    @Test public void focusProjectionHasNoLegacyStatusPromotionOrViewSelectionLogic()
            throws Exception {
        String step = new String(Files.readAllBytes(Path.of(
                "../today-core/src/main/java/de/thonktank/autosecretary/presentation/today/FocusStepUiModel.java")),
                StandardCharsets.UTF_8);
        String feature = new String(Files.readAllBytes(Path.of(
                "../today-core/src/main/java/de/thonktank/autosecretary/presentation/today/TodayFeatureState.java")),
                StandardCharsets.UTF_8);
        String list = source("ui/today/FocusStepListLayout.java");
        String task = source("ui/today/FocusTaskView.java");

        assertFalse(step.contains("FocusStepStatus"));
        assertFalse(step.contains("activeExecutionAction"));
        assertFalse(step.contains("executionAction"));
        assertFalse(feature.contains("promotedStepId"));
        assertTrue(feature.contains("selectedStepId"));
        assertFalse(list.contains("promoteForDisplay"));
        assertFalse(list.contains("activeStepId("));
        assertTrue(task.contains("bind(FocusCardUiModel model, boolean stacked"));
        assertFalse(task.contains("bind(FocusTaskUiModel task"));
    }

    private static String source(String name) throws Exception {
        return new String(Files.readAllBytes(Path.of(
                "src/main/java/de/thonktank/autosecretary", name)), StandardCharsets.UTF_8);
    }
}
