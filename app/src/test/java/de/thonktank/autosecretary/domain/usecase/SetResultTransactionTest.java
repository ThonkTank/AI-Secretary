package de.thonktank.autosecretary.domain.usecase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.ResistanceLoad;
import de.thonktank.autosecretary.domain.model.RestTimerPolicy;
import de.thonktank.autosecretary.domain.model.SetResult;
import de.thonktank.autosecretary.domain.model.StepActivationKind;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.StepPrescription;
import de.thonktank.autosecretary.domain.model.TaskBoundKind;
import de.thonktank.autosecretary.domain.model.TaskDefinition;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskStepDefinition;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.model.TimeOfDay;
import de.thonktank.autosecretary.domain.model.TrainingAssistantPolicy;
import de.thonktank.autosecretary.domain.model.TrainingAssistantProfile;
import de.thonktank.autosecretary.domain.model.TrainingAssistantState;
import de.thonktank.autosecretary.domain.model.TrainingMuscleGroup;
import de.thonktank.autosecretary.domain.model.TrainingObservation;
import de.thonktank.autosecretary.domain.model.TrainingPrescription;
import de.thonktank.autosecretary.domain.repository.ComboPolicySource;
import de.thonktank.autosecretary.testing.InMemoryExecutionRepository;

public final class SetResultTransactionTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 31);

    @Test public void adaptationFailureRollsBackResultCompletionRewardAndTemplate() {
        InMemoryExecutionRepository repository = new InMemoryExecutionRepository();
        FixedClock clock = new FixedClock();
        SequenceIds ids = new SequenceIds();
        ResistanceLoad load = ResistanceLoad.numeric(ResistanceLoad.Mode.EXTERNAL,
                ResistanceLoad.Unit.KG, 20_000);
        TrainingAssistantPolicy policy = new TrainingAssistantPolicy(2, 3, 8, 12, 10,
                TrainingMuscleGroup.BACK, Collections.emptySet());
        TaskStepDefinition stepDefinition = new TaskStepDefinition(null, 0, "Rudern", 0, 0,
                new StepPrescription(StepAmount.setsReps(2, 10), RestTimerPolicy.inherit(),
                        new TrainingPrescription(load, 2)), policy, "",
                StepActivationKind.SCHEDULED);
        TaskDefinition task = new TaskDefinition("Training", 30, TaskSlot.MORNING,
                Recurrence.DAILY, 1, 0, TimeOfDay.MORNING.bit, TaskBoundKind.FOREVER,
                null, null, null, null, "", Collections.singletonList(stepDefinition));
        new CreateTask(repository, repository, clock, ids).execute(task);
        new MaterializeDueOccurrences(repository, clock, ids).execute();
        TaskStepTemplate template = repository.templates(repository.allTasks().get(0).id).get(0);
        TrainingAssistantState active = new TrainingAssistantState(
                TrainingAssistantState.Status.ACTIVE, 4, 1, 0);
        repository.updateTrainingTemplate(template.withTraining(template.prescription,
                new TrainingAssistantProfile(template.assistantProfile.policy, active)));
        Occurrence occurrence = repository.openOccurrences().get(0);
        OccurrenceStep step = repository.occurrenceSteps(occurrence.id).get(0);
        RecordSetResult record = new RecordSetResult(repository, clock, ids,
                ComboPolicySource.defaults());
        SetResult result = new SetResult(10, TrainingObservation.user(load, 2));
        record.execute(step.id, result);
        int xpBeforeFailure = repository.xp();
        repository.failNextTrainingAdjustmentInsert();

        assertThrows(IllegalStateException.class, () -> record.execute(step.id, result));

        OccurrenceStep restored = repository.findOccurrenceStep(step.id);
        assertFalse(restored.done);
        assertEquals(Collections.singletonList(result), restored.repetitionProgress.results);
        assertEquals(xpBeforeFailure, repository.xp());
        TaskStepTemplate restoredTemplate = repository.findTemplate(template.id);
        assertEquals(template.prescription, restoredTemplate.prescription);
        assertEquals(active, restoredTemplate.assistantProfile.state);
        assertNull(repository.latestTrainingAdjustment(template.id));
    }

    @Test public void topOfRangeCreatesOneDurableQuestionAndCorrectionCancelsIt() {
        InMemoryExecutionRepository repository = new InMemoryExecutionRepository();
        FixedClock clock = new FixedClock();
        SequenceIds ids = new SequenceIds();
        ResistanceLoad load = ResistanceLoad.numeric(ResistanceLoad.Mode.EXTERNAL,
                ResistanceLoad.Unit.KG, 50_000);
        TrainingAssistantPolicy policy = new TrainingAssistantPolicy(2, 3, 8, 12, 10,
                TrainingMuscleGroup.BACK, Collections.emptySet());
        TaskStepDefinition stepDefinition = new TaskStepDefinition(null, 0, "Rudern", 0, 0,
                new StepPrescription(StepAmount.setsReps(2, 12), RestTimerPolicy.inherit(),
                        new TrainingPrescription(load, 2)), policy, "",
                StepActivationKind.SCHEDULED);
        TaskDefinition task = new TaskDefinition("Training", 30, TaskSlot.MORNING,
                Recurrence.DAILY, 1, 0, TimeOfDay.MORNING.bit, TaskBoundKind.FOREVER,
                null, null, null, null, "", Collections.singletonList(stepDefinition));
        new CreateTask(repository, repository, clock, ids).execute(task);
        new MaterializeDueOccurrences(repository, clock, ids).execute();
        TaskStepTemplate template = repository.templates(repository.allTasks().get(0).id).get(0);
        repository.updateTrainingTemplate(template.withTraining(template.prescription,
                new TrainingAssistantProfile(template.assistantProfile.policy,
                        new TrainingAssistantState(TrainingAssistantState.Status.ACTIVE,
                                4, 1, 0))));
        OccurrenceStep step = repository.occurrenceSteps(
                repository.openOccurrences().get(0).id).get(0);
        RecordSetResult record = new RecordSetResult(repository, clock, ids,
                ComboPolicySource.defaults());
        SetResult value = new SetResult(12, TrainingObservation.user(load, 2));
        record.execute(step.id, value);
        record.execute(step.id, value);

        assertNotNull(repository.openTrainingLoadRequest(template.id));
        new TrainingAdaptationService(repository, repository, clock, ids).evaluate(step.id);
        assertEquals(1, repository.recentTrainingLoadRequests(template.id, 10).size());

        CorrectSetResult correct = new CorrectSetResult(repository, clock,
                ComboPolicySource.defaults());
        correct.execute(step.id, 1,
                new SetResult(11, TrainingObservation.user(load, 2)));
        assertNull(repository.openTrainingLoadRequest(template.id));
        assertEquals(TrainingAssistantState.Status.CALIBRATING,
                repository.findTemplate(template.id).assistantProfile.state.status);
    }

    private static final class SequenceIds implements IdGenerator {
        private int value;
        @Override public String nextId() { return "set-result-" + ++value; }
    }

    private static final class FixedClock implements Clock {
        @Override public LocalDate today() { return TODAY; }
        @Override public LocalTime time() { return LocalTime.of(9, 0); }
    }
}
