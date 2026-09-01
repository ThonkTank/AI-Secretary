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

    public LoadFlowRuns(CatalogRepository tasks, FlowRepository runs) {
        this.tasks = tasks;
        this.runs = runs;
    }

    public List<FlowRunSummary> execute() {
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
            List<FlowRunSummary.Resource> resources = new ArrayList<>();
            for (FlowRunResourceSnapshot value : resourcesByRun.getOrDefault(run.id,
                    java.util.Collections.emptyList()))
                resources.add(new FlowRunSummary.Resource(value.resourceId, value.resourceName,
                        value.units, value.acquirePosition, value.releasePosition, value.state));
            result.add(new FlowRunSummary(run.id, run.taskId, task.title, run.seedStepId,
                    seed.text, current.text, run.state, run.readyAtEpochMillis,
                    run.currentSheetOccurrenceId, run.queueOrder, run.currentPosition,
                    steps.size(), current.delayAfter, resources));
        }
        return result;
    }
}
