package de.thonktank.autosecretary.data.local;

import android.database.Cursor;
import androidx.sqlite.db.SupportSQLiteDatabase;
import de.thonktank.autosecretary.domain.model.*;
import java.util.ArrayList;
import java.util.List;

/** Named-column read adapter for the prepared schema-25 execution snapshot. */
public final class FlowGraphSnapshotReader {
    private final SupportSQLiteDatabase database;

    public FlowGraphSnapshotReader(SupportSQLiteDatabase database) { this.database = database; }

    /** Call inside the caller's transaction so steps, waits and claims belong to one revision. */
    public FlowGraphRun find(String runId) {
        if (!database.inTransaction()) throw new IllegalStateException("Snapshot read requires a transaction");
        String taskId;
        String startStepId;
        long paid;
        boolean collected;
        boolean cancelled;
        try (Cursor cursor = database.query("SELECT taskId,startStepId,alreadyPaidTau,collected,cancelled "
                + "FROM step_flow_runs WHERE id=?", new Object[]{runId})) {
            if (!cursor.moveToFirst()) return null;
            taskId = cursor.getString(0); startStepId = cursor.getString(1); paid = cursor.getLong(2);
            collected = cursor.getInt(3) != 0; cancelled = cursor.getInt(4) != 0;
        }
        List<String> ids = new ArrayList<>();
        List<FlowGraphRun.Step> steps = new ArrayList<>();
        try (Cursor cursor = database.query("SELECT * FROM flow_run_steps WHERE runId=? ORDER BY position", new Object[]{runId})) {
            Row row = new Row(cursor);
            while (cursor.moveToNext()) {
                String id = row.text("id"); ids.add(id);
                StepPrescription prescription = StepPrescription.restore(StepAmount.fromStorage(
                        StepAmountKind.fromStorage(row.text("amountKind")), row.optionalInt("plannedSets"),
                        row.optionalInt("plannedReps"), row.optionalInt("plannedDurationSeconds")),
                        RestTimerPolicy.fromStorage(row.text("restTimerMode"), row.optionalInt("restTimerSeconds")),
                        ResistanceLoad.restore(row.text("plannedLoadMode"), row.text("plannedLoadUnit"),
                                row.optionalLong("plannedLoadMilli")), Math.toIntExact(row.number("targetRir")));
                FlowGraphDefinition.Node node = new FlowGraphDefinition.Node(row.text("sourceTemplateId"),
                        row.text("text"), prescription, row.text("note"), new FlowDelayPolicy(
                        FlowDelayPolicy.Mode.valueOf(row.text("delayMode")), row.number("defaultDelayMillis"),
                        row.optionalLong("lastUsedDelayMillis")));
                steps.add(new FlowGraphRun.Step(id, node, FlowGraphRun.State.valueOf(row.text("state")),
                        row.optionalLong("chosenDelayMillis"), row.optionalLong("readyAtEpochMillis"),
                        row.optionalLong("actionAtEpochMillis"), row.number("earnedTau")));
            }
        }
        List<FlowTileGraph.Link> edges = new ArrayList<>();
        try (Cursor cursor = database.query("SELECT sourceStepId,targetStepId FROM flow_run_edges WHERE runId=? "
                + "ORDER BY sourceStepId,targetStepId", new Object[]{runId})) {
            while (cursor.moveToNext()) edges.add(new FlowTileGraph.Link(cursor.getString(0), cursor.getString(1)));
        }
        List<FlowGraphRun.Lease> leases = new ArrayList<>();
        try (Cursor cursor = database.query("SELECT id,sourceLeaseId,resourceId,acquireStepId,releaseStepId,units,"
                + "releaseAfterWait,state FROM flow_run_resources WHERE runId=? ORDER BY id", new Object[]{runId})) {
            while (cursor.moveToNext()) leases.add(new FlowGraphRun.Lease(cursor.getString(0), cursor.getString(1),
                    cursor.getString(2), cursor.getString(3), cursor.getString(4), cursor.getInt(5),
                    cursor.getInt(6) != 0, FlowResourceState.valueOf(cursor.getString(7))));
        }
        return new FlowGraphRun(runId, TaskId.of(taskId), startStepId, new FlowTileGraph(ids, edges),
                steps, leases, paid, collected, cancelled);
    }

    private static final class Row {
        private final Cursor cursor;
        Row(Cursor cursor) { this.cursor = cursor; }
        String text(String name) { return cursor.getString(cursor.getColumnIndexOrThrow(name)); }
        long number(String name) { return cursor.getLong(cursor.getColumnIndexOrThrow(name)); }
        Long optionalLong(String name) {
            int index = cursor.getColumnIndexOrThrow(name);
            return cursor.isNull(index) ? null : cursor.getLong(index);
        }
        Integer optionalInt(String name) {
            Long value = optionalLong(name); return value == null ? null : Math.toIntExact(value);
        }
    }
}
