package com.autosecretary.features.task.ui.list;

import static org.junit.Assert.assertEquals;

import com.autosecretary.features.task.application.listmodel.TaskListItem;
import com.autosecretary.features.task.application.listmodel.TaskListItemMapper;
import com.autosecretary.features.task.domain.model.Task;
import com.autosecretary.features.task.domain.model.TaskSlot;
import com.autosecretary.features.task.ui.list.state.ViewSlotList;
import com.autosecretary.shared.Priority;
import com.autosecretary.testing.TaskFixtures;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Protects the invariants of the flat list view modes:
 * <ul>
 *   <li>Urgency mode lists every open task exactly once, flat, sorted by priority, then
 *       nearest deadline, then title — completed tasks are excluded, unscheduled included.</li>
 *   <li>Deadline mode lists only open one-off (non-repeating) tasks that have a deadline,
 *       sorted by nearest deadline.</li>
 *   <li>{@link TaskListItem#deadlineElapsedPercent()} clamps to 0–100 and reports overdue
 *       and empty spans as 100 (full bar).</li>
 * </ul>
 */
public final class TaskListViewModesCharacterizationTest {

    private final TaskListItemMapper mapper = new TaskListItemMapper();

    private List<String> displayTitles(List<Task> tasks, ListConfig config) {
        ViewSlotList list = new ViewSlotList();
        list.fromList(mapper.map(tasks));
        list.rebuildDisplay(
                slot -> config.matches(slot, LocalDate.now()),
                Collections.emptyList(),
                config.comparator(),
                slot -> true,
                config.grouping()
        );
        return list.getDisplaySlots().stream()
                .map(slot -> slot.getItem().title)
                .collect(Collectors.toList());
    }

    private static Task oneOffTask(String title, LocalDate deadline) {
        Task task = new Task();
        task.core.title = title;
        task.core.deadline = deadline;
        return task;
    }

    @Test
    public void urgencyModeSortsOpenTasksByPriorityThenDeadlineThenTitleInvariant() {
        LocalDate today = LocalDate.now();

        Task critical = TaskFixtures.taskWithSlot("Kritisch", today);
        critical.core.priority = Priority.CRITICAL;

        Task highSoon = TaskFixtures.taskWithSlot("Bald fällig", today);
        highSoon.core.priority = Priority.HIGH;
        highSoon.core.deadline = today.plusDays(1);

        Task highLater = TaskFixtures.taskWithSlot("Später fällig", today);
        highLater.core.priority = Priority.HIGH;
        highLater.core.deadline = today.plusDays(5);

        Task highNoDeadline = TaskFixtures.taskWithSlot("Anna", today);
        highNoDeadline.core.priority = Priority.HIGH;

        Task low = TaskFixtures.taskWithSlot("Niedrig", today);
        low.core.priority = Priority.LOW;

        List<String> titles = displayTitles(
                List.of(low, highNoDeadline, highLater, highSoon, critical), ListConfig.URGENCY);

        assertEquals(List.of("Kritisch", "Bald fällig", "Später fällig", "Anna", "Niedrig"), titles);
    }

    @Test
    public void urgencyModeExcludesCompletedAndShowsUnscheduledTasksOncePerTaskInvariant() {
        LocalDate today = LocalDate.now();

        Task completed = TaskFixtures.taskWithSlot("Erledigt", today);
        completed.slots.get(0).completed = true;

        Task unscheduled = new Task();
        unscheduled.core.title = "Ungeplant";

        // Two slots on the same day — must still render as a single row.
        Task twoSlots = TaskFixtures.taskWithSlot("Doppelt", today);
        TaskSlot second = new TaskSlot();
        second.taskId = twoSlots.core.id;
        second.day = today;
        second.start = LocalTime.of(14, 0);
        second.end = LocalTime.of(14, 30);
        second.scheduled = true;
        twoSlots.slots.add(second);

        List<String> titles = displayTitles(List.of(completed, unscheduled, twoSlots), ListConfig.URGENCY);

        assertEquals(List.of("Doppelt", "Ungeplant"), titles);
    }

    @Test
    public void deadlineModeShowsOnlyNonRepeatingTasksWithDeadlineSortedAscendingInvariant() {
        LocalDate today = LocalDate.now();

        Task nearest = oneOffTask("In einem Tag", today.plusDays(1));
        Task later = oneOffTask("In drei Tagen", today.plusDays(3));
        Task withoutDeadline = oneOffTask("Ohne Frist", null);

        // Repeating task (reps > 0 via fixture) with a deadline — must not appear.
        Task repeating = TaskFixtures.taskWithSlot("Wiederholend", today);
        repeating.core.deadline = today.plusDays(2);

        List<String> titles = displayTitles(
                List.of(later, repeating, withoutDeadline, nearest), ListConfig.DEADLINE);

        assertEquals(List.of("In einem Tag", "In drei Tagen"), titles);
    }

    @Test
    public void dayScopedGatingInvariant() {
        // Day-scoped modes render one selected day (day navigation, today-gating);
        // the flat modes ignore the day cursor and stay interactive.
        assertEquals(true, ListConfig.CHECKLIST.isDayScoped());
        assertEquals(true, ListConfig.MANAGE.isDayScoped());
        assertEquals(false, ListConfig.URGENCY.isDayScoped());
        assertEquals(false, ListConfig.DEADLINE.isDayScoped());
        // The sets coincide today: calendar rows only exist where a day cursor exists.
        for (ListConfig config : ListConfig.values()) {
            assertEquals("day scope and calendar visibility coincide for " + config,
                    config.isDayScoped(), config.showsCalendarEvents());
        }
    }

    @Test
    public void deadlineElapsedPercentClampsAndMarksOverdueAsFullInvariant() {
        LocalDate today = LocalDate.now();

        Task halfway = oneOffTask("Hälfte", today.plusDays(10));
        halfway.core.created = today.minusDays(10);

        Task overdue = oneOffTask("Überfällig", today.minusDays(1));
        overdue.core.created = today.minusDays(10);

        Task emptySpan = oneOffTask("Leerer Zeitraum", today);
        emptySpan.core.created = today;

        Task noDeadline = oneOffTask("Ohne Frist", null);

        List<TaskListItem> items = mapper.map(List.of(halfway, overdue, emptySpan, noDeadline));

        assertEquals(50, itemByTitle(items, "Hälfte").deadlineElapsedPercent());
        assertEquals(100, itemByTitle(items, "Überfällig").deadlineElapsedPercent());
        assertEquals(100, itemByTitle(items, "Leerer Zeitraum").deadlineElapsedPercent());
        assertEquals(0, itemByTitle(items, "Ohne Frist").deadlineElapsedPercent());
    }

    private static TaskListItem itemByTitle(List<TaskListItem> items, String title) {
        return items.stream().filter(item -> title.equals(item.title)).findFirst().orElseThrow();
    }
}
