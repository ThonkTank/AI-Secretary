package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.model.TrainingAdjustment;
import de.thonktank.autosecretary.domain.model.TrainingAssistantState;
import de.thonktank.autosecretary.domain.model.TrainingAssistantProfile;
import de.thonktank.autosecretary.domain.model.TrainingLoadRequest;
import de.thonktank.autosecretary.domain.model.TrainingPrescription;
import de.thonktank.autosecretary.domain.model.StepPrescription;
import de.thonktank.autosecretary.domain.repository.TrainingRepository;
import de.thonktank.autosecretary.domain.repository.StepRepository;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

/** Restores the latest adjustment only while its after-state is still current. */
public final class UndoLatestTrainingAdjustment {
    private final TrainingRepository repository;
    private final StepRepository steps;
    private final TransactionRunner transactions;
    private final Clock clock;

    public UndoLatestTrainingAdjustment(StepRepository steps, TrainingRepository repository,
                                        TransactionRunner transactions, Clock clock) {
        this.steps = steps;
        this.repository = repository;
        this.transactions = transactions;
        this.clock = clock;
    }

    public boolean execute(String templateId) {
        return transactions.inTransaction(() -> {
            TaskStepTemplate template = steps.findTemplate(templateId);
            TrainingAdjustment adjustment = repository.latestTrainingAdjustment(templateId);
            if (template == null || adjustment == null
                    || adjustment.state != TrainingAdjustment.State.APPLIED
                    || !template.prescription.amount.equals(adjustment.after)
                    || !template.prescription.plannedLoad().equals(adjustment.afterLoad)
                    || template.assistantProfile == null) return false;
            StepPrescription before = new StepPrescription(adjustment.before,
                    template.prescription.rest, new TrainingPrescription(adjustment.beforeLoad,
                    template.prescription.targetRir()));
            steps.updateTemplate(template.withTraining(before,
                    new TrainingAssistantProfile(template.assistantProfile.policy,
                            TrainingAssistantState.calibrating())));
            repository.updateTrainingAdjustment(adjustment.undone());
            TrainingLoadRequest request = repository.openTrainingLoadRequest(templateId);
            if (request != null) repository.updateTrainingLoadRequest(request.cancel(
                    TrainingLoadRequest.Resolution.UNDONE, clock.today()));
            return true;
        });
    }
}
