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


    private static String source(String name) throws IOException {
        Path path = sourcePath(name);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path sourcePath(String name) {
        Path module = Path.of("src/main/java/de/thonktank/autosecretary", name);
        return Files.exists(module) ? module
                : Path.of("app/src/main/java/de/thonktank/autosecretary", name);
    }
}
