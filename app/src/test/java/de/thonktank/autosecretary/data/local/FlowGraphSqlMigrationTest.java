package de.thonktank.autosecretary.data.local;

import static org.junit.Assert.*;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.core.app.ApplicationProvider;
import de.thonktank.autosecretary.domain.model.*;
import de.thonktank.autosecretary.domain.usecase.FlowGraphExecution;
import de.thonktank.autosecretary.testing.ExportedRoomSchemaFixture;
import de.thonktank.autosecretary.testing.HistoricalDatabaseFixture;
import java.time.LocalDate;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Executes the prepared SQL cutover on the real exported source schema, including API 26. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35}, manifest = Config.NONE)
public final class FlowGraphSqlMigrationTest {
    private SupportSQLiteOpenHelper helper;
    private SupportSQLiteDatabase db;
    private final StepFlowEntityMapper mapper = new StepFlowEntityMapper();
    private static final LocalDate DATE = LocalDate.of(2026, 9, 11);

    @Before public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        helper = new FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
                .callback(new SupportSQLiteOpenHelper.Callback(24) {
                    @Override public void onConfigure(SupportSQLiteDatabase database) { database.setForeignKeyConstraintsEnabled(true); }
                    @Override public void onCreate(SupportSQLiteDatabase database) { ExportedRoomSchemaFixture.create(database, 24); }
                    @Override public void onUpgrade(SupportSQLiteDatabase database, int oldVersion, int newVersion) { throw new AssertionError(); }
                }).build());
        db = helper.getWritableDatabase();
        task("task"); task("normal");
        for (int i = 0; i < 3; i++) {
            TaskStepEntity step = new TaskStepEntity("template" + i, "task", i, "Schritt " + i);
            step.activationKind = i == 0 ? "SCHEDULED" : "FOLLOW_UP";
            step.intervalDays = i == 0 ? 5 : 0;
            insert("task_steps", step);
        }
        insert("step_transitions", new StepTransitionEntity("template0", "template1", "REMEMBER_LAST", 25, 50L));
        insert("step_transitions", new StepTransitionEntity("template1", "template2", "FIXED", 0, null));
        insert("capacity_resources", new CapacityResourceEntity("rack", "Wäscheständer", "wäscheständer", 3));
        insert("step_resource_leases", new StepResourceLeaseEntity("definition-lease", "task", "template0", "template2", "rack", 1));
        insert("flow_candidates", new FlowCandidateEntity("candidate", "task", "template0", "not-started", DATE.toString(), "MORNING", 456, 123));
        insert("flow_task_sheet_placements", new FlowTaskSheetPlacementEntity("placement", "task", "MORNING", DATE.plusDays(1).toString(), 987));
    }

    @After public void tearDown() { helper.close(); }

    @Test public void timedWaitPreservesEveryPayloadAndConvertsOnlyCursorAndLeaseEndpoints() {
        run("run", StepFlowRunState.WAITING_TIME, 1, FlowResourceState.ACTIVE, 0);
        occurrence("paid", "run", "COMPLETED", "template0", true);
        booking("earned", "paid", "paid-step", "VESSEL", 4, null);
        booking("harvest", "paid", null, "HEAD", 40, null);
        Map<String, String> unchanged = unchangedRows();
        migrate();
        FlowGraphRun migrated = read("run");
        assertEquals(4L, migrated.alreadyPaidTau); // not the multiplied HEAD value 40
        assertEquals(4L, migrated.earnedTau());
        assertEquals(Arrays.asList("run-s0", "run-s1", "run-s2"), migrated.graph.stepIds);
        FlowGraphRun.Step first = migrated.steps.get("run-s0");
        assertEquals(FlowGraphRun.State.WAITING_TIME, first.state);
        assertEquals(Long.valueOf(999), first.readyAtEpochMillis);
        assertEquals(Long.valueOf(50), first.chosenDelayMillis);
        assertEquals("Frozen note 0", first.source.note);
        assertEquals(FlowDelayPolicy.Mode.REMEMBER_LAST, first.source.waitAfter.mode);
        assertEquals(4, first.source.prescription.targetRir());
        assertEquals(Long.valueOf(12345), first.source.prescription.plannedLoad().milliUnits);
        assertEquals("run-s0", scalar("SELECT acquireStepId FROM flow_run_resources"));
        assertEquals("run-s2", scalar("SELECT releaseStepId FROM flow_run_resources"));
        assertEquals("0", scalar("SELECT releaseAfterWait FROM flow_run_resources"));
        assertEquals("100", scalar("SELECT reservedAtEpochMillis FROM flow_run_resources"));
        assertEquals("110", scalar("SELECT activatedAtEpochMillis FROM flow_run_resources"));
        assertEquals("Wäscheständer zur Startzeit", scalar("SELECT resourceName FROM flow_run_resources"));
        assertEquals("7", scalar("SELECT capacityAtCreation FROM flow_run_resources"));
        assertEquals("1234", scalar("SELECT queueOrder FROM step_flow_runs"));
        assertEquals("run-s0", scalar("SELECT flowRunStepId FROM occurrence_steps WHERE id='paid-step'"));
        assertEquals(unchanged, unchangedRows());
        assertFalse(HistoricalDatabaseFixture.columns(db, "step_flow_runs").contains("currentPosition"));
        assertFalse(HistoricalDatabaseFixture.columns(db, "step_flow_runs").contains("currentExecutionOccurrenceId"));
        assertFalse(HistoricalDatabaseFixture.columns(db, "flow_run_resources").contains("acquirePosition"));
        FlowGraphExecution execution = new FlowGraphExecution(() -> { throw new AssertionError(); });
        assertEquals("run-s1", execution.settle(migrated, 999,
                new FlowGraphExecution.Capacity(Map.of("rack", 3), Collections.emptyMap())).availableSteps().get(0).id);
    }

    @Test public void migrationRetainsCandidatesPlacementsAndMakesDefinitionWaitsNodeOwned() {
        Map<String, String> unchanged = unchangedRows();
        migrate();
        assertEquals(unchanged, unchangedRows());
        assertEquals("FLOW", scalar("SELECT taskKind FROM tasks WHERE id='task'"));
        assertEquals("TASK", scalar("SELECT taskKind FROM tasks WHERE id='normal'"));
        assertEquals("REMEMBER_LAST", scalar("SELECT mode FROM flow_step_waits WHERE stepId='template0'"));
        assertEquals("50", scalar("SELECT lastUsedDelayMillis FROM flow_step_waits WHERE stepId='template0'"));
        assertEquals("0", scalar("SELECT defaultDelayMillis FROM flow_step_waits WHERE stepId='template2'"));
        assertEquals("5", scalar("SELECT intervalDays FROM task_steps WHERE id='template0'"));
        assertEquals("0", scalar("SELECT COUNT(*) FROM step_flow_runs"));
        // A second outgoing edge is now representable; a repeated edge remains unique.
        db.execSQL("INSERT INTO step_transitions(sourceStepId,targetStepId) VALUES ('template0','template2')");
        assertEquals("3", scalar("SELECT COUNT(*) FROM step_transitions"));
        assertThrows(android.database.SQLException.class, () -> db.execSQL(
                "INSERT INTO step_transitions(sourceStepId,targetStepId) VALUES ('template0','template2')"));
    }

    @Test public void allLegacyStatesTranslateWithoutCreatingOffersOrPayments() {
        run("offered", StepFlowRunState.OFFERED, 1, FlowResourceState.ACTIVE, 0);
        run("resource", StepFlowRunState.WAITING_RESOURCE, 1, FlowResourceState.ACTIVE, 0);
        run("postponed", StepFlowRunState.WAITING_TIME, 1, FlowResourceState.RESERVED, 1);
        run("cancelled", StepFlowRunState.CANCELLED, 1, FlowResourceState.RELEASED, 0);
        run("done", StepFlowRunState.COMPLETED, 2, FlowResourceState.RELEASED, 0);
        migrate();
        assertEquals(FlowGraphRun.State.AVAILABLE, read("offered").steps.get("offered-s1").state);
        assertEquals(FlowGraphRun.State.WAITING_RESOURCE, read("resource").steps.get("resource-s1").state);
        assertEquals(FlowResourceState.RESERVED, read("postponed").leases.get(0).state);
        assertTrue(read("cancelled").cancelled);
        assertTrue(read("cancelled").availableSteps().isEmpty());
        assertTrue(read("done").collected); // no earned amount and no new payout
        assertEquals("0", scalar("SELECT COUNT(*) FROM reward_bookings"));
        assertEquals("1", scalar("SELECT COUNT(*) FROM flow_candidates"));
    }

    @Test public void partialSetsCarryForwardAndReversalsCountEachLedgerEntryExactlyOnce() {
        run("run", StepFlowRunState.OFFERED, 1, FlowResourceState.ACTIVE, 0);
        occurrence("first", "run", "COMPLETED", "template0", true);
        occurrence("partial", "run", "HARVESTED_WITH_MISSED_STEPS", "template1", false);
        occurrence("carried", "run", "OPEN", "template1", false);
        db.execSQL("UPDATE occurrence_steps SET originOccurrenceId='partial' WHERE id='carried-step'");
        booking("first-earned", "first", "first-step", "VESSEL", 4, null);
        booking("first-harvest", "first", null, "HEAD", 40, null);
        booking("partial-earned", "partial", "partial-step", "VESSEL", 3, null);
        booking("partial-correction", "partial", "partial-step", "VESSEL", -1, null);
        booking("partial-harvest", "partial", null, "HEAD", 20, null);
        booking("carried-earned", "carried", "carried-step", "VESSEL", 1, null);
        booking("carried-undo", "carried", "carried-step", "VESSEL", -1, "carried-earned");
        Map<String, String> before = unchangedRows();
        migrate();
        FlowGraphRun run = read("run");
        assertEquals(6, run.earnedTau());
        assertEquals(6, run.alreadyPaidTau);
        assertEquals(2, run.steps.get("run-s1").earnedTau);
        assertEquals("run-s1", scalar("SELECT flowRunStepId FROM occurrence_steps WHERE id='carried-step'"));
        assertEquals(before, unchangedRows());
    }

    @Test public void reversedHarvestLeavesPrincipalUnpaidAndLaterReharvestPaysOnlyOnce() {
        run("run", StepFlowRunState.COMPLETED, 2, FlowResourceState.RELEASED, 0);
        occurrence("first", "run", "COMPLETED", "template0", true);
        booking("earned", "first", "first-step", "VESSEL", 4, null);
        booking("harvest", "first", null, "HEAD", 40, null);
        booking("undo", "first", null, "HEAD", -40, "harvest");
        migrate();
        FlowGraphRun run = read("run");
        assertEquals(0, run.alreadyPaidTau);
        assertEquals(4, run.uncollectedTau());
        assertTrue(run.collectionAvailable());
    }

    @Test public void activeReharvestAndReportingAssignmentsDoNotMultiplyPaidPrincipal() {
        run("run", StepFlowRunState.COMPLETED, 2, FlowResourceState.RELEASED, 0);
        occurrence("origin", "run", "OPEN", "template0", true);
        occurrence("report", "run", "COMPLETED", "template1", true);
        booking("earned", "origin", "origin-step", "VESSEL", 4, null);
        insert("reward_assignments", new RewardAssignmentEntity("earned", "report"));
        booking("harvest", "report", null, "HEAD", 40, null);
        booking("undo", "report", null, "HEAD", -40, "harvest");
        booking("reharvest", "report", null, "HEAD", 80, null);
        migrate();
        FlowGraphRun run = read("run");
        assertEquals(4, run.alreadyPaidTau);
        assertEquals(4, run.steps.get("run-s0").earnedTau);
        assertTrue(run.collected);
    }

    @Test public void invalidLegacyCursorRollsBackWithoutAnyPersistedSchemaChange() {
        run("run", StepFlowRunState.WAITING_TIME, 0, FlowResourceState.PLANNED, 0);
        Map<String, String> before = unchangedRows();
        assertThrows(IllegalArgumentException.class, this::migrate);
        assertEquals(before, unchangedRows());
        assertFalse(HistoricalDatabaseFixture.columns(db, "tasks").contains("taskKind"));
        assertTrue(HistoricalDatabaseFixture.columns(db, "step_flow_runs").contains("currentPosition"));
        assertEquals("0", scalar("SELECT currentPosition FROM step_flow_runs"));
    }

    @Test public void snapshotReadAndMigrationRequireAnAtomicTransaction() {
        assertThrows(IllegalStateException.class, () -> new FlowGraphMigration25().migrate(db));
        assertThrows(IllegalStateException.class, () -> new FlowGraphSnapshotReader(db).find("run"));
    }

    @Test public void migratedWaitCanBeExtendedAndSavedWithoutRewritingResourceHistory() {
        run("run", StepFlowRunState.WAITING_TIME, 1, FlowResourceState.ACTIVE, 0);
        migrate();
        FlowGraphRun original = read("run");
        FlowGraphRun extended = engine().adjustWait(original, "flow-wait:run-s0", 2500, 500, capacity()).run;
        write(extended, 500);
        FlowGraphRun loaded = read("run");
        assertEquals(Long.valueOf(2500), loaded.steps.get("run-s0").readyAtEpochMillis);
        assertEquals(Long.valueOf(50), loaded.steps.get("run-s0").chosenDelayMillis);
        assertEquals("110", scalar("SELECT activatedAtEpochMillis FROM flow_run_resources"));
        assertEquals("7", scalar("SELECT capacityAtCreation FROM flow_run_resources"));
        assertEquals("Wäscheständer zur Startzeit", scalar("SELECT resourceName FROM flow_run_resources"));
    }

    @Test public void newParallelSnapshotRoundTripsAndWaitsRemainIndependentlyAddressable() {
        migrate();
        FlowGraphRun run = newParallelRun();
        insertRun(run);
        FlowGraphRun loaded = read(run.id);
        assertEquals(2, loaded.availableSteps().size());
        assertEquals(new HashSet<>(run.graph.links), new HashSet<>(loaded.graph.links));
        String first = loaded.availableSteps().get(0).id;
        String second = loaded.availableSteps().get(1).id;
        loaded = engine().complete(loaded, first, null, 2, 100, capacity()).run;
        write(loaded, 100);
        loaded = engine().complete(read(run.id), second, null, 3, 100, capacity()).run;
        write(loaded, 100);
        loaded = engine().adjustWait(read(run.id), "flow-wait:" + first, 900, 150, capacity()).run;
        write(loaded, 150);
        assertEquals(Long.valueOf(900), read(run.id).steps.get(first).readyAtEpochMillis);
        assertEquals(Long.valueOf(300), read(run.id).steps.get(second).readyAtEpochMillis);
        loaded = engine().settle(read(run.id), 900, capacity());
        assertEquals(1, loaded.availableSteps().size());
        FlowGraphExecution.Change completed = engine().complete(loaded, loaded.availableSteps().get(0).id,
                null, 4, 900, capacity());
        assertEquals(10, completed.paymentTau);
        write(completed.run, 900);
        assertTrue(read(run.id).collected);
        assertFalse(engine().collect(read(run.id)).changed);
        assertEquals("900", scalar("SELECT releasedAtEpochMillis FROM flow_run_resources"));
        assertEquals("100", scalar("SELECT activatedAtEpochMillis FROM flow_run_resources"));
        assertEquals("3", scalar("SELECT capacityAtCreation FROM flow_run_resources"));
    }

    @Test public void snapshotUpdatesRejectDefinitionEditsAndLeaveTheFrozenPayloadUntouched() {
        migrate();
        FlowGraphRun run = newParallelRun(); insertRun(run);
        List<FlowGraphRun.Step> changedSteps = new ArrayList<>();
        for (FlowGraphRun.Step step : run.steps.values()) {
            FlowGraphDefinition.Node edited = new FlowGraphDefinition.Node(step.source.id,
                    "Edit during run", step.source.prescription, step.source.note, step.source.waitAfter);
            changedSteps.add(new FlowGraphRun.Step(step.id, edited, step.state, step.chosenDelayMillis,
                    step.readyAtEpochMillis, step.actionAtEpochMillis, step.earnedTau));
        }
        FlowGraphRun edited = new FlowGraphRun(run.id, run.taskId, run.startStepId, run.graph, changedSteps,
                run.leases, run.alreadyPaidTau, run.collected);
        assertThrows(IllegalArgumentException.class, () -> write(edited, 200));
        assertEquals("Start", read(run.id).steps.get(run.startStepId).source.title);
    }

    @Test public void duplicateSourceCannotReplaceAnExistingRunOrAnyOfItsSteps() {
        migrate();
        FlowGraphRun first = newParallelRun(); insertRun(first);
        FlowGraphRun duplicate = newParallelRun();
        assertThrows(android.database.SQLException.class, () -> insertRun(duplicate));
        assertEquals("1", scalar("SELECT COUNT(*) FROM step_flow_runs"));
        assertEquals("4", scalar("SELECT COUNT(*) FROM flow_run_steps"));
        assertNotNull(read(first.id));
        assertNull(read(duplicate.id));
    }

    @Test public void failedPaymentTransactionRestoresStepClaimsAndCollectionMarkerTogether() {
        run("run", StepFlowRunState.OFFERED, 2, FlowResourceState.ACTIVE, 0);
        migrate();
        FlowGraphRun before = read("run");
        FlowGraphExecution.Change completed = engine().complete(before, "run-s2", null, 4, 1000, capacity());
        assertTrue(completed.run.collected);
        assertThrows(IllegalStateException.class, () -> {
            db.beginTransaction();
            try {
                new FlowGraphSnapshotWriter(db).update(completed.run, 1000);
                throw new IllegalStateException("Simulated ledger write failure");
            } finally { db.endTransaction(); }
        });
        assertFalse(read("run").collected);
        assertEquals(FlowGraphRun.State.AVAILABLE, read("run").steps.get("run-s2").state);
        assertEquals("ACTIVE", scalar("SELECT state FROM flow_run_resources"));
        assertEquals("110", scalar("SELECT activatedAtEpochMillis FROM flow_run_resources"));
    }

    @Test public void newSnapshotsPreserveAllAmountKindsTrainingAndRememberedWaits() {
        migrate();
        int index = 0;
        for (StepAmount amount : Arrays.asList(StepAmount.none(), StepAmount.repetitions(12),
                StepAmount.duration(120), StepAmount.setsReps(3, 8))) {
            StepPrescription prescription = amount instanceof StepAmount.SetsReps
                    ? StepPrescription.restore(amount, RestTimerPolicy.fromStorage("CUSTOM", 75),
                            ResistanceLoad.restore("EXTERNAL", "LB", 12345L), 4)
                    : StepPrescription.forAmount(amount);
            FlowGraphDefinition.Node node = new FlowGraphDefinition.Node("template0", "Payload " + index,
                    prescription, "Preserved note", new FlowDelayPolicy(FlowDelayPolicy.Mode.REMEMBER_LAST, 300, 600L));
            FlowGraphDefinition definition = new FlowGraphDefinition(TaskId.of("task"),
                    new FlowTileGraph(Collections.singletonList("template0"), Collections.emptyList()),
                    Collections.singletonList(node), Collections.emptyList());
            FlowGraphRun run = engine().settle(engine().snapshot(definition, "template0"), 100, capacity());
            run = engine().complete(run, run.startStepId, 900L, 1, 100, capacity()).run;
            db.beginTransaction();
            try {
                new FlowGraphSnapshotWriter(db).insert(new FlowCandidate("candidate-" + index, TaskId.of("task"),
                        "template0", "source-" + index++, DATE, TaskSlot.MORNING, 123, 100), run, 100);
                db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
            FlowGraphRun.Step restored = read(run.id).steps.get(run.startStepId);
            assertEquals(prescription, restored.source.prescription);
            assertEquals("Preserved note", restored.source.note);
            assertEquals(300, restored.source.waitAfter.defaultDelayMillis);
            assertEquals(Long.valueOf(600), restored.source.waitAfter.lastUsedDelayMillis);
            assertEquals(Long.valueOf(900), restored.chosenDelayMillis);
        }
    }

    private FlowGraphRun newParallelRun() {
        List<String> ids = Arrays.asList("template0", "template1", "template2", "join");
        List<FlowTileGraph.Link> edges = Arrays.asList(new FlowTileGraph.Link("template0", "template1"),
                new FlowTileGraph.Link("template0", "template2"), new FlowTileGraph.Link("template1", "join"),
                new FlowTileGraph.Link("template2", "join"));
        List<FlowGraphDefinition.Node> nodes = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) nodes.add(new FlowGraphDefinition.Node(ids.get(i),
                i == 0 ? "Start" : "Step " + i, StepPrescription.forAmount(StepAmount.none()), "",
                FlowDelayPolicy.fixed(i == 1 ? 100 : i == 2 ? 200 : 0)));
        FlowGraphDefinition definition = new FlowGraphDefinition(TaskId.of("task"), new FlowTileGraph(ids, edges), nodes,
                Collections.singletonList(new FlowGraphDefinition.Lease("binding", "rack", "template0", "join", 1, false)));
        FlowGraphRun run = engine().snapshot(definition, "template0");
        run = engine().settle(run, 100, capacity());
        return engine().complete(run, run.startStepId, null, 1, 100, capacity()).run;
    }

    private static FlowGraphExecution engine() { return new FlowGraphExecution(() -> UUID.randomUUID().toString()); }
    private static FlowGraphExecution.Capacity capacity() {
        return new FlowGraphExecution.Capacity(Map.of("rack", 3), Collections.emptyMap());
    }
    private void insertRun(FlowGraphRun run) {
        db.beginTransaction();
        try {
            new FlowGraphSnapshotWriter(db).insert(new FlowCandidate("candidate", TaskId.of("task"), "template0",
                    "not-started", DATE, TaskSlot.MORNING, 456, 123), run, 100);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    private void write(FlowGraphRun run, long now) {
        db.beginTransaction();
        try { new FlowGraphSnapshotWriter(db).update(run, now); db.setTransactionSuccessful(); }
        finally { db.endTransaction(); }
    }

    private void task(String id) {
        Task task = Task.create(TaskId.of(id), TaskDefinition.basic("Aufgabe " + id, TaskSlot.MORNING,
                Recurrence.DAILY, 1, 0, Collections.emptyList()), DATE, 45);
        insert("tasks", new TaskEntityMapper().toEntity(task));
    }

    private void run(String id, StepFlowRunState state, int position, FlowResourceState resourceState, int acquire) {
        StepFlowRun header = new StepFlowRun(id, TaskId.of("task"), "template0", "source-" + id, DATE, TaskSlot.MORNING,
                state, position, state == StepFlowRunState.WAITING_TIME ? 999L : null, null, 1234, 7, 100, 200);
        insert("step_flow_runs", mapper.toEntity(header));
        for (int i = 0; i < 3; i++) {
            StepPrescription prescription = StepPrescription.restore(StepAmount.setsReps(3, 10), RestTimerPolicy.fromStorage("OFF", null),
                    ResistanceLoad.restore("EXTERNAL", "LB", 12345L), 4);
            insert("flow_run_steps", mapper.toEntity(FlowRunStepSnapshot.rehydrate(id + "-s" + i, id, i,
                    "template" + i, "Frozen " + i, prescription, "Frozen note " + i,
                    i == 2 ? null : FlowDelayPolicy.rememberLast(25), i == 0 ? 50L : null)));
        }
        insert("flow_run_resources", mapper.toEntity(new FlowRunResourceSnapshot(id + "-lease", id, "definition-lease",
                "rack", "Wäscheständer zur Startzeit", 7, 1, acquire, 2, resourceState,
                resourceState == FlowResourceState.PLANNED ? null : 100L,
                resourceState == FlowResourceState.ACTIVE ? 110L : null,
                resourceState == FlowResourceState.RELEASED ? 200L : null)));
    }

    private void occurrence(String id, String runId, String state, String template, boolean done) {
        insert("occurrences", new OccurrenceEntity(id, "task", DATE.toString(), state, 51,
                state.equals("OPEN") ? null : DATE.toString(), "MORNING", "FLOW_STEP", "occ-" + id, runId, 1));
        insert("occurrence_steps", new OccurrenceStepEntity(id + "-step", id, 0, "Ausführung", done,
                "NONE", null, null, null, "Notiz", "", template, "step:" + template));
    }

    private void booking(String id, String occurrence, String step, String target, int xp, String reverses) {
        insert("reward_bookings", new RewardBookingEntity(id, "tx-" + id, occurrence, step,
                "owner", reverses != null ? "REVERSAL" : target.equals("HEAD") ? "ROUTINE_HARVEST" : "STEP_EARNED",
                target, xp, 0, DATE.toString(), reverses, null));
    }

    private void migrate() {
        db.beginTransaction();
        try { new FlowGraphMigration25().migrate(db); db.setTransactionSuccessful(); }
        finally { db.endTransaction(); }
    }

    private FlowGraphRun read(String id) {
        db.beginTransaction();
        try { return new FlowGraphSnapshotReader(db).find(id); }
        finally { db.endTransaction(); }
    }

    private String scalar(String query) {
        try (Cursor cursor = db.query(query)) { assertTrue(query, cursor.moveToFirst()); return cursor.getString(0); }
    }

    private Map<String, String> unchangedRows() {
        Map<String, String> rows = new LinkedHashMap<>();
        for (String table : Arrays.asList("tasks", "task_steps", "occurrences", "occurrence_steps", "reward_bookings",
                "reward_assignments", "capacity_resources", "flow_candidates", "flow_task_sheet_placements")) {
            String columns = String.join(",", ExportedRoomSchemaFixture.columns(24, table));
            StringBuilder values = new StringBuilder();
            try (Cursor cursor = db.query("SELECT " + columns + " FROM " + table + " ORDER BY 1")) {
                while (cursor.moveToNext()) {
                    for (int i = 0; i < cursor.getColumnCount(); i++) values.append(cursor.getString(i)).append('|');
                    values.append('\n');
                }
            }
            rows.put(table, values.toString());
        }
        return rows;
    }

    /** Fixture writes use the frozen v24 column list, never Java reflection order. */
    private void insert(String table, Object entity) {
        ContentValues values = new ContentValues();
        try {
            for (String column : ExportedRoomSchemaFixture.columns(24, table)) {
                Object value = entity.getClass().getField(column.equals("actualRepetitions")
                        ? "legacyActualRepetitions" : column).get(entity);
                if (value == null) values.putNull(column);
                else if (value instanceof Boolean) values.put(column, (Boolean) value);
                else if (value instanceof Number) values.put(column, ((Number) value).longValue());
                else values.put(column, (String) value);
            }
        } catch (ReflectiveOperationException invalid) { throw new AssertionError(invalid); }
        db.insert(table, SQLiteDatabase.CONFLICT_ABORT, values);
    }
}
