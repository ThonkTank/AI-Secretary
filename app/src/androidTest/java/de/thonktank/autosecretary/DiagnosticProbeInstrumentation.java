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
            String fixtureToken = arguments.getString("diagnosticFixtureToken", "");
            if (!fixtureToken.isEmpty()) {
                if (!"de.thonktank.autosecretary.test".equals(getTargetContext().getPackageName())
                        || !("ranchu".equals(android.os.Build.HARDWARE)
                        || "goldfish".equals(android.os.Build.HARDWARE))) {
                    throw new AssertionError("Lifecycle witness requires the isolated emulator target");
                }
                if (!fixtureToken.matches("[a-f0-9]{32}")) throw new IllegalArgumentException("Invalid fixture token");
            }
            java.io.File database = getTargetContext().getDatabasePath("auto_secretary.db");
            String report = readOnlyDiagnosis(database);
            Bundle ready = new Bundle();
            ready.putString("diagnosticReady", "protocol=1; pid=" + Process.myPid());
            sendStatus(0, ready);
            if (!fixtureToken.isEmpty()) {
                // Only the isolated emulator can request this read-only witness. Its full
                // snapshot must be checked before Android may start ordinary background work.
                java.io.File eventsComplete = new java.io.File(getTargetContext().getFilesDir(),
                        "diagnostic-events-" + fixtureToken);
                long deadline = SystemClock.elapsedRealtime() + 30_000;
                while (!eventsComplete.exists() && SystemClock.elapsedRealtime() < deadline) {
                    SystemClock.sleep(25);
                }
                if (!eventsComplete.exists()) throw new AssertionError("Native events did not complete");
                // Do not link fixture/Room classes into the cross-APK production runner.
                Class<?> fixture = Class.forName("de.thonktank.autosecretary.DiagnosticFixtures",
                        true, getClass().getClassLoader());
                fixture.getDeclaredMethod("assertUnchanged", android.content.Context.class)
                        .invoke(null, getTargetContext());
                fixture.getDeclaredMethod("assertNativeEvents", android.content.Context.class, int.class)
                        .invoke(null, getTargetContext(), Process.myPid());
                report = readOnlyDiagnosis(database);
                Bundle verified = new Bundle();
                verified.putString("diagnosticVerified", "protocol=1; pid=" + Process.myPid()
                        + "; token=" + fixtureToken);
                sendStatus(0, verified);
                if ("true".equals(arguments.getString("diagnosticAbortExpected", "false"))) {
                    SystemClock.sleep(30_000);
                    throw new AssertionError("Expected diagnostic abort did not occur");
                }
            } else if (hold > 0) {
                SystemClock.sleep(hold);
                report = readOnlyDiagnosis(database);
            }
            result.putInt("diagnosticProtocol", 1);
            result.putInt("diagnosticPid", Process.myPid());
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
