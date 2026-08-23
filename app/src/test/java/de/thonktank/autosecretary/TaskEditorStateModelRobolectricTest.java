package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.os.Bundle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.TaskBoundKind;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TimeOfDay;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class TaskEditorStateModelRobolectricTest {
    @Test public void currentBundleIsVersionedNestedAndRestoresAllThreeStateParts() {
        EditorStepState step = new EditorStepState("draft:1", "Laufen",
                StepCadenceMode.INTERVAL, 0, 3, StepAmount.duration(600), "locker");
        ValidationIssue issue = ValidationIssue.step(ValidationIssue.Field.STEP_AMOUNT, step.id);
        EditorUiState state = EditorUiState.create().draft("Training", TaskSlot.EVENING, 40,
                Recurrence.INTERVAL, 4, 0, TimeOfDay.EVENING.bit, TaskBoundKind.N_TIMES,
                null, null, 8, null, "Plan", Collections.singletonList(step), step.id, 2)
                .withPage(EditorUiState.Page.STEPS, true)
                .withValidationAttempt(EditorUiState.Page.STEPS, step.id,
                        Collections.singleton(issue))
                .withFeedback(Collections.singleton(issue), EditorUiState.Prompt.DISCARD,
                        "Speichern fehlgeschlagen");

        Bundle bundle = state.toBundle();
        assertEquals(2, bundle.getInt("format_version"));
        assertNotNull(bundle.getBundle("draft"));
        assertNotNull(bundle.getBundle("navigation"));
        assertNotNull(bundle.getBundle("feedback"));
        assertFalse(bundle.containsKey("title"));

        EditorUiState restored = EditorUiState.fromBundle(bundle);
        assertEquals(state.draft.snapshot(), restored.draft.snapshot());
        assertEquals(EditorUiState.Page.STEPS, restored.navigation.page);
        assertTrue(restored.navigation.returnToSummary);
        assertEquals(step.id, restored.navigation.expandedStepId);
        assertEquals(state.feedback.issues, restored.feedback.issues);
        assertEquals(state.feedback.attemptedStepIds, restored.feedback.attemptedStepIds);
        assertEquals(EditorUiState.Prompt.DISCARD, restored.feedback.prompt);
        assertEquals("Speichern fehlgeschlagen", restored.feedback.storageError);
        assertTrue(restored.dirty);
    }

    @Test public void legacyFlatBundleRestoresEquivalentDraftNavigationAndFeedback() {
        Bundle legacy = new Bundle();
        legacy.putBoolean("open", true); legacy.putString("task_id", "legacy-task");
        legacy.putString("title", "Altbestand"); legacy.putString("slot", "MIDDAY");
        legacy.putBoolean("estimated_set", true); legacy.putInt("estimated", 25);
        legacy.putString("recurrence", "WEEKDAYS"); legacy.putInt("interval", 1);
        legacy.putInt("weekdays", 5); legacy.putInt("times", TimeOfDay.MIDDAY.bit);
        legacy.putString("bound", "UNTIL_DATE"); legacy.putString("until", "2026-09-01");
        legacy.putString("note", "übernommen"); legacy.putInt("next_id", 2);
        EditorStepState step = new EditorStepState("step-1", "Anrufen", 0,
                StepAmount.repetitions(2), "");
        ArrayList<Bundle> steps = new ArrayList<>(); steps.add(step.toBundle());
        legacy.putParcelableArrayList("step_states", steps);
        legacy.putString("expanded", "step-1"); legacy.putString("page", "STEPS");
        legacy.putBoolean("return_summary", true);
        legacy.putStringArrayList("errors", new ArrayList<>(Collections.singletonList(
                "amount:step-1")));
        legacy.putStringArrayList("attempted_steps", new ArrayList<>(
                Collections.singletonList("step-1")));
        legacy.putString("prompt", "DELETE");

        EditorUiState restored = EditorUiState.fromBundle(legacy);
        TaskEditorDraft expectedDraft = new TaskEditorDraft("Altbestand", TaskSlot.MIDDAY, 25,
                Recurrence.WEEKDAYS, 1, 5, TimeOfDay.MIDDAY.bit,
                TaskBoundKind.UNTIL_DATE, LocalDate.of(2026, 9, 1), null, null, null,
                "übernommen", Collections.singletonList(step), 2);
        assertEquals(expectedDraft.snapshot(), restored.draft.snapshot());
        assertEquals("Altbestand", restored.title);
        assertEquals(TaskSlot.MIDDAY, restored.slot);
        assertEquals(Integer.valueOf(25), restored.estimatedMinutes);
        assertEquals(Recurrence.WEEKDAYS, restored.recurrence);
        assertEquals(LocalDate.of(2026, 9, 1), restored.boundUntilOn);
        assertEquals(step, restored.stepStates.get(0));
        assertEquals(EditorUiState.Page.STEPS, restored.page);
        assertTrue(restored.returnToSummary);
        assertEquals("step-1", restored.expandedStepId);
        assertTrue(restored.issues.contains(ValidationIssue.step(
                ValidationIssue.Field.STEP_AMOUNT, "step-1")));
        assertEquals(EditorUiState.Prompt.DELETE, restored.prompt);
        assertFalse(restored.dirty);
    }
}
