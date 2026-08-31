package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Bundle;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import de.thonktank.autosecretary.data.local.RoomTaskRepository;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.ResistanceLoad;
import de.thonktank.autosecretary.domain.model.SetResult;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.StepAmountKind;
import de.thonktank.autosecretary.domain.model.StepActivationKind;
import de.thonktank.autosecretary.domain.model.StepPrescription;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskBoundKind;
import de.thonktank.autosecretary.domain.model.TaskDefinition;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskOrdering;
import de.thonktank.autosecretary.domain.model.TaskScheduleEntry;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskStepDefinition;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.model.TimeOfDay;
import de.thonktank.autosecretary.domain.model.TrainingObservation;
import de.thonktank.autosecretary.data.local.TaskStore;
import de.thonktank.autosecretary.domain.repository.ComboPolicySource;
import de.thonktank.autosecretary.domain.usecase.RecordRepetitionResult;
import de.thonktank.autosecretary.domain.usecase.CompleteOccurrence;
import de.thonktank.autosecretary.domain.usecase.CompleteRemainingSteps;
import de.thonktank.autosecretary.domain.usecase.CreateTask;
import de.thonktank.autosecretary.domain.usecase.CorrectRepetitionResult;
import de.thonktank.autosecretary.domain.usecase.CorrectSetResult;
import de.thonktank.autosecretary.domain.usecase.IdGenerator;
import de.thonktank.autosecretary.domain.usecase.LoadDashboard;
import de.thonktank.autosecretary.domain.usecase.MaterializeDueOccurrences;
import de.thonktank.autosecretary.domain.usecase.RecordSetResult;
import de.thonktank.autosecretary.domain.usecase.ToggleStep;
import de.thonktank.autosecretary.domain.usecase.UpdateTask;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class TaskEditorFeatureRobolectricTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 17);
    private AppDatabase database;
    private TaskStore repository;
    private SequenceIds ids;
    private MutableClock clock;

    @Before public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries().build();
        repository = new RoomTaskRepository(database);
        ids = new SequenceIds();
        clock = new MutableClock(TODAY);
    }

    @After public void tearDown() { database.close(); }

    @Test public void definitionRejectsInvalidTypedAmounts() {
        assertThrows(IllegalArgumentException.class, () -> definition("", Recurrence.ONCE,
                0, TaskBoundKind.FOREVER, null, Collections.emptyList()));
        assertThrows(IllegalArgumentException.class, () -> definition("Routine",
                Recurrence.WEEKDAYS, 0, TaskBoundKind.FOREVER, null, Collections.emptyList()));
        TaskStepDefinition none = de.thonktank.autosecretary.testing.StepTestFixtures.definition(null, 0, "Schritt", 0,
                StepAmount.none(), "Notiz");
        assertEquals(StepAmount.none(), none.amount);
        assertThrows(IllegalArgumentException.class, () -> de.thonktank.autosecretary.testing.StepTestFixtures.definition(
                null, 0, "Schritt", 0, StepAmount.setsReps(0, 12), ""));
    }

    @Test public void multipleTimesMaterializeDistinctIdempotentOccurrencesAndRespectCount() {
        TaskDefinition definition = new TaskDefinition("Katze", 15, TaskSlot.MORNING,
                Recurrence.DAILY, 1, 0, TimeOfDay.MORNING.bit | TimeOfDay.EVENING.bit,
                TaskBoundKind.N_TIMES, null, null, 3, null, "", Collections.emptyList());
        new CreateTask(repository, repository, repository, clock, ids).execute(definition);
        MaterializeDueOccurrences materialize = new MaterializeDueOccurrences(repository, repository, repository, repository, repository, clock, ids);
        materialize.execute(); materialize.execute();

        List<Occurrence> firstRound = repository.openOccurrences();
        assertEquals(2, firstRound.size());
        assertEquals(TaskSlot.MORNING, firstRound.get(0).slot);
        assertEquals(TaskSlot.EVENING, firstRound.get(1).slot);
        assertEquals(Integer.valueOf(1), repository.allTasks().get(0).remainingCount);
        assertEquals(2, new LoadDashboard(repository, repository).execute(TODAY).tasks.size());

        Occurrence morning = repository.findOccurrence(repository.allTasks().get(0).id,
                TODAY, TaskSlot.MORNING);
        CompleteOccurrence complete = new CompleteOccurrence(repository, repository, repository, repository, clock);
        complete.execute(morning.id);
        materialize.execute();
        assertEquals(morning.id, repository.findOccurrence(repository.allTasks().get(0).id,
                TODAY, TaskSlot.MORNING).id);
        assertEquals(1, database.tasks().occurrencesByState("OPEN").size());
        assertEquals(1, database.tasks().completedOccurrences("COMPLETED", TODAY.toString()).size());
        complete.execute(firstRound.get(1).id);
        assertEquals(2, new LoadDashboard(repository, repository).execute(TODAY).tasks.size());
        assertEquals(25, repository.xp());
        assertEquals(TODAY.plusDays(1), repository.allTasks().get(0).nextDueOn);
    }

    @Test public void newEditorChoosesAContextualDaySlot() {
        assertEquals(TaskSlot.MORNING, TaskEditorViewModel.defaultSlot(LocalTime.of(7, 30)));
        assertEquals(TaskSlot.MIDDAY, TaskEditorViewModel.defaultSlot(LocalTime.of(12, 0)));
        assertEquals(TaskSlot.EVENING, TaskEditorViewModel.defaultSlot(LocalTime.of(18, 0)));
        assertEquals(TaskSlot.LATER, TaskEditorViewModel.defaultSlot(LocalTime.of(22, 0)));
    }

    @Test public void templateUpdateKeepsIdentityAndDoesNotMutateOpenSnapshot() {
        TaskStepDefinition original = de.thonktank.autosecretary.testing.StepTestFixtures.definition(null, 0, "Alt", 0,
                StepAmount.setsReps(3, 12), "23 kg");
        TaskDefinition definition = definition("Training", Recurrence.DAILY, 0,
                TaskBoundKind.FOREVER, null, Collections.singletonList(original));
        CreateTask create = new CreateTask(repository, repository, repository, clock, ids);
        create.execute(definition);
        Task task = repository.allTasks().get(0);
        String stableId = repository.templates(task.id).get(0).id;
        new MaterializeDueOccurrences(repository, repository, repository, repository, repository, clock, ids).execute();
        Occurrence occurrence = repository.openOccurrences().get(0);

        TaskStepDefinition edited = de.thonktank.autosecretary.testing.StepTestFixtures.definition(stableId, 0, "Neu", 0,
                StepAmount.setsReps(4, 10), "25 kg");
        new UpdateTask(repository, repository, repository, repository, repository, ids, clock).execute(task.id,
                definition("Training neu", Recurrence.DAILY, 0, TaskBoundKind.FOREVER,
                        null, Collections.singletonList(edited)));

        TaskStepTemplate template = repository.templates(task.id).get(0);
        OccurrenceStep snapshot = repository.occurrenceSteps(occurrence.id).get(0);
        assertEquals(stableId, template.id);
        assertEquals(stableId, snapshot.sourceTemplateId);
        assertEquals("Neu", template.text);
        assertEquals("Alt", snapshot.text);
        assertEquals(3, ((StepAmount.SetsReps) snapshot.amount).sets);
        assertEquals("23 kg", snapshot.note);
    }

    @Test public void stepIntervalsStayAnchoredToTheFirstDueCalendarDay() {
        TaskStepDefinition always = de.thonktank.autosecretary.testing.StepTestFixtures.definition(null, 0, "Immer", 0,
                StepAmount.none(), "");
        TaskStepDefinition everyOtherDay = de.thonktank.autosecretary.testing.StepTestFixtures.definition(null, 1, "Intervall", 0, 2,
                StepAmount.none(), "");
        new CreateTask(repository, repository, repository, clock, ids).execute(definition(
                "Pflege", Recurrence.DAILY, 0, TaskBoundKind.FOREVER, null,
                Arrays.asList(always, everyOtherDay)));
        MaterializeDueOccurrences materialize = new MaterializeDueOccurrences(repository, repository, repository, repository, repository, clock, ids);

        materialize.execute();
        assertEquals(2, repository.occurrenceSteps(repository.openOccurrences().get(0).id).size());
        closeOpenOccurrence();

        clock.date = TODAY.plusDays(1);
        materialize.execute();
        assertEquals(1, repository.occurrenceSteps(repository.openOccurrences().get(0).id).size());
        assertEquals("Immer", repository.occurrenceSteps(
                repository.openOccurrences().get(0).id).get(0).text);
        closeOpenOccurrence();

        clock.date = TODAY.plusDays(2);
        materialize.execute();
        assertEquals(2, repository.occurrenceSteps(repository.openOccurrences().get(0).id).size());
    }

    @Test public void cadenceAnchorSurvivesHistoryDeletionReloadAndDefinitionEdit() {
        TaskStepDefinition always = de.thonktank.autosecretary.testing.StepTestFixtures.definition(null, 0, "Immer", 0,
                StepAmount.none(), "");
        TaskStepDefinition interval = de.thonktank.autosecretary.testing.StepTestFixtures.definition(null, 1, "Intervall", 0, 2,
                StepAmount.none(), "");
        TaskDefinition original = definition("Pflege", Recurrence.DAILY, 0,
                TaskBoundKind.FOREVER, null, Arrays.asList(always, interval));
        new CreateTask(repository, repository, repository, clock, ids).execute(original);
        Task task = repository.allTasks().get(0);
        assertEquals(TODAY, task.cadenceAnchorOn);

        new MaterializeDueOccurrences(repository, repository, repository, repository, repository, clock, ids).execute();
        closeOpenOccurrence();
        database.getOpenHelper().getWritableDatabase().execSQL(
                "DELETE FROM occurrences WHERE taskId='" + task.id.value + "'");

        List<TaskStepTemplate> templates = repository.templates(task.id);
        TaskDefinition edited = definition("Pflege bearbeitet", Recurrence.DAILY, 0,
                TaskBoundKind.FOREVER, null, Arrays.asList(
                de.thonktank.autosecretary.testing.StepTestFixtures.definition(templates.get(0).id, 0, "Immer", 0,
                        StepAmount.none(), ""),
                de.thonktank.autosecretary.testing.StepTestFixtures.definition(templates.get(1).id, 1, "Intervall", 0, 2,
                        StepAmount.none(), "")));
        new UpdateTask(repository, repository, repository, repository, repository, ids, clock).execute(task.id, edited);
        repository = new RoomTaskRepository(database);
        assertEquals(TODAY, repository.findTask(task.id).cadenceAnchorOn);

        clock.date = TODAY.plusDays(1);
        new MaterializeDueOccurrences(repository, repository, repository, repository, repository, clock, ids).execute();
        assertEquals(1, repository.occurrenceSteps(repository.openOccurrences().get(0).id).size());
        closeOpenOccurrence();
        clock.date = TODAY.plusDays(2);
        new MaterializeDueOccurrences(repository, repository, repository, repository, repository, clock, ids).execute();
        assertEquals(2, repository.occurrenceSteps(repository.openOccurrences().get(0).id).size());
    }

    @Test public void activeStepIntervalWithoutPersistedAnchorFailsFast() {
        TaskId id = TaskId.of("missing-anchor");
        repository.insertTask(Task.restore(id, "Beschädigt", Recurrence.DAILY, 1, 0,
                false, "", false, false, TODAY, null, null, null, 1_024L, false,
                null, TaskBoundKind.FOREVER, null, null, null, null, ""));
        repository.insertTemplates(Collections.singletonList(de.thonktank.autosecretary.testing.StepTestFixtures.template(
                "interval", id, 0, "Intervall", 0, 2, StepAmount.none(), "")));
        repository.putScheduleEntries(Collections.singletonList(new TaskScheduleEntry(
                "schedule", id, TaskSlot.MORNING, 1_024L)));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new MaterializeDueOccurrences(repository, repository, repository, repository, repository, clock, ids).execute());
        assertTrue(failure.getMessage().contains("missing-anchor"));
    }

    @Test public void changingIntervalLengthRecalculatesCursorButPreservesAnchor() {
        TaskDefinition everyThreeDays = new TaskDefinition("Intervall", null, TaskSlot.MORNING,
                Recurrence.INTERVAL, 3, 0, TimeOfDay.MORNING.bit, TaskBoundKind.FOREVER,
                null, null, null, null, "", Collections.emptyList());
        new CreateTask(repository, repository, repository, clock, ids).execute(everyThreeDays);
        Task task = repository.allTasks().get(0);
        new MaterializeDueOccurrences(repository, repository, repository, repository, repository, clock, ids).execute();
        assertEquals(TODAY.plusDays(3), repository.findTask(task.id).nextDueOn);

        clock.date = TODAY.plusDays(1);
        TaskDefinition everyFiveDays = new TaskDefinition("Intervall", null, TaskSlot.MORNING,
                Recurrence.INTERVAL, 5, 0, TimeOfDay.MORNING.bit, TaskBoundKind.FOREVER,
                null, null, null, null, "", Collections.emptyList());
        new UpdateTask(repository, repository, repository, repository, repository, ids, clock).execute(task.id, everyFiveDays);

        Task updated = repository.findTask(task.id);
        assertEquals(TODAY.plusDays(1), updated.nextDueOn);
        assertEquals(TODAY, updated.cadenceAnchorOn);
    }

    private void closeOpenOccurrence() {
        Occurrence occurrence = repository.openOccurrences().get(0);
        new CompleteRemainingSteps(repository, repository, repository, repository, clock).execute(occurrence.id);
        new CompleteOccurrence(repository, repository, repository, repository, clock).execute(occurrence.id);
    }

    @Test public void repetitionResultsSurviveReloadAndCorrectionsAdjustRewards() {
        int monday = 1;
        TaskStepDefinition mondayStep = de.thonktank.autosecretary.testing.StepTestFixtures.definition(null, 0, "Beinpresse", monday,
                StepAmount.setsReps(3, 12), "23 kg, Sitz 5");
        new CreateTask(repository, repository, repository, clock, ids).execute(new TaskDefinition(
                "Training", 45, TaskSlot.MORNING, Recurrence.DAILY, 1, 0,
                TimeOfDay.MORNING.bit, TaskBoundKind.FOREVER, null, null, null, null,
                "", Collections.singletonList(mondayStep)));
        new MaterializeDueOccurrences(repository, repository, repository, repository, repository, clock, ids).execute();
        Occurrence occurrence = repository.openOccurrences().get(0);
        OccurrenceStep step = repository.occurrenceSteps(occurrence.id).get(0);
        RecordRepetitionResult record = new RecordRepetitionResult(repository, repository, repository, repository, clock);
        record.execute(step.id, 10); record.execute(step.id, 11);

        OccurrenceStep restored = new RoomTaskRepository(database).findOccurrenceStep(step.id);
        assertEquals(Arrays.asList(10, 11), restored.repetitionProgress.actualRepetitions);
        assertTrue(restored.repetitionProgress.results.stream()
                .allMatch(value -> value.training == null));
        assertFalse(restored.done);
        CorrectRepetitionResult correct = new CorrectRepetitionResult(repository, repository, repository, repository);
        assertEquals(-2, correct.execute(step.id, 0, 0).xp);
        assertEquals(Arrays.asList(0, 11),
                repository.findOccurrenceStep(step.id).repetitionProgress.actualRepetitions);
        assertThrows(IllegalArgumentException.class,
                () -> correct.execute(step.id, 0, 1_000));
        assertEquals(3, record.execute(step.id, 12).xp);
        assertTrue(repository.findOccurrenceStep(step.id).done);
        assertEquals(2, repository.combo(step.comboOwnerId).points);

        assertEquals(2, correct.execute(step.id, 0, 9).xp);
        OccurrenceStep editedDone = repository.findOccurrenceStep(step.id);
        assertTrue(editedDone.done);
        assertEquals(Arrays.asList(9, 11, 12),
                editedDone.repetitionProgress.actualRepetitions);
        assertEquals(9, vesselXp(occurrence.id));

        assertTrue(repository.findOccurrenceStep(step.id).done);
        assertEquals(2, repository.combo(step.comboOwnerId).points);
    }

    @Test public void detailedSetResultsReloadAndCorrectAsOneValue() {
        TaskStepDefinition exercise = de.thonktank.autosecretary.testing.StepTestFixtures.definition(
                null, 0, "Rudern", 0, StepAmount.setsReps(2, 12), "");
        new CreateTask(repository, repository, repository, clock, ids).execute(definition(
                "Training", Recurrence.DAILY, 0, TaskBoundKind.FOREVER, null,
                Collections.singletonList(exercise)));
        new MaterializeDueOccurrences(repository, repository, repository, repository, repository, clock, ids).execute();
        OccurrenceStep step = repository.occurrenceSteps(
                repository.openOccurrences().get(0).id).get(0);
        ResistanceLoad load = ResistanceLoad.numeric(ResistanceLoad.Mode.EXTERNAL,
                ResistanceLoad.Unit.KG, 23_000);
        RecordSetResult record = new RecordSetResult(repository, repository, repository, repository, repository, clock, ids,
                ComboPolicySource.defaults());
        SetResult first = new SetResult(12, TrainingObservation.user(load, 2));
        SetResult second = new SetResult(11, new TrainingObservation(load, 1,
                TrainingObservation.Safety.PAIN_OR_TECHNIQUE,
                TrainingObservation.Origin.USER));

        record.execute(step.id, first);
        record.execute(step.id, second);
        OccurrenceStep restored = new RoomTaskRepository(database).findOccurrenceStep(step.id);

        assertEquals(Arrays.asList(first, second), restored.repetitionProgress.results);
        assertEquals(Arrays.asList(12, 11), restored.repetitionProgress.actualRepetitions);
        SetResult corrected = new SetResult(10, TrainingObservation.user(load, 3));
        new CorrectSetResult(repository, repository, repository, repository, repository, clock, ComboPolicySource.defaults())
                .execute(step.id, 1, corrected);
        assertEquals(Arrays.asList(first, corrected), new RoomTaskRepository(database)
                .findOccurrenceStep(step.id).repetitionProgress.results);
    }

    private int vesselXp(String occurrenceId) {
        return repository.rewardBookings(occurrenceId).stream()
                .filter(value -> value.target
                        == de.thonktank.autosecretary.domain.model.RewardBooking.Target.VESSEL)
                .mapToInt(value -> value.xpDelta).sum();
    }

    @Test public void completeRemainingFillsMissingPlansAndNextStartsEmpty() {
        TaskStepDefinition exercise = de.thonktank.autosecretary.testing.StepTestFixtures.definition(null, 0, "Kniebeugen", 0,
                StepAmount.setsReps(3, 15), "");
        new CreateTask(repository, repository, repository, clock, ids).execute(definition(
                "Training", Recurrence.DAILY, 0, TaskBoundKind.FOREVER, null,
                Collections.singletonList(exercise)));
        new MaterializeDueOccurrences(repository, repository, repository, repository, repository, clock, ids).execute();
        OccurrenceStep step = repository.occurrenceSteps(repository.openOccurrences().get(0).id).get(0);
        new RecordRepetitionResult(repository, repository, repository, repository).execute(step.id, 13);
        new CompleteRemainingSteps(repository, repository, repository, repository, clock).execute(step.occurrenceId);
        assertTrue(repository.findOccurrenceStep(step.id).done);
        assertEquals(Arrays.asList(13, 15, 15), repository.findOccurrenceStep(step.id)
                .repetitionProgress.actualRepetitions);
        assertEquals(de.thonktank.autosecretary.domain.model.RepetitionProgress.Completion
                        .RESULTS_COMPLETE,
                repository.findOccurrenceStep(step.id).repetitionProgress.completion);

        new CompleteOccurrence(repository, repository, repository, repository, clock).execute(step.occurrenceId);
        clock.date = TODAY.plusDays(1);
        new MaterializeDueOccurrences(repository, repository, repository, repository, repository, clock, ids).execute();
        OccurrenceStep next = repository.occurrenceSteps(repository.openOccurrences().get(0).id).get(0);
        assertTrue(next.repetitionProgress.actualRepetitions.isEmpty());
        assertFalse(next.done);
    }

    @Test public void explicitZeroPersistsAndCompletesWithoutRewardOrCombo() {
        TaskStepDefinition exercise = de.thonktank.autosecretary.testing.StepTestFixtures.definition(null, 0, "Liegestütze", 0,
                StepAmount.repetitions(12), "auf Fäusten");
        new CreateTask(repository, repository, repository, clock, ids).execute(definition(
                "Training", Recurrence.DAILY, 0, TaskBoundKind.FOREVER, null,
                Collections.singletonList(exercise)));
        new MaterializeDueOccurrences(repository, repository, repository, repository, repository, clock, ids).execute();
        OccurrenceStep step = repository.occurrenceSteps(
                repository.openOccurrences().get(0).id).get(0);

        assertEquals(0, new RecordRepetitionResult(repository, repository, repository, repository, clock).execute(step.id, 0).xp);

        OccurrenceStep restored = new RoomTaskRepository(database).findOccurrenceStep(step.id);
        assertTrue(restored.done);
        assertEquals(Collections.singletonList(0), restored.repetitionProgress.actualRepetitions);
        assertEquals(0, repository.combo(step.comboOwnerId).points);
        assertEquals(0, new RecordRepetitionResult(repository, repository, repository, repository, clock).execute(step.id, 1).xp);

        assertEquals(0, new ToggleStep(repository, repository, repository, repository, clock).execute(step.id).xp);
        assertTrue(repository.findOccurrenceStep(step.id).done);
        assertTrue(repository.rewardBookings(step.occurrenceId).isEmpty());
    }

    @Test public void editorBundleKeepsDirtyExpandedStepsAndErrors() {
        EditorUiState initial = EditorUiState.create();
        EditorStepState step = EditorStepState.blank(1).withText("Bad putzen");
        EditorUiState draft = initial.draft("Wohnung", TaskSlot.MORNING, 30,
                Recurrence.WEEKDAYS, 1, 1 | 8, TimeOfDay.MORNING.bit,
                TaskBoundKind.FOR_WEEKS, TODAY.plusWeeks(2), 2, null, null,
                "Notiz", Collections.singletonList(step), step.id, 2)
                .withFeedback(Collections.singleton(ValidationIssue.step(
                                ValidationIssue.Field.STEP_AMOUNT, step.id)),
                        EditorUiState.Prompt.DISCARD, "");
        draft = draft.withPage(EditorUiState.Page.SCHEDULE, true);
        draft = draft.withValidationAttempt(EditorUiState.Page.STEPS, step.id, draft.issues);
        draft = draft.withValidationAttempt(EditorUiState.Page.SCHEDULE, null, draft.issues);
        EditorUiState restored = EditorUiState.fromBundle(draft.toBundle());
        assertTrue(restored.dirty);
        assertEquals(step.id, restored.expandedStepId);
        assertEquals(EditorUiState.Prompt.DISCARD, restored.prompt);
        assertTrue(restored.issues.contains(ValidationIssue.step(
                ValidationIssue.Field.STEP_AMOUNT, step.id)));
        assertTrue(restored.attemptedStepIds.contains(step.id));
        assertTrue(restored.attemptedPages.contains(EditorUiState.Page.SCHEDULE));
        assertEquals(EditorUiState.Page.SCHEDULE, restored.page);
        assertTrue(restored.returnToSummary);
    }

    @Test public void stepCadenceBundleKeepsSelectedEmptyIntervalAndReadsLegacyValues() {
        EditorStepState interval = new EditorStepState("step", "Gießen",
                StepCadenceMode.INTERVAL, 0, null,
                StepPrescription.forAmount(StepAmount.none()), null, "",
                StepActivationKind.SCHEDULED);
        EditorStepState restored = EditorStepState.fromBundle(interval.toBundle());
        assertEquals(StepCadenceMode.INTERVAL, restored.cadenceMode);
        assertEquals(null, restored.intervalDays);

        Bundle legacy = new Bundle();
        legacy.putString("id", "legacy"); legacy.putString("text", "Alt");
        legacy.putInt("weekdays", 0); legacy.putInt("interval", 4);
        legacy.putString("amount", StepAmountKind.NONE.name());
        EditorStepState migrated = EditorStepState.fromBundle(legacy);
        assertEquals(StepCadenceMode.INTERVAL, migrated.cadenceMode);
        assertEquals(Integer.valueOf(4), migrated.intervalDays);
    }

    private static TaskDefinition definition(String title, Recurrence recurrence, int weekdays,
                                             TaskBoundKind bound, LocalDate until,
                                             List<TaskStepDefinition> steps) {
        return new TaskDefinition(title, null, TaskSlot.MORNING, recurrence, 2, weekdays,
                recurrence == Recurrence.ONCE ? 0 : TimeOfDay.MORNING.bit, bound, until,
                bound == TaskBoundKind.FOR_WEEKS ? 2 : null,
                bound == TaskBoundKind.N_TIMES ? 2 : null, null, "", steps);
    }

    private static final class SequenceIds implements IdGenerator {
        private int value;
        @Override public String nextId() { return "feature-" + ++value; }
    }

    private static final class MutableClock implements Clock {
        private LocalDate date;
        private MutableClock(LocalDate date) { this.date = date; }
        @Override public LocalDate today() { return date; }
        @Override public LocalTime time() { return LocalTime.of(9, 40); }
    }
}
