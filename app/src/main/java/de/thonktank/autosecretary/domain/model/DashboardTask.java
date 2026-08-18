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
    public final int awardedXp;

    public DashboardTask(Task task, Occurrence occurrence, List<OccurrenceStep> steps, boolean done) {
        this(task, occurrence, steps, done, Collections.emptyMap(), 0);
    }

    public DashboardTask(Task task, Occurrence occurrence, List<OccurrenceStep> steps, boolean done,
                         Map<String, Integer> stepEarnedXp, int awardedXp) {
        if (task == null) throw new IllegalArgumentException("Dashboard task needs a task");
        this.task = task;
        this.occurrence = occurrence;
        this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
        this.done = done;
        this.stepEarnedXp = Collections.unmodifiableMap(new LinkedHashMap<>(stepEarnedXp));
        this.awardedXp = Math.max(0, awardedXp);
    }

    public int earnedXp(String stepId) { return Math.max(0, stepEarnedXp.getOrDefault(stepId, 0)); }
}
