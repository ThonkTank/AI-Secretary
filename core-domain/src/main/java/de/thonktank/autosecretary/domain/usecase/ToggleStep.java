package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.SystemClock;
import de.thonktank.autosecretary.SystemZoneIdProvider;
import de.thonktank.autosecretary.domain.model.RewardReceipt;
import de.thonktank.autosecretary.domain.repository.OccurrenceExecutionRepository;
import de.thonktank.autosecretary.domain.repository.RewardLedgerRepository;

public final class ToggleStep {
    private final StepExecutionService completion;
    public <T extends OccurrenceExecutionRepository & RewardLedgerRepository>
    ToggleStep(T repository) {
        this(repository, new SystemClock(new SystemZoneIdProvider()));
    }
    public <T extends OccurrenceExecutionRepository & RewardLedgerRepository>
    ToggleStep(T repository, Clock clock) {
        completion = new StepExecutionService(repository, clock);
    }
    public RewardReceipt execute(String stepId) { return completion.toggleStep(stepId); }
}
