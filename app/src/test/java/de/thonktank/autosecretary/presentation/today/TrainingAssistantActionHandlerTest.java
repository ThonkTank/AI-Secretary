package de.thonktank.autosecretary.presentation.today;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.R;
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
import de.thonktank.autosecretary.domain.usecase.IdGenerator;
import de.thonktank.autosecretary.domain.usecase.LoadTrainingContext;
import de.thonktank.autosecretary.domain.usecase.ResolveTrainingLoadRequest;
import de.thonktank.autosecretary.domain.usecase.TrainingUseCases;
import de.thonktank.autosecretary.domain.usecase.UndoLatestTrainingAdjustment;
import de.thonktank.autosecretary.testing.InMemoryTrainingRepository;

public final class TrainingAssistantActionHandlerTest {
    @Test public void applyLoadParsesCommaToExactMilliUnitsAndMapsCompletion() {
        Fixture fixture = new Fixture();

        TrainingAssistantActionHandler.Result result = fixture.handler.handle(
                new TrainingAssistantUiAction.ApplyLoad(fixture.template.id, "52,5",
                        ResistanceLoad.Mode.EXTERNAL, ResistanceLoad.Unit.KG));

        assertTrue(result instanceof TrainingAssistantActionHandler.Completed);
        assertEquals(Long.valueOf(52_500), fixture.repository.findTemplate(fixture.template.id)
                .prescription.plannedLoad().milliUnits);
        assertNull(fixture.repository.openTrainingLoadRequest(fixture.template.id));
    }

    @Test public void applyLoadRejectsNonExactOrNonPositiveRawValuesBeforeUseCase() {
        Fixture fixture = new Fixture();

        TrainingAssistantActionHandler.Result fractional = fixture.handler.handle(
                new TrainingAssistantUiAction.ApplyLoad(fixture.template.id, "52.1234",
                        ResistanceLoad.Mode.EXTERNAL, ResistanceLoad.Unit.KG));
        TrainingAssistantActionHandler.Result nonPositive = fixture.handler.handle(
                new TrainingAssistantUiAction.ApplyLoad(fixture.template.id, "0",
                        ResistanceLoad.Mode.EXTERNAL, ResistanceLoad.Unit.KG));

        assertRejected(fractional, R.string.training_load_invalid);
        assertRejected(nonPositive, R.string.training_load_invalid);
        assertNotNull(fixture.repository.openTrainingLoadRequest(fixture.template.id));
        assertEquals(Long.valueOf(50_000), fixture.repository.findTemplate(fixture.template.id)
                .prescription.plannedLoad().milliUnits);
    }

    @Test public void laterMapsToFeedbackAndKeepsQuestionOpen() {
        Fixture fixture = new Fixture();

        TrainingAssistantActionHandler.Result result = fixture.handler.handle(
                new TrainingAssistantUiAction.Later(fixture.template.id));

        assertTrue(result instanceof TrainingAssistantActionHandler.Feedback);
        assertEquals(R.string.training_request_deferred,
                ((TrainingAssistantActionHandler.Feedback) result).message);
        assertNotNull(fixture.repository.openTrainingLoadRequest(fixture.template.id));
    }

    @Test public void noHigherLoadCompletesTheTodayQuestion() {
        Fixture fixture = new Fixture();

        TrainingAssistantActionHandler.Result noHigher = fixture.handler.handle(
                new TrainingAssistantUiAction.NoHigherLoad(fixture.template.id));

        assertTrue(noHigher instanceof TrainingAssistantActionHandler.Completed);
        assertNull(fixture.repository.openTrainingLoadRequest(fixture.template.id));
        assertEquals(3, ((StepAmount.SetsReps) fixture.repository
                .findTemplate(fixture.template.id).prescription.amount).sets);

    }

    private static void assertRejected(TrainingAssistantActionHandler.Result result,
                                       int message) {
        assertTrue(result instanceof TrainingAssistantActionHandler.Rejected);
        assertEquals(message, ((TrainingAssistantActionHandler.Rejected) result).message);
    }

    private static final class Fixture {
        final InMemoryTrainingRepository repository = new InMemoryTrainingRepository();
        final TaskStepTemplate template = template();
        final TrainingAssistantActionHandler handler;

        Fixture() {
            repository.insertTemplate(template);
            repository.insertTrainingLoadRequest(TrainingLoadRequest.open(
                    "request-press", template.id, "occ-step",
                    TrainingDecision.LoadDirection.PROGRESS,
                    template.prescription.plannedLoad(), FixedClock.TODAY,
                    repository.nextTrainingAuditOrder(), TrainingDecision.RULE_VERSION));
            TrainingUseCases training = new TrainingUseCases(
                    new UndoLatestTrainingAdjustment(repository.steps, repository,
                            repository.transactions, new FixedClock()),
                    new ResolveTrainingLoadRequest(repository.steps, repository,
                            repository.transactions, new FixedClock(), new SequenceIds()),
                    new LoadTrainingContext(repository.steps, repository,
                            repository.transactions));
            handler = new TrainingAssistantActionHandler(training);
        }

        private static TaskStepTemplate template() {
            ResistanceLoad load = ResistanceLoad.numeric(
                    ResistanceLoad.Mode.EXTERNAL, ResistanceLoad.Unit.KG, 50_000);
            return new TaskStepTemplate("press", TaskId.of("gym"), 0, "Beinpresse", 0, 0,
                    new StepPrescription(StepAmount.setsReps(2, 12), RestTimerPolicy.inherit(),
                            new TrainingPrescription(load, 2)),
                    new TrainingAssistantProfile(TrainingAssistantPolicy.defaults(
                            TrainingMuscleGroup.QUADRICEPS), new TrainingAssistantState(
                            TrainingAssistantState.Status.ACTIVE, 5, 0, 0)), "",
                    StepActivationKind.SCHEDULED);
        }
    }

    private static final class FixedClock implements Clock {
        static final LocalDate TODAY = LocalDate.of(2026, 9, 1);
        @Override public LocalDate today() { return TODAY; }
        @Override public LocalTime time() { return LocalTime.NOON; }
    }

    private static final class SequenceIds implements IdGenerator {
        private int value;
        @Override public String nextId() { return "handler-" + ++value; }
    }
}
