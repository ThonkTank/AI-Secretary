package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.database.Cursor;

import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

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
            AppDatabase.class.getCanonicalName(),
            new FrameworkSQLiteOpenHelperFactory());

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
                DATABASE, 2, true, DatabaseProvider.MIGRATION_1_2);

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
}
