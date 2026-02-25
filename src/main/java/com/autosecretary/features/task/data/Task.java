package com.autosecretary.features.task.data;


import androidx.room.Embedded;
import androidx.room.Relation;
import androidx.room.Ignore;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import com.autosecretary.shared.Period;

/**
 * Room POJO assembled via {@code @Embedded} + {@code @Relation} from five tables
 * (TaskCore, TaskSlot, TaskPrefSlot, TaskRelation, TaskPrerequisite).
 * Not a {@code @Entity} itself — {@link TaskCore} is the persisted entity.
 * The {@link #children} list is {@code @Ignore}, used only for in-memory tree building.
 */
public class Task {

    @Embedded public TaskCore core;

    @Relation(parentColumn = "id", entityColumn = "taskId")
    public List<TaskSlot> slots;

    @Relation(parentColumn = "id", entityColumn = "taskId")
    public List<TaskPrefSlot> prefSlots;

    @Relation(parentColumn = "id", entityColumn = "child")
    public List<TaskRelation> parents;

    @Relation(parentColumn = "id", entityColumn = "taskId")
    public List<TaskPrerequisite> prerequisites;

    @Ignore
    public List<Task> children = new ArrayList<>();

    /**
     * Returns the number of days remaining until the task's deadline or period end.
     * Falls back to 1 if neither a deadline nor a repeating period is configured.
     */
    public double remainingDays() {
        if (core.deadline != null) {
            return (double) ChronoUnit.DAYS.between(LocalDate.now(), core.deadline);
        } else if (core.repetition != null && core.repetition.reps > 0
                && core.repetition.periodUnit != null) {
            return core.repetition.remainingDays();
        }
        return 1;
    }

    /**
     * Estimates how many days are needed to complete this task, factoring in
     * progress-based rep requirements and cooldown between repetitions.
     * Falls back to 1 if no progress or repetition is configured.
     */
    public double requiredDays() {
        if (core.progress.target > 0 && core.progress.resetPerRep) {
            return core.progress.remaining() / (core.progress.repsRequired(core.minDuration) * core.cooldown);
        }
        if (core.repetition != null && core.repetition.reps > 0) {
            return Math.max(1, core.repetition.remainingReps()) * core.cooldown;
        }
        return 1;
    }

    /**
     * Sets the task ID and cascades it to all related entities
     * (prefSlots, slots, and prerequisites) so their foreign keys stay consistent.
     */
    @Ignore
    public void setId(String id) {
        core.id = id;
        for (TaskPrefSlot prefSlot : prefSlots) {
            prefSlot.taskId = id;
        }
        for (TaskSlot slot : slots) {
            slot.taskId = id;
        }
        for (TaskPrerequisite prereq : prerequisites) {
            prereq.taskId = id;
        }
    }


    /**
     * Increments the completion counter. If {@code trackDuration} is true,
     * also increments tracked completions and accumulates the duration.
     */
    public void recordCompletion(long durationMinutes, boolean trackDuration) {
        core.history.completions++;
        if (trackDuration) {
            core.history.trackedCompletions++;
            core.history.totalDuration += (int) durationMinutes;
        }
    }

    public void setParentId(String id) {
        for (TaskRelation parent : parents) {
            parent.parent = id;
        }
    }


    /** Empty constructor required by Room. */
    public Task() {}
    /** Convenience constructor with default prefSlots. */
    public Task(String title, int reps, int perPeriod, Period periodUnit, LocalDate deadline, int cooldown, LocalTime start, int maxDuration) {
        this.core = new TaskCore();
        this.core.title = title;
        this.core.cooldown = cooldown;
        this.core.deadline = deadline;
        this.core.maxDuration = maxDuration;

        this.core.repetition.reps = reps;
        this.core.repetition.perPeriod = perPeriod;
        this.core.repetition.periodUnit = periodUnit;
        this.core.repetition.periodStart = LocalDate.now();

        this.slots = new ArrayList<>();
        this.prefSlots = new ArrayList<>();
        this.parents = new ArrayList<>();
        this.prerequisites = new ArrayList<>();

        int repsPerDay = core.repsPerDay();
        for (int i = 0; i < repsPerDay; i++) {
            TaskPrefSlot prefSlot = TaskPrefSlotFactory.createDefault(this.core.id);
            prefSlot.start = start;
            this.prefSlots.add(prefSlot);
        }
    }
}
