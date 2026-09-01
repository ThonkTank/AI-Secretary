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
    @Test public void composeRendererDelegatesEveryDraftToTheExistingOwner() throws IOException {
        String host = kotlinSource("presentation/editor/TaskEditorComposeHostView.kt");
        String pages = kotlinSource("presentation/editor/TaskEditorComposePages.kt");
        String steps = kotlinSource("presentation/editor/TaskEditorComposeSteps.kt");

        assertTrue(pages.contains("TaskEditorStateReducer."));
        assertTrue(steps.contains("TaskEditorStateReducer."));
        assertTrue(host.contains("listener?.onDraftChanged(it)"));
        assertFalse(host.contains("onDraftChanged = { editorState ="));
        assertFalse(host.contains("MutableStateFlow"));
        String viewModel = source("TaskEditorViewModel.java");
        assertTrue(viewModel.contains("TaskEditorStateReducer."));
        assertFalse(viewModel.contains("draft.withSaving("));
        assertFalse(viewModel.contains("draft.withFeedback("));
        assertFalse(viewModel.contains("draft.withAllValidationAttempted("));
    }

    @Test public void editorHasOneStateFlowOwnerOutsideDashboardState() throws IOException {
        String editorOwner = source("TaskEditorViewModel.java");
        String dashboardOwner = source("presentation/today/TodayViewModel.java");
        String activity = source("MainActivity.java");

        assertTrue(editorOwner.contains("StateFlow<TaskEditorScreenState> state()"));
        assertTrue(editorOwner.contains("void dispatch(TaskEditorAction action)"));
        assertFalse(editorOwner.contains("LiveData"));
        assertFalse(dashboardOwner.contains("EditorUiState"));
        assertFalse(dashboardOwner.contains("TaskEditorStateReducer"));
        assertFalse(Files.exists(sourcePath("DashboardUiState.java")));
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
        String renderer = kotlinSource("presentation/editor/TaskEditorComposeScreen.kt")
                + kotlinSource("presentation/editor/TaskEditorComposePages.kt")
                + kotlinSource("presentation/editor/TaskEditorComposeSteps.kt");
        assertTrue(renderer.contains("TaskEditorTextFormatter"));
        assertFalse(renderer.contains("private fun summaryLine"));
        assertFalse(renderer.contains("\"alle \" +"));
        assertFalse(renderer.contains("\", ausgewählt\""));
    }

    @Test public void trainingAssistantSectionOwnsCanonicalEditorControls() throws IOException {
        String steps = kotlinSource("presentation/editor/TaskEditorComposeSteps.kt");
        String section = kotlinSource("presentation/editor/TrainingAssistantEditorSection.kt");

        assertTrue(steps.contains("TrainingAssistantEditorSection("));
        assertFalse(steps.contains("TrainingAssistantInputs"));
        assertFalse(steps.contains("trainingLoadModes"));
        assertFalse(steps.contains("trainingMuscleLabel"));
        assertTrue(section.contains("prescription: StepPrescription"));
        assertTrue(section.contains("policy: TrainingAssistantPolicy?"));
        assertTrue(section.contains("assistantState: TrainingAssistantState"));
        assertTrue(section.contains("onChange: (StepPrescription, TrainingAssistantPolicy?)"));
        assertFalse(section.contains("EditorStepState"));
        assertFalse(section.contains("EditorUiState"));
        assertFalse(section.contains("TaskEditorComposeDispatcher"));
    }

    private static String source(String name) throws IOException {
        Path path = sourcePath(name);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String kotlinSource(String name) throws IOException {
        Path module = Path.of("src/main/kotlin/de/thonktank/autosecretary", name);
        Path path = Files.exists(module) ? module
                : Path.of("app/src/main/kotlin/de/thonktank/autosecretary", name);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path sourcePath(String name) {
        Path module = Path.of("src/main/java/de/thonktank/autosecretary", name);
        return Files.exists(module) ? module
                : Path.of("app/src/main/java/de/thonktank/autosecretary", name);
    }
}
