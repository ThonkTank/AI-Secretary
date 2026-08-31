package de.thonktank.autosecretary.data.local;

import de.thonktank.autosecretary.domain.repository.ComboObligationRepository;
import de.thonktank.autosecretary.domain.repository.DashboardReadRepository;
import de.thonktank.autosecretary.domain.repository.OccurrenceExecutionRepository;
import de.thonktank.autosecretary.domain.repository.StepFlowDefinitionRepository;
import de.thonktank.autosecretary.domain.repository.StepFlowRunRepository;
import de.thonktank.autosecretary.domain.repository.MaterializationRepository;
import de.thonktank.autosecretary.domain.repository.RewardLedgerRepository;
import de.thonktank.autosecretary.domain.repository.TaskDefinitionRepository;
import de.thonktank.autosecretary.domain.repository.TodayStepOrderRepository;
import de.thonktank.autosecretary.domain.repository.TrainingRepository;
import de.thonktank.autosecretary.domain.schedule.TaskScheduleRepository;
import de.thonktank.autosecretary.domain.steps.StepOrganizationRepository;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

/**
 * Infrastructure-only aggregate implemented by the Room gateway and full acceptance test store.
 * Domain and presentation code depend on the focused parent ports instead.
 */
public interface TaskStore extends TaskDefinitionRepository, DashboardReadRepository,
        OccurrenceExecutionRepository, RewardLedgerRepository, MaterializationRepository,
        TodayStepOrderRepository, TaskScheduleRepository, StepOrganizationRepository,
        ComboObligationRepository, StepFlowDefinitionRepository, StepFlowRunRepository,
        TrainingRepository,
        TransactionRunner {
}
