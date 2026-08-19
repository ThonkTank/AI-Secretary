package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;

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
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskBoundKind;
import de.thonktank.autosecretary.domain.model.TaskDefinition;
import de.thonktank.autosecretary.domain.model.TaskOrdering;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskStepDefinition;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.model.TimeOfDay;
import de.thonktank.autosecretary.domain.repository.TaskRepository;
import de.thonktank.autosecretary.domain.usecase.ConfirmSet;
import de.thonktank.autosecretary.domain.usecase.CompleteOccurrence;
import de.thonktank.autosecretary.domain.usecase.CreateTask;
import de.thonktank.autosecretary.domain.usecase.EditStepProgress;
import de.thonktank.autosecretary.domain.usecase.FinishExercise;
import de.thonktank.autosecretary.domain.usecase.IdGenerator;
import de.thonktank.autosecretary.domain.usecase.LoadDashboard;
import de.thonktank.autosecretary.domain.usecase.MaterializeDueOccurrences;
import de.thonktank.autosecretary.domain.usecase.ReopenExercise;
import de.thonktank.autosecretary.domain.usecase.UpdateTask;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class TaskEditorFeatureRobolectricTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 17);
    private AppDatabase database;
    private TaskRepository repository;
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
        TaskStepDefinition none = new TaskStepDefinition(null, 0, "Schritt", 0,
                StepAmount.none(), "Notiz");
        assertEquals(StepAmount.none(), none.amount);
        assertThrows(IllegalArgumentException.class, () -> new TaskStepDefinition(
                null, 0, "Schritt", 0, StepAmount.setsReps(0, 12), ""));
    }

    @Test public void multipleTimesMaterializeDistinctIdempotentOccurrencesAndRespectCount() {
        TaskDefinition definition = new TaskDefinition("Katze", 15, TaskSlot.MORNING,
                Recurrence.DAILY, 1, 0, TimeOfDay.MORNING.bit | TimeOfDay.EVENING.bit,
                TaskBoundKind.N_TIMES, null, null, 3, null, "", Collections.emptyList());
        new CreateTask(repository, clock, ids, new TaskOrdering()).execute(definition);
        MaterializeDueOccurrences materialize = new MaterializeDueOccurrences(repository, clock, ids);
        materialize.execute(); materialize.execute();

        List<Occurrence> firstRound = repository.openOccurrences();
        assertEquals(2, firstRound.size());
        assertEquals(TaskSlot.MORNING, firstRound.get(0).slot);
        assertEquals(TaskSlot.EVENING, firstRound.get(1).slot);
        assertEquals(Integer.valueOf(1), repository.allTasks().get(0).remainingCount);
        assertEquals(2, new LoadDashboard(repository).execute(TODAY).tasks.size());

        Occurrence morning = repository.findOccurrence(repository.allTasks().get(0).id,
                TODAY, TaskSlot.MORNING);
        CompleteOccurrence complete = new CompleteOccurrence(repository, clock);
        complete.execute(morning.id);
        materialize.execute();
        assertEquals(morning.id, repository.findOccurrence(repository.allTasks().get(0).id,
                TODAY, TaskSlot.MORNING).id);
        assertEquals(1, database.tasks().occurrencesByState("OPEN").size());
        assertEquals(1, database.tasks().completedOccurrences("COMPLETED", TODAY.toString()).size());
        complete.execute(firstRound.get(1).id);
        assertEquals(2, new LoadDashboard(repository).execute(TODAY).tasks.size());
        assertEquals(30, repository.xp());
        assertEquals(TODAY.plusDays(1), repository.allTasks().get(0).nextDueOn);
    }

    @Test public void newEditorChoosesAContextualDaySlot() {
        assertEquals(TaskSlot.MORNING, TaskViewModel.defaultEditorSlot(LocalTime.of(7, 30)));
        assertEquals(TaskSlot.MIDDAY, TaskViewModel.defaultEditorSlot(LocalTime.of(12, 0)));
        assertEquals(TaskSlot.EVENING, TaskViewModel.defaultEditorSlot(LocalTime.of(18, 0)));
        assertEquals(TaskSlot.LATER, TaskViewModel.defaultEditorSlot(LocalTime.of(22, 0)));
    }

    @Test public void templateUpdateKeepsIdentityAndDoesNotMutateOpenSnapshot() {
        TaskStepDefinition original = new TaskStepDefinition(null, 0, "Alt", 0,
                StepAmount.setsReps(3, 12), "23 kg");
        TaskDefinition definition = definition("Training", Recurrence.DAILY, 0,
                TaskBoundKind.FOREVER, null, Collections.singletonList(original));
        CreateTask create = new CreateTask(repository, clock, ids, new TaskOrdering());
        create.execute(definition);
        Task task = repository.allTasks().get(0);
        String stableId = repository.templates(task.id).get(0).id;
        new MaterializeDueOccurrences(repository, clock, ids).execute();
        Occurrence occurrence = repository.openOccurrences().get(0);

        TaskStepDefinition edited = new TaskStepDefinition(stableId, 0, "Neu", 0,
                StepAmount.setsReps(4, 10), "25 kg");
        new UpdateTask(repository, new TaskOrdering(), ids, clock).execute(task.id,
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

    @Test public void setProgressAndExplicitCompletionSurviveReloadAndReverseExactly() {
        int monday = 1;
        TaskStepDefinition mondayStep = new TaskStepDefinition(null, 0, "Beinpresse", monday,
                StepAmount.setsReps(3, 12), "23 kg, Sitz 5");
        new CreateTask(repository, clock, ids, new TaskOrdering()).execute(new TaskDefinition(
                "Training", 45, TaskSlot.MORNING, Recurrence.DAILY, 1, 0,
                TimeOfDay.MORNING.bit, TaskBoundKind.FOREVER, null, null, null, null,
                "", Collections.singletonList(mondayStep)));
        new MaterializeDueOccurrences(repository, clock, ids).execute();
        Occurrence occurrence = repository.openOccurrences().get(0);
        OccurrenceStep step = repository.occurrenceSteps(occurrence.id).get(0);
        ConfirmSet confirm = new ConfirmSet(repository, clock);
        confirm.execute(step.id, 10); confirm.execute(step.id, 11);

        OccurrenceStep restored = new RoomTaskRepository(database).findOccurrenceStep(step.id);
        assertEquals(Arrays.asList(10, 11), restored.actualRepetitions);
        assertFalse(restored.done);
        assertEquals(10, confirm.execute(step.id, 12).xp);
        assertTrue(repository.findOccurrenceStep(step.id).done);
        assertEquals(1, repository.combo(step.comboOwnerId).points);

        EditStepProgress edit = new EditStepProgress(repository);
        assertEquals(0, edit.execute(step.id, Arrays.asList(9, 11, 12)).xp);
        OccurrenceStep editedDone = repository.findOccurrenceStep(step.id);
        assertTrue(editedDone.done);
        assertEquals(10, vesselXp(occurrence.id));

        ReopenExercise reopen = new ReopenExercise(repository, clock);
        assertEquals(-10, reopen.execute(step.id, Arrays.asList(9, 11, 12)).xp);
        OccurrenceStep reopened = repository.findOccurrenceStep(step.id);
        assertFalse(reopened.done);
        assertEquals(Arrays.asList(9, 11, 12), reopened.actualRepetitions);
        assertEquals(0, vesselXp(occurrence.id));
        assertEquals(0, repository.combo(step.comboOwnerId).points);
        assertEquals(0, reopen.execute(step.id, reopened.actualRepetitions).xp);

        assertEquals(0, edit.execute(step.id, Arrays.asList(10, 11, 12)).xp);
        assertFalse(repository.findOccurrenceStep(step.id).done);
        FinishExercise finish = new FinishExercise(repository, clock);
        assertEquals(10, finish.execute(step.id).xp);
        assertEquals(0, finish.execute(step.id).xp);
        assertTrue(repository.findOccurrenceStep(step.id).done);
        assertEquals(1, repository.combo(step.comboOwnerId).points);
    }

    private int vesselXp(String occurrenceId) {
        return repository.rewardBookings(occurrenceId).stream()
                .filter(value -> value.target
                        == de.thonktank.autosecretary.domain.model.RewardBooking.Target.VESSEL)
                .mapToInt(value -> value.xpDelta).sum();
    }

    @Test public void finishExerciseClosesEarlyAndNewOccurrenceStartsEmpty() {
        TaskStepDefinition exercise = new TaskStepDefinition(null, 0, "Kniebeugen", 0,
                StepAmount.setsReps(3, 15), "");
        new CreateTask(repository, clock, ids, new TaskOrdering()).execute(definition(
                "Training", Recurrence.DAILY, 0, TaskBoundKind.FOREVER, null,
                Collections.singletonList(exercise)));
        new MaterializeDueOccurrences(repository, clock, ids).execute();
        OccurrenceStep step = repository.occurrenceSteps(repository.openOccurrences().get(0).id).get(0);
        new ConfirmSet(repository).execute(step.id, 13);
        new FinishExercise(repository).execute(step.id);
        assertTrue(repository.findOccurrenceStep(step.id).done);
        assertEquals(Collections.singletonList(13), repository.findOccurrenceStep(step.id).actualRepetitions);

        new CompleteOccurrence(repository, clock).execute(step.occurrenceId);
        clock.date = TODAY.plusDays(1);
        new MaterializeDueOccurrences(repository, clock, ids).execute();
        OccurrenceStep next = repository.occurrenceSteps(repository.openOccurrences().get(0).id).get(0);
        assertTrue(next.actualRepetitions.isEmpty());
        assertFalse(next.done);
    }

    @Test public void editorBundleKeepsDirtyExpandedStepsAndErrors() {
        EditorUiState initial = EditorUiState.create();
        EditorStepState step = EditorStepState.blank(1).withText("Bad putzen");
        EditorUiState draft = initial.draft("Wohnung", TaskSlot.MORNING, 30,
                Recurrence.WEEKDAYS, 1, 1 | 8, TimeOfDay.MORNING.bit,
                TaskBoundKind.FOR_WEEKS, TODAY.plusWeeks(2), 2, null, null,
                "Notiz", Collections.singletonList(step), step.id, 2)
                .withFeedback(Collections.singleton(TaskEditorValidator.AMOUNT_PREFIX + step.id),
                        EditorUiState.Prompt.DISCARD, "");
        EditorUiState restored = EditorUiState.fromBundle(draft.toBundle());
        assertTrue(restored.dirty);
        assertEquals(step.id, restored.expandedStepId);
        assertEquals(EditorUiState.Prompt.DISCARD, restored.prompt);
        assertTrue(restored.errors.contains(TaskEditorValidator.AMOUNT_PREFIX + step.id));
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
