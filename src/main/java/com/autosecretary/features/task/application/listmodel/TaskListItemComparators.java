package com.autosecretary.features.task.application.listmodel;

import java.util.Comparator;

/**
 * Shared sort orders over {@link TaskListItem}, used by the home screen widget and the
 * flat list modes (urgency and deadline view) of the task list screen.
 */
public final class TaskListItemComparators {

    private TaskListItemComparators() {
    }

    /** Highest priority first, then nearest deadline, then title. */
    public static final Comparator<TaskListItem> BY_PRIORITY =
            Comparator.comparingInt((TaskListItem item) -> item.priorityWeight).reversed()
                    .thenComparing(item -> item.deadline, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(item -> item.title, Comparator.nullsLast(Comparator.naturalOrder()));

    /** Nearest deadline first, then title. */
    public static final Comparator<TaskListItem> BY_DEADLINE =
            Comparator.comparing((TaskListItem item) -> item.deadline, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(item -> item.title, Comparator.nullsLast(Comparator.naturalOrder()));
}
