package de.thonktank.autosecretary.data.local;

import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

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

    private DatabaseMigrations() { }
}
