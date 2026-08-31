package de.thonktank.autosecretary.domain.usecase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
import de.thonktank.autosecretary.domain.model.TrainingContext;
import de.thonktank.autosecretary.domain.model.TrainingDecision;
import de.thonktank.autosecretary.domain.model.TrainingLoadRequest;
import de.thonktank.autosecretary.domain.model.TrainingMuscleGroup;
import de.thonktank.autosecretary.domain.model.TrainingPrescription;
import de.thonktank.autosecretary.testing.InMemoryExecutionRepository;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Collections;

public final class LoadTrainingContextTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 31);

    @Test public void mergesBothEventKindsNewestFirstAndLimitsHistoryToTen() {
        InMemoryExecutionRepository repository = repository();
        for (int index = 1; index <= 9; index++)
            repository.insertTrainingAdjustment(adjustment(repository, index,
                    index % 3 == 0 ? TrainingAdjustment.State.UNDONE
                            : TrainingAdjustment.State.APPLIED));
        TrainingLoadRequest request = TrainingLoadRequest.open("request", "press", "occ-step",
                TrainingDecision.LoadDirection.PROGRESS, load(50_000), TODAY,
                repository.nextTrainingAuditOrder(), TrainingDecision.RULE_VERSION);
        repository.insertTrainingLoadRequest(request);
        repository.updateTrainingLoadRequest(request.resolve(
                TrainingLoadRequest.Resolution.NO_HIGHER_LOAD, TODAY));
        repository.insertTrainingAdjustment(adjustment(repository, 10,
                TrainingAdjustment.State.APPLIED));

        TrainingContext context = new LoadTrainingContext(repository).execute("press");

        assertEquals(10, context.history.size());
        assertEquals(11L, context.history.get(0).auditOrder);
        assertEquals(2L, context.history.get(9).auditOrder);
        assertTrue(context.canUndo);
        assertTrue(context.history.stream().anyMatch(value ->
                value.adjustmentState == TrainingAdjustment.State.UNDONE));
        assertTrue(context.history.stream().anyMatch(value ->
                value.kind == de.thonktank.autosecretary.domain.model.TrainingHistoryEntry.Kind.LOAD_REQUEST));
    }

    @Test public void newerOpenQuestionDisablesUndoEvenWhenAdjustmentAfterStateIsCurrent() {
        InMemoryExecutionRepository repository = repository();
        repository.insertTrainingAdjustment(adjustment(repository, 1,
                TrainingAdjustment.State.APPLIED));
        assertTrue(new LoadTrainingContext(repository).execute("press").canUndo);
        repository.insertTrainingLoadRequest(TrainingLoadRequest.open("request", "press",
                "occ-step", TrainingDecision.LoadDirection.PROGRESS, load(50_000), TODAY,
                repository.nextTrainingAuditOrder(), TrainingDecision.RULE_VERSION));

        assertFalse(new LoadTrainingContext(repository).execute("press").canUndo);
    }

    private static InMemoryExecutionRepository repository() {
        InMemoryExecutionRepository repository = new InMemoryExecutionRepository();
        repository.insertTemplates(Collections.singletonList(template()));
        return repository;
    }

    private static TaskStepTemplate template() {
        return new TaskStepTemplate("press", TaskId.of("task"), 0, "Beinpresse", 0, 0,
                new StepPrescription(StepAmount.setsReps(3, 12), RestTimerPolicy.inherit(),
                        new TrainingPrescription(load(50_000), 2)),
                new TrainingAssistantProfile(TrainingAssistantPolicy.defaults(
                        TrainingMuscleGroup.QUADRICEPS), new TrainingAssistantState(
                        TrainingAssistantState.Status.ACTIVE, 5, 0, 0)), "",
                StepActivationKind.SCHEDULED);
    }

    private static TrainingAdjustment adjustment(InMemoryExecutionRepository repository,
                                                 int index,
                                                 TrainingAdjustment.State state) {
        return new TrainingAdjustment("adjustment-" + index, "press", "occ-step",
                TrainingDecision.Reason.LOAD_APPLIED,
                (StepAmount.SetsReps) StepAmount.setsReps(3, 11), load(49_000),
                (StepAmount.SetsReps) StepAmount.setsReps(3, 12), load(50_000), TODAY, state,
                repository.nextTrainingAuditOrder(), TrainingDecision.RULE_VERSION);
    }

    private static ResistanceLoad load(long milliUnits) {
        return ResistanceLoad.numeric(ResistanceLoad.Mode.EXTERNAL,
                ResistanceLoad.Unit.KG, milliUnits);
    }
}
