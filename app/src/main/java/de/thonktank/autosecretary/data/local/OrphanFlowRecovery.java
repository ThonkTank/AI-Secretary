package de.thonktank.autosecretary.data.local;

import android.database.Cursor;
import android.util.Base64;
import android.util.Log;
import androidx.sqlite.db.SupportSQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONException;

/** Narrow recovery for snapshots whose parent run is absent at the schema-24 boundary. */
public final class OrphanFlowRecovery {
    private static final String[] TABLES = {"flow_run_steps", "flow_run_resources"};
    private OrphanFlowRecovery() { }

    static void createArchive(SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS migration_recovery (sourceSchema INTEGER NOT NULL, "
                + "sourceTable TEXT NOT NULL, sourceId TEXT NOT NULL, reason TEXT NOT NULL, "
                + "payload TEXT NOT NULL, PRIMARY KEY(sourceSchema,sourceTable,sourceId))");
    }

    static void recover(SupportSQLiteDatabase db) {
        if (!db.inTransaction()) throw new IllegalStateException("Recovery requires an upgrade transaction");
        createArchive(db);
        // Save BOTH tables before deleting either. Plain INSERT deliberately fails on conflicts.
        for (String table : TABLES) {
            try (Cursor rows = db.query("SELECT * FROM " + table + " WHERE " + orphanPredicate(table))) {
                while (rows.moveToNext()) {
                    db.execSQL("INSERT INTO migration_recovery(sourceSchema,sourceTable,sourceId,reason,payload) "
                                    + "VALUES (24,?,?,?,?)",
                            new Object[]{table, rows.getString(rows.getColumnIndexOrThrow("id")),
                                    "MISSING_PARENT_RUN", encode(rows)});
                }
            }
        }
        for (String table : TABLES) {
            // Each deleted row must have its own archived copy, even if this code is later changed.
            db.execSQL("DELETE FROM " + table + " WHERE " + orphanPredicate(table)
                    + " AND EXISTS (SELECT 1 FROM migration_recovery a WHERE a.sourceSchema=24 "
                    + "AND a.sourceTable=? AND a.sourceId=" + table + ".id)", new Object[]{table});
        }
    }

    private static String orphanPredicate(String table) {
        return "NOT EXISTS (SELECT 1 FROM step_flow_runs r WHERE r.id=" + table + ".runId)";
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
