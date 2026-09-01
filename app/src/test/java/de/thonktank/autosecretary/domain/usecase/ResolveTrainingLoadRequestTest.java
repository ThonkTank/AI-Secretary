package de.thonktank.autosecretary.domain.usecase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.ResistanceLoad;
import de.thonktank.autosecretary.domain.model.RestTimerPolicy;
import de.thonktank.autosecretary.domain.model.StepActivationKind;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.StepPrescription;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.model.TrainingAssistantPolicy;
import de.thonktank.autosecretary.domain.model.TrainingAssistantProfile;
import de.thonktank.autosecretary.domain.model.TrainingAssistantState;
import de.thonktank.autosecretary.domain.model.TrainingDecision;
import de.thonktank.autosecretary.domain.model.TrainingLoadRequest;
import de.thonktank.autosecretary.domain.model.TrainingMuscleGroup;
import de.thonktank.autosecretary.domain.model.TrainingPrescription;
import de.thonktank.autosecretary.testing.InMemoryTrainingRepository;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalTime;

public final class ResolveTrainingLoadRequestTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 31);

    @Test public void concreteLoadAppliesOnlyAtTenPercentAndKeepsLargerQuestionOpen() {
        InMemoryTrainingRepository repository = new InMemoryTrainingRepository();
        TaskStepTemplate template = template("row", ResistanceLoad.Mode.EXTERNAL, 50_000, 3, 12);
        repository.insertTemplate(template);
        repository.insertTrainingLoadRequest(request(repository, template,
                TrainingDecision.LoadDirection.PROGRESS));
        ResolveTrainingLoadRequest useCase = new ResolveTrainingLoadRequest(repository.steps, repository, repository.transactions,
                new FixedClock(), new SequenceIds());

        assertEquals(ResolveTrainingLoadRequest.Result.JUMP_TOO_LARGE,
                useCase.applyConcreteLoad(template.id, load(ResistanceLoad.Mode.EXTERNAL, 56_000)));
        assertNotNull(repository.openTrainingLoadRequest(template.id));
        assertEquals(ResolveTrainingLoadRequest.Result.APPLIED,
                useCase.applyConcreteLoad(template.id, load(ResistanceLoad.Mode.EXTERNAL, 55_000)));

        assertNull(repository.openTrainingLoadRequest(template.id));
        TaskStepTemplate changed = repository.findTemplate(template.id);
        assertEquals(Long.valueOf(55_000), changed.prescription.plannedLoad().milliUnits);
        assertEquals(8, ((StepAmount.SetsReps) changed.prescription.amount).repetitions);
        assertEquals(TrainingDecision.Reason.LOAD_APPLIED,
                repository.latestTrainingAdjustment(template.id).reason);
    }

    @Test public void assistedBodyweightProgressionRequiresLessAssistance() {
        InMemoryTrainingRepository repository = new InMemoryTrainingRepository();
        TaskStepTemplate template = template("pullup",
                ResistanceLoad.Mode.ASSISTED_BODYWEIGHT, 20_000, 3, 12);
        repository.insertTemplate(template);
        repository.insertTrainingLoadRequest(request(repository, template,
                TrainingDecision.LoadDirection.PROGRESS));
        ResolveTrainingLoadRequest useCase = new ResolveTrainingLoadRequest(repository.steps, repository, repository.transactions,
                new FixedClock(), new SequenceIds());

        assertEquals(ResolveTrainingLoadRequest.Result.WRONG_DIRECTION,
                useCase.applyConcreteLoad(template.id,
                        load(ResistanceLoad.Mode.ASSISTED_BODYWEIGHT, 21_000)));
        assertEquals(ResolveTrainingLoadRequest.Result.APPLIED,
                useCase.applyConcreteLoad(template.id,
                        load(ResistanceLoad.Mode.ASSISTED_BODYWEIGHT, 18_000)));
        assertEquals(Long.valueOf(18_000), repository.findTemplate(template.id)
                .prescription.plannedLoad().milliUnits);
    }

    @Test public void unavailableHigherLoadFallsBackToOneAllowedSet() {
        InMemoryTrainingRepository repository = new InMemoryTrainingRepository();
        TaskStepTemplate template = template("press", ResistanceLoad.Mode.EXTERNAL,
                40_000, 2, 12);
        repository.insertTemplate(template);
        repository.insertTrainingLoadRequest(request(repository, template,
                TrainingDecision.LoadDirection.PROGRESS));
        ResolveTrainingLoadRequest useCase = new ResolveTrainingLoadRequest(repository.steps, repository, repository.transactions,
                new FixedClock(), new SequenceIds());

        assertEquals(ResolveTrainingLoadRequest.Result.DEFERRED, useCase.later(template.id));
        assertNotNull(repository.openTrainingLoadRequest(template.id));
        assertEquals(ResolveTrainingLoadRequest.Result.SETS_ADDED,
                useCase.noHigherLoad(template.id));

        assertNull(repository.openTrainingLoadRequest(template.id));
        StepAmount.SetsReps changed = (StepAmount.SetsReps) repository
                .findTemplate(template.id).prescription.amount;
        assertEquals(3, changed.sets);
        assertEquals(8, changed.repetitions);
    }

    private static TrainingLoadRequest request(InMemoryTrainingRepository repository,
                                               TaskStepTemplate template,
                                               TrainingDecision.LoadDirection direction) {
        return TrainingLoadRequest.open("request-" + template.id, template.id, "occ-step",
                direction, template.prescription.plannedLoad(), TODAY,
                repository.nextTrainingAuditOrder(), TrainingDecision.RULE_VERSION);
    }

    private static TaskStepTemplate template(String id, ResistanceLoad.Mode mode,
                                             long milli, int sets, int repetitions) {
        TrainingAssistantPolicy policy = TrainingAssistantPolicy.defaults(
                TrainingMuscleGroup.BACK);
        return new TaskStepTemplate(id, TaskId.of("task"), 0, id, 0, 0,
                new StepPrescription(StepAmount.setsReps(sets, repetitions),
                        RestTimerPolicy.inherit(), new TrainingPrescription(load(mode, milli), 2)),
                new TrainingAssistantProfile(policy, new TrainingAssistantState(
                        TrainingAssistantState.Status.ACTIVE, 5, 0, 0)), "",
                StepActivationKind.SCHEDULED);
    }

    private static ResistanceLoad load(ResistanceLoad.Mode mode, long milli) {
        return ResistanceLoad.numeric(mode, ResistanceLoad.Unit.KG, milli);
    }

    private static final class SequenceIds implements IdGenerator {
        private int value;
        @Override public String nextId() { return "resolved-" + ++value; }
    }

    private static final class FixedClock implements Clock {
        @Override public LocalDate today() { return TODAY; }
        @Override public LocalTime time() { return LocalTime.NOON; }
    }
}
