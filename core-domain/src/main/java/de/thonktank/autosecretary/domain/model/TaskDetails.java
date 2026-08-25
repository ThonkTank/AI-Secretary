package de.thonktank.autosecretary.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TaskDetails {
    public final TaskId id;
    public final String title;
    public final TaskSlot slot;
    public final Recurrence recurrence;
    public final int intervalDays;
    public final int weekdayMask;
    public final List<String> steps;
    public final List<TaskStepTemplate> stepTemplates;
    public final boolean ongoing;
    public final String condition;
    public final Integer estimatedMinutes;
    public final int timeOfDayMask;
    public final TaskBoundKind boundKind;
    public final java.time.LocalDate boundUntilOn;
    public final Integer boundWeeks;
    public final Integer remainingCount;
    public final java.time.LocalDate deadlineOn;
    public final String note;
    public final MissedOccurrenceMode missedOccurrenceMode;

    public TaskDetails(Task task, List<TaskStepTemplate> templates, TaskSchedule schedule) {
        id = task.id;
        title = task.title;
        slot = schedule.primary(task.id).slot;
        recurrence = task.recurrence;
        intervalDays = task.intervalDays;
        weekdayMask = task.weekdayMask;
        ongoing = task.ongoing;
        condition = task.conditionText;
        estimatedMinutes = task.estimatedMinutes;
        int times = 0;
        for (TaskSlot placement : schedule.slots(task.id))
            times |= TimeOfDay.fromSlot(placement).bit;
        timeOfDayMask = task.recurrence == Recurrence.ONCE ? 0 : times;
        boundKind = task.boundKind;
        boundUntilOn = task.boundUntilOn;
        boundWeeks = task.boundWeeks;
        remainingCount = task.remainingCount;
        deadlineOn = task.deadlineOn;
        note = task.note;
        missedOccurrenceMode = task.missedOccurrenceMode;
        stepTemplates = Collections.unmodifiableList(new ArrayList<>(templates));
        List<String> values = new ArrayList<>();
        for (TaskStepTemplate template : templates) values.add(template.text);
        steps = Collections.unmodifiableList(values);
    }
}
