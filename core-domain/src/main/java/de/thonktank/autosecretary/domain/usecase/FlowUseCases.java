package de.thonktank.autosecretary.domain.usecase;

/** Focused application commands and queries for capacity-aware step flows. */
public final class FlowUseCases {
    public final SaveCapacityResource saveCapacityResource;
    public final SaveStepFlowDefinition saveStepFlowDefinition;
    public final LoadStepFlowSetup loadStepFlowSetup;
    public final SaveStepFlowSetup saveStepFlowSetup;
    public final LoadCapacityResources loadCapacityResources;
    public final ActivateReadyFlows activateReadyFlows;
    public final DeferFlowRun deferFlowRun;
    public final CancelFlowRun cancelFlowRun;
    public final AdjustFlowRunReadyAt adjustFlowRunReadyAt;
    public final ReorderFlowRun reorderFlowRun;
    public final LoadFlowRuns loadFlowRuns;

    public FlowUseCases(SaveCapacityResource saveCapacityResource,
                        SaveStepFlowDefinition saveStepFlowDefinition,
                        LoadStepFlowSetup loadStepFlowSetup,
                        SaveStepFlowSetup saveStepFlowSetup,
                        LoadCapacityResources loadCapacityResources,
                        ActivateReadyFlows activateReadyFlows,
                        DeferFlowRun deferFlowRun, CancelFlowRun cancelFlowRun,
                        AdjustFlowRunReadyAt adjustFlowRunReadyAt,
                        ReorderFlowRun reorderFlowRun, LoadFlowRuns loadFlowRuns) {
        this.saveCapacityResource = saveCapacityResource;
        this.saveStepFlowDefinition = saveStepFlowDefinition;
        this.loadStepFlowSetup = loadStepFlowSetup;
        this.saveStepFlowSetup = saveStepFlowSetup;
        this.loadCapacityResources = loadCapacityResources;
        this.activateReadyFlows = activateReadyFlows;
        this.deferFlowRun = deferFlowRun;
        this.cancelFlowRun = cancelFlowRun;
        this.adjustFlowRunReadyAt = adjustFlowRunReadyAt;
        this.reorderFlowRun = reorderFlowRun;
        this.loadFlowRuns = loadFlowRuns;
    }
}
