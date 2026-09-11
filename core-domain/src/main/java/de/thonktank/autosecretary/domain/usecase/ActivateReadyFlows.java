package de.thonktank.autosecretary.domain.usecase;

public final class ActivateReadyFlows {
    private final java.util.function.BooleanSupplier activate;
    private final java.util.function.Supplier<Long> next;

    public ActivateReadyFlows(GraphFlowRuntime runtime) {
        this.activate = runtime::activateReady; this.next = runtime::nextReadyAtEpochMillis;
    }

    public boolean execute() { return activate.getAsBoolean(); }
    public Long nextReadyAtEpochMillis() { return next.get(); }
}
