package de.thonktank.autosecretary;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Guards the state/rendering boundaries introduced by the task-editor refactor. */
public final class TaskEditorArchitectureTest {
    @Test public void viewsDelegateStateChangesAndControlConstruction() throws IOException {
        String editor = source("TaskEditorView.java");
        String steps = source("TaskStepsEditorView.java");

        assertTrue(editor.contains("TaskEditorStateReducer."));
        assertTrue(steps.contains("TaskEditorStateReducer."));
        assertTrue(editor.contains("TaskEditorControlFactory"));
        assertTrue(steps.contains("TaskEditorControlFactory"));
        assertFalse(editor.contains("new EditText("));
        assertFalse(steps.contains("new EditText("));
        assertFalse(editor.contains("state.draft("));
        assertFalse(editor.contains("state.withFeedback("));
        assertFalse(steps.contains("state.draft("));
        String editorApply = editor.substring(editor.indexOf("private void apply(EditorUiState"),
                editor.indexOf("private static void traceState"));
        assertTrue(editorApply.indexOf("listener.onDraftChanged(validated)")
                < editorApply.indexOf("state = validated"));
        String stepsApply = steps.substring(steps.indexOf("private void apply(EditorUiState"),
                steps.indexOf("private static LayoutParams params"));
        assertTrue(stepsApply.indexOf("listener.onStateChanged(next, rerender)")
                < stepsApply.indexOf("state = next"));
        String viewModel = source("TaskEditorViewModel.java");
        assertTrue(viewModel.contains("TaskEditorStateReducer."));
        assertFalse(viewModel.contains("draft.withSaving("));
        assertFalse(viewModel.contains("draft.withFeedback("));
        assertFalse(viewModel.contains("draft.withAllValidationAttempted("));
    }

    @Test public void editorHasOneStateFlowOwnerOutsideDashboardState() throws IOException {
        String editorOwner = source("TaskEditorViewModel.java");
        String dashboardOwner = source("TaskViewModel.java");
        String dashboardState = source("DashboardUiState.java");
        String activity = source("MainActivity.java");

        assertTrue(editorOwner.contains("StateFlow<TaskEditorScreenState> state()"));
        assertTrue(editorOwner.contains("void dispatch(TaskEditorAction action)"));
        assertFalse(editorOwner.contains("LiveData"));
        assertFalse(dashboardOwner.contains("EditorUiState"));
        assertFalse(dashboardOwner.contains("TaskEditorStateReducer"));
        assertFalse(dashboardState.contains("EditorUiState"));
        assertTrue(activity.contains("LegacyStateFlowBinder.observe(this,"
                + " editorViewModel.state()"));
    }

    @Test public void editorStateUsesSeparatedModelsAndVersionedBundle() throws IOException {
        String state = source("EditorUiState.java");
        assertTrue(state.contains("TaskEditorDraft draft"));
        assertTrue(state.contains("TaskEditorNavigation navigation"));
        assertTrue(state.contains("TaskEditorFeedback feedback"));
        assertTrue(state.contains("TaskDraftSnapshot baseline"));
        assertTrue(state.contains("format_version"));
        assertTrue(state.contains("fromLegacyBundle"));
    }

    @Test public void visibleSummaryCopyLivesOutsideViews() throws IOException {
        String editor = source("TaskEditorView.java");
        String steps = source("TaskStepsEditorView.java");
        assertTrue(editor.contains("TaskEditorTextFormatter"));
        assertTrue(steps.contains("TaskEditorTextFormatter"));
        assertFalse(editor.contains("private String summaryLine"));
        assertFalse(steps.contains("\"alle \" +"));
        assertFalse(steps.contains("\", ausgewählt\""));
    }

    private static String source(String name) throws IOException {
        Path module = Path.of("src/main/java/de/thonktank/autosecretary", name);
        Path path = Files.exists(module) ? module
                : Path.of("app/src/main/java/de/thonktank/autosecretary", name);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
