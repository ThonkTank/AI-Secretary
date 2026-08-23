package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
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
        Context context = ApplicationProvider.getApplicationContext();
        RecordingListener listener = new RecordingListener();
        TaskEditorView view = new TaskEditorView(context, listener);
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
