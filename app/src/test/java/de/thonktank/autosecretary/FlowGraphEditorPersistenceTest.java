package de.thonktank.autosecretary;

import static org.junit.Assert.*;
import android.content.Context;
import android.database.Cursor;
import androidx.room.Room;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import de.thonktank.autosecretary.data.local.*;
import de.thonktank.autosecretary.domain.model.*;
import de.thonktank.autosecretary.domain.repository.FlowGraphDefinitionRepository;
import de.thonktank.autosecretary.domain.usecase.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Real catalog/step/capacity adapters against the prepared graph schema, not a production upgrade. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35})
public final class FlowGraphEditorPersistenceTest {
    private AppDatabase database;
    private SupportSQLiteDatabase sql;
    private RoomRepositoryFixture repository;
    private FlowGraphDefinitionRepository graphs;
    private SaveFlowGraph save;
    private LoadFlowGraph load;
    private CreateTask create;
    private int nextId;
    private final IdGenerator ids = () -> "editor-id-" + ++nextId;
    private static final LocalDate DATE = LocalDate.of(2026, 9, 11);

    @Before public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).allowMainThreadQueries().build();
        sql = database.getOpenHelper().getWritableDatabase();
        database.runInTransaction(() -> new FlowGraphMigration25().migrate(sql));
        repository = new RoomRepositoryFixture(database);
        graphs = new SqlFlowGraphDefinitionRepository(() -> sql, repository.steps);
        Clock clock = new Clock() {
            @Override public LocalDate today() { return DATE; }
            @Override public LocalTime time() { return LocalTime.NOON; }
        };
        create = new CreateTask(repository.catalog, repository.steps, repository.today, repository.transactions, clock, ids);
        save = saver(graphs);
        load = new LoadFlowGraph(repository.catalog, repository.steps, repository.flows, graphs, repository.transactions);
    }

    @After public void tearDown() { database.close(); }

    @Test public void aSingleStepPersistsAsAFlowIncludingCadenceWaitAndCapacity() {
        FlowEditorDraft draft = FlowEditorDraft.empty().rename("Ein Ablauf").addStep("Beginnen", FlowDelayPolicy.rememberLast(7_200_000));
        String step = draft.steps.get(0).id;
        draft = draft.cadence(step, 5);
        FlowEditorCapacities capacities = draft.capacities.addResource("Waschmaschine", 1);
        capacities = capacities.addLease(capacities.resources.get(0).key, step, step, 1);
        draft = draft.withCapacities(capacities).releaseAfter(capacities.leases.get(0).key, true);
        TaskId id = save.execute(draft.edit());
        assertTrue(repository.transactions.inTransaction(() -> graphs.isFlowTask(id)));
        FlowEditorDraft restored = FlowEditorDraft.from(load.execute(id));
        assertEquals("Ein Ablauf", restored.name);
        assertEquals(1, restored.steps.size());
        assertFalse(restored.steps.get(0).isDraftIdentity());
        assertEquals(Integer.valueOf(5), restored.steps.get(0).intervalDays);
        assertEquals(FlowDelayPolicy.Mode.REMEMBER_LAST, restored.waits.get(restored.steps.get(0).id).mode);
        assertTrue(restored.releaseAfterWait.get(restored.capacities.leases.get(0).key));
        assertEquals("0", scalar("SELECT COUNT(*) FROM step_flow_runs"));
        assertTrue(repository.flows.flowCandidates().isEmpty());
        assertTrue(repository.today.openOccurrences().isEmpty());
    }

    @Test public void laundryRootsAndSharedFollowUpsRoundTripWithStableIdentities() {
        FlowEditorDraft draft = FlowEditorDraft.empty().rename("Wäsche");
        for (String title : Arrays.asList("Bunt", "Weiß", "Handtuch", "Bett", "Aufhängen", "Abhängen", "Wegräumen"))
            draft = draft.addStep(title, FlowDelayPolicy.fixed(0));
        List<String> keys = draft.graph.stepIds;
        List<FlowTileGraph.Link> edges = new ArrayList<>();
        for (int i = 0; i < 4; i++) edges.add(new FlowTileGraph.Link(keys.get(i), keys.get(4)));
        edges.add(new FlowTileGraph.Link(keys.get(4), keys.get(5)));
        edges.add(new FlowTileGraph.Link(keys.get(5), keys.get(6)));
        draft = draft.withGraph(new FlowTileGraph(keys, edges));
        draft = draft.cadence(keys.get(0), 5).cadence(keys.get(1), 5).cadence(keys.get(2), 7).cadence(keys.get(3), 20);
        FlowEditorCapacities capacities = draft.capacities.addResource("Waschmaschine", 1).addResource("Wäscheständer", 3);
        for (int i = 0; i < 4; i++) {
            draft = draft.editStep(keys.get(i), draft.step(keys.get(i)).text, FlowDelayPolicy.rememberLast(7_200_000));
            capacities = capacities.addLease(capacities.resources.get(0).key, keys.get(i), keys.get(i), 1)
                    .addLease(capacities.resources.get(1).key, keys.get(i), keys.get(5), 1);
        }
        draft = draft.withCapacities(capacities).editStep(keys.get(4), "Aufhängen", FlowDelayPolicy.fixed(86_400_000));
        for (int i = 0; i < capacities.leases.size(); i += 2) draft = draft.releaseAfter(capacities.leases.get(i).key, true);
        TaskId taskId = save.execute(draft.edit());
        FlowEditorDraft loaded = FlowEditorDraft.from(load.execute(taskId));
        assertEquals(4, loaded.graph.roots().size());
        assertEquals(6, loaded.graph.links.size());
        assertEquals(8, loaded.capacities.leases.size());
        List<String> firstIds = loaded.graph.stepIds;
        List<String> leaseIds = new ArrayList<>(); loaded.capacities.leases.forEach(lease -> leaseIds.add(lease.persistedId));
        save.execute(loaded.rename("Wäsche neu").edit());
        FlowEditorDraft again = FlowEditorDraft.from(load.execute(taskId));
        assertEquals(firstIds, again.graph.stepIds);
        assertEquals(leaseIds, again.capacities.leases.stream().map(lease -> lease.persistedId).collect(java.util.stream.Collectors.toList()));
        FlowGraphRun run = new FlowGraphExecution(ids).snapshot(load.execute(taskId).definition, firstIds.get(0));
        assertEquals(4, run.steps.size());
        assertEquals(2, run.leases.size());
        assertEquals(FlowDelayPolicy.Mode.REMEMBER_LAST, run.steps.get(run.startStepId).source.waitAfter.mode);
    }

    @Test public void globalCapacityNameCollisionRollsBackTheNewTaskAndAllItsSteps() {
        repository.flows.putCapacityResource(new CapacityResource("existing", "Waschmaschine", 1));
        FlowEditorDraft draft = twoSteps();
        draft = draft.withCapacities(draft.capacities.addResource("  WASCHMASCHINE  ", 2));
        FlowGraphEdit input = draft.edit();
        assertThrows(IllegalArgumentException.class, () -> save.execute(input));
        assertTrue(repository.catalog.allTasks().isEmpty());
        assertEquals("0", scalar("SELECT COUNT(*) FROM task_steps"));
        assertEquals("0", scalar("SELECT COUNT(*) FROM task_schedule_entries"));
        assertEquals(1, repository.flows.capacityResources().size());
        assertEquals(1, repository.flows.findCapacityResource("existing").capacity);
    }

    @Test public void failedGraphSaveRollsBackTaskNameDefinitionAndCapacityChanges() {
        TaskId id = save.execute(withRack(twoSteps()).edit());
        FlowEditorDraft before = FlowEditorDraft.from(load.execute(id));
        String resource = before.capacities.resources.get(0).key;
        FlowEditorDraft edit = before.rename("Muss zurückrollen").withCapacities(before.capacities.updateResource(resource, "Neu", 2));
        FlowGraphDefinitionRepository failing = new FlowGraphDefinitionRepository() {
            @Override public boolean isFlowTask(TaskId taskId) { return graphs.isFlowTask(taskId); }
            @Override public FlowGraphDefinition find(TaskId taskId) { return graphs.find(taskId); }
            @Override public void replace(FlowGraphDefinition definition) { graphs.replace(definition); throw new IllegalStateException("Save failed"); }
            @Override public TaskId findSaveResult(String key) { return graphs.findSaveResult(key); }
            @Override public void recordSaveResult(String key, TaskId taskId) { graphs.recordSaveResult(key, taskId); }
        };
        assertThrows(IllegalStateException.class, () -> saver(failing).execute(edit.edit()));
        FlowEditorDraft after = FlowEditorDraft.from(load.execute(id));
        assertEquals(before.name, after.name);
        assertEquals(before.graph.links, after.graph.links);
        assertEquals("Ständer", after.capacities.resources.get(0).name);
        assertEquals(3, after.capacities.resources.get(0).capacity);
    }

    @Test public void exchangingCapacityNamesIsAtomicAndKeepsTheirIdentitiesAndAmounts() {
        repository.flows.putCapacityResource(new CapacityResource("one", "Maschine", 1));
        repository.flows.putCapacityResource(new CapacityResource("two", "Ständer", 3));
        FlowEditorDraft draft = FlowEditorDraft.from(load.execute(null)).rename("Tausch")
                .addStep("Schritt", FlowDelayPolicy.fixed(0));
        String first = draft.capacities.resources.stream().filter(r -> "one".equals(r.persistedId)).findFirst().get().key;
        String second = draft.capacities.resources.stream().filter(r -> "two".equals(r.persistedId)).findFirst().get().key;
        // A temporary distinct editor name permits an intentional exchange without duplicate draft names.
        FlowEditorCapacities edited = draft.capacities.updateResource(first, "Zwischenname", 1)
                .updateResource(second, "Maschine", 3).updateResource(first, "Ständer", 1);
        save.execute(draft.withCapacities(edited).edit());
        assertEquals("Ständer", repository.flows.findCapacityResource("one").name);
        assertEquals(1, repository.flows.findCapacityResource("one").capacity);
        assertEquals("Maschine", repository.flows.findCapacityResource("two").name);
        assertEquals(3, repository.flows.findCapacityResource("two").capacity);
        assertEquals(2, repository.flows.capacityResources().size());
    }

    @Test public void aDeletedUnusedCapacityDoesNotBlockSavingAnUnrelatedFlow() {
        repository.flows.putCapacityResource(new CapacityResource("unused", "Werkzeug", 1));
        TaskId taskId = save.execute(twoSteps().edit());
        FlowEditorDraft editing = FlowEditorDraft.from(load.execute(taskId));
        repository.flows.deleteCapacityResource("unused");
        save.execute(editing.rename("Neuer Name").edit());
        assertEquals("Neuer Name", repository.catalog.findTask(taskId).title);
        assertNull(repository.flows.findCapacityResource("unused"));
    }

    @Test public void renameDoesNotResetAnExhaustedBoundOrAnyExistingSchedulePlacement() {
        TaskId id = save.execute(twoSteps().edit());
        Task old = repository.catalog.findTask(id);
        TaskDefinition bound = new TaskDefinition(old.title, 17, TaskSlot.MORNING, Recurrence.INTERVAL,
                7, 0, TimeOfDay.MORNING.bit, TaskBoundKind.N_TIMES, null, null, 1, null,
                "Notiz außerhalb des Schrittdialogs", MissedOccurrenceMode.ACCUMULATE, Collections.emptyList());
        Task exhausted = old.editDefinition(bound, 456).afterPlanning(DATE.plusDays(10), 1);
        repository.catalog.updateTask(exhausted);
        repository.catalog.putScheduleEntries(Collections.singletonList(new TaskScheduleEntry("extra", id, TaskSlot.EVENING, 901)));
        List<TaskScheduleEntry> placements = repository.catalog.scheduleEntries(id);
        save.execute(FlowEditorDraft.from(load.execute(id)).rename("Umbenannt").edit());
        Task changed = repository.catalog.findTask(id);
        assertEquals("Umbenannt", changed.title);
        assertEquals(Integer.valueOf(0), changed.remainingCount);
        assertEquals(Recurrence.INTERVAL, changed.recurrence);
        assertEquals(7, changed.intervalDays);
        assertEquals(DATE.plusDays(10), changed.nextDueOn);
        assertEquals(17, changed.estimatedMinutes.intValue());
        assertEquals(exhausted.note, changed.note);
        assertEquals(MissedOccurrenceMode.ACCUMULATE, changed.missedOccurrenceMode);
        List<TaskScheduleEntry> kept = repository.catalog.scheduleEntries(id);
        assertEquals(placements.size(), kept.size());
        for (int i = 0; i < placements.size(); i++) {
            assertEquals(placements.get(i).id, kept.get(i).id);
            assertEquals(placements.get(i).slot, kept.get(i).slot);
            assertEquals(placements.get(i).displayOrder, kept.get(i).displayOrder);
        }
    }

    @Test public void livePrescriptionAndUntouchedCapacityAreNotOverwrittenByAnOlderEditor() {
        TaskId id = save.execute(withRack(twoSteps()).edit());
        FlowEditorDraft editing = FlowEditorDraft.from(load.execute(id));
        TaskStepTemplate old = repository.steps.templates(id).get(0);
        StepPrescription changed = StepPrescription.restore(StepAmount.setsReps(3, 8), RestTimerPolicy.inherit(),
                ResistanceLoad.restore("EXTERNAL", "KG", 12000L), 3);
        repository.steps.insertTemplates(Collections.singletonList(new TaskStepTemplate(old.id, id, old.position,
                old.text, old.weekdayMask, old.intervalDays, changed, null, "Neue Notiz", old.activationKind)));
        String resourceId = editing.capacities.resources.get(0).persistedId;
        repository.flows.putCapacityResource(new CapacityResource(resourceId, "Live-Name", 2));
        save.execute(editing.rename("Editorname").edit());
        LoadFlowGraph.Setup loaded = load.execute(id);
        assertEquals(changed, loaded.steps.get(0).prescription);
        assertEquals("Neue Notiz", loaded.steps.get(0).note);
        assertEquals("Live-Name", loaded.resources.get(0).name);
        assertEquals(2, loaded.resources.get(0).capacity);
    }

    @Test public void editingTheDefinitionNeverChangesAnAlreadyStartedSnapshotOrClaim() {
        TaskId id = save.execute(withRack(twoSteps()).edit());
        LoadFlowGraph.Setup original = load.execute(id);
        FlowGraphExecution execution = new FlowGraphExecution(ids);
        FlowGraphExecution.Capacity capacity = new FlowGraphExecution.Capacity(
                Map.of(original.resources.get(0).id, 3), Collections.emptyMap());
        FlowGraphRun started = execution.settle(execution.snapshot(original.definition, original.definition.graph.roots().get(0)), 100, capacity);
        started = execution.complete(started, started.startStepId, null, 4, 100, capacity).run;
        FlowGraphRun snapshot = started;
        repository.transactions.inTransaction(() -> {
            new FlowGraphSnapshotWriter(sql).insert(new FlowCandidate("candidate", id, snapshot.steps.get(snapshot.startStepId).source.id,
                    "started", DATE, TaskSlot.MORNING, 45, 100), snapshot, 100); return null;
        });
        FlowEditorDraft editing = FlowEditorDraft.from(original);
        save.execute(editing.editStep(editing.steps.get(0).id, "Späterer Name", FlowDelayPolicy.fixed(9000)).edit());
        FlowGraphRun kept = repository.transactions.inTransaction(() -> new FlowGraphSnapshotReader(sql).find(snapshot.id));
        assertEquals("Start", kept.steps.get(kept.startStepId).source.title);
        assertEquals(Long.valueOf(1000), kept.steps.get(kept.startStepId).chosenDelayMillis);
        assertEquals(Long.valueOf(1100), kept.steps.get(kept.startStepId).readyAtEpochMillis);
        assertEquals(FlowResourceState.ACTIVE, kept.leases.get(0).state);
    }

    @Test public void deletedOrForeignPersistedStepIsRejectedWithoutRecreatingIt() {
        TaskId id = save.execute(twoSteps().edit());
        FlowEditorDraft editing = FlowEditorDraft.from(load.execute(id));
        repository.steps.deleteTemplate(editing.steps.get(0).id);
        assertThrows(IllegalArgumentException.class, () -> save.execute(editing.rename("Nicht speichern").edit()));
        assertEquals("Ablauf", repository.catalog.findTask(id).title);
        assertEquals(1, repository.steps.templates(id).size());
    }

    @Test public void recoveringTheSameSaveAttemptDoesNotCreateAnotherTaskOrResurrectADeletedOne() {
        FlowGraphEdit input = withRack(twoSteps()).edit();
        TaskId first = save.execute(input, "one-save-attempt");
        TaskId recovered = saver(graphs).execute(input, "one-save-attempt");
        assertEquals(first, recovered);
        assertEquals(1, repository.catalog.allTasks().size());
        assertEquals(1, repository.flows.capacityResources().size());
        repository.catalog.deleteTask(first);
        assertEquals(first, saver(graphs).execute(input, "one-save-attempt"));
        assertTrue(repository.catalog.allTasks().isEmpty());
    }

    private SaveFlowGraph saver(FlowGraphDefinitionRepository store) {
        return new SaveFlowGraph(repository.catalog, repository.steps, repository.flows, store, repository.transactions, create, ids);
    }
    private FlowEditorDraft twoSteps() {
        FlowEditorDraft draft = FlowEditorDraft.empty().rename("Ablauf").addStep("Start", FlowDelayPolicy.fixed(1000))
                .addStep("Ende", FlowDelayPolicy.fixed(0));
        return draft.withGraph(new FlowTileGraph(draft.graph.stepIds,
                Collections.singletonList(new FlowTileGraph.Link(draft.steps.get(0).id, draft.steps.get(1).id))));
    }
    private FlowEditorDraft withRack(FlowEditorDraft draft) {
        FlowEditorCapacities capacities = draft.capacities.addResource("Ständer", 3);
        return draft.withCapacities(capacities.addLease(capacities.resources.get(0).key,
                draft.steps.get(0).id, draft.steps.get(1).id, 1));
    }
    private String scalar(String query) {
        try (Cursor cursor = sql.query(query)) { assertTrue(cursor.moveToFirst()); return cursor.getString(0); }
    }
}
