package de.thonktank.autosecretary.domain.repository;

import de.thonktank.autosecretary.domain.schedule.TaskScheduleRepository;
import de.thonktank.autosecretary.domain.steps.StepOrganizationRepository;

/**
 * Composition-root contract implemented by the concrete store. Concrete feature use cases
 * depend on one of the narrower parent ports; only application wiring consumes this interface.
 */
public interface ApplicationTaskRepository extends TaskDefinitionRepository,
        DashboardReadRepository, OccurrenceExecutionRepository, RewardLedgerRepository,
        MaterializationRepository, TodayStepOrderRepository, TaskScheduleRepository,
        StepOrganizationRepository, ComboObligationRepository,
        FlowExecutionRepository {
}
