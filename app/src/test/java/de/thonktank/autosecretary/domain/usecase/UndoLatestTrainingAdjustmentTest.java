package de.thonktank.autosecretary.domain.usecase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.ResistanceLoad;
import de.thonktank.autosecretary.domain.model.RestTimerPolicy;
import de.thonktank.autosecretary.domain.model.StepActivationKind;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.StepPrescription;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.model.TrainingAdjustment;
import de.thonktank.autosecretary.domain.model.TrainingAssistantPolicy;
import de.thonktank.autosecretary.domain.model.TrainingAssistantProfile;
import de.thonktank.autosecretary.domain.model.TrainingAssistantState;
import de.thonktank.autosecretary.domain.model.TrainingDecision;
import de.thonktank.autosecretary.domain.model.TrainingMuscleGroup;
import de.thonktank.autosecretary.domain.model.TrainingPrescription;
import de.thonktank.autosecretary.testing.InMemoryExecutionRepository;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;

public final class UndoLatestTrainingAdjustmentTest {
    @Test public void currentLatestAdjustmentCanBeUndoneExactlyOnce() {
        InMemoryExecutionRepository repository = new InMemoryExecutionRepository();
        ResistanceLoad load = load(50_000);
        TaskStepTemplate template = new TaskStepTemplate("press", TaskId.of("gym"), 0,
                "Beinpresse", 0, 0,
                new StepPrescription(StepAmount.setsReps(3, 12), RestTimerPolicy.inherit(),
                        new TrainingPrescription(load, 2)),
                new TrainingAssistantProfile(TrainingAssistantPolicy.defaults(
                        TrainingMuscleGroup.QUADRICEPS), new TrainingAssistantState(
                        TrainingAssistantState.Status.ACTIVE, 5, 0, 0)), "",
                StepActivationKind.SCHEDULED);
        repository.insertTemplates(Collections.singletonList(template));
        repository.insertTrainingAdjustment(new TrainingAdjustment("adjustment", template.id,
                "occ-step", TrainingDecision.Reason.REPETITIONS_INCREASED,
                (StepAmount.SetsReps) StepAmount.setsReps(3, 11), load,
                (StepAmount.SetsReps) StepAmount.setsReps(3, 12), load,
                LocalDate.of(2026, 8, 31), TrainingAdjustment.State.APPLIED,
                repository.nextTrainingAuditOrder(), TrainingDecision.RULE_VERSION));
        UndoLatestTrainingAdjustment useCase = new UndoLatestTrainingAdjustment(repository,
                new FixedClock());

        assertTrue(useCase.execute(template.id));
        assertEquals(11, ((StepAmount.SetsReps) repository.findTemplate(template.id).amount)
                .repetitions);
        assertEquals(TrainingAssistantState.Status.CALIBRATING,
                repository.findTemplate(template.id).assistantProfile.state.status);
        assertEquals(TrainingAdjustment.State.UNDONE,
                repository.latestTrainingAdjustment(template.id).state);
        assertFalse(useCase.execute(template.id));
        assertFalse(new LoadTrainingContext(repository).execute(template.id).canUndo);
    }

    private static ResistanceLoad load(long milliUnits) {
        return ResistanceLoad.numeric(ResistanceLoad.Mode.EXTERNAL,
                ResistanceLoad.Unit.KG, milliUnits);
    }

    private static final class FixedClock implements Clock {
        @Override public LocalDate today() { return LocalDate.of(2026, 8, 31); }
        @Override public LocalTime time() { return LocalTime.NOON; }
    }
}
