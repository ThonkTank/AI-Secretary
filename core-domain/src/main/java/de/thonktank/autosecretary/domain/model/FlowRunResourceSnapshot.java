package de.thonktank.autosecretary.domain.model;

import java.util.Objects;

/** Immutable lease shape plus its mutable reservation lifecycle for one run. */
public final class FlowRunResourceSnapshot {
    public final String id;
    public final String runId;
    public final String sourceLeaseId;
    public final String resourceId;
    public final String resourceName;
    public final int capacityAtCreation;
    public final int units;
    public final int acquirePosition;
    public final int releasePosition;
    public final FlowResourceState state;
    public final Long reservedAtEpochMillis;
    public final Long activatedAtEpochMillis;
    public final Long releasedAtEpochMillis;

    public FlowRunResourceSnapshot(String id, String runId, String sourceLeaseId,
                                   String resourceId, String resourceName,
                                   int capacityAtCreation, int units, int acquirePosition,
                                   int releasePosition, FlowResourceState state,
                                   Long reservedAtEpochMillis, Long activatedAtEpochMillis,
                                   Long releasedAtEpochMillis) {
        if (blank(id) || blank(runId) || blank(sourceLeaseId) || blank(resourceId)
                || blank(resourceName) || capacityAtCreation < 1 || units < 1
                || units > capacityAtCreation || acquirePosition < 0
                || releasePosition <= acquirePosition || state == null)
            throw new IllegalArgumentException("Flow resource snapshot is invalid");
        this.id = id;
        this.runId = runId;
        this.sourceLeaseId = sourceLeaseId;
        this.resourceId = resourceId;
        this.resourceName = resourceName.trim();
        this.capacityAtCreation = capacityAtCreation;
        this.units = units;
        this.acquirePosition = acquirePosition;
        this.releasePosition = releasePosition;
        this.state = state;
        this.reservedAtEpochMillis = reservedAtEpochMillis;
        this.activatedAtEpochMillis = activatedAtEpochMillis;
        this.releasedAtEpochMillis = releasedAtEpochMillis;
    }

    public FlowRunResourceSnapshot withState(FlowResourceState next, long now) {
        Long reserved = reservedAtEpochMillis;
        Long activated = activatedAtEpochMillis;
        Long released = releasedAtEpochMillis;
        if (next == FlowResourceState.RESERVED && reserved == null) reserved = now;
        if (next == FlowResourceState.ACTIVE && activated == null) activated = now;
        if (next == FlowResourceState.RELEASED && released == null) released = now;
        return new FlowRunResourceSnapshot(id, runId, sourceLeaseId, resourceId, resourceName,
                capacityAtCreation, units, acquirePosition, releasePosition, next,
                reserved, activated, released);
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof FlowRunResourceSnapshot)) return false;
        FlowRunResourceSnapshot value = (FlowRunResourceSnapshot) other;
        return id.equals(value.id) && runId.equals(value.runId)
                && sourceLeaseId.equals(value.sourceLeaseId)
                && resourceId.equals(value.resourceId) && resourceName.equals(value.resourceName)
                && capacityAtCreation == value.capacityAtCreation && units == value.units
                && acquirePosition == value.acquirePosition && releasePosition == value.releasePosition
                && state == value.state
                && Objects.equals(reservedAtEpochMillis, value.reservedAtEpochMillis)
                && Objects.equals(activatedAtEpochMillis, value.activatedAtEpochMillis)
                && Objects.equals(releasedAtEpochMillis, value.releasedAtEpochMillis);
    }

    @Override public int hashCode() {
        return Objects.hash(id, runId, sourceLeaseId, resourceId, resourceName,
                capacityAtCreation, units, acquirePosition, releasePosition, state,
                reservedAtEpochMillis, activatedAtEpochMillis, releasedAtEpochMillis);
    }
}
