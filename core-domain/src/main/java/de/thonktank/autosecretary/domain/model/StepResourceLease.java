package de.thonktank.autosecretary.domain.model;

import java.util.Objects;

/** Capacity acquired before one step and released after a reachable later step. */
public final class StepResourceLease {
    public final String id;
    public final TaskId taskId;
    public final String acquireStepId;
    public final String releaseStepId;
    public final String resourceId;
    public final int units;

    public StepResourceLease(String id, TaskId taskId, String acquireStepId,
                             String releaseStepId, String resourceId, int units) {
        if (blank(id) || taskId == null || blank(acquireStepId) || blank(releaseStepId)
                || blank(resourceId))
            throw new IllegalArgumentException("Resource lease is incomplete");
        if (units < 1)
            throw new IllegalArgumentException("Resource lease units must be positive");
        this.id = id;
        this.taskId = taskId;
        this.acquireStepId = acquireStepId;
        this.releaseStepId = releaseStepId;
        this.resourceId = resourceId;
        this.units = units;
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof StepResourceLease)) return false;
        StepResourceLease value = (StepResourceLease) other;
        return id.equals(value.id) && taskId.equals(value.taskId)
                && acquireStepId.equals(value.acquireStepId)
                && releaseStepId.equals(value.releaseStepId)
                && resourceId.equals(value.resourceId) && units == value.units;
    }

    @Override public int hashCode() {
        return Objects.hash(id, taskId, acquireStepId, releaseStepId, resourceId, units);
    }
}
