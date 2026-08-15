package de.thonktank.autosecretary.domain.model;

import java.time.LocalDate;

public final class Task {
    public final TaskId id;
    public final String title;
    public final TaskSlot slot;
    public final Recurrence recurrence;
    public final int intervalDays;
    public final int weekdayMask;
    public final boolean ongoing;
    public final String conditionText;
    public final boolean conditionDone;
    public final boolean archived;
    public final LocalDate nextDueOn;
    public final LocalDate lastScheduledOn;
    public final LocalDate lastCompletedOn;
    public final RoutineProgress routineProgress;
    public final long displayOrder;
    public final boolean hasCompletedOccurrence;

    private Task(TaskId id, String title, TaskSlot slot, Recurrence recurrence, int intervalDays,
                 int weekdayMask, boolean ongoing, String conditionText, boolean conditionDone,
                 boolean archived, LocalDate nextDueOn, LocalDate lastScheduledOn,
                 LocalDate lastCompletedOn, RoutineProgress routineProgress, long displayOrder,
                 boolean hasCompletedOccurrence) {
        if (title == null || title.trim().isEmpty()) throw new IllegalArgumentException("Task title must not be blank");
        if (recurrence == Recurrence.WEEKDAYS && weekdayMask == 0)
            throw new IllegalArgumentException("Weekday recurrence needs at least one weekday");
        this.id = id;
        this.title = title.trim();
        this.slot = slot;
        this.recurrence = recurrence;
        this.intervalDays = Math.max(1, intervalDays);
        this.weekdayMask = weekdayMask;
        this.ongoing = ongoing;
        this.conditionText = conditionText == null ? "" : conditionText.trim();
        this.conditionDone = conditionDone;
        this.archived = archived;
        this.nextDueOn = nextDueOn;
        this.lastScheduledOn = lastScheduledOn;
        this.lastCompletedOn = lastCompletedOn;
        this.routineProgress = routineProgress;
        this.displayOrder = displayOrder;
        this.hasCompletedOccurrence = hasCompletedOccurrence;
    }

    public static Task create(TaskId id, String title, TaskSlot slot, Recurrence recurrence,
                              int intervalDays, int weekdayMask, boolean ongoing,
                              String conditionText, LocalDate firstDueOn, long displayOrder) {
        if (ongoing && (conditionText == null || conditionText.trim().isEmpty()))
            throw new IllegalArgumentException("Ongoing task needs a completion condition");
        return restore(id, title, slot, recurrence, intervalDays, weekdayMask, ongoing,
                conditionText, false, false, firstDueOn, null, null,
                new RoutineProgress(1, 0, 0, null), displayOrder, false);
    }

    public static Task restore(TaskId id, String title, TaskSlot slot, Recurrence recurrence,
                               int intervalDays, int weekdayMask, boolean ongoing,
                               String conditionText, boolean conditionDone, boolean archived,
                               LocalDate nextDueOn, LocalDate lastScheduledOn,
                               LocalDate lastCompletedOn, RoutineProgress routineProgress,
                               long displayOrder, boolean hasCompletedOccurrence) {
        if (id == null || slot == null || recurrence == null || routineProgress == null)
            throw new IllegalArgumentException("Task identity, slot, recurrence and progress are required");
        return new Task(id, title, slot, recurrence, intervalDays, weekdayMask, ongoing,
                conditionText, conditionDone, archived, nextDueOn, lastScheduledOn,
                lastCompletedOn, routineProgress, displayOrder, hasCompletedOccurrence);
    }

    public Task edit(String newTitle, TaskSlot newSlot, long newDisplayOrder) {
        return copy(newTitle, newSlot, conditionDone, archived, nextDueOn, lastScheduledOn,
                lastCompletedOn, routineProgress, newDisplayOrder, hasCompletedOccurrence);
    }

    public Task move(TaskSlot newSlot, long newDisplayOrder) {
        return copy(title, newSlot, conditionDone, archived, nextDueOn, lastScheduledOn,
                lastCompletedOn, routineProgress, newDisplayOrder, hasCompletedOccurrence);
    }

    public Task withDisplayOrder(long newDisplayOrder) {
        return copy(title, slot, conditionDone, archived, nextDueOn, lastScheduledOn,
                lastCompletedOn, routineProgress, newDisplayOrder, hasCompletedOccurrence);
    }

    public Task afterOccurrence(LocalDate scheduledOn, LocalDate completedOn, LocalDate nextDue,
                                RoutineProgress progress, boolean archive) {
        return copy(title, slot, conditionDone, archive, nextDue, scheduledOn, completedOn,
                progress, displayOrder, true);
    }

    public Task closeCondition(LocalDate completedOn) {
        return copy(title, slot, true, true, nextDueOn, lastScheduledOn, completedOn,
                routineProgress, displayOrder, hasCompletedOccurrence);
    }

    private Task copy(String newTitle, TaskSlot newSlot, boolean newConditionDone,
                      boolean newArchived, LocalDate newNextDueOn, LocalDate newLastScheduledOn,
                      LocalDate newLastCompletedOn, RoutineProgress newProgress,
                      long newDisplayOrder, boolean newHasCompletedOccurrence) {
        return restore(id, newTitle, newSlot, recurrence, intervalDays, weekdayMask, ongoing,
                conditionText, newConditionDone, newArchived, newNextDueOn,
                newLastScheduledOn, newLastCompletedOn, newProgress, newDisplayOrder,
                newHasCompletedOccurrence);
    }
}
