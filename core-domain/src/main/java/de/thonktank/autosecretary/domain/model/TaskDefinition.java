package de.thonktank.autosecretary.domain.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class TaskDefinition {
    public final String title;
    public final Integer estimatedMinutes;
    public final TaskSlot fallbackSlot;
    public final Recurrence recurrence;
    public final int intervalDays;
    public final int weekdayMask;
    public final int timeOfDayMask;
    public final TaskBoundKind boundKind;
    public final LocalDate boundUntilOn;
    public final Integer boundWeeks;
    public final Integer remainingCount;
    public final LocalDate deadlineOn;
    public final String note;
    public final List<TaskStepDefinition> steps;

    public TaskDefinition(String title, Integer estimatedMinutes, TaskSlot fallbackSlot,
                          Recurrence recurrence, int intervalDays, int weekdayMask,
                          int timeOfDayMask, TaskBoundKind boundKind, LocalDate boundUntilOn,
                          Integer boundWeeks, Integer remainingCount, LocalDate deadlineOn,
                          String note, List<TaskStepDefinition> steps) {
        if (title == null || title.trim().isEmpty() || title.trim().length() > 120)
            throw new IllegalArgumentException("Task title must contain 1 to 120 characters");
        if (estimatedMinutes != null && estimatedMinutes < 1)
            throw new IllegalArgumentException("Estimated duration must be positive");
        if (fallbackSlot == null || recurrence == null || boundKind == null || steps == null)
            throw new IllegalArgumentException("Task definition is incomplete");
        if (recurrence == Recurrence.WEEKDAYS && (weekdayMask & 0x7f) == 0)
            throw new IllegalArgumentException("Weekday recurrence needs a weekday");
        if (recurrence == Recurrence.INTERVAL && intervalDays < 1)
            throw new IllegalArgumentException("Interval must be positive");
        if (recurrence != Recurrence.ONCE && (timeOfDayMask & TimeOfDay.ALL_MASK) == 0)
            throw new IllegalArgumentException("Recurring task needs a time of day");
        LocalDate until = null;
        Integer weeks = null;
        Integer count = null;
        LocalDate deadline = null;
        if (recurrence == Recurrence.ONCE) {
            deadline = deadlineOn;
        } else if (boundKind == TaskBoundKind.UNTIL_DATE) {
            if (boundUntilOn == null) throw new IllegalArgumentException("End date is required");
            until = boundUntilOn;
        } else if (boundKind == TaskBoundKind.FOR_WEEKS) {
            if (boundUntilOn == null || boundWeeks == null || boundWeeks < 1)
                throw new IllegalArgumentException("Positive week bound and end date are required");
            until = boundUntilOn;
            weeks = boundWeeks;
        } else if (boundKind == TaskBoundKind.N_TIMES) {
            if (remainingCount == null || remainingCount < 1)
                throw new IllegalArgumentException("Occurrence count must be positive");
            count = remainingCount;
        }
        List<TaskStepDefinition> copied = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            TaskStepDefinition step = Objects.requireNonNull(steps.get(i), "step");
            if (step.position != i) throw new IllegalArgumentException("Step positions must be contiguous");
            copied.add(recurrence == Recurrence.ONCE && step.weekdayMask != 0
                    ? new TaskStepDefinition(step.id, i, step.text, 0, step.amount, step.note)
                    : step);
        }
        this.title = title.trim();
        this.estimatedMinutes = estimatedMinutes;
        this.fallbackSlot = fallbackSlot;
        this.recurrence = recurrence;
        this.intervalDays = recurrence == Recurrence.INTERVAL ? intervalDays : 1;
        this.weekdayMask = recurrence == Recurrence.WEEKDAYS ? weekdayMask & 0x7f : 0;
        this.timeOfDayMask = recurrence == Recurrence.ONCE ? 0 : timeOfDayMask & TimeOfDay.ALL_MASK;
        this.boundKind = recurrence == Recurrence.ONCE ? TaskBoundKind.FOREVER : boundKind;
        this.boundUntilOn = until;
        this.boundWeeks = weeks;
        this.remainingCount = count;
        this.deadlineOn = deadline;
        this.note = note == null ? "" : note;
        this.steps = Collections.unmodifiableList(copied);
    }

    public TaskSlot primarySlot() {
        return recurrence == Recurrence.ONCE ? fallbackSlot
                : TimeOfDay.earliestSlot(timeOfDayMask, fallbackSlot);
    }

    /** Concise canonical definition for callers that do not need advanced editor fields. */
    public static TaskDefinition basic(String title, TaskSlot slot, Recurrence recurrence,
                                       int intervalDays, int weekdayMask,
                                       List<String> stepTitles) {
        List<TaskStepDefinition> steps = new ArrayList<>();
        for (String value : stepTitles)
            if (value != null && !value.trim().isEmpty())
                steps.add(new TaskStepDefinition(null, steps.size(), value, 0,
                        StepAmount.none(), ""));
        return new TaskDefinition(title, null, slot, recurrence, intervalDays, weekdayMask,
                recurrence == Recurrence.ONCE ? 0 : TimeOfDay.fromSlot(slot).bit,
                TaskBoundKind.FOREVER, null, null, null, null, "", steps);
    }
}
