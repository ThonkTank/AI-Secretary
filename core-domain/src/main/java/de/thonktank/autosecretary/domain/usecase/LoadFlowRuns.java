package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.FlowRunResourceSnapshot;
import de.thonktank.autosecretary.domain.model.FlowRunStepSnapshot;
import de.thonktank.autosecretary.domain.model.FlowRunSummary;
import de.thonktank.autosecretary.domain.model.StepFlowRun;
import de.thonktank.autosecretary.domain.model.StepFlowRunState;
import de.thonktank.autosecretary.domain.model.FlowResourceState;
import de.thonktank.autosecretary.domain.model.CapacityResource;
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
        List<FlowRunSummary> result = new ArrayList<>();
        for (FlowRunSummary summary : summaries(taskById, runs))
            if (summary.state != StepFlowRunState.PENDING_START) result.add(summary);
        return result;
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
        Map<String, Integer> usedByResource = new HashMap<>();
        for (FlowRunResourceSnapshot resource : runs.consumingFlowResources())
            usedByResource.put(resource.resourceId,
                    usedByResource.getOrDefault(resource.resourceId, 0) + resource.units);
        Map<String, Integer> capacityByResource = new HashMap<>();
        for (CapacityResource resource : runs.capacityResources())
            capacityByResource.put(resource.id, resource.capacity);
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
            boolean startable = canStart(run, snapshots, usedByResource, capacityByResource);
            result.add(new FlowRunSummary(run.id, run.taskId, task.title, run.seedStepId,
                    seed.text, current.sourceTemplateId, current.text, run.state,
                    run.readyAtEpochMillis,
                    run.currentSheetOccurrenceId, run.queueOrder, run.currentPosition,
                    steps.size(), current.delayAfter, resources, startable, arrivalDelay));
        }
        return result;
    }

    private static boolean canStart(StepFlowRun run,
                                    List<FlowRunResourceSnapshot> resources,
                                    Map<String, Integer> usedByResource,
                                    Map<String, Integer> capacityByResource) {
        if (run.state != StepFlowRunState.PENDING_START) return true;
        Map<String, Integer> required = new HashMap<>();
        Map<String, Integer> snapshotCapacity = new HashMap<>();
        for (FlowRunResourceSnapshot resource : resources) {
            snapshotCapacity.put(resource.resourceId, resource.capacityAtCreation);
            if (resource.state == FlowResourceState.PLANNED
                    && resource.acquirePosition == run.currentPosition)
                required.put(resource.resourceId,
                        required.getOrDefault(resource.resourceId, 0) + resource.units);
        }
        for (Map.Entry<String, Integer> requirement : required.entrySet()) {
            int capacity = capacityByResource.getOrDefault(requirement.getKey(),
                    snapshotCapacity.getOrDefault(requirement.getKey(), 0));
            if (usedByResource.getOrDefault(requirement.getKey(), 0)
                    + requirement.getValue() > capacity) return false;
        }
        return true;
    }
}
