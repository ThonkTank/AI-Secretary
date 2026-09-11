package de.thonktank.autosecretary.domain.model;

import java.time.LocalDate;
import java.util.Objects;

/** Durable scheduling identity surrounding a frozen execution; never a current-step cursor. */
public final class FlowGraphRunRecord {
    public final FlowGraphRun run;
    public final String sourceKey;
    public final LocalDate scheduledOn;
    public final TaskSlot slot;
    public final long queueOrder;
    public final int nextExecutionSequence;
    public final long createdAtEpochMillis;
    public final long updatedAtEpochMillis;

    public FlowGraphRunRecord(FlowGraphRun run, String sourceKey, LocalDate scheduledOn,
                              TaskSlot slot, long queueOrder, int nextExecutionSequence,
                              long createdAtEpochMillis, long updatedAtEpochMillis) {
        this.run = Objects.requireNonNull(run);
        if (sourceKey == null || sourceKey.trim().isEmpty() || queueOrder < 0L
                || nextExecutionSequence < 0 || createdAtEpochMillis < 0L || updatedAtEpochMillis < 0L)
            throw new IllegalArgumentException("Flow execution metadata is invalid");
        this.sourceKey = sourceKey;
        this.scheduledOn = Objects.requireNonNull(scheduledOn);
        this.slot = Objects.requireNonNull(slot);
        this.queueOrder = queueOrder;
        this.nextExecutionSequence = nextExecutionSequence;
        this.createdAtEpochMillis = createdAtEpochMillis;
        this.updatedAtEpochMillis = updatedAtEpochMillis;
    }
}
