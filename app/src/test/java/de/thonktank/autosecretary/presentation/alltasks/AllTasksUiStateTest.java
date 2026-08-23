package de.thonktank.autosecretary.presentation.alltasks;

import de.thonktank.autosecretary.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
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
        assertEquals(EnumSet.of(FilterField.QUERY, FilterField.STATUS, FilterField.SLOTS,
                        FilterField.RECURRENCES, FilterField.WEEKDAY),
                Arrays.stream(AllTasksFilter.class.getDeclaredFields())
                        .map(field -> FilterField.valueOf(field.getName().toUpperCase()))
                        .collect(java.util.stream.Collectors.toCollection(
                                () -> EnumSet.noneOf(FilterField.class))));
    }

    @Test public void presentationOwnsModeExpansionAndFilterVisibilityByValue() {
        String cardKey = AllTasksUiState.cardKey("gym", TaskSlot.MORNING);
        AllTasksPresentationState first = AllTasksPresentationState.defaults()
                .withFilter(AllTasksFilter.defaults().withQuery("Gym"))
                .toggleExpanded(cardKey).withFiltersExpanded(false);
        AllTasksPresentationState same = new AllTasksPresentationState(
                AllTasksFilter.defaults().withQuery("Gym"), AllTasksUiState.Mode.LIST,
                Collections.singleton(cardKey), false);

        assertEquals(first, same);
        assertEquals(first.hashCode(), same.hashCode());
        assertNotEquals(first, same.withFiltersExpanded(true));
        assertFalse(first.expandedCardKeys.isEmpty());
    }

    @Test public void rowContentsUseTypedValueEquality() {
        AllTasksUiState state = AllTasksUiState.empty().withCatalog(catalog());
        AllTasksRow first = AllTasksRow.project(state).get(0);
        AllTasksRow same = AllTasksRow.project(
                AllTasksUiState.empty().withCatalog(catalog())).get(0);
        AllTasksRow expanded = AllTasksRow.project(state.toggleExpanded(first.cardKey)).get(0);

        assertFalse(String.class.isInstance(first.content));
        assertEquals(first.content, same.content);
        assertEquals(first.content.hashCode(), same.content.hashCode());
        assertNotEquals(first.content, expanded.content);
        assertNotEquals(new AllTasksRowContent.Schedule("Gym", TaskSlot.MORNING,
                        1_024, Recurrence.DAILY),
                new AllTasksRowContent.Schedule("Gym", TaskSlot.MORNING,
                        1_024, Recurrence.ONCE));
    }

    @Test public void searchAndFiltersInspectTaskAndNestedStepText() {
        TaskCatalog catalog = catalog();
        AllTasksUiState state = AllTasksUiState.empty().withCatalog(catalog)
                .withQuery("kniebeugen");

        assertEquals(2, state.tasks.size());
        assertEquals("gym", state.tasks.get(0).task.id.value);
        assertEquals(TaskSlot.MORNING, state.tasks.get(0).slot);
        assertEquals(TaskSlot.EVENING, state.tasks.get(1).slot);
        assertTrue(state.tasks.stream().allMatch(value -> value.searchExpanded));

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
        assertEquals(3, state.tasks.size());
        state = state.withStatus(AllTasksUiState.Status.ARCHIVED);
        assertEquals(1, state.tasks.size());
        assertTrue(state.tasks.get(0).archived);
        assertTrue(state.schedule.isEmpty());
    }

    @Test public void placementCardsExpandIndependentlyAndCountAgainstStatusPool() {
        AllTasksUiState state = AllTasksUiState.empty().withCatalog(catalog());

        assertEquals(3, state.taskPoolSize);
        assertEquals(3, state.tasks.size());
        state = state.toggleExpanded(AllTasksUiState.cardKey("gym", TaskSlot.EVENING));

        assertTrue(state.tasks.stream().filter(value -> value.task.id.value.equals("gym"))
                .anyMatch(value -> value.slot == TaskSlot.EVENING && value.expanded));
        assertTrue(state.tasks.stream().filter(value -> value.task.id.value.equals("gym"))
                .anyMatch(value -> value.slot == TaskSlot.MORNING && !value.expanded));
    }

    @Test public void searchDoesNotInspectTaskOrStepNotes() {
        AllTasksUiState state = AllTasksUiState.empty().withCatalog(catalog())
                .withQuery("Training");
        assertTrue(state.tasks.isEmpty());

        state = AllTasksUiState.empty().withCatalog(catalog()).withQuery("tief");
        assertTrue(state.tasks.isEmpty());
    }

    @Test public void filterAxesUseOrInternallyAndAndAcrossStatusSearchAndAxes() {
        AllTasksUiState state = AllTasksUiState.empty().withCatalog(catalog())
                .withStatus(AllTasksUiState.Status.ALL)
                .withSlots(EnumSet.of(TaskSlot.MORNING, TaskSlot.MIDDAY))
                .withRecurrences(EnumSet.of(Recurrence.DAILY, Recurrence.WEEKDAYS));

        assertEquals(2, state.tasks.size());
        assertTrue(state.tasks.stream().anyMatch(value -> value.task.id.value.equals("gym")
                && value.slot == TaskSlot.MORNING));
        assertTrue(state.tasks.stream().anyMatch(value -> value.task.id.value.equals("weekday")
                && value.slot == TaskSlot.MIDDAY));

        state = state.withQuery("kniebeugen");
        assertEquals(1, state.tasks.size());
        assertEquals("gym", state.tasks.get(0).task.id.value);

        state = AllTasksUiState.empty().withCatalog(catalog())
                .withStatus(AllTasksUiState.Status.ARCHIVED)
                .withSlots(EnumSet.of(TaskSlot.LATER))
                .withRecurrences(EnumSet.of(Recurrence.ONCE))
                .withQuery("alt");
        assertEquals(1, state.tasks.size());
        assertEquals("archive", state.tasks.get(0).task.id.value);
    }

    private static TaskCatalog catalog() {
        Task gym = Task.restore(TaskId.of("gym"), "Gym", Recurrence.DAILY, 1, 0,
                false, "", false, false, MONDAY, null, null, MONDAY, 1_024, false, 45,
                de.thonktank.autosecretary.domain.model.TaskBoundKind.FOREVER,
                null, null, null, null, "Training");
        Task weekday = Task.restore(TaskId.of("weekday"), "Büro", Recurrence.WEEKDAYS,
                1, 1, false, "", false, false, MONDAY, null, null, MONDAY, 2_048,
                false, null,
                de.thonktank.autosecretary.domain.model.TaskBoundKind.FOREVER,
                null, null, null, null, "");
        Task archived = Task.restore(TaskId.of("archive"), "Alt", Recurrence.ONCE,
                1, 0, false, "", false, true, null, MONDAY, MONDAY, null, 3_072,
                true, null,
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

    private enum FilterField { QUERY, STATUS, SLOTS, RECURRENCES, WEEKDAY }
}
