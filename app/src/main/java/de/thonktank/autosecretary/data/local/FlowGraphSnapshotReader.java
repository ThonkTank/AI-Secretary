package de.thonktank.autosecretary.data.local;

import android.database.Cursor;
import androidx.sqlite.db.SupportSQLiteDatabase;
import de.thonktank.autosecretary.domain.model.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Named-column read adapter for the prepared schema-25 execution snapshot. */
public final class FlowGraphSnapshotReader {
    private static final String GRAPH_COLUMNS = "id,taskId,startStepId,alreadyPaidTau,collected,cancelled";
    private final SupportSQLiteDatabase database;

    public FlowGraphSnapshotReader(SupportSQLiteDatabase database) { this.database = database; }

    /** Call inside the caller's transaction so steps, waits and claims belong to one revision. */
    public FlowGraphRun find(String runId) {
        requireTransaction();
        Object[] args = {runId};
        try (Cursor header = database.query("SELECT " + GRAPH_COLUMNS + " FROM step_flow_runs WHERE id=?", args)) {
            if (!header.moveToFirst()) return null;
            return assemble(new Row(header), readChildren("runId=?", args));
        }
    }

    List<FlowGraphRunRecord> readRecords(String runFilter, Object[] args) {
        requireTransaction();
        String fromRuns = " FROM step_flow_runs WHERE " + runFilter;
        try (Cursor cursor = database.query("SELECT " + GRAPH_COLUMNS + ",sourceKey,scheduledOn,slot,queueOrder,"
                + "nextExecutionSequence,createdAtEpochMillis,updatedAtEpochMillis" + fromRuns
                + " ORDER BY queueOrder,createdAtEpochMillis,id", args)) {
            if (!cursor.moveToFirst()) return Collections.emptyList();
            Map<String, Children> children = readChildren("runId IN (SELECT id" + fromRuns + ")", args);
            List<FlowGraphRunRecord> records = new ArrayList<>();
            Row row = new Row(cursor);
            do {
                records.add(new FlowGraphRunRecord(assemble(row, children), row.text("sourceKey"),
                        LocalDate.parse(row.text("scheduledOn")), TaskSlot.fromStorage(row.text("slot")),
                        row.number("queueOrder"), Math.toIntExact(row.number("nextExecutionSequence")),
                        row.number("createdAtEpochMillis"), row.number("updatedAtEpochMillis")));
            } while (cursor.moveToNext());
            return Collections.unmodifiableList(records);
        }
    }

    private void requireTransaction() {
        if (!database.inTransaction()) throw new IllegalStateException("Snapshot read requires a transaction");
    }

    private static FlowGraphRun assemble(Row header, Map<String, Children> byRun) {
        String id = header.text("id");
        Children children = byRun.computeIfAbsent(id, ignored -> new Children());
        List<String> stepIds = new ArrayList<>(children.steps.size());
        for (FlowGraphRun.Step step : children.steps) stepIds.add(step.id);
        return new FlowGraphRun(id, TaskId.of(header.text("taskId")), header.text("startStepId"),
                new FlowTileGraph(stepIds, children.edges), children.steps, children.leases,
                header.number("alreadyPaidTau"), header.number("collected") != 0, header.number("cancelled") != 0);
    }

    private static final class Children {
        final List<FlowGraphRun.Step> steps = new ArrayList<>();
        final List<FlowTileGraph.Link> edges = new ArrayList<>();
        final List<FlowGraphRun.Lease> leases = new ArrayList<>();
    }

    private Map<String, Children> readChildren(String filter, Object[] args) {
        Map<String, Children> byRun = new HashMap<>();
        try (Cursor cursor = database.query("SELECT * FROM flow_run_steps WHERE " + filter
                + " ORDER BY runId,position", args)) {
            Row row = new Row(cursor);
            while (cursor.moveToNext()) {
                Children children = byRun.computeIfAbsent(row.text("runId"), ignored -> new Children());
                StepPrescription prescription = new StepPrescription(StepAmount.fromStorage(
                        StepAmountKind.fromStorage(row.text("amountKind")), row.optionalInt("plannedSets"),
                        row.optionalInt("plannedReps"), row.optionalInt("plannedDurationSeconds")),
                        RestTimerPolicy.fromStorage(row.text("restTimerMode"), row.optionalInt("restTimerSeconds")));
                FlowGraphDefinition.Node node = new FlowGraphDefinition.Node(row.text("sourceTemplateId"),
                        row.text("text"), prescription, row.text("note"), new FlowDelayPolicy(
                        FlowDelayPolicy.Mode.valueOf(row.text("delayMode")), row.number("defaultDelayMillis"),
                        row.optionalLong("lastUsedDelayMillis")));
                children.steps.add(new FlowGraphRun.Step(row.text("id"), node, FlowGraphRun.State.valueOf(row.text("state")),
                        row.optionalLong("chosenDelayMillis"), row.optionalLong("readyAtEpochMillis"),
                        row.optionalLong("actionAtEpochMillis"), row.number("earnedTau")));
            }
        }
        try (Cursor cursor = database.query("SELECT runId,sourceStepId,targetStepId FROM flow_run_edges WHERE "
                + filter + " ORDER BY runId,sourceStepId,targetStepId", args)) {
            while (cursor.moveToNext()) {
                Children children = byRun.computeIfAbsent(cursor.getString(0), ignored -> new Children());
                children.edges.add(new FlowTileGraph.Link(cursor.getString(1), cursor.getString(2)));
            }
        }
        try (Cursor cursor = database.query("SELECT runId,id,sourceLeaseId,resourceId,acquireStepId,releaseStepId,"
                + "units,releaseAfterWait,state FROM flow_run_resources WHERE " + filter + " ORDER BY runId,id", args)) {
            while (cursor.moveToNext()) {
                Children children = byRun.computeIfAbsent(cursor.getString(0), ignored -> new Children());
                children.leases.add(new FlowGraphRun.Lease(cursor.getString(1), cursor.getString(2),
                        cursor.getString(3), cursor.getString(4), cursor.getString(5), cursor.getInt(6),
                        cursor.getInt(7) != 0, FlowResourceState.valueOf(cursor.getString(8))));
            }
        }
        return byRun;
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
