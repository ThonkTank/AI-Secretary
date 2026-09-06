package de.thonktank.autosecretary.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Read model for the compact running strip and the functional run overview. */
public final class FlowRunSummary {
    public static final class Resource {
        public final String id;
        public final String name;
        public final int units;
        public final int acquirePosition;
        public final int releasePosition;
        public final FlowResourceState state;

        public Resource(String id, String name, int units, int acquirePosition,
                        int releasePosition, FlowResourceState state) {
            this.id = id;
            this.name = name;
            this.units = units;
            this.acquirePosition = acquirePosition;
            this.releasePosition = releasePosition;
            this.state = state;
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
    public final String currentSheetOccurrenceId;
    public final long queueOrder;
    public final boolean startable;
    public final List<Resource> resources;

    public FlowRunSummary(String id, TaskId taskId, String taskTitle, String seedStepId,
                          String seedTitle, String currentStepId, String currentStepTitle,
                          StepFlowRunState state,
                          Long readyAtEpochMillis, String currentSheetOccurrenceId,
                          long queueOrder, int currentPosition, int totalSteps,
                          FlowDelayPolicy delayAfter, List<Resource> resources,
                          boolean startable) {
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
        this.currentSheetOccurrenceId = currentSheetOccurrenceId;
        this.queueOrder = queueOrder;
        this.startable = startable;
        this.resources = Collections.unmodifiableList(new ArrayList<>(resources));
    }
}
