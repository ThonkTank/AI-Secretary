package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;

import androidx.room.Room;
import androidx.sqlite.db.SupportSQLiteDatabase;

import de.thonktank.autosecretary.data.local.DatabaseMigrations;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class DatabaseMigrationRobolectricTest {
    private static final String DATABASE = "migration-robolectric-1-to-2";

    private Context context;

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(DATABASE);
    }

    @After public void tearDown() {
        context.deleteDatabase(DATABASE);
    }

    @Test public void roomOpensVersionOneDataThroughTheFullMigrationChain() {
        createVersionOneDatabase();

        AppDatabase migrated = Room.databaseBuilder(context, AppDatabase.class, DATABASE)
                .addMigrations(DatabaseMigrations.MIGRATION_1_2, DatabaseMigrations.MIGRATION_2_3)
                .allowMainThreadQueries()
                .build();
        SupportSQLiteDatabase database = migrated.getOpenHelper().getWritableDatabase();

        try (Cursor cursor = database.query("SELECT id,slot,routineStreakWeeks,lastStreakWeek,displayOrder "
                + "FROM tasks ORDER BY id")) {
            assertEquals(2, cursor.getCount());
            assertTrue(cursor.moveToFirst());
            assertEquals("later", cursor.getString(0));
            assertEquals("LATER", cursor.getString(1));
            assertEquals(0, cursor.getInt(2));
            assertTrue(cursor.getLong(4) >= 4_000_000L);

            assertTrue(cursor.moveToNext());
            assertEquals("morning", cursor.getString(0));
            assertEquals("MORNING", cursor.getString(1));
            assertEquals(1, cursor.getInt(2));
            assertEquals("2026-08-15", cursor.getString(3));
            assertTrue(cursor.getLong(4) >= 1_000_000L && cursor.getLong(4) < 2_000_000L);
        } finally {
            migrated.close();
        }
    }

    @Test public void roomValidatesTheDirectVersionTwoToThreeMigration() {
        createVersionOneDatabase();
        upgradeFixtureToVersionTwo();

        AppDatabase migrated = Room.databaseBuilder(context, AppDatabase.class, DATABASE)
                .addMigrations(DatabaseMigrations.MIGRATION_2_3)
                .allowMainThreadQueries()
                .build();
        SupportSQLiteDatabase database = migrated.getOpenHelper().getWritableDatabase();

        try (Cursor cursor = database.query("SELECT id,slot FROM tasks ORDER BY id")) {
            assertTrue(cursor.moveToFirst());
            assertEquals("LATER", cursor.getString(1));
            assertTrue(cursor.moveToNext());
            assertEquals("MORNING", cursor.getString(1));
        } finally {
            migrated.close();
        }
    }

    private void createVersionOneDatabase() {
        SupportSQLiteOpenHelper.Configuration configuration = SupportSQLiteOpenHelper.Configuration
                .builder(context)
                .name(DATABASE)
                .callback(new SupportSQLiteOpenHelper.Callback(1) {
                    @Override public void onCreate(SupportSQLiteDatabase database) {
                        createVersionOneSchema(database);
                    }

                    @Override public void onUpgrade(SupportSQLiteDatabase database, int oldVersion, int newVersion) {
                        throw new AssertionError("The fixture must stay at schema version 1");
                    }
                })
                .build();
        SupportSQLiteOpenHelper helper = new FrameworkSQLiteOpenHelperFactory().create(configuration);
        SupportSQLiteDatabase database = helper.getWritableDatabase();
        database.execSQL("INSERT INTO tasks (id,title,slot,recurrence,intervalDays,weekdayMask,ongoing,"
                + "conditionText,conditionDone,archived,nextDueOn,lastScheduledOn,lastCompletedOn,"
                + "routineLevel,routineStreak,hasCompletedOccurrence) VALUES "
                + "('morning','Morgenroutine','Morgen','DAILY',1,0,0,'',0,0,'2026-08-16',"
                + "'2026-08-15','2026-08-15',3,4,1),"
                + "('later','Ablage','Unbekannt','ONCE',1,0,0,'',0,0,'2026-08-16','','',1,0,0)");
        helper.close();
    }

    private void upgradeFixtureToVersionTwo() {
        SupportSQLiteOpenHelper.Configuration configuration = SupportSQLiteOpenHelper.Configuration
                .builder(context)
                .name(DATABASE)
                .callback(new SupportSQLiteOpenHelper.Callback(2) {
                    @Override public void onCreate(SupportSQLiteDatabase database) {
                        throw new AssertionError("Version 1 fixture must already exist");
                    }

                    @Override public void onUpgrade(SupportSQLiteDatabase database, int oldVersion, int newVersion) {
                        assertEquals(1, oldVersion);
                        assertEquals(2, newVersion);
                        DatabaseMigrations.MIGRATION_1_2.migrate(database);
                    }
                })
                .build();
        SupportSQLiteOpenHelper helper = new FrameworkSQLiteOpenHelperFactory().create(configuration);
        helper.getWritableDatabase();
        helper.close();
    }

    private static void createVersionOneSchema(SupportSQLiteDatabase database) {
        database.execSQL("CREATE TABLE IF NOT EXISTS tasks (id TEXT NOT NULL, title TEXT NOT NULL, slot TEXT NOT NULL, "
                + "recurrence TEXT NOT NULL, intervalDays INTEGER NOT NULL, weekdayMask INTEGER NOT NULL, ongoing INTEGER NOT NULL, "
                + "conditionText TEXT NOT NULL, conditionDone INTEGER NOT NULL, archived INTEGER NOT NULL, nextDueOn TEXT NOT NULL, "
                + "lastScheduledOn TEXT NOT NULL, lastCompletedOn TEXT NOT NULL, routineLevel INTEGER NOT NULL, "
                + "routineStreak INTEGER NOT NULL, hasCompletedOccurrence INTEGER NOT NULL, PRIMARY KEY(id))");
        database.execSQL("CREATE TABLE IF NOT EXISTS task_steps (id TEXT NOT NULL, taskId TEXT NOT NULL, position INTEGER NOT NULL, "
                + "text TEXT NOT NULL, PRIMARY KEY(id), FOREIGN KEY(taskId) REFERENCES tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE)");
        database.execSQL("CREATE INDEX IF NOT EXISTS index_task_steps_taskId ON task_steps(taskId)");
        database.execSQL("CREATE TABLE IF NOT EXISTS occurrences (id TEXT NOT NULL, taskId TEXT NOT NULL, scheduledOn TEXT NOT NULL, "
                + "state TEXT NOT NULL, sortOrder INTEGER NOT NULL, completedOn TEXT NOT NULL, PRIMARY KEY(id), "
                + "FOREIGN KEY(taskId) REFERENCES tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE)");
        database.execSQL("CREATE INDEX IF NOT EXISTS index_occurrences_taskId ON occurrences(taskId)");
        database.execSQL("CREATE TABLE IF NOT EXISTS occurrence_steps (id TEXT NOT NULL, occurrenceId TEXT NOT NULL, "
                + "position INTEGER NOT NULL, text TEXT NOT NULL, done INTEGER NOT NULL, PRIMARY KEY(id), "
                + "FOREIGN KEY(occurrenceId) REFERENCES occurrences(id) ON UPDATE NO ACTION ON DELETE CASCADE)");
        database.execSQL("CREATE INDEX IF NOT EXISTS index_occurrence_steps_occurrenceId ON occurrence_steps(occurrenceId)");
        database.execSQL("CREATE TABLE IF NOT EXISTS stats (id INTEGER NOT NULL, xp INTEGER NOT NULL, PRIMARY KEY(id))");
        database.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)");
        database.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) "
                + "VALUES(42, 'f4e7f1f252d9e90f53465ed54b2d766f')");
    }
}
