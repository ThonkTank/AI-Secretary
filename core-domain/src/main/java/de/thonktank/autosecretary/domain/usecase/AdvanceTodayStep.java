package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.repository.OccurrenceExecutionRepository;
import de.thonktank.autosecretary.domain.repository.RewardLedgerRepository;
import de.thonktank.autosecretary.domain.repository.ComboObligationRepository;
import de.thonktank.autosecretary.domain.repository.ComboPolicySource;
import de.thonktank.autosecretary.domain.today.AdvanceTodayStepResult;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

/** Advances a visible non-active step with its planned value and focuses it when still open. */
public final class AdvanceTodayStep {
    private final StepExecutionService completion;

    public AdvanceTodayStep(OccurrenceExecutionRepository occurrences,
                     RewardLedgerRepository rewards, ComboObligationRepository obligations, TransactionRunner transactions, Clock clock) {
        completion = new StepExecutionService(occurrences, rewards, obligations, transactions, clock);
    }

    public AdvanceTodayStep(OccurrenceExecutionRepository occurrences,
                     RewardLedgerRepository rewards, ComboObligationRepository obligations, TransactionRunner transactions, Clock clock,
                     ComboPolicySource policies) {
        completion = new StepExecutionService(occurrences, rewards, obligations, transactions, clock, policies);
    }

    public AdvanceTodayStep(OccurrenceExecutionRepository occurrences,
                     RewardLedgerRepository rewards, ComboObligationRepository obligations, TransactionRunner transactions, Clock clock,
                     ComboPolicySource policies,
                     FlowRuntimeCoordinator flows) {
        completion = new StepExecutionService(occurrences, rewards, obligations, transactions, clock, policies,
                flows);
    }

    public AdvanceTodayStepResult execute(String stepId) {
        return completion.advanceStepWithPlannedResult(stepId);
    }
}
