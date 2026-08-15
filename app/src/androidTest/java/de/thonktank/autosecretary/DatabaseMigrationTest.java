package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.database.Cursor;

import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import de.thonktank.autosecretary.data.local.DatabaseMigrations;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

@RunWith(AndroidJUnit4.class)
public final class DatabaseMigrationTest {
    private static final String DATABASE = "migration-1-to-2";

    @Rule public final MigrationTestHelper helper = new MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase.class);

    @After public void deleteDatabase() {
        InstrumentationRegistry.getInstrumentation().getTargetContext().deleteDatabase(DATABASE);
    }

    @Test public void migration1To2PreservesTasksAndInitializesNewFields() throws IOException {
        SupportSQLiteDatabase database = helper.createDatabase(DATABASE, 1);
        database.execSQL("INSERT INTO tasks (id,title,slot,recurrence,intervalDays,weekdayMask,ongoing,"
                + "conditionText,conditionDone,archived,nextDueOn,lastScheduledOn,lastCompletedOn,"
                + "routineLevel,routineStreak,hasCompletedOccurrence) VALUES "
                + "('morning','Morgenroutine','Morgen','DAILY',1,0,0,'',0,0,'2026-08-16',"
                + "'2026-08-15','2026-08-15',3,4,1),"
                + "('later','Ablage','Später','ONCE',1,0,0,'',0,0,'2026-08-16','','',1,0,0)");
        database.close();

        database = helper.runMigrationsAndValidate(
                DATABASE, 2, true, DatabaseMigrations.MIGRATION_1_2);

        try (Cursor cursor = database.query("SELECT id,slot,routineStreakWeeks,lastStreakWeek,displayOrder "
                + "FROM tasks ORDER BY id")) {
            assertEquals(2, cursor.getCount());
            assertTrue(cursor.moveToFirst());
            assertEquals("later", cursor.getString(0));
            assertEquals("Später", cursor.getString(1));
            assertEquals(0, cursor.getInt(2));
            assertEquals("", cursor.getString(3));
            assertTrue(cursor.getLong(4) >= 4_000_000L);

            assertTrue(cursor.moveToNext());
            assertEquals("morning", cursor.getString(0));
            assertEquals("Morgen", cursor.getString(1));
            assertEquals(1, cursor.getInt(2));
            assertEquals("2026-08-15", cursor.getString(3));
            assertTrue(cursor.getLong(4) >= 1_000_000L && cursor.getLong(4) < 2_000_000L);
        }
    }

    @Test public void migration2To3NormalizesSlotsAndCreatesQueryIndices() throws IOException {
        SupportSQLiteDatabase database = helper.createDatabase(DATABASE, 2);
        database.execSQL("INSERT INTO tasks (id,title,slot,recurrence,intervalDays,weekdayMask,ongoing,"
                + "conditionText,conditionDone,archived,nextDueOn,lastScheduledOn,lastCompletedOn,"
                + "routineLevel,routineStreak,routineStreakWeeks,lastStreakWeek,displayOrder,hasCompletedOccurrence) VALUES "
                + "('legacy','Aufgabe','Morgen','ONCE',1,0,0,'',0,0,'2026-08-16','','',1,0,0,'',1000001,0),"
                + "('unknown','Unbekannt','Etwas','ONCE',1,0,0,'',0,0,'2026-08-16','','',1,0,0,'',4000001,0)");
        database.close();

        database = helper.runMigrationsAndValidate(
                DATABASE, 3, true, DatabaseMigrations.MIGRATION_2_3);

        try (Cursor cursor = database.query("SELECT id,slot FROM tasks ORDER BY id")) {
            assertTrue(cursor.moveToFirst());
            assertEquals("legacy", cursor.getString(0));
            assertEquals("MORNING", cursor.getString(1));
            assertTrue(cursor.moveToNext());
            assertEquals("unknown", cursor.getString(0));
            assertEquals("LATER", cursor.getString(1));
        }
        assertIndexExists(database, "index_tasks_archived_conditionDone_displayOrder");
        assertIndexExists(database, "index_occurrences_state_completedOn");
    }

    @Test public void migration1To3HasACompletePath() throws IOException {
        SupportSQLiteDatabase database = helper.createDatabase(DATABASE, 1);
        database.execSQL("INSERT INTO tasks (id,title,slot,recurrence,intervalDays,weekdayMask,ongoing,"
                + "conditionText,conditionDone,archived,nextDueOn,lastScheduledOn,lastCompletedOn,"
                + "routineLevel,routineStreak,hasCompletedOccurrence) VALUES "
                + "('chain','Kette','Mittag','DAILY',1,0,0,'',0,0,'2026-08-16',"
                + "'2026-08-15','2026-08-12',2,3,1)");
        database.close();

        database = helper.runMigrationsAndValidate(DATABASE, 3, true,
                DatabaseMigrations.MIGRATION_1_2, DatabaseMigrations.MIGRATION_2_3);
        try (Cursor cursor = database.query("SELECT title,slot,routineStreakWeeks,lastStreakWeek "
                + "FROM tasks WHERE id='chain'")) {
            assertTrue(cursor.moveToFirst());
            assertEquals("Kette", cursor.getString(0));
            assertEquals("MIDDAY", cursor.getString(1));
            assertEquals(1, cursor.getInt(2));
            assertEquals("2026-08-12", cursor.getString(3));
        }
        database.close();
    }

    private static void assertIndexExists(SupportSQLiteDatabase database, String index) {
        try (Cursor cursor = database.query(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='" + index + "'")) {
            assertTrue(cursor.moveToFirst());
            assertEquals(1, cursor.getInt(0));
        }
    }
}
