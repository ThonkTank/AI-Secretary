package de.thonktank.autosecretary.domain.repository;

import de.thonktank.autosecretary.domain.model.FlowGraphDefinition;
import de.thonktank.autosecretary.domain.model.TaskId;

/** Graph definitions and the explicit flow type, independent from running snapshots. */
public interface FlowGraphDefinitionRepository {
    boolean isFlowTask(TaskId taskId);
    FlowGraphDefinition find(TaskId taskId);
    /** Marks even a one-node definition as FLOW; caller includes task/steps/resources in its transaction. */
    void replace(FlowGraphDefinition definition);
    /** Durable result for one editor save attempt, including process death after commit. */
    TaskId findSaveResult(String requestKey);
    void recordSaveResult(String requestKey, TaskId taskId);
}
