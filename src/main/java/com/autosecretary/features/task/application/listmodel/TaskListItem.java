package com.autosecretary.features.task.application.listmodel;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class TaskListItem {
    public enum ItemType {
        TASK,
        CALENDAR_EVENT
    }

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
    public final String description;
    public final LocalDate day;
    public final LocalTime start;
    public final LocalTime end;
    public final LocalDate deadline;
    public final int streak;
    public final int score;
    public final boolean completed;
    public final boolean inProgress;
    public final int progressCurrent;
    public final int progressTarget;
    public final String progressUnit;
    public final int progressStepDelta;
    public final String goalIcon;
    public final String goalColorHex;

    TaskListItem(ItemType itemType,
                 String taskId,
                 String slotId,
                 String slotParentId,
                 List<String> parentTaskIds,
                 String title,
                 String description,
                 LocalDate day,
                 LocalTime start,
                 LocalTime end,
                 LocalDate deadline,
                 int streak,
                 int score,
                 boolean completed,
                 boolean inProgress,
                 int progressCurrent,
                 int progressTarget,
                 String progressUnit,
                 int progressStepDelta,
                 String goalIcon,
                 String goalColorHex) {
        this.itemType = itemType;
        this.taskId = taskId;
        this.slotId = slotId;
        this.slotParentId = slotParentId;
        this.parentTaskIds = parentTaskIds;
        this.title = title;
        this.description = description;
        this.day = day;
        this.start = start;
        this.end = end;
        this.deadline = deadline;
        this.streak = streak;
        this.score = score;
        this.completed = completed;
        this.inProgress = inProgress;
        this.progressCurrent = progressCurrent;
        this.progressTarget = progressTarget;
        this.progressUnit = progressUnit;
        this.progressStepDelta = progressStepDelta;
        this.goalIcon = goalIcon;
        this.goalColorHex = goalColorHex;
    }

    public boolean hasProgressTarget() {
        return progressTarget > 0;
    }

    public static TaskListItem calendarEvent(String eventId, String title, LocalDate day, LocalTime start, LocalTime end) {
        return new TaskListItem(eventId, title, day, start, end);
    }

    private TaskListItem(String eventId, String title, LocalDate day, LocalTime start, LocalTime end) {
        this.itemType = ItemType.CALENDAR_EVENT;
        this.taskId = eventId;
        this.slotId = eventId;
        this.slotParentId = null;
        this.parentTaskIds = List.of();
        this.title = title;
        this.description = null;
        this.day = day;
        this.start = start;
        this.end = end;
        this.deadline = null;
        this.streak = 0;
        this.score = 0;
        this.completed = false;
        this.inProgress = false;
        this.progressCurrent = 0;
        this.progressTarget = 0;
        this.progressUnit = null;
        this.progressStepDelta = 0;
        this.goalIcon = null;
        this.goalColorHex = null;
    }

    public boolean isCalendarEvent() {
        return itemType == ItemType.CALENDAR_EVENT;
    }

    /** Returns true if this is a scheduled (non-calendar) task item on the given day. */
    public boolean isScheduledOn(LocalDate day) {
        return this.start != null && (day == null || day.equals(this.day));
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
        if (daysUntil <= 3) return DeadlineUrgency.SOON;
        return DeadlineUrgency.FUTURE;
    }
}
