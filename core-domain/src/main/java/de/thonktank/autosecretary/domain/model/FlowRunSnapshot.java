package de.thonktank.autosecretary.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Aggregate written atomically when a due entry step becomes a flow run. */
public final class FlowRunSnapshot {
    public final StepFlowRun run;
    public final List<FlowRunStepSnapshot> steps;
    public final List<FlowRunResourceSnapshot> resources;

    public FlowRunSnapshot(StepFlowRun run, List<FlowRunStepSnapshot> steps,
                           List<FlowRunResourceSnapshot> resources) {
        if (run == null || steps == null || steps.isEmpty() || resources == null)
            throw new IllegalArgumentException("Flow run snapshot is incomplete");
        for (int index = 0; index < steps.size(); index++) {
            FlowRunStepSnapshot step = steps.get(index);
            if (!run.id.equals(step.runId) || step.position != index)
                throw new IllegalArgumentException("Flow run step positions must be contiguous");
        }
        for (FlowRunResourceSnapshot resource : resources)
            if (!run.id.equals(resource.runId))
                throw new IllegalArgumentException("Flow resource belongs to another run");
        this.run = run;
        this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
        this.resources = Collections.unmodifiableList(new ArrayList<>(resources));
    }
}
