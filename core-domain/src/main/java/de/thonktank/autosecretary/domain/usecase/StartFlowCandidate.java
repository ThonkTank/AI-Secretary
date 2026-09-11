package de.thonktank.autosecretary.domain.usecase;

/** Atomic graph admission; no execution exists until the start action commits. */
public final class StartFlowCandidate {
    private final GraphFlowRuntime graph;
    public StartFlowCandidate(GraphFlowRuntime graph) { this.graph = java.util.Objects.requireNonNull(graph); }

    public StartFlowCandidateResult execute(String candidateId, Long chosenDelayMillis) {
        GraphFlowRuntime.Result result = graph.start(candidateId, chosenDelayMillis);
        switch (result.status) {
            case CHANGED: return StartFlowCandidateResult.started(result.runId, result.reward);
            case DURATION_REQUIRED: return StartFlowCandidateResult.of(StartFlowCandidateResult.Status.DURATION_REQUIRED);
            case CAPACITY_UNAVAILABLE: return StartFlowCandidateResult.of(StartFlowCandidateResult.Status.CAPACITY_CHANGED);
            case NOT_FOUND: return StartFlowCandidateResult.of(StartFlowCandidateResult.Status.NOT_FOUND);
            default: return StartFlowCandidateResult.of(StartFlowCandidateResult.Status.STALE_CANDIDATE);
        }
    }
}
