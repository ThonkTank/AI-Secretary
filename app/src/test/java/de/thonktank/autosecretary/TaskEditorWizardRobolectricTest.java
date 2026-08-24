package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.shadows.ShadowLooper.shadowMainLooper;

import android.content.Context;
import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.EditText;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.TaskBoundKind;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TimeOfDay;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w412dp-h892dp-xhdpi")
public final class TaskEditorWizardRobolectricTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 23);

    @Test public void createFlowAdvancesAndEditingStartsOnSummary() {
        Activity context = Robolectric.buildActivity(Activity.class).setup().get();
        RecordingListener listener = new RecordingListener();
        TaskEditorView view = new TaskEditorView(context, listener);
        context.setContentView(view);
        EditorUiState create = validState(new ArrayList<>()).withPage(EditorUiState.Page.TITLE,
                false);
        view.bind(create, palette(), TODAY);

        ((Button) view.findViewById(R.id.task_editor_save)).performClick();
        assertEquals(EditorUiState.Page.SCHEDULE, listener.draft.page);

        Bundle bundle = create.toBundle();
        bundle.putString("task_id", "task");
        EditorUiState edit = EditorUiState.fromBundle(bundle)
                .withPage(EditorUiState.Page.SUMMARY, false);
        view.bind(edit, palette(), TODAY);
        LinearLayout leaf = view.findViewById(R.id.task_editor_leaf);
        leaf.getChildAt(0).performClick();
        assertEquals(EditorUiState.Page.TITLE, listener.draft.page);
        assertTrue(listener.draft.returnToSummary);
    }

    @Test public void contentScrollsOnlyWhenTheMeasuredPageOverflows() {
        Context context = ApplicationProvider.getApplicationContext();
        TaskEditorView view = new TaskEditorView(context, new RecordingListener());
        view.bind(validState(new ArrayList<>()), palette(), TODAY);
        measure(view);
        ScrollView scroll = view.findViewById(R.id.task_editor_scroll);
        assertFalse(scroll.canScrollVertically(1));

        List<EditorStepState> steps = new ArrayList<>();
        for (int index = 0; index < 12; index++)
            steps.add(new EditorStepState("s" + index, "Schritt " + (index + 1), 0,
                    StepAmount.none(), ""));
        EditorUiState crowded = validState(steps).withPage(EditorUiState.Page.STEPS, false);
        view.bind(crowded, palette(), TODAY);
        measure(view);
        assertTrue(scroll.canScrollVertically(1));
    }

    @Test public void samePageRebindKeepsScrollWhileRealNavigationResetsIt() {
        Activity context = Robolectric.buildActivity(Activity.class).setup().get();
        TaskEditorView view = new TaskEditorView(context, new RecordingListener());
        context.setContentView(view);
        List<EditorStepState> steps = new ArrayList<>();
        for (int index = 0; index < 18; index++)
            steps.add(new EditorStepState("s" + index, "Schritt " + index, 0,
                    StepAmount.none(), ""));
        EditorUiState state = validState(steps).withPage(EditorUiState.Page.STEPS, false);
        DayPalette palette = palette();
        view.bind(state, palette, TODAY);
        measure(view);
        ScrollView scroll = view.findViewById(R.id.task_editor_scroll);
        scroll.scrollTo(0, 180);

        view.bind(state.withFeedback(state.issues, EditorUiState.Prompt.NONE, ""),
                palette, TODAY);
        shadowMainLooper().idle();
        assertEquals(180, scroll.getScrollY());

        view.bind(state.withPage(EditorUiState.Page.TITLE, false), palette, TODAY);
        shadowMainLooper().idle();
        assertEquals(0, scroll.getScrollY());
    }

    @Test public void correctingAttemptedTitleRemovesIssueAndReenablesNextWithoutLosingFocus() {
        Activity context = Robolectric.buildActivity(Activity.class).setup().get();
        RecordingListener listener = new RecordingListener();
        TaskEditorView view = new TaskEditorView(context, listener);
        context.setContentView(view);
        EditorUiState invalid = validState(new ArrayList<>()).draft("", TaskSlot.MORNING,
                30, Recurrence.DAILY, 1, 0, TimeOfDay.MORNING.bit,
                TaskBoundKind.FOREVER, null, null, null, null, "",
                new ArrayList<>(), null, 1);
        view.bind(invalid, palette(), TODAY);
        Button next = view.findViewById(R.id.task_editor_save);

        next.performClick();
        assertFalse(next.isEnabled());
        EditText title = view.findViewWithTag("task:title");
        title.requestFocus();
        title.setText("Korrigierter Titel");
        shadowMainLooper().idle();

        EditText restored = view.findViewWithTag("task:title");
        assertTrue(restored.hasFocus());
        assertEquals(restored.length(), restored.getSelectionStart());
        assertTrue(next.isEnabled());
        assertTrue(listener.draft.issues.isEmpty());
        next.performClick();
        assertEquals(EditorUiState.Page.SCHEDULE, listener.draft.page);
    }

    @Test public void correctingAttemptedStepTitleAndAmountReenablesApply() {
        Activity context = Robolectric.buildActivity(Activity.class).setup().get();
        RecordingListener listener = new RecordingListener();
        TaskEditorView view = new TaskEditorView(context, listener);
        context.setContentView(view);
        List<EditorStepState> steps = new ArrayList<>();
        steps.add(new EditorStepState("step", "", StepCadenceMode.ALWAYS, 0, null,
                StepAmount.setsReps(0, 12), ""));
        EditorUiState invalid = validState(steps).withPage(EditorUiState.Page.STEPS, false)
                .withExpandedStep("step");
        view.bind(invalid, palette(), TODAY);
        measure(view);
        Button apply = view.findViewById(R.id.task_editor_save);

        apply.performClick();
        assertFalse(apply.isEnabled());
        ((EditText) view.findViewWithTag("step:step:title")).setText("Liegestütze");
        assertFalse(apply.isEnabled());
        ScrollView scroll = view.findViewById(R.id.task_editor_scroll);
        scroll.scrollTo(0, 80);
        int previousScroll = scroll.getScrollY();
        EditText sets = view.findViewWithTag("step:step:sets");
        sets.requestFocus();
        sets.setSelection(sets.length());
        sets.setText("3");
        shadowMainLooper().idle();

        assertTrue(apply.isEnabled());
        assertTrue(listener.draft.issues.isEmpty());
        assertTrue(((EditText) view.findViewWithTag("step:step:sets")).hasFocus());
        assertEquals(previousScroll, scroll.getScrollY());
        apply.performClick();
        assertEquals(null, listener.draft.expandedStepId);
    }

    @Test public void blankStepIntervalRemainsSelectedInvalidAndCanBeCorrected() {
        Context context = ApplicationProvider.getApplicationContext();
        RecordingListener listener = new RecordingListener();
        TaskEditorView view = new TaskEditorView(context, listener);
        List<EditorStepState> steps = new ArrayList<>();
        steps.add(new EditorStepState("step", "Gießen", StepCadenceMode.INTERVAL, 0, 2,
                StepAmount.none(), ""));
        EditorUiState state = validState(steps).withPage(EditorUiState.Page.STEPS, false)
                .withExpandedStep("step");
        view.bind(state, palette(), TODAY);
        Button apply = view.findViewById(R.id.task_editor_save);
        EditText interval = view.findViewWithTag("step:step:interval");

        interval.setText("");
        assertEquals(StepCadenceMode.INTERVAL,
                listener.draft.stepStates.get(0).cadenceMode);
        assertEquals(null, listener.draft.stepStates.get(0).intervalDays);
        apply.performClick();
        assertFalse(apply.isEnabled());
        assertTrue(listener.draft.issues.contains(ValidationIssue.step(
                ValidationIssue.Field.STEP_INTERVAL, "step")));

        ((EditText) view.findViewWithTag("step:step:interval")).setText("3");
        assertTrue(apply.isEnabled());
        assertEquals(Integer.valueOf(3), listener.draft.stepStates.get(0).intervalDays);
    }

    private static EditorUiState validState(List<EditorStepState> steps) {
        EditorUiState state = EditorUiState.create(TaskSlot.MORNING);
        return state.draft("Morgenroutine", TaskSlot.MORNING, 30, Recurrence.DAILY, 1, 0,
                TimeOfDay.MORNING.bit, TaskBoundKind.FOREVER, null, null, null, null,
                "", steps, null, steps.size() + 1);
    }

    private static DayPalette palette() {
        return DayPalette.at(LocalTime.of(9, 40), DayPalette.Mode.LIGHT);
    }

    private static void measure(View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(824, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1784, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 824, 1784);
    }

    private static final class RecordingListener implements TaskEditorView.Listener {
        EditorUiState draft;
        @Override public void onDraftChanged(EditorUiState draft) { this.draft = draft; }
        @Override public void onSave(EditorUiState draft) { }
        @Override public void onDelete(String taskId) { }
        @Override public void onDismiss() { }
    }
}
