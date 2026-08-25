package de.thonktank.autosecretary.domain.repository;

/** Capabilities needed by the single runtime coordinator for flow progression. */
public interface FlowExecutionRepository extends OccurrenceExecutionRepository,
        StepFlowDefinitionRepository, StepFlowRunRepository {
}
