package de.thonktank.autosecretary.data.local;

import android.database.Cursor;
import android.util.Base64;
import android.util.Log;
import androidx.sqlite.db.SupportSQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONException;

/** Recovery of snapshots whose run/occurrence parent is absent at the schema-24 boundary. */
public final class OrphanFlowRecovery {
    private static final String[] TABLES = {"flow_run_steps", "flow_run_resources", "occurrence_steps",
            "repetition_results", "timer_sessions", "reward_bookings", "reward_assignments", "combo_obligations"};
    private OrphanFlowRecovery() { }

    static void createArchive(SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS migration_recovery (sourceSchema INTEGER NOT NULL, "
                + "sourceTable TEXT NOT NULL, sourceId TEXT NOT NULL, reason TEXT NOT NULL, "
                + "payload TEXT NOT NULL, PRIMARY KEY(sourceSchema,sourceTable,sourceId))");
    }

    static void recover(SupportSQLiteDatabase db) {
        if (!db.inTransaction()) throw new IllegalStateException("Recovery requires an upgrade transaction");
        createArchive(db);
        // Capture the full dependency closure first. This works with foreign keys both ON and OFF;
        // no cascade may remove a child before that child's original payload has been archived.
        db.execSQL("CREATE TEMP TABLE _recovery_rows (sourceTable TEXT NOT NULL, rowId INTEGER NOT NULL, "
                + "PRIMARY KEY(sourceTable,rowId))");
        mark(db, "flow_run_steps", missing("step_flow_runs", "runId"));
        mark(db, "flow_run_resources", missing("step_flow_runs", "runId"));
        mark(db, "occurrence_steps", missing("occurrences", "occurrenceId"));
        mark(db, "repetition_results", missing("occurrence_steps", "stepId")
                + " OR " + selectedParent("occurrence_steps", "stepId"));
        mark(db, "timer_sessions", missing("occurrence_steps", "stepId")
                + " OR " + selectedParent("occurrence_steps", "stepId"));
        mark(db, "reward_bookings", missing("occurrences", "occurrenceId")
                + " OR " + selectedParent("occurrence_steps", "occurrenceStepId"));
        mark(db, "reward_assignments", missing("occurrences", "occurrenceId")
                + " OR " + selectedParent("reward_bookings", "bookingId"));
        mark(db, "combo_obligations", missing("occurrences", "occurrenceId"));
        for (String table : TABLES) {
            try (Cursor rows = db.query("SELECT * FROM " + table + " WHERE " + selected(table))) {
                while (rows.moveToNext()) {
                    db.execSQL("INSERT INTO migration_recovery(sourceSchema,sourceTable,sourceId,reason,payload) "
                                    + "VALUES (24,?,?,?,?)",
                            new Object[]{table, sourceId(table, rows),
                                    table.startsWith("flow_run_") ? "MISSING_PARENT_RUN" : "MISSING_OCCURRENCE_OR_STEP", encode(rows)});
                }
            }
        }
        // Children first, using the frozen row identities (not predicates changed by deletion).
        for (int i = TABLES.length - 1; i >= 0; i--) {
            String table = TABLES[i];
            db.execSQL("DELETE FROM " + table + " WHERE " + selected(table));
        }
        db.execSQL("DROP TABLE _recovery_rows");
    }

    private static void mark(SupportSQLiteDatabase db, String table, String predicate) {
        db.execSQL("INSERT INTO _recovery_rows(sourceTable,rowId) SELECT ?,child.rowid FROM "
                + table + " child WHERE " + predicate, new Object[]{table});
    }

    private static String missing(String parent, String column) {
        return "NOT EXISTS (SELECT 1 FROM " + parent + " p WHERE p.id=child." + column + ")";
    }

    private static String selectedParent(String parent, String column) {
        return "child." + column + " IN (SELECT id FROM " + parent + " WHERE " + selected(parent) + ")";
    }

    private static String selected(String table) {
        return "rowid IN (SELECT rowId FROM _recovery_rows WHERE sourceTable='" + table + "')";
    }

    private static String sourceId(String table, Cursor row) {
        if ("repetition_results".equals(table)) return new JSONArray()
                .put(row.getString(row.getColumnIndexOrThrow("stepId")))
                .put(row.getLong(row.getColumnIndexOrThrow("slotIndex"))).toString();
        return row.getString(row.getColumnIndexOrThrow("reward_assignments".equals(table) ? "bookingId" : "id"));
    }

    // Version 1 payload: ordered [column name, SQLite cursor type, value] tuples.
    // Integers and floats are strings to retain 64-bit precision; floats use hexadecimal.
    // Blobs are base64, NULL is JSON null, and text remains text (including empty strings).
    static String encode(Cursor row) {
        JSONArray columns = new JSONArray();
        try {
            for (int i = 0; i < row.getColumnCount(); i++) {
                int type = row.getType(i);
                Object value;
                switch (type) {
                    case Cursor.FIELD_TYPE_NULL: value = org.json.JSONObject.NULL; break;
                    case Cursor.FIELD_TYPE_INTEGER: value = Long.toString(row.getLong(i)); break;
                    case Cursor.FIELD_TYPE_FLOAT: value = Double.toHexString(row.getDouble(i)); break;
                    case Cursor.FIELD_TYPE_BLOB: value = Base64.encodeToString(row.getBlob(i), Base64.NO_WRAP); break;
                    case Cursor.FIELD_TYPE_STRING: value = row.getString(i); break;
                    default: throw new IllegalStateException("Unsupported SQLite value type " + type);
                }
                columns.put(new JSONArray().put(row.getColumnName(i)).put(type).put(value));
            }
            return new org.json.JSONObject().put("formatVersion", 1).put("columns", columns).toString();
        } catch (JSONException invalid) {
            throw new IllegalStateException("Cannot archive original flow snapshot", invalid);
        }
    }

    /** Called after Room has committed and validated the upgrade. Contains counts only, no task data. */
    public static void report(SupportSQLiteDatabase db) {
        try (Cursor rows = db.query("SELECT sourceSchema,sourceTable,COUNT(*) FROM migration_recovery "
                + "GROUP BY sourceSchema,sourceTable ORDER BY sourceSchema,sourceTable")) {
            while (rows.moveToNext()) Log.i("MigrationRecovery", "Committed recovery: schema="
                    + rows.getInt(0) + " table=" + rows.getString(1) + " archivedRows=" + rows.getLong(2));
        }
    }
}
