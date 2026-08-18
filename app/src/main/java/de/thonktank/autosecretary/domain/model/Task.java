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
    public final Integer estimatedMinutes;
    public final int timeOfDayMask;
    public final TaskBoundKind boundKind;
    public final LocalDate boundUntilOn;
    public final Integer boundWeeks;
    public final Integer remainingCount;
    public final LocalDate deadlineOn;
    public final String note;

    private Task(TaskId id, String title, TaskSlot slot, Recurrence recurrence, int intervalDays,
                 int weekdayMask, boolean ongoing, String conditionText, boolean conditionDone,
                 boolean archived, LocalDate nextDueOn, LocalDate lastScheduledOn,
                 LocalDate lastCompletedOn, RoutineProgress routineProgress, long displayOrder,
                 boolean hasCompletedOccurrence, Integer estimatedMinutes, int timeOfDayMask,
                 TaskBoundKind boundKind, LocalDate boundUntilOn, Integer boundWeeks,
                 Integer remainingCount, LocalDate deadlineOn, String note) {
        if (title == null || title.trim().isEmpty() || title.trim().length() > 120)
            throw new IllegalArgumentException("Task title must contain 1 to 120 characters");
        if (recurrence == Recurrence.WEEKDAYS && weekdayMask == 0)
            throw new IllegalArgumentException("Weekday recurrence needs at least one weekday");
        if (estimatedMinutes != null && estimatedMinutes < 1)
            throw new IllegalArgumentException("Estimated duration must be positive");
        if (recurrence != Recurrence.ONCE && (timeOfDayMask & TimeOfDay.ALL_MASK) == 0)
            throw new IllegalArgumentException("Recurring task needs a time of day");
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
        this.estimatedMinutes = estimatedMinutes;
        this.timeOfDayMask = recurrence == Recurrence.ONCE ? 0 : timeOfDayMask & TimeOfDay.ALL_MASK;
        this.boundKind = recurrence == Recurrence.ONCE ? TaskBoundKind.FOREVER : boundKind;
        this.boundUntilOn = recurrence == Recurrence.ONCE ? null : boundUntilOn;
        this.boundWeeks = this.boundKind == TaskBoundKind.FOR_WEEKS ? boundWeeks : null;
        this.remainingCount = this.boundKind == TaskBoundKind.N_TIMES ? remainingCount : null;
        this.deadlineOn = recurrence == Recurrence.ONCE ? deadlineOn : null;
        this.note = note == null ? "" : note;
    }

    public static Task create(TaskId id, String title, TaskSlot slot, Recurrence recurrence,
                              int intervalDays, int weekdayMask, boolean ongoing,
                              String conditionText, LocalDate firstDueOn, long displayOrder) {
        if (ongoing && (conditionText == null || conditionText.trim().isEmpty()))
            throw new IllegalArgumentException("Ongoing task needs a completion condition");
        int times = recurrence == Recurrence.ONCE ? 0 : TimeOfDay.fromSlot(slot).bit;
        return restore(id, title, slot, recurrence, intervalDays, weekdayMask, ongoing,
                conditionText, false, false, firstDueOn, null, null,
                new RoutineProgress(1, 0, 0, null), displayOrder, false,
                null, times, TaskBoundKind.FOREVER, null, null, null, null, "");
    }

    public static Task create(TaskId id, TaskDefinition definition, LocalDate firstDueOn,
                              long displayOrder) {
        return restore(id, definition.title, definition.primarySlot(), definition.recurrence,
                definition.intervalDays, definition.weekdayMask, false, "", false, false,
                firstDueOn, null, null, new RoutineProgress(1, 0, 0, null), displayOrder,
                false, definition.estimatedMinutes, definition.timeOfDayMask,
                definition.boundKind, definition.boundUntilOn, definition.boundWeeks,
                definition.remainingCount, definition.deadlineOn, definition.note);
    }

    public static Task restore(TaskId id, String title, TaskSlot slot, Recurrence recurrence,
                               int intervalDays, int weekdayMask, boolean ongoing,
                               String conditionText, boolean conditionDone, boolean archived,
                               LocalDate nextDueOn, LocalDate lastScheduledOn,
                               LocalDate lastCompletedOn, RoutineProgress routineProgress,
                               long displayOrder, boolean hasCompletedOccurrence) {
        int times = recurrence == Recurrence.ONCE ? 0 : TimeOfDay.fromSlot(slot).bit;
        return restore(id, title, slot, recurrence, intervalDays, weekdayMask, ongoing,
                conditionText, conditionDone, archived, nextDueOn, lastScheduledOn,
                lastCompletedOn, routineProgress, displayOrder, hasCompletedOccurrence,
                null, times, TaskBoundKind.FOREVER, null, null, null, null, "");
    }

    public static Task restore(TaskId id, String title, TaskSlot slot, Recurrence recurrence,
                               int intervalDays, int weekdayMask, boolean ongoing,
                               String conditionText, boolean conditionDone, boolean archived,
                               LocalDate nextDueOn, LocalDate lastScheduledOn,
                               LocalDate lastCompletedOn, RoutineProgress routineProgress,
                               long displayOrder, boolean hasCompletedOccurrence,
                               Integer estimatedMinutes, int timeOfDayMask,
                               TaskBoundKind boundKind, LocalDate boundUntilOn,
                               Integer boundWeeks, Integer remainingCount,
                               LocalDate deadlineOn, String note) {
        if (id == null || slot == null || recurrence == null || routineProgress == null
                || boundKind == null)
            throw new IllegalArgumentException("Task identity, slot, recurrence and progress are required");
        return new Task(id, title, slot, recurrence, intervalDays, weekdayMask, ongoing,
                conditionText, conditionDone, archived, nextDueOn, lastScheduledOn,
                lastCompletedOn, routineProgress, displayOrder, hasCompletedOccurrence,
                estimatedMinutes, timeOfDayMask, boundKind, boundUntilOn, boundWeeks,
                remainingCount, deadlineOn, note);
    }

    public Task edit(String newTitle, TaskSlot newSlot, long newDisplayOrder) {
        return copy(newTitle, newSlot, recurrence, intervalDays, weekdayMask, ongoing,
                conditionText, conditionDone, archived, nextDueOn, lastScheduledOn,
                lastCompletedOn, routineProgress, newDisplayOrder, hasCompletedOccurrence,
                estimatedMinutes, timeOfDayMask, boundKind, boundUntilOn, boundWeeks,
                remainingCount, deadlineOn, note);
    }

    public Task editDefinition(String newTitle, TaskSlot newSlot, Recurrence newRecurrence,
                               int newIntervalDays, int newWeekdayMask, boolean newOngoing,
                               String newConditionText, long newDisplayOrder) {
        if (newOngoing && (newConditionText == null || newConditionText.trim().isEmpty()))
            throw new IllegalArgumentException("Ongoing task needs a completion condition");
        int times = newRecurrence == Recurrence.ONCE ? 0 : TimeOfDay.fromSlot(newSlot).bit;
        return copy(newTitle, newSlot, newRecurrence, newIntervalDays, newWeekdayMask,
                newOngoing, newConditionText, conditionDone, archived, nextDueOn,
                lastScheduledOn, lastCompletedOn, routineProgress, newDisplayOrder,
                hasCompletedOccurrence, estimatedMinutes, times, TaskBoundKind.FOREVER,
                null, null, null, null, note);
    }

    public Task editDefinition(TaskDefinition definition, long newDisplayOrder) {
        return editDefinition(definition, newDisplayOrder, nextDueOn);
    }

    public Task editDefinition(TaskDefinition definition, long newDisplayOrder,
                               LocalDate newNextDueOn) {
        return copy(definition.title, definition.primarySlot(), definition.recurrence,
                definition.intervalDays, definition.weekdayMask, false, "", conditionDone,
                archived, newNextDueOn, lastScheduledOn, lastCompletedOn, routineProgress,
                newDisplayOrder, hasCompletedOccurrence, definition.estimatedMinutes,
                definition.timeOfDayMask, definition.boundKind, definition.boundUntilOn,
                definition.boundWeeks, definition.remainingCount, definition.deadlineOn,
                definition.note);
    }

    public Task move(TaskSlot newSlot, long newDisplayOrder) {
        int times = recurrence == Recurrence.ONCE ? 0 : TimeOfDay.fromSlot(newSlot).bit;
        return copy(title, newSlot, recurrence, intervalDays, weekdayMask, ongoing,
                conditionText, conditionDone, archived, nextDueOn, lastScheduledOn,
                lastCompletedOn, routineProgress, newDisplayOrder, hasCompletedOccurrence,
                estimatedMinutes, times, boundKind, boundUntilOn, boundWeeks, remainingCount,
                deadlineOn, note);
    }

    public Task withDisplayOrder(long newDisplayOrder) {
        return copy(title, slot, recurrence, intervalDays, weekdayMask, ongoing, conditionText,
                conditionDone, archived, nextDueOn, lastScheduledOn, lastCompletedOn,
                routineProgress, newDisplayOrder, hasCompletedOccurrence, estimatedMinutes,
                timeOfDayMask, boundKind, boundUntilOn, boundWeeks, remainingCount,
                deadlineOn, note);
    }

    public Task afterOccurrence(LocalDate scheduledOn, LocalDate completedOn, LocalDate nextDue,
                                RoutineProgress progress, boolean archive) {
        return copy(title, slot, recurrence, intervalDays, weekdayMask, ongoing, conditionText,
                conditionDone, archive, nextDue, scheduledOn, completedOn, progress,
                displayOrder, true, estimatedMinutes, timeOfDayMask, boundKind, boundUntilOn,
                boundWeeks, remainingCount, deadlineOn, note);
    }

    public Task afterMaterializing(int count) {
        Integer remaining = remainingCount;
        if (boundKind == TaskBoundKind.N_TIMES)
            remaining = Math.max(0, (remainingCount == null ? 0 : remainingCount) - count);
        return copy(title, slot, recurrence, intervalDays, weekdayMask, ongoing, conditionText,
                conditionDone, archived, nextDueOn, lastScheduledOn, lastCompletedOn,
                routineProgress, displayOrder, hasCompletedOccurrence, estimatedMinutes,
                timeOfDayMask, boundKind, boundUntilOn, boundWeeks, remaining,
                deadlineOn, note);
    }

    public Task withOccurrenceState(boolean newArchived, LocalDate newNextDueOn,
                                    LocalDate newLastScheduledOn,
                                    LocalDate newLastCompletedOn,
                                    boolean newHasCompletedOccurrence) {
        return copy(title, slot, recurrence, intervalDays, weekdayMask, ongoing,
                conditionText, conditionDone, newArchived, newNextDueOn,
                newLastScheduledOn, newLastCompletedOn, routineProgress, displayOrder,
                newHasCompletedOccurrence, estimatedMinutes, timeOfDayMask, boundKind,
                boundUntilOn, boundWeeks, remainingCount, deadlineOn, note);
    }

    public Task closeCondition(LocalDate completedOn) {
        return copy(title, slot, recurrence, intervalDays, weekdayMask, ongoing, conditionText,
                true, true, nextDueOn, lastScheduledOn, completedOn, routineProgress,
                displayOrder, hasCompletedOccurrence, estimatedMinutes, timeOfDayMask,
                boundKind, boundUntilOn, boundWeeks, remainingCount, deadlineOn, note);
    }

    public Task reopenCondition() {
        return copy(title, slot, recurrence, intervalDays, weekdayMask, ongoing, conditionText,
                false, false, nextDueOn, lastScheduledOn, lastCompletedOn, routineProgress,
                displayOrder, hasCompletedOccurrence, estimatedMinutes, timeOfDayMask,
                boundKind, boundUntilOn, boundWeeks, remainingCount, deadlineOn, note);
    }

    private Task copy(String newTitle, TaskSlot newSlot, Recurrence newRecurrence,
                      int newIntervalDays, int newWeekdayMask, boolean newOngoing,
                      String newConditionText, boolean newConditionDone, boolean newArchived,
                      LocalDate newNextDueOn, LocalDate newLastScheduledOn,
                      LocalDate newLastCompletedOn, RoutineProgress newProgress,
                      long newDisplayOrder, boolean newHasCompletedOccurrence,
                      Integer newEstimatedMinutes, int newTimeOfDayMask,
                      TaskBoundKind newBoundKind, LocalDate newBoundUntilOn,
                      Integer newBoundWeeks, Integer newRemainingCount,
                      LocalDate newDeadlineOn, String newNote) {
        return restore(id, newTitle, newSlot, newRecurrence, newIntervalDays, newWeekdayMask,
                newOngoing, newConditionText, newConditionDone, newArchived, newNextDueOn,
                newLastScheduledOn, newLastCompletedOn, newProgress, newDisplayOrder,
                newHasCompletedOccurrence, newEstimatedMinutes, newTimeOfDayMask,
                newBoundKind, newBoundUntilOn, newBoundWeeks, newRemainingCount,
                newDeadlineOn, newNote);
    }
}
