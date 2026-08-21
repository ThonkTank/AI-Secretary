package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.RewardReceipt;
import de.thonktank.autosecretary.domain.repository.OccurrenceExecutionRepository;
import de.thonktank.autosecretary.domain.repository.RewardLedgerRepository;

public final class CompleteRemainingSteps {
    private final OccurrenceCompletionService completion;
    public <T extends OccurrenceExecutionRepository & RewardLedgerRepository>
    CompleteRemainingSteps(T repository, Clock clock) {
        completion = new OccurrenceCompletionService(repository, clock);
    }
    public RewardReceipt execute(String occurrenceId) {
        return completion.completeRemainingSteps(occurrenceId);
    }
}
