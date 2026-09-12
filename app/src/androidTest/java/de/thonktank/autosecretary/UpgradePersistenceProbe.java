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

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

/** Product-upgrade assertions shared by the ordinary JUnit test and the release-safe runner. */
final class UpgradePersistenceProbe {
    private static final String DATABASE = "auto_secretary.db";
    private static final long DATABASE_READY_TIMEOUT_MILLIS = 15_000L;
    private static final String PROBE_PREFERENCES = "upgrade_e2e_probe";
    private static final String PREVIOUS_VERSION = "previous_version";
    private static final String PREVIOUS_DATABASE_VERSION = "previous_database_version";
    private static final String EXPECTED_LAST_CHECK = "expected_last_check";
    private static final String SELECTED_FIXTURE_ID = "selected_fixture_id";
    private static final long DEDICATED_UPDATE_PREFERENCES_VERSION = 1_002_301L;
    private static final long SEEDED_POSTPONED_CODE = 987_654L;
    private static final long SEEDED_POSTPONED_AT = 123_450_000L;
    private static final Pattern FIXTURE_ID = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");
    private static final Pattern SQL_IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    private UpgradePersistenceProbe() {}

    static void seed(Context targetContext, Context testContext,
                     Instrumentation instrumentation, String fixtureId) throws Exception {
        long previousVersion = installedVersion(targetContext);
        long expectedLastCheck = System.currentTimeMillis();
        JSONObject fixture = fixture(testContext, fixtureId);
        JSONObject source = fixture.getJSONObject("source");
        int sourceDatabaseVersion = source.getInt("databaseVersion");
        equal(source.getLong("versionCode"), previousVersion,
                "Installed source version differs from fixture " + fixtureId);
        equal(source.getString("packageName"), targetContext.getPackageName(),
                "Installed source package differs from fixture " + fixtureId);
        if (previousVersion >= DEDICATED_UPDATE_PREFERENCES_VERSION) {
            check(targetContext.getSharedPreferences("forest_updates", Context.MODE_PRIVATE).edit()
                    .putLong("last_update_check", expectedLastCheck)
                    .putLong("postponed_update_code", SEEDED_POSTPONED_CODE)
                    .putLong("postponed_update_at", SEEDED_POSTPONED_AT)
                    .commit(), "Could not prepare dedicated update preferences");
        }

        Activity activity = startMainActivity(targetContext, instrumentation);
        try (SQLiteDatabase database = awaitDatabaseVersion(targetContext,
                sourceDatabaseVersion, SQLiteDatabase.OPEN_READWRITE, "previous app")) {
            equal(sourceDatabaseVersion, database.getVersion(),
                    "The rolling fixture must run against its declared source schema");
            database.beginTransaction();
            try {
                seedFixture(database, fixture);
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
                .putInt(PREVIOUS_DATABASE_VERSION, sourceDatabaseVersion)
                .putLong(EXPECTED_LAST_CHECK, expectedLastCheck)
                .putString(SELECTED_FIXTURE_ID, fixtureId)
                .commit(), "Could not seed the previous-version marker");
    }

    static void verify(Context context, Context testContext,
                       Instrumentation instrumentation, String fixtureId) throws Exception {
        SharedPreferences probe = context.getSharedPreferences(
                PROBE_PREFERENCES, Context.MODE_PRIVATE);
        long previousVersion = probe.getLong(PREVIOUS_VERSION, -1L);
        check(previousVersion > 0L, "The previous-version marker is missing");
        long expectedLastCheck = probe.getLong(EXPECTED_LAST_CHECK, -1L);
        check(expectedLastCheck > 0L, "The update-check marker is missing");
        int previousDatabaseVersion = probe.getInt(PREVIOUS_DATABASE_VERSION, -1);
        check(previousDatabaseVersion > 0, "The previous database version is missing");
        equal(fixtureId, probe.getString(SELECTED_FIXTURE_ID, ""),
                "Seed and verify fixture IDs differ");
        JSONObject fixture = fixture(testContext, fixtureId);
        int targetDatabaseVersion = fixture.getInt("targetDatabaseVersion");
        equal(fixture.getJSONObject("source").getInt("databaseVersion"), previousDatabaseVersion,
                "Source schema marker differs from pinned fixture");
        equal(fixture.getJSONObject("source").getLong("versionCode"), previousVersion,
                "Source version marker differs from pinned fixture");
        check(currentSmoke(fixture) ? targetDatabaseVersion >= previousDatabaseVersion
                        : targetDatabaseVersion > previousDatabaseVersion,
                "The fixture target violates its declared upgrade contract");
        check(installedVersion(context) > previousVersion,
                "adb install -r did not install a newer version");
        Activity activity = startMainActivity(context, instrumentation);
        try {
            try (SQLiteDatabase database = awaitDatabaseVersion(context,
                    targetDatabaseVersion, SQLiteDatabase.OPEN_READONLY, "product")) {
                verifyRows(database, fixture);
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

    private static void verifyRows(SQLiteDatabase database, JSONObject fixture) throws Exception {
        JSONArray expectations = fixture.getJSONArray("expectedTarget");
        for (int index = 0; index < expectations.length(); index++) {
            JSONObject expectation = expectations.getJSONObject(index);
            String table = identifier(expectation.getString("table"));
            JSONObject where = expectation.getJSONObject("where");
            JSONObject values = expectation.getJSONObject("values");
            String[] columns = keys(values);
            List<String> clauses = new ArrayList<>();
            List<String> arguments = new ArrayList<>();
            for (Iterator<String> names = where.keys(); names.hasNext();) {
                String column = identifier(names.next());
                Object value = where.get(column);
                if (value == JSONObject.NULL) {
                    clauses.add(column + " IS NULL");
                } else {
                    clauses.add(column + " = ?");
                    arguments.add(String.valueOf(value));
                }
            }
            Cursor row = database.query(table, columns, String.join(" AND ", clauses),
                    arguments.toArray(new String[0]), null, null, null);
            try {
                if (row.getCount() != 1 || !row.moveToFirst()) {
                    throw new AssertionError("Expected exactly one " + table
                            + " row for fixture " + fixture.getString("id")
                            + ", found " + row.getCount());
                }
                for (String column : columns) {
                    Object expected = values.get(column);
                    int position = row.getColumnIndexOrThrow(column);
                    String detail = table + " " + where + "." + column + " differs: expected="
                            + expected + ", actual=" + (row.isNull(position) ? "NULL" : row.getString(position));
                    if (expected == JSONObject.NULL) {
                        check(row.isNull(position), detail);
                    } else if (expected instanceof Number) {
                        equal(((Number) expected).longValue(), row.getLong(position),
                                detail);
                    } else if (expected instanceof Boolean) {
                        equal((Boolean) expected ? 1L : 0L, row.getLong(position),
                                detail);
                    } else {
                        equal(expected, row.getString(position),
                                detail);
                    }
                }
            } finally {
                row.close();
            }
        }
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

    private static void seedFixture(SQLiteDatabase database, JSONObject fixture) throws Exception {
        JSONArray seed = fixture.getJSONArray("seed");
        for (int entryIndex = 0; entryIndex < seed.length(); entryIndex++) {
            JSONObject entry = seed.getJSONObject(entryIndex);
            String table = identifier(entry.getString("table"));
            int conflict = "REPLACE".equals(entry.getString("conflict"))
                    ? SQLiteDatabase.CONFLICT_REPLACE : SQLiteDatabase.CONFLICT_ABORT;
            JSONArray rows = entry.getJSONArray("rows");
            for (int rowIndex = 0; rowIndex < rows.length(); rowIndex++) {
                insert(database, table, values(rows.getJSONObject(rowIndex)), conflict);
            }
        }
    }

    private static void insert(SQLiteDatabase database, String table,
                               ContentValues values, int conflict) {
        long row = database.insertWithOnConflict(table, null, values, conflict);
        if (row == -1L) throw new AssertionError("Could not seed fixture table " + table);
    }

    private static JSONObject fixture(Context testContext, String fixtureId) throws Exception {
        check(fixtureId != null && FIXTURE_ID.matcher(fixtureId).matches(),
                "Missing or invalid upgrade fixture ID");
        try (InputStream stream = testContext.getAssets().open(fixtureId + ".json")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = stream.read(buffer)) != -1) bytes.write(buffer, 0, count);
            JSONObject fixture = new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
            equal(fixtureId, fixture.getString("id"), "Loaded upgrade fixture ID differs");
            int contract = fixture.getInt("contractVersion");
            check((contract == 1 && !fixture.has("kind")) || currentSmoke(fixture),
                    "Unsupported upgrade fixture contract");
            int sourceSchema = fixture.getJSONObject("source").getInt("databaseVersion");
            int targetSchema = fixture.getInt("targetDatabaseVersion");
            check(currentSmoke(fixture) ? targetSchema >= sourceSchema : targetSchema > sourceSchema,
                    "Unsupported source/target schema pair");
            return fixture;
        }
    }

    private static boolean currentSmoke(JSONObject fixture) {
        return fixture.optInt("contractVersion") == 2
                && "current-smoke".equals(fixture.optString("kind"))
                && "current-production".equals(fixture.optString("id"));
    }

    private static String[] keys(JSONObject object) {
        List<String> result = new ArrayList<>();
        object.keys().forEachRemaining(key -> result.add(identifier(key)));
        return result.toArray(new String[0]);
    }

    private static String identifier(String value) {
        check(value != null && SQL_IDENTIFIER.matcher(value).matches(),
                "Invalid SQL identifier in upgrade fixture: " + value);
        return value;
    }

    private static ContentValues values(JSONObject source) throws Exception {
        ContentValues result = new ContentValues();
        for (Iterator<String> keys = source.keys(); keys.hasNext();) {
            String key = identifier(keys.next());
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
