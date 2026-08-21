package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.database.sqlite.SQLiteDatabase;

import androidx.core.content.pm.PackageInfoCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.JSONObject;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;

import de.thonktank.autosecretary.calendar.CalendarPolicy;
import de.thonktank.autosecretary.data.preferences.UiThemeMode;

/**
 * Cross-installation probe invoked in two separate instrumentation runs by CI. The seed method
 * runs against the previous production APK; the verification method runs after adb install -r.
 */
@RunWith(AndroidJUnit4.class)
public final class UpgradePersistenceTest {
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

    @Test public void seedPreviousVersion() throws Exception {
        requirePhase("seed");
        Context context = targetContext();
        File path = context.getDatabasePath(DATABASE);
        assertTrue("The previous app did not create its production database", path.isFile());

        try (SQLiteDatabase database = SQLiteDatabase.openDatabase(
                path.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE)) {
            assertEquals("The rolling fixture must run against the supported 0.2.80 schema",
                    DatabaseContract.PRODUCTION_UPGRADE_SOURCE_VERSION, database.getVersion());
            database.beginTransaction();
            try {
                seedFixture(database);
                database.setTransactionSuccessful();
            } finally {
                database.endTransaction();
            }
        }

        long previousVersion = installedVersion(context);
        assertTrue("Could not seed UI and legacy update preferences",
                context.getSharedPreferences("forest_ui", Context.MODE_PRIVATE).edit()
                .putString("theme_mode", UiThemeMode.DARK.name())
                .putString("calendar_policy", CalendarPolicy.GOOGLE_ONLY.name())
                .putLong("last_update_check", SEEDED_LAST_CHECK)
                .putLong("postponed_update_code", SEEDED_POSTPONED_CODE)
                .putLong("postponed_update_at", SEEDED_POSTPONED_AT)
                .commit());
        if (previousVersion >= DEDICATED_UPDATE_PREFERENCES_VERSION) {
            assertTrue("Could not seed dedicated update preferences",
                    context.getSharedPreferences("forest_updates", Context.MODE_PRIVATE).edit()
                    .putLong("last_update_check", SEEDED_LAST_CHECK)
                    .putLong("postponed_update_code", SEEDED_POSTPONED_CODE)
                    .putLong("postponed_update_at", SEEDED_POSTPONED_AT)
                    .commit());
        }
        assertTrue("Could not seed the previous-version marker",
                context.getSharedPreferences(PROBE_PREFERENCES, Context.MODE_PRIVATE).edit()
                .putLong(PREVIOUS_VERSION, previousVersion)
                .commit());
    }

    @Test public void currentVersionStartsAndReadsPreviousData() throws Exception {
        requirePhase("verify");
        Context context = targetContext();
        SharedPreferences probe = context.getSharedPreferences(
                PROBE_PREFERENCES, Context.MODE_PRIVATE);
        long previousVersion = probe.getLong(PREVIOUS_VERSION, -1L);
        assertTrue("The previous-version marker is missing", previousVersion > 0L);
        assertTrue("adb install -r did not install a newer version",
                installedVersion(context) > previousVersion);

        AutoSecretaryApplication application = AutoSecretaryApplication.from(context);
        AppDatabase database = application.container().database;
        assertEquals(DatabaseContract.VERSION,
                database.getOpenHelper().getReadableDatabase().getVersion());

        TaskEntity task = database.tasks().task(TASK_ID);
        assertNotNull(task);
        assertEquals(TITLE, task.title);
        assertEquals(4_001_024L, task.catalogOrder);
        assertEquals("FOREVER", task.boundKind);

        List<TaskScheduleEntity> schedule = database.tasks().scheduleEntries(TASK_ID);
        assertEquals(1, schedule.size());
        assertEquals("LATER", schedule.get(0).slot);

        List<TaskStepEntity> templates = database.tasks().templates(TASK_ID);
        assertEquals(1, templates.size());
        assertEquals(STEP_TEXT, templates.get(0).text);
        assertEquals("NONE", templates.get(0).amountKind);

        OccurrenceEntity occurrence = database.tasks().occurrence(OCCURRENCE_ID);
        assertNotNull(occurrence);
        assertEquals(TASK_ID, occurrence.taskId);
        assertEquals("OPEN", occurrence.state);
        assertEquals("LATER", occurrence.slot);

        OccurrenceStepEntity occurrenceStep = database.tasks().occurrenceStep(STEP_ID);
        assertNotNull(occurrenceStep);
        assertEquals(STEP_TEXT, occurrenceStep.text);
        assertTrue(occurrenceStep.done);
        assertEquals("NONE", occurrenceStep.amountKind);
        assertEquals("", occurrenceStep.legacyActualRepetitions);
        assertEquals(TEMPLATE_ID, occurrenceStep.sourceTemplateId);
        assertEquals(1, database.tasks().rewardBookings(OCCURRENCE_ID).size());
        assertEquals(10, database.tasks().rewardBookings(OCCURRENCE_ID).get(0).xpDelta);
        assertEquals(73, database.tasks().stats().xp);

        assertEquals(UiThemeMode.DARK, application.container().uiPreferences.themeMode());
        assertEquals(CalendarPolicy.GOOGLE_ONLY,
                application.container().uiPreferences.calendarPolicy());
        SharedPreferences updates = context.getSharedPreferences(
                "forest_updates", Context.MODE_PRIVATE);
        assertEquals(SEEDED_LAST_CHECK, updates.getLong("last_update_check", -1L));
        assertEquals(SEEDED_POSTPONED_CODE, updates.getLong("postponed_update_code", -1L));
        assertEquals(SEEDED_POSTPONED_AT, updates.getLong("postponed_update_at", -1L));
        SharedPreferences ui = context.getSharedPreferences("forest_ui", Context.MODE_PRIVATE);
        assertTrue(!ui.contains("last_update_check"));
        assertTrue(!ui.contains("postponed_update_code"));
        assertTrue(!ui.contains("postponed_update_at"));

        Intent launch = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity activity = InstrumentationRegistry.getInstrumentation().startActivitySync(launch);
        assertNotNull(activity);
        assertEquals(MainActivity.class, activity.getClass());
        InstrumentationRegistry.getInstrumentation().runOnMainSync(activity::finish);
    }

    private static Context targetContext() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    private static void requirePhase(String expected) {
        assumeTrue("Only the cross-installation CI probe runs this test",
                expected.equals(InstrumentationRegistry.getArguments().getString("upgradePhase")));
    }

    private static long installedVersion(Context context) throws Exception {
        PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        return PackageInfoCompat.getLongVersionCode(info);
    }

    private static void seedFixture(SQLiteDatabase database) throws Exception {
        deleteIfPresent(database, "reward_bookings", "id", BOOKING_ID);
        deleteIfPresent(database, "occurrence_steps", "id", STEP_ID);
        deleteIfPresent(database, "occurrences", "id", OCCURRENCE_ID);
        deleteIfPresent(database, "task_steps", "id", TEMPLATE_ID);
        deleteIfPresent(database, "tasks", "id", TASK_ID);

        JSONObject tables = fixture().getJSONObject("tables");
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

    private static JSONObject fixture() throws Exception {
        try (InputStream stream = InstrumentationRegistry.getInstrumentation()
                .getContext().getAssets().open("v0.2.80.json")) {
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
}
