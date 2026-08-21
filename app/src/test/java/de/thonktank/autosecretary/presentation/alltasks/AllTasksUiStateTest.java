package de.thonktank.autosecretary.presentation.alltasks;

import de.thonktank.autosecretary.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskCatalog;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskScheduleEntry;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;

public final class AllTasksUiStateTest {
    private static final LocalDate MONDAY = LocalDate.of(2026, 8, 17);

    @Test public void filterModelIsAndroidFreeAndImmutable() {
        AllTasksFilter filter = AllTasksFilter.defaults()
                .withQuery("  Gym  ").withSlots(EnumSet.of(TaskSlot.MORNING));

        assertEquals("  Gym  ", filter.query);
        assertEquals(EnumSet.of(TaskSlot.MORNING), filter.slots);
        assertTrue(Arrays.stream(AllTasksFilter.class.getDeclaredFields())
                .noneMatch(field -> field.getType().getName().startsWith("android.")));
    }

    @Test public void searchAndFiltersInspectTaskAndNestedStepText() {
        TaskCatalog catalog = catalog();
        AllTasksUiState state = AllTasksUiState.empty().withCatalog(catalog)
                .withQuery("kniebeugen");

        assertEquals(1, state.tasks.size());
        assertEquals("gym", state.tasks.get(0).task.id.value);

        state = state.withQuery("").withSlots(EnumSet.of(TaskSlot.EVENING))
                .withRecurrences(EnumSet.of(Recurrence.DAILY));
        assertEquals(1, state.tasks.size());
        assertEquals("gym", state.tasks.get(0).task.id.value);
    }

    @Test public void scheduleRepeatsMultiTimeTasksAndAppliesAbstractWeekdayRules() {
        AllTasksUiState state = AllTasksUiState.empty().withCatalog(catalog())
                .withMode(AllTasksUiState.Mode.SORT);

        assertEquals(3, state.schedule.size());
        assertEquals(TaskSlot.MORNING, state.schedule.get(0).slot);
        assertEquals(TaskSlot.EVENING, state.schedule.get(2).slot);

        state = state.withWeekday(2);
        assertEquals(2, state.schedule.size());
        assertTrue(state.schedule.stream().allMatch(value -> value.taskId.equals("gym")));

        state = state.withWeekday(1);
        assertEquals(3, state.schedule.size());
    }

    @Test public void archiveIsHiddenByDefaultAndReadablyFilterable() {
        AllTasksUiState state = AllTasksUiState.empty().withCatalog(catalog());
        assertEquals(2, state.tasks.size());
        state = state.withStatus(AllTasksUiState.Status.ARCHIVED);
        assertEquals(1, state.tasks.size());
        assertTrue(state.tasks.get(0).archived);
        assertTrue(state.schedule.isEmpty());
    }

    private static TaskCatalog catalog() {
        Task gym = Task.restore(TaskId.of("gym"), "Gym", Recurrence.DAILY, 1, 0,
                false, "", false, false, MONDAY, null, null, 1_024, false, 45,
                de.thonktank.autosecretary.domain.model.TaskBoundKind.FOREVER,
                null, null, null, null, "Training");
        Task weekday = Task.restore(TaskId.of("weekday"), "Büro", Recurrence.WEEKDAYS,
                1, 1, false, "", false, false, MONDAY, null, null, 2_048, false, null,
                de.thonktank.autosecretary.domain.model.TaskBoundKind.FOREVER,
                null, null, null, null, "");
        Task archived = Task.restore(TaskId.of("archive"), "Alt", Recurrence.ONCE,
                1, 0, false, "", false, true, null, MONDAY, MONDAY, 3_072, true, null,
                de.thonktank.autosecretary.domain.model.TaskBoundKind.FOREVER,
                null, null, null, null, "");
        return new TaskCatalog(Arrays.asList(
                new TaskCatalog.Item(gym, Collections.singletonList(
                        new TaskStepTemplate("s1", gym.id, 0, "Kniebeugen", 0,
                                StepAmount.none(), "tief")), Arrays.asList(
                        new TaskScheduleEntry("gm", gym.id, TaskSlot.MORNING, 1_024),
                        new TaskScheduleEntry("ge", gym.id, TaskSlot.EVENING, 1_024))),
                new TaskCatalog.Item(weekday, Collections.emptyList(), Collections.singletonList(
                        new TaskScheduleEntry("wm", weekday.id, TaskSlot.MIDDAY, 1_024))),
                new TaskCatalog.Item(archived, Collections.emptyList(), Collections.singletonList(
                        new TaskScheduleEntry("al", archived.id, TaskSlot.LATER, 1_024)))));
    }
}
