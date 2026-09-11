package de.thonktank.autosecretary.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Read model for the compact running strip and the functional run overview. */
public final class FlowRunSummary {
    /** Every actionable/waiting branch is represented; IDs refer to the frozen execution. */
    public static final class Step {
        public final String id;
        public final String waitId;
        public final String title;
        public final FlowGraphRun.State state;
        public final FlowDelayPolicy waitAfter;
        public final Long readyAtEpochMillis;

        public Step(FlowGraphRun.Step step) {
            id = step.id; waitId = step.waitId(); title = step.source.title;
            state = step.state; waitAfter = step.source.waitAfter;
            readyAtEpochMillis = step.readyAtEpochMillis;
        }
    }
    public static final class Resource {
        public final String id;
        public final String name;
        public final int units;
        public final int acquirePosition;
        public final int releasePosition;
        public final FlowResourceState state;
        public final String acquireStepId;
        public final String releaseStepId;
        public final boolean releaseAfterWait;

        public Resource(String id, String name, int units, int acquirePosition,
                        int releasePosition, FlowResourceState state) {
            this.id = id;
            this.name = name;
            this.units = units;
            this.acquirePosition = acquirePosition;
            this.releasePosition = releasePosition;
            this.state = state;
            this.acquireStepId = null;
            this.releaseStepId = null;
            this.releaseAfterWait = false;
        }

        public Resource(FlowGraphRun.Lease lease, String name) {
            this.id = lease.resourceId; this.name = name; this.units = lease.units;
            this.state = lease.state; this.acquireStepId = lease.acquireStepId;
            this.releaseStepId = lease.releaseStepId; this.releaseAfterWait = lease.releaseAfterWait;
            this.acquirePosition = -1; this.releasePosition = -1;
        }
    }

    public final String id;
    public final TaskId taskId;
    public final String taskTitle;
    public final String seedStepId;
    public final String seedTitle;
    public final String currentStepTitle;
    public final String currentStepId;
    public final int currentPosition;
    public final int totalSteps;
    public final FlowDelayPolicy delayAfter;
    public final StepFlowRunState state;
    public final Long readyAtEpochMillis;
    public final String currentExecutionOccurrenceId;
    public final long queueOrder;
    public final Long arrivalDelayMillis;
    public final List<Resource> resources;
    public final List<Step> steps;
    public final boolean collectionAvailable;
    public final long uncollectedTau;

    public FlowRunSummary(String id, TaskId taskId, String taskTitle, String seedStepId,
                          String seedTitle, String currentStepId, String currentStepTitle,
                          StepFlowRunState state,
                          Long readyAtEpochMillis, String currentExecutionOccurrenceId,
                          long queueOrder, int currentPosition, int totalSteps,
                          FlowDelayPolicy delayAfter, List<Resource> resources,
                          Long arrivalDelayMillis) {
        if (id == null || taskId == null || taskTitle == null || seedStepId == null
                || seedTitle == null || currentStepId == null || currentStepTitle == null
                || state == null
                || resources == null)
            throw new IllegalArgumentException("Flow run summary is incomplete");
        if (currentPosition < 0 || totalSteps < 1 || currentPosition >= totalSteps)
            throw new IllegalArgumentException("Flow run progress is invalid");
        this.id = id;
        this.taskId = taskId;
        this.taskTitle = taskTitle;
        this.seedStepId = seedStepId;
        this.seedTitle = seedTitle;
        this.currentStepId = currentStepId;
        this.currentStepTitle = currentStepTitle;
        this.currentPosition = currentPosition;
        this.totalSteps = totalSteps;
        this.delayAfter = delayAfter;
        this.state = state;
        this.readyAtEpochMillis = readyAtEpochMillis;
        this.currentExecutionOccurrenceId = currentExecutionOccurrenceId;
        this.queueOrder = queueOrder;
        this.arrivalDelayMillis = arrivalDelayMillis;
        this.resources = Collections.unmodifiableList(new ArrayList<>(resources));
        this.steps = Collections.emptyList();
        this.collectionAvailable = false;
        this.uncollectedTau = 0;
    }

    public FlowRunSummary(FlowGraphRunRecord record, String taskTitle, List<Resource> resources) {
        FlowGraphRun run = record.run;
        FlowGraphRun.Step seed = run.steps.get(run.startStepId);
        this.id = run.id; this.taskId = run.taskId; this.taskTitle = taskTitle;
        this.seedStepId = seed.source.id; this.seedTitle = seed.source.title;
        List<Step> states = new ArrayList<>();
        for (String stepId : run.graph.stepIds) states.add(new Step(run.steps.get(stepId)));
        this.steps = Collections.unmodifiableList(states);
        this.collectionAvailable = run.collectionAvailable();
        this.uncollectedTau = run.uncollectedTau();
        this.totalSteps = states.size();
        this.queueOrder = record.queueOrder;
        this.resources = Collections.unmodifiableList(new ArrayList<>(resources));
        // Aggregate compatibility labels, never used to choose an executable step.
        this.currentPosition = Math.min(totalSteps - 1, (int) states.stream()
                .filter(step -> step.state == FlowGraphRun.State.DONE).count());
        this.currentStepId = seed.source.id;
        this.currentStepTitle = seed.source.title;
        this.delayAfter = null;
        this.readyAtEpochMillis = run.nextReadyAt();
        this.currentExecutionOccurrenceId = null;
        this.arrivalDelayMillis = null;
        this.state = !run.availableSteps().isEmpty() || collectionAvailable ? StepFlowRunState.OFFERED
                : readyAtEpochMillis != null ? StepFlowRunState.WAITING_TIME : StepFlowRunState.WAITING_RESOURCE;
    }
}
