package de.thonktank.autosecretary.domain.usecase;

/** Focused application commands and queries for capacity-aware step flows. */
public final class FlowUseCases {
    public final GraphFlowRuntime runtime;
    public final LoadFlowGraph loadGraph;
    public final SaveFlowGraph saveGraph;
    public final SaveCapacityResource saveCapacityResource;
    public final SaveStepFlowDefinition saveStepFlowDefinition;
    public final LoadStepFlowSetup loadStepFlowSetup;
    public final SaveStepFlowSetup saveStepFlowSetup;
    public final LoadCapacityResources loadCapacityResources;
    public final ActivateReadyFlows activateReadyFlows;
    public final DeferFlowRun deferFlowRun;
    public final CancelFlowRun cancelFlowRun;
    public final AdjustFlowRunReadyAt adjustFlowRunReadyAt;
    public final PostponeFlowRun postponeFlowRun;
    public final ReorderFlowRun reorderFlowRun;
    public final LoadFlowRuns loadFlowRuns;
    public final StartFlowCandidate startFlowCandidate;

    public FlowUseCases(SaveCapacityResource saveCapacityResource,
                        SaveStepFlowDefinition saveStepFlowDefinition,
                        LoadStepFlowSetup loadStepFlowSetup,
                        SaveStepFlowSetup saveStepFlowSetup,
                        LoadCapacityResources loadCapacityResources,
                        ActivateReadyFlows activateReadyFlows,
                        DeferFlowRun deferFlowRun, CancelFlowRun cancelFlowRun,
                        AdjustFlowRunReadyAt adjustFlowRunReadyAt,
                        PostponeFlowRun postponeFlowRun,
                        ReorderFlowRun reorderFlowRun, LoadFlowRuns loadFlowRuns,
                        StartFlowCandidate startFlowCandidate) {
        this.runtime = null; this.loadGraph = null; this.saveGraph = null;
        this.saveCapacityResource = saveCapacityResource;
        this.saveStepFlowDefinition = saveStepFlowDefinition;
        this.loadStepFlowSetup = loadStepFlowSetup;
        this.saveStepFlowSetup = saveStepFlowSetup;
        this.loadCapacityResources = loadCapacityResources;
        this.activateReadyFlows = activateReadyFlows;
        this.deferFlowRun = deferFlowRun;
        this.cancelFlowRun = cancelFlowRun;
        this.adjustFlowRunReadyAt = adjustFlowRunReadyAt;
        this.postponeFlowRun = postponeFlowRun;
        this.reorderFlowRun = reorderFlowRun;
        this.loadFlowRuns = loadFlowRuns;
        this.startFlowCandidate = startFlowCandidate;
    }

    public FlowUseCases(GraphFlowRuntime runtime, LoadFlowGraph loadGraph, SaveFlowGraph saveGraph,
                        LoadGraphFlowSheets sheets, LoadCapacityResources capacities,
                        de.thonktank.autosecretary.Clock clock) {
        this.runtime = runtime; this.loadGraph = loadGraph; this.saveGraph = saveGraph;
        this.loadCapacityResources = capacities;
        this.activateReadyFlows = new ActivateReadyFlows(runtime);
        this.loadFlowRuns = new LoadFlowRuns(sheets, clock);
        this.startFlowCandidate = new StartFlowCandidate(runtime);
        // The old setup/run-screen entry points are retired by the product navigation cutover.
        this.saveCapacityResource = null; this.saveStepFlowDefinition = null;
        this.loadStepFlowSetup = null; this.saveStepFlowSetup = null;
        this.deferFlowRun = null; this.cancelFlowRun = null; this.adjustFlowRunReadyAt = null;
        this.postponeFlowRun = null; this.reorderFlowRun = null;
    }
}
