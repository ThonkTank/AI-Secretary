package de.thonktank.autosecretary.data.local;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/** Retires the assistant while preserving notes, repetitions and every dependent runtime row. */
public final class RemoveTrainingMigration28 extends Migration {
    public RemoveTrainingMigration28() { super(27, 28); }

    @Override public void migrate(SupportSQLiteDatabase db) {
        appendLoads(db, "task_steps");
        appendLoads(db, "occurrence_steps");
        appendLoads(db, "flow_run_steps");
        // Back up the full foreign-key closure before dropping parents: cascades must not
        // erase rewards, timers, flow edges or resource leases. All column maps are explicit.
        db.execSQL("CREATE TEMP TABLE _training28_task_steps AS SELECT `id`,`taskId`,`position`,`text`,`weekdayMask`,`intervalDays`,`amountKind`,`plannedSets`,`plannedReps`,`plannedDurationSeconds`,`restTimerMode`,`restTimerSeconds`,`note`,`activationKind` FROM `task_steps`");
        db.execSQL("CREATE TEMP TABLE _training28_occurrence_steps AS SELECT `id`,`occurrenceId`,`position`,`text`,`done`,`amountKind`,`plannedSets`,`plannedReps`,`plannedDurationSeconds`,`restTimerMode`,`restTimerSeconds`,`note`,`actualRepetitions`,`sourceTemplateId`,`flowRunStepId`,`comboOwnerId`,`originOccurrenceId`,`carryForwardReason` FROM `occurrence_steps`");
        db.execSQL("CREATE TEMP TABLE _training28_reward_bookings AS SELECT `id`,`transactionId`,`occurrenceId`,`occurrenceStepId`,`ownerId`,`kind`,`target`,`xpDelta`,`comboPointDelta`,`bookedOn`,`reversesBookingId`,`plannedXp` FROM `reward_bookings`");
        db.execSQL("CREATE TEMP TABLE _training28_reward_assignments AS SELECT `bookingId`,`occurrenceId` FROM `reward_assignments`");
        db.execSQL("CREATE TEMP TABLE _training28_repetition_results AS SELECT `stepId`,`slotIndex`,`actualRepetitions` FROM `repetition_results`");
        db.execSQL("CREATE TEMP TABLE _training28_timer_sessions AS SELECT `id`,`stepId`,`title`,`kind`,`state`,`totalSeconds`,`remainingMillis`,`targetElapsedRealtime`,`targetEpochMillis`,`notificationId`,`completionObserved` FROM `timer_sessions`");
        db.execSQL("CREATE TEMP TABLE _training28_step_transitions AS SELECT `sourceStepId`,`targetStepId` FROM `step_transitions`");
        db.execSQL("CREATE TEMP TABLE _training28_flow_step_waits AS SELECT `stepId`,`mode`,`defaultDelayMillis`,`lastUsedDelayMillis` FROM `flow_step_waits`");
        db.execSQL("CREATE TEMP TABLE _training28_step_resource_leases AS SELECT `id`,`taskId`,`acquireStepId`,`releaseStepId`,`resourceId`,`units`,`releaseAfterWait` FROM `step_resource_leases`");
        db.execSQL("CREATE TEMP TABLE _training28_flow_run_steps AS SELECT `id`,`runId`,`position`,`sourceTemplateId`,`text`,`amountKind`,`plannedSets`,`plannedReps`,`plannedDurationSeconds`,`restTimerMode`,`restTimerSeconds`,`note`,`delayMode`,`defaultDelayMillis`,`lastUsedDelayMillis`,`state`,`chosenDelayMillis`,`readyAtEpochMillis`,`actionAtEpochMillis`,`earnedTau` FROM `flow_run_steps`");
        db.execSQL("CREATE TEMP TABLE _training28_flow_run_resources AS SELECT `id`,`runId`,`sourceLeaseId`,`resourceId`,`resourceName`,`capacityAtCreation`,`units`,`state`,`reservedAtEpochMillis`,`activatedAtEpochMillis`,`releasedAtEpochMillis`,`acquireStepId`,`releaseStepId`,`releaseAfterWait` FROM `flow_run_resources`");
        db.execSQL("CREATE TEMP TABLE _training28_flow_run_edges AS SELECT `runId`,`sourceStepId`,`targetStepId` FROM `flow_run_edges`");
        db.execSQL("DROP TABLE `training_adjustments`");
        db.execSQL("DROP TABLE `training_load_requests`");
        db.execSQL("DROP TABLE `flow_run_edges`");
        db.execSQL("DROP TABLE `flow_run_resources`");
        db.execSQL("DROP TABLE `flow_run_steps`");
        db.execSQL("DROP TABLE `step_resource_leases`");
        db.execSQL("DROP TABLE `flow_step_waits`");
        db.execSQL("DROP TABLE `step_transitions`");
        db.execSQL("DROP TABLE `timer_sessions`");
        db.execSQL("DROP TABLE `repetition_results`");
        db.execSQL("DROP TABLE `reward_assignments`");
        db.execSQL("DROP TABLE `reward_bookings`");
        db.execSQL("DROP TABLE `occurrence_steps`");
        db.execSQL("DROP TABLE `task_steps`");
        db.execSQL("CREATE TABLE IF NOT EXISTS `task_steps` (`id` TEXT NOT NULL, `taskId` TEXT NOT NULL, `position` INTEGER NOT NULL, `text` TEXT NOT NULL, `weekdayMask` INTEGER NOT NULL, `intervalDays` INTEGER NOT NULL, `amountKind` TEXT NOT NULL, `plannedSets` INTEGER, `plannedReps` INTEGER, `plannedDurationSeconds` INTEGER, `restTimerMode` TEXT NOT NULL, `restTimerSeconds` INTEGER, `note` TEXT NOT NULL, `activationKind` TEXT NOT NULL DEFAULT 'SCHEDULED', PRIMARY KEY(`id`), FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_steps_taskId` ON `task_steps` (`taskId`)");
        db.execSQL("INSERT INTO `task_steps` (`id`,`taskId`,`position`,`text`,`weekdayMask`,`intervalDays`,`amountKind`,`plannedSets`,`plannedReps`,`plannedDurationSeconds`,`restTimerMode`,`restTimerSeconds`,`note`,`activationKind`) SELECT `id`,`taskId`,`position`,`text`,`weekdayMask`,`intervalDays`,`amountKind`,`plannedSets`,`plannedReps`,`plannedDurationSeconds`,`restTimerMode`,`restTimerSeconds`,`note`,`activationKind` FROM _training28_task_steps");
        db.execSQL("DROP TABLE _training28_task_steps");
        db.execSQL("CREATE TABLE IF NOT EXISTS `occurrence_steps` (`id` TEXT NOT NULL, `occurrenceId` TEXT NOT NULL, `position` INTEGER NOT NULL, `text` TEXT NOT NULL, `done` INTEGER NOT NULL, `amountKind` TEXT NOT NULL, `plannedSets` INTEGER, `plannedReps` INTEGER, `plannedDurationSeconds` INTEGER, `restTimerMode` TEXT NOT NULL, `restTimerSeconds` INTEGER, `note` TEXT NOT NULL, `actualRepetitions` TEXT NOT NULL, `sourceTemplateId` TEXT, `flowRunStepId` TEXT, `comboOwnerId` TEXT NOT NULL, `originOccurrenceId` TEXT, `carryForwardReason` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`occurrenceId`) REFERENCES `occurrences`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_occurrence_steps_occurrenceId` ON `occurrence_steps` (`occurrenceId`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_occurrence_steps_flowRunStepId` ON `occurrence_steps` (`flowRunStepId`)");
        db.execSQL("INSERT INTO `occurrence_steps` (`id`,`occurrenceId`,`position`,`text`,`done`,`amountKind`,`plannedSets`,`plannedReps`,`plannedDurationSeconds`,`restTimerMode`,`restTimerSeconds`,`note`,`actualRepetitions`,`sourceTemplateId`,`flowRunStepId`,`comboOwnerId`,`originOccurrenceId`,`carryForwardReason`) SELECT `id`,`occurrenceId`,`position`,`text`,`done`,`amountKind`,`plannedSets`,`plannedReps`,`plannedDurationSeconds`,`restTimerMode`,`restTimerSeconds`,`note`,`actualRepetitions`,`sourceTemplateId`,`flowRunStepId`,`comboOwnerId`,`originOccurrenceId`,`carryForwardReason` FROM _training28_occurrence_steps");
        db.execSQL("DROP TABLE _training28_occurrence_steps");
        db.execSQL("CREATE TABLE IF NOT EXISTS `reward_bookings` (`id` TEXT NOT NULL, `transactionId` TEXT NOT NULL, `occurrenceId` TEXT NOT NULL, `occurrenceStepId` TEXT, `ownerId` TEXT NOT NULL, `kind` TEXT NOT NULL, `target` TEXT NOT NULL, `xpDelta` INTEGER NOT NULL, `comboPointDelta` INTEGER NOT NULL, `bookedOn` TEXT NOT NULL, `reversesBookingId` TEXT, `plannedXp` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`occurrenceId`) REFERENCES `occurrences`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`occurrenceStepId`) REFERENCES `occurrence_steps`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reward_bookings_transactionId` ON `reward_bookings` (`transactionId`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reward_bookings_occurrenceId` ON `reward_bookings` (`occurrenceId`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reward_bookings_occurrenceStepId` ON `reward_bookings` (`occurrenceStepId`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reward_bookings_ownerId` ON `reward_bookings` (`ownerId`)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_reward_bookings_reversesBookingId` ON `reward_bookings` (`reversesBookingId`)");
        db.execSQL("INSERT INTO `reward_bookings` (`id`,`transactionId`,`occurrenceId`,`occurrenceStepId`,`ownerId`,`kind`,`target`,`xpDelta`,`comboPointDelta`,`bookedOn`,`reversesBookingId`,`plannedXp`) SELECT `id`,`transactionId`,`occurrenceId`,`occurrenceStepId`,`ownerId`,`kind`,`target`,`xpDelta`,`comboPointDelta`,`bookedOn`,`reversesBookingId`,`plannedXp` FROM _training28_reward_bookings");
        db.execSQL("DROP TABLE _training28_reward_bookings");
        db.execSQL("CREATE TABLE IF NOT EXISTS `reward_assignments` (`bookingId` TEXT NOT NULL, `occurrenceId` TEXT NOT NULL, PRIMARY KEY(`bookingId`), FOREIGN KEY(`bookingId`) REFERENCES `reward_bookings`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`occurrenceId`) REFERENCES `occurrences`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reward_assignments_occurrenceId` ON `reward_assignments` (`occurrenceId`)");
        db.execSQL("INSERT INTO `reward_assignments` (`bookingId`,`occurrenceId`) SELECT `bookingId`,`occurrenceId` FROM _training28_reward_assignments");
        db.execSQL("DROP TABLE _training28_reward_assignments");
        db.execSQL("CREATE TABLE IF NOT EXISTS `repetition_results` (`stepId` TEXT NOT NULL, `slotIndex` INTEGER NOT NULL, `actualRepetitions` INTEGER NOT NULL, PRIMARY KEY(`stepId`, `slotIndex`), FOREIGN KEY(`stepId`) REFERENCES `occurrence_steps`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_repetition_results_stepId` ON `repetition_results` (`stepId`)");
        db.execSQL("INSERT INTO `repetition_results` (`stepId`,`slotIndex`,`actualRepetitions`) SELECT `stepId`,`slotIndex`,`actualRepetitions` FROM _training28_repetition_results");
        db.execSQL("DROP TABLE _training28_repetition_results");
        db.execSQL("CREATE TABLE IF NOT EXISTS `timer_sessions` (`id` TEXT NOT NULL, `stepId` TEXT NOT NULL, `title` TEXT NOT NULL, `kind` TEXT NOT NULL, `state` TEXT NOT NULL, `totalSeconds` INTEGER NOT NULL, `remainingMillis` INTEGER NOT NULL, `targetElapsedRealtime` INTEGER NOT NULL, `targetEpochMillis` INTEGER NOT NULL, `notificationId` INTEGER NOT NULL, `completionObserved` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`stepId`) REFERENCES `occurrence_steps`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_timer_sessions_stepId` ON `timer_sessions` (`stepId`)");
        db.execSQL("INSERT INTO `timer_sessions` (`id`,`stepId`,`title`,`kind`,`state`,`totalSeconds`,`remainingMillis`,`targetElapsedRealtime`,`targetEpochMillis`,`notificationId`,`completionObserved`) SELECT `id`,`stepId`,`title`,`kind`,`state`,`totalSeconds`,`remainingMillis`,`targetElapsedRealtime`,`targetEpochMillis`,`notificationId`,`completionObserved` FROM _training28_timer_sessions");
        db.execSQL("DROP TABLE _training28_timer_sessions");
        db.execSQL("CREATE TABLE IF NOT EXISTS `step_transitions` (`sourceStepId` TEXT NOT NULL, `targetStepId` TEXT NOT NULL, PRIMARY KEY(`sourceStepId`, `targetStepId`), FOREIGN KEY(`sourceStepId`) REFERENCES `task_steps`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`targetStepId`) REFERENCES `task_steps`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_step_transitions_targetStepId` ON `step_transitions` (`targetStepId`)");
        db.execSQL("INSERT INTO `step_transitions` (`sourceStepId`,`targetStepId`) SELECT `sourceStepId`,`targetStepId` FROM _training28_step_transitions");
        db.execSQL("DROP TABLE _training28_step_transitions");
        db.execSQL("CREATE TABLE IF NOT EXISTS `flow_step_waits` (`stepId` TEXT NOT NULL, `mode` TEXT NOT NULL, `defaultDelayMillis` INTEGER NOT NULL, `lastUsedDelayMillis` INTEGER, PRIMARY KEY(`stepId`), FOREIGN KEY(`stepId`) REFERENCES `task_steps`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        db.execSQL("INSERT INTO `flow_step_waits` (`stepId`,`mode`,`defaultDelayMillis`,`lastUsedDelayMillis`) SELECT `stepId`,`mode`,`defaultDelayMillis`,`lastUsedDelayMillis` FROM _training28_flow_step_waits");
        db.execSQL("DROP TABLE _training28_flow_step_waits");
        db.execSQL("CREATE TABLE IF NOT EXISTS `step_resource_leases` (`id` TEXT NOT NULL, `taskId` TEXT NOT NULL, `acquireStepId` TEXT NOT NULL, `releaseStepId` TEXT NOT NULL, `resourceId` TEXT NOT NULL, `units` INTEGER NOT NULL, `releaseAfterWait` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`), FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`acquireStepId`) REFERENCES `task_steps`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`releaseStepId`) REFERENCES `task_steps`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`resourceId`) REFERENCES `capacity_resources`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_step_resource_leases_taskId` ON `step_resource_leases` (`taskId`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_step_resource_leases_acquireStepId` ON `step_resource_leases` (`acquireStepId`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_step_resource_leases_releaseStepId` ON `step_resource_leases` (`releaseStepId`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_step_resource_leases_resourceId` ON `step_resource_leases` (`resourceId`)");
        db.execSQL("INSERT INTO `step_resource_leases` (`id`,`taskId`,`acquireStepId`,`releaseStepId`,`resourceId`,`units`,`releaseAfterWait`) SELECT `id`,`taskId`,`acquireStepId`,`releaseStepId`,`resourceId`,`units`,`releaseAfterWait` FROM _training28_step_resource_leases");
        db.execSQL("DROP TABLE _training28_step_resource_leases");
        db.execSQL("CREATE TABLE IF NOT EXISTS `flow_run_steps` (`id` TEXT NOT NULL, `runId` TEXT NOT NULL, `position` INTEGER NOT NULL, `sourceTemplateId` TEXT NOT NULL, `text` TEXT NOT NULL, `amountKind` TEXT NOT NULL, `plannedSets` INTEGER, `plannedReps` INTEGER, `plannedDurationSeconds` INTEGER, `restTimerMode` TEXT NOT NULL, `restTimerSeconds` INTEGER, `note` TEXT NOT NULL, `delayMode` TEXT NOT NULL, `defaultDelayMillis` INTEGER NOT NULL, `lastUsedDelayMillis` INTEGER, `state` TEXT NOT NULL, `chosenDelayMillis` INTEGER, `readyAtEpochMillis` INTEGER, `actionAtEpochMillis` INTEGER, `earnedTau` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`runId`) REFERENCES `step_flow_runs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_flow_run_steps_runId` ON `flow_run_steps` (`runId`)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_flow_run_steps_runId_position` ON `flow_run_steps` (`runId`, `position`)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_flow_run_steps_runId_sourceTemplateId` ON `flow_run_steps` (`runId`, `sourceTemplateId`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_flow_run_steps_state_readyAtEpochMillis` ON `flow_run_steps` (`state`, `readyAtEpochMillis`)");
        db.execSQL("INSERT INTO `flow_run_steps` (`id`,`runId`,`position`,`sourceTemplateId`,`text`,`amountKind`,`plannedSets`,`plannedReps`,`plannedDurationSeconds`,`restTimerMode`,`restTimerSeconds`,`note`,`delayMode`,`defaultDelayMillis`,`lastUsedDelayMillis`,`state`,`chosenDelayMillis`,`readyAtEpochMillis`,`actionAtEpochMillis`,`earnedTau`) SELECT `id`,`runId`,`position`,`sourceTemplateId`,`text`,`amountKind`,`plannedSets`,`plannedReps`,`plannedDurationSeconds`,`restTimerMode`,`restTimerSeconds`,`note`,`delayMode`,`defaultDelayMillis`,`lastUsedDelayMillis`,`state`,`chosenDelayMillis`,`readyAtEpochMillis`,`actionAtEpochMillis`,`earnedTau` FROM _training28_flow_run_steps");
        db.execSQL("DROP TABLE _training28_flow_run_steps");
        db.execSQL("CREATE TABLE IF NOT EXISTS `flow_run_resources` (`id` TEXT NOT NULL, `runId` TEXT NOT NULL, `sourceLeaseId` TEXT NOT NULL, `resourceId` TEXT NOT NULL, `resourceName` TEXT NOT NULL, `capacityAtCreation` INTEGER NOT NULL, `units` INTEGER NOT NULL, `state` TEXT NOT NULL, `reservedAtEpochMillis` INTEGER, `activatedAtEpochMillis` INTEGER, `releasedAtEpochMillis` INTEGER, `acquireStepId` TEXT NOT NULL, `releaseStepId` TEXT NOT NULL, `releaseAfterWait` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`runId`) REFERENCES `step_flow_runs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`acquireStepId`) REFERENCES `flow_run_steps`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`releaseStepId`) REFERENCES `flow_run_steps`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_flow_run_resources_runId` ON `flow_run_resources` (`runId`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_flow_run_resources_resourceId_state` ON `flow_run_resources` (`resourceId`, `state`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_flow_run_resources_acquireStepId` ON `flow_run_resources` (`acquireStepId`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_flow_run_resources_releaseStepId` ON `flow_run_resources` (`releaseStepId`)");
        db.execSQL("INSERT INTO `flow_run_resources` (`id`,`runId`,`sourceLeaseId`,`resourceId`,`resourceName`,`capacityAtCreation`,`units`,`state`,`reservedAtEpochMillis`,`activatedAtEpochMillis`,`releasedAtEpochMillis`,`acquireStepId`,`releaseStepId`,`releaseAfterWait`) SELECT `id`,`runId`,`sourceLeaseId`,`resourceId`,`resourceName`,`capacityAtCreation`,`units`,`state`,`reservedAtEpochMillis`,`activatedAtEpochMillis`,`releasedAtEpochMillis`,`acquireStepId`,`releaseStepId`,`releaseAfterWait` FROM _training28_flow_run_resources");
        db.execSQL("DROP TABLE _training28_flow_run_resources");
        db.execSQL("CREATE TABLE IF NOT EXISTS `flow_run_edges` (`runId` TEXT NOT NULL, `sourceStepId` TEXT NOT NULL, `targetStepId` TEXT NOT NULL, PRIMARY KEY(`runId`, `sourceStepId`, `targetStepId`), FOREIGN KEY(`runId`) REFERENCES `step_flow_runs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`sourceStepId`) REFERENCES `flow_run_steps`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`targetStepId`) REFERENCES `flow_run_steps`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_flow_run_edges_sourceStepId` ON `flow_run_edges` (`sourceStepId`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_flow_run_edges_targetStepId` ON `flow_run_edges` (`targetStepId`)");
        db.execSQL("INSERT INTO `flow_run_edges` (`runId`,`sourceStepId`,`targetStepId`) SELECT `runId`,`sourceStepId`,`targetStepId` FROM _training28_flow_run_edges");
        db.execSQL("DROP TABLE _training28_flow_run_edges");
    }

    private static void appendLoads(SupportSQLiteDatabase db, String table) {
        try (Cursor rows = db.query("SELECT id,note,plannedLoadMode,plannedLoadUnit,plannedLoadMilli FROM " + table)) {
            while (rows.moveToNext()) {
                String original = rows.getString(1);
                String note = LegacyTrainingNote.append(original, rows.getString(2), rows.getString(3),
                        rows.isNull(4) ? null : rows.getLong(4));
                if (note.equals(original)) continue;
                ContentValues values = new ContentValues(); values.put("note", note);
                db.update(table, SQLiteDatabase.CONFLICT_ABORT, values, "id=?", new Object[]{rows.getString(0)});
            }
        }
    }
}
