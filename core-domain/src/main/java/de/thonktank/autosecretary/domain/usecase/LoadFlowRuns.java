package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.FlowRunSummary;
import java.util.List;

public final class LoadFlowRuns {
    private final LoadGraphFlowSheets graph;
    private final Clock clock;
    public LoadFlowRuns(LoadGraphFlowSheets graph, Clock clock) { this.graph = graph; this.clock = clock; }
    public List<FlowRunSummary> execute() { return graph.execute(clock.today()).runs; }
}
