package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.model.TrainingAssistantState;
import de.thonktank.autosecretary.domain.model.TrainingSetResult;
import de.thonktank.autosecretary.domain.repository.OccurrenceExecutionRepository;
import de.thonktank.autosecretary.domain.repository.RewardLedgerRepository;
import de.thonktank.autosecretary.domain.repository.TrainingRepository;
import de.thonktank.autosecretary.domain.repository.ComboPolicySource;
import de.thonktank.autosecretary.domain.today.StepExecutionResult;

/** Corrects a detailed set and conservatively restarts learning for its exercise. */
public final class CorrectTrainingSetResult {
    private final OccurrenceExecutionRepository occurrences;
    private final TrainingRepository training;
    private final StepExecutionService execution;

    public <T extends OccurrenceExecutionRepository & RewardLedgerRepository & TrainingRepository>
    CorrectTrainingSetResult(T repository, Clock clock, ComboPolicySource policies) {
        occurrences = repository; training = repository;
        execution = new StepExecutionService(repository, clock, policies);
    }

    public StepExecutionResult execute(String stepId, int index, TrainingSetResult value) {
        return training.inTransaction(() -> {
            StepExecutionResult result = execution.correctRepetitionResult(stepId, index,
                    value.repetitions);
            if (result.status != StepExecutionResult.Status.CORRECTED) return result;
            training.putTrainingSetResult(stepId, index, value);
            OccurrenceStep step = occurrences.findOccurrenceStep(stepId);
            if (step != null && step.sourceTemplateId != null) {
                TaskStepTemplate template = training.findTemplate(step.sourceTemplateId);
                if (template != null && template.trainingAssistant.enabled)
                    training.updateTrainingTemplate(template.withTraining(template.amount,
                            template.trainingAssistant, TrainingAssistantState.calibrating()));
            }
            return result;
        });
    }
}
