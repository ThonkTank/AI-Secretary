package de.thonktank.autosecretary.domain.repository;

import de.thonktank.autosecretary.domain.model.FlowCandidate;
import de.thonktank.autosecretary.domain.model.FlowGraphRun;
import de.thonktank.autosecretary.domain.model.FlowGraphRunRecord;
import de.thonktank.autosecretary.domain.model.TaskId;
import java.util.List;
import java.util.Map;

/** All calls share the enclosing admission/action/ledger transaction. */
public interface FlowGraphRunRepository {
    FlowGraphRunRecord find(String runId);
    FlowGraphRunRecord findBySourceKey(String sourceKey);
    FlowGraphRunRecord findByStepId(String runtimeStepId);
    List<FlowGraphRunRecord> active();
    List<FlowGraphRunRecord> active(TaskId taskId);
    Long nextReadyAtEpochMillis();
    /** Includes reserved and active claims; excludes only the specified run. */
    Map<String, Long> consumingUnitsExcluding(String runId);
    void insert(FlowCandidate candidate, FlowGraphRun run, long now);
    void update(FlowGraphRun run, long now);
    /** Only ordering metadata changes, never step state, waits, capacity or rewards. */
    boolean reorder(String runId, long queueOrder, long now);
    /** Allocates a legacy-compatible occurrence sequence without a current occurrence pointer. */
    int allocateExecutionSequence(String runId, long now);
}
