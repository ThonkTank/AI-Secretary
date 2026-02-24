package com.autosecretary.views.taskTab;

import com.autosecretary.application.task.model.TaskListItem;
import com.autosecretary.views.models.ViewSlotList;
import com.autosecretary.views.models.ViewSlotList.ViewSlot;

import org.junit.Test;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class TaskViewModelQueryTest {

    @SuppressWarnings("unchecked")
    @Test
    public void buildPredicate_filtersByDayAndScheduledFlag() throws Exception {
        Method method = TaskViewModel.class.getDeclaredMethod("buildPredicate", LocalDate.class, boolean.class);
        method.setAccessible(true);

        Predicate<ViewSlot> predicate = (Predicate<ViewSlot>) method.invoke(null, LocalDate.of(2025, 1, 1), false);

        ViewSlotList list = new ViewSlotList();
        list.fromList(Arrays.asList(
                item("task-a", "slot-a", "Today", 10, LocalDate.of(2025, 1, 1), LocalTime.of(9, 0)),
                item("task-b", "slot-b", "Wrong day", 10, LocalDate.of(2025, 1, 2), LocalTime.of(9, 0)),
                item("task-c", "slot-c", "Unscheduled", 10, LocalDate.of(2025, 1, 1), null)
        ));

        list.filter(predicate);
        List<String> titles = list.displaySlots.stream().map(vs -> vs.item.title).collect(Collectors.toList());

        assertEquals(Collections.singletonList("Today"), titles);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void buildComparator_preservesExpectedSortOrder() throws Exception {
        Method method = TaskViewModel.class.getDeclaredMethod("buildComparator", boolean.class, boolean.class, boolean.class);
        method.setAccessible(true);

        Comparator<ViewSlot> comparator = (Comparator<ViewSlot>) method.invoke(null, true, true, true);

        ViewSlotList list = new ViewSlotList();
        list.fromList(Arrays.asList(
                item("task-a", "slot-a", "Bravo", 10, LocalDate.of(2025, 1, 1), LocalTime.of(9, 0)),
                item("task-b", "slot-b", "Alpha", 10, LocalDate.of(2025, 1, 1), LocalTime.of(9, 0)),
                item("task-c", "slot-c", "Charlie", 5, LocalDate.of(2025, 1, 1), LocalTime.of(8, 0)),
                item("task-d", "slot-d", "Delta", 10, LocalDate.of(2025, 1, 1), null)
        ));
        list.filter(vs -> true);

        list.sort(false, comparator);

        List<String> titles = list.displaySlots.stream().map(vs -> vs.item.title).collect(Collectors.toList());
        assertEquals(Arrays.asList("Alpha", "Bravo", "Delta", "Charlie"), titles);
    }

    private static TaskListItem item(String taskId, String slotId, String title, int score, LocalDate day, LocalTime start) {
        return new TaskListItem(
                taskId,
                slotId,
                null,
                Collections.emptyList(),
                title,
                day,
                start,
                start == null ? null : start.plusMinutes(30),
                null,
                0,
                score,
                false,
                false
        );
    }
}
