package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.SetResult;
import de.thonktank.autosecretary.domain.repository.ComboPolicySource;
import de.thonktank.autosecretary.domain.repository.OccurrenceExecutionRepository;
import de.thonktank.autosecretary.domain.repository.RewardLedgerRepository;
import de.thonktank.autosecretary.domain.repository.TrainingRepository;
import de.thonktank.autosecretary.domain.today.StepExecutionResult;

/** Owns the single transaction for result, completion, reward and adaptation consequences. */
public final class RecordSetResult {
    private final TrainingRepository training;
    private final StepExecutionService execution;
    private final TrainingAdaptationService adaptation;

    public <T extends OccurrenceExecutionRepository & RewardLedgerRepository & TrainingRepository>
    RecordSetResult(T repository, Clock clock, IdGenerator ids, ComboPolicySource policies) {
        training = repository;
        execution = new StepExecutionService(repository, clock, policies);
        adaptation = new TrainingAdaptationService(repository, repository, clock, ids);
    }

    public StepExecutionResult execute(String stepId, SetResult value) {
        return training.inTransaction(() -> {
            StepExecutionResult result = execution.recordSetResultInsideTransaction(stepId, value);
            if (result.status == StepExecutionResult.Status.COMPLETED)
                adaptation.evaluate(stepId);
            return result;
        });
    }
}
