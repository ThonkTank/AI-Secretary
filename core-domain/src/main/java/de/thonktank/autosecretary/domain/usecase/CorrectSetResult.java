package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.SetResult;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.model.TrainingAssistantProfile;
import de.thonktank.autosecretary.domain.model.TrainingAssistantState;
import de.thonktank.autosecretary.domain.repository.ComboPolicySource;
import de.thonktank.autosecretary.domain.repository.OccurrenceExecutionRepository;
import de.thonktank.autosecretary.domain.repository.RewardLedgerRepository;
import de.thonktank.autosecretary.domain.repository.TrainingRepository;
import de.thonktank.autosecretary.domain.today.StepExecutionResult;

/** Atomically corrects one result and restarts learning for its exercise. */
public final class CorrectSetResult {
    private final OccurrenceExecutionRepository occurrences;
    private final TrainingRepository training;
    private final StepExecutionService execution;

    public <T extends OccurrenceExecutionRepository & RewardLedgerRepository & TrainingRepository>
    CorrectSetResult(T repository, Clock clock, ComboPolicySource policies) {
        occurrences = repository;
        training = repository;
        execution = new StepExecutionService(repository, clock, policies);
    }

    public StepExecutionResult execute(String stepId, int index, SetResult value) {
        return training.inTransaction(() -> {
            StepExecutionResult result = execution.correctSetResultInsideTransaction(
                    stepId, index, value);
            if (result.status != StepExecutionResult.Status.CORRECTED) return result;
            OccurrenceStep step = occurrences.findOccurrenceStep(stepId);
            if (step != null && step.sourceTemplateId != null) {
                TaskStepTemplate template = training.findTemplate(step.sourceTemplateId);
                if (template != null && template.assistantProfile != null)
                    training.updateTrainingTemplate(template.withTraining(template.prescription,
                            new TrainingAssistantProfile(template.assistantProfile.policy,
                                    TrainingAssistantState.calibrating())));
            }
            return result;
        });
    }
}
