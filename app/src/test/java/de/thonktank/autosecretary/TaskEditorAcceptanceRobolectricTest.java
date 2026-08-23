package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.TaskBoundKind;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TimeOfDay;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w412dp-h892dp-xhdpi")
public final class TaskEditorAcceptanceRobolectricTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 23);

    @Test public void createJourneyVisitsPagesOneThroughFourAndSaves() {
        Harness harness = harness(valid(Collections.emptyList(), EditorUiState.Page.TITLE));
        Button primary = harness.view.findViewById(R.id.task_editor_save);

        primary.performClick(); assertEquals(EditorUiState.Page.SCHEDULE, harness.listener.draft.page);
        primary.performClick(); assertEquals(EditorUiState.Page.STEPS, harness.listener.draft.page);
        primary.performClick(); assertEquals(EditorUiState.Page.SUMMARY, harness.listener.draft.page);
        primary.performClick();

        assertNotNull(harness.listener.saved);
        assertEquals("Morgenroutine", harness.listener.saved.definition().title);
    }

    @Test public void hardwareBackCoversPromptDetailReturnTargetPagesAndDismissal() {
        assertBackPage(valid(Collections.emptyList(), EditorUiState.Page.SCHEDULE),
                EditorUiState.Page.TITLE);
        assertBackPage(valid(Collections.emptyList(), EditorUiState.Page.STEPS),
                EditorUiState.Page.SCHEDULE);
        assertBackPage(valid(Collections.emptyList(), EditorUiState.Page.SUMMARY),
                EditorUiState.Page.STEPS);

        EditorStepState step = step("one", "Dehnen");
        Harness detail = harness(valid(Collections.singletonList(step), EditorUiState.Page.STEPS)
                .withExpandedStep(step.id));
        assertTrue(detail.view.handleBack());
        assertNull(detail.listener.draft.expandedStepId);

        Harness returnTarget = harness(valid(Collections.emptyList(), EditorUiState.Page.TITLE)
                .withPage(EditorUiState.Page.TITLE, true));
        assertTrue(returnTarget.view.handleBack());
        assertEquals(EditorUiState.Page.SUMMARY, returnTarget.listener.draft.page);

        EditorUiState prompted = valid(Collections.emptyList(), EditorUiState.Page.TITLE)
                .withFeedback(Collections.emptySet(), EditorUiState.Prompt.DISCARD, "");
        Harness prompt = harness(prompted);
        assertTrue(prompt.view.handleBack());
        assertEquals(EditorUiState.Prompt.NONE, prompt.listener.draft.prompt);

        Harness cleanCreate = harness(EditorUiState.create());
        assertTrue(cleanCreate.view.handleBack());
        assertTrue(cleanCreate.listener.dismissed);

        Harness dirtyCreate = harness(valid(Collections.emptyList(), EditorUiState.Page.TITLE));
        assertTrue(dirtyCreate.view.handleBack());
        assertEquals(EditorUiState.Prompt.DISCARD, dirtyCreate.listener.draft.prompt);

        Harness cleanEdit = harness(clean(valid(Collections.emptyList(), EditorUiState.Page.SUMMARY),
                "edit", EditorUiState.Page.SUMMARY, null));
        assertTrue(cleanEdit.view.handleBack());
        assertTrue(cleanEdit.listener.dismissed);
    }

    @Test public void visibleBackAndDiscardLinksCoverTheirOwnNavigationPaths() {
        Harness title = harness(valid(Collections.emptyList(), EditorUiState.Page.TITLE));
        assertEquals(View.GONE,
                title.view.findViewById(R.id.task_editor_discard).getVisibility());

        Harness schedule = harness(valid(Collections.emptyList(), EditorUiState.Page.SCHEDULE));
        schedule.view.findViewById(R.id.task_editor_discard).performClick();
        assertEquals(EditorUiState.Page.TITLE, schedule.listener.draft.page);

        Harness steps = harness(valid(Collections.emptyList(), EditorUiState.Page.STEPS));
        steps.view.findViewById(R.id.task_editor_discard).performClick();
        assertEquals(EditorUiState.Page.SCHEDULE, steps.listener.draft.page);

        Harness returnTarget = harness(valid(Collections.emptyList(), EditorUiState.Page.SCHEDULE)
                .withPage(EditorUiState.Page.SCHEDULE, true));
        returnTarget.view.findViewById(R.id.task_editor_discard).performClick();
        assertEquals(EditorUiState.Page.SUMMARY, returnTarget.listener.draft.page);

        Harness summary = harness(valid(Collections.emptyList(), EditorUiState.Page.SUMMARY));
        summary.view.findViewById(R.id.task_editor_discard).performClick();
        assertEquals(EditorUiState.Prompt.DISCARD, summary.listener.draft.prompt);

        EditorStepState step = step("one", "Dehnen");
        Harness detail = harness(valid(Collections.singletonList(step), EditorUiState.Page.STEPS)
                .withExpandedStep(step.id));
        detail.view.findViewById(R.id.task_editor_cancel).performClick();
        assertNull(detail.listener.draft.expandedStepId);
    }

    @Test public void everySummaryChangeRowReturnsToSummaryAndProgressStaysHidden() {
        EditorStepState step = step("one", "Dehnen");
        EditorUiState summary = clean(valid(Collections.singletonList(step),
                EditorUiState.Page.SUMMARY), "edit", EditorUiState.Page.SUMMARY, null);
        EditorUiState.Page[] targets = {EditorUiState.Page.TITLE, EditorUiState.Page.SCHEDULE,
                EditorUiState.Page.SCHEDULE, EditorUiState.Page.SCHEDULE,
                EditorUiState.Page.TITLE, EditorUiState.Page.STEPS, EditorUiState.Page.TITLE};

        for (int index = 0; index < targets.length; index++) {
            Harness harness = harness(summary);
            assertEquals(View.GONE,
                    harness.view.findViewById(R.id.task_editor_progress).getVisibility());
            LinearLayout leaf = harness.view.findViewById(R.id.task_editor_leaf);
            leaf.getChildAt(index).performClick();
            assertEquals(targets[index], harness.listener.draft.page);
            assertTrue(harness.listener.draft.returnToSummary);
            ((Button) harness.view.findViewById(R.id.task_editor_save)).performClick();
            assertEquals(EditorUiState.Page.SUMMARY, harness.listener.draft.page);
        }
    }

    @Test public void cancelDeleteKeepAndConfirmPathsAreExplicit() {
        Harness unchanged = harness(EditorUiState.create());
        unchanged.view.findViewById(R.id.task_editor_cancel).performClick();
        assertTrue(unchanged.listener.dismissed);

        Harness changed = harness(valid(Collections.singletonList(step("one", "Dehnen")),
                EditorUiState.Page.TITLE));
        changed.view.findViewById(R.id.task_editor_cancel).performClick();
        clickDialogText(changed.context, R.string.ask_discard_keep);
        assertEquals(EditorUiState.Prompt.NONE, changed.listener.draft.prompt);
        changed.view.findViewById(R.id.task_editor_cancel).performClick();
        clickDialogText(changed.context, R.string.ask_discard_confirm);
        assertTrue(changed.listener.dismissed);

        EditorUiState edit = clean(valid(Collections.singletonList(step("one", "Dehnen")),
                EditorUiState.Page.SUMMARY), "edit", EditorUiState.Page.SUMMARY, null);
        Harness deleting = harness(edit);
        deleting.view.findViewById(R.id.task_editor_delete).performClick();
        clickDialogText(deleting.context, R.string.ask_delete_keep);
        assertEquals(EditorUiState.Prompt.NONE, deleting.listener.draft.prompt);
        deleting.view.findViewById(R.id.task_editor_delete).performClick();
        clickDialogText(deleting.context, R.string.ask_delete_confirm);
        assertEquals("edit", deleting.listener.deletedTaskId);
    }

    @Test public void stepsCanBeAddedEditedMovedAndRemoved() {
        List<EditorStepState> initial = Arrays.asList(step("one", "Erster"),
                step("two", "Zweiter"));
        Harness adding = harness(valid(initial, EditorUiState.Page.STEPS));
        TaskStepsEditorView stepsView = (TaskStepsEditorView) ((LinearLayout) adding.view
                .findViewById(R.id.task_editor_leaf)).getChildAt(0);
        stepsView.getChildAt(stepsView.getChildCount() - 1).performClick();
        String addedId = adding.listener.draft.expandedStepId;
        assertEquals(3, adding.listener.draft.stepStates.size());
        ((EditText) adding.view.findViewWithTag("step:" + addedId + ":title"))
                .setText("Dritter");
        ((Button) adding.view.findViewById(R.id.task_editor_save)).performClick();
        assertNull(adding.listener.draft.expandedStepId);
        assertEquals("Dritter", adding.listener.draft.stepStates.get(2).text);

        Harness moving = harness(valid(initial, EditorUiState.Page.STEPS));
        TaskStepsEditorView movingSteps = (TaskStepsEditorView) ((LinearLayout) moving.view
                .findViewById(R.id.task_editor_leaf)).getChildAt(0);
        LinearLayout secondRow = (LinearLayout) movingSteps.getChildAt(2);
        secondRow.getChildAt(2).performClick();
        assertEquals("Zweiter", moving.listener.draft.stepStates.get(0).text);

        Harness removing = harness(valid(initial, EditorUiState.Page.STEPS)
                .withExpandedStep("one"));
        removing.view.findViewById(R.id.task_editor_delete).performClick();
        assertEquals(1, removing.listener.draft.stepStates.size());
        assertEquals("two", removing.listener.draft.stepStates.get(0).id);
    }

    @Test public void summaryValidationRoutesToTheFirstInvalidTaskOrStepField() {
        EditorStepState invalidStep = step("broken", "").withAmount(StepAmount.repetitions(0));
        EditorUiState invalidTitle = valid(Collections.singletonList(invalidStep),
                EditorUiState.Page.SUMMARY).draft("", TaskSlot.MORNING, 30,
                Recurrence.DAILY, 1, 0, TimeOfDay.MORNING.bit, TaskBoundKind.FOREVER,
                null, null, null, null, "", Collections.singletonList(invalidStep), null, 2);
        Harness title = harness(invalidTitle.withPage(EditorUiState.Page.SUMMARY, false));
        title.view.findViewById(R.id.task_editor_save).performClick();
        assertEquals(EditorUiState.Page.TITLE, title.listener.draft.page);
        assertTrue(title.listener.draft.returnToSummary);

        Harness step = harness(valid(Collections.singletonList(invalidStep),
                EditorUiState.Page.SUMMARY));
        step.view.findViewById(R.id.task_editor_save).performClick();
        assertEquals(EditorUiState.Page.STEPS, step.listener.draft.page);
        assertEquals("broken", step.listener.draft.expandedStepId);
    }

    private static void assertBackPage(EditorUiState state, EditorUiState.Page expected) {
        Harness harness = harness(state);
        assertTrue(harness.view.handleBack());
        assertEquals(expected, harness.listener.draft.page);
    }

    private static EditorStepState step(String id, String title) {
        return new EditorStepState(id, title, 0, StepAmount.none(), "");
    }

    private static EditorUiState valid(List<EditorStepState> steps, EditorUiState.Page page) {
        return EditorUiState.create().draft("Morgenroutine", TaskSlot.MORNING, 30,
                Recurrence.DAILY, 1, 0, TimeOfDay.MORNING.bit, TaskBoundKind.FOREVER,
                null, null, null, null, "", steps, null, steps.size() + 1)
                .withPage(page, false);
    }

    private static EditorUiState clean(EditorUiState state, String taskId,
                                       EditorUiState.Page page, String expandedStepId) {
        return new EditorUiState(true, false, false, taskId, state.draft,
                new TaskEditorNavigation(page, false, expandedStepId),
                TaskEditorFeedback.empty(), state.draft.snapshot());
    }

    private static Harness harness(EditorUiState state) {
        Activity context = Robolectric.buildActivity(Activity.class).setup().get();
        RecordingListener listener = new RecordingListener();
        TaskEditorView view = new TaskEditorView(context, listener);
        context.setContentView(view);
        view.bind(state, DayPalette.at(LocalTime.of(9, 40), DayPalette.Mode.LIGHT), TODAY);
        return new Harness(context, view, listener);
    }

    private static void clickDialogText(Context context, int resource) {
        Dialog dialog = ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        TextView target = findText(dialog.getWindow().getDecorView(), context.getString(resource));
        assertNotNull(target);
        target.performClick();
    }

    private static TextView findText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText()))
            return (TextView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                TextView result = findText(group.getChildAt(index), text);
                if (result != null) return result;
            }
        }
        return null;
    }

    private static final class Harness {
        final Activity context;
        final TaskEditorView view;
        final RecordingListener listener;
        Harness(Activity context, TaskEditorView view, RecordingListener listener) {
            this.context = context; this.view = view; this.listener = listener;
        }
    }

    private static final class RecordingListener implements TaskEditorView.Listener {
        EditorUiState draft;
        EditorUiState saved;
        boolean dismissed;
        String deletedTaskId;
        @Override public void onDraftChanged(EditorUiState value) { draft = value; }
        @Override public void onSave(EditorUiState value) { saved = value; }
        @Override public void onDelete(String taskId) { deletedTaskId = taskId; }
        @Override public void onDismiss() { dismissed = true; }
    }
}
