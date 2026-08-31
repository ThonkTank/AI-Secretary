package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import de.thonktank.autosecretary.data.local.RoomTaskRepository;
import de.thonktank.autosecretary.domain.model.FlowDelayPolicy;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.StepActivationKind;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.StepFlowRun;
import de.thonktank.autosecretary.domain.model.StepResourceLease;
import de.thonktank.autosecretary.domain.model.StepTransition;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskBoundKind;
import de.thonktank.autosecretary.domain.model.TaskDefinition;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskStepDefinition;
import de.thonktank.autosecretary.domain.model.TimeOfDay;
import de.thonktank.autosecretary.domain.repository.ApplicationTaskRepository;
import de.thonktank.autosecretary.domain.usecase.CreateTask;
import de.thonktank.autosecretary.domain.usecase.IdGenerator;
import de.thonktank.autosecretary.domain.usecase.MaterializeDueOccurrences;
import de.thonktank.autosecretary.domain.usecase.SaveCapacityResource;
import de.thonktank.autosecretary.domain.usecase.SaveStepFlowDefinition;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class StepFlowMaterializationRobolectricTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 25);

    private AppDatabase database;
    private ApplicationTaskRepository repository;
    private SequenceIds ids;
    private Clock clock;

    @Before public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries().build();
        repository = new RoomTaskRepository(database);
        ids = new SequenceIds();
        clock = new Clock() {
            @Override public LocalDate today() { return TODAY; }
            @Override public LocalTime time() { return LocalTime.NOON; }
        };
    }

    @After public void tearDown() { database.close(); }

    @Test public void fourDueSeedsBecomeIdempotentRunsWithSharedTailSnapshots() {
        new CreateTask(repository, repository, clock, ids).execute(laundryTask());
        Task task = repository.allTasks().get(0);
        SaveCapacityResource resources = new SaveCapacityResource(repository, ids);
        resources.execute("washer", "Waschmaschine", 1);
        resources.execute("dry", "Trockenplatz", 2);
        new SaveStepFlowDefinition(repository, repository).execute(task.id,
                transitions(), leases(task));
        MaterializeDueOccurrences materialize = new MaterializeDueOccurrences(repository, clock,
                () -> 1_777_000L, ids);

        assertTrue(materialize.execute());
        assertFalse(materialize.execute());

        List<StepFlowRun> runs = repository.activeFlowRuns(task.id);
        assertEquals(4, runs.size());
        assertTrue(repository.openOccurrences().isEmpty());
        for (StepFlowRun run : runs) {
            assertEquals(4, repository.flowRunSteps(run.id).size());
            assertEquals("Aufhängen", repository.flowRunSteps(run.id).get(1).text);
            assertEquals("Abhängen", repository.flowRunSteps(run.id).get(2).text);
            assertEquals(2, repository.flowRunResources(run.id).size());
        }
        assertEquals(4, repository.templates(task.id).stream()
                .filter(value -> value.activationKind == StepActivationKind.SCHEDULED).count());
        assertEquals(3, repository.templates(task.id).stream()
                .filter(value -> value.activationKind == StepActivationKind.FOLLOW_UP).count());
    }

    @Test public void overdueFlowSeedsQueueEachDueInsteadOfDroppingBlockedLoads() {
        Clock startedEarlier = new Clock() {
            @Override public LocalDate today() { return TODAY.minusDays(2); }
            @Override public LocalTime time() { return LocalTime.NOON; }
        };
        new CreateTask(repository, repository, startedEarlier, ids).execute(laundryTask());
        Task task = repository.allTasks().get(0);
        SaveCapacityResource resources = new SaveCapacityResource(repository, ids);
        resources.execute("washer", "Waschmaschine", 1);
        resources.execute("dry", "Trockenplatz", 2);
        new SaveStepFlowDefinition(repository, repository).execute(task.id,
                transitions(), leases(task));

        MaterializeDueOccurrences materialize = new MaterializeDueOccurrences(repository, clock,
                () -> 1_777_000L, ids);

        assertTrue(materialize.execute());
        assertEquals(12, repository.activeFlowRuns(task.id).size());
        assertTrue(repository.openOccurrences().isEmpty());
        assertFalse(materialize.execute());
    }

    private static TaskDefinition laundryTask() {
        List<TaskStepDefinition> steps = new ArrayList<>();
        steps.add(step("colors", "Buntwäsche", StepActivationKind.SCHEDULED, 0));
        steps.add(step("whites", "Weißwäsche", StepActivationKind.SCHEDULED, 1));
        steps.add(step("sheets", "Bettwäsche", StepActivationKind.SCHEDULED, 2));
        steps.add(step("towels", "Handtücher", StepActivationKind.SCHEDULED, 3));
        steps.add(step("hang", "Aufhängen", StepActivationKind.FOLLOW_UP, 4));
        steps.add(step("take-down", "Abhängen", StepActivationKind.FOLLOW_UP, 5));
        steps.add(step("put-away", "Wegräumen", StepActivationKind.FOLLOW_UP, 6));
        return new TaskDefinition("Wäsche waschen", null, TaskSlot.MORNING,
                Recurrence.DAILY, 1, 0, TimeOfDay.MORNING.bit, TaskBoundKind.FOREVER,
                null, null, null, null, "", steps);
    }

    private static TaskStepDefinition step(String id, String text, StepActivationKind kind,
                                           int position) {
        return de.thonktank.autosecretary.testing.StepTestFixtures.definition(id, position, text, 0, 0, StepAmount.none(), "", kind);
    }

    private static List<StepTransition> transitions() {
        List<StepTransition> result = new ArrayList<>();
        for (String seed : Arrays.asList("colors", "whites", "sheets", "towels"))
            result.add(new StepTransition(seed, "hang",
                    FlowDelayPolicy.rememberLast(2 * 60 * 60 * 1_000L)));
        result.add(new StepTransition("hang", "take-down",
                FlowDelayPolicy.rememberLast(24 * 60 * 60 * 1_000L)));
        result.add(new StepTransition("take-down", "put-away", FlowDelayPolicy.fixed(0L)));
        return result;
    }

    private static List<StepResourceLease> leases(Task task) {
        List<StepResourceLease> result = new ArrayList<>();
        for (String seed : Arrays.asList("colors", "whites", "sheets", "towels")) {
            result.add(new StepResourceLease("washer-" + seed, task.id, seed, "hang",
                    "washer", 1));
            result.add(new StepResourceLease("dry-" + seed, task.id, seed, "take-down",
                    "dry", 1));
        }
        return result;
    }

    private static final class SequenceIds implements IdGenerator {
        private int next;
        @Override public String nextId() { return "generated-" + ++next; }
    }
}
