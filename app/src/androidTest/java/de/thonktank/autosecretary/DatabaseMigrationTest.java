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

    @Test public void migration3To4PreservesSnapshotsAndConvertsOngoingTasks() throws IOException {
        SupportSQLiteDatabase database = helper.createDatabase(DATABASE, 3);
        database.execSQL("INSERT INTO tasks (id,title,slot,recurrence,intervalDays,weekdayMask,ongoing,"
                + "conditionText,conditionDone,archived,nextDueOn,lastScheduledOn,lastCompletedOn,"
                + "routineLevel,routineStreak,routineStreakWeeks,lastStreakWeek,displayOrder,"
                + "hasCompletedOccurrence) VALUES ('legacy','Praktikum','LATER','DAILY',1,0,1,"
                + "'Vertrag unterschrieben',0,0,'2026-08-17','','',2,3,4,'2026-08-10',4000001,1)");
        database.execSQL("INSERT INTO task_steps (id,taskId,position,text) VALUES "
                + "('template','legacy',0,'Unterlagen prüfen')");
        database.execSQL("INSERT INTO occurrences (id,taskId,scheduledOn,state,sortOrder,completedOn) "
                + "VALUES ('occurrence','legacy','2026-08-17','OPEN',1,'')");
        database.execSQL("INSERT INTO occurrence_steps (id,occurrenceId,position,text,done) VALUES "
                + "('snapshot','occurrence',0,'Unterlagen prüfen',1)");
        database.execSQL("INSERT OR REPLACE INTO stats (id,xp) VALUES (1,70)");
        database.close();

        database = helper.runMigrationsAndValidate(
                DATABASE, 4, true, DatabaseMigrations.MIGRATION_3_4);
        try (Cursor cursor = database.query("SELECT recurrence,ongoing,note,timeOfDayMask "
                + "FROM tasks WHERE id='legacy'")) {
            assertTrue(cursor.moveToFirst());
            assertEquals("ONCE", cursor.getString(0));
            assertEquals(0, cursor.getInt(1));
            assertEquals("Erledigt, wenn: Vertrag unterschrieben", cursor.getString(2));
            assertEquals(0, cursor.getInt(3));
        }
        try (Cursor cursor = database.query("SELECT slot FROM occurrences WHERE id='occurrence'")) {
            assertTrue(cursor.moveToFirst());
            assertEquals("LATER", cursor.getString(0));
        }
        try (Cursor cursor = database.query("SELECT amountKind,actualRepetitions,done "
                + "FROM occurrence_steps WHERE id='snapshot'")) {
            assertTrue(cursor.moveToFirst());
            assertEquals("NONE", cursor.getString(0));
            assertEquals("", cursor.getString(1));
            assertEquals(1, cursor.getInt(2));
        }
        assertIndexExists(database, "index_occurrences_taskId_scheduledOn_slot");
        database.close();
    }

    @Test public void migration4To5PreservesXpAndStartsCombosAtZero() throws IOException {
        SupportSQLiteDatabase database = helper.createDatabase(DATABASE, 4);
        database.execSQL("INSERT INTO tasks (id,title,slot,recurrence,intervalDays,weekdayMask,"
                + "ongoing,conditionText,conditionDone,archived,nextDueOn,lastScheduledOn,"
                + "lastCompletedOn,routineLevel,routineStreak,routineStreakWeeks,lastStreakWeek,"
                + "displayOrder,hasCompletedOccurrence,estimatedMinutes,timeOfDayMask,boundKind,"
                + "boundUntilOn,boundWeeks,remainingCount,deadlineOn,note) VALUES "
                + "('v4','Routine','MORNING','DAILY',1,0,0,'',0,0,'2026-08-18','','',"
                + "1,0,0,'',1,0,NULL,1,'FOREVER','',NULL,NULL,'','')");
        database.execSQL("INSERT INTO task_steps (id,taskId,position,text,weekdayMask,amountKind,"
                + "plannedSets,plannedReps,plannedDurationSeconds,note) VALUES "
                + "('stable','v4',0,'Schritt',0,'NONE',NULL,NULL,NULL,'')");
        database.execSQL("INSERT INTO occurrences (id,taskId,scheduledOn,slot,state,sortOrder,"
                + "completedOn) VALUES ('open','v4','2026-08-18','MORNING','OPEN',1,'')");
        database.execSQL("INSERT INTO occurrence_steps (id,occurrenceId,position,text,done,"
                + "amountKind,plannedSets,plannedReps,plannedDurationSeconds,note,actualRepetitions) "
                + "VALUES ('snapshot','open',0,'Schritt',1,'NONE',NULL,NULL,NULL,'','')");
        database.execSQL("INSERT OR REPLACE INTO stats (id,xp) VALUES (1,137)");
        database.close();

        database = helper.runMigrationsAndValidate(
                DATABASE, 5, true, DatabaseMigrations.MIGRATION_4_5);
        try (Cursor cursor = database.query("SELECT xp FROM stats WHERE id=1")) {
            assertTrue(cursor.moveToFirst()); assertEquals(137, cursor.getInt(0));
        }
        try (Cursor cursor = database.query("SELECT COUNT(*),MAX(points) FROM combo_progress")) {
            assertTrue(cursor.moveToFirst()); assertTrue(cursor.getInt(0) >= 2);
            assertEquals(0, cursor.getInt(1));
        }
        try (Cursor cursor = database.query("SELECT comboOwnerId,earnedXp FROM occurrence_steps "
                + "WHERE id='snapshot'")) {
            assertTrue(cursor.moveToFirst()); assertEquals("step:stable", cursor.getString(0));
            assertEquals(10, cursor.getInt(1));
        }
        database.close();
    }

    @Test public void migration5To6RemovesLegacyColumnsAndKeepsRewardState() throws IOException {
        SupportSQLiteDatabase database = helper.createDatabase(DATABASE, 5);
        database.execSQL("INSERT INTO tasks (id,title,slot,recurrence,intervalDays,weekdayMask,"
                + "ongoing,conditionText,conditionDone,archived,nextDueOn,lastScheduledOn,"
                + "lastCompletedOn,routineLevel,routineStreak,routineStreakWeeks,lastStreakWeek,"
                + "displayOrder,hasCompletedOccurrence,estimatedMinutes,timeOfDayMask,boundKind,"
                + "boundUntilOn,boundWeeks,remainingCount,deadlineOn,note) VALUES "
                + "('v5','Routine','MORNING','DAILY',1,0,0,'',0,0,'2026-08-18','','',"
                + "4,8,3,'2026-08-11',1001,1,NULL,1,'FOREVER','',NULL,NULL,'','')");
        database.execSQL("INSERT INTO task_steps (id,taskId,position,text,weekdayMask,amountKind,"
                + "plannedSets,plannedReps,plannedDurationSeconds,note) VALUES "
                + "('stable','v5',0,'Schritt',0,'NONE',NULL,NULL,NULL,'')");
        database.execSQL("INSERT INTO occurrences (id,taskId,scheduledOn,state,sortOrder,"
                + "completedOn,slot,awardedXp,comboPointDelta) VALUES "
                + "('open','v5','2026-08-18','OPEN',1,'','MORNING',0,0),"
                + "('done','v5','2026-08-17','COMPLETED',2,'2026-08-17','MORNING',10,3)");
        database.execSQL("INSERT INTO occurrence_steps (id,occurrenceId,position,text,done,"
                + "amountKind,plannedSets,plannedReps,plannedDurationSeconds,note,"
                + "actualRepetitions,comboOwnerId,earnedXp,comboPointDelta) VALUES "
                + "('snapshot','open',0,'Schritt',1,'NONE',NULL,NULL,NULL,'','',"
                + "'step:stable',10,1)");
        database.execSQL("INSERT INTO stats (id,xp) VALUES (1,137)");
        database.execSQL("INSERT INTO combo_progress (ownerId,taskId,kind,points,settledThroughOn) "
                + "VALUES ('task:v5','v5','TASK',5,'2026-08-17'),"
                + "('step:stable','v5','STEP',7,'2026-08-18')");
        database.close();

        database = helper.runMigrationsAndValidate(
                DATABASE, 6, true, DatabaseMigrations.MIGRATION_5_6);
        try (Cursor cursor = database.query("SELECT sourceTemplateId,comboOwnerId,earnedXp,"
                + "comboPointDelta FROM occurrence_steps WHERE id='snapshot'")) {
            assertTrue(cursor.moveToFirst());
            assertEquals("stable", cursor.getString(0));
            assertEquals("step:stable", cursor.getString(1));
            assertEquals(10, cursor.getInt(2));
            assertEquals(1, cursor.getInt(3));
        }
        try (Cursor cursor = database.query("SELECT awardedXp,comboPointDelta FROM occurrences "
                + "WHERE id='done'")) {
            assertTrue(cursor.moveToFirst());
            assertEquals(10, cursor.getInt(0));
            assertEquals(3, cursor.getInt(1));
        }
        try (Cursor cursor = database.query("SELECT xp FROM stats WHERE id=1")) {
            assertTrue(cursor.moveToFirst()); assertEquals(137, cursor.getInt(0));
        }
        try (Cursor cursor = database.query("SELECT points FROM combo_progress "
                + "WHERE ownerId='step:stable'")) {
            assertTrue(cursor.moveToFirst()); assertEquals(7, cursor.getInt(0));
        }
        try (Cursor cursor = database.query("PRAGMA table_info(tasks)")) {
            while (cursor.moveToNext()) {
                String column = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                assertTrue(!column.startsWith("routine") && !column.equals("lastStreakWeek"));
            }
        }
        database.close();
    }

    @Test public void migration6To7CreatesLedgerWithoutChangingMaterializedStats() throws IOException {
        SupportSQLiteDatabase database = helper.createDatabase(DATABASE, 6);
        database.execSQL("INSERT INTO tasks (id,title,slot,recurrence,intervalDays,weekdayMask,"
                + "ongoing,conditionText,conditionDone,archived,nextDueOn,lastScheduledOn,"
                + "lastCompletedOn,displayOrder,hasCompletedOccurrence,estimatedMinutes,"
                + "timeOfDayMask,boundKind,boundUntilOn,boundWeeks,remainingCount,deadlineOn,note) "
                + "VALUES ('v6','Routine','MORNING','DAILY',1,0,0,'',0,0,'2026-08-18','','',"
                + "1001,1,NULL,1,'FOREVER','',NULL,NULL,'','')");
        database.execSQL("INSERT INTO task_steps (id,taskId,position,text,weekdayMask,amountKind,"
                + "plannedSets,plannedReps,plannedDurationSeconds,note) VALUES "
                + "('stable','v6',0,'Schritt',0,'NONE',NULL,NULL,NULL,'')");
        database.execSQL("INSERT INTO occurrences (id,taskId,scheduledOn,state,sortOrder,"
                + "completedOn,slot,awardedXp,comboPointDelta) VALUES "
                + "('done','v6','2026-08-18','COMPLETED',1,'2026-08-18','MORNING',20,3)");
        database.execSQL("INSERT INTO occurrence_steps (id,occurrenceId,position,text,done,"
                + "amountKind,plannedSets,plannedReps,plannedDurationSeconds,note,"
                + "actualRepetitions,sourceTemplateId,comboOwnerId,earnedXp,comboPointDelta) VALUES "
                + "('snapshot','done',0,'Schritt',1,'NONE',NULL,NULL,NULL,'','','stable',"
                + "'step:stable',10,1)");
        database.execSQL("INSERT INTO stats (id,xp) VALUES (1,137)");
        database.execSQL("INSERT INTO combo_progress (ownerId,taskId,kind,points,settledThroughOn) "
                + "VALUES ('task:v6','v6','TASK',3,'2026-08-18'),"
                + "('step:stable','v6','STEP',1,'2026-08-18')");
        database.close();

        database = helper.runMigrationsAndValidate(
                DATABASE, 7, true, DatabaseMigrations.MIGRATION_6_7);
        try (Cursor cursor = database.query("SELECT target,occurrenceStepId,xpDelta,"
                + "comboPointDelta FROM reward_bookings ORDER BY target")) {
            assertEquals(2, cursor.getCount());
            assertTrue(cursor.moveToFirst());
            assertEquals("HEAD", cursor.getString(0)); assertEquals(null, cursor.getString(1));
            assertEquals(20, cursor.getInt(2)); assertEquals(3, cursor.getInt(3));
            assertTrue(cursor.moveToNext());
            assertEquals("VESSEL", cursor.getString(0)); assertEquals("snapshot", cursor.getString(1));
            assertEquals(10, cursor.getInt(2)); assertEquals(1, cursor.getInt(3));
        }
        try (Cursor cursor = database.query("SELECT xp FROM stats WHERE id=1")) {
            assertTrue(cursor.moveToFirst()); assertEquals(137, cursor.getInt(0));
        }
        assertColumnsMissing(database, "occurrences", "awardedXp", "comboPointDelta");
        assertColumnsMissing(database, "occurrence_steps", "earnedXp", "comboPointDelta");
        database.close();
    }

    @Test public void migration7To8PreservesLegacyValuesInStructuredRows() throws IOException {
        SupportSQLiteDatabase database = helper.createDatabase(DATABASE, 7);
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
                + "('v7-step','v7-occ',0,'Sätze',1,'SETS_REPS',3,12,NULL,'',"
                + "'0,999,1200',NULL,'step:v7')");
        database.close();

        database = helper.runMigrationsAndValidate(
                DATABASE, 8, true, DatabaseMigrations.MIGRATION_7_8);
        try (Cursor cursor = database.query("SELECT slotIndex,actualRepetitions "
                + "FROM repetition_results WHERE stepId='v7-step' ORDER BY slotIndex")) {
            assertEquals(3, cursor.getCount());
            assertTrue(cursor.moveToFirst());
            assertEquals(0, cursor.getInt(0)); assertEquals(0, cursor.getInt(1));
            assertTrue(cursor.moveToNext());
            assertEquals(1, cursor.getInt(0)); assertEquals(999, cursor.getInt(1));
            assertTrue(cursor.moveToNext());
            assertEquals(2, cursor.getInt(0)); assertEquals(1_200, cursor.getInt(1));
        }
        try (Cursor cursor = database.query("SELECT actualRepetitions FROM occurrence_steps "
                + "WHERE id='v7-step'")) {
            assertTrue(cursor.moveToFirst()); assertEquals("0,999,1200", cursor.getString(0));
        }
        database.close();
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

    private static void assertIndexExists(SupportSQLiteDatabase database, String index) {
        try (Cursor cursor = database.query(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='" + index + "'")) {
            assertTrue(cursor.moveToFirst());
            assertEquals(1, cursor.getInt(0));
        }
    }
}
