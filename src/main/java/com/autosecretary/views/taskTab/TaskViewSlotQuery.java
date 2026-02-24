package com.autosecretary.views.taskTab;

import com.autosecretary.views.models.ViewSlotList.ViewSlot;

import java.util.Comparator;
import java.util.function.Predicate;

public final class TaskViewSlotQuery {

    private TaskViewSlotQuery() {}

    public static Predicate<ViewSlot> buildPredicate(TaskViewModel.Filters filters) {
        Predicate<ViewSlot> predicate = vs -> true;

        if (filters.day != null) {
            predicate = predicate.and(vs -> vs.item.day.equals(filters.day));
        }
        if (!filters.displayUnscheduled) {
            predicate = predicate.and(vs -> vs.item.start != null);
        }
        return predicate;
    }

    public static Comparator<ViewSlot> buildComparator(TaskViewModel.Sorters sorters) {
        Comparator<ViewSlot> comparator = (a, b) -> 0;

        if (sorters.byScore) {
            comparator = comparator.thenComparing((a, b) -> Integer.compare(b.item.score, a.item.score));
        }
        if (sorters.byTime) {
            comparator = comparator.thenComparing((a, b) -> {
                if (a.item.start == null && b.item.start == null) return 0;
                if (a.item.start == null) return 1;
                if (b.item.start == null) return -1;
                return a.item.start.compareTo(b.item.start);
            });
        }
        if (sorters.byTitle) {
            comparator = comparator.thenComparing((a, b) -> a.item.title.compareTo(b.item.title));
        }
        return comparator;
    }
}
