package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.SystemClock;
import de.thonktank.autosecretary.SystemZoneIdProvider;
import de.thonktank.autosecretary.domain.model.RewardReceipt;
import de.thonktank.autosecretary.domain.repository.OccurrenceExecutionRepository;
import de.thonktank.autosecretary.domain.repository.RewardLedgerRepository;
import de.thonktank.autosecretary.domain.repository.ComboObligationRepository;
import de.thonktank.autosecretary.domain.repository.ComboPolicySource;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

public final class ToggleStep {
    private final StepExecutionService completion;
    public ToggleStep(OccurrenceExecutionRepository occurrences, RewardLedgerRepository rewards, ComboObligationRepository obligations,
                      TransactionRunner transactions) {
        this(occurrences, rewards, obligations, transactions, new SystemClock(new SystemZoneIdProvider()));
    }
    public ToggleStep(OccurrenceExecutionRepository occurrences, RewardLedgerRepository rewards, ComboObligationRepository obligations,
                      TransactionRunner transactions, Clock clock) {
        completion = new StepExecutionService(occurrences, rewards, obligations, transactions, clock);
    }
    public ToggleStep(OccurrenceExecutionRepository occurrences, RewardLedgerRepository rewards, ComboObligationRepository obligations,
               TransactionRunner transactions, Clock clock,
               ComboPolicySource policies) {
        completion = new StepExecutionService(occurrences, rewards, obligations, transactions, clock, policies);
    }
    public ToggleStep(OccurrenceExecutionRepository occurrences, RewardLedgerRepository rewards, ComboObligationRepository obligations,
               TransactionRunner transactions, Clock clock,
               ComboPolicySource policies,
               FlowRuntimeCoordinator flows) {
        completion = new StepExecutionService(occurrences, rewards, obligations, transactions, clock, policies,
                flows);
    }
    public RewardReceipt execute(String stepId) { return completion.toggleStep(stepId); }
    public RewardReceipt execute(String stepId, Long chosenDelayMillis) {
        return completion.toggleStep(stepId, chosenDelayMillis);
    }
}
