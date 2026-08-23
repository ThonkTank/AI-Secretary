package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import de.thonktank.autosecretary.editor.TaskEditorStateReducer;

public final class TaskEditorStateReducerTest {
    @Test public void stepTransitionsPreserveStableDraftIdentityAndOrdering() {
        EditorUiState original = EditorUiState.create();
        EditorUiState first = TaskEditorStateReducer.addStep(original);
        EditorUiState second = TaskEditorStateReducer.addStep(first);

        assertEquals("draft:1", first.stepStates.get(0).id);
        assertEquals("draft:2", second.stepStates.get(1).id);
        assertEquals("draft:2", second.expandedStepId);
        assertEquals(3, second.nextDraftIdentity);

        EditorUiState named = TaskEditorStateReducer.updateStep(second, 1,
                second.stepStates.get(1).withText("Rudern"));
        EditorUiState moved = TaskEditorStateReducer.moveStep(named, 1, 0);
        assertEquals("Rudern", moved.stepStates.get(0).text);
        assertEquals("draft:2", moved.expandedStepId);

        EditorUiState removed = TaskEditorStateReducer.removeStep(moved, 0);
        assertEquals(1, removed.stepStates.size());
        assertNull(removed.expandedStepId);
        assertEquals(3, removed.nextDraftIdentity);
    }

    @Test public void invalidMoveIsANoOp() {
        EditorUiState state = TaskEditorStateReducer.addStep(EditorUiState.create());
        assertSame(state, TaskEditorStateReducer.moveStep(state, 0, 4));
    }

    @Test public void navigationDoesNotMakeTheDraftDirty() {
        EditorUiState original = EditorUiState.create();
        EditorUiState navigated = original.withPage(EditorUiState.Page.SCHEDULE, true);

        assertEquals(EditorUiState.Page.SCHEDULE, navigated.page);
        assertEquals(true, navigated.returnToSummary);
        assertEquals(false, navigated.dirty);
    }
}
