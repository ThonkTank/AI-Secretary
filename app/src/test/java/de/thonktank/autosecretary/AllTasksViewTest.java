package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskBoundKind;
import de.thonktank.autosecretary.domain.model.TaskCatalog;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskScheduleEntry;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class AllTasksViewTest {
    @Test public void inventoryExpandsStepsWithoutOfferingCompletionActions() {
        Context context = ApplicationProvider.getApplicationContext();
        Recorder recorder = new Recorder();
        AllTasksView view = new AllTasksView(context, new ScrollView(context), recorder);
        AllTasksUiState state = AllTasksUiState.empty().withCatalog(catalog())
                .toggleExpanded("task");

        view.bind(state, DayPalette.at(LocalTime.NOON, DayPalette.Mode.LIGHT));

        java.util.List<AllTasksRow> rows = AllTasksRow.project(state);
        assertTrue(rows.stream().anyMatch(value -> value.kind == AllTasksRow.Kind.TASK_HEADER));
        assertTrue(rows.stream().anyMatch(value -> value.kind == AllTasksRow.Kind.STEP
                && value.step.text.equals("Duschen")));
        assertTrue(rows.stream().anyMatch(value -> value.kind == AllTasksRow.Kind.STEP_TARGET
                && value.endTarget));
        assertEquals(null, find(view, context.getString(R.string.action_complete)));
        find(view, context.getString(R.string.all_status_archived)).performClick();
        assertEquals(AllTasksUiState.Status.ARCHIVED, recorder.status);
    }

    @Test public void sortModeRendersOneRowPerTimePlacement() {
        Context context = ApplicationProvider.getApplicationContext();
        AllTasksView view = new AllTasksView(context, new ScrollView(context), new Recorder());
        AllTasksUiState state = AllTasksUiState.empty().withCatalog(catalog())
                .withMode(AllTasksUiState.Mode.SORT);

        view.bind(state, DayPalette.at(LocalTime.NOON, DayPalette.Mode.LIGHT));

        java.util.List<AllTasksRow> rows = AllTasksRow.project(state);
        assertEquals(2, rows.stream()
                .filter(value -> value.kind == AllTasksRow.Kind.SCHEDULE).count());
        assertTrue(rows.stream().anyMatch(value -> value.kind == AllTasksRow.Kind.SLOT_HEADER
                && value.slot == TaskSlot.MORNING));
        assertTrue(rows.stream().anyMatch(value -> value.kind == AllTasksRow.Kind.SLOT_HEADER
                && value.slot == TaskSlot.EVENING));
    }

    private static TaskCatalog catalog() {
        Task task = Task.restore(TaskId.of("task"), "Morgenroutine", TaskSlot.MORNING,
                Recurrence.DAILY, 1, 0, false, "", false, false,
                LocalDate.of(2026, 8, 20), null, null, 1_024, false, 30, 5,
                TaskBoundKind.FOREVER, null, null, null, null, "");
        return new TaskCatalog(Collections.singletonList(new TaskCatalog.Item(task,
                Arrays.asList(new TaskStepTemplate("one", task.id, 0, "Duschen"),
                        new TaskStepTemplate("two", task.id, 1, "Anziehen")),
                Arrays.asList(new TaskScheduleEntry("morning", task.id,
                                TaskSlot.MORNING, 1_024),
                        new TaskScheduleEntry("evening", task.id,
                                TaskSlot.EVENING, 1_024)))));
    }

    private static TextView find(View root, String text) {
        if (root instanceof TextView && text.contentEquals(((TextView) root).getText()))
            return (TextView) root;
        if (!(root instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) root;
        for (int index = 0; index < group.getChildCount(); index++) {
            TextView found = find(group.getChildAt(index), text);
            if (found != null) return found;
        }
        return null;
    }

    private static int count(View root, String text) {
        int result = root instanceof TextView && text.contentEquals(((TextView) root).getText()) ? 1 : 0;
        if (!(root instanceof ViewGroup)) return result;
        ViewGroup group = (ViewGroup) root;
        for (int index = 0; index < group.getChildCount(); index++)
            result += count(group.getChildAt(index), text);
        return result;
    }

    private static final class Recorder implements AllTasksView.Listener {
        AllTasksUiState.Status status;
        @Override public void onQuery(String query) { }
        @Override public void onStatus(AllTasksUiState.Status value) { status = value; }
        @Override public void onSlots(Set<TaskSlot> slots) { }
        @Override public void onRecurrences(Set<Recurrence> recurrences) { }
        @Override public void onWeekday(int weekday) { }
        @Override public void onMode(AllTasksUiState.Mode mode) { }
        @Override public void onToggleTask(String taskId) { }
        @Override public void onEditTask(String taskId) { }
        @Override public void onEditStep(String taskId, String stepId) { }
        @Override public void onAddStep(String taskId) { }
        @Override public void onDeleteTask(String taskId, String title) { }
        @Override public void onMoveSchedule(String entryId, TaskSlot slot, String beforeEntryId) { }
        @Override public void onMoveStep(String stepId, String taskId, String beforeStepId) { }
        @Override public void onSwapSteps(String stepId, String targetStepId) { }
    }
}
