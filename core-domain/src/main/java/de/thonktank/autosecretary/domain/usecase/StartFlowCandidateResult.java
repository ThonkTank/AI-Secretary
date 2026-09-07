package de.thonktank.autosecretary.domain.usecase;

/** Typed outcome of the atomic candidate-to-run transition. */
public final class StartFlowCandidateResult {
    public enum Status { STARTED, NOT_FOUND, STALE_CANDIDATE, CAPACITY_CHANGED }

    public final Status status;
    public final String runId;

    private StartFlowCandidateResult(Status status, String runId) {
        this.status = status;
        this.runId = runId;
    }

    public static StartFlowCandidateResult of(Status status) {
        return new StartFlowCandidateResult(status, null);
    }

    public static StartFlowCandidateResult started(String runId) {
        return new StartFlowCandidateResult(Status.STARTED, runId);
    }
}
