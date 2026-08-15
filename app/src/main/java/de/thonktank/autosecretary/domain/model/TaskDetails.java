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
    public final boolean ongoing;
    public final String condition;

    public TaskDetails(Task task, List<TaskStepTemplate> templates) {
        id = task.id;
        title = task.title;
        slot = task.slot;
        recurrence = task.recurrence;
        intervalDays = task.intervalDays;
        weekdayMask = task.weekdayMask;
        ongoing = task.ongoing;
        condition = task.conditionText;
        List<String> values = new ArrayList<>();
        for (TaskStepTemplate template : templates) values.add(template.text);
        steps = Collections.unmodifiableList(values);
    }
}
