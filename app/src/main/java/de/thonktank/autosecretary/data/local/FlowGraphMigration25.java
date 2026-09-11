package de.thonktank.autosecretary.data.local;

import android.database.Cursor;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import de.thonktank.autosecretary.domain.model.FlowGraphRun;
import java.util.ArrayList;
import java.util.List;

/**
 * One-way schema cutover: existing identities and ledgers survive; runtime cursors do not.
 */
public final class FlowGraphMigration25 extends Migration {
    public FlowGraphMigration25() { super(24, 25); }

    @Override public void migrate(SupportSQLiteDatabase database) {
        if (!database.inTransaction()) throw new IllegalStateException("Flow migration requires an upgrade transaction");
        requireForeignKeys(database);
        prepareExecutionStates(database);
        migrateDefinitions(database);
        migrateExecutions(database);
        requireForeignKeys(database);
    }

    private static void prepareExecutionStates(SupportSQLiteDatabase db) {
        db.execSQL("CREATE TEMP TABLE _m25_run_state (id TEXT PRIMARY KEY, startStepId TEXT, "
                + "alreadyPaidTau INTEGER, collected INTEGER, cancelled INTEGER)");
        db.execSQL("CREATE TEMP TABLE _m25_step_state (id TEXT PRIMARY KEY, state TEXT, "
                + "chosenDelayMillis INTEGER, readyAtEpochMillis INTEGER, actionAtEpochMillis INTEGER, earnedTau INTEGER)");
        List<String> ids = new ArrayList<>();
        try (Cursor cursor = db.query("SELECT id FROM step_flow_runs ORDER BY id")) {
            while (cursor.moveToNext()) ids.add(cursor.getString(0));
        }
        // Validate and project each run before rebuilding any persisted table. Keep only IDs and
        // compact state rows; do not hold every historical prescription/lease in Android's heap.
        for (String id : ids) {
            FlowGraphRun run = Schema24FlowMigrationInput.read(db, id).converted;
            db.execSQL("INSERT INTO _m25_run_state(id,startStepId,alreadyPaidTau,collected,cancelled) VALUES (?,?,?,?,?)",
                    new Object[]{run.id, run.startStepId, run.alreadyPaidTau, run.collected ? 1 : 0, run.cancelled ? 1 : 0});
            for (FlowGraphRun.Step step : run.steps.values())
                db.execSQL("INSERT INTO _m25_step_state(id,state,chosenDelayMillis,readyAtEpochMillis,actionAtEpochMillis,earnedTau) "
                                + "VALUES (?,?,?,?,?,?)", new Object[]{step.id, step.state.name(), step.chosenDelayMillis,
                                step.readyAtEpochMillis, step.actionAtEpochMillis, step.earnedTau});
        }
    }

    private static void migrateDefinitions(SupportSQLiteDatabase db) {
        // Save receipts deliberately survive task deletion: retrying an old process-restored
        // request must never resurrect an explicitly deleted task.
        db.execSQL("CREATE TABLE flow_editor_saves (requestKey TEXT NOT NULL PRIMARY KEY, taskId TEXT NOT NULL)");
        db.execSQL("ALTER TABLE tasks ADD COLUMN taskKind TEXT NOT NULL DEFAULT 'TASK'");
        db.execSQL("UPDATE tasks SET taskKind='FLOW' WHERE id IN ("
                + "SELECT s.taskId FROM task_steps s JOIN step_transitions t ON t.sourceStepId=s.id "
                + "UNION SELECT taskId FROM step_resource_leases UNION SELECT taskId FROM step_flow_runs "
                + "UNION SELECT taskId FROM flow_candidates)");
        db.execSQL("CREATE TABLE flow_step_waits (stepId TEXT NOT NULL PRIMARY KEY, "
                + "mode TEXT NOT NULL, defaultDelayMillis INTEGER NOT NULL, lastUsedDelayMillis INTEGER, "
                + "FOREIGN KEY(stepId) REFERENCES task_steps(id) ON UPDATE NO ACTION ON DELETE CASCADE)");
        db.execSQL("INSERT INTO flow_step_waits(stepId,mode,defaultDelayMillis,lastUsedDelayMillis) "
                + "SELECT s.id,COALESCE(t.delayMode,'FIXED'),COALESCE(t.defaultDelayMillis,0),t.lastUsedDelayMillis "
                + "FROM task_steps s JOIN tasks task ON task.id=s.taskId "
                + "LEFT JOIN step_transitions t ON t.sourceStepId=s.id WHERE task.taskKind='FLOW'");
        db.execSQL("CREATE TEMP TABLE _m25_edges AS SELECT sourceStepId,targetStepId FROM step_transitions");
        db.execSQL("DROP TABLE step_transitions");
        db.execSQL("CREATE TABLE step_transitions (sourceStepId TEXT NOT NULL, targetStepId TEXT NOT NULL, "
                + "PRIMARY KEY(sourceStepId,targetStepId), "
                + "FOREIGN KEY(sourceStepId) REFERENCES task_steps(id) ON UPDATE NO ACTION ON DELETE CASCADE, "
                + "FOREIGN KEY(targetStepId) REFERENCES task_steps(id) ON UPDATE NO ACTION ON DELETE CASCADE)");
        db.execSQL("CREATE INDEX index_step_transitions_targetStepId ON step_transitions(targetStepId)");
        db.execSQL("INSERT INTO step_transitions(sourceStepId,targetStepId) SELECT sourceStepId,targetStepId FROM _m25_edges");
        db.execSQL("DROP TABLE _m25_edges");
        db.execSQL("ALTER TABLE step_resource_leases ADD COLUMN releaseAfterWait INTEGER NOT NULL DEFAULT 0");
    }

    private static void migrateExecutions(SupportSQLiteDatabase db) {
        String header = "id,taskId,seedStepId,sourceKey,scheduledOn,slot,queueOrder,nextExecutionSequence,createdAtEpochMillis,updatedAtEpochMillis";
        String step = "id,runId,position,sourceTemplateId,text,amountKind,plannedSets,plannedReps,plannedDurationSeconds,"
                + "restTimerMode,restTimerSeconds,plannedLoadMode,plannedLoadUnit,plannedLoadMilli,targetRir,note,"
                + "delayMode,defaultDelayMillis,lastUsedDelayMillis";
        String resource = "id,runId,sourceLeaseId,resourceId,resourceName,capacityAtCreation,units,"
                + "state,reservedAtEpochMillis,activatedAtEpochMillis,releasedAtEpochMillis";
        db.execSQL("CREATE TEMP TABLE _m25_runs AS SELECT " + header + " FROM step_flow_runs");
        db.execSQL("CREATE TEMP TABLE _m25_steps AS SELECT " + step + " FROM flow_run_steps");
        db.execSQL("CREATE TEMP TABLE _m25_resources AS SELECT " + resource
                + ",acquirePosition,releasePosition FROM flow_run_resources");
        db.execSQL("DROP TABLE flow_run_resources");
        db.execSQL("DROP TABLE flow_run_steps");
        db.execSQL("DROP TABLE step_flow_runs");
        db.execSQL("CREATE TABLE step_flow_runs (id TEXT NOT NULL PRIMARY KEY, taskId TEXT NOT NULL, "
                + "seedStepId TEXT NOT NULL, startStepId TEXT NOT NULL, sourceKey TEXT NOT NULL, scheduledOn TEXT NOT NULL, "
                + "slot TEXT NOT NULL, queueOrder INTEGER NOT NULL, nextExecutionSequence INTEGER NOT NULL, "
                + "createdAtEpochMillis INTEGER NOT NULL, updatedAtEpochMillis INTEGER NOT NULL, "
                + "alreadyPaidTau INTEGER NOT NULL, collected INTEGER NOT NULL, cancelled INTEGER NOT NULL, "
                + "FOREIGN KEY(taskId) REFERENCES tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE)");
        db.execSQL("INSERT INTO step_flow_runs(" + header + ",startStepId,alreadyPaidTau,collected,cancelled) "
                + "SELECT r.id,r.taskId,r.seedStepId,r.sourceKey,r.scheduledOn,r.slot,r.queueOrder,r.nextExecutionSequence,"
                + "r.createdAtEpochMillis,r.updatedAtEpochMillis,s.startStepId,s.alreadyPaidTau,s.collected,s.cancelled "
                + "FROM _m25_runs r JOIN _m25_run_state s ON s.id=r.id");
        db.execSQL("CREATE INDEX index_step_flow_runs_taskId ON step_flow_runs(taskId)");
        db.execSQL("CREATE INDEX index_step_flow_runs_seedStepId ON step_flow_runs(seedStepId)");
        db.execSQL("CREATE UNIQUE INDEX index_step_flow_runs_sourceKey ON step_flow_runs(sourceKey)");
        db.execSQL("CREATE INDEX index_step_flow_runs_collected_cancelled_queueOrder ON step_flow_runs(collected,cancelled,queueOrder)");

        db.execSQL("CREATE TABLE flow_run_steps (id TEXT NOT NULL PRIMARY KEY, runId TEXT NOT NULL, position INTEGER NOT NULL, "
                + "sourceTemplateId TEXT NOT NULL, text TEXT NOT NULL, amountKind TEXT NOT NULL, plannedSets INTEGER, "
                + "plannedReps INTEGER, plannedDurationSeconds INTEGER, restTimerMode TEXT NOT NULL, restTimerSeconds INTEGER, "
                + "plannedLoadMode TEXT NOT NULL, plannedLoadUnit TEXT NOT NULL, plannedLoadMilli INTEGER, targetRir INTEGER NOT NULL, "
                + "note TEXT NOT NULL, delayMode TEXT NOT NULL, defaultDelayMillis INTEGER NOT NULL, lastUsedDelayMillis INTEGER, "
                + "state TEXT NOT NULL, chosenDelayMillis INTEGER, readyAtEpochMillis INTEGER, actionAtEpochMillis INTEGER, earnedTau INTEGER NOT NULL, "
                + "FOREIGN KEY(runId) REFERENCES step_flow_runs(id) ON UPDATE NO ACTION ON DELETE CASCADE)");
        db.execSQL("INSERT INTO flow_run_steps(" + step + ",state,chosenDelayMillis,readyAtEpochMillis,actionAtEpochMillis,earnedTau) "
                + "SELECT s.id,s.runId,s.position,s.sourceTemplateId,s.text,s.amountKind,s.plannedSets,s.plannedReps,s.plannedDurationSeconds,"
                + "s.restTimerMode,s.restTimerSeconds,s.plannedLoadMode,s.plannedLoadUnit,s.plannedLoadMilli,s.targetRir,s.note,"
                + "COALESCE(s.delayMode,'FIXED'),COALESCE(s.defaultDelayMillis,0),s.lastUsedDelayMillis,"
                + "p.state,p.chosenDelayMillis,p.readyAtEpochMillis,p.actionAtEpochMillis,p.earnedTau "
                + "FROM _m25_steps s JOIN _m25_step_state p ON p.id=s.id");
        db.execSQL("CREATE INDEX index_flow_run_steps_runId ON flow_run_steps(runId)");
        db.execSQL("CREATE UNIQUE INDEX index_flow_run_steps_runId_position ON flow_run_steps(runId,position)");
        db.execSQL("CREATE UNIQUE INDEX index_flow_run_steps_runId_sourceTemplateId ON flow_run_steps(runId,sourceTemplateId)");
        db.execSQL("CREATE INDEX index_flow_run_steps_state_readyAtEpochMillis ON flow_run_steps(state,readyAtEpochMillis)");
        db.execSQL("CREATE TABLE flow_run_edges (runId TEXT NOT NULL, sourceStepId TEXT NOT NULL, targetStepId TEXT NOT NULL, "
                + "PRIMARY KEY(runId,sourceStepId,targetStepId), "
                + "FOREIGN KEY(runId) REFERENCES step_flow_runs(id) ON UPDATE NO ACTION ON DELETE CASCADE, "
                + "FOREIGN KEY(sourceStepId) REFERENCES flow_run_steps(id) ON UPDATE NO ACTION ON DELETE CASCADE, "
                + "FOREIGN KEY(targetStepId) REFERENCES flow_run_steps(id) ON UPDATE NO ACTION ON DELETE CASCADE)");
        db.execSQL("CREATE INDEX index_flow_run_edges_sourceStepId ON flow_run_edges(sourceStepId)");
        db.execSQL("CREATE INDEX index_flow_run_edges_targetStepId ON flow_run_edges(targetStepId)");
        db.execSQL("INSERT INTO flow_run_edges(runId,sourceStepId,targetStepId) SELECT a.runId,a.id,b.id "
                + "FROM _m25_steps a JOIN _m25_steps b ON b.runId=a.runId AND b.position=a.position+1");

        db.execSQL("CREATE TABLE flow_run_resources (id TEXT NOT NULL PRIMARY KEY, runId TEXT NOT NULL, sourceLeaseId TEXT NOT NULL, "
                + "resourceId TEXT NOT NULL, resourceName TEXT NOT NULL, capacityAtCreation INTEGER NOT NULL, units INTEGER NOT NULL, "
                + "state TEXT NOT NULL, reservedAtEpochMillis INTEGER, activatedAtEpochMillis INTEGER, releasedAtEpochMillis INTEGER, "
                + "acquireStepId TEXT NOT NULL, releaseStepId TEXT NOT NULL, releaseAfterWait INTEGER NOT NULL, "
                + "FOREIGN KEY(runId) REFERENCES step_flow_runs(id) ON UPDATE NO ACTION ON DELETE CASCADE, "
                + "FOREIGN KEY(acquireStepId) REFERENCES flow_run_steps(id) ON UPDATE NO ACTION ON DELETE CASCADE, "
                + "FOREIGN KEY(releaseStepId) REFERENCES flow_run_steps(id) ON UPDATE NO ACTION ON DELETE CASCADE)");
        db.execSQL("INSERT INTO flow_run_resources(" + resource + ",acquireStepId,releaseStepId,releaseAfterWait) "
                + "SELECT r.id,r.runId,r.sourceLeaseId,r.resourceId,r.resourceName,r.capacityAtCreation,r.units,"
                + "r.state,r.reservedAtEpochMillis,r.activatedAtEpochMillis,r.releasedAtEpochMillis,a.id,b.id,0 "
                + "FROM _m25_resources r JOIN _m25_steps a ON a.runId=r.runId AND a.position=r.acquirePosition "
                + "JOIN _m25_steps b ON b.runId=r.runId AND b.position=r.releasePosition");
        db.execSQL("CREATE INDEX index_flow_run_resources_runId ON flow_run_resources(runId)");
        db.execSQL("CREATE INDEX index_flow_run_resources_resourceId_state ON flow_run_resources(resourceId,state)");
        db.execSQL("CREATE INDEX index_flow_run_resources_acquireStepId ON flow_run_resources(acquireStepId)");
        db.execSQL("CREATE INDEX index_flow_run_resources_releaseStepId ON flow_run_resources(releaseStepId)");

        db.execSQL("ALTER TABLE occurrence_steps ADD COLUMN flowRunStepId TEXT");
        db.execSQL("UPDATE occurrence_steps SET flowRunStepId=(SELECT r.id FROM flow_run_steps r "
                + "JOIN occurrences o ON o.flowRunId=r.runId WHERE o.id=occurrence_steps.occurrenceId "
                + "AND r.sourceTemplateId=occurrence_steps.sourceTemplateId)");
        db.execSQL("CREATE INDEX index_occurrence_steps_flowRunStepId ON occurrence_steps(flowRunStepId)");
        for (String name : new String[]{"_m25_resources", "_m25_steps", "_m25_runs", "_m25_step_state", "_m25_run_state"})
            db.execSQL("DROP TABLE " + name);
    }

    private static void requireForeignKeys(SupportSQLiteDatabase db) {
        try (Cursor cursor = db.query("PRAGMA foreign_key_check")) {
            if (cursor.moveToFirst()) throw new IllegalStateException("Flow upgrade found a broken foreign key in " + cursor.getString(0));
        }
    }
}
