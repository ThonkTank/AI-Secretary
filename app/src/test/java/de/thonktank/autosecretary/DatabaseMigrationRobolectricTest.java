package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;

import androidx.room.Room;
import androidx.room.migration.Migration;
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
import org.robolectric.shadows.ShadowLog;
import org.robolectric.annotation.Config;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;

import de.thonktank.autosecretary.data.local.RoomTaskRepository;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.RewardReceipt;
import de.thonktank.autosecretary.domain.usecase.UndoOccurrence;
import de.thonktank.autosecretary.testing.ExportedRoomSchemaFixture;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35})
public final class DatabaseMigrationRobolectricTest {
    private static final String DATABASE = "migration-robolectric-1-to-2";

    private Context context;

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(DATABASE);
        ShadowLog.clear();
    }

    @After public void tearDown() {
        context.deleteDatabase(DATABASE);
    }

    @Test public void roomOpensVersionOneDataThroughTheFullMigrationChain() {
        createVersionOneDatabase();

        AppDatabase migrated = Room.databaseBuilder(context, AppDatabase.class, DATABASE)
                .addMigrations(DatabaseMigrations.from(1))
                .allowMainThreadQueries()
                .build();
        SupportSQLiteDatabase database = migrated.getOpenHelper().getWritableDatabase();

        try (Cursor cursor = database.query("SELECT tasks.id,task_schedule_entries.slot,"
                + "tasks.catalogOrder FROM tasks JOIN task_schedule_entries "
                + "ON task_schedule_entries.taskId=tasks.id ORDER BY tasks.id")) {
            assertEquals(2, cursor.getCount());
            assertTrue(cursor.moveToFirst());
            assertEquals("later", cursor.getString(0));
            assertEquals("LATER", cursor.getString(1));
            assertTrue(cursor.getLong(2) >= 4_000_000L);

            assertTrue(cursor.moveToNext());
            assertEquals("morning", cursor.getString(0));
            assertEquals("MORNING", cursor.getString(1));
            assertTrue(cursor.getLong(2) >= 1_000_000L && cursor.getLong(2) < 2_000_000L);
            try (Cursor optional = database.query("SELECT lastScheduledOn,lastCompletedOn,"
                    + "boundUntilOn,deadlineOn FROM tasks WHERE id='later'")) {
                assertTrue(optional.moveToFirst());
                assertTrue(optional.isNull(0));
                assertTrue(optional.isNull(1));
                assertTrue(optional.isNull(2));
                assertTrue(optional.isNull(3));
            }
            assertXpAndZeroCombos(database, 53);
            assertLegacyColumnsRemoved(database);
        } finally {
            migrated.close();
        }
    }

    @Test public void roomValidatesTheDirectVersionTwoToThreeMigration() {
        createVersionOneDatabase();
        upgradeFixtureToVersionTwo();

        AppDatabase migrated = Room.databaseBuilder(context, AppDatabase.class, DATABASE)
                .addMigrations(DatabaseMigrations.from(2))
                .allowMainThreadQueries()
                .build();
        SupportSQLiteDatabase database = migrated.getOpenHelper().getWritableDatabase();

        try (Cursor cursor = database.query("SELECT tasks.id,task_schedule_entries.slot "
                + "FROM tasks JOIN task_schedule_entries "
                + "ON task_schedule_entries.taskId=tasks.id ORDER BY tasks.id")) {
            assertTrue(cursor.moveToFirst());
            assertEquals("LATER", cursor.getString(1));
            assertTrue(cursor.moveToNext());
            assertEquals("MORNING", cursor.getString(1));
            assertXpAndZeroCombos(database, 53);
            assertLegacyColumnsRemoved(database);
        } finally {
            migrated.close();
        }
    }

    @Test public void migrationElevenToTwelveExpandsEveryConfiguredTimePlacement() {
        SupportSQLiteOpenHelper.Configuration configuration = SupportSQLiteOpenHelper.Configuration
                .builder(context).name(DATABASE)
                .callback(new SupportSQLiteOpenHelper.Callback(11) {
                    @Override public void onCreate(SupportSQLiteDatabase database) {
                        ExportedRoomSchemaFixture.create(database, 11);
                    }
                    @Override public void onUpgrade(SupportSQLiteDatabase database,
                                                    int oldVersion, int newVersion) { }
                }).build();
        SupportSQLiteOpenHelper helper = new FrameworkSQLiteOpenHelperFactory()
                .create(configuration);
        SupportSQLiteDatabase database = helper.getWritableDatabase();
        database.execSQL("INSERT INTO tasks(id,title,slot,recurrence,intervalDays,weekdayMask,"
                + "ongoing,conditionText,conditionDone,archived,nextDueOn,lastScheduledOn,"
                + "lastCompletedOn,displayOrder,hasCompletedOccurrence,estimatedMinutes,"
                + "timeOfDayMask,boundKind,boundUntilOn,boundWeeks,remainingCount,deadlineOn,note) "
                + "VALUES ('multi','Routine','MORNING','DAILY',1,0,0,'',0,0,'2026-08-20',"
                + "NULL,NULL,2048,0,NULL,5,'FOREVER',NULL,NULL,NULL,NULL,''),"
                + "('once','Einmal','LATER','ONCE',1,0,0,'',0,0,'2026-08-20',NULL,NULL,"
                + "4096,0,NULL,0,'FOREVER',NULL,NULL,NULL,NULL,''),"
                + "('fallback','Alt','MIDDAY','DAILY',1,0,0,'',0,0,'2026-08-20',NULL,NULL,"
                + "3072,0,NULL,16,'FOREVER',NULL,NULL,NULL,NULL,'')");
        helper.close();

        AppDatabase migrated = Room.databaseBuilder(context, AppDatabase.class, DATABASE)
                .addMigrations(DatabaseMigrations.from(11))
                .allowMainThreadQueries().build();
        database = migrated.getOpenHelper().getWritableDatabase();
        try (Cursor cursor = database.query("SELECT taskId,slot,displayOrder "
                + "FROM task_schedule_entries ORDER BY taskId,slot")) {
            assertEquals(4, cursor.getCount());
            assertTrue(cursor.moveToFirst());
            assertEquals("fallback", cursor.getString(0)); assertEquals("MIDDAY", cursor.getString(1));
            assertTrue(cursor.moveToNext());
            assertEquals("multi", cursor.getString(0)); assertEquals("EVENING", cursor.getString(1));
            assertEquals(2048, cursor.getLong(2));
            assertTrue(cursor.moveToNext()); assertEquals("MORNING", cursor.getString(1));
            assertTrue(cursor.moveToNext()); assertEquals("once", cursor.getString(0));
            assertEquals("LATER", cursor.getString(1));
        } finally {
            migrated.close();
        }
    }

    @Test public void migrationTwelveToThirteenKeepsCatalogScheduleAndDependentRowsLossless() {
        SupportSQLiteOpenHelper.Configuration configuration = SupportSQLiteOpenHelper.Configuration
                .builder(context).name(DATABASE)
                .callback(new SupportSQLiteOpenHelper.Callback(12) {
                    @Override public void onCreate(SupportSQLiteDatabase database) {
                        ExportedRoomSchemaFixture.create(database, 12);
                    }
                    @Override public void onUpgrade(SupportSQLiteDatabase database,
                                                    int oldVersion, int newVersion) { }
                }).build();
        SupportSQLiteOpenHelper helper = new FrameworkSQLiteOpenHelperFactory()
                .create(configuration);
        SupportSQLiteDatabase old = helper.getWritableDatabase();
        old.execSQL("INSERT INTO tasks(id,title,slot,recurrence,intervalDays,weekdayMask,"
                + "ongoing,conditionText,conditionDone,archived,nextDueOn,lastScheduledOn,"
                + "lastCompletedOn,displayOrder,hasCompletedOccurrence,estimatedMinutes,"
                + "timeOfDayMask,boundKind,boundUntilOn,boundWeeks,remainingCount,deadlineOn,note) "
                + "VALUES ('task','Routine','MORNING','DAILY',1,0,0,'',0,0,'2026-08-21',"
                + "NULL,NULL,4096,0,25,5,'FOREVER',NULL,NULL,NULL,NULL,'Notiz')");
        old.execSQL("INSERT INTO task_schedule_entries(id,taskId,slot,displayOrder) VALUES "
                + "('morning','task','MORNING',2048),('evening','task','EVENING',3072)");
        old.execSQL("INSERT INTO task_steps(id,taskId,position,text,weekdayMask,amountKind,"
                + "plannedSets,plannedReps,plannedDurationSeconds,note) VALUES "
                + "('step','task',0,'Vorlage',0,'NONE',NULL,NULL,NULL,'')");
        helper.close();

        AppDatabase migrated = Room.databaseBuilder(context, AppDatabase.class, DATABASE)
                .addMigrations(DatabaseMigrations.from(12))
                .allowMainThreadQueries().build();
        SupportSQLiteDatabase database = migrated.getOpenHelper().getWritableDatabase();
        try (Cursor cursor = database.query("SELECT title,catalogOrder,estimatedMinutes,note "
                + "FROM tasks WHERE id='task'")) {
            assertTrue(cursor.moveToFirst());
            assertEquals("Routine", cursor.getString(0));
            assertEquals(4096L, cursor.getLong(1));
            assertEquals(25, cursor.getInt(2));
            assertEquals("Notiz", cursor.getString(3));
        }
        try (Cursor cursor = database.query("SELECT id,slot,displayOrder FROM "
                + "task_schedule_entries WHERE taskId='task' ORDER BY slot")) {
            assertEquals(2, cursor.getCount());
            assertTrue(cursor.moveToFirst());
            assertEquals("evening", cursor.getString(0));
            assertEquals("EVENING", cursor.getString(1));
            assertEquals(3072L, cursor.getLong(2));
            assertTrue(cursor.moveToNext());
            assertEquals("morning", cursor.getString(0));
            assertEquals("MORNING", cursor.getString(1));
            assertEquals(2048L, cursor.getLong(2));
        }
        try (Cursor cursor = database.query("SELECT text FROM task_steps WHERE taskId='task'")) {
            assertTrue(cursor.moveToFirst());
            assertEquals("Vorlage", cursor.getString(0));
        }
        assertLegacyColumnsRemoved(database);
        migrated.close();
    }

    @Test public void migrationThirteenToFourteenKeepsLedgerAndStartsEmptyAssignments() {
        SupportSQLiteOpenHelper.Configuration configuration = SupportSQLiteOpenHelper.Configuration
                .builder(context).name(DATABASE)
                .callback(new SupportSQLiteOpenHelper.Callback(13) {
                    @Override public void onCreate(SupportSQLiteDatabase database) {
                        ExportedRoomSchemaFixture.create(database, 13);
                    }
                    @Override public void onUpgrade(SupportSQLiteDatabase database,
                                                    int oldVersion, int newVersion) { }
                }).build();
        SupportSQLiteOpenHelper helper = new FrameworkSQLiteOpenHelperFactory()
                .create(configuration);
        SupportSQLiteDatabase old = helper.getWritableDatabase();
        old.execSQL("INSERT INTO tasks(id,title,recurrence,intervalDays,weekdayMask,ongoing,"
                + "conditionText,conditionDone,archived,nextDueOn,lastScheduledOn,lastCompletedOn,"
                + "catalogOrder,hasCompletedOccurrence,estimatedMinutes,boundKind,boundUntilOn,"
                + "boundWeeks,remainingCount,deadlineOn,note) VALUES "
                + "('task','Routine','DAILY',1,0,0,'',0,0,'2026-08-21',NULL,NULL,1,0,NULL,"
                + "'FOREVER',NULL,NULL,NULL,NULL,'')");
        old.execSQL("INSERT INTO occurrences(id,taskId,scheduledOn,state,sortOrder,completedOn,slot) "
                + "VALUES ('occ','task','2026-08-21','OPEN',1,NULL,'MORNING')");
        old.execSQL("INSERT INTO reward_bookings(id,transactionId,occurrenceId,occurrenceStepId,"
                + "ownerId,kind,target,xpDelta,comboPointDelta,bookedOn,reversesBookingId) VALUES "
                + "('booking','tx','occ',NULL,'task:task','SINGLE_COMPLETION','HEAD',10,0,"
                + "'2026-08-21',NULL)");
        helper.close();

        AppDatabase migrated = Room.databaseBuilder(context, AppDatabase.class, DATABASE)
                .addMigrations(DatabaseMigrations.from(13))
                .allowMainThreadQueries().build();
        SupportSQLiteDatabase database = migrated.getOpenHelper().getWritableDatabase();
        try (Cursor cursor = database.query("SELECT occurrenceId,xpDelta FROM reward_bookings "
                + "WHERE id='booking'")) {
            assertTrue(cursor.moveToFirst());
            assertEquals("occ", cursor.getString(0));
            assertEquals(10, cursor.getInt(1));
        }
        try (Cursor cursor = database.query("SELECT COUNT(*) FROM reward_assignments")) {
            assertTrue(cursor.moveToFirst());
            assertEquals(0, cursor.getInt(0));
        }
        migrated.close();
    }

    @Test public void migrationFourteenToFifteenKeepsStepsAndDefaultsTheirInterval() {
        SupportSQLiteOpenHelper.Configuration configuration = SupportSQLiteOpenHelper.Configuration
                .builder(context).name(DATABASE)
                .callback(new SupportSQLiteOpenHelper.Callback(14) {
                    @Override public void onCreate(SupportSQLiteDatabase database) {
                        ExportedRoomSchemaFixture.create(database, 14);
                    }
                    @Override public void onUpgrade(SupportSQLiteDatabase database,
                                                    int oldVersion, int newVersion) { }
                }).build();
        SupportSQLiteOpenHelper helper = new FrameworkSQLiteOpenHelperFactory()
                .create(configuration);
        SupportSQLiteDatabase old = helper.getWritableDatabase();
        old.execSQL("INSERT INTO tasks(id,title,recurrence,intervalDays,weekdayMask,ongoing,"
                + "conditionText,conditionDone,archived,nextDueOn,lastScheduledOn,lastCompletedOn,"
                + "catalogOrder,hasCompletedOccurrence,estimatedMinutes,boundKind,boundUntilOn,"
                + "boundWeeks,remainingCount,deadlineOn,note) VALUES "
                + "('task','Routine','DAILY',1,0,0,'',0,0,'2026-08-21',NULL,NULL,1,0,NULL,"
                + "'FOREVER',NULL,NULL,NULL,NULL,'')");
        old.execSQL("INSERT INTO task_steps(id,taskId,position,text,weekdayMask,amountKind,"
                + "plannedSets,plannedReps,plannedDurationSeconds,note) VALUES "
                + "('step','task',0,'Vorlage',0,'NONE',NULL,NULL,NULL,'')");
        helper.close();

        AppDatabase migrated = Room.databaseBuilder(context, AppDatabase.class, DATABASE)
                .addMigrations(DatabaseMigrations.from(14))
                .allowMainThreadQueries().build();
        try (Cursor cursor = migrated.getOpenHelper().getWritableDatabase().query(
                "SELECT text,intervalDays FROM task_steps WHERE id='step'")) {
            assertTrue(cursor.moveToFirst());
            assertEquals("Vorlage", cursor.getString(0));
            assertEquals(0, cursor.getInt(1));
        }
        migrated.close();
    }

    @Test public void migrationRegistryProvidesEveryContiguousPathToTheCentralVersion() {
        Migration[] all = DatabaseMigrations.all();
        assertEquals(DatabaseContract.VERSION - 1, all.length);
        for (int source = 1; source <= DatabaseContract.VERSION; source++) {
            Migration[] path = DatabaseMigrations.from(source);
            assertEquals(DatabaseContract.VERSION - source, path.length);
            for (int index = 0; index < path.length; index++) {
                assertEquals(source + index, path[index].startVersion);
                assertEquals(source + index + 1, path[index].endVersion);
            }
        }
    }

    @Test public void everySupportedSchemaOpensAtTheCentralVersion() {
        for (int source = DatabaseContract.PRODUCTION_UPGRADE_SOURCE_VERSION;
             source < DatabaseContract.VERSION; source++) {
            String name = DATABASE + "-supported-" + source;
            context.deleteDatabase(name);
            SupportSQLiteOpenHelper.Configuration configuration =
                    SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                    .callback(new SupportSQLiteOpenHelper.Callback(source) {
                        @Override public void onCreate(SupportSQLiteDatabase database) {
                            ExportedRoomSchemaFixture.create(database, version);
                        }
                        @Override public void onUpgrade(SupportSQLiteDatabase database,
                                                        int oldVersion, int newVersion) { }
                    }).build();
            SupportSQLiteOpenHelper helper = new FrameworkSQLiteOpenHelperFactory()
                    .create(configuration);
            helper.getWritableDatabase();
            helper.close();

            AppDatabase migrated = Room.databaseBuilder(context, AppDatabase.class, name)
                    .addMigrations(DatabaseMigrations.from(source))
                    .allowMainThreadQueries().build();
            assertEquals(DatabaseContract.VERSION,
                    migrated.getOpenHelper().getWritableDatabase().getVersion());
            migrated.close();
            context.deleteDatabase(name);
        }
    }

    @Test public void migrationTwentyToTwentyOnePreservesFlowsAndAddsTrainingSnapshots() {
        SupportSQLiteOpenHelper.Configuration configuration =
                SupportSQLiteOpenHelper.Configuration.builder(context).name(DATABASE)
                        .callback(new SupportSQLiteOpenHelper.Callback(20) {
                            @Override public void onCreate(SupportSQLiteDatabase database) {
                                ExportedRoomSchemaFixture.create(database, 20);
                                database.execSQL("INSERT INTO capacity_resources "
                                        + "(id,name,normalizedName,capacity) VALUES "
                                        + "('rack','Rack','rack',1)");
                            }
                            @Override public void onUpgrade(SupportSQLiteDatabase database,
                                                            int oldVersion, int newVersion) { }
                        }).build();
        SupportSQLiteOpenHelper helper = new FrameworkSQLiteOpenHelperFactory()
                .create(configuration);
        helper.getWritableDatabase();
        helper.close();

        AppDatabase migrated = Room.databaseBuilder(context, AppDatabase.class, DATABASE)
                .addMigrations(DatabaseMigrations.from(20))
                .allowMainThreadQueries().build();
        SupportSQLiteDatabase database = migrated.getOpenHelper().getWritableDatabase();

        assertEquals(DatabaseContract.VERSION, database.getVersion());
        try (Cursor cursor = database.query(
                "SELECT name,capacity FROM capacity_resources WHERE id='rack'")) {
            assertTrue(cursor.moveToFirst());
            assertEquals("Rack", cursor.getString(0));
            assertEquals(1, cursor.getInt(1));
        }
        assertColumnsPresent(database, "task_steps", "assistantEnabled", "plannedLoadMode");
        assertColumnsPresent(database, "occurrence_steps", "plannedLoadMode", "targetRir");
        assertColumnsPresent(database, "flow_run_steps", "plannedLoadMode", "targetRir");
        try (Cursor cursor = database.query("SELECT COUNT(*) FROM sqlite_master "
                + "WHERE type='table' AND name='training_adjustments'")) {
            assertTrue(cursor.moveToFirst());
            assertEquals(1, cursor.getInt(0));
        }
        migrated.close();
    }

    @Test public void migrationTwentyOneToTwentyTwoKeepsDependentRowsAndRemovesIncrement() {
        SupportSQLiteOpenHelper.Configuration configuration =
                SupportSQLiteOpenHelper.Configuration.builder(context).name(DATABASE)
                        .callback(new SupportSQLiteOpenHelper.Callback(21) {
                            @Override public void onCreate(SupportSQLiteDatabase database) {
                                ExportedRoomSchemaFixture.create(database, 21);
                            }
                            @Override public void onUpgrade(SupportSQLiteDatabase database,
                                                            int oldVersion, int newVersion) { }
                        }).build();
        SupportSQLiteOpenHelper helper = new FrameworkSQLiteOpenHelperFactory()
                .create(configuration);
        SupportSQLiteDatabase old = helper.getWritableDatabase();
        old.execSQL("INSERT INTO tasks(id,title,recurrence,intervalDays,weekdayMask,ongoing,"
                + "conditionText,conditionDone,archived,nextDueOn,catalogOrder,"
                + "hasCompletedOccurrence,boundKind,note,missedOccurrenceMode) VALUES "
                + "('task','Training','DAILY',1,0,0,'',0,0,'2026-08-31',1,0,"
                + "'FOREVER','','SKIP')");
        old.execSQL("INSERT INTO task_steps(id,taskId,position,text,weekdayMask,intervalDays,"
                + "amountKind,plannedSets,plannedReps,restTimerMode,assistantEnabled,"
                + "assistantMinSets,assistantMaxSets,assistantMinReps,assistantMaxReps,"
                + "assistantTargetRir,assistantLoadIncrementMilli,assistantWeeklySetCeiling,"
                + "plannedLoadMode,plannedLoadUnit,plannedLoadMilli,secondaryMuscles,"
                + "assistantStatus,assistantObservations,assistantReadyStreak,"
                + "assistantHardStreak,note,activationKind) VALUES "
                + "('first','task',0,'Rudern',0,0,'SETS_REPS',3,12,'INHERIT',1,2,4,8,12,"
                + "2,2500,10,'EXTERNAL','KG',50000,'','ACTIVE',5,1,0,'','SCHEDULED'),"
                + "('second','task',1,'Zug',0,0,'SETS_REPS',3,12,'INHERIT',0,2,3,8,12,"
                + "2,2500,10,'EXTERNAL','KG',40000,'','DISABLED',0,0,0,'','SCHEDULED')");
        old.execSQL("INSERT INTO capacity_resources(id,name,normalizedName,capacity) "
                + "VALUES ('rack','Rack','rack',1)");
        old.execSQL("INSERT INTO step_transitions(sourceStepId,targetStepId,delayMode,"
                + "defaultDelayMillis,lastUsedDelayMillis) VALUES "
                + "('first','second','IMMEDIATE',0,NULL)");
        old.execSQL("INSERT INTO step_resource_leases(id,taskId,acquireStepId,releaseStepId,"
                + "resourceId,units) VALUES ('lease','task','first','second','rack',1)");
        old.execSQL("INSERT INTO training_adjustments(id,templateId,sourceOccurrenceStepId,"
                + "reason,beforeSets,beforeReps,beforeLoadMode,beforeLoadUnit,beforeLoadMilli,"
                + "afterSets,afterReps,afterLoadMode,afterLoadUnit,afterLoadMilli,createdOn,state)"
                + " VALUES ('adjust','first','occ-step','REPETITIONS_INCREASED',3,11,"
                + "'EXTERNAL','KG',50000,3,12,'EXTERNAL','KG',50000,'2026-08-31','APPLIED')");
        helper.close();

        AppDatabase migrated = Room.databaseBuilder(context, AppDatabase.class, DATABASE)
                .addMigrations(DatabaseMigrations.from(21)).allowMainThreadQueries().build();
        SupportSQLiteDatabase database = migrated.getOpenHelper().getWritableDatabase();
        try (Cursor cursor = database.query("SELECT text,plannedLoadMilli FROM task_steps "
                + "WHERE id='first'")) {
            assertTrue(cursor.moveToFirst());
            assertEquals("Rudern", cursor.getString(0));
            assertEquals(50_000L, cursor.getLong(1));
        }
        try (Cursor cursor = database.query("SELECT targetStepId FROM step_transitions "
                + "WHERE sourceStepId='first'")) {
            assertTrue(cursor.moveToFirst()); assertEquals("second", cursor.getString(0));
        }
        try (Cursor cursor = database.query("SELECT resourceId FROM step_resource_leases "
                + "WHERE id='lease'")) {
            assertTrue(cursor.moveToFirst()); assertEquals("rack", cursor.getString(0));
        }
        try (Cursor cursor = database.query("SELECT reason,auditOrder,ruleVersion FROM "
                + "training_adjustments WHERE id='adjust'")) {
            assertTrue(cursor.moveToFirst());
            assertEquals("REPETITIONS_INCREASED", cursor.getString(0));
            assertTrue(cursor.getLong(1) > 0); assertEquals(1, cursor.getInt(2));
        }
        try (Cursor cursor = database.query("PRAGMA table_info(task_steps)")) {
            while (cursor.moveToNext())
                if ("assistantLoadIncrementMilli".equals(cursor.getString(1)))
                    fail("Obsolete increment column survived schema 22");
        }
        migrated.close();
    }

    @Test public void migrationFifteenToSixteenDerivesStableCadenceAnchors() {
        SupportSQLiteOpenHelper.Configuration configuration = SupportSQLiteOpenHelper.Configuration
                .builder(context).name(DATABASE)
                .callback(new SupportSQLiteOpenHelper.Callback(15) {
                    @Override public void onCreate(SupportSQLiteDatabase database) {
                        ExportedRoomSchemaFixture.create(database, 15);
                    }
                    @Override public void onUpgrade(SupportSQLiteDatabase database,
                                                    int oldVersion, int newVersion) { }
                }).build();
        SupportSQLiteOpenHelper helper = new FrameworkSQLiteOpenHelperFactory()
                .create(configuration);
        SupportSQLiteDatabase old = helper.getWritableDatabase();
        insertVersionFifteenTask(old, "history", false, "2026-08-23");
        insertVersionFifteenTask(old, "cursor", false, "2026-08-25");
        insertVersionFifteenTask(old, "archive", true, "");
        old.execSQL("INSERT INTO occurrences(id,taskId,scheduledOn,state,sortOrder,"
                + "completedOn,slot) VALUES "
                + "('later','history','2026-08-20','COMPLETED',2,'2026-08-20','MORNING'),"
                + "('first','history','2026-08-18','COMPLETED',1,'2026-08-18','MORNING')");
        helper.close();

        AppDatabase migrated = Room.databaseBuilder(context, AppDatabase.class, DATABASE)
                .addMigrations(DatabaseMigrations.from(15))
                .allowMainThreadQueries().build();
        SupportSQLiteDatabase database = migrated.getOpenHelper().getWritableDatabase();
        assertCadenceAnchor(database, "history", "2026-08-18");
        assertCadenceAnchor(database, "cursor", "2026-08-25");
        assertCadenceAnchor(database, "archive", null);
        migrated.close();
    }

    @Test public void supportedVersionEightUpgradeThroughSixteenIsLossless() {
        SupportSQLiteOpenHelper.Configuration configuration = SupportSQLiteOpenHelper.Configuration
                .builder(context).name(DATABASE)
                .callback(new SupportSQLiteOpenHelper.Callback(8) {
                    @Override public void onCreate(SupportSQLiteDatabase database) {
                        ExportedRoomSchemaFixture.create(database, 8);
                    }
                    @Override public void onUpgrade(SupportSQLiteDatabase database,
                                                    int oldVersion, int newVersion) { }
                }).build();
        SupportSQLiteOpenHelper helper = new FrameworkSQLiteOpenHelperFactory()
                .create(configuration);
        SupportSQLiteDatabase old = helper.getWritableDatabase();
        old.execSQL("INSERT INTO tasks(id,title,slot,recurrence,intervalDays,weekdayMask,"
                + "ongoing,conditionText,conditionDone,archived,nextDueOn,lastScheduledOn,"
                + "lastCompletedOn,displayOrder,hasCompletedOccurrence,estimatedMinutes,"
                + "timeOfDayMask,boundKind,boundUntilOn,boundWeeks,remainingCount,deadlineOn,note) "
                + "VALUES ('v8','Produktionsstand','LATER','ONCE',1,0,0,'',0,0,'2999-12-31',"
                + "'','',4001024,1,NULL,0,'FOREVER','',NULL,NULL,'','')");
        old.execSQL("INSERT INTO task_steps(id,taskId,position,text,weekdayMask,amountKind,"
                + "plannedSets,plannedReps,plannedDurationSeconds,note) VALUES "
                + "('template-v8','v8',0,'Schritt',0,'NONE',NULL,NULL,NULL,'')");
        old.execSQL("INSERT INTO occurrences(id,taskId,scheduledOn,state,sortOrder,completedOn,slot) "
                + "VALUES ('occ-v8','v8','2999-12-31','OPEN',42,'','LATER')");
        old.execSQL("INSERT INTO occurrence_steps(id,occurrenceId,position,text,done,amountKind,"
                + "plannedSets,plannedReps,plannedDurationSeconds,note,actualRepetitions,"
                + "sourceTemplateId,comboOwnerId) VALUES "
                + "('step-v8','occ-v8',0,'Schritt',1,'NONE',NULL,NULL,NULL,'','',"
                + "'template-v8','step:template-v8')");
        old.execSQL("INSERT OR REPLACE INTO stats(id,xp) VALUES (1,73)");
        old.execSQL("INSERT INTO reward_bookings(id,transactionId,occurrenceId,occurrenceStepId,"
                + "ownerId,kind,target,xpDelta,comboPointDelta,bookedOn,reversesBookingId) VALUES "
                + "('booking-v8','transaction-v8','occ-v8','step-v8','step:template-v8',"
                + "'LEGACY_STEP','VESSEL',10,0,'2999-12-31',NULL)");
        helper.close();

        AppDatabase migrated = Room.databaseBuilder(context, AppDatabase.class, DATABASE)
                .addMigrations(DatabaseMigrations.from(8))
                .allowMainThreadQueries().build();
        SupportSQLiteDatabase database = migrated.getOpenHelper().getWritableDatabase();
        try (Cursor cursor = database.query("SELECT title,catalogOrder,cadenceAnchorOn "
                + "FROM tasks WHERE id='v8'")) {
            assertTrue(cursor.moveToFirst());
            assertEquals("Produktionsstand", cursor.getString(0));
            assertEquals(4_001_024L, cursor.getLong(1));
            assertEquals("2999-12-31", cursor.getString(2));
        }
        try (Cursor cursor = database.query("SELECT slot,displayOrder FROM "
                + "task_schedule_entries WHERE taskId='v8'")) {
            assertTrue(cursor.moveToFirst());
            assertEquals("LATER", cursor.getString(0));
            assertEquals(4_001_024L, cursor.getLong(1));
        }
        try (Cursor cursor = database.query("SELECT sourceTemplateId,done FROM occurrence_steps "
                + "WHERE id='step-v8'")) {
            assertTrue(cursor.moveToFirst());
            assertEquals("template-v8", cursor.getString(0));
            assertEquals(1, cursor.getInt(1));
        }
        try (Cursor cursor = database.query("SELECT xpDelta FROM reward_bookings "
                + "WHERE id='booking-v8'")) {
            assertTrue(cursor.moveToFirst());
            assertEquals(10, cursor.getInt(0));
        }
        try (Cursor cursor = database.query("SELECT xp FROM stats WHERE id=1")) {
            assertTrue(cursor.moveToFirst());
            assertEquals(73, cursor.getInt(0));
        }
        assertLegacyColumnsRemoved(database);
        migrated.close();
    }

    private static void insertVersionFifteenTask(SupportSQLiteDatabase database, String id,
                                                  boolean archived, String nextDueOn) {
        database.execSQL("INSERT INTO tasks(id,title,recurrence,intervalDays,weekdayMask,"
                + "ongoing,conditionText,conditionDone,archived,nextDueOn,lastScheduledOn,"
                + "lastCompletedOn,catalogOrder,hasCompletedOccurrence,estimatedMinutes,"
                + "boundKind,boundUntilOn,boundWeeks,remainingCount,deadlineOn,note) VALUES "
                + "('" + id + "','Task','DAILY',1,0,0,'',0," + (archived ? 1 : 0)
                + ",'" + nextDueOn + "',NULL,NULL,1,0,NULL,'FOREVER',NULL,NULL,NULL,NULL,'')");
    }

    private static void assertCadenceAnchor(SupportSQLiteDatabase database, String taskId,
                                            String expected) {
        try (Cursor cursor = database.query("SELECT cadenceAnchorOn FROM tasks WHERE id='"
                + taskId + "'")) {
            assertTrue(cursor.moveToFirst());
            if (expected == null) assertTrue(cursor.isNull(0));
            else assertEquals(expected, cursor.getString(0));
        }
    }

    @Test public void migrationThreeToFourPreservesHistoryAndConvertsOngoingDefinitions() {
        createVersionOneDatabase();
        upgradeFixtureToVersionThree();
        SupportSQLiteOpenHelper.Configuration configuration = SupportSQLiteOpenHelper.Configuration
                .builder(context).name(DATABASE)
                .callback(new SupportSQLiteOpenHelper.Callback(3) {
                    @Override public void onCreate(SupportSQLiteDatabase database) { }
                    @Override public void onUpgrade(SupportSQLiteDatabase database,
                                                    int oldVersion, int newVersion) { }
                }).build();
        SupportSQLiteOpenHelper helper = new FrameworkSQLiteOpenHelperFactory().create(configuration);
        SupportSQLiteDatabase old = helper.getWritableDatabase();
        old.execSQL("INSERT INTO tasks (id,title,slot,recurrence,intervalDays,weekdayMask,ongoing,"
                + "conditionText,conditionDone,archived,nextDueOn,lastScheduledOn,lastCompletedOn,"
                + "routineLevel,routineStreak,routineStreakWeeks,lastStreakWeek,displayOrder,"
                + "hasCompletedOccurrence) VALUES ('project','Praktikum','LATER','ONCE',1,0,1,"
                + "'Vertrag unterschrieben',0,0,'2026-08-17','','',2,3,4,'2026-08-10',4096,1)");
        old.execSQL("INSERT INTO task_steps (id,taskId,position,text) VALUES "
                + "('template','morning',0,'Duschen')");
        old.execSQL("INSERT INTO occurrences (id,taskId,scheduledOn,state,sortOrder,completedOn) "
                + "VALUES ('open','morning','2026-08-17','OPEN',1,'')");
        old.execSQL("INSERT INTO occurrence_steps (id,occurrenceId,position,text,done) VALUES "
                + "('snapshot','open',0,'Duschen',1)");
        old.execSQL("INSERT OR REPLACE INTO stats (id,xp) VALUES (1,70)");
        helper.close();

        AppDatabase migrated = Room.databaseBuilder(context, AppDatabase.class, DATABASE)
                .addMigrations(DatabaseMigrations.from(3))
                .allowMainThreadQueries().build();
        SupportSQLiteDatabase database = migrated.getOpenHelper().getWritableDatabase();
        try (Cursor cursor = database.query("SELECT recurrence,ongoing,note,archived FROM tasks "
                + "WHERE id='project'")) {
            assertTrue(cursor.moveToFirst());
            assertEquals("ONCE", cursor.getString(0));
            assertEquals(0, cursor.getInt(1));
            assertEquals("Erledigt, wenn: Vertrag unterschrieben", cursor.getString(2));
            assertEquals(0, cursor.getInt(3));
        }
        try (Cursor cursor = database.query("SELECT slot FROM occurrences WHERE id='open'")) {
            assertTrue(cursor.moveToFirst()); assertEquals("MORNING", cursor.getString(0));
        }
        try (Cursor cursor = database.query("SELECT amountKind,actualRepetitions,done "
                + "FROM occurrence_steps WHERE id='snapshot'")) {
            assertTrue(cursor.moveToFirst()); assertEquals("NONE", cursor.getString(0));
            assertEquals("", cursor.getString(1)); assertEquals(1, cursor.getInt(2));
        }
        try (Cursor cursor = database.query("SELECT xp FROM stats WHERE id=1")) {
            assertTrue(cursor.moveToFirst()); assertEquals(70, cursor.getInt(0));
        }
        assertXpAndZeroCombos(database, 70);
        assertLegacyColumnsRemoved(database);
        migrated.close();
    }

    @Test public void migrationFiveToSevenKeepsUnknownOwnerWithoutInventingTemplateId() {
        createVersionOneDatabase();
        upgradeFixtureToVersionFive();
        SupportSQLiteOpenHelper.Configuration configuration = SupportSQLiteOpenHelper.Configuration
                .builder(context).name(DATABASE)
                .callback(new SupportSQLiteOpenHelper.Callback(5) {
                    @Override public void onCreate(SupportSQLiteDatabase database) { }
                    @Override public void onUpgrade(SupportSQLiteDatabase database,
                                                    int oldVersion, int newVersion) { }
                }).build();
        SupportSQLiteOpenHelper helper = new FrameworkSQLiteOpenHelperFactory().create(configuration);
        SupportSQLiteDatabase old = helper.getWritableDatabase();
        old.execSQL("INSERT INTO occurrences (id,taskId,scheduledOn,state,sortOrder,completedOn,"
                + "slot,awardedXp,comboPointDelta) VALUES "
                + "('unknown-occ','morning','2026-08-18','OPEN',1,'','MORNING',0,0)");
        old.execSQL("INSERT INTO occurrence_steps (id,occurrenceId,position,text,done,amountKind,"
                + "plannedSets,plannedReps,plannedDurationSeconds,note,actualRepetitions,"
                + "comboOwnerId,earnedXp,comboPointDelta) VALUES "
                + "('unknown-step','unknown-occ',0,'Historisch',1,'NONE',NULL,NULL,NULL,'','',"
                + "'step:missing-template',10,0)");
        helper.close();

        AppDatabase migrated = Room.databaseBuilder(context, AppDatabase.class, DATABASE)
                .addMigrations(DatabaseMigrations.from(5))
                .allowMainThreadQueries().build();
        SupportSQLiteDatabase database = migrated.getOpenHelper().getWritableDatabase();
        try (Cursor cursor = database.query("SELECT sourceTemplateId,comboOwnerId "
                + "FROM occurrence_steps WHERE id='unknown-step'")) {
            assertTrue(cursor.moveToFirst());
            assertEquals(null, cursor.getString(0));
            assertEquals("step:missing-template", cursor.getString(1));
        }
        try (Cursor cursor = database.query("SELECT ownerId,target,xpDelta "
                + "FROM reward_bookings WHERE occurrenceStepId='unknown-step'")) {
            assertTrue(cursor.moveToFirst());
            assertEquals("step:missing-template", cursor.getString(0));
            assertEquals("VESSEL", cursor.getString(1));
            assertEquals(10, cursor.getInt(2));
        }
        assertXpAndZeroCombos(database, 53);
        assertLegacyColumnsRemoved(database);
        migrated.close();
    }

    @Test public void migrationFourToSevenPreservesXpCombosRewardsAndStableSource() {
        createVersionOneDatabase();
        upgradeFixtureToVersionFour();
        SupportSQLiteOpenHelper.Configuration configuration = SupportSQLiteOpenHelper.Configuration
                .builder(context).name(DATABASE)
                .callback(new SupportSQLiteOpenHelper.Callback(4) {
                    @Override public void onCreate(SupportSQLiteDatabase database) { }
                    @Override public void onUpgrade(SupportSQLiteDatabase database,
                                                    int oldVersion, int newVersion) { }
                }).build();
        SupportSQLiteOpenHelper helper = new FrameworkSQLiteOpenHelperFactory().create(configuration);
        SupportSQLiteDatabase old = helper.getWritableDatabase();
        old.execSQL("INSERT INTO task_steps (id,taskId,position,text,weekdayMask,amountKind,"
                + "plannedSets,plannedReps,plannedDurationSeconds,note) VALUES "
                + "('stable-step','morning',0,'Duschen',0,'NONE',NULL,NULL,NULL,'')");
        old.execSQL("INSERT INTO occurrences (id,taskId,scheduledOn,slot,state,sortOrder,completedOn) "
                + "VALUES ('open-v4','morning','2026-08-18','MORNING','OPEN',1,''),"
                + "('done-v4','morning','2026-08-17','MORNING','COMPLETED',2,'2026-08-17')");
        old.execSQL("INSERT INTO occurrence_steps (id,occurrenceId,position,text,done,amountKind,"
                + "plannedSets,plannedReps,plannedDurationSeconds,note,actualRepetitions) VALUES "
                + "('open-step','open-v4',0,'Duschen',1,'NONE',NULL,NULL,NULL,'','')");
        old.execSQL("INSERT OR REPLACE INTO stats (id,xp) VALUES (1,137)");
        helper.close();

        AppDatabase migrated = Room.databaseBuilder(context, AppDatabase.class, DATABASE)
                .addMigrations(DatabaseMigrations.from(4))
                .allowMainThreadQueries().build();
        SupportSQLiteDatabase database = migrated.getOpenHelper().getWritableDatabase();
        try (Cursor cursor = database.query("SELECT xp FROM stats WHERE id=1")) {
            assertTrue(cursor.moveToFirst()); assertEquals(137, cursor.getInt(0));
        }
        try (Cursor cursor = database.query("SELECT COUNT(*),MAX(points) FROM combo_progress")) {
            assertTrue(cursor.moveToFirst()); assertTrue(cursor.getInt(0) >= 3);
            assertEquals(0, cursor.getInt(1));
        }
        try (Cursor cursor = database.query("SELECT sourceTemplateId,comboOwnerId "
                + "FROM occurrence_steps WHERE id='open-step'")) {
            assertTrue(cursor.moveToFirst()); assertEquals("stable-step", cursor.getString(0));
            assertEquals("step:stable-step", cursor.getString(1));
        }
        try (Cursor cursor = database.query("SELECT target,xpDelta,comboPointDelta "
                + "FROM reward_bookings WHERE occurrenceStepId='open-step'")) {
            assertTrue(cursor.moveToFirst()); assertEquals("VESSEL", cursor.getString(0));
            assertEquals(10, cursor.getInt(1)); assertEquals(0, cursor.getInt(2));
        }
        try (Cursor cursor = database.query("SELECT target,xpDelta,comboPointDelta FROM reward_bookings "
                + "WHERE occurrenceId='done-v4'")) {
            assertTrue(cursor.moveToFirst()); assertEquals("HEAD", cursor.getString(0));
            assertEquals(10, cursor.getInt(1)); assertEquals(0, cursor.getInt(2));
        }
        assertLegacyColumnsRemoved(database);
        try (Cursor cursor = database.query("SELECT originOccurrenceId,carryForwardReason "
                + "FROM occurrence_steps WHERE id='open-step'")) {
            assertTrue(cursor.moveToFirst());
            assertTrue(cursor.isNull(0));
            assertEquals("NONE", cursor.getString(1));
        }
        try (Cursor cursor = database.query("SELECT completedOn FROM occurrences "
                + "WHERE id='open-v4'")) {
            assertTrue(cursor.moveToFirst());
            assertTrue(cursor.isNull(0));
        }
        database.execSQL("INSERT INTO occurrences(id,taskId,scheduledOn,state,sortOrder,"
                + "completedOn,slot) VALUES ('queued-open','morning','2026-08-19',"
                + "'OPEN',3,NULL,'MORNING')");
        try (Cursor cursor = database.query("SELECT COUNT(*) FROM occurrences "
                + "WHERE taskId='morning' AND slot='MORNING' AND state='OPEN'")) {
            assertTrue(cursor.moveToFirst());
            assertEquals(2, cursor.getInt(0));
        }
        RewardReceipt undo = new UndoOccurrence(new RoomTaskRepository(migrated), new Clock() {
            @Override public LocalDate today() { return LocalDate.of(2026, 8, 17); }
            @Override public LocalTime time() { return LocalTime.NOON; }
        }).execute("done-v4");
        assertEquals(0, undo.xp);
        assertEquals(137, new RoomTaskRepository(migrated).xp());
        assertEquals(OccurrenceState.COMPLETED,
                new RoomTaskRepository(migrated).findOccurrence("done-v4").state);
        migrated.close();
    }

    @Test public void migrationSixToSevenPreservesStatsAndCreatesSignedLegacyBookings() {
        createVersionOneDatabase();
        upgradeFixtureToVersionSix();
        SupportSQLiteOpenHelper.Configuration configuration = SupportSQLiteOpenHelper.Configuration
                .builder(context).name(DATABASE)
                .callback(new SupportSQLiteOpenHelper.Callback(6) {
                    @Override public void onCreate(SupportSQLiteDatabase database) { }
                    @Override public void onUpgrade(SupportSQLiteDatabase database,
                                                    int oldVersion, int newVersion) { }
                }).build();
        SupportSQLiteOpenHelper helper = new FrameworkSQLiteOpenHelperFactory().create(configuration);
        SupportSQLiteDatabase old = helper.getWritableDatabase();
        old.execSQL("INSERT INTO occurrences (id,taskId,scheduledOn,state,sortOrder,completedOn,"
                + "slot,awardedXp,comboPointDelta) VALUES "
                + "('six-done','morning','2026-08-18','COMPLETED',1,'2026-08-18',"
                + "'MORNING',30,2)");
        old.execSQL("INSERT INTO occurrence_steps (id,occurrenceId,position,text,done,amountKind,"
                + "plannedSets,plannedReps,plannedDurationSeconds,note,actualRepetitions,"
                + "sourceTemplateId,comboOwnerId,earnedXp,comboPointDelta) VALUES "
                + "('six-step','six-done',0,'Historisch',1,'NONE',NULL,NULL,NULL,'','',NULL,"
                + "'step:six-step',10,1)");
        helper.close();

        AppDatabase migrated = Room.databaseBuilder(context, AppDatabase.class, DATABASE)
                .addMigrations(DatabaseMigrations.from(6))
                .allowMainThreadQueries().build();
        SupportSQLiteDatabase database = migrated.getOpenHelper().getWritableDatabase();
        try (Cursor cursor = database.query("SELECT target,xpDelta,comboPointDelta "
                + "FROM reward_bookings WHERE occurrenceId='six-done' ORDER BY target")) {
            assertEquals(2, cursor.getCount());
            assertTrue(cursor.moveToFirst());
            assertEquals("HEAD", cursor.getString(0));
            assertEquals(30, cursor.getInt(1));
            assertEquals(2, cursor.getInt(2));
            assertTrue(cursor.moveToNext());
            assertEquals("VESSEL", cursor.getString(0));
            assertEquals(10, cursor.getInt(1));
            assertEquals(1, cursor.getInt(2));
        }
        try (Cursor cursor = database.query("SELECT xp FROM stats WHERE id=1")) {
            assertTrue(cursor.moveToFirst()); assertEquals(53, cursor.getInt(0));
        }
        assertLegacyColumnsRemoved(database);
        migrated.close();
    }

    @Test public void migrationSevenToEightNormalizesResultsAndCorrectionWritesOneSlot() {
        createVersionSevenRepetitionDatabase();

        AppDatabase migrated = Room.databaseBuilder(context, AppDatabase.class, DATABASE)
                .addMigrations(DatabaseMigrations.from(7))
                .allowMainThreadQueries().build();
        SupportSQLiteDatabase database = migrated.getOpenHelper().getWritableDatabase();
        try (Cursor cursor = database.query("SELECT slotIndex,actualRepetitions "
                + "FROM repetition_results WHERE stepId='valid-step' ORDER BY slotIndex")) {
            assertEquals(3, cursor.getCount());
            assertTrue(cursor.moveToFirst());
            assertEquals(0, cursor.getInt(0)); assertEquals(12, cursor.getInt(1));
            assertTrue(cursor.moveToNext());
            assertEquals(1, cursor.getInt(0)); assertEquals(1_200, cursor.getInt(1));
            assertTrue(cursor.moveToNext());
            assertEquals(2, cursor.getInt(0)); assertEquals(0, cursor.getInt(1));
        }
        try (Cursor cursor = database.query("SELECT actualRepetitions FROM occurrence_steps "
                + "WHERE id='valid-step'")) {
            assertTrue(cursor.moveToFirst());
            assertEquals("12,1200,0", cursor.getString(0));
        }
        RoomTaskRepository repository = new RoomTaskRepository(migrated);
        de.thonktank.autosecretary.domain.model.OccurrenceStep valid =
                repository.findOccurrenceStep("valid-step");
        assertEquals(Arrays.asList(12, 1_200, 0),
                valid.repetitionProgress.actualRepetitions);
        assertTrue(repository.findOccurrenceStep("malformed-step")
                .repetitionProgress.actualRepetitions.isEmpty());
        assertTrue(ShadowLog.getLogsForTag("DatabaseMigrations").stream()
                .anyMatch(item -> item.msg.contains("malformed-step")));

        DatabaseMigrations.MIGRATION_7_8.migrate(database);
        try (Cursor cursor = database.query("SELECT COUNT(*) FROM repetition_results "
                + "WHERE stepId='valid-step'")) {
            assertTrue(cursor.moveToFirst()); assertEquals(3, cursor.getInt(0));
        }

        database.execSQL("CREATE TABLE repetition_write_audit(slotIndex INTEGER NOT NULL)");
        database.execSQL("CREATE TRIGGER audit_repetition_insert AFTER INSERT ON "
                + "repetition_results BEGIN INSERT INTO repetition_write_audit(slotIndex) "
                + "VALUES (NEW.slotIndex); END");
        repository.updateOccurrenceStep(valid.correctRepetitionResult(1, 13));
        try (Cursor cursor = database.query("SELECT slotIndex FROM repetition_write_audit")) {
            assertEquals(1, cursor.getCount());
            assertTrue(cursor.moveToFirst()); assertEquals(1, cursor.getInt(0));
        }
        assertEquals(Arrays.asList(12, 13, 0), repository.findOccurrenceStep("valid-step")
                .repetitionProgress.actualRepetitions);
        try (Cursor cursor = database.query("SELECT actualRepetitions FROM occurrence_steps "
                + "WHERE id='valid-step'")) {
            assertTrue(cursor.moveToFirst()); assertEquals("", cursor.getString(0));
        }
        migrated.close();
    }

    private void createVersionSevenRepetitionDatabase() {
        SupportSQLiteOpenHelper.Configuration configuration = SupportSQLiteOpenHelper.Configuration
                .builder(context).name(DATABASE)
                .callback(new SupportSQLiteOpenHelper.Callback(7) {
                    @Override public void onCreate(SupportSQLiteDatabase database) {
                        ExportedRoomSchemaFixture.create(database, 7);
                    }
                    @Override public void onUpgrade(SupportSQLiteDatabase database,
                                                    int oldVersion, int newVersion) { }
                }).build();
        SupportSQLiteOpenHelper helper = new FrameworkSQLiteOpenHelperFactory()
                .create(configuration);
        SupportSQLiteDatabase database = helper.getWritableDatabase();
        database.execSQL("INSERT INTO tasks (id,title,slot,recurrence,intervalDays,weekdayMask,"
                + "ongoing,conditionText,conditionDone,archived,nextDueOn,lastScheduledOn,"
                + "lastCompletedOn,displayOrder,hasCompletedOccurrence,estimatedMinutes,"
                + "timeOfDayMask,boundKind,boundUntilOn,boundWeeks,remainingCount,deadlineOn,note) "
                + "VALUES ('v7','Routine','MORNING','DAILY',1,0,0,'',0,0,'2026-08-20','',"
                + "'',1,0,NULL,1,'FOREVER','',NULL,NULL,'','')");
        database.execSQL("INSERT INTO occurrences (id,taskId,scheduledOn,state,sortOrder,"
                + "completedOn,slot) VALUES ('v7-occ','v7','2026-08-20','OPEN',1,'','MORNING')");
        database.execSQL("INSERT INTO occurrence_steps (id,occurrenceId,position,text,done,"
                + "amountKind,plannedSets,plannedReps,plannedDurationSeconds,note,"
                + "actualRepetitions,sourceTemplateId,comboOwnerId) VALUES "
                + "('valid-step','v7-occ',0,'Sätze',1,'SETS_REPS',3,12,NULL,'','12,1200,0',NULL,"
                + "'step:valid'),('malformed-step','v7-occ',1,'Kaputt',0,'SETS_REPS',3,12,NULL,"
                + "'','12,,9',NULL,'step:malformed')");
        helper.close();
    }

    private void createVersionOneDatabase() {
        SupportSQLiteOpenHelper.Configuration configuration = SupportSQLiteOpenHelper.Configuration
                .builder(context)
                .name(DATABASE)
                .callback(new SupportSQLiteOpenHelper.Callback(1) {
                    @Override public void onCreate(SupportSQLiteDatabase database) {
                        ExportedRoomSchemaFixture.create(database, 1);
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
        database.execSQL("INSERT OR REPLACE INTO stats (id,xp) VALUES (1,53)");
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

    private void upgradeFixtureToVersionThree() {
        upgradeFixtureToVersionTwo();
        SupportSQLiteOpenHelper.Configuration configuration = SupportSQLiteOpenHelper.Configuration
                .builder(context).name(DATABASE)
                .callback(new SupportSQLiteOpenHelper.Callback(3) {
                    @Override public void onCreate(SupportSQLiteDatabase database) { }
                    @Override public void onUpgrade(SupportSQLiteDatabase database,
                                                    int oldVersion, int newVersion) {
                        assertEquals(2, oldVersion); assertEquals(3, newVersion);
                        DatabaseMigrations.MIGRATION_2_3.migrate(database);
                    }
                }).build();
        SupportSQLiteOpenHelper helper = new FrameworkSQLiteOpenHelperFactory().create(configuration);
        helper.getWritableDatabase(); helper.close();
    }

    private void upgradeFixtureToVersionFour() {
        upgradeFixtureToVersionThree();
        SupportSQLiteOpenHelper.Configuration configuration = SupportSQLiteOpenHelper.Configuration
                .builder(context).name(DATABASE)
                .callback(new SupportSQLiteOpenHelper.Callback(4) {
                    @Override public void onCreate(SupportSQLiteDatabase database) { }
                    @Override public void onUpgrade(SupportSQLiteDatabase database,
                                                    int oldVersion, int newVersion) {
                        assertEquals(3, oldVersion); assertEquals(4, newVersion);
                        DatabaseMigrations.MIGRATION_3_4.migrate(database);
                    }
                }).build();
        SupportSQLiteOpenHelper helper = new FrameworkSQLiteOpenHelperFactory().create(configuration);
        helper.getWritableDatabase(); helper.close();
    }

    private void upgradeFixtureToVersionFive() {
        upgradeFixtureToVersionFour();
        SupportSQLiteOpenHelper.Configuration configuration = SupportSQLiteOpenHelper.Configuration
                .builder(context).name(DATABASE)
                .callback(new SupportSQLiteOpenHelper.Callback(5) {
                    @Override public void onCreate(SupportSQLiteDatabase database) { }
                    @Override public void onUpgrade(SupportSQLiteDatabase database,
                                                    int oldVersion, int newVersion) {
                        assertEquals(4, oldVersion); assertEquals(5, newVersion);
                        DatabaseMigrations.MIGRATION_4_5.migrate(database);
                    }
                }).build();
        SupportSQLiteOpenHelper helper = new FrameworkSQLiteOpenHelperFactory().create(configuration);
        helper.getWritableDatabase(); helper.close();
    }

    private void upgradeFixtureToVersionSix() {
        upgradeFixtureToVersionFive();
        SupportSQLiteOpenHelper.Configuration configuration = SupportSQLiteOpenHelper.Configuration
                .builder(context).name(DATABASE)
                .callback(new SupportSQLiteOpenHelper.Callback(6) {
                    @Override public void onCreate(SupportSQLiteDatabase database) { }
                    @Override public void onUpgrade(SupportSQLiteDatabase database,
                                                    int oldVersion, int newVersion) {
                        assertEquals(5, oldVersion); assertEquals(6, newVersion);
                        DatabaseMigrations.MIGRATION_5_6.migrate(database);
                    }
                }).build();
        SupportSQLiteOpenHelper helper = new FrameworkSQLiteOpenHelperFactory().create(configuration);
        helper.getWritableDatabase(); helper.close();
    }

    private static void assertXpAndZeroCombos(SupportSQLiteDatabase database, int xp) {
        try (Cursor cursor = database.query("SELECT xp FROM stats WHERE id=1")) {
            assertTrue(cursor.moveToFirst()); assertEquals(xp, cursor.getInt(0));
        }
        try (Cursor cursor = database.query("SELECT COUNT(*),MAX(points) FROM combo_progress")) {
            assertTrue(cursor.moveToFirst()); assertTrue(cursor.getInt(0) >= 2);
            assertEquals(0, cursor.getInt(1));
        }
    }

    private static void assertLegacyColumnsRemoved(SupportSQLiteDatabase database) {
        try (Cursor cursor = database.query("PRAGMA table_info(tasks)")) {
            while (cursor.moveToNext()) {
                String column = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                assertTrue(!column.equals("routineLevel") && !column.equals("routineStreak")
                        && !column.equals("routineStreakWeeks")
                        && !column.equals("lastStreakWeek") && !column.equals("slot")
                        && !column.equals("timeOfDayMask") && !column.equals("displayOrder"));
            }
        }
        assertColumnsMissing(database, "occurrences", "awardedXp", "comboPointDelta");
        assertColumnsMissing(database, "occurrence_steps", "earnedXp", "comboPointDelta");
        try (Cursor cursor = database.query("SELECT COUNT(*) FROM sqlite_master WHERE type='index' "
                + "AND name='index_reward_bookings_reversesBookingId'")) {
            assertTrue(cursor.moveToFirst()); assertEquals(1, cursor.getInt(0));
        }
    }

    private static void assertColumnsMissing(SupportSQLiteDatabase database, String table,
                                             String... forbidden) {
        try (Cursor cursor = database.query("PRAGMA table_info(" + table + ")")) {
            while (cursor.moveToNext()) {
                String column = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                for (String value : forbidden) assertTrue(!value.equals(column));
            }
        }
    }

    private static void assertColumnsPresent(SupportSQLiteDatabase database, String table,
                                             String... expected) {
        java.util.Set<String> columns = new java.util.HashSet<>();
        try (Cursor cursor = database.query("PRAGMA table_info(" + table + ")")) {
            while (cursor.moveToNext())
                columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")));
        }
        for (String value : expected) assertTrue(table + " is missing " + value,
                columns.contains(value));
    }
}
