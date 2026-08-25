package de.thonktank.autosecretary.domain.usecase;

public final class ActivateReadyFlows {
    private final FlowRuntimeCoordinator coordinator;

    public ActivateReadyFlows(FlowRuntimeCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    public boolean execute() { return coordinator.activateReady(); }
    public Long nextReadyAtEpochMillis() { return coordinator.nextReadyAtEpochMillis(); }
}
