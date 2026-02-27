package com.autosecretary.features.task.data;


import androidx.room.Embedded;
import androidx.room.Relation;
import androidx.room.Ignore;

import java.time.LocalDate;
import java.time.LocalTime;
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

    @Relation(parentColumn = "id", entityColumn = "taskId")
    public List<TaskPlannedMeal> plannedMeals;

    @Ignore
    public List<Task> children = new ArrayList<>();

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

    /** Returns the slot with the given id, or {@code null} if not found. */
    public TaskSlot findSlot(String slotId) {
        if (slotId == null || slots == null) return null;
        for (TaskSlot slot : slots) {
            if (slotId.equals(slot.id)) return slot;
        }
        return null;
    }

    public TaskPlannedMeal getPlannedMealForDate(LocalDate date) {
        if (date == null || plannedMeals == null) {
            return null;
        }
        for (TaskPlannedMeal meal : plannedMeals) {
            if (date.equals(meal.day)) {
                return meal;
            }
        }
        return null;
    }

    public boolean completePlannedMeal(LocalDate date, int actualServings) {
        TaskPlannedMeal meal = getPlannedMealForDate(date);
        if (meal == null || meal.completed) {
            return false;
        }
        meal.markCompleted(actualServings);
        return true;
    }

    /**
     * Sets the task ID and cascades it to all related entities
     * (prefSlots, slots, prerequisites, plannedMeals, and parent relations)
     * so their foreign keys stay consistent.
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
        for (TaskPlannedMeal meal : plannedMeals) {
            meal.taskId = id;
        }
        for (TaskRelation rel : parents) {
            rel.child = id;
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
        this.plannedMeals = new ArrayList<>();

        int repsPerDay = core.repsPerDay();
        for (int i = 0; i < repsPerDay; i++) {
            TaskPrefSlot prefSlot = TaskPrefSlotFactory.createDefault(this.core.id);
            prefSlot.start = start;
            this.prefSlots.add(prefSlot);
        }
    }
}
