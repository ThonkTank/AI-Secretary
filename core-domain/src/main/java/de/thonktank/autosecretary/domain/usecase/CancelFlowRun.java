package de.thonktank.autosecretary.domain.usecase;

public final class CancelFlowRun {
    private final FlowRuntimeCoordinator coordinator;
    public CancelFlowRun(FlowRuntimeCoordinator coordinator) { this.coordinator = coordinator; }
    public boolean execute(String runId) { return coordinator.cancel(runId); }
}
