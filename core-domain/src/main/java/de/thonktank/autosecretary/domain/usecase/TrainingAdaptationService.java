package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.model.TrainingAdjustment;
import de.thonktank.autosecretary.domain.model.TrainingAssistantState;
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
        if (template == null || !template.trainingAssistant.enabled
                || !(template.amount instanceof StepAmount.SetsReps)) return;
        if (!template.amount.equals(step.amount)
                || !template.trainingAssistant.load.equals(step.plannedLoad)) {
            training.updateTrainingTemplate(template.withTraining(template.amount,
                    template.trainingAssistant, TrainingAssistantState.calibrating()));
            return;
        }
        double effective = template.trainingAssistant.primaryMuscle == null ? 0
                : training.effectiveSetsSince(template.trainingAssistant.primaryMuscle,
                clock.today().minusDays(6), clock.today());
        StepAmount.SetsReps before = (StepAmount.SetsReps) template.amount;
        TrainingAdaptationEngine.Result result = engine.evaluate(before,
                template.trainingAssistant, template.trainingState,
                training.trainingSetResults(step.id), effective);
        training.updateTrainingTemplate(template.withTraining(result.prescription,
                result.config, result.state));
        if (!result.changedFrom(before, template.trainingAssistant)) return;
        training.insertTrainingAdjustment(new TrainingAdjustment(ids.nextId(), template.id,
                step.id, result.reason, before, template.trainingAssistant.load,
                result.prescription, result.config.load, clock.today(),
                TrainingAdjustment.State.APPLIED));
    }
}
