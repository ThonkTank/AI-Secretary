package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import de.thonktank.autosecretary.data.local.RoomTaskRepository;
import de.thonktank.autosecretary.domain.model.FlowDelayPolicy;
import de.thonktank.autosecretary.domain.model.FlowConfigurationDraft;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.RestTimerPolicy;
import de.thonktank.autosecretary.domain.model.StepActivationKind;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.StepResourceLease;
import de.thonktank.autosecretary.domain.model.StepTransition;
import de.thonktank.autosecretary.domain.model.TaskBoundKind;
import de.thonktank.autosecretary.domain.model.TaskDefinition;
import de.thonktank.autosecretary.domain.model.TaskDetails;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskStepDefinition;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.model.TimeOfDay;
import de.thonktank.autosecretary.domain.repository.ApplicationTaskRepository;
import de.thonktank.autosecretary.domain.usecase.IdGenerator;
import de.thonktank.autosecretary.domain.usecase.TaskUseCases;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class StepFlowSetupRobolectricTest {
    private AppDatabase database;
    private ApplicationTaskRepository repository;
    private TaskUseCases tasks;

    @Before public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries().build();
        repository = new RoomTaskRepository(database);
        Clock clock = new Clock() {
            @Override public LocalDate today() { return LocalDate.of(2026, 8, 25); }
            @Override public LocalTime time() { return LocalTime.NOON; }
        };
        tasks = new TaskUseCases(repository, clock, new SequenceIds());
        tasks.create.execute(TaskDefinition.basic("Wäsche", TaskSlot.MORNING,
                Recurrence.DAILY, 1, 0,
                Arrays.asList("Waschgang", "Aufhängen", "Abhängen")));
    }

    @After public void tearDown() { database.close(); }

    @Test public void combinedEditorSaveCreatesConvergingLaundryFlowAndSharedResources() {
        int taskCount = repository.allTasks().size();
        TaskDefinition definition = TaskDefinition.basic("Wäsche-Test", TaskSlot.MORNING,
                Recurrence.DAILY, 1, 0, Arrays.asList(
                        "Buntwäsche starten", "Weißwäsche starten", "Wäsche aufhängen",
                        "Wäsche abnehmen"));
        List<String> keys = Arrays.asList("color", "white", "hang", "take-down");
        List<FlowConfigurationDraft.Link> links = Arrays.asList(
                new FlowConfigurationDraft.Link("color", "hang",
                        FlowDelayPolicy.rememberLast(7_200_000L)),
                new FlowConfigurationDraft.Link("white", "hang",
                        FlowDelayPolicy.rememberLast(7_200_000L)),
                new FlowConfigurationDraft.Link("hang", "take-down",
                        FlowDelayPolicy.fixed(86_400_000L)));
        List<FlowConfigurationDraft.Resource> resources = Arrays.asList(
                new FlowConfigurationDraft.Resource("washer-key", null,
                        "Waschmaschine", 1, true),
                new FlowConfigurationDraft.Resource("rack-key", null,
                        "Wäscheständer", 2, true));
        List<FlowConfigurationDraft.Lease> leases = Arrays.asList(
                new FlowConfigurationDraft.Lease("washer-color", null, "washer-key",
                        "color", "hang", 1),
                new FlowConfigurationDraft.Lease("washer-white", null, "washer-key",
                        "white", "hang", 1),
                new FlowConfigurationDraft.Lease("rack-color", null, "rack-key",
                        "color", "take-down", 1),
                new FlowConfigurationDraft.Lease("rack-white", null, "rack-key",
                        "white", "take-down", 1));

        de.thonktank.autosecretary.domain.model.TaskId saved =
                tasks.saveTaskConfiguration.execute(null, definition,
                        new FlowConfigurationDraft(keys, links, resources, leases));

        assertEquals(taskCount + 1, repository.allTasks().size());
        assertEquals(3, repository.stepTransitions(saved).size());
        assertEquals(4, repository.stepResourceLeases(saved).size());
        List<TaskStepTemplate> savedSteps = repository.templates(saved);
        assertEquals(StepActivationKind.SCHEDULED, savedSteps.get(0).activationKind);
        assertEquals(StepActivationKind.SCHEDULED, savedSteps.get(1).activationKind);
        assertEquals(StepActivationKind.FOLLOW_UP, savedSteps.get(2).activationKind);
        assertEquals(StepActivationKind.FOLLOW_UP, savedSteps.get(3).activationKind);
        assertEquals(1, repository.capacityResources().stream()
                .filter(value -> value.name.equals("Waschmaschine")).findFirst().get().capacity);
        assertEquals(2, repository.capacityResources().stream()
                .filter(value -> value.name.equals("Wäscheständer")).findFirst().get().capacity);
    }

    @Test public void invalidCombinedEditorSaveRollsBackTaskAndNewResource() {
        int taskCount = repository.allTasks().size();
        TaskDefinition definition = TaskDefinition.basic("Ungültiger Ablauf",
                TaskSlot.MORNING, Recurrence.DAILY, 1, 0,
                Arrays.asList("Start", "Ende"));
        FlowConfigurationDraft draft = new FlowConfigurationDraft(
                Arrays.asList("start", "end"),
                java.util.Collections.singletonList(new FlowConfigurationDraft.Link(
                        "start", "end", FlowDelayPolicy.fixed(0L))),
                java.util.Collections.singletonList(new FlowConfigurationDraft.Resource(
                        "only-one", null, "Nur einer", 1, true)),
                java.util.Collections.singletonList(new FlowConfigurationDraft.Lease(
                        "too-many", null, "only-one", "start", "end", 2)));

        assertThrows(IllegalArgumentException.class,
                () -> tasks.saveTaskConfiguration.execute(null, definition, draft));

        assertEquals(taskCount, repository.allTasks().size());
        assertEquals(0, repository.capacityResources().stream()
                .filter(value -> value.name.equals("Nur einer")).count());
    }

    @Test public void setupDerivesRolesAtomicallyAndSurvivesNormalTaskEdits() {
        de.thonktank.autosecretary.domain.model.Task task = repository.allTasks().get(0);
        List<TaskStepTemplate> steps = repository.templates(task.id);
        String wash = steps.get(0).id;
        String hang = steps.get(1).id;
        String takeDown = steps.get(2).id;
        tasks.saveCapacityResource.execute("rack", "Wäscheständer", 2);
        Map<String, StepActivationKind> roles = roles(steps, hang, takeDown);
        List<StepTransition> transitions = Arrays.asList(
                new StepTransition(wash, hang, FlowDelayPolicy.rememberLast(7_200_000L)),
                new StepTransition(hang, takeDown, FlowDelayPolicy.fixed(86_400_000L)));
        List<StepResourceLease> leases = java.util.Collections.singletonList(
                new StepResourceLease("rack-rule", task.id, wash, takeDown, "rack", 2));

        tasks.saveStepFlowSetup.execute(task.id, roles, transitions, leases);

        assertEquals(2, tasks.loadStepFlowSetup.execute(task.id).transitions.size());
        assertEquals(StepActivationKind.SCHEDULED,
                repository.templates(task.id).get(0).activationKind);
        assertEquals(StepActivationKind.FOLLOW_UP,
                repository.templates(task.id).get(1).activationKind);

        TaskDetails details = tasks.loadTaskDetails.execute(task.id);
        List<TaskStepDefinition> definitions = new ArrayList<>();
        for (TaskStepTemplate step : details.stepTemplates) definitions.add(step.definition());
        tasks.update.execute(task.id, new TaskDefinition("Wäsche umbenannt",
                details.estimatedMinutes, details.slot, details.recurrence, details.intervalDays,
                details.weekdayMask, details.timeOfDayMask, details.boundKind,
                details.boundUntilOn, details.boundWeeks, details.remainingCount,
                details.deadlineOn, details.note, definitions));

        assertEquals(2, repository.stepTransitions(task.id).size());
        assertEquals(1, repository.stepResourceLeases(task.id).size());
        assertEquals(StepActivationKind.FOLLOW_UP,
                repository.templates(task.id).get(1).activationKind);

        Map<String, StepActivationKind> invalid = roles(steps, hang, takeDown);
        assertThrows(IllegalArgumentException.class, () -> tasks.saveStepFlowSetup.execute(
                task.id, invalid, java.util.Collections.emptyList(), leases));
        assertEquals(2, repository.stepTransitions(task.id).size());
    }

    @Test public void shrinkingResourceBelowAnExistingRuleRollsBack() {
        de.thonktank.autosecretary.domain.model.Task task = repository.allTasks().get(0);
        List<TaskStepTemplate> steps = repository.templates(task.id);
        tasks.saveCapacityResource.execute("rack", "Wäscheständer", 2);
        Map<String, StepActivationKind> roles = roles(steps, steps.get(1).id, steps.get(2).id);
        tasks.saveStepFlowSetup.execute(task.id, roles, Arrays.asList(
                        new StepTransition(steps.get(0).id, steps.get(1).id,
                                FlowDelayPolicy.fixed(0L)),
                        new StepTransition(steps.get(1).id, steps.get(2).id,
                                FlowDelayPolicy.fixed(0L))),
                java.util.Collections.singletonList(new StepResourceLease("rack-rule", task.id,
                        steps.get(0).id, steps.get(2).id, "rack", 2)));

        assertThrows(IllegalArgumentException.class,
                () -> tasks.saveCapacityResource.execute("rack", "Wäscheständer", 1));

        assertEquals(2, repository.findCapacityResource("rack").capacity);
    }

    @Test public void changingFlowRolesPreservesCustomRestTimerPolicy() {
        de.thonktank.autosecretary.domain.model.Task task = repository.allTasks().get(0);
        List<TaskStepTemplate> steps = new ArrayList<>(repository.templates(task.id));
        TaskStepTemplate first = steps.get(0);
        steps.set(0, de.thonktank.autosecretary.testing.StepTestFixtures.template(first.id, first.taskId, first.position, first.text,
                first.weekdayMask, first.intervalDays, StepAmount.setsReps(3, 8),
                RestTimerPolicy.custom(75), first.note, first.activationKind));
        repository.insertTemplates(steps);
        steps = repository.templates(task.id);

        tasks.saveStepFlowSetup.execute(task.id, roles(steps, steps.get(1).id),
                java.util.Collections.singletonList(new StepTransition(steps.get(0).id,
                        steps.get(1).id, FlowDelayPolicy.fixed(0L))),
                java.util.Collections.emptyList());

        assertEquals(RestTimerPolicy.Mode.CUSTOM,
                repository.templates(task.id).get(0).restTimerPolicy.mode);
        assertEquals(Integer.valueOf(75),
                repository.templates(task.id).get(0).restTimerPolicy.customSeconds);
    }

    private static Map<String, StepActivationKind> roles(List<TaskStepTemplate> steps,
                                                          String... followUps) {
        List<String> automatic = Arrays.asList(followUps);
        Map<String, StepActivationKind> result = new HashMap<>();
        for (TaskStepTemplate step : steps) result.put(step.id, automatic.contains(step.id)
                ? StepActivationKind.FOLLOW_UP : StepActivationKind.SCHEDULED);
        return result;
    }

    private static final class SequenceIds implements IdGenerator {
        private int next;
        @Override public String nextId() { return "setup-" + ++next; }
    }
}
