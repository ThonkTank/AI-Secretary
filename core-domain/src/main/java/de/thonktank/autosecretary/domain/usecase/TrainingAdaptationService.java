package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.model.TrainingAdjustment;
import de.thonktank.autosecretary.domain.model.TrainingDecision;
import de.thonktank.autosecretary.domain.model.TrainingLoadRequest;
import de.thonktank.autosecretary.domain.model.TrainingAssistantState;
import de.thonktank.autosecretary.domain.model.TrainingAssistantProfile;
import de.thonktank.autosecretary.domain.model.StepPrescription;
import de.thonktank.autosecretary.domain.repository.StepRepository;
import de.thonktank.autosecretary.domain.repository.TrainingRepository;
import de.thonktank.autosecretary.domain.training.TrainingAdaptationEngine;

/** Applies one completed occurrence-step observation to its reusable template. */
final class TrainingAdaptationService {
    private final StepRepository steps;
    private final TrainingRepository training;
    private final Clock clock;
    private final IdGenerator ids;
    private final TrainingAdaptationEngine engine = new TrainingAdaptationEngine();

    TrainingAdaptationService(StepRepository steps,
                              TrainingRepository training, Clock clock, IdGenerator ids) {
        this.steps = steps; this.training = training;
        this.clock = clock; this.ids = ids;
    }

    void evaluate(String occurrenceStepId) {
        OccurrenceStep step = steps.findOccurrenceStep(occurrenceStepId);
        if (step == null || !step.done || step.sourceTemplateId == null
                || !(step.prescription.amount instanceof StepAmount.SetsReps)) return;
        TaskStepTemplate template = steps.findTemplate(step.sourceTemplateId);
        if (template == null || !template.assistantEnabled()
                || !(template.prescription.amount instanceof StepAmount.SetsReps)) return;
        if (training.openTrainingLoadRequest(template.id) != null) return;
        if (!template.prescription.amount.equals(step.prescription.amount)
                || !template.prescription.plannedLoad()
                .equals(step.prescription.plannedLoad())) {
            steps.updateTemplate(template.withTraining(template.prescription,
                    new TrainingAssistantProfile(template.assistantProfile.policy,
                            TrainingAssistantState.calibrating())));
            return;
        }
        double effective = template.assistantProfile.policy.primaryMuscle == null ? 0
                : training.effectiveSetsSince(template.assistantProfile.policy.primaryMuscle,
                clock.today().minusDays(6), clock.today());
        StepPrescription before = template.prescription;
        TrainingDecision result = engine.evaluate(before, template.assistantProfile,
                step.repetitionProgress.results, effective);
        steps.updateTemplate(template.withTraining(result.nextPrescription,
                new TrainingAssistantProfile(template.assistantProfile.policy,
                        result.nextState)));
        if (result.action == TrainingDecision.Action.REQUEST_NEXT_LOAD) {
            training.insertTrainingLoadRequest(TrainingLoadRequest.open(ids.nextId(), template.id,
                    step.id, result.loadDirection, result.nextPrescription.training.load,
                    clock.today(),
                    training.nextTrainingAuditOrder(), result.ruleVersion));
            return;
        }
        if (!result.changedFrom(before)) return;
        StepAmount.SetsReps beforeAmount = (StepAmount.SetsReps) before.amount;
        StepAmount.SetsReps afterAmount =
                (StepAmount.SetsReps) result.nextPrescription.amount;
        training.insertTrainingAdjustment(new TrainingAdjustment(ids.nextId(), template.id,
                step.id, result.reason, beforeAmount, before.training.load,
                afterAmount, result.nextPrescription.training.load, clock.today(),
                TrainingAdjustment.State.APPLIED, training.nextTrainingAuditOrder(),
                result.ruleVersion));
    }
}
