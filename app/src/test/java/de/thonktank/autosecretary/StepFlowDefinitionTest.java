package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import de.thonktank.autosecretary.domain.model.CapacityResource;
import de.thonktank.autosecretary.domain.model.FlowDelayPolicy;
import de.thonktank.autosecretary.domain.model.FlowDefinitionException;
import de.thonktank.autosecretary.domain.model.FlowRunSnapshot;
import de.thonktank.autosecretary.domain.model.StepActivationKind;
import de.thonktank.autosecretary.domain.model.StepFlowDefinition;
import de.thonktank.autosecretary.domain.model.StepResourceLease;
import de.thonktank.autosecretary.domain.model.StepTransition;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.ResistanceLoad;
import de.thonktank.autosecretary.domain.model.RestTimerPolicy;
import de.thonktank.autosecretary.domain.model.TrainingAssistantConfig;
import de.thonktank.autosecretary.domain.model.TrainingAssistantState;
import de.thonktank.autosecretary.domain.model.TrainingMuscleGroup;
import de.thonktank.autosecretary.domain.usecase.CreateFlowRunSnapshot;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class StepFlowDefinitionTest {
    private static final TaskId TASK = TaskId.of("laundry");

    @Test public void scheduledSeedsMayConvergeIntoOneSharedLinearTail() {
        StepFlowDefinition definition = laundryDefinition();

        assertEquals(Arrays.asList("colors", "hang", "take-down", "put-away"),
                ids(definition.resolvedPath("colors")));
        assertEquals(Arrays.asList("whites", "hang", "take-down", "put-away"),
                ids(definition.resolvedPath("whites")));
    }

    @Test public void graphRejectsForksCyclesScheduledTargetsAndForeignSteps() {
        List<TaskStepTemplate> steps = steps();
        assertThrows(FlowDefinitionException.class, () -> definition(steps, Arrays.asList(
                edge("colors", "hang"), edge("colors", "take-down")),
                Collections.emptyList()));
        assertThrows(FlowDefinitionException.class, () -> definition(steps, Arrays.asList(
                edge("colors", "hang"), edge("hang", "take-down"),
                edge("take-down", "hang")), Collections.emptyList()));
        assertThrows(FlowDefinitionException.class, () -> definition(steps,
                Collections.singletonList(edge("hang", "colors")), Collections.emptyList()));

        List<TaskStepTemplate> foreign = new java.util.ArrayList<>(steps);
        foreign.add(new TaskStepTemplate("foreign", TaskId.of("other"), 7, "Fremd", 0, 0,
                StepAmount.none(), "", StepActivationKind.FOLLOW_UP));
        assertThrows(FlowDefinitionException.class, () -> definition(foreign,
                Collections.emptyList(), Collections.emptyList()));
    }

    @Test public void leasesRequireEnoughCapacityAndAReachableLaterRelease() {
        List<StepTransition> transitions = transitions();
        CapacityResource washer = new CapacityResource("washer", "Waschmaschine", 1);
        assertThrows(FlowDefinitionException.class, () -> new StepFlowDefinition(TASK, steps(),
                transitions, Collections.singletonList(new StepResourceLease("too-much", TASK,
                "colors", "hang", "washer", 2)), Collections.singletonList(washer)));
        assertThrows(FlowDefinitionException.class, () -> new StepFlowDefinition(TASK, steps(),
                transitions, Collections.singletonList(new StepResourceLease("backwards", TASK,
                "hang", "colors", "washer", 1)), Collections.singletonList(washer)));
        assertThrows(FlowDefinitionException.class, () -> new StepFlowDefinition(TASK, steps(),
                transitions, Arrays.asList(
                new StepResourceLease("one", TASK, "colors", "hang", "washer", 1),
                new StepResourceLease("two", TASK, "colors", "take-down", "washer", 1)),
                Collections.singletonList(washer)));
    }

    @Test public void runSnapshotKeepsResolvedTextDelayAndCapacityAfterDefinitionEdits() {
        AtomicInteger sequence = new AtomicInteger();
        FlowRunSnapshot snapshot = new CreateFlowRunSnapshot(
                () -> "id-" + sequence.incrementAndGet()).execute(laundryDefinition(),
                "colors", "due:colors", LocalDate.of(2026, 8, 25), TaskSlot.MORNING,
                1_000L, 10_000L);

        assertEquals(Arrays.asList("Buntwäsche", "Aufhängen", "Abhängen", "Wegräumen"),
                snapshot.steps.stream().map(value -> value.text)
                        .collect(java.util.stream.Collectors.toList()));
        assertEquals(2, snapshot.resources.size());
        assertEquals(1, snapshot.resources.get(0).capacityAtCreation);
        assertEquals(2 * 60 * 60 * 1_000L,
                snapshot.steps.get(0).delayAfter.proposedDelayMillis());
        assertEquals("colors", snapshot.run.seedStepId);
    }

    @Test public void rememberLastDelayProposesTheLastChosenValue() {
        FlowDelayPolicy policy = FlowDelayPolicy.rememberLast(60_000L).remember(90_000L);

        assertEquals(90_000L, policy.proposedDelayMillis());
        assertEquals(120_000L, policy.choose(120_000L));
    }

    @Test public void runSnapshotFreezesTrainingLoadAndTargetRir() {
        ResistanceLoad load = ResistanceLoad.numeric(ResistanceLoad.Mode.EXTERNAL,
                ResistanceLoad.Unit.KG, 60_000L);
        TrainingAssistantConfig training = TrainingAssistantConfig.defaults(
                load, TrainingMuscleGroup.CHEST);
        TaskStepTemplate press = new TaskStepTemplate("press", TASK, 0, "Bankdrücken",
                0, 0, StepAmount.setsReps(3, 10), RestTimerPolicy.inherit(),
                training, TrainingAssistantState.calibrating(), "",
                StepActivationKind.SCHEDULED);
        StepFlowDefinition definition = new StepFlowDefinition(TASK,
                Collections.singletonList(press), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());

        FlowRunSnapshot snapshot = new CreateFlowRunSnapshot(() -> "snapshot").execute(
                definition, "press", "due:press", LocalDate.of(2026, 8, 28),
                TaskSlot.MORNING, 1_000L, 10_000L);

        assertEquals(load, snapshot.steps.get(0).plannedLoad);
        assertEquals(2, snapshot.steps.get(0).targetRir);
    }

    private static StepFlowDefinition laundryDefinition() {
        List<StepResourceLease> leases = Arrays.asList(
                new StepResourceLease("washer-colors", TASK, "colors", "hang", "washer", 1),
                new StepResourceLease("dry-colors", TASK, "colors", "take-down", "dry", 1),
                new StepResourceLease("washer-whites", TASK, "whites", "hang", "washer", 1),
                new StepResourceLease("dry-whites", TASK, "whites", "take-down", "dry", 1));
        return new StepFlowDefinition(TASK, steps(), transitions(), leases, Arrays.asList(
                new CapacityResource("washer", "Waschmaschine", 1),
                new CapacityResource("dry", "Trockenplatz", 2)));
    }

    private static StepFlowDefinition definition(List<TaskStepTemplate> steps,
                                                 List<StepTransition> transitions,
                                                 List<StepResourceLease> leases) {
        return new StepFlowDefinition(TASK, steps, transitions, leases,
                Collections.singletonList(new CapacityResource("washer", "Waschmaschine", 1)));
    }

    private static List<TaskStepTemplate> steps() {
        return Arrays.asList(
                step("colors", 0, "Buntwäsche", StepActivationKind.SCHEDULED),
                step("whites", 1, "Weißwäsche", StepActivationKind.SCHEDULED),
                step("hang", 2, "Aufhängen", StepActivationKind.FOLLOW_UP),
                step("take-down", 3, "Abhängen", StepActivationKind.FOLLOW_UP),
                step("put-away", 4, "Wegräumen", StepActivationKind.FOLLOW_UP));
    }

    private static List<StepTransition> transitions() {
        return Arrays.asList(
                new StepTransition("colors", "hang",
                        FlowDelayPolicy.rememberLast(2 * 60 * 60 * 1_000L)),
                new StepTransition("whites", "hang",
                        FlowDelayPolicy.rememberLast(2 * 60 * 60 * 1_000L)),
                new StepTransition("hang", "take-down",
                        FlowDelayPolicy.rememberLast(24 * 60 * 60 * 1_000L)),
                new StepTransition("take-down", "put-away", FlowDelayPolicy.fixed(0L)));
    }

    private static StepTransition edge(String source, String target) {
        return new StepTransition(source, target, FlowDelayPolicy.fixed(0L));
    }

    private static TaskStepTemplate step(String id, int position, String text,
                                         StepActivationKind kind) {
        return new TaskStepTemplate(id, TASK, position, text, 0, 0,
                StepAmount.none(), "", kind);
    }

    private static List<String> ids(List<TaskStepTemplate> steps) {
        return steps.stream().map(value -> value.id)
                .collect(java.util.stream.Collectors.toList());
    }
}
