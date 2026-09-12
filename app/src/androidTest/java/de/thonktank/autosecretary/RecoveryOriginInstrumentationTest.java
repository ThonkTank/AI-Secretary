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
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import de.thonktank.autosecretary.data.local.DatabaseMigrations;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Characterizes the historical orphan generator through the production Room opening path. */
@RunWith(AndroidJUnit4.class)
public final class RecoveryOriginInstrumentationTest {
    private static final String DATABASE = "recovery-origin";
    private static final long READY_AT = 32503680000000L;
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    @Rule public final MigrationTestHelper helper = new MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(), AppDatabase.class);

    @After public void deleteFixture() { context.deleteDatabase(DATABASE); }

    @Test public void exported22CreatesOrphansDuringActualRoomUpgrade() throws Exception {
        characterize(22);
    }

    @Test public void organic20CreatesSameOrphansWithoutColumnPermutation() throws Exception {
        characterize(20);
    }

    private void characterize(int source) throws Exception {
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
        assertEquals(1, scalar(old, "PRAGMA foreign_keys"));
        assertEquals(new TreeMap<>(), violations(old));
        assertEquals(bookings, row(old, "reward_bookings", "id='history-booking'"));
        assertEquals(assignments, row(old, "reward_assignments", "bookingId='history-booking'"));
        Map<String, Map<String, String>> retainedSteps = new LinkedHashMap<>();
        for (String id : new String[]{"started-prep", "started-run-step", "waiting-run-step", "waiting-next-step"})
            retainedSteps.put(id, row(old, "flow_run_steps", "id='" + id + "'"));
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
                    Map<String, Integer> expected = new TreeMap<>();
                    expected.put("flow_run_steps", 1);
                    expected.put("flow_run_resources", 1);
                    expected.put("occurrence_steps", 1);
                    assertEquals("Generator before the later recovery", expected, observed);
                    assertEquals(0, scalar(db, "SELECT COUNT(*) FROM step_flow_runs WHERE id='clean'"));
                    assertEquals(1, scalar(db, "SELECT COUNT(*) FROM flow_candidates WHERE id='clean'"));
                    for (Map.Entry<String, Map<String, String>> entry : originals.entrySet()) {
                        String id = entry.getValue().get("id").substring(2);
                        assertEquals("Original semantic columns survive 22->23", entry.getValue(),
                                row(db, entry.getKey(), "id='" + id + "'"));
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
            assertEquals(3, scalar(db, "SELECT COUNT(*) FROM migration_recovery"));
            try (Cursor archive = db.query("SELECT sourceSchema,sourceTable,sourceId,payload FROM migration_recovery")) {
                while (archive.moveToNext()) {
                    assertEquals(24, archive.getInt(0));
                    Map<String, String> original = originals.get(archive.getString(1));
                    assertEquals(original.get("id"), Cursor.FIELD_TYPE_STRING + ":" + archive.getString(2));
                    assertEquals("Every original column and SQLite type archived", original,
                            archivedRow(archive.getString(3)));
                }
            }
            assertEquals(bookings, row(db, "reward_bookings", "id='history-booking'"));
            assertEquals(assignments, row(db, "reward_assignments", "bookingId='history-booking'"));
            for (Map.Entry<String, Map<String, String>> step : retainedSteps.entrySet()) {
                Map<String, String> actual = row(db, "flow_run_steps", "id='" + step.getKey() + "'");
                for (Map.Entry<String, String> column : step.getValue().entrySet())
                    assertEquals(step.getKey() + "." + column.getKey(), column.getValue(), actual.get(column.getKey()));
            }
            assertEquals(1, scalar(db, "SELECT COUNT(*) FROM tasks WHERE id='laundry' AND title='Laundry'"));
            assertEquals(1, scalar(db, "SELECT COUNT(*) FROM flow_candidates WHERE id='clean' AND seedStepId='colors'"));
            assertEquals(2, scalar(db, "SELECT COUNT(*) FROM step_flow_runs"));
            assertEquals(2, scalar(db, "SELECT nextExecutionSequence FROM step_flow_runs WHERE id='started'"));
            assertEquals(1, scalar(db, "SELECT nextExecutionSequence FROM step_flow_runs WHERE id='waiting'"));
            assertEquals(4, scalar(db, "SELECT COUNT(*) FROM flow_run_steps"));
            assertEquals(1, scalar(db, "SELECT COUNT(*) FROM flow_run_steps WHERE id='started-prep' AND state='DONE'"));
            assertEquals(1, scalar(db, "SELECT COUNT(*) FROM flow_run_steps WHERE id='waiting-run-step' AND readyAtEpochMillis=" + READY_AT));
            assertEquals(1, scalar(db, "SELECT COUNT(*) FROM flow_run_resources WHERE id='started-resource' AND state='ACTIVE' "
                    + "AND acquireStepId='started-prep' AND releaseStepId='started-run-step'"));
            assertEquals(2, scalar(db, "SELECT COUNT(*) FROM occurrences"));
            assertEquals(1, scalar(db, "SELECT COUNT(*) FROM occurrences WHERE id='started-history' AND state='COMPLETED' "
                    + "AND completedOn='2026-08-24' AND flowExecutionSequence=0"));
            assertEquals(1, scalar(db, "SELECT COUNT(*) FROM occurrences WHERE id='started-sheet' AND state='OPEN' AND flowExecutionSequence=1"));
            assertEquals(2, scalar(db, "SELECT COUNT(*) FROM occurrence_steps"));
            assertEquals(1, scalar(db, "SELECT COUNT(*) FROM occurrence_steps WHERE id='history-step' AND done=1 AND note='history-step note'"));
            Log.i("RecoveryOrigin", "source=" + source + "; target=" + db.getVersion()
                    + "; roomMigrationForeignKeys=0; intermediate=" + observed
                    + "; archivedOriginalRows=3; retainedHistory=true");
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
        resource(db, "clean", "PLANNED", 0);
        occurrence(db, "clean-sheet", "clean", 0, false);
        offeredStep(db, 22, "clean-offered-step", "clean-sheet", "colors", false);
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
        values.put("plannedLoadMode", "NONE"); values.put("plannedLoadUnit", "KG"); values.put("targetRir", 0);
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
        Map<String, String> result = new TreeMap<>();
        try (Cursor cursor = db.query("SELECT * FROM " + table + " WHERE " + predicate)) {
            assertEquals(1, cursor.getCount()); assertTrue(cursor.moveToFirst());
            for (int i = 0; i < cursor.getColumnCount(); i++) {
                int type = cursor.getType(i);
                assertTrue("Fixture has only null/integer/text", type == 0 || type == 1 || type == 3);
                result.put(cursor.getColumnName(i), type + ":" + (cursor.isNull(i) ? "null" : cursor.getString(i)));
            }
        }
        return result;
    }

    private static Map<String, String> archivedRow(String payload) throws Exception {
        JSONObject object = new JSONObject(payload);
        assertEquals(1, object.getInt("formatVersion"));
        JSONArray columns = object.getJSONArray("columns");
        Map<String, String> result = new TreeMap<>();
        for (int i = 0; i < columns.length(); i++) {
            JSONArray column = columns.getJSONArray(i);
            result.put(column.getString(0), column.getInt(1) + ":" + column.get(2));
        }
        return result;
    }
}
