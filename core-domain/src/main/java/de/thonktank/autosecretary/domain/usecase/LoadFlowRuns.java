package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.FlowRunResourceSnapshot;
import de.thonktank.autosecretary.domain.model.FlowRunStepSnapshot;
import de.thonktank.autosecretary.domain.model.FlowRunSummary;
import de.thonktank.autosecretary.domain.model.StepFlowRun;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.repository.CatalogRepository;
import de.thonktank.autosecretary.domain.repository.FlowRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class LoadFlowRuns {
    private final CatalogRepository tasks;
    private final FlowRepository runs;
    private final LoadGraphFlowSheets graph;
    private final de.thonktank.autosecretary.Clock clock;

    public LoadFlowRuns(CatalogRepository tasks, FlowRepository runs) {
        this.tasks = tasks;
        this.runs = runs;
        this.graph = null; this.clock = null;
    }

    public LoadFlowRuns(LoadGraphFlowSheets graph, de.thonktank.autosecretary.Clock clock) {
        this.tasks = null; this.runs = null; this.graph = graph; this.clock = clock;
    }

    public List<FlowRunSummary> execute() {
        if (graph != null) return graph.execute(clock.today()).runs;
        Map<TaskId, Task> taskById = new HashMap<>();
        for (Task task : tasks.allTasks()) taskById.put(task.id, task);
        return summaries(taskById, runs);
    }

    static List<FlowRunSummary> summaries(Map<TaskId, Task> taskById,
                                          FlowRepository runs) {
        return summaries(taskById, runs, runs.activeFlowRuns());
    }

    static List<FlowRunSummary> summaries(Map<TaskId, Task> taskById,
                                          FlowRepository runs,
                                          List<StepFlowRun> active) {
        if (active.isEmpty()) return java.util.Collections.emptyList();
        List<String> runIds = new ArrayList<>();
        for (StepFlowRun run : active) runIds.add(run.id);
        Map<String, List<FlowRunStepSnapshot>> stepsByRun = new HashMap<>();
        for (FlowRunStepSnapshot step : runs.flowRunStepsFor(runIds))
            stepsByRun.computeIfAbsent(step.runId, ignored -> new ArrayList<>()).add(step);
        Map<String, List<FlowRunResourceSnapshot>> resourcesByRun = new HashMap<>();
        for (FlowRunResourceSnapshot resource : runs.flowRunResourcesFor(runIds))
            resourcesByRun.computeIfAbsent(resource.runId, ignored -> new ArrayList<>())
                    .add(resource);
        List<FlowRunSummary> result = new ArrayList<>();
        for (StepFlowRun run : active) {
            Task task = taskById.get(run.taskId);
            if (task == null) continue;
            List<FlowRunStepSnapshot> steps = stepsByRun.getOrDefault(run.id,
                    java.util.Collections.emptyList());
            if (steps.isEmpty() || run.currentPosition >= steps.size()) continue;
            FlowRunStepSnapshot seed = steps.get(0);
            FlowRunStepSnapshot current = steps.get(run.currentPosition);
            Long arrivalDelay = null;
            if (run.currentPosition > 0) {
                FlowRunStepSnapshot previous = steps.get(run.currentPosition - 1);
                if (previous.delayAfter != null) arrivalDelay = previous.chosenDelayMillis == null
                        ? previous.delayAfter.proposedDelayMillis()
                        : previous.chosenDelayMillis;
            }
            List<FlowRunSummary.Resource> resources = new ArrayList<>();
            List<FlowRunResourceSnapshot> snapshots = resourcesByRun.getOrDefault(run.id,
                    java.util.Collections.emptyList());
            for (FlowRunResourceSnapshot value : snapshots)
                resources.add(new FlowRunSummary.Resource(value.resourceId, value.resourceName,
                        value.units, value.acquirePosition, value.releasePosition, value.state));
            result.add(new FlowRunSummary(run.id, run.taskId, task.title, run.seedStepId,
                    seed.text, current.sourceTemplateId, current.text, run.state,
                    run.readyAtEpochMillis,
                    run.currentExecutionOccurrenceId, run.queueOrder, run.currentPosition,
                    steps.size(), current.delayAfter, resources, arrivalDelay));
        }
        return result;
    }

}
