package de.thonktank.autosecretary;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import android.util.Log;

/**
 * Minimal cross-installation runner. It deliberately avoids AndroidX/JUnit because an
 * instrumentation APK shares the target application's class loader after adb install -r; test
 * framework dependencies must therefore not become a hidden release-APK ABI requirement.
 */
public final class UpgradeProbeInstrumentation extends Instrumentation {
    private static final String TAG = "UpgradeProbe";

    private Bundle arguments;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        this.arguments = arguments == null ? Bundle.EMPTY : new Bundle(arguments);
        start();
    }

    @Override public void callApplicationOnCreate(android.app.Application application) {
        // Diagnostic runs must not start widgets, Room, or migrations in the target application.
        if (!"diagnose".equals(arguments == null ? "" : arguments.getString("upgradePhase", ""))) {
            super.callApplicationOnCreate(application);
        }
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        String phase = arguments.getString("upgradePhase", "");
        String fixtureId = arguments.getString("upgradeFixture", "");
        try {
            if ("diagnose".equals(phase)) {
                result.putString("diagnosis", readOnlyDiagnosis(getTargetContext().getDatabasePath("auto_secretary.db")));
            } else if ("seed".equals(phase)) {
                UpgradePersistenceProbe.seed(getTargetContext(), getContext(), this, fixtureId);
            } else if ("verify".equals(phase)) {
                UpgradePersistenceProbe.verify(getTargetContext(), getContext(), this, fixtureId);
            } else {
                throw new AssertionError("Unknown upgrade phase: " + phase);
            }
            result.putString("stream", "\nOK (1 probe)\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            Log.e(TAG, "Upgrade probe failed in phase " + phase, failure);
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
