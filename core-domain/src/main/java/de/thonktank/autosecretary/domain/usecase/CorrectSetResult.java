package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.SetResult;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.model.TrainingAssistantProfile;
import de.thonktank.autosecretary.domain.model.TrainingAssistantState;
import de.thonktank.autosecretary.domain.model.TrainingLoadRequest;
import de.thonktank.autosecretary.domain.repository.ComboPolicySource;
import de.thonktank.autosecretary.domain.repository.OccurrenceExecutionRepository;
import de.thonktank.autosecretary.domain.repository.RewardLedgerRepository;
import de.thonktank.autosecretary.domain.repository.ComboObligationRepository;
import de.thonktank.autosecretary.domain.repository.TrainingRepository;
import de.thonktank.autosecretary.domain.today.StepExecutionResult;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

/** Atomically corrects one result and restarts learning for its exercise. */
public final class CorrectSetResult {
    private final OccurrenceExecutionRepository occurrences;
    private final TrainingRepository training;
    private final TransactionRunner transactions;
    private final StepExecutionService execution;
    private final Clock clock;

    public CorrectSetResult(OccurrenceExecutionRepository occurrences,
                            RewardLedgerRepository rewards,
                            ComboObligationRepository obligations,
                            TrainingRepository training,
                            TransactionRunner transactions, Clock clock,
                            ComboPolicySource policies) {
        this(occurrences, rewards, obligations, training, transactions, clock, policies, null);
    }

    public CorrectSetResult(OccurrenceExecutionRepository occurrences,
                            RewardLedgerRepository rewards,
                            ComboObligationRepository obligations,
                            TrainingRepository training,
                            TransactionRunner transactions, Clock clock,
                            ComboPolicySource policies, FlowRuntimeCoordinator flows) {
        this.occurrences = occurrences;
        this.training = training;
        this.transactions = transactions;
        this.clock = clock;
        execution = new StepExecutionService(occurrences, rewards, obligations, transactions, clock,
                new RewardCalculator(policies), new CompletionStateMachine(), flows);
    }

    public StepExecutionResult execute(String stepId, int index, SetResult value) {
        return transactions.inTransaction(() -> {
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
                TrainingLoadRequest request = training.openTrainingLoadRequest(
                        step.sourceTemplateId);
                if (request != null) training.updateTrainingLoadRequest(request.cancel(
                        TrainingLoadRequest.Resolution.SET_RESULT_CORRECTED, clock.today()));
            }
            return result;
        });
    }
}
