package de.thonktank.autosecretary.domain.usecase;

/** Typed outcome of the atomic candidate-to-run transition. */
public final class StartFlowCandidateResult {
    public enum Status { STARTED, NOT_FOUND, STALE_CANDIDATE, CAPACITY_CHANGED, DURATION_REQUIRED }

    public final Status status;
    public final String runId;
    public final de.thonktank.autosecretary.domain.model.RewardReceipt reward;

    private StartFlowCandidateResult(Status status, String runId) {
        this(status, runId, de.thonktank.autosecretary.domain.model.RewardReceipt.none());
    }

    private StartFlowCandidateResult(Status status, String runId, de.thonktank.autosecretary.domain.model.RewardReceipt reward) {
        this.status = status;
        this.runId = runId;
        this.reward = reward;
    }

    public static StartFlowCandidateResult of(Status status) {
        return new StartFlowCandidateResult(status, null);
    }

    public static StartFlowCandidateResult started(String runId) {
        return new StartFlowCandidateResult(Status.STARTED, runId);
    }

    public static StartFlowCandidateResult started(String runId, de.thonktank.autosecretary.domain.model.RewardReceipt reward) {
        return new StartFlowCandidateResult(Status.STARTED, runId, reward);
    }
}
