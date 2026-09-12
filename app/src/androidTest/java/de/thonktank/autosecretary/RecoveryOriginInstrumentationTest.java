package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import androidx.room.Room;
import androidx.room.migration.Migration;
import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import de.thonktank.autosecretary.data.local.DatabaseMigrations;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Regression for candidate cleanup through the production Room opening path. */
@RunWith(AndroidJUnit4.class)
public final class RecoveryOriginInstrumentationTest {
    private static final String DATABASE = "recovery-origin";
    private static final long READY_AT = 32503680000000L;
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    @Rule public final MigrationTestHelper helper = new MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(), AppDatabase.class);

    @After public void deleteFixture() { context.deleteDatabase(DATABASE); }

    @Test public void exported22RemovesOnlyUnusedChildrenWithoutRecovery() throws Exception {
        verifyUpgrade(22, "none");
    }

    @Test public void organic20PreservesHistoryWithoutCreatingOrphans() throws Exception {
        verifyUpgrade(20, "none");
    }

    @Test public void partialResultsKeepPendingRunAndItsSnapshots() throws Exception { verifyUpgrade(22, "partial"); }
    @Test public void timerKeepsPendingRunAndItsSnapshots() throws Exception { verifyUpgrade(22, "timer"); }
    @Test public void assignedRewardKeepsPendingRunAndItsSnapshots() throws Exception { verifyUpgrade(22, "assigned"); }
    @Test public void bookingLinkedToPendingStepKeepsItsRun() throws Exception { verifyUpgrade(22, "linked-booking"); }
    @Test public void completedOccurrenceKeepsPendingRunAndItsSnapshots() throws Exception { verifyUpgrade(22, "completed"); }
    @Test public void resolvedObligationKeepsPendingRunAndItsSnapshots() throws Exception { verifyUpgrade(22, "resolved"); }

    @Test public void failureAfterCleanupRollsBackEveryOriginalRow() throws Exception {
        DiagnosticFixtures.requireIsolated(context);
        SupportSQLiteDatabase old = helper.createDatabase(DATABASE, 22);
        old.setForeignKeyConstraintsEnabled(true);
        seed(old, 22); seedUntouchedOffer(old);
        old.execSQL("CREATE TRIGGER reject_clean_parent BEFORE DELETE ON step_flow_runs WHEN OLD.id='clean' BEGIN "
                + "SELECT CASE WHEN EXISTS(SELECT 1 FROM flow_run_steps WHERE runId=OLD.id) "
                + "OR EXISTS(SELECT 1 FROM flow_run_resources WHERE runId=OLD.id) "
                + "OR EXISTS(SELECT 1 FROM occurrence_steps WHERE occurrenceId='clean-sheet') "
                + "OR EXISTS(SELECT 1 FROM combo_obligations WHERE occurrenceId='clean-sheet') "
                + "THEN RAISE(ABORT,'children-not-cleaned') ELSE RAISE(ABORT,'injected-after-cleanup') END; END");
        Map<String, List<Map<String, String>>> before = databaseSnapshot(old);
        old.close();
        AppDatabase room = Room.databaseBuilder(context, AppDatabase.class, DATABASE)
                .addMigrations(DatabaseMigrations.from(22)).build();
        RuntimeException failure = null;
        try { room.getOpenHelper().getWritableDatabase(); }
        catch (RuntimeException expected) { failure = expected; }
        finally { room.close(); }
        assertTrue("Injected upgrade failure must occur", failure != null);
        assertTrue("Failure must follow actual child cleanup: " + failure,
                Log.getStackTraceString(failure).contains("injected-after-cleanup"));
        SupportSQLiteOpenHelper reopened = new FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration.builder(context).name(DATABASE)
                        .callback(new SupportSQLiteOpenHelper.Callback(22) {
                            @Override public void onCreate(SupportSQLiteDatabase db) { throw new AssertionError("Source missing"); }
                            @Override public void onUpgrade(SupportSQLiteDatabase db, int from, int to) { throw new AssertionError("Source version changed"); }
                        }).build());
        try {
            SupportSQLiteDatabase db = reopened.getWritableDatabase();
            assertEquals(22, db.getVersion());
            assertEquals(before, databaseSnapshot(db));
            assertEquals(new TreeMap<>(), violations(db));
        } finally { reopened.close(); }
    }

    private void verifyUpgrade(int source, String evidence) throws Exception {
        boolean keepPending = !evidence.equals("none");
        int retainedPending = keepPending ? 1 : 0;
        DiagnosticFixtures.requireIsolated(context);
        SupportSQLiteDatabase old = helper.createDatabase(DATABASE, source);
        // Seed a demonstrably healthy database. This connection is closed before Room opens it.
        old.setForeignKeyConstraintsEnabled(true);
        seed(old, source);
        Map<String, String> bookings = row(old, "reward_bookings", "id='history-booking'");
        Map<String, String> assignments = row(old, "reward_assignments", "bookingId='history-booking'");
        if (source == 20) {
            // PENDING_START did not exist in the schema20 app. Preserve its real data first,
            // then add a later schema22 offer to the physically upgraded database.
            assertEquals(new TreeMap<>(), violations(old));
            old.close();
            old = helper.runMigrationsAndValidate(DATABASE, 22, true,
                    DatabaseMigrations.MIGRATION_20_21, DatabaseMigrations.MIGRATION_21_22);
            old.setForeignKeyConstraintsEnabled(true);
        }
        seedUntouchedOffer(old);
        Map<String, String> evidenceQueries = seedEvidence(old, evidence);
        Map<String, String> pendingOccurrence = row(old, "occurrences", "id='clean-sheet'");
        pendingOccurrence.put("flowExecutionSequence", pendingOccurrence.remove("flowSheetSequence"));
        pendingOccurrence.put("kind", Cursor.FIELD_TYPE_STRING + ":FLOW_STEP");
        pendingOccurrence.put("sourceKey", Cursor.FIELD_TYPE_STRING + ":flow-step:clean:0");
        Map<String, Map<String, String>> evidenceRows = new LinkedHashMap<>();
        for (Map.Entry<String, String> query : evidenceQueries.entrySet())
            evidenceRows.put(query.getKey(), row(old, query.getKey(), query.getValue()));
        assertEquals(1, scalar(old, "PRAGMA foreign_keys"));
        assertEquals(new TreeMap<>(), violations(old));
        assertEquals(bookings, row(old, "reward_bookings", "id='history-booking'"));
        assertEquals(assignments, row(old, "reward_assignments", "bookingId='history-booking'"));
        Map<String, Map<String, String>> retainedSteps = new LinkedHashMap<>();
        for (String id : new String[]{"started-prep", "started-run-step", "waiting-run-step", "waiting-next-step"})
            retainedSteps.put(id, row(old, "flow_run_steps", "id='" + id + "'"));
        if (keepPending) {
            retainedSteps.put("clean-run-step", row(old, "flow_run_steps", "id='clean-run-step'"));
            retainedSteps.put("clean-next-step", row(old, "flow_run_steps", "id='clean-next-step'"));
        }
        old.close();

        Map<String, Map<String, String>> originals = new LinkedHashMap<>();
        Map<String, Integer> observed = new TreeMap<>();
        boolean[] reached = {false};
        Migration[] path = DatabaseMigrations.from(DatabaseContract.PRODUCTION_UPGRADE_SOURCE_VERSION);
        for (int i = 0; i < path.length; i++) {
            Migration migration = path[i];
            if (migration.startVersion != 22) continue;
            path[i] = new Migration(22, 23) {
                @Override public void migrate(SupportSQLiteDatabase db) {
                    reached[0] = true;
                    assertTrue("Production upgrade transaction", db.inTransaction());
                    // Observe the actual Room connection. Never configure its foreign-key mode.
                    assertEquals("Room enables FK only after migration", 0, scalar(db, "PRAGMA foreign_keys"));
                    assertEquals(new TreeMap<>(), violations(db));
                    List<String> columns = columns(db, "flow_run_steps");
                    assertEquals("Physical history must really differ", source == 20,
                            columns.indexOf("note") < columns.indexOf("plannedLoadMode"));
                    originals.put("flow_run_steps", row(db, "flow_run_steps", "id='clean-run-step'"));
                    originals.put("flow_run_resources", row(db, "flow_run_resources", "id='clean-resource'"));
                    originals.put("occurrence_steps", row(db, "occurrence_steps", "id='clean-offered-step'"));
                    migration.migrate(db);
                    observed.putAll(violations(db));
                    assertEquals("Cleanup must not rely on later recovery", new TreeMap<>(), observed);
                    assertEquals(retainedPending, scalar(db, "SELECT COUNT(*) FROM step_flow_runs WHERE id='clean'"));
                    assertEquals(1 - retainedPending, scalar(db, "SELECT COUNT(*) FROM flow_candidates WHERE id='clean'"));
                    for (Map.Entry<String, Map<String, String>> entry : originals.entrySet()) {
                        String id = entry.getValue().get("id").substring(2);
                        if (keepPending) assertEquals("Original semantic columns survive 22->23", entry.getValue(),
                                row(db, entry.getKey(), "id='" + id + "'"));
                        else assertEquals(0, scalar(db, "SELECT COUNT(*) FROM " + entry.getKey() + " WHERE id='" + id + "'"));
                    }
                    assertEquals(bookings, row(db, "reward_bookings", "id='history-booking'"));
                    assertEquals(assignments, row(db, "reward_assignments", "bookingId='history-booking'"));
                }
            };
        }
        AppDatabase room = Room.databaseBuilder(context, AppDatabase.class, DATABASE)
                .addMigrations(path).build();
        try {
            // Unlike runMigrationsAndValidate, this uses Room's real configure/upgrade/open callbacks.
            SupportSQLiteDatabase db = room.getOpenHelper().getWritableDatabase();
            assertTrue("Observed the real 22->23 migration", reached[0]);
            assertEquals(DatabaseContract.VERSION, db.getVersion());
            assertEquals(1, scalar(db, "PRAGMA foreign_keys"));
            assertEquals(new TreeMap<>(), violations(db));
            assertEquals(0, scalar(db, "SELECT COUNT(*) FROM migration_recovery"));
            if (keepPending) {
                assertEquals(pendingOccurrence, row(db, "occurrences", "id='clean-sheet'"));
                Map<String, String> offeredStep = new TreeMap<>(originals.get("occurrence_steps"));
                offeredStep.put("flowRunStepId", Cursor.FIELD_TYPE_STRING + ":clean-run-step");
                assertEquals(offeredStep, row(db, "occurrence_steps", "id='clean-offered-step'"));
                Map<String, String> resource = new TreeMap<>(originals.get("flow_run_resources"));
                resource.remove("acquirePosition"); resource.remove("releasePosition");
                resource.put("acquireStepId", Cursor.FIELD_TYPE_STRING + ":clean-run-step");
                resource.put("releaseStepId", Cursor.FIELD_TYPE_STRING + ":clean-next-step");
                resource.put("releaseAfterWait", Cursor.FIELD_TYPE_INTEGER + ":0");
                assertEquals(resource, row(db, "flow_run_resources", "id='clean-resource'"));
            }
            for (Map.Entry<String, String> query : evidenceQueries.entrySet())
                assertEquals("Retained " + evidence + " evidence", evidenceRows.get(query.getKey()),
                        row(db, query.getKey(), query.getValue()));
            assertEquals(retainedPending, scalar(db, "SELECT COUNT(*) FROM combo_obligations WHERE id='clean-obligation'"));
            assertEquals(bookings, row(db, "reward_bookings", "id='history-booking'"));
            assertEquals(assignments, row(db, "reward_assignments", "bookingId='history-booking'"));
            for (Map.Entry<String, Map<String, String>> step : retainedSteps.entrySet()) {
                Map<String, String> actual = row(db, "flow_run_steps", "id='" + step.getKey() + "'");
                for (Map.Entry<String, String> column : step.getValue().entrySet())
                    assertEquals(step.getKey() + "." + column.getKey(), column.getValue(), actual.get(column.getKey()));
            }
            assertEquals(1, scalar(db, "SELECT COUNT(*) FROM tasks WHERE id='laundry' AND title='Laundry'"));
            assertEquals(1 - retainedPending, scalar(db, "SELECT COUNT(*) FROM flow_candidates WHERE id='clean' AND seedStepId='colors'"));
            assertEquals(2 + retainedPending, scalar(db, "SELECT COUNT(*) FROM step_flow_runs"));
            assertEquals(2, scalar(db, "SELECT nextExecutionSequence FROM step_flow_runs WHERE id='started'"));
            assertEquals(1, scalar(db, "SELECT nextExecutionSequence FROM step_flow_runs WHERE id='waiting'"));
            assertEquals(4 + 2 * retainedPending, scalar(db, "SELECT COUNT(*) FROM flow_run_steps"));
            assertEquals(1, scalar(db, "SELECT COUNT(*) FROM flow_run_steps WHERE id='started-prep' AND state='DONE'"));
            assertEquals(1, scalar(db, "SELECT COUNT(*) FROM flow_run_steps WHERE id='waiting-run-step' AND readyAtEpochMillis=" + READY_AT));
            assertEquals(1, scalar(db, "SELECT COUNT(*) FROM flow_run_resources WHERE id='started-resource' AND state='ACTIVE' "
                    + "AND acquireStepId='started-prep' AND releaseStepId='started-run-step'"));
            assertEquals(2 + retainedPending, scalar(db, "SELECT COUNT(*) FROM occurrences"));
            assertEquals(1, scalar(db, "SELECT COUNT(*) FROM occurrences WHERE id='started-history' AND state='COMPLETED' "
                    + "AND completedOn='2026-08-24' AND flowExecutionSequence=0"));
            assertEquals(1, scalar(db, "SELECT COUNT(*) FROM occurrences WHERE id='started-sheet' AND state='OPEN' AND flowExecutionSequence=1"));
            assertEquals(2 + retainedPending, scalar(db, "SELECT COUNT(*) FROM occurrence_steps"));
            assertEquals(1, scalar(db, "SELECT COUNT(*) FROM occurrence_steps WHERE id='history-step' AND done=1 AND note='history-step note'"));
            Log.i("RecoveryOrigin", "source=" + source + "; target=" + db.getVersion()
                    + "; roomMigrationForeignKeys=0; intermediate=" + observed
                    + "; newRecoveryRows=0; retainedHistory=true; pendingEvidence=" + evidence);
        } finally { room.close(); }
    }

    private static void seed(SupportSQLiteDatabase db, int version) {
        insert(db, "tasks", "id", "laundry", "title", "Laundry", "recurrence", "DAILY", "intervalDays", 1,
                "weekdayMask", 0, "ongoing", 0, "conditionText", "", "conditionDone", 0, "archived", 0,
                "nextDueOn", "2026-08-25", "catalogOrder", 1, "hasCompletedOccurrence", 1,
                "boundKind", "FOREVER", "note", "retained task", "missedOccurrenceMode", "SKIP");
        run(db, "started", "prep", "OFFERED", 1, null, "started-sheet", 2);
        run(db, "waiting", "sheets", "WAITING_TIME", 1, READY_AT, null, 1);
        step(db, version, "started-prep", "started", 0, "prep");
        step(db, version, "started-run-step", "started", 1, "whites");
        step(db, version, "waiting-run-step", "waiting", 0, "sheets");
        step(db, version, "waiting-next-step", "waiting", 1, "fold");
        resource(db, "started", "ACTIVE", 1);
        occurrence(db, "started-history", "started", 0, true);
        occurrence(db, "started-sheet", "started", 1, false);
        offeredStep(db, version, "history-step", "started-history", "prep", true);
        offeredStep(db, version, "started-offered-step", "started-sheet", "whites", false);
        insert(db, "reward_bookings", "id", "history-booking", "transactionId", "history-transaction",
                "occurrenceId", "started-history", "occurrenceStepId", "history-step", "ownerId", "head",
                "kind", "LEGACY_COMPLETION", "target", "HEAD", "xpDelta", 7, "comboPointDelta", 0,
                "bookedOn", "2026-08-24", "plannedXp", 7);
        insert(db, "reward_assignments", "bookingId", "history-booking", "occurrenceId", "started-history");
    }

    private static void seedUntouchedOffer(SupportSQLiteDatabase db) {
        assertEquals(22, db.getVersion());
        run(db, "clean", "colors", "PENDING_START", 0, null, "clean-sheet", 1);
        step(db, 22, "clean-run-step", "clean", 0, "colors");
        step(db, 22, "clean-next-step", "clean", 1, "colors-finish");
        resource(db, "clean", "PLANNED", 1);
        occurrence(db, "clean-sheet", "clean", 0, false);
        offeredStep(db, 22, "clean-offered-step", "clean-sheet", "colors", false);
        insert(db, "combo_obligations", "id", "clean-obligation", "ownerId", "task:laundry", "taskId", "laundry",
                "kind", "TASK", "slot", "MORNING", "scheduledOn", "2026-08-25", "occurrenceId", "clean-sheet", "state", "OPEN");
    }

    private static Map<String, String> seedEvidence(SupportSQLiteDatabase db, String evidence) {
        Map<String, String> queries = new LinkedHashMap<>();
        switch (evidence) {
            case "none": return queries;
            case "partial":
                db.execSQL("UPDATE flow_run_steps SET amountKind='SETS_REPS',plannedSets=3,plannedReps=5 WHERE id='clean-run-step'");
                db.execSQL("UPDATE occurrence_steps SET amountKind='SETS_REPS',plannedSets=3,plannedReps=5,actualRepetitions='5' WHERE id='clean-offered-step'");
                insert(db, "repetition_results", "stepId", "clean-offered-step", "slotIndex", 0, "actualRepetitions", 5,
                        "loadMode", "UNSPECIFIED", "loadUnit", "NONE", "source", "ACTUAL", "safetyFlag", "NONE");
                queries.put("repetition_results", "stepId='clean-offered-step'");
                break;
            case "timer":
                insert(db, "timer_sessions", "id", "evidence-timer", "stepId", "clean-offered-step", "title", "Started timer",
                        "kind", "REST", "state", "PAUSED", "totalSeconds", 60, "remainingMillis", 12345,
                        "targetElapsedRealtime", 0, "targetEpochMillis", 0, "notificationId", 812302, "completionObserved", 0);
                queries.put("timer_sessions", "id='evidence-timer'");
                break;
            case "assigned":
            case "linked-booking":
                insert(db, "reward_bookings", "id", "evidence-booking", "transactionId", "evidence-transaction",
                        "occurrenceId", "started-history", "occurrenceStepId", evidence.equals("assigned") ? "history-step" : "clean-offered-step", "ownerId", "head",
                        "kind", "LEGACY_COMPLETION", "target", "HEAD", "xpDelta", 3, "comboPointDelta", 0,
                        "bookedOn", "2026-08-24", "plannedXp", 3);
                if (evidence.equals("assigned")) {
                    insert(db, "reward_assignments", "bookingId", "evidence-booking", "occurrenceId", "clean-sheet");
                    queries.put("reward_assignments", "bookingId='evidence-booking'");
                }
                queries.put("reward_bookings", "id='evidence-booking'");
                break;
            case "completed":
                db.execSQL("UPDATE occurrences SET state='COMPLETED',completedOn='2026-08-25' WHERE id='clean-sheet'");
                break;
            case "resolved":
                db.execSQL("UPDATE combo_obligations SET state='RESOLVED',resolvedOn='2026-08-25' WHERE id='clean-obligation'");
                break;
            default: throw new AssertionError("Unknown evidence " + evidence);
        }
        queries.put("combo_obligations", "id='clean-obligation'");
        return queries;
    }

    private static void run(SupportSQLiteDatabase db, String id, String seed, String state, int position,
                            Long ready, String sheet, int sequence) {
        insert(db, "step_flow_runs", "id", id, "taskId", "laundry", "seedStepId", seed,
                "sourceKey", "flow:" + id, "scheduledOn", "2026-08-25", "slot", "MORNING", "state", state,
                "currentPosition", position, "readyAtEpochMillis", ready, "currentSheetOccurrenceId", sheet,
                "queueOrder", sequence * 1000000000L, "nextSheetSequence", sequence,
                "createdAtEpochMillis", 20, "updatedAtEpochMillis", 30);
    }

    private static void step(SupportSQLiteDatabase db, int version, String id, String run, int position, String template) {
        ContentValues values = values("id", id, "runId", run, "position", position, "sourceTemplateId", template,
                "text", id + " text", "amountKind", "NONE", "restTimerMode", "OFF", "note", id + " note",
                "delayMode", "FIXED", "defaultDelayMillis", 12345, "lastUsedDelayMillis", 9876,
                "chosenDelayMillis", position == 0 && !run.equals("clean") ? 12345 : null);
        addLoadFields(values, version);
        db.insert("flow_run_steps", SQLiteDatabase.CONFLICT_ABORT, values);
    }

    private static void resource(SupportSQLiteDatabase db, String run, String state, int release) {
        assertTrue("Legacy lease must release after acquisition", release > 0);
        assertEquals("Release step must exist", 1, scalar(db,
                "SELECT COUNT(*) FROM flow_run_steps WHERE runId='" + run + "' AND position=" + release));
        insert(db, "flow_run_resources", "id", run + "-resource", "runId", run, "sourceLeaseId", run + "-lease",
                "resourceId", "dry", "resourceName", "Drying space", "capacityAtCreation", 3, "units", 1,
                "acquirePosition", 0, "releasePosition", release, "state", state,
                "reservedAtEpochMillis", state.equals("ACTIVE") ? 20L : null,
                "activatedAtEpochMillis", state.equals("ACTIVE") ? 20L : null);
    }

    private static void occurrence(SupportSQLiteDatabase db, String id, String run, int sequence, boolean completed) {
        insert(db, "occurrences", "id", id, "taskId", "laundry", "scheduledOn", completed ? "2026-08-24" : "2026-08-25",
                "state", completed ? "COMPLETED" : "OPEN", "sortOrder", sequence,
                "completedOn", completed ? "2026-08-24" : null, "slot", "MORNING", "kind", "FLOW_SHEET",
                "sourceKey", "flow-sheet:" + run + ":" + sequence, "flowRunId", run, "flowSheetSequence", sequence);
    }

    private static void offeredStep(SupportSQLiteDatabase db, int version, String id, String occurrence,
                                    String template, boolean done) {
        ContentValues values = values("id", id, "occurrenceId", occurrence, "position", 0, "text", id + " text",
                "done", done ? 1 : 0, "amountKind", "NONE", "restTimerMode", "OFF", "note", id + " note",
                "actualRepetitions", "", "sourceTemplateId", template, "comboOwnerId", "step:" + template,
                "carryForwardReason", "NONE");
        addLoadFields(values, version);
        db.insert("occurrence_steps", SQLiteDatabase.CONFLICT_ABORT, values);
    }

    private static void addLoadFields(ContentValues values, int version) {
        if (version < 21) return; // Real 20->21 ALTER TABLE supplies these fields for the organic case.
        values.put("plannedLoadMode", "UNSPECIFIED"); values.put("plannedLoadUnit", "NONE"); values.put("targetRir", 2);
    }

    private static void insert(SupportSQLiteDatabase db, String table, Object... pairs) {
        db.insert(table, SQLiteDatabase.CONFLICT_ABORT, values(pairs));
    }

    private static ContentValues values(Object... pairs) {
        ContentValues values = new ContentValues();
        for (int i = 0; i < pairs.length; i += 2) {
            String key = (String) pairs[i]; Object value = pairs[i + 1];
            if (value == null) values.putNull(key);
            else if (value instanceof Number) values.put(key, ((Number) value).longValue());
            else values.put(key, (String) value);
        }
        return values;
    }

    private static int scalar(SupportSQLiteDatabase db, String sql) {
        try (Cursor cursor = db.query(sql)) { assertTrue(cursor.moveToFirst()); return cursor.getInt(0); }
    }

    private static List<String> columns(SupportSQLiteDatabase db, String table) {
        List<String> result = new ArrayList<>();
        try (Cursor cursor = db.query("PRAGMA table_info(" + table + ")")) {
            while (cursor.moveToNext()) result.add(cursor.getString(cursor.getColumnIndexOrThrow("name")));
        }
        return result;
    }

    private static Map<String, Integer> violations(SupportSQLiteDatabase db) {
        Map<String, Integer> result = new TreeMap<>();
        try (Cursor cursor = db.query("PRAGMA foreign_key_check")) {
            while (cursor.moveToNext()) result.put(cursor.getString(0), result.getOrDefault(cursor.getString(0), 0) + 1);
        }
        return result;
    }

    private static Map<String, String> row(SupportSQLiteDatabase db, String table, String predicate) {
        try (Cursor cursor = db.query("SELECT * FROM " + table + " WHERE " + predicate)) {
            assertEquals(1, cursor.getCount()); assertTrue(cursor.moveToFirst());
            return cursorRow(cursor);
        }
    }

    private static Map<String, String> cursorRow(Cursor cursor) {
        Map<String, String> result = new TreeMap<>();
        for (int i = 0; i < cursor.getColumnCount(); i++) {
            int type = cursor.getType(i);
            assertTrue("Fixture has only null/integer/text", type == 0 || type == 1 || type == 3);
            result.put(cursor.getColumnName(i), type + ":" + (cursor.isNull(i) ? "null" : cursor.getString(i)));
        }
        return result;
    }

    private static Map<String, List<Map<String, String>>> databaseSnapshot(SupportSQLiteDatabase db) {
        Map<String, List<Map<String, String>>> result = new TreeMap<>();
        List<String> tables = new ArrayList<>();
        tables.add("sqlite_master");
        try (Cursor cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name")) {
            while (cursor.moveToNext()) tables.add(cursor.getString(0));
        }
        for (String table : tables) {
            List<Map<String, String>> rows = new ArrayList<>();
            try (Cursor cursor = db.query("SELECT * FROM \"" + table.replace("\"", "\"\"") + "\" ORDER BY rowid")) {
                while (cursor.moveToNext()) rows.add(cursorRow(cursor));
            }
            result.put(table, rows);
        }
        return result;
    }

}
