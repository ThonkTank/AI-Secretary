package de.thonktank.autosecretary.domain.model;

import java.time.LocalDate;

/** Persisted due work that has not been started and owns no runtime resources. */
public final class FlowCandidate {
    public final String id;
    public final TaskId taskId;
    public final String seedStepId;
    public final String sourceKey;
    public final LocalDate scheduledOn;
    public final TaskSlot slot;
    public final long queueOrder;
    public final long createdAtEpochMillis;

    public FlowCandidate(String id, TaskId taskId, String seedStepId, String sourceKey,
                         LocalDate scheduledOn, TaskSlot slot, long queueOrder,
                         long createdAtEpochMillis) {
        if (blank(id) || taskId == null || blank(seedStepId) || blank(sourceKey)
                || scheduledOn == null || slot == null || queueOrder < 0L
                || createdAtEpochMillis < 0L)
            throw new IllegalArgumentException("Flow candidate is incomplete");
        this.id = id;
        this.taskId = taskId;
        this.seedStepId = seedStepId;
        this.sourceKey = sourceKey;
        this.scheduledOn = scheduledOn;
        this.slot = slot;
        this.queueOrder = queueOrder;
        this.createdAtEpochMillis = createdAtEpochMillis;
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
