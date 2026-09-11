package de.thonktank.autosecretary;

import static org.junit.Assert.*;
import androidx.room.Room;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import de.thonktank.autosecretary.data.local.*;
import de.thonktank.autosecretary.domain.model.*;
import de.thonktank.autosecretary.domain.repository.*;
import de.thonktank.autosecretary.domain.usecase.*;
import java.time.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Actual shared SQLite transactions, including the existing step and HEAD reward ledger. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35})
public final class GraphFlowRuntimePersistenceTest {
    private AppDatabase database;
    private RoomRepositoryFixture repository;
    private FlowGraphDefinitionRepository definitions;
    private FlowGraphRunRepository runs;
    private GraphFlowRuntime runtime;
    private SaveFlowGraph save;
    private long now = 1_000;
    private int nextId;
    private final IdGenerator ids = () -> "graph-runtime-" + ++nextId;
    private static final LocalDate DATE = LocalDate.of(2026, 9, 11);

    @Before public void setup() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase.class)
                .allowMainThreadQueries().build();
        SupportSQLiteDatabase sql = database.getOpenHelper().getWritableDatabase();
        repository = new RoomRepositoryFixture(database);
        definitions = new SqlFlowGraphDefinitionRepository(() -> sql, repository.steps);
        runs = new SqlFlowGraphRunRepository(() -> sql);
        Clock clock = new Clock() {
            @Override public LocalDate today() { return DATE; }
            @Override public LocalTime time() { return LocalTime.NOON; }
        };
        CreateTask create = new CreateTask(repository.catalog, repository.steps, repository.today,
                repository.transactions, clock, ids);
        save = new SaveFlowGraph(repository.catalog, repository.steps, repository.flows, definitions,
                repository.transactions, create, ids);
        runtime = new GraphFlowRuntime(repository.catalog, repository.steps, repository.today, repository.flows,
                definitions, runs, repository.transactions, clock, () -> now, ids, ComboPolicySource.defaults());
    }

    @After public void close() { database.close(); }

    @Test public void oneStepCollectsThroughTheRealLedgerOnceAndLeavesNoOpenExecution() {
        TaskId task = save.execute(FlowEditorDraft.empty().rename("Ein Ablauf").addStep("Beginnen", FlowDelayPolicy.fixed(0)).edit());
        String candidate = candidate(task, "Beginnen");
        GraphFlowRuntime.Result first = runtime.start(candidate, null);
        assertEquals(FlowGraphCommands.Status.CHANGED, first.status);
        assertEquals(RewardReceipt.Target.HEAD, first.reward.target);
        assertTrue(first.reward.xp > 0);
        int paid = repository.today.xp();
        assertEquals(first.reward.xp, paid);
        assertTrue(run(first.runId).collected);
        assertTrue(repository.today.openOccurrences().isEmpty());
        assertTrue(repository.flows.flowCandidates().isEmpty());
        assertEquals(FlowGraphCommands.Status.NOT_FOUND, runtime.start(candidate, null).status);
        assertEquals(FlowGraphCommands.Status.UNCHANGED, runtime.collect(first.runId).status);
        assertEquals(paid, repository.today.xp());
    }

    @Test public void cancelledDurationQuestionCreatesNoRunOccurrenceClaimOrReward() {
        TaskId task = save.execute(FlowEditorDraft.empty().rename("Nachfragen").addStep("Start", FlowDelayPolicy.rememberLast(100)).edit());
        String candidate = candidate(task, "Start");
        assertEquals(FlowGraphCommands.Status.DURATION_REQUIRED, runtime.start(candidate, null).status);
        assertTrue(repository.transactions.inTransaction(runs::active).isEmpty());
        assertTrue(repository.today.allOccurrences().isEmpty());
        assertEquals(0, repository.today.xp());
        assertNotNull(repository.flows.findFlowCandidate(candidate));
        GraphFlowRuntime.Result started = runtime.start(candidate, 200L);
        assertEquals(Long.valueOf(now + 200), runtime.nextReadyAtEpochMillis());
        assertEquals(Long.valueOf(200), repository.transactions.inTransaction(() -> definitions.find(task))
                .nodes.values().iterator().next().waitAfter.lastUsedDelayMillis);
        assertEquals(0, repository.today.xp());
        now += 200;
        assertTrue(runtime.activateReady());
        assertTrue(run(started.runId).collectionAvailable());
        assertTrue(repository.today.openOccurrences().isEmpty());
        assertEquals(RewardReceipt.Target.HEAD, runtime.collect(started.runId).reward.target);
        assertFalse(runtime.activateReady());
    }

    @Test public void unequalParallelBranchesHaveSeparateOccurrencesAndOnlyTheirJoinWaitsForBoth() {
        FlowEditorDraft draft = FlowEditorDraft.empty().rename("Parallel");
        for (String title : List.of("Start", "Kurz", "Lang", "Ende"))
            draft = draft.addStep(title, FlowDelayPolicy.fixed(title.equals("Kurz") ? 100 : title.equals("Lang") ? 300 : 0));
        List<String> keys = draft.graph.stepIds;
        draft = draft.withGraph(new FlowTileGraph(keys, List.of(new FlowTileGraph.Link(keys.get(0), keys.get(1)),
                new FlowTileGraph.Link(keys.get(0), keys.get(2)), new FlowTileGraph.Link(keys.get(1), keys.get(3)),
                new FlowTileGraph.Link(keys.get(2), keys.get(3)))));
        TaskId task = save.execute(draft.edit());
        String id = runtime.start(candidate(task, "Start"), null).runId;
        assertEquals(2, run(id).availableSteps().size());
        assertEquals(2, repository.today.openOccurrences().size());
        LoadGraphFlowSheets.Result available = projection();
        assertEquals(1, available.sheets.size());
        assertEquals(2, available.sheets.get(0).entries.size());
        assertEquals(Set.of(step(id, "Kurz").id, step(id, "Lang").id), new HashSet<>(
                available.sheets.get(0).entries.stream().map(entry -> entry.targetId).toList()));
        assertEquals(RewardReceipt.Target.NONE, runtime.complete(step(id, "Kurz").id, null).reward.target);
        assertEquals(RewardReceipt.Target.NONE, runtime.complete(step(id, "Lang").id, null).reward.target);
        assertTrue(repository.today.openOccurrences().isEmpty());
        assertTrue(projection().sheets.isEmpty());
        assertEquals(2, projection().runs.get(0).steps.stream()
                .filter(step -> step.state == FlowGraphRun.State.WAITING_TIME).count());
        now += 100;
        runtime.activateReady();
        assertTrue(run(id).availableSteps().isEmpty());
        assertTrue(runtime.adjustWait(id, step(id, "Lang").waitId(), now + 500));
        assertEquals(FlowGraphRun.State.DONE, step(id, "Kurz").state);
        now += 500;
        runtime.activateReady();
        assertEquals("Ende", run(id).availableSteps().get(0).source.title);
        GraphFlowRuntime.Result collected = runtime.complete(step(id, "Ende").id, null);
        assertEquals(RewardReceipt.Target.HEAD, collected.reward.target);
        assertEquals(collected.reward.xp, repository.today.xp());
        assertTrue(repository.today.openOccurrences().isEmpty());
        assertEquals(FlowGraphCommands.Status.UNCHANGED, runtime.complete(step(id, "Ende").id, null).status);
        assertEquals(collected.reward.xp, repository.today.xp());
    }

    @Test public void laundryReservesThreeDryingPlacesButOnlyOneMachine() {
        FlowEditorDraft draft = FlowEditorDraft.empty().rename("Wäsche");
        for (String title : List.of("Bunt", "Weiß", "Handtücher", "Bett"))
            draft = draft.addStep(title, FlowDelayPolicy.fixed(100));
        draft = draft.addStep("Aufhängen", FlowDelayPolicy.fixed(1_000));
        draft = draft.addStep("Abhängen", FlowDelayPolicy.fixed(0));
        draft = draft.addStep("Wegräumen", FlowDelayPolicy.fixed(0));
        List<String> keys = draft.graph.stepIds;
        draft = draft.withGraph(draft.graph.join(keys.subList(0, 4), keys.get(4))
                .join(List.of(keys.get(4)), keys.get(5)).join(List.of(keys.get(5)), keys.get(6)));
        FlowEditorCapacities capacities = draft.capacities.addResource("Waschmaschine", 1).addResource("Wäscheständer", 3);
        List<String> machineLeases = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            capacities = capacities.addLease(capacities.resources.get(0).key, keys.get(i), keys.get(i), 1);
            machineLeases.add(capacities.leases.get(capacities.leases.size() - 1).key);
            capacities = capacities.addLease(capacities.resources.get(1).key, keys.get(i), keys.get(5), 1);
        }
        draft = draft.withCapacities(capacities);
        for (String lease : machineLeases) draft = draft.releaseAfter(lease, true);
        TaskId task = save.execute(draft.edit());
        String first = runtime.start(candidate(task, "Bunt"), null).runId;
        assertEquals(4, run(first).steps.size()); // Other roots are alternatives, not prerequisites.
        String secondCandidate = candidate(task, "Weiß");
        assertEquals(FlowGraphCommands.Status.CAPACITY_UNAVAILABLE, runtime.start(secondCandidate, null).status);
        now += 100; runtime.activateReady();
        String second = runtime.start(secondCandidate, null).runId;
        now += 100; runtime.activateReady();
        String third = runtime.start(candidate(task, "Handtücher"), null).runId;
        String fourth = candidate(task, "Bett");
        now += 100; runtime.activateReady();
        assertEquals(FlowGraphCommands.Status.CAPACITY_UNAVAILABLE, runtime.start(fourth, null).status);
        assertEquals(3, repository.transactions.inTransaction(runs::active).size());
        for (String id : List.of(first, second, third)) runtime.complete(step(id, "Aufhängen").id, null);
        now += 1_000; runtime.activateReady();
        assertEquals(FlowGraphCommands.Status.CAPACITY_UNAVAILABLE, runtime.start(fourth, null).status);
        runtime.complete(step(first, "Abhängen").id, null);
        assertEquals(FlowGraphCommands.Status.CHANGED, runtime.start(fourth, null).status);
    }

    @Test public void failedFinalBookingRollsBackTheWholeStartAndCanBeRetried() {
        FlowEditorDraft draft = FlowEditorDraft.empty().rename("Atomar")
                .addStep("Start", FlowDelayPolicy.fixed(0));
        String root = draft.graph.stepIds.get(0);
        FlowEditorCapacities capacities = draft.capacities.addResource("Platz", 1);
        draft = draft.withCapacities(capacities.addLease(capacities.resources.get(0).key, root, root, 1));
        TaskId task = save.execute(draft.edit());
        String candidate = candidate(task, "Start");
        rejectHeadBooking();
        assertThrows(RuntimeException.class, () -> runtime.start(candidate, null));
        assertEquals(0, repository.today.xp());
        assertTrue(repository.today.allOccurrences().isEmpty());
        assertTrue(repository.transactions.inTransaction(runs::active).isEmpty());
        assertTrue(repository.today.combos().stream().allMatch(combo -> combo.points == 0));
        assertNotNull(repository.flows.findFlowCandidate(candidate));
        database.getOpenHelper().getWritableDatabase().execSQL("DROP TRIGGER reject_graph_head");
        assertEquals(RewardReceipt.Target.HEAD, runtime.start(candidate, null).reward.target);
    }

    @Test public void elapsedDryingWaitCanHideItsOfferAgainWithoutLosingTheRackOrActionIdentity() {
        FlowEditorDraft draft = FlowEditorDraft.empty().rename("Trocknen")
                .addStep("Aufhängen", FlowDelayPolicy.fixed(100))
                .addStep("Abhängen", FlowDelayPolicy.fixed(0));
        String first = draft.graph.stepIds.get(0), last = draft.graph.stepIds.get(1);
        draft = draft.withGraph(draft.graph.join(List.of(first), last));
        FlowEditorCapacities capacity = draft.capacities.addResource("Ständer", 1);
        draft = draft.withCapacities(capacity.addLease(capacity.resources.get(0).key, first, last, 1));
        TaskId task = save.execute(draft.edit());
        String id = runtime.start(candidate(task, "Aufhängen"), null).runId;
        now += 100; runtime.activateReady();
        String actionId = step(id, "Abhängen").id;
        String occurrenceId = repository.today.openOccurrences().get(0).id;
        assertTrue(run(id).canAdjustWait(step(id, "Aufhängen").id));
        assertTrue(runtime.adjustWait(id, step(id, "Aufhängen").waitId(), now + 500));
        assertTrue(projection().sheets.isEmpty());
        assertEquals(FlowResourceState.ACTIVE, run(id).leases.get(0).state);
        assertEquals(FlowGraphCommands.Status.UNCHANGED, runtime.complete(actionId, null).status);
        assertEquals(0, repository.today.xp());
        now += 500; runtime.activateReady();
        assertEquals(actionId, run(id).availableSteps().get(0).id);
        assertEquals(occurrenceId, repository.today.openOccurrences().get(0).id);
        runtime.complete(actionId, null);
        assertFalse(runtime.adjustWait(id, step(id, "Aufhängen").waitId(), now + 500));
    }

    @Test public void elapsedWaitCannotReclaimCapacityAlreadyTakenByAnotherChain() {
        FlowEditorDraft draft = FlowEditorDraft.empty().rename("Kapazität")
                .addStep("Start", FlowDelayPolicy.fixed(100))
                .addStep("Ende", FlowDelayPolicy.fixed(0));
        String first = draft.graph.stepIds.get(0), last = draft.graph.stepIds.get(1);
        draft = draft.withGraph(draft.graph.join(List.of(first), last));
        FlowEditorCapacities capacity = draft.capacities.addResource("Platz", 1);
        capacity = capacity.addLease(capacity.resources.get(0).key, first, first, 1);
        draft = draft.withCapacities(capacity).releaseAfter(capacity.leases.get(0).key, true);
        TaskId task = save.execute(draft.edit());
        String id = runtime.start(candidate(task, "Start"), null).runId;
        now += 100; runtime.activateReady();
        String other = runtime.start(candidate(task, "Start"), null).runId;
        assertNotEquals(id, other);
        assertThrows(IllegalArgumentException.class,
                () -> runtime.adjustWait(id, step(id, "Start").waitId(), now + 500));
        assertEquals(FlowGraphRun.State.AVAILABLE, step(id, "Ende").state);
        now += 100; runtime.activateReady();
        assertTrue(runtime.adjustWait(id, step(id, "Start").waitId(), now + 500));
        assertEquals(FlowResourceState.ACTIVE, run(id).leases.get(0).state);
    }

    @Test public void failedFinalActionRetainsItsOfferAndPreviouslyEarnedTau() {
        FlowEditorDraft draft = FlowEditorDraft.empty().rename("Atomarer Abschluss")
                .addStep("Start", FlowDelayPolicy.fixed(0)).addStep("Ende", FlowDelayPolicy.fixed(0));
        draft = draft.withGraph(draft.graph.join(List.of(draft.graph.stepIds.get(0)), draft.graph.stepIds.get(1)));
        TaskId task = save.execute(draft.edit());
        String runId = runtime.start(candidate(task, "Start"), null).runId;
        String finalStep = step(runId, "Ende").id;
        long earned = run(runId).earnedTau();
        rejectHeadBooking();
        assertThrows(RuntimeException.class, () -> runtime.complete(finalStep, null));
        assertEquals(FlowGraphRun.State.AVAILABLE, step(runId, "Ende").state);
        assertEquals(earned, run(runId).earnedTau());
        assertEquals(1, repository.today.openOccurrences().size());
        assertEquals(0, repository.today.xp());
        database.getOpenHelper().getWritableDatabase().execSQL("DROP TRIGGER reject_graph_head");
        assertEquals(RewardReceipt.Target.HEAD, runtime.complete(finalStep, null).reward.target);
    }

    private void rejectHeadBooking() {
        database.getOpenHelper().getWritableDatabase().execSQL("CREATE TRIGGER reject_graph_head "
                + "BEFORE INSERT ON reward_bookings WHEN NEW.target = 'HEAD' "
                + "BEGIN SELECT RAISE(ABORT, 'injected reward failure'); END");
    }

    @Test public void productionCompositionMaterializesOneStepFlowsWithoutOrdinaryOrGhostSheets() {
        Clock clock = new Clock() {
            @Override public LocalDate today() { return DATE; }
            @Override public LocalTime time() { return LocalTime.NOON; }
        };
        ApplicationUseCaseComposition app = new ApplicationUseCaseComposition(database, clock, () -> now, ids,
                ComboPolicySource.defaults());
        TaskId task = app.flows.saveGraph.execute(FlowEditorDraft.empty().rename("Ein Schritt")
                .addStep("Los", FlowDelayPolicy.fixed(100)).edit());
        assertEquals(TaskKind.FLOW, repository.catalog.findTask(task).kind);
        assertTrue(app.today.materializeDue.execute());
        Dashboard before = app.today.loadDashboard.execute(DATE);
        assertTrue(before.tasks.isEmpty());
        assertTrue(before.flowRuns.isEmpty());
        assertEquals(1, before.flowTaskSheets.size());
        assertEquals(1, before.flowTaskSheets.get(0).entries.size());
        assertFalse(app.today.materializeDue.execute());
        String candidate = before.flowTaskSheets.get(0).entries.get(0).targetId;
        assertEquals(StartFlowCandidateResult.Status.STARTED, app.flows.startFlowCandidate.execute(candidate, null).status);
        Dashboard waiting = app.today.loadDashboard.execute(DATE);
        assertTrue(waiting.tasks.isEmpty());
        assertTrue(waiting.flowTaskSheets.isEmpty());
        assertEquals(1, waiting.flowRuns.size());
        assertFalse(app.today.materializeDue.execute());
        now += 100;
        assertTrue(app.flows.activateReadyFlows.execute());
        FlowTaskSheet sheet = app.today.loadDashboard.execute(DATE).flowTaskSheets.get(0);
        assertEquals(FlowTaskSheet.Entry.Kind.COLLECTION, sheet.entries.get(0).kind);
        assertEquals(RewardReceipt.Target.HEAD, app.flows.runtime.collect(sheet.entries.get(0).targetId).reward.target);
        assertTrue(app.today.loadDashboard.execute(DATE).flowTaskSheets.isEmpty());
        assertFalse(app.today.materializeDue.execute());
    }

    private LoadGraphFlowSheets.Result projection() {
        return new LoadGraphFlowSheets(repository.catalog, repository.steps, repository.today, repository.flows,
                definitions, runs, repository.transactions).execute(DATE);
    }

    private String candidate(TaskId task, String title) {
        String seed = repository.transactions.inTransaction(() -> definitions.find(task)).nodes.values().stream()
                .filter(node -> node.title.equals(title)).findFirst().orElseThrow().id;
        String id = ids.nextId();
        repository.flows.insertFlowCandidate(new FlowCandidate(id, task, seed, "source:" + id,
                DATE, TaskSlot.MORNING, nextId * 1000L, now));
        return id;
    }

    private FlowGraphRun run(String id) { return repository.transactions.inTransaction(() -> runs.find(id).run); }
    private FlowGraphRun.Step step(String id, String title) {
        return run(id).steps.values().stream().filter(step -> step.source.title.equals(title)).findFirst().orElseThrow();
    }
}
