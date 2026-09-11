package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;

import androidx.room.Room;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.core.app.ApplicationProvider;

import de.thonktank.autosecretary.data.local.DatabaseMigrations;
import de.thonktank.autosecretary.testing.ExportedRoomSchemaFixture;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Semantic sentinels for every table rebuilt by migration 22 to 23. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35})
public final class DatabaseMigrationSemanticCopyTest {
    private static final String DATABASE = "migration-semantic-copy";

    private Context context;

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(DATABASE);
    }

    @After public void tearDown() {
        context.deleteDatabase(DATABASE);
    }

    @Test public void migrationTwentyTwoToTwentyFourPreservesEveryRebuiltColumnByMeaning() {
        SupportSQLiteOpenHelper helper = new FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration.builder(context).name(DATABASE)
                        .callback(new SupportSQLiteOpenHelper.Callback(22) {
                            @Override public void onCreate(SupportSQLiteDatabase database) {
                                ExportedRoomSchemaFixture.create(database, 22);
                            }

                            @Override public void onUpgrade(SupportSQLiteDatabase database,
                                                            int oldVersion, int newVersion) { }
                        }).build());
        SupportSQLiteDatabase old = helper.getWritableDatabase();
        seedEveryRebuiltTable(old);
        helper.close();

        SupportSQLiteOpenHelper migrated = de.thonktank.autosecretary.testing.HistoricalDatabaseFixture
                .openMigrated(context, DATABASE, 22, 24);
        SupportSQLiteDatabase database = migrated.getWritableDatabase();
        assertEveryRebuiltTable(database);
        migrated.close();
    }

    private static void seedEveryRebuiltTable(SupportSQLiteDatabase database) {
        database.execSQL("INSERT INTO tasks(id,title,recurrence,intervalDays,weekdayMask,ongoing,"
                + "conditionText,conditionDone,archived,nextDueOn,cadenceAnchorOn,lastScheduledOn,"
                + "lastCompletedOn,catalogOrder,hasCompletedOccurrence,estimatedMinutes,boundKind,"
                + "boundUntilOn,boundWeeks,remainingCount,deadlineOn,note,missedOccurrenceMode) "
                + "VALUES ('sentinel-task','Sentinel','DAILY',2,4,0,'Bedingung',1,0,"
                + "'2026-09-08','2026-09-01','2026-09-07','2026-09-06',42,1,17,'COUNT',"
                + "NULL,NULL,5,'2026-12-31','Aufgabennotiz','CARRY_FORWARD')");
        database.execSQL("INSERT INTO step_flow_runs(id,taskId,seedStepId,sourceKey,scheduledOn,"
                + "slot,state,currentPosition,readyAtEpochMillis,currentSheetOccurrenceId,"
                + "queueOrder,nextSheetSequence,createdAtEpochMillis,updatedAtEpochMillis) VALUES "
                + "('sentinel-run','sentinel-task','seed-17','flow:sentinel','2026-09-08',"
                + "'MIDDAY','WAITING_TIME',3,123456,'sentinel-occurrence',47,11,1001,1002)");
        database.execSQL("INSERT INTO occurrences(id,taskId,scheduledOn,state,sortOrder,completedOn,"
                + "slot,kind,sourceKey,flowRunId,flowSheetSequence) VALUES "
                + "('sentinel-occurrence','sentinel-task','2026-09-08','OPEN',53,NULL,'MIDDAY',"
                + "'SCHEDULED','occurrence:sentinel','sentinel-run',13)");
        database.execSQL("INSERT INTO occurrence_steps(id,occurrenceId,position,text,done,"
                + "amountKind,plannedSets,plannedReps,plannedDurationSeconds,restTimerMode,"
                + "restTimerSeconds,plannedLoadMode,plannedLoadUnit,plannedLoadMilli,targetRir,"
                + "note,actualRepetitions,sourceTemplateId,comboOwnerId,originOccurrenceId,"
                + "carryForwardReason) VALUES ('sentinel-step','sentinel-occurrence',5,"
                + "'Sentinel-Schritt',1,'SETS_REPS',4,9,NULL,'CUSTOM',67,'EXTERNAL','LB',"
                + "12345,4,'Schrittnotiz','9,8','seed-17','step:seed-17',NULL,'MISSED')");
        database.execSQL("INSERT INTO reward_bookings(id,transactionId,occurrenceId,"
                + "occurrenceStepId,ownerId,kind,target,xpDelta,comboPointDelta,bookedOn,"
                + "reversesBookingId,plannedXp) VALUES ('sentinel-booking','tx-71',"
                + "'sentinel-occurrence','sentinel-step','step:seed-17','STEP_DONE','VESSEL',"
                + "73,17,'2026-09-08',NULL,79)");
        database.execSQL("INSERT INTO reward_assignments(bookingId,occurrenceId) VALUES "
                + "('sentinel-booking','sentinel-occurrence')");
        database.execSQL("INSERT INTO repetition_results(stepId,slotIndex,actualRepetitions,"
                + "loadMode,loadUnit,loadMilli,rir,source,safetyFlag) VALUES "
                + "('sentinel-step',2,8,'EXTERNAL','LB',12500,3,'USER','READY')");
        database.execSQL("INSERT INTO timer_sessions(id,stepId,title,kind,state,totalSeconds,"
                + "remainingMillis,targetElapsedRealtime,targetEpochMillis,notificationId,"
                + "completionObserved) VALUES ('sentinel-timer','sentinel-step','Pause 67',"
                + "'REST','RUNNING',67,32000,2001,3002,401,1)");
        database.execSQL("INSERT INTO combo_obligations(id,ownerId,taskId,kind,slot,scheduledOn,"
                + "occurrenceId,state,resolvedOn) VALUES ('sentinel-obligation','step:seed-17',"
                + "'sentinel-task','STEP','MIDDAY','2026-09-08','sentinel-occurrence','RESOLVED',"
                + "'2026-09-09')");
        database.execSQL("INSERT INTO flow_run_steps(id,runId,position,sourceTemplateId,text,"
                + "amountKind,plannedSets,plannedReps,plannedDurationSeconds,restTimerMode,"
                + "restTimerSeconds,plannedLoadMode,plannedLoadUnit,plannedLoadMilli,targetRir,"
                + "note,delayMode,defaultDelayMillis,lastUsedDelayMillis,chosenDelayMillis) VALUES "
                + "('sentinel-flow-step','sentinel-run',3,'seed-17','Flow-Sentinel','DURATION',"
                + "NULL,NULL,321,'CUSTOM',45,'BODYWEIGHT_PLUS','LB',22222,5,'Flownotiz',"
                + "'FIXED',6000,7000,8000)");
        database.execSQL("INSERT INTO flow_run_resources(id,runId,sourceLeaseId,resourceId,"
                + "resourceName,capacityAtCreation,units,acquirePosition,releasePosition,state,"
                + "reservedAtEpochMillis,activatedAtEpochMillis,releasedAtEpochMillis) VALUES "
                + "('sentinel-resource','sentinel-run','lease-83','resource-89','Rack',7,2,3,9,"
                + "'RELEASED',5001,5002,5003)");
    }

    private static void assertEveryRebuiltTable(SupportSQLiteDatabase database) {
        assertRow(database, "SELECT id,taskId,seedStepId,sourceKey,scheduledOn,slot,state,"
                        + "currentPosition,readyAtEpochMillis,currentExecutionOccurrenceId,"
                        + "queueOrder,nextExecutionSequence,createdAtEpochMillis,updatedAtEpochMillis "
                        + "FROM step_flow_runs WHERE id='sentinel-run'",
                "sentinel-run", "sentinel-task", "seed-17", "flow:sentinel", "2026-09-08",
                "MIDDAY", "WAITING_TIME", 3L, 123456L, "sentinel-occurrence", 47L, 11L,
                1001L, 1002L);
        assertRow(database, "SELECT id,taskId,scheduledOn,state,sortOrder,completedOn,slot,kind,"
                        + "sourceKey,flowRunId,flowExecutionSequence FROM occurrences "
                        + "WHERE id='sentinel-occurrence'",
                "sentinel-occurrence", "sentinel-task", "2026-09-08", "OPEN", 53L, null,
                "MIDDAY", "SCHEDULED", "occurrence:sentinel", "sentinel-run", 13L);
        assertRow(database, "SELECT id,occurrenceId,position,text,done,amountKind,plannedSets,"
                        + "plannedReps,plannedDurationSeconds,restTimerMode,restTimerSeconds,"
                        + "plannedLoadMode,plannedLoadUnit,plannedLoadMilli,targetRir,note,"
                        + "actualRepetitions,sourceTemplateId,comboOwnerId,originOccurrenceId,"
                        + "carryForwardReason FROM occurrence_steps WHERE id='sentinel-step'",
                "sentinel-step", "sentinel-occurrence", 5L, "Sentinel-Schritt", 1L,
                "SETS_REPS", 4L, 9L, null, "CUSTOM", 67L, "EXTERNAL", "LB", 12345L, 4L,
                "Schrittnotiz", "9,8", "seed-17", "step:seed-17", null, "MISSED");
        assertRow(database, "SELECT id,transactionId,occurrenceId,occurrenceStepId,ownerId,kind,"
                        + "target,xpDelta,comboPointDelta,bookedOn,reversesBookingId,plannedXp "
                        + "FROM reward_bookings WHERE id='sentinel-booking'",
                "sentinel-booking", "tx-71", "sentinel-occurrence", "sentinel-step",
                "step:seed-17", "STEP_DONE", "VESSEL", 73L, 17L, "2026-09-08", null, 79L);
        assertRow(database, "SELECT bookingId,occurrenceId FROM reward_assignments "
                        + "WHERE bookingId='sentinel-booking'",
                "sentinel-booking", "sentinel-occurrence");
        assertRow(database, "SELECT stepId,slotIndex,actualRepetitions,loadMode,loadUnit,"
                        + "loadMilli,rir,source,safetyFlag FROM repetition_results "
                        + "WHERE stepId='sentinel-step'",
                "sentinel-step", 2L, 8L, "EXTERNAL", "LB", 12500L, 3L, "USER", "READY");
        assertRow(database, "SELECT id,stepId,title,kind,state,totalSeconds,remainingMillis,"
                        + "targetElapsedRealtime,targetEpochMillis,notificationId,"
                        + "completionObserved FROM timer_sessions WHERE id='sentinel-timer'",
                "sentinel-timer", "sentinel-step", "Pause 67", "REST", "RUNNING", 67L,
                32000L, 2001L, 3002L, 401L, 1L);
        assertRow(database, "SELECT id,ownerId,taskId,kind,slot,scheduledOn,occurrenceId,state,"
                        + "resolvedOn FROM combo_obligations WHERE id='sentinel-obligation'",
                "sentinel-obligation", "step:seed-17", "sentinel-task", "STEP", "MIDDAY",
                "2026-09-08", "sentinel-occurrence", "RESOLVED", "2026-09-09");
        assertRow(database, "SELECT id,runId,position,sourceTemplateId,text,amountKind,plannedSets,"
                        + "plannedReps,plannedDurationSeconds,restTimerMode,restTimerSeconds,"
                        + "plannedLoadMode,plannedLoadUnit,plannedLoadMilli,targetRir,note,delayMode,"
                        + "defaultDelayMillis,lastUsedDelayMillis,chosenDelayMillis "
                        + "FROM flow_run_steps WHERE id='sentinel-flow-step'",
                "sentinel-flow-step", "sentinel-run", 3L, "seed-17", "Flow-Sentinel",
                "DURATION", null, null, 321L, "CUSTOM", 45L, "BODYWEIGHT_PLUS", "LB",
                22222L, 5L, "Flownotiz", "FIXED", 6000L, 7000L, 8000L);
        assertRow(database, "SELECT id,runId,sourceLeaseId,resourceId,resourceName,"
                        + "capacityAtCreation,units,acquirePosition,releasePosition,state,"
                        + "reservedAtEpochMillis,activatedAtEpochMillis,releasedAtEpochMillis "
                        + "FROM flow_run_resources WHERE id='sentinel-resource'",
                "sentinel-resource", "sentinel-run", "lease-83", "resource-89", "Rack", 7L,
                2L, 3L, 9L, "RELEASED", 5001L, 5002L, 5003L);
    }

    private static void assertRow(SupportSQLiteDatabase database, String query,
                                  Object... expected) {
        try (Cursor cursor = database.query(query)) {
            assertTrue(query, cursor.moveToFirst());
            assertEquals(query, expected.length, cursor.getColumnCount());
            for (int column = 0; column < expected.length; column++) {
                Object value = expected[column];
                if (value == null) {
                    assertTrue(query + " column " + column, cursor.isNull(column));
                } else if (value instanceof Number) {
                    assertEquals(query + " column " + column,
                            ((Number) value).longValue(), cursor.getLong(column));
                } else {
                    assertEquals(query + " column " + column, value, cursor.getString(column));
                }
            }
            assertFalse(query + " returned duplicate rows", cursor.moveToNext());
        }
    }
}
