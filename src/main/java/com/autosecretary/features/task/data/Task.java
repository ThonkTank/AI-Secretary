package com.autosecretary.features.task.data;


import androidx.room.Embedded;
import androidx.room.Relation;
import androidx.room.Ignore;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import com.autosecretary.constants.Period;

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

    public double remainingDays() {
        if (core.deadline != null) {
            return (double) ChronoUnit.DAYS.between(LocalDate.now(), core.deadline);
        } else if (core.repetition != null && core.repetition.reps > 0
                && core.repetition.periodUnit != null) {
            return core.repetition.remainingDays();
        }
        return 1;
    }

    public double requiredDays() {
        if (core.progress.target > 0) {
            return core.progress.resetPerRep ? core.repetition.requiredDays() : core.progress.remaining() / (core.progress.repsRequired(core.minDuration)*(core.cooldown));
        } else if (core.repetition != null) {
            return core.repetition.requiredDays();
        }
        return 1;
    }

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


    //Leerer Construktor für Room
    public Task() {}
    //convenience Constructor für mich
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
