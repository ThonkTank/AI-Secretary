package de.thonktank.autosecretary.presentation.alltasks;

import de.thonktank.autosecretary.*;

import static android.view.View.MeasureSpec.EXACTLY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskCatalog;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskScheduleEntry;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public final class AllTasksInteractionTest {
    @Test public void stepReorderWorksThroughDragPathAndRealAccessibilityAction() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Recorder recorder = new Recorder();
        AtomicReference<AllTasksView> mounted = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            AllTasksView view = new AllTasksView(context, recorder);
            AllTasksUiState state = AllTasksUiState.from(catalog(), AllTasksFilter.defaults())
                    .toggleExpanded("first").toggleExpanded("second");
            view.bind(state, DayPalette.at(LocalTime.NOON, DayPalette.Mode.LIGHT));
            int width = Math.round(412 * context.getResources().getDisplayMetrics().density);
            int height = Math.round(1_000 * context.getResources().getDisplayMetrics().density);
            view.measure(View.MeasureSpec.makeMeasureSpec(width, EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, EXACTLY));
            view.layout(0, 0, width, height);
            mounted.set(view);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            AllTasksView view = mounted.get();
            int source = view.positionForTest(AllTasksRow.Kind.STEP, "first-b");
            RecyclerView.ViewHolder holder = view.recyclerForTest()
                    .findViewHolderForAdapterPosition(source);
            assertNotNull(holder);
            assertTrue(holder.itemView.performAccessibilityAction(R.id.action_step_up, null));
            assertEquals("first-b|first|first-a", recorder.stepMove);

            int dragSource = view.positionForTest(AllTasksRow.Kind.STEP, "first-a");
            int dragTarget = view.positionForTest(AllTasksRow.Kind.STEP_TARGET, "second:end");
            if (dragTarget < 0) dragTarget = view.positionForTest(
                    AllTasksRow.Kind.STEP_TARGET, "second|MORNING:end");
            assertTrue(view.dragForTest(dragSource, dragTarget));
            assertEquals("first-a|second|null", recorder.stepMove);
        });
    }

    @Test public void scheduleReorderAndSlotMoveHaveDragAndAccessibilityPaths() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Recorder recorder = new Recorder();
        AtomicReference<AllTasksView> mounted = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            AllTasksView view = new AllTasksView(context, recorder);
            view.bind(AllTasksUiState.from(catalog(), AllTasksFilter.defaults())
                            .withMode(AllTasksUiState.Mode.SORT),
                    DayPalette.at(LocalTime.NOON, DayPalette.Mode.LIGHT));
            int width = Math.round(412 * context.getResources().getDisplayMetrics().density);
            int height = Math.round(1_000 * context.getResources().getDisplayMetrics().density);
            view.measure(View.MeasureSpec.makeMeasureSpec(width, EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, EXACTLY));
            view.layout(0, 0, width, height);
            mounted.set(view);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            AllTasksView view = mounted.get();
            int first = view.positionForTest(AllTasksRow.Kind.SCHEDULE, "schedule-first");
            RecyclerView.ViewHolder holder = view.recyclerForTest()
                    .findViewHolderForAdapterPosition(first);
            assertNotNull(holder);
            assertTrue(holder.itemView.performAccessibilityAction(
                    R.id.action_schedule_next_slot, null));
            assertEquals("schedule-first|MIDDAY|null", recorder.scheduleMove);

            int eveningEnd = view.positionForTest(AllTasksRow.Kind.SCHEDULE_TARGET,
                    "EVENING:end");
            assertTrue(view.dragForTest(first, eveningEnd));
            assertEquals("schedule-first|EVENING|null", recorder.scheduleMove);
        });
    }

    private static TaskCatalog catalog() {
        Task first = task("first", 1_024);
        Task second = task("second", 2_048);
        return new TaskCatalog(Arrays.asList(
                new TaskCatalog.Item(first, Arrays.asList(
                        new TaskStepTemplate("first-a", first.id, 0, "A"),
                        new TaskStepTemplate("first-b", first.id, 1, "B")),
                        Collections.singletonList(new TaskScheduleEntry("schedule-first",
                                first.id, TaskSlot.MORNING, 1_024))),
                new TaskCatalog.Item(second, Collections.singletonList(
                        new TaskStepTemplate("second-a", second.id, 0, "C")),
                        Collections.singletonList(new TaskScheduleEntry("schedule-second",
                                second.id, TaskSlot.MORNING, 2_048)))));
    }

    private static Task task(String id, long order) {
        return Task.restore(TaskId.of(id), "Aufgabe " + id, Recurrence.DAILY, 1, 0,
                false, "", false, false, LocalDate.of(2026, 8, 21), null, null,
                order, false, null, de.thonktank.autosecretary.domain.model.TaskBoundKind.FOREVER,
                null, null, null, null, "");
    }

    private static final class Recorder implements AllTasksView.Listener {
        String stepMove;
        String scheduleMove;
        @Override public void onMoveStep(String stepId, String taskId, String beforeStepId) {
            stepMove = stepId + '|' + taskId + '|' + beforeStepId;
        }
        @Override public void onMoveSchedule(String entryId, TaskSlot slot, String beforeEntryId) {
            scheduleMove = entryId + '|' + slot + '|' + beforeEntryId;
        }
    }
}
