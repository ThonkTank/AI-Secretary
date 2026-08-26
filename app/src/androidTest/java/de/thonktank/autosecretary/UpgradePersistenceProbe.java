package de.thonktank.autosecretary;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;

import de.thonktank.autosecretary.calendar.CalendarPolicy;
import de.thonktank.autosecretary.data.local.OccurrenceEntity;
import de.thonktank.autosecretary.data.local.OccurrenceStepEntity;
import de.thonktank.autosecretary.data.local.TaskEntity;
import de.thonktank.autosecretary.data.local.TaskScheduleEntity;
import de.thonktank.autosecretary.data.local.TaskStepEntity;
import de.thonktank.autosecretary.data.preferences.UiThemeMode;

/** Product-upgrade assertions shared by the ordinary JUnit test and the release-safe runner. */
final class UpgradePersistenceProbe {
    private static final String DATABASE = "auto_secretary.db";
    private static final String TASK_ID = "upgrade-e2e-task";
    private static final String TEMPLATE_ID = "upgrade-e2e-template";
    private static final String OCCURRENCE_ID = "upgrade-e2e-occurrence";
    private static final String STEP_ID = "upgrade-e2e-step";
    private static final String BOOKING_ID = "upgrade-e2e-booking";
    private static final String TITLE = "Upgrade-Daten bleiben erhalten";
    private static final String STEP_TEXT = "Persistierten Schritt lesen";
    private static final String PROBE_PREFERENCES = "upgrade_e2e_probe";
    private static final String PREVIOUS_VERSION = "previous_version";
    private static final long DEDICATED_UPDATE_PREFERENCES_VERSION = 1_002_301L;
    private static final long SEEDED_LAST_CHECK = 123_456_789L;
    private static final long SEEDED_POSTPONED_CODE = 987_654L;
    private static final long SEEDED_POSTPONED_AT = 123_450_000L;

    private UpgradePersistenceProbe() {}

    static void seed(Context targetContext, Context testContext) throws Exception {
        File path = targetContext.getDatabasePath(DATABASE);
        check(path.isFile(), "The previous app did not create its production database");

        try (SQLiteDatabase database = SQLiteDatabase.openDatabase(
                path.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE)) {
            equal(DatabaseContract.PRODUCTION_UPGRADE_SOURCE_VERSION, database.getVersion(),
                    "The rolling fixture must run against the supported 0.2.80 schema");
            database.beginTransaction();
            try {
                seedFixture(database, testContext);
                database.setTransactionSuccessful();
            } finally {
                database.endTransaction();
            }
        }

        long previousVersion = installedVersion(targetContext);
        check(targetContext.getSharedPreferences("forest_ui", Context.MODE_PRIVATE).edit()
                .putString("theme_mode", UiThemeMode.DARK.name())
                .putString("calendar_policy", CalendarPolicy.GOOGLE_ONLY.name())
                .putLong("last_update_check", SEEDED_LAST_CHECK)
                .putLong("postponed_update_code", SEEDED_POSTPONED_CODE)
                .putLong("postponed_update_at", SEEDED_POSTPONED_AT)
                .commit(), "Could not seed UI and legacy update preferences");
        if (previousVersion >= DEDICATED_UPDATE_PREFERENCES_VERSION) {
            check(targetContext.getSharedPreferences("forest_updates", Context.MODE_PRIVATE).edit()
                    .putLong("last_update_check", SEEDED_LAST_CHECK)
                    .putLong("postponed_update_code", SEEDED_POSTPONED_CODE)
                    .putLong("postponed_update_at", SEEDED_POSTPONED_AT)
                    .commit(), "Could not seed dedicated update preferences");
        }
        check(targetContext.getSharedPreferences(PROBE_PREFERENCES, Context.MODE_PRIVATE).edit()
                .putLong(PREVIOUS_VERSION, previousVersion)
                .commit(), "Could not seed the previous-version marker");
    }

    static void verify(Context context, Instrumentation instrumentation) throws Exception {
        SharedPreferences probe = context.getSharedPreferences(
                PROBE_PREFERENCES, Context.MODE_PRIVATE);
        long previousVersion = probe.getLong(PREVIOUS_VERSION, -1L);
        check(previousVersion > 0L, "The previous-version marker is missing");
        check(installedVersion(context) > previousVersion,
                "adb install -r did not install a newer version");

        AutoSecretaryApplication application = AutoSecretaryApplication.from(context);
        AppDatabase database = application.container().database;
        equal(DatabaseContract.VERSION,
                database.getOpenHelper().getReadableDatabase().getVersion());

        TaskEntity task = database.tasks().task(TASK_ID);
        notNull(task);
        equal(TITLE, task.title);
        equal(4_001_024L, task.catalogOrder);
        equal("FOREVER", task.boundKind);
        equal("2999-12-31", task.cadenceAnchorOn);

        List<TaskScheduleEntity> schedule = database.tasks().scheduleEntries(TASK_ID);
        equal(1, schedule.size());
        equal("LATER", schedule.get(0).slot);

        List<TaskStepEntity> templates = database.tasks().templates(TASK_ID);
        equal(1, templates.size());
        equal(STEP_TEXT, templates.get(0).text);
        equal("NONE", templates.get(0).amountKind);

        OccurrenceEntity occurrence = database.tasks().occurrence(OCCURRENCE_ID);
        notNull(occurrence);
        equal(TASK_ID, occurrence.taskId);
        equal("OPEN", occurrence.state);
        equal("LATER", occurrence.slot);

        OccurrenceStepEntity occurrenceStep = database.tasks().occurrenceStep(STEP_ID);
        notNull(occurrenceStep);
        equal(STEP_TEXT, occurrenceStep.text);
        check(occurrenceStep.done, "The persisted occurrence step is not done");
        equal("NONE", occurrenceStep.amountKind);
        equal("", occurrenceStep.legacyActualRepetitions);
        equal(TEMPLATE_ID, occurrenceStep.sourceTemplateId);
        equal(1, database.tasks().rewardBookings(OCCURRENCE_ID).size());
        equal(10, database.tasks().rewardBookings(OCCURRENCE_ID).get(0).xpDelta);
        equal(73, database.tasks().stats().xp);

        equal(UiThemeMode.DARK, application.container().uiPreferences.themeMode());
        equal(CalendarPolicy.GOOGLE_ONLY,
                application.container().uiPreferences.calendarPolicy());
        SharedPreferences updates = context.getSharedPreferences(
                "forest_updates", Context.MODE_PRIVATE);
        equal(SEEDED_LAST_CHECK, updates.getLong("last_update_check", -1L));
        equal(SEEDED_POSTPONED_CODE, updates.getLong("postponed_update_code", -1L));
        equal(SEEDED_POSTPONED_AT, updates.getLong("postponed_update_at", -1L));
        SharedPreferences ui = context.getSharedPreferences("forest_ui", Context.MODE_PRIVATE);
        check(!ui.contains("last_update_check"), "Legacy last-update check was not migrated");
        check(!ui.contains("postponed_update_code"), "Legacy postponed code was not migrated");
        check(!ui.contains("postponed_update_at"), "Legacy postponed time was not migrated");

        Intent launch = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity activity = instrumentation.startActivitySync(launch);
        notNull(activity);
        equal(MainActivity.class, activity.getClass());
        instrumentation.runOnMainSync(activity::finish);
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
