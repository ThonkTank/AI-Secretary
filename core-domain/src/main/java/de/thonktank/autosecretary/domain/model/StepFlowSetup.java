package de.thonktank.autosecretary.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Complete editable flow setup for one existing task. */
public final class StepFlowSetup {
    public final Task task;
    public final List<TaskStepTemplate> steps;
    public final List<CapacityResource> resources;
    public final List<StepTransition> transitions;
    public final List<StepResourceLease> resourceLeases;

    public StepFlowSetup(Task task, List<TaskStepTemplate> steps,
                         List<CapacityResource> resources,
                         List<StepTransition> transitions,
                         List<StepResourceLease> resourceLeases) {
        if (task == null || steps == null || resources == null || transitions == null
                || resourceLeases == null)
            throw new IllegalArgumentException("Ablaufeinrichtung ist unvollständig");
        this.task = task;
        this.steps = immutable(steps);
        this.resources = immutable(resources);
        this.transitions = immutable(transitions);
        this.resourceLeases = immutable(resourceLeases);
    }

    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
}
