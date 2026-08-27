package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.RewardReceipt;
import de.thonktank.autosecretary.domain.repository.OccurrenceExecutionRepository;
import de.thonktank.autosecretary.domain.repository.RewardLedgerRepository;
import de.thonktank.autosecretary.domain.repository.ComboPolicySource;

public final class CompleteRemainingSteps {
    private final OccurrenceCompletionService completion;
    public <T extends OccurrenceExecutionRepository & RewardLedgerRepository>
    CompleteRemainingSteps(T repository, Clock clock) {
        completion = new OccurrenceCompletionService(repository, clock);
    }
    public <T extends OccurrenceExecutionRepository & RewardLedgerRepository>
    CompleteRemainingSteps(T repository, Clock clock, ComboPolicySource policies) {
        completion = new OccurrenceCompletionService(repository, clock, policies);
    }
    public <T extends OccurrenceExecutionRepository & RewardLedgerRepository>
    CompleteRemainingSteps(T repository, Clock clock, ComboPolicySource policies,
                           FlowRuntimeCoordinator flows) {
        completion = new OccurrenceCompletionService(repository, clock, policies, flows);
    }
    public RewardReceipt execute(String occurrenceId) {
        return completion.completeRemainingSteps(occurrenceId);
    }
}
