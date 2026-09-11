package de.thonktank.autosecretary.data.local;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;
import de.thonktank.autosecretary.domain.model.*;
import java.util.HashSet;

/** Schema-25 persistence boundary. It must share the admission/action/ledger transaction. */
public final class FlowGraphSnapshotWriter {
    private final SupportSQLiteDatabase database;
    private final FlowGraphSnapshotReader reader;

    public FlowGraphSnapshotWriter(SupportSQLiteDatabase database) {
        this.database = database;
        reader = new FlowGraphSnapshotReader(database);
    }

    public void insert(FlowCandidate candidate, FlowGraphRun run, long now) {
        requireTransaction(now);
        if (!candidate.taskId.equals(run.taskId)
                || !candidate.seedStepId.equals(run.steps.get(run.startStepId).source.id))
            throw new IllegalArgumentException("Candidate and run disagree");
        database.execSQL("INSERT INTO step_flow_runs(id,taskId,seedStepId,startStepId,sourceKey,scheduledOn,slot,"
                        + "queueOrder,nextExecutionSequence,createdAtEpochMillis,updatedAtEpochMillis,alreadyPaidTau,collected,cancelled) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)", new Object[]{run.id, run.taskId.value,
                        candidate.seedStepId, run.startStepId, candidate.sourceKey, candidate.scheduledOn.toString(),
                        candidate.slot.storageCode, candidate.queueOrder, 0, now, now, run.alreadyPaidTau,
                        run.collected ? 1 : 0, run.cancelled ? 1 : 0});
        int position = 0;
        for (String id : run.graph.stepIds) {
            FlowGraphRun.Step step = run.steps.get(id);
            ContentValues values = stepState(step);
            values.put("id", id); values.put("runId", run.id); values.put("position", position++);
            values.put("sourceTemplateId", step.source.id); values.put("text", step.source.title);
            putPrescription(values, step.source.prescription);
            values.put("note", step.source.note); values.put("delayMode", step.source.waitAfter.mode.name());
            values.put("defaultDelayMillis", step.source.waitAfter.defaultDelayMillis);
            values.put("lastUsedDelayMillis", step.source.waitAfter.lastUsedDelayMillis);
            database.insert("flow_run_steps", SQLiteDatabase.CONFLICT_ABORT, values);
        }
        for (FlowTileGraph.Link link : run.graph.links)
            database.execSQL("INSERT INTO flow_run_edges(runId,sourceStepId,targetStepId) VALUES (?,?,?)",
                    new Object[]{run.id, link.source, link.target});
        for (FlowGraphRun.Lease lease : run.leases) {
            String resourceName;
            int total;
            try (Cursor cursor = database.query("SELECT name,capacity FROM capacity_resources WHERE id=?", new Object[]{lease.resourceId})) {
                if (!cursor.moveToFirst()) throw new IllegalStateException("Capacity disappeared during admission");
                resourceName = cursor.getString(0); total = cursor.getInt(1);
            }
            boolean reserved = lease.state != FlowResourceState.PLANNED;
            boolean activated = lease.state == FlowResourceState.ACTIVE || lease.state == FlowResourceState.RELEASED;
            database.execSQL("INSERT INTO flow_run_resources(id,runId,sourceLeaseId,resourceId,resourceName,capacityAtCreation,"
                            + "units,acquireStepId,releaseStepId,releaseAfterWait,state,reservedAtEpochMillis,activatedAtEpochMillis,releasedAtEpochMillis) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)", new Object[]{lease.id, run.id, lease.sourceLeaseId,
                            lease.resourceId, resourceName, total, lease.units, lease.acquireStepId, lease.releaseStepId,
                            lease.releaseAfterWait ? 1 : 0, lease.state.name(), reserved ? now : null,
                            activated ? now : null, lease.state == FlowResourceState.RELEASED ? now : null});
        }
    }

    public void update(FlowGraphRun run, long now) {
        requireTransaction(now);
        FlowGraphRun before = reader.find(run.id);
        if (before == null || !before.taskId.equals(run.taskId) || !before.startStepId.equals(run.startStepId)
                || !before.graph.stepIds.equals(run.graph.stepIds)
                || !new HashSet<>(before.graph.links).equals(new HashSet<>(run.graph.links))
                || before.leases.size() != run.leases.size() || run.alreadyPaidTau < before.alreadyPaidTau
                || (before.collected && !run.collected) || before.cancelled != run.cancelled)
            throw new IllegalArgumentException("Execution update cannot replace its frozen snapshot or paid history");
        for (FlowGraphRun.Step step : run.steps.values()) {
            FlowGraphDefinition.Node original = before.steps.get(step.id).source;
            if (!original.id.equals(step.source.id) || !original.title.equals(step.source.title)
                    || !original.note.equals(step.source.note) || !original.prescription.equals(step.source.prescription)
                    || original.waitAfter.mode != step.source.waitAfter.mode
                    || original.waitAfter.defaultDelayMillis != step.source.waitAfter.defaultDelayMillis
                    || !java.util.Objects.equals(original.waitAfter.lastUsedDelayMillis, step.source.waitAfter.lastUsedDelayMillis))
                throw new IllegalArgumentException("Execution update cannot edit its prescription");
        }
        java.util.Map<String, FlowGraphRun.Lease> oldLeases = new java.util.HashMap<>();
        for (FlowGraphRun.Lease lease : before.leases) oldLeases.put(lease.id, lease);
        for (FlowGraphRun.Lease lease : run.leases) {
            FlowGraphRun.Lease old = oldLeases.get(lease.id);
            if (old == null || !old.sourceLeaseId.equals(lease.sourceLeaseId) || !old.resourceId.equals(lease.resourceId)
                    || !old.acquireStepId.equals(lease.acquireStepId) || !old.releaseStepId.equals(lease.releaseStepId)
                    || old.units != lease.units || old.releaseAfterWait != lease.releaseAfterWait)
                throw new IllegalArgumentException("Execution update cannot edit capacity bindings");
        }
        for (FlowGraphRun.Step step : run.steps.values())
            if (database.update("flow_run_steps", SQLiteDatabase.CONFLICT_ABORT, stepState(step),
                    "id=? AND runId=?", new Object[]{step.id, run.id}) != 1)
                throw new IllegalStateException("Execution step disappeared");
        for (FlowGraphRun.Lease lease : run.leases) {
            FlowGraphRun.Lease old = oldLeases.get(lease.id);
            if (old.state == lease.state) continue;
            ContentValues values = new ContentValues(); values.put("state", lease.state.name());
            if (lease.state == FlowResourceState.PLANNED) {
                values.putNull("reservedAtEpochMillis");
                values.putNull("activatedAtEpochMillis");
            }
            if (lease.state != FlowResourceState.RELEASED) values.putNull("releasedAtEpochMillis");
            if (old.state == FlowResourceState.PLANNED) values.put("reservedAtEpochMillis", now);
            if ((old.state == FlowResourceState.PLANNED || old.state == FlowResourceState.RESERVED)
                    && (lease.state == FlowResourceState.ACTIVE || lease.state == FlowResourceState.RELEASED))
                values.put("activatedAtEpochMillis", now);
            if (lease.state == FlowResourceState.RELEASED) values.put("releasedAtEpochMillis", now);
            if (database.update("flow_run_resources", SQLiteDatabase.CONFLICT_ABORT, values,
                    "id=? AND runId=?", new Object[]{lease.id, run.id}) != 1)
                throw new IllegalStateException("Execution capacity disappeared");
        }
        database.execSQL("UPDATE step_flow_runs SET alreadyPaidTau=?,collected=?,updatedAtEpochMillis=? WHERE id=?",
                new Object[]{run.alreadyPaidTau, run.collected ? 1 : 0, now, run.id});
    }

    private static ContentValues stepState(FlowGraphRun.Step step) {
        ContentValues values = new ContentValues();
        values.put("state", step.state.name()); values.put("chosenDelayMillis", step.chosenDelayMillis);
        values.put("readyAtEpochMillis", step.readyAtEpochMillis); values.put("actionAtEpochMillis", step.actionAtEpochMillis);
        values.put("earnedTau", step.earnedTau);
        return values;
    }

    /** Serialize the frozen graph payload directly; never create an old linear snapshot to write it. */
    private static void putPrescription(ContentValues values, StepPrescription prescription) {
        StepAmount amount = prescription.amount;
        values.put("amountKind", amount.kind().storageCode());
        values.putNull("plannedSets"); values.putNull("plannedReps"); values.putNull("plannedDurationSeconds");
        if (amount instanceof StepAmount.SetsReps) {
            values.put("plannedSets", ((StepAmount.SetsReps) amount).sets);
            values.put("plannedReps", ((StepAmount.SetsReps) amount).repetitions);
        } else if (amount instanceof StepAmount.Repetitions) {
            values.put("plannedReps", ((StepAmount.Repetitions) amount).repetitions);
        } else if (amount instanceof StepAmount.Duration) {
            values.put("plannedDurationSeconds", ((StepAmount.Duration) amount).seconds);
        }
        values.put("restTimerMode", prescription.rest.mode.name());
        values.put("restTimerSeconds", prescription.rest.customSeconds);
        ResistanceLoad load = prescription.plannedLoad();
        values.put("plannedLoadMode", load.mode.name()); values.put("plannedLoadUnit", load.unit.name());
        values.put("plannedLoadMilli", load.milliUnits); values.put("targetRir", prescription.targetRir());
    }

    private void requireTransaction(long now) {
        if (!database.inTransaction()) throw new IllegalStateException("Snapshot write requires a transaction");
        if (now < 0L) throw new IllegalArgumentException("Timestamp must not be negative");
    }
}
