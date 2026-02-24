package com.autosecretary.views.taskTab;

import com.autosecretary.views.models.ViewSlotList.ViewSlot;

import java.util.Comparator;
import java.util.function.Predicate;

public final class TaskViewSlotQuery {

    private TaskViewSlotQuery() {}

    public static Predicate<ViewSlot> buildPredicate(TaskViewModel.Filters filters) {
        Predicate<ViewSlot> predicate = vs -> true;

        if (filters.day != null) {
            predicate = predicate.and(vs -> vs.slot.day.equals(filters.day));
        }
        if (!filters.displayUnscheduled) {
            predicate = predicate.and(vs -> vs.slot.start != null);
        }
        return predicate;
    }

    public static Comparator<ViewSlot> buildComparator(TaskViewModel.Sorters sorters) {
        Comparator<ViewSlot> comparator = (a, b) -> 0;

        if (sorters.byScore) {
            comparator = comparator.thenComparing((a, b) -> Integer.compare(b.slot.score, a.slot.score));
        }
        if (sorters.byTime) {
            comparator = comparator.thenComparing((a, b) -> {
                if (a.slot.start == null && b.slot.start == null) return 0;
                if (a.slot.start == null) return 1;
                if (b.slot.start == null) return -1;
                return a.slot.start.compareTo(b.slot.start);
            });
        }
        if (sorters.byTitle) {
            comparator = comparator.thenComparing((a, b) -> a.task.core.title.compareTo(b.task.core.title));
        }
        return comparator;
    }
}
