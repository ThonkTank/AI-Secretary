package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.ResistanceLoad;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.StepPrescription;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.model.TrainingAdjustment;
import de.thonktank.autosecretary.domain.model.TrainingAssistantProfile;
import de.thonktank.autosecretary.domain.model.TrainingAssistantState;
import de.thonktank.autosecretary.domain.model.TrainingDecision;
import de.thonktank.autosecretary.domain.model.TrainingLoadRequest;
import de.thonktank.autosecretary.domain.model.TrainingPrescription;
import de.thonktank.autosecretary.domain.repository.TrainingRepository;
import de.thonktank.autosecretary.domain.repository.StepRepository;
import de.thonktank.autosecretary.domain.training.TrainingAdaptationEngine;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

/** Resolves a durable load question without inventing equipment increments. */
public final class ResolveTrainingLoadRequest {
    public enum Result {
        APPLIED,
        SETS_ADDED,
        HELD,
        DEFERRED,
        NO_OPEN_REQUEST,
        INVALID_LOAD,
        WRONG_DIRECTION,
        JUMP_TOO_LARGE
    }

    private final TrainingRepository repository;
    private final StepRepository steps;
    private final TransactionRunner transactions;
    private final Clock clock;
    private final IdGenerator ids;
    private final TrainingAdaptationEngine engine = new TrainingAdaptationEngine();

    public ResolveTrainingLoadRequest(StepRepository steps, TrainingRepository repository,
                                      TransactionRunner transactions, Clock clock,
                                      IdGenerator ids) {
        this.steps = steps;
        this.repository = repository;
        this.transactions = transactions;
        this.clock = clock;
        this.ids = ids;
    }

    public Result applyConcreteLoad(String templateId, ResistanceLoad load) {
        return transactions.inTransaction(() -> applyInside(templateId, load));
    }

    public Result noHigherLoad(String templateId) {
        return transactions.inTransaction(() -> noHigherInside(templateId));
    }

    public Result later(String templateId) {
        return repository.openTrainingLoadRequest(templateId) == null
                ? Result.NO_OPEN_REQUEST : Result.DEFERRED;
    }

    private Result applyInside(String templateId, ResistanceLoad load) {
        TrainingLoadRequest request = repository.openTrainingLoadRequest(templateId);
        TaskStepTemplate template = steps.findTemplate(templateId);
        if (request == null || template == null || template.assistantProfile == null)
            return Result.NO_OPEN_REQUEST;
        if (!validComparableLoad(request.currentLoad, load)) return Result.INVALID_LOAD;
        long beforeMilli = request.currentLoad.milliUnits;
        long afterMilli = load.milliUnits;
        boolean increasingResistance = request.currentLoad.mode
                == ResistanceLoad.Mode.ASSISTED_BODYWEIGHT
                ? afterMilli < beforeMilli : afterMilli > beforeMilli;
        boolean expectedIncrease = request.direction == TrainingDecision.LoadDirection.PROGRESS;
        if (afterMilli == beforeMilli || increasingResistance != expectedIncrease)
            return Result.WRONG_DIRECTION;
        long delta = Math.abs(afterMilli - beforeMilli);
        if (delta * 100L > beforeMilli * TrainingAdaptationEngine.MAXIMUM_LOAD_JUMP_PERCENT)
            return Result.JUMP_TOO_LARGE;

        StepAmount.SetsReps before = (StepAmount.SetsReps) template.prescription.amount;
        int repetitions = request.direction == TrainingDecision.LoadDirection.PROGRESS
                ? template.assistantProfile.policy.minRepetitions : before.repetitions;
        StepAmount.SetsReps after = (StepAmount.SetsReps) StepAmount.setsReps(
                before.sets, repetitions);
        TrainingAssistantState oldState = template.assistantProfile.state;
        TrainingAssistantState state = new TrainingAssistantState(
                TrainingAssistantState.Status.ACTIVE, oldState.eligibleObservations, 0, 0);
        StepPrescription prescription = new StepPrescription(after,
                template.prescription.rest,
                new TrainingPrescription(load, template.prescription.targetRir()));
        steps.updateTemplate(template.withTraining(prescription,
                new TrainingAssistantProfile(template.assistantProfile.policy, state)));
        repository.insertTrainingAdjustment(new TrainingAdjustment(ids.nextId(), template.id,
                request.sourceOccurrenceStepId, TrainingDecision.Reason.LOAD_APPLIED,
                before, request.currentLoad, after, load, clock.today(),
                TrainingAdjustment.State.APPLIED, repository.nextTrainingAuditOrder(),
                request.ruleVersion));
        repository.updateTrainingLoadRequest(request.resolve(
                TrainingLoadRequest.Resolution.LOAD_APPLIED, clock.today()));
        return Result.APPLIED;
    }

    private Result noHigherInside(String templateId) {
        TrainingLoadRequest request = repository.openTrainingLoadRequest(templateId);
        TaskStepTemplate template = steps.findTemplate(templateId);
        if (request == null || template == null || template.assistantProfile == null)
            return Result.NO_OPEN_REQUEST;
        if (request.direction != TrainingDecision.LoadDirection.PROGRESS)
            return Result.WRONG_DIRECTION;
        double effective = template.assistantProfile.policy.primaryMuscle == null ? 0
                : repository.effectiveSetsSince(template.assistantProfile.policy.primaryMuscle,
                clock.today().minusDays(6), clock.today());
        StepAmount.SetsReps before =
                (StepAmount.SetsReps) template.prescription.amount;
        TrainingDecision decision = engine.progressSetsAfterUnavailableLoad(
                template.prescription, template.assistantProfile, effective);
        repository.updateTrainingLoadRequest(request.resolve(
                TrainingLoadRequest.Resolution.NO_HIGHER_LOAD, clock.today()));
        steps.updateTemplate(template.withTraining(decision.nextPrescription,
                new TrainingAssistantProfile(template.assistantProfile.policy,
                        decision.nextState)));
        if (decision.action != TrainingDecision.Action.APPLY) return Result.HELD;
        StepAmount.SetsReps after =
                (StepAmount.SetsReps) decision.nextPrescription.amount;
        repository.insertTrainingAdjustment(new TrainingAdjustment(ids.nextId(), template.id,
                request.sourceOccurrenceStepId, decision.reason, before, request.currentLoad,
                after, decision.nextPrescription.training.load, clock.today(),
                TrainingAdjustment.State.APPLIED, repository.nextTrainingAuditOrder(),
                decision.ruleVersion));
        return Result.SETS_ADDED;
    }

    private static boolean validComparableLoad(ResistanceLoad before, ResistanceLoad after) {
        return before != null && after != null && before.adjustable() && after.adjustable()
                && before.mode == after.mode && before.unit == after.unit
                && before.milliUnits != null && before.milliUnits > 0
                && after.milliUnits != null && after.milliUnits > 0;
    }
}
