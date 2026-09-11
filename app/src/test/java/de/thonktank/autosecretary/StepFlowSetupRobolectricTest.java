package de.thonktank.autosecretary;

import static org.junit.Assert.*;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import de.thonktank.autosecretary.domain.model.*;
import de.thonktank.autosecretary.domain.repository.ComboPolicySource;
import java.time.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Replaces the retired embedded-flow editor contract with explicit task/flow ownership. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35})
public final class StepFlowSetupRobolectricTest {
    private AppDatabase database;
    private RoomRepositoryFixture repository;
    private ApplicationUseCaseComposition app;
    private int ids;

    @Before public void setup() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase.class)
                .allowMainThreadQueries().build();
        repository = new RoomRepositoryFixture(database);
        Clock clock = new Clock() {
            public LocalDate today() { return LocalDate.of(2026, 9, 11); }
            public LocalTime time() { return LocalTime.NOON; }
        };
        app = new ApplicationUseCaseComposition(database, clock, () -> "boundary-" + ++ids, ComboPolicySource.defaults());
    }
    @After public void close() { database.close(); }

    @Test public void normalEditorCannotCreateAFlowOrWriteCapacities() {
        TaskDefinition task = TaskDefinition.basic("Alt", TaskSlot.MORNING, Recurrence.DAILY, 1, 0, List.of("A", "B"));
        FlowConfigurationDraft draft = new FlowConfigurationDraft(List.of("a", "b"),
                List.of(new FlowConfigurationDraft.Link("a", "b", FlowDelayPolicy.fixed(100))),
                List.of(new FlowConfigurationDraft.Resource("rack", null, "Ständer", 1, true)), List.of());
        assertThrows(IllegalArgumentException.class, () -> app.catalog.saveTaskConfiguration.execute(null, task, draft));
        assertTrue(repository.catalog.allTasks().isEmpty());
        assertTrue(repository.flows.capacityResources().isEmpty());
    }

    @Test public void staleNormalEditorCannotOverwriteAnExplicitFlow() {
        TaskId id = app.flows.saveGraph.execute(FlowEditorDraft.empty().rename("Ablauf")
                .addStep("Start", FlowDelayPolicy.rememberLast(100)).edit());
        TaskDefinition replacement = TaskDefinition.basic("Veraltet", TaskSlot.EVENING, Recurrence.DAILY,
                1, 0, List.of("Ersatz"));
        assertThrows(IllegalArgumentException.class, () -> app.catalog.update.execute(id, replacement));
        assertEquals("Ablauf", app.flows.loadGraph.execute(id).task.title);
        assertEquals("Start", repository.steps.templates(id).get(0).text);
    }

    @Test public void unrelatedNormalTaskCanBeSavedWhileAParallelFlowExists() {
        FlowEditorDraft flow = FlowEditorDraft.empty().rename("Parallel")
                .addStep("Start", FlowDelayPolicy.fixed(0)).addStep("Links", FlowDelayPolicy.fixed(0))
                .addStep("Rechts", FlowDelayPolicy.fixed(0));
        List<String> keys = flow.graph.stepIds;
        flow = flow.withGraph(new FlowTileGraph(keys, List.of(new FlowTileGraph.Link(keys.get(0), keys.get(1)),
                new FlowTileGraph.Link(keys.get(0), keys.get(2)))));
        TaskId flowId = app.flows.saveGraph.execute(flow.edit());
        TaskDefinition task = TaskDefinition.basic("Normal", TaskSlot.EVENING, Recurrence.DAILY, 1, 0, List.of("Tun"));
        TaskId normal = app.catalog.saveTaskConfiguration.execute(null, task,
                new FlowConfigurationDraft(List.of("one"), List.of(), List.of(), List.of()));
        assertEquals(TaskKind.TASK, repository.catalog.findTask(normal).kind);
        assertEquals(2, app.flows.loadGraph.execute(flowId).definition.graph.links.size());
    }
}
