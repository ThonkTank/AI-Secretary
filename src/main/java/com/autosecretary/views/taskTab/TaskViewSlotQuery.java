package com.autosecretary.views.taskTab;

import com.autosecretary.views.models.ViewSlotList.ViewSlot;

import java.util.Comparator;
import java.util.function.Predicate;

public final class TaskViewSlotQuery {

    private TaskViewSlotQuery() {}

    public static Predicate<ViewSlot> buildPredicate(TaskListFilter filters) {
        Predicate<ViewSlot> predicate = vs -> true;

        if (filters.day != null) {
            predicate = predicate.and(vs -> vs.item.day.equals(filters.day));
        }
        if (!filters.displayUnscheduled) {
            predicate = predicate.and(vs -> vs.item.start != null);
        }
        return predicate;
    }

    public static Comparator<ViewSlot> buildComparator(TaskListSort sorters) {
        Comparator<ViewSlot> comparator = (a, b) -> 0;

        if (sorters.byScore) {
            comparator = comparator.thenComparing(
                    Comparator.comparingInt((ViewSlot vs) -> vs.item.score).reversed()
            );
        }
        if (sorters.byTime) {
            comparator = comparator.thenComparing(
                    vs -> vs.item.start,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
        }
        if (sorters.byTitle) {
            comparator = comparator.thenComparing(vs -> vs.item.title, Comparator.naturalOrder());
        }
        return comparator;
    }
}
