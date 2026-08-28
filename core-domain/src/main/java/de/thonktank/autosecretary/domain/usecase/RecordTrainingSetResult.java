package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.TrainingSetResult;
import de.thonktank.autosecretary.domain.repository.OccurrenceExecutionRepository;
import de.thonktank.autosecretary.domain.repository.RewardLedgerRepository;
import de.thonktank.autosecretary.domain.repository.TrainingRepository;
import de.thonktank.autosecretary.domain.repository.ComboPolicySource;
import de.thonktank.autosecretary.domain.today.StepExecutionResult;

/** Atomically records reps, resistance, RIR and then evaluates a completed set step. */
public final class RecordTrainingSetResult {
    private final OccurrenceExecutionRepository occurrences;
    private final TrainingRepository training;
    private final StepExecutionService execution;
    private final TrainingAdaptationService adaptation;

    public <T extends OccurrenceExecutionRepository & RewardLedgerRepository & TrainingRepository>
    RecordTrainingSetResult(T repository, Clock clock, IdGenerator ids,
                            ComboPolicySource policies) {
        occurrences = repository; training = repository;
        execution = new StepExecutionService(repository, clock, policies);
        adaptation = new TrainingAdaptationService(repository, repository, clock, ids);
    }

    public StepExecutionResult execute(String stepId, TrainingSetResult value) {
        return training.inTransaction(() -> {
            OccurrenceStep before = occurrences.findOccurrenceStep(stepId);
            int index = before == null || before.repetitionProgress == null ? -1
                    : before.repetitionProgress.actualRepetitions.size();
            StepExecutionResult result = execution.recordRepetitionResult(stepId,
                    value.repetitions);
            if (index >= 0 && (result.status == StepExecutionResult.Status.RECORDED
                    || result.status == StepExecutionResult.Status.COMPLETED)) {
                training.putTrainingSetResult(stepId, index, value);
                if (result.status == StepExecutionResult.Status.COMPLETED)
                    adaptation.evaluate(stepId);
            }
            return result;
        });
    }
}
