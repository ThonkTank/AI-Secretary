package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One-way schema-24 execution conversion. Existing header/occurrence/placement/ledger rows stay
 * authoritative; this converts the cursor and positional leases, not those unrelated records.
 * The SQL migration supplies net earned VESSEL amounts (including partial sets) and the
 * already harvested VESSEL principal. Multiplied HEAD bookings must not be counted as principal.
 */
public final class MigrateLinearFlowExecution {
    public FlowGraphRun execute(FlowRunSnapshot legacy, Map<String, Long> earnedBySnapshotId,
                                long alreadyPaidTau) {
        StepFlowRun header = legacy.run;
        int cursor = header.currentPosition;
        if (cursor >= legacy.steps.size()) throw new IllegalArgumentException("Legacy cursor is outside its snapshot");
        if (header.state == StepFlowRunState.WAITING_TIME && cursor == 0)
            throw new IllegalArgumentException("Legacy timed wait has no completed predecessor");
        List<FlowGraphRun.Step> steps = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        List<FlowTileGraph.Link> links = new ArrayList<>();
        long totalEarned = 0L;
        for (int index = 0; index < legacy.steps.size(); index++) {
            FlowRunStepSnapshot old = legacy.steps.get(index);
            ids.add(old.id);
            if (index > 0) links.add(new FlowTileGraph.Link(legacy.steps.get(index - 1).id, old.id));
            FlowDelayPolicy wait = old.delayAfter == null ? FlowDelayPolicy.fixed(0) : old.delayAfter;
            FlowGraphRun.State state = FlowGraphRun.State.BLOCKED;
            if (header.state == StepFlowRunState.COMPLETED || index < cursor) state = FlowGraphRun.State.DONE;
            if (index == cursor && header.state == StepFlowRunState.OFFERED) state = FlowGraphRun.State.AVAILABLE;
            if (index == cursor && header.state == StepFlowRunState.WAITING_RESOURCE) state = FlowGraphRun.State.WAITING_RESOURCE;
            if (index == cursor - 1 && header.state == StepFlowRunState.WAITING_TIME) state = FlowGraphRun.State.WAITING_TIME;
            boolean acted = state == FlowGraphRun.State.DONE || state == FlowGraphRun.State.WAITING_TIME;
            Long chosen = acted ? old.chosenDelayMillis == null ? wait.proposedDelayMillis() : old.chosenDelayMillis : null;
            Long readyAt = state == FlowGraphRun.State.WAITING_TIME ? header.readyAtEpochMillis : null;
            // Schema 24 has no per-action timestamp. Keep a conservative known lower bound;
            // never reconstruct an action time from a possibly extended ready timestamp.
            Long actionAt = acted ? readyAt == null ? header.createdAtEpochMillis
                    : Math.min(header.createdAtEpochMillis, readyAt) : null;
            long earned = earnedBySnapshotId.getOrDefault(old.id, 0L);
            totalEarned = Math.addExact(totalEarned, earned);
            steps.add(new FlowGraphRun.Step(old.id, new FlowGraphDefinition.Node(old.sourceTemplateId,
                    old.text, old.prescription, old.note, wait), state, chosen, readyAt, actionAt, earned));
        }
        if (!ids.containsAll(earnedBySnapshotId.keySet()))
            throw new IllegalArgumentException("Reward refers to a foreign run step");
        List<FlowGraphRun.Lease> leases = new ArrayList<>();
        for (FlowRunResourceSnapshot old : legacy.resources) {
            if (old.acquirePosition >= ids.size() || old.releasePosition >= ids.size())
                throw new IllegalArgumentException("Legacy resource points outside the run");
            leases.add(new FlowGraphRun.Lease(old.id, old.sourceLeaseId, old.resourceId,
                    ids.get(old.acquirePosition), ids.get(old.releasePosition), old.units,
                    false, old.state));
        }
        boolean collected = header.state == StepFlowRunState.COMPLETED && alreadyPaidTau >= totalEarned;
        return new FlowGraphRun(header.id, header.taskId, ids.get(0), new FlowTileGraph(ids, links),
                steps, leases, alreadyPaidTau, collected, header.state == StepFlowRunState.CANCELLED);
    }
}
