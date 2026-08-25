package de.thonktank.autosecretary.domain.usecase;

public final class ReorderFlowRun {
    private final FlowRuntimeCoordinator coordinator;
    public ReorderFlowRun(FlowRuntimeCoordinator coordinator) { this.coordinator = coordinator; }
    public boolean execute(String runId, long queueOrder) {
        return coordinator.reorder(runId, queueOrder);
    }
    public boolean execute(String runId, String beforeRunId) {
        return coordinator.reorderBefore(runId, beforeRunId);
    }
}
