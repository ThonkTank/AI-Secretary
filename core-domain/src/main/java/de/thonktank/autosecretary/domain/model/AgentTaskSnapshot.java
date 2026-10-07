package de.thonktank.autosecretary.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Canonical, read-only normal-task projection exposed to the chat agent. */
public final class AgentTaskSnapshot {
    public final TaskId id;
    public final boolean archived;
    public final TaskDefinition definition;
    public final String fingerprint;

    private AgentTaskSnapshot(TaskId id, boolean archived, TaskDefinition definition) {
        this.id = id;
        this.archived = archived;
        this.definition = definition;
        this.fingerprint = fingerprint(id, archived, definition);
    }

    public static AgentTaskSnapshot from(TaskCatalog.Item item) {
        if (item == null || item.task.kind != TaskKind.TASK)
            throw new IllegalArgumentException("Only normal tasks can be exposed to the agent");
        if (item.schedule.isEmpty())
            throw new IllegalArgumentException("Agent task needs a schedule placement");
        TaskSlot fallback = item.schedule.get(0).slot;
        int timeOfDayMask = 0;
        for (TaskScheduleEntry entry : item.schedule)
            timeOfDayMask |= TimeOfDay.fromSlot(entry.slot).bit;
        List<TaskStepDefinition> stepDefinitions = new ArrayList<>();
        for (TaskStepTemplate step : item.steps) stepDefinitions.add(step.definition());
        Task task = item.task;
        TaskDefinition definition = new TaskDefinition(task.title, task.estimatedMinutes,
                fallback, task.recurrence, task.intervalDays, task.weekdayMask,
                task.recurrence == Recurrence.ONCE ? 0 : timeOfDayMask,
                task.boundKind, task.boundUntilOn, task.boundWeeks, task.remainingCount,
                task.deadlineOn, task.note, task.missedOccurrenceMode, stepDefinitions);
        return new AgentTaskSnapshot(task.id, task.archived || task.conditionDone, definition);
    }

    public static List<AgentTaskSnapshot> from(TaskCatalog catalog) {
        if (catalog == null) return Collections.emptyList();
        List<AgentTaskSnapshot> result = new ArrayList<>();
        for (TaskCatalog.Item item : catalog.items)
            if (item.task.kind == TaskKind.TASK) result.add(from(item));
        return Collections.unmodifiableList(result);
    }

    private static String fingerprint(TaskId id, boolean archived, TaskDefinition definition) {
        StringBuilder value = new StringBuilder();
        append(value, id.value);
        append(value, archived);
        append(value, definition.title);
        append(value, definition.estimatedMinutes);
        append(value, definition.fallbackSlot);
        append(value, definition.recurrence);
        append(value, definition.intervalDays);
        append(value, definition.weekdayMask);
        append(value, definition.timeOfDayMask);
        append(value, definition.boundKind);
        append(value, definition.boundUntilOn);
        append(value, definition.boundWeeks);
        append(value, definition.remainingCount);
        append(value, definition.deadlineOn);
        append(value, definition.note);
        append(value, definition.missedOccurrenceMode);
        append(value, definition.steps.size());
        for (TaskStepDefinition step : definition.steps) {
            append(value, step.id);
            append(value, step.position);
            append(value, step.text);
            append(value, step.weekdayMask);
            append(value, step.intervalDays);
            append(value, step.activationKind);
            append(value, step.note);
            StepAmount amount = step.prescription.amount;
            append(value, amount.kind());
            if (amount instanceof StepAmount.SetsReps) {
                append(value, ((StepAmount.SetsReps) amount).sets);
                append(value, ((StepAmount.SetsReps) amount).repetitions);
            } else if (amount instanceof StepAmount.Repetitions) {
                append(value, ((StepAmount.Repetitions) amount).repetitions);
            } else if (amount instanceof StepAmount.Duration) {
                append(value, ((StepAmount.Duration) amount).seconds);
            }
            append(value, step.prescription.rest.mode);
            append(value, step.prescription.rest.customSeconds);
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte part : digest) hex.append(String.format("%02x", part & 0xff));
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void append(StringBuilder target, Object value) {
        String text = value == null ? "<null>" : value.toString();
        target.append(text.length()).append(':').append(text).append('|');
    }
}
