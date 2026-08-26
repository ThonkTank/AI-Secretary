package de.thonktank.autosecretary;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.SystemClock;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

/** Product-upgrade assertions shared by the ordinary JUnit test and the release-safe runner. */
final class UpgradePersistenceProbe {
    private static final String DATABASE = "auto_secretary.db";
    private static final int SOURCE_DATABASE_VERSION = 8;
    private static final int TARGET_DATABASE_VERSION = 19;
    private static final long DATABASE_READY_TIMEOUT_MILLIS = 15_000L;
    private static final String TASK_ID = "upgrade-e2e-task";
    private static final String TEMPLATE_ID = "upgrade-e2e-template";
    private static final String OCCURRENCE_ID = "upgrade-e2e-occurrence";
    private static final String STEP_ID = "upgrade-e2e-step";
    private static final String BOOKING_ID = "upgrade-e2e-booking";
    private static final String TITLE = "Upgrade-Daten bleiben erhalten";
    private static final String STEP_TEXT = "Persistierten Schritt lesen";
    private static final String PROBE_PREFERENCES = "upgrade_e2e_probe";
    private static final String PREVIOUS_VERSION = "previous_version";
    private static final String EXPECTED_LAST_CHECK = "expected_last_check";
    private static final long DEDICATED_UPDATE_PREFERENCES_VERSION = 1_002_301L;
    private static final long SEEDED_POSTPONED_CODE = 987_654L;
    private static final long SEEDED_POSTPONED_AT = 123_450_000L;

    private UpgradePersistenceProbe() {}

    static void seed(Context targetContext, Context testContext,
                     Instrumentation instrumentation) throws Exception {
        long previousVersion = installedVersion(targetContext);
        long expectedLastCheck = System.currentTimeMillis();
        if (previousVersion >= DEDICATED_UPDATE_PREFERENCES_VERSION) {
            check(targetContext.getSharedPreferences("forest_updates", Context.MODE_PRIVATE).edit()
                    .putLong("last_update_check", expectedLastCheck)
                    .putLong("postponed_update_code", SEEDED_POSTPONED_CODE)
                    .putLong("postponed_update_at", SEEDED_POSTPONED_AT)
                    .commit(), "Could not prepare dedicated update preferences");
        }

        Activity activity = startMainActivity(targetContext, instrumentation);
        try (SQLiteDatabase database = awaitDatabaseVersion(targetContext,
                SOURCE_DATABASE_VERSION, SQLiteDatabase.OPEN_READWRITE, "previous app")) {
            equal(SOURCE_DATABASE_VERSION, database.getVersion(),
                    "The rolling fixture must run against the supported 0.2.80 schema");
            database.beginTransaction();
            try {
                seedFixture(database, testContext);
                database.setTransactionSuccessful();
            } finally {
                database.endTransaction();
            }
        } finally {
            finish(activity, instrumentation);
        }

        check(targetContext.getSharedPreferences("forest_ui", Context.MODE_PRIVATE).edit()
                .putString("theme_mode", "DARK")
                .putString("calendar_policy", "GOOGLE_ONLY")
                .putLong("last_update_check", expectedLastCheck)
                .putLong("postponed_update_code", SEEDED_POSTPONED_CODE)
                .putLong("postponed_update_at", SEEDED_POSTPONED_AT)
                .commit(), "Could not seed UI and legacy update preferences");
        if (previousVersion >= DEDICATED_UPDATE_PREFERENCES_VERSION) {
            check(targetContext.getSharedPreferences("forest_updates", Context.MODE_PRIVATE).edit()
                    .putLong("last_update_check", expectedLastCheck)
                    .putLong("postponed_update_code", SEEDED_POSTPONED_CODE)
                    .putLong("postponed_update_at", SEEDED_POSTPONED_AT)
                    .commit(), "Could not seed dedicated update preferences");
        }
        check(targetContext.getSharedPreferences(PROBE_PREFERENCES, Context.MODE_PRIVATE).edit()
                .putLong(PREVIOUS_VERSION, previousVersion)
                .putLong(EXPECTED_LAST_CHECK, expectedLastCheck)
                .commit(), "Could not seed the previous-version marker");
    }

    static void verify(Context context, Instrumentation instrumentation) throws Exception {
        SharedPreferences probe = context.getSharedPreferences(
                PROBE_PREFERENCES, Context.MODE_PRIVATE);
        long previousVersion = probe.getLong(PREVIOUS_VERSION, -1L);
        check(previousVersion > 0L, "The previous-version marker is missing");
        long expectedLastCheck = probe.getLong(EXPECTED_LAST_CHECK, -1L);
        check(expectedLastCheck > 0L, "The update-check marker is missing");
        check(installedVersion(context) > previousVersion,
                "adb install -r did not install a newer version");
        Activity activity = startMainActivity(context, instrumentation);
        try {
            try (SQLiteDatabase database = awaitMigratedDatabase(context)) {
                verifyRows(database);
            }
            verifyPreferencesAfterActivityStart(context, expectedLastCheck);
        } finally {
            finish(activity, instrumentation);
        }
    }

    private static Activity startMainActivity(Context context,
                                              Instrumentation instrumentation) {
        Intent launch = new Intent()
                .setClassName(context.getPackageName(), context.getPackageName() + ".MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity activity = instrumentation.startActivitySync(launch);
        notNull(activity);
        equal(context.getPackageName() + ".MainActivity", activity.getClass().getName());
        instrumentation.waitForIdleSync();
        return activity;
    }

    private static void finish(Activity activity, Instrumentation instrumentation) {
        instrumentation.runOnMainSync(activity::finish);
        instrumentation.waitForIdleSync();
    }

    private static SQLiteDatabase awaitMigratedDatabase(Context context) throws Exception {
        return awaitDatabaseVersion(context, TARGET_DATABASE_VERSION,
                SQLiteDatabase.OPEN_READONLY, "product");
    }

    private static SQLiteDatabase awaitDatabaseVersion(Context context, int expectedVersion,
                                                       int openFlags, String owner)
            throws Exception {
        File path = context.getDatabasePath(DATABASE);
        long deadline = SystemClock.uptimeMillis() + DATABASE_READY_TIMEOUT_MILLIS;
        int observedVersion = -1;
        RuntimeException lastFailure = null;
        while (SystemClock.uptimeMillis() < deadline) {
            try {
                SQLiteDatabase database = SQLiteDatabase.openDatabase(
                        path.getAbsolutePath(), null, openFlags);
                observedVersion = database.getVersion();
                if (observedVersion == expectedVersion) return database;
                database.close();
                if (observedVersion > expectedVersion) {
                    throw new AssertionError("Unexpected future database version "
                            + observedVersion);
                }
            } catch (RuntimeException failure) {
                lastFailure = failure;
            }
            Thread.sleep(100L);
        }
        AssertionError timeout = new AssertionError("The " + owner
                + " did not open its database at version " + expectedVersion
                + "; last observed version was " + observedVersion);
        if (lastFailure != null) timeout.initCause(lastFailure);
        throw timeout;
    }

    private static void verifyRows(SQLiteDatabase database) {
        try (Cursor row = row(database, "tasks",
                new String[]{"title", "catalogOrder", "boundKind", "cadenceAnchorOn"},
                "id", TASK_ID)) {
            equal(TITLE, text(row, "title"));
            equal(4_001_024L, number(row, "catalogOrder"));
            equal("FOREVER", text(row, "boundKind"));
            equal("2999-12-31", text(row, "cadenceAnchorOn"));
        }
        try (Cursor row = row(database, "task_schedule_entries",
                new String[]{"slot"}, "taskId", TASK_ID)) {
            equal("LATER", text(row, "slot"));
        }
        try (Cursor row = row(database, "task_steps",
                new String[]{"text", "amountKind"}, "id", TEMPLATE_ID)) {
            equal(STEP_TEXT, text(row, "text"));
            equal("NONE", text(row, "amountKind"));
        }
        try (Cursor row = row(database, "occurrences",
                new String[]{"taskId", "state", "slot"}, "id", OCCURRENCE_ID)) {
            equal(TASK_ID, text(row, "taskId"));
            equal("OPEN", text(row, "state"));
            equal("LATER", text(row, "slot"));
        }
        try (Cursor row = row(database, "occurrence_steps",
                new String[]{"text", "done", "amountKind", "actualRepetitions",
                        "sourceTemplateId"}, "id", STEP_ID)) {
            equal(STEP_TEXT, text(row, "text"));
            equal(1L, number(row, "done"));
            equal("NONE", text(row, "amountKind"));
            equal("", text(row, "actualRepetitions"));
            equal(TEMPLATE_ID, text(row, "sourceTemplateId"));
        }
        try (Cursor row = row(database, "reward_bookings",
                new String[]{"xpDelta"}, "occurrenceId", OCCURRENCE_ID)) {
            equal(10L, number(row, "xpDelta"));
        }
        try (Cursor row = row(database, "stats", new String[]{"xp"}, "id", "1")) {
            equal(73L, number(row, "xp"));
        }
    }

    private static Cursor row(SQLiteDatabase database, String table, String[] columns,
                              String keyColumn, String keyValue) {
        Cursor row = database.query(table, columns, keyColumn + " = ?",
                new String[]{keyValue}, null, null, null);
        if (row.getCount() != 1 || !row.moveToFirst()) {
            int count = row.getCount();
            row.close();
            throw new AssertionError("Expected exactly one " + table + " row for "
                    + keyColumn + "=" + keyValue + ", found " + count);
        }
        return row;
    }

    private static String text(Cursor row, String column) {
        return row.getString(row.getColumnIndexOrThrow(column));
    }

    private static long number(Cursor row, String column) {
        return row.getLong(row.getColumnIndexOrThrow(column));
    }

    private static void verifyPreferencesAfterActivityStart(Context context,
                                                            long expectedLastCheck) {
        SharedPreferences ui = context.getSharedPreferences("forest_ui", Context.MODE_PRIVATE);
        equal("DARK", ui.getString("theme_mode", ""));
        equal("GOOGLE_ONLY", ui.getString("calendar_policy", ""));
        SharedPreferences updates = context.getSharedPreferences(
                "forest_updates", Context.MODE_PRIVATE);
        equal(expectedLastCheck, updates.getLong("last_update_check", -1L));
        equal(SEEDED_POSTPONED_CODE, updates.getLong("postponed_update_code", -1L));
        equal(SEEDED_POSTPONED_AT, updates.getLong("postponed_update_at", -1L));
        check(!ui.contains("last_update_check"), "Legacy last-update check reappeared");
        check(!ui.contains("postponed_update_code"), "Legacy postponed code reappeared");
        check(!ui.contains("postponed_update_at"), "Legacy postponed time reappeared");
    }

    @SuppressWarnings("deprecation")
    private static long installedVersion(Context context) throws Exception {
        PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        // Product version codes are deliberately within the legacy 32-bit field on every
        // supported release. Reading it directly keeps this API-26 probe free of support code.
        return info.versionCode;
    }

    private static void seedFixture(SQLiteDatabase database, Context testContext) throws Exception {
        deleteIfPresent(database, "reward_bookings", "id", BOOKING_ID);
        deleteIfPresent(database, "occurrence_steps", "id", STEP_ID);
        deleteIfPresent(database, "occurrences", "id", OCCURRENCE_ID);
        deleteIfPresent(database, "task_steps", "id", TEMPLATE_ID);
        deleteIfPresent(database, "tasks", "id", TASK_ID);

        JSONObject tables = fixture(testContext).getJSONObject("tables");
        insert(database, "tasks", values(tables.getJSONObject("tasks")),
                SQLiteDatabase.CONFLICT_ABORT);
        insert(database, "task_steps", values(tables.getJSONObject("task_steps")),
                SQLiteDatabase.CONFLICT_ABORT);
        insert(database, "occurrences", values(tables.getJSONObject("occurrences")),
                SQLiteDatabase.CONFLICT_ABORT);
        insert(database, "occurrence_steps", values(tables.getJSONObject("occurrence_steps")),
                SQLiteDatabase.CONFLICT_ABORT);
        insert(database, "stats", values(tables.getJSONObject("stats")),
                SQLiteDatabase.CONFLICT_REPLACE);
        insert(database, "reward_bookings", values(tables.getJSONObject("reward_bookings")),
                SQLiteDatabase.CONFLICT_ABORT);
    }

    private static void insert(SQLiteDatabase database, String table,
                               ContentValues values, int conflict) {
        long row = database.insertWithOnConflict(table, null, values, conflict);
        if (row == -1L) throw new AssertionError("Could not seed fixture table " + table);
    }

    private static void deleteIfPresent(SQLiteDatabase database, String table,
                                        String column, String value) {
        database.delete(table, column + " = ?", new String[]{value});
    }

    private static JSONObject fixture(Context testContext) throws Exception {
        try (InputStream stream = testContext.getAssets().open("v0.2.80.json")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = stream.read(buffer)) != -1) bytes.write(buffer, 0, count);
            return new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
        }
    }

    private static ContentValues values(JSONObject source) throws Exception {
        ContentValues result = new ContentValues();
        for (Iterator<String> keys = source.keys(); keys.hasNext();) {
            String key = keys.next();
            Object value = source.get(key);
            if (value == JSONObject.NULL) result.putNull(key);
            else if (value instanceof Integer) result.put(key, (Integer) value);
            else if (value instanceof Long) result.put(key, (Long) value);
            else if (value instanceof Boolean) result.put(key, (Boolean) value);
            else if (value instanceof String) result.put(key, (String) value);
            else throw new AssertionError("Unsupported fixture value for " + key);
        }
        return result;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void notNull(Object value) {
        if (value == null) throw new AssertionError("Expected a non-null value");
    }

    private static void equal(Object expected, Object actual) {
        equal(expected, actual, "Expected <" + expected + "> but was <" + actual + ">");
    }

    private static void equal(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message);
        }
    }
}
