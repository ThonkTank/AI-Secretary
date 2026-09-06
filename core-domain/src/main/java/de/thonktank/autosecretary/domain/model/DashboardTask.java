package de.thonktank.autosecretary.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

public final class DashboardTask {
    public final Task task;
    public final Occurrence occurrence;
    public final List<OccurrenceStep> steps;
    public final boolean done;
    public final Map<String, Integer> stepEarnedXp;
    public final Map<String, Integer> stepPlannedXp;
    public final int awardedXp;
    public final TaskSlot displaySlot;
    public final int backlogCount;
    public final boolean flowAggregate;
    public final Map<String, FlowRunSummary> flowRunByStepId;

    public DashboardTask(Task task, Occurrence occurrence, List<OccurrenceStep> steps, boolean done) {
        this(task, occurrence, steps, done, Collections.emptyMap(), 0,
                occurrence == null ? null : occurrence.slot, 0, Collections.emptyMap(), false,
                Collections.emptyMap());
    }

    public DashboardTask(Task task, Occurrence occurrence, List<OccurrenceStep> steps, boolean done,
                         Map<String, Integer> stepEarnedXp, int awardedXp) {
        this(task, occurrence, steps, done, stepEarnedXp, awardedXp,
                occurrence == null ? null : occurrence.slot, 0, Collections.emptyMap(), false,
                Collections.emptyMap());
    }

    public DashboardTask(Task task, Occurrence occurrence, List<OccurrenceStep> steps, boolean done,
                         Map<String, Integer> stepEarnedXp, int awardedXp,
                         TaskSlot displaySlot) {
        this(task, occurrence, steps, done, stepEarnedXp, awardedXp, displaySlot, 0,
                Collections.emptyMap(), false, Collections.emptyMap());
    }

    public DashboardTask(Task task, Occurrence occurrence, List<OccurrenceStep> steps, boolean done,
                         Map<String, Integer> stepEarnedXp, int awardedXp,
                         TaskSlot displaySlot, int backlogCount) {
        this(task, occurrence, steps, done, stepEarnedXp, awardedXp, displaySlot, backlogCount,
                Collections.emptyMap(), false, Collections.emptyMap());
    }

    public DashboardTask(Task task, Occurrence occurrence, List<OccurrenceStep> steps, boolean done,
                         Map<String, Integer> stepEarnedXp, int awardedXp,
                         TaskSlot displaySlot, int backlogCount,
                         Map<String, Integer> stepPlannedXp) {
        this(task, occurrence, steps, done, stepEarnedXp, awardedXp, displaySlot, backlogCount,
                stepPlannedXp, false, Collections.emptyMap());
    }

    public DashboardTask(Task task, Occurrence occurrence, List<OccurrenceStep> steps, boolean done,
                         Map<String, Integer> stepEarnedXp, int awardedXp,
                         TaskSlot displaySlot, int backlogCount,
                         Map<String, Integer> stepPlannedXp, boolean flowAggregate,
                         Map<String, FlowRunSummary> flowRunByStepId) {
        if (task == null) throw new IllegalArgumentException("Dashboard task needs a task");
        if (displaySlot == null)
            throw new IllegalArgumentException("Dashboard task needs its schedule placement");
        this.task = task;
        this.occurrence = occurrence;
        this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
        this.done = done;
        this.stepEarnedXp = Collections.unmodifiableMap(new LinkedHashMap<>(stepEarnedXp));
        this.stepPlannedXp = Collections.unmodifiableMap(new LinkedHashMap<>(stepPlannedXp));
        this.awardedXp = Math.max(0, awardedXp);
        this.displaySlot = displaySlot;
        this.backlogCount = Math.max(0, backlogCount);
        this.flowAggregate = flowAggregate;
        this.flowRunByStepId = Collections.unmodifiableMap(
                new LinkedHashMap<>(flowRunByStepId));
    }

    public int earnedXp(String stepId) { return Math.max(0, stepEarnedXp.getOrDefault(stepId, 0)); }
    public int plannedXp(String stepId, int fallback) {
        return Math.max(0, stepPlannedXp.getOrDefault(stepId, fallback));
    }
}
