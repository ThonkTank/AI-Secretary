package de.thonktank.autosecretary.domain.usecase;

public final class DeferFlowRun {
    private final FlowRuntimeCoordinator coordinator;
    public DeferFlowRun(FlowRuntimeCoordinator coordinator) { this.coordinator = coordinator; }
    public boolean execute(String runId) { return coordinator.defer(runId); }
}
