package de.thonktank.autosecretary.domain.usecase;

/** The single productive graph editor and execution boundary. */
public final class FlowUseCases {
    public final GraphFlowRuntime runtime;
    public final LoadFlowGraph loadGraph;
    public final SaveFlowGraph saveGraph;
    public final ActivateReadyFlows activateReadyFlows;
    public final LoadFlowRuns loadFlowRuns;
    public final StartFlowCandidate startFlowCandidate;

    public FlowUseCases(GraphFlowRuntime runtime, LoadFlowGraph loadGraph, SaveFlowGraph saveGraph,
                        LoadGraphFlowSheets sheets,
                        de.thonktank.autosecretary.Clock clock) {
        this.runtime = runtime; this.loadGraph = loadGraph; this.saveGraph = saveGraph;
        this.activateReadyFlows = new ActivateReadyFlows(runtime);
        this.loadFlowRuns = new LoadFlowRuns(sheets, clock);
        this.startFlowCandidate = new StartFlowCandidate(runtime);
    }
}
