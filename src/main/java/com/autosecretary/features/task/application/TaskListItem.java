package com.autosecretary.features.task.application;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Flat, immutable display model extracted from {@link com.autosecretary.features.task.data.Task}
 * and {@link com.autosecretary.features.task.data.TaskSlot} by
 * {@link TaskListItemMapper}.
 * Used by {@link com.autosecretary.features.task.ui.state.ViewSlotList} for filtering, sorting, and display.
 */
public class TaskListItem {
    public enum ItemType {
        TASK,
        CALENDAR_EVENT
    }

    /** Categorizes deadline proximity for color-coded display in the list. */
    public enum DeadlineUrgency {
        NONE,
        OVERDUE,
        TODAY,
        SOON,
        FUTURE
    }

    public final ItemType itemType;
    public final String taskId;
    public final String slotId;
    public final String slotParentId;
    public final List<String> parentTaskIds;
    public final String title;
    public final LocalDate day;
    public final LocalTime start;
    public final LocalTime end;
    public final LocalDate deadline;
    public final int streak;
    public final int score;
    public final boolean completed;
    public final boolean inProgress;

    public TaskListItem(ItemType itemType, String taskId, String slotId, String slotParentId, List<String> parentTaskIds,
                        String title, LocalDate day, LocalTime start, LocalTime end, LocalDate deadline,
                        int streak, int score, boolean completed, boolean inProgress) {
        this.itemType = itemType;
        this.taskId = taskId;
        this.slotId = slotId;
        this.slotParentId = slotParentId;
        this.parentTaskIds = parentTaskIds;
        this.title = title;
        this.day = day;
        this.start = start;
        this.end = end;
        this.deadline = deadline;
        this.streak = streak;
        this.score = score;
        this.completed = completed;
        this.inProgress = inProgress;
    }

    public static TaskListItem task(String taskId, String slotId, String slotParentId, List<String> parentTaskIds,
                                    String title, LocalDate day, LocalTime start, LocalTime end, LocalDate deadline,
                                    int streak, int score, boolean completed, boolean inProgress) {
        return new TaskListItem(
                ItemType.TASK,
                taskId,
                slotId,
                slotParentId,
                parentTaskIds,
                title,
                day,
                start,
                end,
                deadline,
                streak,
                score,
                completed,
                inProgress
        );
    }

    public static TaskListItem calendarEvent(String eventId, String title, LocalDate day, LocalTime start, LocalTime end) {
        return new TaskListItem(
                ItemType.CALENDAR_EVENT,
                eventId,
                eventId,
                null,
                List.of(),
                title,
                day,
                start,
                end,
                null,
                0,
                0,
                false,
                false
        );
    }

    public boolean isCalendarEvent() {
        return itemType == ItemType.CALENDAR_EVENT;
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
