package de.thonktank.autosecretary.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DashboardTask {
    public final Task task;
    public final Occurrence occurrence;
    public final List<OccurrenceStep> steps;
    public final boolean done;

    public DashboardTask(Task task, Occurrence occurrence, List<OccurrenceStep> steps, boolean done) {
        if (task == null) throw new IllegalArgumentException("Dashboard task needs a task");
        this.task = task;
        this.occurrence = occurrence;
        this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
        this.done = done;
    }
}
