package com.autosecretary.features.task.application;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.autosecretary.features.task.data.TaskItemType;

/**
 * Flat, immutable display model extracted from {@link com.autosecretary.features.task.data.Task}
 * and {@link com.autosecretary.features.task.data.TaskSlot} by
 * {@link TaskListItemMapper}.
 * Used by {@link com.autosecretary.features.task.ui.state.ViewSlotList} for filtering, sorting, and display.
 */
public class TaskListItem {
    /** Categorizes deadline proximity for color-coded display in the list. */
    public enum DeadlineUrgency {
        NONE,
        OVERDUE,
        TODAY,
        SOON,
        FUTURE
    }

    public final String taskId;
    public final String slotId;
    public final String slotParentId;
    public final List<String> parentTaskIds;
    public final String title;
    public final TaskItemType type;
    public final LocalDate day;
    public final LocalTime start;
    public final LocalTime end;
    public final LocalDate deadline;
    public final int streak;
    public final int score;
    public final boolean completed;
    public final boolean inProgress;

    public TaskListItem(String taskId, String slotId, String slotParentId, List<String> parentTaskIds,
                        String title, TaskItemType type, LocalDate day, LocalTime start, LocalTime end, LocalDate deadline,
                        int streak, int score, boolean completed, boolean inProgress) {
        this.taskId = taskId;
        this.slotId = slotId;
        this.slotParentId = slotParentId;
        this.parentTaskIds = parentTaskIds;
        this.title = title;
        this.type = type;
        this.day = day;
        this.start = start;
        this.end = end;
        this.deadline = deadline;
        this.streak = streak;
        this.score = score;
        this.completed = completed;
        this.inProgress = inProgress;
    }

    public long daysUntilDeadline() {
        if (deadline == null) {
            return Long.MAX_VALUE;
        }
        return ChronoUnit.DAYS.between(LocalDate.now(), deadline);
    }

    public DeadlineUrgency deadlineUrgency() {
        if (deadline == null) {
            return DeadlineUrgency.NONE;
        }

        long daysUntil = daysUntilDeadline();
        if (daysUntil < 0) return DeadlineUrgency.OVERDUE;
        if (daysUntil == 0) return DeadlineUrgency.TODAY;
        if (daysUntil <= 3) return DeadlineUrgency.SOON; // 3 days = "soon" threshold for deadline proximity warning
        return DeadlineUrgency.FUTURE;
    }
}
