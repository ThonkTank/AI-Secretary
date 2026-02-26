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
    public final boolean timerRunning;
    public final int progressCurrent;
    public final int progressTarget;
    public final String progressUnit;
    public final int progressStepDelta;
    public final boolean goalTask;
    public final String goalIcon;
    public final String goalColorHex;

    public TaskListItem(ItemType itemType,
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
                        boolean timerRunning,
                        int progressCurrent,
                        int progressTarget,
                        String progressUnit,
                        int progressStepDelta,
                        boolean goalTask,
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
        this.timerRunning = timerRunning;
        this.progressCurrent = progressCurrent;
        this.progressTarget = progressTarget;
        this.progressUnit = progressUnit;
        this.progressStepDelta = progressStepDelta;
        this.goalTask = goalTask;
        this.goalIcon = goalIcon;
        this.goalColorHex = goalColorHex;
    }

    public boolean hasProgressTarget() {
        return progressTarget > 0;
    }

    public static TaskListItem task(String taskId,
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
                                    boolean timerRunning,
                                    int progressCurrent,
                                    int progressTarget,
                                    String progressUnit,
                                    int progressStepDelta,
                                    boolean goalTask,
                                    String goalIcon,
                                    String goalColorHex) {
        return new TaskListItem(
                ItemType.TASK,
                taskId,
                slotId,
                slotParentId,
                parentTaskIds,
                title,
                description,
                day,
                start,
                end,
                deadline,
                streak,
                score,
                completed,
                inProgress,
                timerRunning,
                progressCurrent,
                progressTarget,
                progressUnit,
                progressStepDelta,
                goalTask,
                goalIcon,
                goalColorHex
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
                null,
                day,
                start,
                end,
                null,
                0,
                0,
                false,
                false,
                false,
                0,
                0,
                "",
                0,
                false,
                null,
                null
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
        if (daysUntil <= 3) return DeadlineUrgency.SOON;
        return DeadlineUrgency.FUTURE;
    }
}
