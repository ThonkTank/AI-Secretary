package de.thonktank.autosecretary.domain.repository;

import de.thonktank.autosecretary.domain.model.CapacityResource;
import de.thonktank.autosecretary.domain.model.StepResourceLease;
import de.thonktank.autosecretary.domain.model.StepTransition;
import de.thonktank.autosecretary.domain.model.TaskId;

import java.util.List;
import java.util.Collections;

/** Definition-side storage for generic step flows and user-named capacity pools. */
public interface StepFlowDefinitionRepository extends TransactionalRepository {
    default List<CapacityResource> capacityResources() { return Collections.emptyList(); }
    default CapacityResource findCapacityResource(String id) { return null; }
    default void putCapacityResource(CapacityResource resource) {
        throw new UnsupportedOperationException("Step flows are not supported by this store");
    }
    default void deleteCapacityResource(String id) {
        throw new UnsupportedOperationException("Step flows are not supported by this store");
    }
    default List<StepTransition> stepTransitions(TaskId taskId) {
        return Collections.emptyList();
    }
    default List<StepResourceLease> stepResourceLeases(TaskId taskId) {
        return Collections.emptyList();
    }
    default void replaceStepFlow(TaskId taskId, List<StepTransition> transitions,
                                 List<StepResourceLease> leases) {
        throw new UnsupportedOperationException("Step flows are not supported by this store");
    }
    default void updateStepTransition(StepTransition transition) {
        throw new UnsupportedOperationException("Step flows are not supported by this store");
    }
}
