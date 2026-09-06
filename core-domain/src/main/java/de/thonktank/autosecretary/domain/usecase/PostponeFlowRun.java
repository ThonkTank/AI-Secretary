package de.thonktank.autosecretary.domain.usecase;

/** Returns an offered follow-up to a timed wait without releasing held resources. */
public final class PostponeFlowRun {
    private final FlowRuntimeCoordinator coordinator;

    public PostponeFlowRun(FlowRuntimeCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    public boolean execute(String runId, long delayMillis) {
        return coordinator.postponeOffered(runId, delayMillis);
    }
}
