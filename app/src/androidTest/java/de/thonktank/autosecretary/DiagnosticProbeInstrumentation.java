package de.thonktank.autosecretary;

import android.app.Activity;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

/** Dedicated read-only entry; mode is selected before Application and provider initialization. */
public final class DiagnosticProbeInstrumentation extends EarlyDiagnosticInstrumentation {
    private Bundle arguments;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        this.arguments = arguments == null ? Bundle.EMPTY : new Bundle(arguments);
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            requireEarlyEntry();
            long hold = Long.parseLong(arguments.getString("diagnosticHoldMillis", "0"));
            if (hold < 0 || hold > 30_000) throw new IllegalArgumentException("Invalid diagnostic hold");
            java.io.File database = getTargetContext().getDatabasePath("auto_secretary.db");
            String report = readOnlyDiagnosis(database);
            Bundle ready = new Bundle();
            ready.putString("diagnosticReady", "protocol=1; pid=" + Process.myPid());
            sendStatus(0, ready);
            if (hold > 0) {
                SystemClock.sleep(hold);
                report = readOnlyDiagnosis(database);
            }
            result.putInt("diagnosticProtocol", 1);
            result.putString("diagnosis", report);
            result.putString("stream", "\nOK (1 diagnostic probe)\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            Log.e("DiagnosticProbe", "Read-only diagnostic failed", failure);
            result.putString("shortMsg", failure.toString());
            result.putString("stream", "\nFAIL: " + failure + "\n");
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    static String readOnlyDiagnosis(java.io.File file) {
        try (android.database.sqlite.SQLiteDatabase db = android.database.sqlite.SQLiteDatabase.openDatabase(
                file.getPath(), null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY)) {
            java.util.Map<String, Integer> violations = new java.util.TreeMap<>();
            try (android.database.Cursor rows = db.rawQuery("PRAGMA foreign_key_check", null)) {
                while (rows.moveToNext()) violations.merge(rows.getString(0), 1, Integer::sum);
            }
            String report = "schema=" + db.getVersion() + "; foreignKeys=" + violations;
            if (db.getVersion() >= 25) {
                try (android.database.Cursor rows = db.rawQuery("SELECT COUNT(*) FROM step_flow_runs r "
                        + "WHERE EXISTS (SELECT 1 FROM occurrences o WHERE o.flowRunId=r.id "
                        + "AND o.flowExecutionSequence>=r.nextExecutionSequence)", null)) {
                    rows.moveToFirst(); report += "; staleExecutionCounters=" + rows.getLong(0);
                }
                try (android.database.Cursor rows = db.rawQuery("SELECT COUNT(*) FROM step_flow_runs r "
                        + "WHERE EXISTS (SELECT 1 FROM occurrences o WHERE o.sourceKey='flow-step:' || r.id || ':' || r.nextExecutionSequence)", null)) {
                    rows.moveToFirst(); report += "; occupiedNextExecutionKeys=" + rows.getLong(0);
                }
            }
            return report;
        }
    }

}
