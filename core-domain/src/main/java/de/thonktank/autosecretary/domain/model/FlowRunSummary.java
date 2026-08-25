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
        public final FlowResourceState state;

        public Resource(String id, String name, int units, FlowResourceState state) {
            this.id = id;
            this.name = name;
            this.units = units;
            this.state = state;
        }
    }

    public final String id;
    public final TaskId taskId;
    public final String taskTitle;
    public final String seedStepId;
    public final String seedTitle;
    public final String currentStepTitle;
    public final StepFlowRunState state;
    public final Long readyAtEpochMillis;
    public final String currentSheetOccurrenceId;
    public final long queueOrder;
    public final List<Resource> resources;

    public FlowRunSummary(String id, TaskId taskId, String taskTitle, String seedStepId,
                          String seedTitle, String currentStepTitle, StepFlowRunState state,
                          Long readyAtEpochMillis, String currentSheetOccurrenceId,
                          long queueOrder, List<Resource> resources) {
        if (id == null || taskId == null || taskTitle == null || seedStepId == null
                || seedTitle == null || currentStepTitle == null || state == null
                || resources == null)
            throw new IllegalArgumentException("Flow run summary is incomplete");
        this.id = id;
        this.taskId = taskId;
        this.taskTitle = taskTitle;
        this.seedStepId = seedStepId;
        this.seedTitle = seedTitle;
        this.currentStepTitle = currentStepTitle;
        this.state = state;
        this.readyAtEpochMillis = readyAtEpochMillis;
        this.currentSheetOccurrenceId = currentSheetOccurrenceId;
        this.queueOrder = queueOrder;
        this.resources = Collections.unmodifiableList(new ArrayList<>(resources));
    }
}
