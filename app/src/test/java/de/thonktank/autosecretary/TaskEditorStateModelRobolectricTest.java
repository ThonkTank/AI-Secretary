package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.os.Bundle;
import androidx.lifecycle.SavedStateHandle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.StepActivationKind;
import de.thonktank.autosecretary.domain.model.StepPrescription;
import de.thonktank.autosecretary.domain.model.TaskBoundKind;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.model.TimeOfDay;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class TaskEditorStateModelRobolectricTest {
    @Test public void sharedStepCodecPreservesAutomaticFollowUpRole() {
        TaskStepTemplate template = de.thonktank.autosecretary.testing.StepTestFixtures.template("hang", TaskId.of("laundry"), 0,
                "Aufhängen", 0, 0, StepAmount.none(), "", StepActivationKind.FOLLOW_UP);

        EditorStepState restored = EditorStepState.fromBundle(
                EditorStepState.from(template).toBundle());

        assertEquals(StepActivationKind.FOLLOW_UP, restored.activationKind);
        assertEquals(StepActivationKind.FOLLOW_UP,
                restored.definition(0, false).activationKind);
        assertEquals(0, restored.definition(0, false).weekdayMask);
        assertEquals(0, restored.definition(0, false).intervalDays);
    }

    @Test public void currentBundleIsVersionedNestedAndRestoresAllThreeStateParts() {
        EditorStepState step = new EditorStepState("draft:1", "Laufen",
                StepCadenceMode.INTERVAL, 0, 3,
                StepPrescription.forAmount(StepAmount.duration(600)), "locker",
                StepActivationKind.SCHEDULED);
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
        assertEquals(4, bundle.getInt("format_version"));
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

    @Test public void legacyFlowDraftRestoresAndNotifiesOnceAfterAcknowledgement() {
        SavedStateHandle handle = new SavedStateHandle();
        handle.set("editor", legacyFlowEditor(3, true));
        TaskEditorViewModel editor = restoredEditor(handle);
        try {
            EditorUiState restored = editor.state().getValue().content;
            assertEquals("Wäsche", restored.title);
            assertEquals("Über Nacht", restored.note);
            assertEquals(TaskSlot.EVENING, restored.slot);
            assertEquals(Recurrence.WEEKDAYS, restored.recurrence);
            assertEquals(EditorUiState.Page.STEPS, restored.page);
            assertTrue(restored.attemptedPages.contains(EditorUiState.Page.STEPS));
            assertTrue(restored.dirty);
            assertEquals("Ausgangsnotiz", restored.baseline.toBundle().getBundle("draft").getString("note"));
            assertEquals("draft:2", restored.stepStates.get(1).id);
            assertEquals("Aufhängen", restored.stepStates.get(1).text);
            assertEquals("Draußen", restored.stepStates.get(1).note);
            assertEquals(StepActivationKind.SCHEDULED, restored.stepStates.get(1).activationKind);
            assertEquals(3, restored.definition().steps.get(1).intervalDays);
            assertFalse(restored.toBundle().getBundle("draft").containsKey("flow"));

            TaskEditorRequest notice = editor.state().getValue().firstRequest();
            assertNotNull(notice);
            assertTrue(notice.notice);
            assertEquals(1, editor.state().getValue().requests.size());

            TaskEditorViewModel reopened = restoredEditor(handle);
            try {
                assertEquals(notice.id, reopened.state().getValue().firstRequest().id);
                reopened.dispatch(TaskEditorAction.acknowledgeRequest(notice.id));
            } finally {
                reopened.onCleared();
            }

            TaskEditorViewModel acknowledged = restoredEditor(handle);
            try {
                assertTrue(acknowledged.state().getValue().requests.isEmpty());
                assertEquals(restored.draft.snapshot(), acknowledged.state().getValue().content.draft.snapshot());
            } finally {
                acknowledged.onCleared();
            }
        } finally {
            editor.onCleared();
        }
    }

    @Test public void oldCapacityCatalogCopiesDoNotTriggerNoticeOrMakeDraftDirty() {
        Bundle oldEditor = legacyFlowEditor(2, false);
        assertFalse(EditorUiState.discardsLegacyFlow(oldEditor));
        EditorUiState migrated = EditorUiState.fromBundle(oldEditor);
        assertEquals(EditorUiState.Page.STEPS, migrated.page);
        assertFalse(migrated.dirty);
        assertFalse(migrated.toBundle().getBundle("baseline").getBundle("draft").containsKey("flow"));
    }

    private static TaskEditorViewModel restoredEditor(SavedStateHandle handle) {
        return new TaskEditorViewModel(null, null, null, null, null,
                (resource, args) -> Integer.toString(resource), handle, Executors.newSingleThreadExecutor());
    }

    private static Bundle legacyFlowEditor(int format, boolean hasRules) {
        Bundle first = new Bundle();
        first.putString("id", "draft:1");
        first.putString("text", "Maschine anmachen");
        Bundle second = new Bundle();
        second.putString("id", "draft:2");
        second.putString("text", "Aufhängen");
        second.putString("note", "Draußen");
        second.putString("cadence", "INTERVAL");
        second.putBoolean("interval_set", true);
        second.putInt("interval", 3);
        second.putString("activation", hasRules ? "FOLLOW_UP" : "SCHEDULED");

        Bundle resource = new Bundle();
        resource.putString("key", "rack");
        resource.putString("persisted", "rack");
        resource.putString("name", "Wäscheständer");
        resource.putInt("capacity", 2);
        Bundle flow = new Bundle();
        flow.putParcelableArrayList("resources", new ArrayList<>(List.of(resource)));
        if (hasRules) {
            Bundle transition = new Bundle();
            transition.putString("source", "draft:1");
            transition.putString("target", "draft:2");
            transition.putString("mode", "FIXED");
            transition.putLong("default", 7_200_000L);
            flow.putParcelableArrayList("transitions", new ArrayList<>(List.of(transition)));
        }

        Bundle draft = new Bundle();
        draft.putString("title", "Wäsche");
        draft.putString("note", "Über Nacht");
        draft.putString("slot", "EVENING");
        draft.putString("recurrence", "WEEKDAYS");
        draft.putInt("weekdays", 3);
        draft.putInt("times", TimeOfDay.EVENING.bit);
        draft.putInt("next_id", 3);
        draft.putParcelableArrayList("steps", new ArrayList<>(List.of(first, second)));
        draft.putBundle("flow", flow);
        Bundle baselineDraft = new Bundle(draft);
        if (hasRules) baselineDraft.putString("note", "Ausgangsnotiz");
        Bundle baseline = new Bundle();
        baseline.putBundle("draft", baselineDraft);

        Bundle navigation = new Bundle();
        navigation.putString("page", "FLOW");
        Bundle feedback = new Bundle();
        feedback.putStringArrayList("attempted_pages", new ArrayList<>(List.of("FLOW")));

        Bundle editor = new Bundle();
        editor.putBoolean("open", true);
        editor.putInt("format_version", format);
        editor.putBundle("draft", draft);
        editor.putBundle("baseline", baseline);
        editor.putBundle("navigation", navigation);
        editor.putBundle("feedback", feedback);
        return editor;
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
        EditorStepState step = new EditorStepState("step-1", "Anrufen",
                StepCadenceMode.ALWAYS, 0, null,
                StepPrescription.forAmount(StepAmount.repetitions(2)), "",
                StepActivationKind.SCHEDULED);
        ArrayList<Bundle> steps = new ArrayList<>(); steps.add(step.toBundle());
        legacy.putParcelableArrayList("step_states", steps);
        legacy.putString("expanded", "step-1"); legacy.putString("page", "FLOW");
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
