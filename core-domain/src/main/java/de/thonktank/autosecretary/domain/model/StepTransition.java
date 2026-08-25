package de.thonktank.autosecretary.domain.model;

import java.util.Objects;

/** A single directed edge in a task's flow definition. */
public final class StepTransition {
    public final String sourceStepId;
    public final String targetStepId;
    public final FlowDelayPolicy delay;

    public StepTransition(String sourceStepId, String targetStepId, FlowDelayPolicy delay) {
        if (blank(sourceStepId) || blank(targetStepId))
            throw new IllegalArgumentException("Transition steps are required");
        if (sourceStepId.equals(targetStepId))
            throw new IllegalArgumentException("A step cannot follow itself");
        this.sourceStepId = sourceStepId;
        this.targetStepId = targetStepId;
        this.delay = Objects.requireNonNull(delay, "delay");
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof StepTransition)) return false;
        StepTransition value = (StepTransition) other;
        return sourceStepId.equals(value.sourceStepId)
                && targetStepId.equals(value.targetStepId) && delay.equals(value.delay);
    }

    @Override public int hashCode() { return Objects.hash(sourceStepId, targetStepId, delay); }
}
