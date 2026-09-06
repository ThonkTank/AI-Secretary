package de.thonktank.autosecretary;

import androidx.annotation.Nullable;

/** Closed interaction boundary for the operational flow-run screen. */
public final class FlowRunsAction {
    public enum Kind { REFRESH, DEFER, READY_AT, POSTPONE, MOVE_BEFORE, CANCEL, ACKNOWLEDGE_ERROR }

    public final Kind kind;
    public final String runId;
    @Nullable public final String beforeRunId;
    public final long epochMillis;
    public final long errorId;

    private FlowRunsAction(Kind kind, String runId, @Nullable String beforeRunId,
                           long epochMillis, long errorId) {
        this.kind = kind;
        this.runId = runId == null ? "" : runId;
        this.beforeRunId = beforeRunId;
        this.epochMillis = epochMillis;
        this.errorId = errorId;
    }

    public static FlowRunsAction refresh() {
        return new FlowRunsAction(Kind.REFRESH, "", null, 0L, 0L);
    }

    public static FlowRunsAction readyAt(String runId, long epochMillis) {
        if (epochMillis < 0L) throw new IllegalArgumentException("Ready time is required");
        return new FlowRunsAction(Kind.READY_AT, required(runId), null, epochMillis, 0L);
    }

    public static FlowRunsAction defer(String runId) {
        return new FlowRunsAction(Kind.DEFER, required(runId), null, 0L, 0L);
    }

    public static FlowRunsAction postpone(String runId, long delayMillis) {
        if (delayMillis < 0L) throw new IllegalArgumentException("Delay is required");
        return new FlowRunsAction(Kind.POSTPONE, required(runId), null, delayMillis, 0L);
    }

    public static FlowRunsAction moveBefore(String runId, @Nullable String beforeRunId) {
        return new FlowRunsAction(Kind.MOVE_BEFORE, required(runId), beforeRunId, 0L, 0L);
    }

    public static FlowRunsAction cancel(String runId) {
        return new FlowRunsAction(Kind.CANCEL, required(runId), null, 0L, 0L);
    }

    public static FlowRunsAction acknowledgeError(long errorId) {
        return new FlowRunsAction(Kind.ACKNOWLEDGE_ERROR, "", null, 0L, errorId);
    }

    private static String required(String value) {
        if (value == null || value.trim().isEmpty())
            throw new IllegalArgumentException("Flow run identity is required");
        return value;
    }
}
