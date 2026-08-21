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
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.core.content.pm.PackageInfoCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import de.thonktank.autosecretary.calendar.CalendarPolicy;
import de.thonktank.autosecretary.data.preferences.UiThemeMode;

/**
 * Cross-installation probe invoked in two separate instrumentation runs by CI. The seed method
 * runs against the previous production APK; the verification method runs after adb install -r.
 */
@RunWith(AndroidJUnit4.class)
public final class UpgradePersistenceTest {
    private static final int CURRENT_SCHEMA_VERSION = 12;
    private static final String DATABASE = "auto_secretary.db";
    private static final String TASK_ID = "upgrade-e2e-task";
    private static final String SCHEDULE_ID = "upgrade-e2e-schedule";
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

    @Test public void seedContractSupportsEveryExportedRoomSchema() throws Exception {
        for (int version = 1; version <= CURRENT_SCHEMA_VERSION; version++) {
            try (SQLiteDatabase database = SQLiteDatabase.create(null)) {
                createExportedSchema(database, version);
                seedFixture(database);
                assertEquals("task fixture in schema " + version, 1,
                        count(database, "tasks", "id", TASK_ID));
                assertEquals("template fixture in schema " + version, 1,
                        count(database, "task_steps", "id", TEMPLATE_ID));
                assertEquals("occurrence fixture in schema " + version, 1,
                        count(database, "occurrences", "id", OCCURRENCE_ID));
                assertEquals("step fixture in schema " + version, 1,
                        count(database, "occurrence_steps", "id", STEP_ID));
                if (version >= 7) assertEquals(1,
                        count(database, "reward_bookings", "id", BOOKING_ID));
                if (version >= 12) assertEquals(1,
                        count(database, "task_schedule_entries", "id", SCHEDULE_ID));
            }
        }
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
        assertEquals(CURRENT_SCHEMA_VERSION,
                database.getOpenHelper().getReadableDatabase().getVersion());

        TaskEntity task = database.tasks().task(TASK_ID);
        assertNotNull(task);
        assertEquals(TITLE, task.title);
        assertEquals("LATER", task.slot);
        assertEquals(4_001_024L, task.displayOrder);
        assertEquals(0, task.timeOfDayMask);
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

    private static ContentValues taskValues() {
        ContentValues values = new ContentValues();
        values.put("id", TASK_ID);
        values.put("title", TITLE);
        values.put("slot", "LATER");
        values.put("recurrence", "ONCE");
        values.put("intervalDays", 1);
        values.put("weekdayMask", 0);
        values.put("ongoing", 0);
        values.put("conditionText", "");
        values.put("conditionDone", 0);
        values.put("archived", 0);
        values.put("nextDueOn", "2999-12-31");
        values.put("lastScheduledOn", "");
        values.put("lastCompletedOn", "");
        values.put("routineLevel", 2);
        values.put("routineStreak", 3);
        values.put("routineStreakWeeks", 4);
        values.put("lastStreakWeek", "2026-08-10");
        values.put("displayOrder", 4_001_024L);
        values.put("hasCompletedOccurrence", 1);
        values.putNull("estimatedMinutes");
        values.put("timeOfDayMask", 0);
        values.put("boundKind", "FOREVER");
        values.put("boundUntilOn", "");
        values.putNull("boundWeeks");
        values.putNull("remainingCount");
        values.put("deadlineOn", "");
        values.put("note", "");
        return values;
    }

    private static ContentValues templateValues() {
        ContentValues values = new ContentValues();
        values.put("id", TEMPLATE_ID);
        values.put("taskId", TASK_ID);
        values.put("position", 0);
        values.put("text", STEP_TEXT);
        values.put("weekdayMask", 0);
        values.put("amountKind", "NONE");
        values.putNull("plannedSets");
        values.putNull("plannedReps");
        values.putNull("plannedDurationSeconds");
        values.put("note", "");
        return values;
    }

    private static ContentValues scheduleValues() {
        ContentValues values = new ContentValues();
        values.put("id", SCHEDULE_ID);
        values.put("taskId", TASK_ID);
        values.put("slot", "LATER");
        values.put("displayOrder", 4_001_024L);
        return values;
    }

    private static ContentValues occurrenceValues() {
        ContentValues values = new ContentValues();
        values.put("id", OCCURRENCE_ID);
        values.put("taskId", TASK_ID);
        values.put("scheduledOn", "2999-12-31");
        values.put("state", "OPEN");
        values.put("sortOrder", 42);
        values.put("completedOn", "");
        values.put("slot", "LATER");
        values.put("awardedXp", 0);
        values.put("comboPointDelta", 0);
        return values;
    }

    private static ContentValues occurrenceStepValues() {
        ContentValues values = new ContentValues();
        values.put("id", STEP_ID);
        values.put("occurrenceId", OCCURRENCE_ID);
        values.put("position", 0);
        values.put("text", STEP_TEXT);
        values.put("done", 1);
        values.put("amountKind", "NONE");
        values.putNull("plannedSets");
        values.putNull("plannedReps");
        values.putNull("plannedDurationSeconds");
        values.put("note", "");
        values.put("actualRepetitions", "");
        values.put("sourceTemplateId", TEMPLATE_ID);
        values.put("comboOwnerId", "step:" + TEMPLATE_ID);
        values.put("carryForwardReason", "NONE");
        values.put("earnedXp", 10);
        values.put("comboPointDelta", 0);
        return values;
    }

    private static ContentValues statsValues() {
        ContentValues values = new ContentValues();
        values.put("id", 1);
        values.put("xp", 73);
        return values;
    }

    private static ContentValues bookingValues() {
        ContentValues values = new ContentValues();
        values.put("id", BOOKING_ID);
        values.put("transactionId", "upgrade-e2e-transaction");
        values.put("occurrenceId", OCCURRENCE_ID);
        values.put("occurrenceStepId", STEP_ID);
        values.put("ownerId", "step:" + TEMPLATE_ID);
        values.put("kind", "LEGACY_STEP");
        values.put("target", "VESSEL");
        values.put("xpDelta", 10);
        values.put("comboPointDelta", 0);
        values.put("bookedOn", "2999-12-31");
        values.putNull("reversesBookingId");
        return values;
    }

    private static void seedFixture(SQLiteDatabase database) {
        deleteIfPresent(database, "reward_bookings", "id", BOOKING_ID);
        deleteIfPresent(database, "occurrence_steps", "id", STEP_ID);
        deleteIfPresent(database, "occurrences", "id", OCCURRENCE_ID);
        deleteIfPresent(database, "task_steps", "id", TEMPLATE_ID);
        deleteIfPresent(database, "task_schedule_entries", "id", SCHEDULE_ID);
        deleteIfPresent(database, "tasks", "id", TASK_ID);
        insertCompatible(database, "tasks", taskValues(), SQLiteDatabase.CONFLICT_ABORT);
        if (tableExists(database, "task_schedule_entries"))
            insertCompatible(database, "task_schedule_entries", scheduleValues(),
                    SQLiteDatabase.CONFLICT_ABORT);
        insertCompatible(database, "task_steps", templateValues(), SQLiteDatabase.CONFLICT_ABORT);
        insertCompatible(database, "occurrences", occurrenceValues(), SQLiteDatabase.CONFLICT_ABORT);
        insertCompatible(database, "occurrence_steps", occurrenceStepValues(),
                SQLiteDatabase.CONFLICT_ABORT);
        insertCompatible(database, "stats", statsValues(), SQLiteDatabase.CONFLICT_REPLACE);
        if (tableExists(database, "reward_bookings"))
            insertCompatible(database, "reward_bookings", bookingValues(),
                    SQLiteDatabase.CONFLICT_ABORT);
    }

    private static void insertCompatible(SQLiteDatabase database, String table,
                                         ContentValues superset, int conflict) {
        Set<String> columns = columns(database, table);
        if (columns.isEmpty()) throw new AssertionError("Missing fixture table " + table);
        ContentValues compatible = new ContentValues(superset);
        for (String key : new HashSet<>(compatible.keySet()))
            if (!columns.contains(key)) compatible.remove(key);
        long row = database.insertWithOnConflict(table, null, compatible, conflict);
        if (row == -1L) throw new AssertionError("Could not seed fixture table " + table);
    }

    private static void deleteIfPresent(SQLiteDatabase database, String table,
                                        String column, String value) {
        if (tableExists(database, table))
            database.delete(table, column + " = ?", new String[]{value});
    }

    private static boolean tableExists(SQLiteDatabase database, String table) {
        return !columns(database, table).isEmpty();
    }

    private static Set<String> columns(SQLiteDatabase database, String table) {
        Set<String> result = new HashSet<>();
        try (Cursor cursor = database.rawQuery("PRAGMA table_info(`" + table + "`)", null)) {
            int name = cursor.getColumnIndexOrThrow("name");
            while (cursor.moveToNext()) result.add(cursor.getString(name));
        }
        return result;
    }

    private static int count(SQLiteDatabase database, String table,
                             String column, String value) {
        try (Cursor cursor = database.rawQuery("SELECT COUNT(*) FROM `" + table
                + "` WHERE `" + column + "` = ?", new String[]{value})) {
            assertTrue(cursor.moveToFirst());
            return cursor.getInt(0);
        }
    }

    private static void createExportedSchema(SQLiteDatabase database, int version)
            throws Exception {
        String resource = "de.thonktank.autosecretary.AppDatabase/" + version + ".json";
        try (InputStream stream = InstrumentationRegistry.getInstrumentation()
                .getContext().getAssets().open(resource);
             BufferedReader reader = new BufferedReader(new InputStreamReader(
                     stream, StandardCharsets.UTF_8))) {
            JSONObject exported = new JSONObject(reader.lines().collect(
                    Collectors.joining("\n"))).getJSONObject("database");
            JSONArray entities = exported.getJSONArray("entities");
            for (int index = 0; index < entities.length(); index++) {
                JSONObject entity = entities.getJSONObject(index);
                String table = entity.getString("tableName");
                database.execSQL(entity.getString("createSql")
                        .replace("${TABLE_NAME}", table));
                JSONArray indices = entity.optJSONArray("indices");
                if (indices == null) continue;
                for (int entry = 0; entry < indices.length(); entry++)
                    database.execSQL(indices.getJSONObject(entry).getString("createSql")
                            .replace("${TABLE_NAME}", table));
            }
        }
    }
}
