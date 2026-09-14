package de.thonktank.autosecretary.data.local;

import static org.junit.Assert.*;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.room.Room;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import de.thonktank.autosecretary.AppDatabase;
import de.thonktank.autosecretary.domain.usecase.EditStepNote;
import de.thonktank.autosecretary.testing.HistoricalDatabaseFixture;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35}, manifest = Config.NONE)
public final class StepNotesUpgradeTest {
    private final Context context = ApplicationProvider.getApplicationContext();
    private final String name = "notes-upgrade-" + UUID.randomUUID();
    private AppDatabase room;
    private SupportSQLiteDatabase db;

    @After public void close() {
        if (room != null) room.close();
        context.deleteDatabase(name);
    }

    @Test public void upgradeKeepsDependentRowsAndMovesExactLoadsIntoNotes() {
        openUpgraded();
        assertEquals("Sitz 7\nGewicht: 40,125 kg", text("task_steps", "template", "note"));
        assertEquals("Heute\nGewicht: Körpergewicht + 5 lb", text("occurrence_steps", "step", "note"));
        assertEquals("Lauf\nGewicht: Körpergewicht mit 12,345 kg Unterstützung", text("flow_run_steps", "runtime", "note"));
        assertEquals("2", text("repetition_results", "stepId", "step", "COUNT(*)"));
        assertEquals("11", text("repetition_results", "stepId", "step", "MAX(actualRepetitions)"));
        for (String table : Arrays.asList("reward_bookings", "reward_assignments", "timer_sessions",
                "step_transitions", "flow_step_waits", "step_resource_leases", "flow_run_resources", "flow_run_edges"))
            assertEquals(table, 1, count(table));
        assertEquals("77", text("reward_bookings", "booking", "xpDelta"));
        assertEquals("WAITING_TIME", text("flow_run_steps", "runtime", "state"));
        assertEquals("90000", text("flow_run_steps", "runtime", "readyAtEpochMillis"));
        assertEquals("ACTIVE", text("flow_run_resources", "lease", "state"));
        for (String table : Arrays.asList("task_steps", "occurrence_steps", "flow_run_steps", "repetition_results")) {
            for (String column : HistoricalDatabaseFixture.columns(db, table)) {
                assertFalse(column, column.startsWith("assistant") || column.startsWith("plannedLoad")
                        || column.equals("targetRir") || column.equals("rir") || column.equals("loadMilli"));
            }
        }
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM sqlite_master WHERE name IN ('training_adjustments','training_load_requests')"));
        try (Cursor cursor = db.query("PRAGMA foreign_key_check")) { assertFalse(cursor.moveToFirst()); }
    }

    @Test public void editingChangesOnlySelectedExecutionAndTemplateAndSurvivesReopen() {
        openUpgraded();
        EditStepNote edit = editor();
        assertEquals("Heute\nGewicht: Körpergewicht + 5 lb", edit.load("step").note);
        edit.save("step", "42 kg\nLangsam absenken");
        assertEquals("42 kg\nLangsam absenken", edit.load("step").note);
        assertEquals("42 kg\nLangsam absenken", text("task_steps", "template", "note"));
        assertEquals("42 kg\nLangsam absenken", text("flow_run_steps", "runtime", "note"));
        assertEquals("Andere Ausführung", text("occurrence_steps", "other", "note"));
        assertEquals(2, count("repetition_results"));
        assertEquals(1, count("timer_sessions"));
        assertEquals("0", text("occurrence_steps", "step", "done"));
        edit.save("runtime", "45 kg");
        assertEquals("45 kg", text("task_steps", "template", "note"));
        assertEquals("45 kg", text("flow_run_steps", "runtime", "note"));
        assertEquals("90000", text("flow_run_steps", "runtime", "readyAtEpochMillis"));
        room.close(); room = openRoom(); db = room.getOpenHelper().getWritableDatabase();
        assertEquals("45 kg", editor().load("step").note);
        editor().save("step", ""); assertEquals("", editor().load("step").note);
        assertNull(editor().load("missing"));
        assertThrows(IllegalArgumentException.class, () -> editor().save("missing", "x"));
    }

    @Test public void failedTemplateWriteRollsBackTheExecutionNote() {
        openUpgraded();
        String original = editor().load("step").note;
        db.execSQL("CREATE TRIGGER reject_note BEFORE UPDATE OF note ON task_steps BEGIN SELECT RAISE(ABORT,'injected'); END");
        assertThrows(RuntimeException.class, () -> editor().save("step", "Darf nicht bleiben"));
        assertEquals(original, editor().load("step").note);
    }

    @Test public void orphanAndCompletedNotesRemainEditableWithoutRecreatingTemplates() {
        openUpgraded();
        db.execSQL("UPDATE occurrence_steps SET sourceTemplateId='missing-template',done=1 WHERE id='step'");
        editor().save("step", "Erledigt mit 40 kg");
        assertEquals("Erledigt mit 40 kg", editor().load("step").note);
        assertEquals("1", text("occurrence_steps", "step", "done"));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM task_steps WHERE id='missing-template'"));
    }

    @Test public void noteConversionHandlesEveryModeWithoutRoundingOrRepeatedLines() {
        assertEquals("Gewicht: Körpergewicht", LegacyTrainingNote.append("", "BODYWEIGHT", "NONE", null));
        assertEquals("Text", LegacyTrainingNote.append("Text", "UNSPECIFIED", "NONE", null));
        assertEquals("Text", LegacyTrainingNote.append("Text", "EXTERNAL", "KG", null));
        assertEquals("Gewicht: 0 kg", LegacyTrainingNote.append("", "EXTERNAL", "KG", 0L));
        assertEquals("Text\nGewicht: 2,5 lb", LegacyTrainingNote.append("Text\n", "EXTERNAL", "LB", 2500L));
        assertEquals("Gewicht: 40 kg", LegacyTrainingNote.append("Gewicht: 40 kg", "EXTERNAL", "KG", 40000L));
    }

    private EditStepNote editor() {
        RoomTransactionRunner transactions = new RoomTransactionRunner(room);
        return new EditStepNote(new RoomStepRepository(room, transactions), transactions);
    }
    private AppDatabase openRoom() {
        return Room.databaseBuilder(context, AppDatabase.class, name)
                .addMigrations(DatabaseMigrations.from(27)).allowMainThreadQueries().build();
    }
    private void openUpgraded() {
        HistoricalDatabaseFixture.create(context, name, 27, source -> {
            insert(source, "tasks", "id", "task", "title", "Training", "taskKind", "TASK");
            insert(source, "task_steps", "id", "template", "taskId", "task", "text", "Rudern", "note", "Sitz 7",
                    "amountKind", "SETS_REPS", "plannedSets", 3, "plannedReps", 12, "restTimerMode", "INHERIT",
                    "plannedLoadMode", "EXTERNAL", "plannedLoadUnit", "KG", "plannedLoadMilli", 40125L);
            insert(source, "occurrences", "id", "occ", "taskId", "task");
            for (String id : Arrays.asList("step", "other")) insert(source, "occurrence_steps", "id", id,
                    "occurrenceId", "occ", "sourceTemplateId", "template", "flowRunStepId", id.equals("step") ? "runtime" : null, "text", "Rudern",
                    "note", id.equals("step") ? "Heute" : "Andere Ausführung", "amountKind", "SETS_REPS",
                    "plannedSets", 3, "plannedReps", 12, "restTimerMode", "INHERIT",
                    "plannedLoadMode", id.equals("step") ? "BODYWEIGHT_PLUS" : "UNSPECIFIED",
                    "plannedLoadUnit", id.equals("step") ? "LB" : "NONE", "plannedLoadMilli", id.equals("step") ? 5000L : null);
            insert(source, "repetition_results", "stepId", "step", "slotIndex", 0, "actualRepetitions", 10);
            insert(source, "repetition_results", "stepId", "step", "slotIndex", 1, "actualRepetitions", 11);
            insert(source, "reward_bookings", "id", "booking", "occurrenceId", "occ", "occurrenceStepId", "step", "xpDelta", 77);
            insert(source, "reward_assignments", "bookingId", "booking", "occurrenceId", "occ");
            insert(source, "timer_sessions", "id", "timer", "stepId", "step");
            insert(source, "step_transitions", "sourceStepId", "template", "targetStepId", "template");
            insert(source, "flow_step_waits", "stepId", "template");
            insert(source, "capacity_resources", "id", "resource");
            insert(source, "step_resource_leases", "id", "definition-lease", "taskId", "task", "resourceId", "resource",
                    "acquireStepId", "template", "releaseStepId", "template");
            insert(source, "step_flow_runs", "id", "run", "taskId", "task");
            insert(source, "flow_run_steps", "id", "runtime", "runId", "run", "sourceTemplateId", "template", "text", "Rudern",
                    "note", "Lauf", "plannedLoadMode", "ASSISTED_BODYWEIGHT", "plannedLoadUnit", "KG", "plannedLoadMilli", 12345L,
                    "state", "WAITING_TIME", "readyAtEpochMillis", 90000L);
            insert(source, "flow_run_resources", "id", "lease", "runId", "run", "acquireStepId", "runtime",
                    "releaseStepId", "runtime", "state", "ACTIVE");
            insert(source, "flow_run_edges", "runId", "run", "sourceStepId", "runtime", "targetStepId", "runtime");
            insert(source, "training_adjustments", "id", "adjustment", "templateId", "template");
            insert(source, "training_load_requests", "id", "question", "templateId", "template");
        });
        room = openRoom(); db = room.getOpenHelper().getWritableDatabase();
    }

    /** Complete schema fixture without relying on current Java entities for retired columns. */
    private static void insert(SupportSQLiteDatabase db, String table, Object... fields) {
        ContentValues values = new ContentValues();
        try (Cursor columns = db.query("PRAGMA table_info(" + table + ")")) {
            while (columns.moveToNext()) if (columns.getInt(3) != 0 && columns.isNull(4)) {
                if (columns.getString(2).equals("INTEGER")) values.put(columns.getString(1), 0);
                else values.put(columns.getString(1), "");
            }
        }
        for (int i = 0; i < fields.length; i += 2) {
            String key = (String) fields[i]; Object value = fields[i + 1];
            if (value == null) values.putNull(key);
            else if (value instanceof Number) values.put(key, ((Number) value).longValue());
            else values.put(key, value.toString());
        }
        db.insert(table, SQLiteDatabase.CONFLICT_ABORT, values);
    }
    private int count(String table) { return scalarInt("SELECT COUNT(*) FROM " + table); }
    private int scalarInt(String sql) { try (Cursor c = db.query(sql)) { c.moveToFirst(); return c.getInt(0); } }
    private String text(String table, String id, String column) { return text(table, "id", id, column); }
    private String text(String table, String key, String id, String column) {
        try (Cursor c = db.query("SELECT " + column + " FROM " + table + " WHERE " + key + "=?", new Object[]{id})) {
            assertTrue(c.moveToFirst()); return c.getString(0);
        }
    }
}
