package de.thonktank.autosecretary.domain.model;

import java.time.LocalDate;

/** A task definition. Time-of-day placement lives exclusively in {@link TaskSchedule}. */
public final class Task {
    public final TaskId id;
    public final String title;
    public final Recurrence recurrence;
    public final int intervalDays;
    public final int weekdayMask;
    public final boolean ongoing;
    public final String conditionText;
    public final boolean conditionDone;
    public final boolean archived;
    /** Planning cursor; materialization advances it independently from completion. */
    public final LocalDate nextDueOn;
    /** Immutable first due date used as the calendar-day anchor for step cadences. */
    public final LocalDate cadenceAnchorOn;
    public final LocalDate lastScheduledOn;
    public final LocalDate lastCompletedOn;
    public final long catalogOrder;
    public final boolean hasCompletedOccurrence;
    public final Integer estimatedMinutes;
    public final TaskBoundKind boundKind;
    public final LocalDate boundUntilOn;
    public final Integer boundWeeks;
    public final Integer remainingCount;
    public final LocalDate deadlineOn;
    public final String note;

    private Task(TaskId id, String title, Recurrence recurrence, int intervalDays,
                 int weekdayMask, boolean ongoing, String conditionText, boolean conditionDone,
                 boolean archived, LocalDate nextDueOn, LocalDate cadenceAnchorOn,
                 LocalDate lastScheduledOn,
                 LocalDate lastCompletedOn, long catalogOrder, boolean hasCompletedOccurrence,
                 Integer estimatedMinutes, TaskBoundKind boundKind, LocalDate boundUntilOn,
                 Integer boundWeeks, Integer remainingCount, LocalDate deadlineOn, String note) {
        if (id == null || recurrence == null || boundKind == null)
            throw new IllegalArgumentException("Task identity, recurrence and bound are required");
        if (title == null || title.trim().isEmpty() || title.trim().length() > 120)
            throw new IllegalArgumentException("Task title must contain 1 to 120 characters");
        if (ongoing && (conditionText == null || conditionText.trim().isEmpty()))
            throw new IllegalArgumentException("Ongoing task needs a completion condition");
        if (recurrence == Recurrence.WEEKDAYS && weekdayMask == 0)
            throw new IllegalArgumentException("Weekday recurrence needs at least one weekday");
        if (estimatedMinutes != null && estimatedMinutes < 1)
            throw new IllegalArgumentException("Estimated duration must be positive");
        this.id = id;
        this.title = title.trim();
        this.recurrence = recurrence;
        this.intervalDays = Math.max(1, intervalDays);
        this.weekdayMask = weekdayMask;
        this.ongoing = ongoing;
        this.conditionText = conditionText == null ? "" : conditionText.trim();
        this.conditionDone = conditionDone;
        this.archived = archived;
        this.nextDueOn = nextDueOn;
        this.cadenceAnchorOn = cadenceAnchorOn;
        this.lastScheduledOn = lastScheduledOn;
        this.lastCompletedOn = lastCompletedOn;
        this.catalogOrder = catalogOrder;
        this.hasCompletedOccurrence = hasCompletedOccurrence;
        this.estimatedMinutes = estimatedMinutes;
        this.boundKind = recurrence == Recurrence.ONCE ? TaskBoundKind.FOREVER : boundKind;
        this.boundUntilOn = recurrence == Recurrence.ONCE ? null : boundUntilOn;
        this.boundWeeks = this.boundKind == TaskBoundKind.FOR_WEEKS ? boundWeeks : null;
        this.remainingCount = this.boundKind == TaskBoundKind.N_TIMES ? remainingCount : null;
        this.deadlineOn = recurrence == Recurrence.ONCE ? deadlineOn : null;
        this.note = note == null ? "" : note;
    }

    public static Task create(TaskId id, TaskDefinition definition, LocalDate firstDueOn,
                              long catalogOrder) {
        return restore(id, definition.title, definition.recurrence, definition.intervalDays,
                definition.weekdayMask, false, "", false, false, firstDueOn, null, null,
                firstDueOn, catalogOrder, false, definition.estimatedMinutes, definition.boundKind,
                definition.boundUntilOn, definition.boundWeeks, definition.remainingCount,
                definition.deadlineOn, definition.note);
    }

    public static Task restore(TaskId id, String title, Recurrence recurrence, int intervalDays,
                               int weekdayMask, boolean ongoing, String conditionText,
                               boolean conditionDone, boolean archived, LocalDate nextDueOn,
                               LocalDate lastScheduledOn, LocalDate lastCompletedOn,
                               LocalDate cadenceAnchorOn, long catalogOrder,
                               boolean hasCompletedOccurrence, Integer estimatedMinutes,
                               TaskBoundKind boundKind, LocalDate boundUntilOn, Integer boundWeeks,
                               Integer remainingCount, LocalDate deadlineOn, String note) {
        return new Task(id, title, recurrence, intervalDays, weekdayMask, ongoing, conditionText,
                conditionDone, archived, nextDueOn, cadenceAnchorOn,
                lastScheduledOn, lastCompletedOn,
                catalogOrder, hasCompletedOccurrence, estimatedMinutes, boundKind, boundUntilOn,
                boundWeeks, remainingCount, deadlineOn, note);
    }

    public Task edit(String newTitle, long newCatalogOrder) {
        return copy(newTitle, recurrence, intervalDays, weekdayMask, ongoing, conditionText,
                conditionDone, archived, nextDueOn, lastScheduledOn, lastCompletedOn,
                newCatalogOrder, hasCompletedOccurrence, estimatedMinutes, boundKind,
                boundUntilOn, boundWeeks, remainingCount, deadlineOn, note);
    }

    public Task editDefinition(TaskDefinition definition, long newCatalogOrder) {
        return editDefinition(definition, newCatalogOrder, nextDueOn);
    }

    public Task editDefinition(TaskDefinition definition, long newCatalogOrder,
                               LocalDate newNextDueOn) {
        return copy(definition.title, definition.recurrence, definition.intervalDays,
                definition.weekdayMask, false, "", conditionDone, archived, newNextDueOn,
                lastScheduledOn, lastCompletedOn, newCatalogOrder, hasCompletedOccurrence,
                definition.estimatedMinutes, definition.boundKind, definition.boundUntilOn,
                definition.boundWeeks, definition.remainingCount, definition.deadlineOn,
                definition.note);
    }

    public Task withCatalogOrder(long newCatalogOrder) { return edit(title, newCatalogOrder); }

    public Task afterPlanning(LocalDate newNextDueOn, int count) {
        Integer remaining = remainingCount;
        if (boundKind == TaskBoundKind.N_TIMES)
            remaining = Math.max(0, (remainingCount == null ? 0 : remainingCount) - count);
        return copy(title, recurrence, intervalDays, weekdayMask, ongoing, conditionText,
                conditionDone, archived, newNextDueOn, lastScheduledOn, lastCompletedOn,
                catalogOrder, hasCompletedOccurrence, estimatedMinutes, boundKind,
                boundUntilOn, boundWeeks, remaining, deadlineOn, note);
    }

    public LocalDate planningCursor() { return nextDueOn; }

    public Task withOccurrenceState(boolean newArchived, LocalDate newNextDueOn,
                                    LocalDate newLastScheduledOn,
                                    LocalDate newLastCompletedOn,
                                    boolean newHasCompletedOccurrence) {
        return copy(title, recurrence, intervalDays, weekdayMask, ongoing, conditionText,
                conditionDone, newArchived, newNextDueOn, newLastScheduledOn,
                newLastCompletedOn, catalogOrder, newHasCompletedOccurrence, estimatedMinutes,
                boundKind, boundUntilOn, boundWeeks, remainingCount, deadlineOn, note);
    }

    public Task closeCondition(LocalDate completedOn) {
        return copy(title, recurrence, intervalDays, weekdayMask, ongoing, conditionText,
                true, true, nextDueOn, lastScheduledOn, completedOn, catalogOrder,
                hasCompletedOccurrence, estimatedMinutes, boundKind, boundUntilOn, boundWeeks,
                remainingCount, deadlineOn, note);
    }

    public Task reopenCondition() {
        return copy(title, recurrence, intervalDays, weekdayMask, ongoing, conditionText,
                false, false, nextDueOn, lastScheduledOn, lastCompletedOn, catalogOrder,
                hasCompletedOccurrence, estimatedMinutes, boundKind, boundUntilOn, boundWeeks,
                remainingCount, deadlineOn, note);
    }

    private Task copy(String newTitle, Recurrence newRecurrence, int newIntervalDays,
                      int newWeekdayMask, boolean newOngoing, String newConditionText,
                      boolean newConditionDone, boolean newArchived, LocalDate newNextDueOn,
                      LocalDate newLastScheduledOn, LocalDate newLastCompletedOn,
                      long newCatalogOrder, boolean newHasCompletedOccurrence,
                      Integer newEstimatedMinutes, TaskBoundKind newBoundKind,
                      LocalDate newBoundUntilOn, Integer newBoundWeeks,
                      Integer newRemainingCount, LocalDate newDeadlineOn, String newNote) {
        return restore(id, newTitle, newRecurrence, newIntervalDays, newWeekdayMask, newOngoing,
                newConditionText, newConditionDone, newArchived, newNextDueOn,
                newLastScheduledOn, newLastCompletedOn, cadenceAnchorOn, newCatalogOrder,
                newHasCompletedOccurrence, newEstimatedMinutes, newBoundKind,
                newBoundUntilOn, newBoundWeeks, newRemainingCount, newDeadlineOn, newNote);
    }
}
