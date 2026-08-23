package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;
import java.time.LocalDate;

import de.thonktank.autosecretary.editor.TaskEditorStateReducer;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskBoundKind;
import de.thonktank.autosecretary.domain.model.TimeOfDay;

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

    @Test public void validationAttemptsAndTypedIssuesDoNotMakeTheDraftDirty() {
        EditorUiState original = EditorUiState.create();
        ValidationIssue issue = ValidationIssue.task(ValidationIssue.Field.TITLE);

        EditorUiState attempted = original.withValidationAttempt(EditorUiState.Page.TITLE,
                null, Collections.singleton(issue));

        assertFalse(attempted.dirty);
        assertTrue(attempted.attemptedPages.contains(EditorUiState.Page.TITLE));
        assertTrue(attempted.issues.contains(issue));
    }

    @Test public void everyTaskFieldTransitionIsPureAndDirtyCanReturnToBaseline() {
        EditorUiState original = EditorUiState.create();
        EditorUiState titled = TaskEditorStateReducer.updateTitle(original, "Lesen");
        assertEquals("", original.title);
        assertEquals("Lesen", titled.title);
        assertTrue(titled.dirty);

        EditorUiState reverted = TaskEditorStateReducer.updateTitle(titled, "  ");
        assertFalse(reverted.dirty);

        EditorUiState changed = TaskEditorStateReducer.updateNote(original, "Notiz");
        changed = TaskEditorStateReducer.updateDuration(changed, 45);
        changed = TaskEditorStateReducer.updateInterval(changed, 4);
        changed = TaskEditorStateReducer.updateWeekdays(changed, 5);
        changed = TaskEditorStateReducer.updateDeadline(changed, LocalDate.of(2026, 8, 24));
        assertEquals("Notiz", changed.note);
        assertEquals(Integer.valueOf(45), changed.estimatedMinutes);
        assertEquals(4, changed.intervalDays);
        assertEquals(5, changed.weekdayMask);
        assertEquals(LocalDate.of(2026, 8, 24), changed.deadlineOn);
        assertFalse(original.dirty);
    }

    @Test public void recurrenceTimeAndBoundTransitionsEnforceEditorInvariants() {
        LocalDate today = LocalDate.of(2026, 8, 23);
        EditorUiState state = TaskEditorStateReducer.updateRecurrence(
                EditorUiState.create(), Recurrence.WEEKDAYS);
        assertEquals(1, state.weekdayMask);

        state = TaskEditorStateReducer.toggleTime(state, TimeOfDay.EVENING);
        assertTrue((state.timeOfDayMask & TimeOfDay.EVENING.bit) != 0);
        EditorUiState unchanged = TaskEditorStateReducer.toggleTime(
                TaskEditorStateReducer.toggleTime(state, TimeOfDay.MORNING),
                TimeOfDay.EVENING);
        assertEquals(TimeOfDay.EVENING.bit, unchanged.timeOfDayMask);

        state = TaskEditorStateReducer.updateBoundKind(state, TaskBoundKind.FOR_WEEKS, today);
        assertEquals(Integer.valueOf(1), state.boundWeeks);
        assertEquals(today.plusWeeks(1), state.boundUntilOn);
        state = TaskEditorStateReducer.updateBound(state, today.plusWeeks(3), 3, null);
        assertEquals(Integer.valueOf(3), state.boundWeeks);
        assertEquals(today.plusWeeks(3), state.boundUntilOn);
    }

    @Test public void navigationFeedbackAndSaveRoutingNeverChangeTheDraftSnapshot() {
        EditorUiState original = EditorUiState.create();
        ValidationIssue issue = ValidationIssue.step(ValidationIssue.Field.STEP_TITLE, "draft:1");
        EditorUiState navigated = TaskEditorStateReducer.navigate(original,
                EditorUiState.Page.STEPS, true);
        EditorUiState attempted = TaskEditorStateReducer.validationAttempt(navigated,
                EditorUiState.Page.STEPS, "draft:1", Collections.singleton(issue));
        EditorUiState prompted = TaskEditorStateReducer.feedback(attempted,
                attempted.issues, EditorUiState.Prompt.DISCARD, "");
        EditorUiState routed = TaskEditorStateReducer.routeValidationFailure(prompted,
                Collections.singleton(issue), EditorUiState.Page.STEPS, "draft:1");
        EditorUiState saving = TaskEditorStateReducer.saving(routed, true);

        assertFalse(saving.dirty);
        assertEquals(EditorUiState.Page.STEPS, saving.page);
        assertEquals("draft:1", saving.expandedStepId);
        assertTrue(saving.saving);
        assertEquals(EditorUiState.Prompt.DISCARD, saving.prompt);
        assertTrue(saving.attemptedPages.contains(EditorUiState.Page.STEPS));
    }
}
