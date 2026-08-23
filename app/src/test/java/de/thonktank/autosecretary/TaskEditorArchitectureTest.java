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
        String viewModel = source("TaskViewModel.java");
        assertTrue(viewModel.contains("TaskEditorStateReducer."));
        assertFalse(viewModel.contains("draft.withSaving("));
        assertFalse(viewModel.contains("draft.withFeedback("));
        assertFalse(viewModel.contains("draft.withAllValidationAttempted("));
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
