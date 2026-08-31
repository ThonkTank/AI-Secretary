package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.model.TrainingAdjustment;
import de.thonktank.autosecretary.domain.model.TrainingAssistantState;
import de.thonktank.autosecretary.domain.model.TrainingAssistantConfig;
import de.thonktank.autosecretary.domain.model.TrainingAssistantProfile;
import de.thonktank.autosecretary.domain.model.TrainingPrescription;
import de.thonktank.autosecretary.domain.model.StepPrescription;
import de.thonktank.autosecretary.domain.repository.OccurrenceExecutionRepository;
import de.thonktank.autosecretary.domain.repository.TrainingRepository;
import de.thonktank.autosecretary.domain.training.TrainingAdaptationEngine;

/** Applies one completed occurrence-step observation to its reusable template. */
final class TrainingAdaptationService {
    private final OccurrenceExecutionRepository occurrences;
    private final TrainingRepository training;
    private final Clock clock;
    private final IdGenerator ids;
    private final TrainingAdaptationEngine engine = new TrainingAdaptationEngine();

    TrainingAdaptationService(OccurrenceExecutionRepository occurrences,
                              TrainingRepository training, Clock clock, IdGenerator ids) {
        this.occurrences = occurrences; this.training = training;
        this.clock = clock; this.ids = ids;
    }

    void evaluate(String occurrenceStepId) {
        OccurrenceStep step = occurrences.findOccurrenceStep(occurrenceStepId);
        if (step == null || !step.done || step.sourceTemplateId == null
                || !(step.amount instanceof StepAmount.SetsReps)) return;
        TaskStepTemplate template = training.findTemplate(step.sourceTemplateId);
        if (template == null || !template.assistantEnabled()
                || !(template.amount instanceof StepAmount.SetsReps)) return;
        TrainingAssistantConfig config = template.legacyTrainingConfig();
        if (!template.amount.equals(step.amount)
                || !config.load.equals(step.plannedLoad)) {
            training.updateTrainingTemplate(template.withTraining(template.prescription,
                    new TrainingAssistantProfile(template.assistantProfile.policy,
                            TrainingAssistantState.calibrating())));
            return;
        }
        double effective = config.primaryMuscle == null ? 0
                : training.effectiveSetsSince(config.primaryMuscle,
                clock.today().minusDays(6), clock.today());
        StepAmount.SetsReps before = (StepAmount.SetsReps) template.amount;
        TrainingAdaptationEngine.Result result = engine.evaluate(before,
                config, template.assistantProfile.state,
                training.trainingSetResults(step.id), effective);
        StepPrescription next = new StepPrescription(result.prescription,
                template.prescription.rest,
                new TrainingPrescription(result.config.load, result.config.targetRir));
        training.updateTrainingTemplate(template.withTraining(next,
                new TrainingAssistantProfile(template.assistantProfile.policy, result.state)));
        if (!result.changedFrom(before, config)) return;
        training.insertTrainingAdjustment(new TrainingAdjustment(ids.nextId(), template.id,
                step.id, result.reason, before, config.load,
                result.prescription, result.config.load, clock.today(),
                TrainingAdjustment.State.APPLIED));
    }
}
