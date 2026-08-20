package de.thonktank.autosecretary.data.local;

import android.database.Cursor;
import android.util.Log;

import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

public final class DatabaseMigrations {
    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE tasks ADD COLUMN routineStreakWeeks INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE tasks ADD COLUMN lastStreakWeek TEXT NOT NULL DEFAULT ''");
            database.execSQL("ALTER TABLE tasks ADD COLUMN displayOrder INTEGER NOT NULL DEFAULT 0");
            database.execSQL("UPDATE tasks SET routineStreakWeeks = CASE WHEN routineStreak > 0 THEN 1 ELSE 0 END, "
                    + "lastStreakWeek = lastCompletedOn, displayOrder = "
                    + "(CASE slot WHEN 'Morgen' THEN 1000000 WHEN 'Mittag' THEN 2000000 WHEN 'Abend' THEN 3000000 ELSE 4000000 END) + rowid");
        }
    };

    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("UPDATE tasks SET slot = CASE slot "
                    + "WHEN 'Morgen' THEN 'MORNING' WHEN 'Mittag' THEN 'MIDDAY' "
                    + "WHEN 'Abend' THEN 'EVENING' WHEN 'Später' THEN 'LATER' "
                    + "WHEN 'MORNING' THEN 'MORNING' WHEN 'MIDDAY' THEN 'MIDDAY' "
                    + "WHEN 'EVENING' THEN 'EVENING' WHEN 'LATER' THEN 'LATER' ELSE 'LATER' END");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_archived_conditionDone_displayOrder "
                    + "ON tasks (archived, conditionDone, displayOrder)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_occurrences_state_completedOn "
                    + "ON occurrences (state, completedOn)");
        }
    };

    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE tasks ADD COLUMN estimatedMinutes INTEGER");
            database.execSQL("ALTER TABLE tasks ADD COLUMN timeOfDayMask INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE tasks ADD COLUMN boundKind TEXT NOT NULL DEFAULT 'FOREVER'");
            database.execSQL("ALTER TABLE tasks ADD COLUMN boundUntilOn TEXT NOT NULL DEFAULT ''");
            database.execSQL("ALTER TABLE tasks ADD COLUMN boundWeeks INTEGER");
            database.execSQL("ALTER TABLE tasks ADD COLUMN remainingCount INTEGER");
            database.execSQL("ALTER TABLE tasks ADD COLUMN deadlineOn TEXT NOT NULL DEFAULT ''");
            database.execSQL("ALTER TABLE tasks ADD COLUMN note TEXT NOT NULL DEFAULT ''");
            database.execSQL("UPDATE tasks SET timeOfDayMask = CASE WHEN recurrence = 'ONCE' THEN 0 "
                    + "WHEN slot = 'MORNING' THEN 1 WHEN slot = 'MIDDAY' THEN 2 "
                    + "WHEN slot = 'EVENING' THEN 4 ELSE 8 END");
            database.execSQL("UPDATE tasks SET note = CASE WHEN ongoing = 1 AND conditionText <> '' "
                    + "THEN 'Erledigt, wenn: ' || conditionText ELSE note END");
            database.execSQL("UPDATE tasks SET recurrence = 'ONCE', intervalDays = 1, "
                    + "weekdayMask = 0, timeOfDayMask = 0, ongoing = 0, conditionText = '' "
                    + "WHERE ongoing = 1");

            database.execSQL("ALTER TABLE task_steps ADD COLUMN weekdayMask INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE task_steps ADD COLUMN amountKind TEXT NOT NULL DEFAULT 'NONE'");
            database.execSQL("ALTER TABLE task_steps ADD COLUMN plannedSets INTEGER");
            database.execSQL("ALTER TABLE task_steps ADD COLUMN plannedReps INTEGER");
            database.execSQL("ALTER TABLE task_steps ADD COLUMN plannedDurationSeconds INTEGER");
            database.execSQL("ALTER TABLE task_steps ADD COLUMN note TEXT NOT NULL DEFAULT ''");

            database.execSQL("ALTER TABLE occurrences ADD COLUMN slot TEXT NOT NULL DEFAULT 'MORNING'");
            database.execSQL("UPDATE occurrences SET slot = COALESCE((SELECT tasks.slot FROM tasks "
                    + "WHERE tasks.id = occurrences.taskId), 'MORNING')");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_occurrences_taskId_scheduledOn_slot "
                    + "ON occurrences (taskId, scheduledOn, slot)");

            database.execSQL("ALTER TABLE occurrence_steps ADD COLUMN amountKind TEXT NOT NULL DEFAULT 'NONE'");
            database.execSQL("ALTER TABLE occurrence_steps ADD COLUMN plannedSets INTEGER");
            database.execSQL("ALTER TABLE occurrence_steps ADD COLUMN plannedReps INTEGER");
            database.execSQL("ALTER TABLE occurrence_steps ADD COLUMN plannedDurationSeconds INTEGER");
            database.execSQL("ALTER TABLE occurrence_steps ADD COLUMN note TEXT NOT NULL DEFAULT ''");
            database.execSQL("ALTER TABLE occurrence_steps ADD COLUMN actualRepetitions TEXT NOT NULL DEFAULT ''");
        }
    };

    public static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE occurrences ADD COLUMN awardedXp INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE occurrences ADD COLUMN comboPointDelta INTEGER NOT NULL DEFAULT 0");
            database.execSQL("UPDATE occurrences SET awardedXp = 10 WHERE state = 'COMPLETED'");

            database.execSQL("ALTER TABLE occurrence_steps ADD COLUMN comboOwnerId TEXT NOT NULL DEFAULT ''");
            database.execSQL("ALTER TABLE occurrence_steps ADD COLUMN earnedXp INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE occurrence_steps ADD COLUMN comboPointDelta INTEGER NOT NULL DEFAULT 0");
            database.execSQL("UPDATE occurrence_steps SET comboOwnerId = 'step:' || COALESCE(("
                    + "SELECT task_steps.id FROM task_steps JOIN occurrences ON occurrences.taskId = task_steps.taskId "
                    + "WHERE occurrences.id = occurrence_steps.occurrenceId AND task_steps.position = occurrence_steps.position LIMIT 1"
                    + "), occurrence_steps.id)");
            database.execSQL("UPDATE occurrence_steps SET earnedXp = 10 WHERE done = 1 AND occurrenceId IN "
                    + "(SELECT id FROM occurrences WHERE state = 'OPEN')");

            database.execSQL("CREATE TABLE IF NOT EXISTS combo_progress (ownerId TEXT NOT NULL, "
                    + "taskId TEXT NOT NULL, kind TEXT NOT NULL, points INTEGER NOT NULL, "
                    + "settledThroughOn TEXT NOT NULL, PRIMARY KEY(ownerId), FOREIGN KEY(taskId) "
                    + "REFERENCES tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_combo_progress_taskId ON combo_progress(taskId)");
            database.execSQL("INSERT OR IGNORE INTO combo_progress(ownerId,taskId,kind,points,settledThroughOn) "
                    + "SELECT 'task:' || id,id,'TASK',0,'' FROM tasks");
            database.execSQL("INSERT OR IGNORE INTO combo_progress(ownerId,taskId,kind,points,settledThroughOn) "
                    + "SELECT 'step:' || id,taskId,'STEP',0,'' FROM task_steps");
            database.execSQL("INSERT OR IGNORE INTO combo_progress(ownerId,taskId,kind,points,settledThroughOn) "
                    + "SELECT occurrence_steps.comboOwnerId,occurrences.taskId,'STEP',0,'' FROM occurrence_steps "
                    + "JOIN occurrences ON occurrences.id = occurrence_steps.occurrenceId");
        }
    };

    /** Rebuilds the task aggregate without obsolete streak columns and adds stable step origin. */
    public static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE _m_tasks AS SELECT id,title,slot,recurrence,"
                    + "intervalDays,weekdayMask,ongoing,conditionText,conditionDone,archived,"
                    + "nextDueOn,lastScheduledOn,lastCompletedOn,displayOrder,"
                    + "hasCompletedOccurrence,estimatedMinutes,timeOfDayMask,boundKind,"
                    + "boundUntilOn,boundWeeks,remainingCount,deadlineOn,note FROM tasks");
            database.execSQL("CREATE TABLE _m_task_steps AS SELECT * FROM task_steps");
            database.execSQL("CREATE TABLE _m_occurrences AS SELECT * FROM occurrences");
            database.execSQL("CREATE TABLE _m_occurrence_steps AS SELECT * FROM occurrence_steps");
            database.execSQL("CREATE TABLE _m_combo_progress AS SELECT * FROM combo_progress");

            database.execSQL("DROP TABLE occurrence_steps");
            database.execSQL("DROP TABLE combo_progress");
            database.execSQL("DROP TABLE occurrences");
            database.execSQL("DROP TABLE task_steps");
            database.execSQL("DROP TABLE tasks");

            database.execSQL("CREATE TABLE tasks (id TEXT NOT NULL, title TEXT NOT NULL, "
                    + "slot TEXT NOT NULL, recurrence TEXT NOT NULL, intervalDays INTEGER NOT NULL, "
                    + "weekdayMask INTEGER NOT NULL, ongoing INTEGER NOT NULL, conditionText TEXT NOT NULL, "
                    + "conditionDone INTEGER NOT NULL, archived INTEGER NOT NULL, nextDueOn TEXT NOT NULL, "
                    + "lastScheduledOn TEXT NOT NULL, lastCompletedOn TEXT NOT NULL, "
                    + "displayOrder INTEGER NOT NULL, hasCompletedOccurrence INTEGER NOT NULL, "
                    + "estimatedMinutes INTEGER, timeOfDayMask INTEGER NOT NULL, boundKind TEXT NOT NULL, "
                    + "boundUntilOn TEXT NOT NULL, boundWeeks INTEGER, remainingCount INTEGER, "
                    + "deadlineOn TEXT NOT NULL, note TEXT NOT NULL, PRIMARY KEY(id))");
            database.execSQL("INSERT INTO tasks SELECT * FROM _m_tasks");
            database.execSQL("CREATE INDEX index_tasks_archived_conditionDone_displayOrder "
                    + "ON tasks (archived,conditionDone,displayOrder)");

            database.execSQL("CREATE TABLE task_steps (id TEXT NOT NULL, taskId TEXT NOT NULL, "
                    + "position INTEGER NOT NULL, text TEXT NOT NULL, weekdayMask INTEGER NOT NULL, "
                    + "amountKind TEXT NOT NULL, plannedSets INTEGER, plannedReps INTEGER, "
                    + "plannedDurationSeconds INTEGER, note TEXT NOT NULL, PRIMARY KEY(id), "
                    + "FOREIGN KEY(taskId) REFERENCES tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE)");
            database.execSQL("INSERT INTO task_steps SELECT * FROM _m_task_steps");
            database.execSQL("CREATE INDEX index_task_steps_taskId ON task_steps(taskId)");

            database.execSQL("CREATE TABLE occurrences (id TEXT NOT NULL, taskId TEXT NOT NULL, "
                    + "scheduledOn TEXT NOT NULL, state TEXT NOT NULL, sortOrder INTEGER NOT NULL, "
                    + "completedOn TEXT NOT NULL, slot TEXT NOT NULL, awardedXp INTEGER NOT NULL, "
                    + "comboPointDelta INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(taskId) "
                    + "REFERENCES tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE)");
            database.execSQL("INSERT INTO occurrences SELECT * FROM _m_occurrences");
            database.execSQL("CREATE INDEX index_occurrences_taskId ON occurrences(taskId)");
            database.execSQL("CREATE INDEX index_occurrences_state_completedOn "
                    + "ON occurrences(state,completedOn)");
            database.execSQL("CREATE UNIQUE INDEX index_occurrences_taskId_scheduledOn_slot "
                    + "ON occurrences(taskId,scheduledOn,slot)");

            database.execSQL("CREATE TABLE occurrence_steps (id TEXT NOT NULL, "
                    + "occurrenceId TEXT NOT NULL, position INTEGER NOT NULL, text TEXT NOT NULL, "
                    + "done INTEGER NOT NULL, amountKind TEXT NOT NULL, plannedSets INTEGER, "
                    + "plannedReps INTEGER, plannedDurationSeconds INTEGER, note TEXT NOT NULL, "
                    + "actualRepetitions TEXT NOT NULL, sourceTemplateId TEXT, "
                    + "comboOwnerId TEXT NOT NULL, earnedXp INTEGER NOT NULL, "
                    + "comboPointDelta INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(occurrenceId) "
                    + "REFERENCES occurrences(id) ON UPDATE NO ACTION ON DELETE CASCADE)");
            database.execSQL("INSERT INTO occurrence_steps SELECT os.id,os.occurrenceId,os.position,"
                    + "os.text,os.done,os.amountKind,os.plannedSets,os.plannedReps,"
                    + "os.plannedDurationSeconds,os.note,os.actualRepetitions,(SELECT ts.id FROM "
                    + "_m_task_steps ts JOIN _m_occurrences o ON o.taskId=ts.taskId "
                    + "WHERE o.id=os.occurrenceId AND ('step:' || ts.id)=os.comboOwnerId "
                    + "GROUP BY ts.id HAVING COUNT(*)=1),os.comboOwnerId,os.earnedXp,"
                    + "os.comboPointDelta FROM _m_occurrence_steps os");
            database.execSQL("CREATE INDEX index_occurrence_steps_occurrenceId "
                    + "ON occurrence_steps(occurrenceId)");

            database.execSQL("CREATE TABLE combo_progress (ownerId TEXT NOT NULL, "
                    + "taskId TEXT NOT NULL, kind TEXT NOT NULL, points INTEGER NOT NULL, "
                    + "settledThroughOn TEXT NOT NULL, PRIMARY KEY(ownerId), FOREIGN KEY(taskId) "
                    + "REFERENCES tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE)");
            database.execSQL("INSERT INTO combo_progress SELECT * FROM _m_combo_progress");
            database.execSQL("CREATE INDEX index_combo_progress_taskId ON combo_progress(taskId)");

            database.execSQL("DROP TABLE _m_occurrence_steps");
            database.execSQL("DROP TABLE _m_combo_progress");
            database.execSQL("DROP TABLE _m_occurrences");
            database.execSQL("DROP TABLE _m_task_steps");
            database.execSQL("DROP TABLE _m_tasks");
        }
    };

    /** Moves mutable reward snapshots into an immutable signed booking ledger. */
    public static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE _m_occurrences AS SELECT * FROM occurrences");
            database.execSQL("CREATE TABLE _m_occurrence_steps AS SELECT * FROM occurrence_steps");
            database.execSQL("DROP TABLE occurrence_steps");
            database.execSQL("DROP TABLE occurrences");

            database.execSQL("CREATE TABLE occurrences (id TEXT NOT NULL, taskId TEXT NOT NULL, "
                    + "scheduledOn TEXT NOT NULL, state TEXT NOT NULL, sortOrder INTEGER NOT NULL, "
                    + "completedOn TEXT NOT NULL, slot TEXT NOT NULL, PRIMARY KEY(id), "
                    + "FOREIGN KEY(taskId) REFERENCES tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE)");
            database.execSQL("INSERT INTO occurrences SELECT id,taskId,scheduledOn,state,sortOrder,"
                    + "completedOn,slot FROM _m_occurrences");
            database.execSQL("CREATE INDEX index_occurrences_taskId ON occurrences(taskId)");
            database.execSQL("CREATE INDEX index_occurrences_state_completedOn "
                    + "ON occurrences(state,completedOn)");
            database.execSQL("CREATE UNIQUE INDEX index_occurrences_taskId_scheduledOn_slot "
                    + "ON occurrences(taskId,scheduledOn,slot)");

            database.execSQL("CREATE TABLE occurrence_steps (id TEXT NOT NULL, "
                    + "occurrenceId TEXT NOT NULL, position INTEGER NOT NULL, text TEXT NOT NULL, "
                    + "done INTEGER NOT NULL, amountKind TEXT NOT NULL, plannedSets INTEGER, "
                    + "plannedReps INTEGER, plannedDurationSeconds INTEGER, note TEXT NOT NULL, "
                    + "actualRepetitions TEXT NOT NULL, sourceTemplateId TEXT, "
                    + "comboOwnerId TEXT NOT NULL, PRIMARY KEY(id), FOREIGN KEY(occurrenceId) "
                    + "REFERENCES occurrences(id) ON UPDATE NO ACTION ON DELETE CASCADE)");
            database.execSQL("INSERT INTO occurrence_steps SELECT id,occurrenceId,position,text,done,"
                    + "amountKind,plannedSets,plannedReps,plannedDurationSeconds,note,"
                    + "actualRepetitions,sourceTemplateId,comboOwnerId FROM _m_occurrence_steps");
            database.execSQL("CREATE INDEX index_occurrence_steps_occurrenceId "
                    + "ON occurrence_steps(occurrenceId)");

            database.execSQL("CREATE TABLE reward_bookings (id TEXT NOT NULL, "
                    + "transactionId TEXT NOT NULL, occurrenceId TEXT NOT NULL, "
                    + "occurrenceStepId TEXT, ownerId TEXT NOT NULL, kind TEXT NOT NULL, "
                    + "target TEXT NOT NULL, xpDelta INTEGER NOT NULL, "
                    + "comboPointDelta INTEGER NOT NULL, bookedOn TEXT NOT NULL, "
                    + "reversesBookingId TEXT, PRIMARY KEY(id), FOREIGN KEY(occurrenceId) "
                    + "REFERENCES occurrences(id) ON UPDATE NO ACTION ON DELETE CASCADE, "
                    + "FOREIGN KEY(occurrenceStepId) REFERENCES occurrence_steps(id) "
                    + "ON UPDATE NO ACTION ON DELETE CASCADE)");
            database.execSQL("INSERT INTO reward_bookings SELECT 'legacy-step-reward:' || os.id,"
                    + "'legacy-step-reward:' || os.id,os.occurrenceId,os.id,os.comboOwnerId,"
                    + "'LEGACY_STEP','VESSEL',os.earnedXp,os.comboPointDelta,o.scheduledOn,NULL "
                    + "FROM _m_occurrence_steps os JOIN _m_occurrences o ON o.id=os.occurrenceId "
                    + "WHERE os.earnedXp != 0 OR os.comboPointDelta != 0");
            database.execSQL("INSERT INTO reward_bookings SELECT 'legacy-occurrence-reward:' || o.id,"
                    + "'legacy-occurrence-reward:' || o.id,o.id,NULL,'task:' || o.taskId,"
                    + "'LEGACY_COMPLETION','HEAD',o.awardedXp,o.comboPointDelta,"
                    + "CASE WHEN o.completedOn='' THEN o.scheduledOn ELSE o.completedOn END,NULL "
                    + "FROM _m_occurrences o WHERE o.awardedXp != 0 OR o.comboPointDelta != 0");
            database.execSQL("CREATE INDEX index_reward_bookings_transactionId "
                    + "ON reward_bookings(transactionId)");
            database.execSQL("CREATE INDEX index_reward_bookings_occurrenceId "
                    + "ON reward_bookings(occurrenceId)");
            database.execSQL("CREATE INDEX index_reward_bookings_occurrenceStepId "
                    + "ON reward_bookings(occurrenceStepId)");
            database.execSQL("CREATE INDEX index_reward_bookings_ownerId "
                    + "ON reward_bookings(ownerId)");
            database.execSQL("CREATE UNIQUE INDEX index_reward_bookings_reversesBookingId "
                    + "ON reward_bookings(reversesBookingId)");

            database.execSQL("DROP TABLE _m_occurrence_steps");
            database.execSQL("DROP TABLE _m_occurrences");
        }
    };

    /** Normalizes the transitional comma payload into addressable per-slot result rows. */
    public static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS repetition_results ("
                    + "stepId TEXT NOT NULL, slotIndex INTEGER NOT NULL, "
                    + "actualRepetitions INTEGER NOT NULL, PRIMARY KEY(stepId,slotIndex), "
                    + "FOREIGN KEY(stepId) REFERENCES occurrence_steps(id) "
                    + "ON UPDATE NO ACTION ON DELETE CASCADE)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_repetition_results_stepId "
                    + "ON repetition_results(stepId)");
            try (Cursor cursor = database.query("SELECT id,actualRepetitions "
                    + "FROM occurrence_steps WHERE actualRepetitions <> ''")) {
                while (cursor.moveToNext()) {
                    String stepId = cursor.getString(0);
                    String legacy = cursor.getString(1);
                    List<Integer> values = parseLegacyRepetitions(stepId, legacy);
                    if (values == null) continue;
                    for (int index = 0; index < values.size(); index++)
                        database.execSQL("INSERT OR REPLACE INTO repetition_results"
                                        + "(stepId,slotIndex,actualRepetitions) VALUES (?,?,?)",
                                new Object[]{stepId, index, values.get(index)});
                }
            }
        }
    };

    /** Persists carry-forward provenance and enforces one open occurrence per task and slot. */
    public static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE occurrence_steps ADD COLUMN originOccurrenceId TEXT");
            database.execSQL("ALTER TABLE occurrence_steps ADD COLUMN carryForwardReason TEXT "
                    + "NOT NULL DEFAULT 'NONE'");
            database.execSQL("CREATE TRIGGER IF NOT EXISTS occurrence_one_open_insert "
                    + "BEFORE INSERT ON occurrences WHEN NEW.state = 'OPEN' AND EXISTS "
                    + "(SELECT 1 FROM occurrences WHERE taskId = NEW.taskId AND slot = NEW.slot "
                    + "AND state = 'OPEN') BEGIN SELECT RAISE(ABORT, "
                    + "'one open occurrence per task and slot'); END");
            database.execSQL("CREATE TRIGGER IF NOT EXISTS occurrence_one_open_update "
                    + "BEFORE UPDATE OF taskId,slot,state ON occurrences "
                    + "WHEN NEW.state = 'OPEN' AND EXISTS (SELECT 1 FROM occurrences WHERE "
                    + "taskId = NEW.taskId AND slot = NEW.slot AND state = 'OPEN' AND id <> NEW.id) "
                    + "BEGIN SELECT RAISE(ABORT, 'one open occurrence per task and slot'); END");
        }
    };

    /** Replaces the empty completion-date sentinel with a real nullable column. */
    public static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE _m_occurrences_nullable AS SELECT * FROM occurrences");
            database.execSQL("DROP TABLE occurrences");
            database.execSQL("CREATE TABLE occurrences (id TEXT NOT NULL, taskId TEXT NOT NULL, "
                    + "scheduledOn TEXT NOT NULL, state TEXT NOT NULL, sortOrder INTEGER NOT NULL, "
                    + "completedOn TEXT, slot TEXT NOT NULL, PRIMARY KEY(id), FOREIGN KEY(taskId) "
                    + "REFERENCES tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE)");
            database.execSQL("INSERT INTO occurrences SELECT id,taskId,scheduledOn,state,sortOrder,"
                    + "CASE WHEN completedOn = '' THEN NULL ELSE completedOn END,slot "
                    + "FROM _m_occurrences_nullable");
            database.execSQL("CREATE INDEX index_occurrences_taskId ON occurrences(taskId)");
            database.execSQL("CREATE INDEX index_occurrences_state_completedOn "
                    + "ON occurrences(state,completedOn)");
            database.execSQL("CREATE UNIQUE INDEX index_occurrences_taskId_scheduledOn_slot "
                    + "ON occurrences(taskId,scheduledOn,slot)");
            database.execSQL("CREATE TRIGGER occurrence_one_open_insert "
                    + "BEFORE INSERT ON occurrences WHEN NEW.state = 'OPEN' AND EXISTS "
                    + "(SELECT 1 FROM occurrences WHERE taskId = NEW.taskId AND slot = NEW.slot "
                    + "AND state = 'OPEN') BEGIN SELECT RAISE(ABORT, "
                    + "'one open occurrence per task and slot'); END");
            database.execSQL("CREATE TRIGGER occurrence_one_open_update "
                    + "BEFORE UPDATE OF taskId,slot,state ON occurrences "
                    + "WHEN NEW.state = 'OPEN' AND EXISTS (SELECT 1 FROM occurrences WHERE "
                    + "taskId = NEW.taskId AND slot = NEW.slot AND state = 'OPEN' AND id <> NEW.id) "
                    + "BEGIN SELECT RAISE(ABORT, 'one open occurrence per task and slot'); END");
            database.execSQL("DROP TABLE _m_occurrences_nullable");
        }
    };

    /** Replaces optional task-date sentinels with real nullable columns. */
    public static final Migration MIGRATION_10_11 = new Migration(10, 11) {
        @Override public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE _m_tasks_nullable AS SELECT * FROM tasks");
            database.execSQL("DROP TABLE tasks");
            database.execSQL("CREATE TABLE tasks (id TEXT NOT NULL, title TEXT NOT NULL, "
                    + "slot TEXT NOT NULL, recurrence TEXT NOT NULL, intervalDays INTEGER NOT NULL, "
                    + "weekdayMask INTEGER NOT NULL, ongoing INTEGER NOT NULL, conditionText TEXT NOT NULL, "
                    + "conditionDone INTEGER NOT NULL, archived INTEGER NOT NULL, nextDueOn TEXT NOT NULL, "
                    + "lastScheduledOn TEXT, lastCompletedOn TEXT, displayOrder INTEGER NOT NULL, "
                    + "hasCompletedOccurrence INTEGER NOT NULL, estimatedMinutes INTEGER, "
                    + "timeOfDayMask INTEGER NOT NULL, boundKind TEXT NOT NULL, boundUntilOn TEXT, "
                    + "boundWeeks INTEGER, remainingCount INTEGER, deadlineOn TEXT, note TEXT NOT NULL, "
                    + "PRIMARY KEY(id))");
            database.execSQL("INSERT INTO tasks SELECT id,title,slot,recurrence,intervalDays,weekdayMask,"
                    + "ongoing,conditionText,conditionDone,archived,nextDueOn,"
                    + "NULLIF(lastScheduledOn,''),NULLIF(lastCompletedOn,''),displayOrder,"
                    + "hasCompletedOccurrence,estimatedMinutes,timeOfDayMask,boundKind,"
                    + "NULLIF(boundUntilOn,''),boundWeeks,remainingCount,NULLIF(deadlineOn,''),note "
                    + "FROM _m_tasks_nullable");
            database.execSQL("CREATE INDEX index_tasks_archived_conditionDone_displayOrder "
                    + "ON tasks (archived,conditionDone,displayOrder)");
            database.execSQL("DROP TABLE _m_tasks_nullable");
        }
    };

    /** Normalizes independently sortable task placements for each configured time of day. */
    public static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE task_schedule_entries (id TEXT NOT NULL, "
                    + "taskId TEXT NOT NULL, slot TEXT NOT NULL, displayOrder INTEGER NOT NULL, "
                    + "PRIMARY KEY(id), FOREIGN KEY(taskId) REFERENCES tasks(id) "
                    + "ON UPDATE NO ACTION ON DELETE CASCADE)");
            database.execSQL("CREATE INDEX index_task_schedule_entries_taskId "
                    + "ON task_schedule_entries(taskId)");
            database.execSQL("CREATE UNIQUE INDEX index_task_schedule_entries_taskId_slot "
                    + "ON task_schedule_entries(taskId,slot)");
            database.execSQL("CREATE INDEX index_task_schedule_entries_slot_displayOrder "
                    + "ON task_schedule_entries(slot,displayOrder)");
            database.execSQL("INSERT INTO task_schedule_entries(id,taskId,slot,displayOrder) "
                    + "SELECT 'schedule:' || id || ':' || slot,id,slot,displayOrder FROM tasks "
                    + "WHERE recurrence='ONCE'");
            insertScheduleBit(database, 1, "MORNING");
            insertScheduleBit(database, 2, "MIDDAY");
            insertScheduleBit(database, 4, "EVENING");
            insertScheduleBit(database, 8, "LATER");
            database.execSQL("INSERT OR IGNORE INTO task_schedule_entries"
                    + "(id,taskId,slot,displayOrder) SELECT 'schedule:' || id || ':' || slot,"
                    + "id,slot,displayOrder FROM tasks WHERE recurrence<>'ONCE' "
                    + "AND (timeOfDayMask & 15)=0");
        }

        private void insertScheduleBit(SupportSQLiteDatabase database, int bit, String slot) {
            database.execSQL("INSERT INTO task_schedule_entries(id,taskId,slot,displayOrder) "
                    + "SELECT 'schedule:' || id || ':" + slot + "',id,'" + slot
                    + "',displayOrder FROM tasks WHERE recurrence<>'ONCE' "
                    + "AND (timeOfDayMask & " + bit + ")<>0");
        }
    };

    private static List<Integer> parseLegacyRepetitions(String stepId, String stored) {
        List<Integer> values = new ArrayList<>();
        try {
            for (String part : stored.split(",", -1)) {
                if (part.trim().isEmpty()) throw new NumberFormatException("empty slot");
                int value = Integer.parseInt(part.trim());
                if (value < 0) throw new NumberFormatException("negative slot");
                values.add(value);
            }
            return values;
        } catch (NumberFormatException invalid) {
            // Keep the raw transitional column untouched for recovery and make the loss of a
            // structured projection observable without preventing the rest of the DB upgrade.
            Log.e("DatabaseMigrations", "Skipped malformed repetition progress for step "
                    + stepId + ": " + stored, invalid);
            return null;
        }
    }

    private DatabaseMigrations() { }
}
