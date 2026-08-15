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

import java.io.File;
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
    private static final String TITLE = "Upgrade-Daten bleiben erhalten";
    private static final String STEP_TEXT = "Persistierten Schritt lesen";
    private static final String PROBE_PREFERENCES = "upgrade_e2e_probe";
    private static final String PREVIOUS_VERSION = "previous_version";

    @Test public void seedPreviousVersion() throws Exception {
        requirePhase("seed");
        Context context = targetContext();
        File path = context.getDatabasePath(DATABASE);
        assertTrue("The previous app did not create its production database", path.isFile());

        try (SQLiteDatabase database = SQLiteDatabase.openDatabase(
                path.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE)) {
            database.beginTransaction();
            try {
                database.delete("occurrence_steps", "id = ?", new String[]{STEP_ID});
                database.delete("occurrences", "id = ?", new String[]{OCCURRENCE_ID});
                database.delete("task_steps", "id = ?", new String[]{TEMPLATE_ID});
                database.delete("tasks", "id = ?", new String[]{TASK_ID});
                database.insertOrThrow("tasks", null, taskValues());
                database.insertOrThrow("task_steps", null, templateValues());
                database.insertOrThrow("occurrences", null, occurrenceValues());
                database.insertOrThrow("occurrence_steps", null, occurrenceStepValues());
                database.insertWithOnConflict("stats", null, statsValues(),
                        SQLiteDatabase.CONFLICT_REPLACE);
                database.setTransactionSuccessful();
            } finally {
                database.endTransaction();
            }
        }

        context.getSharedPreferences("forest_ui", Context.MODE_PRIVATE).edit()
                .putString("theme_mode", UiThemeMode.DARK.name())
                .putString("calendar_policy", CalendarPolicy.GOOGLE_ONLY.name())
                .putLong("last_update_check", 123_456_789L)
                .putLong("postponed_update_code", 987_654L)
                .putLong("postponed_update_at", 123_450_000L)
                .commit();
        context.getSharedPreferences(PROBE_PREFERENCES, Context.MODE_PRIVATE).edit()
                .putLong(PREVIOUS_VERSION, installedVersion(context))
                .commit();
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
        assertEquals(3, database.getOpenHelper().getReadableDatabase().getVersion());

        TaskEntity task = database.tasks().task(TASK_ID);
        assertNotNull(task);
        assertEquals(TITLE, task.title);
        assertEquals("LATER", task.slot);
        assertEquals(4_001_024L, task.displayOrder);

        List<TaskStepEntity> templates = database.tasks().templates(TASK_ID);
        assertEquals(1, templates.size());
        assertEquals(STEP_TEXT, templates.get(0).text);

        OccurrenceEntity occurrence = database.tasks().occurrence(OCCURRENCE_ID);
        assertNotNull(occurrence);
        assertEquals(TASK_ID, occurrence.taskId);
        assertEquals("OPEN", occurrence.state);

        OccurrenceStepEntity occurrenceStep = database.tasks().occurrenceStep(STEP_ID);
        assertNotNull(occurrenceStep);
        assertEquals(STEP_TEXT, occurrenceStep.text);
        assertTrue(occurrenceStep.done);
        assertEquals(73, database.tasks().stats().xp);

        assertEquals(UiThemeMode.DARK, application.container().uiPreferences.themeMode());
        assertEquals(CalendarPolicy.GOOGLE_ONLY,
                application.container().uiPreferences.calendarPolicy());
        SharedPreferences ui = context.getSharedPreferences("forest_ui", Context.MODE_PRIVATE);
        assertEquals(123_456_789L, ui.getLong("last_update_check", -1L));
        assertEquals(987_654L, ui.getLong("postponed_update_code", -1L));
        assertEquals(123_450_000L, ui.getLong("postponed_update_at", -1L));

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
        return values;
    }

    private static ContentValues templateValues() {
        ContentValues values = new ContentValues();
        values.put("id", TEMPLATE_ID);
        values.put("taskId", TASK_ID);
        values.put("position", 0);
        values.put("text", STEP_TEXT);
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
        return values;
    }

    private static ContentValues occurrenceStepValues() {
        ContentValues values = new ContentValues();
        values.put("id", STEP_ID);
        values.put("occurrenceId", OCCURRENCE_ID);
        values.put("position", 0);
        values.put("text", STEP_TEXT);
        values.put("done", 1);
        return values;
    }

    private static ContentValues statsValues() {
        ContentValues values = new ContentValues();
        values.put("id", 1);
        values.put("xp", 73);
        return values;
    }
}
