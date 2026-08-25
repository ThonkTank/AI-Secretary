package de.thonktank.autosecretary.domain.usecase;

public final class AdjustFlowRunReadyAt {
    private final FlowRuntimeCoordinator coordinator;
    public AdjustFlowRunReadyAt(FlowRuntimeCoordinator coordinator) {
        this.coordinator = coordinator;
    }
    public boolean execute(String runId, long readyAtEpochMillis) {
        return coordinator.adjustReadyAt(runId, readyAtEpochMillis);
    }
}
