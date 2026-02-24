package com.autosecretary.views.taskTab;

import com.autosecretary.application.task.model.TaskListItem;
import com.autosecretary.views.models.ViewSlotList;
import com.autosecretary.views.models.ViewSlotList.ViewSlot;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class TaskViewSlotQueryTest {

    @Test
    public void buildComparator_preservesExpectedSortOrderWithViewSlotListSort() {
        TaskListSort sorters = new TaskListSort();
        sorters.byTaskParent = false;
        sorters.byScore = true;
        sorters.byTime = true;
        sorters.byTitle = true;

        ViewSlotList list = new ViewSlotList();
        list.fromList(Arrays.asList(
                item("task-a", "slot-a", "Bravo", 10, LocalTime.of(9, 0)),
                item("task-b", "slot-b", "Alpha", 10, LocalTime.of(9, 0)),
                item("task-c", "slot-c", "Charlie", 5, LocalTime.of(8, 0)),
                item("task-d", "slot-d", "Delta", 10, null)
        ));
        list.filter(vs -> true);

        Comparator<ViewSlot> comparator = TaskViewSlotQuery.buildComparator(sorters);
        list.sort(sorters.byTaskParent, comparator);

        List<String> titles = list.displaySlots.stream().map(vs -> vs.item.title).collect(Collectors.toList());
        assertEquals(Arrays.asList("Alpha", "Bravo", "Delta", "Charlie"), titles);
    }

    private static TaskListItem item(String taskId, String slotId, String title, int score, LocalTime start) {
        return new TaskListItem(
                taskId,
                slotId,
                null,
                Collections.emptyList(),
                title,
                LocalDate.of(2025, 1, 1),
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
