package de.thonktank.autosecretary.data.local;

import android.database.Cursor;
import androidx.sqlite.db.SupportSQLiteDatabase;
import de.thonktank.autosecretary.domain.model.*;
import de.thonktank.autosecretary.domain.usecase.MigrateLinearFlowExecution;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads the old schema by column name before its cursor/position columns are removed. */
final class Schema24FlowMigrationInput {
    final FlowRunSnapshot legacy;
    final FlowGraphRun converted;

    private Schema24FlowMigrationInput(FlowRunSnapshot legacy, FlowGraphRun converted) {
        this.legacy = legacy;
        this.converted = converted;
    }

    static Schema24FlowMigrationInput read(SupportSQLiteDatabase database, String runId) {
        StepFlowEntityMapper mapper = new StepFlowEntityMapper();
        StepFlowRun header;
        try (Cursor cursor = database.query("SELECT * FROM step_flow_runs WHERE id=?", new Object[]{runId})) {
            if (!cursor.moveToFirst()) throw new IllegalArgumentException("Missing legacy flow " + runId);
            Row row = new Row(cursor);
            header = mapper.toDomain(new StepFlowRunEntity(row.text("id"), row.text("taskId"),
                    row.text("seedStepId"), row.text("sourceKey"), row.text("scheduledOn"),
                    row.text("slot"), row.text("state"), row.integer("currentPosition"),
                    row.optionalLong("readyAtEpochMillis"), row.text("currentExecutionOccurrenceId"),
                    row.number("queueOrder"), row.integer("nextExecutionSequence"),
                    row.number("createdAtEpochMillis"), row.number("updatedAtEpochMillis")));
        }
        List<FlowRunStepSnapshot> steps = new ArrayList<>();
        try (Cursor cursor = database.query("SELECT * FROM flow_run_steps WHERE runId=? ORDER BY position", new Object[]{runId})) {
            Row row = new Row(cursor);
            while (cursor.moveToNext()) steps.add(mapper.toDomain(new FlowRunStepEntity(
                    row.text("id"), row.text("runId"), row.integer("position"), row.text("sourceTemplateId"),
                    row.text("text"), row.text("amountKind"), row.optionalInt("plannedSets"),
                    row.optionalInt("plannedReps"), row.optionalInt("plannedDurationSeconds"),
                    row.text("restTimerMode"), row.optionalInt("restTimerSeconds"),
                    row.text("plannedLoadMode"), row.text("plannedLoadUnit"), row.optionalLong("plannedLoadMilli"),
                    row.integer("targetRir"), row.text("note"), row.text("delayMode"),
                    row.optionalLong("defaultDelayMillis"), row.optionalLong("lastUsedDelayMillis"),
                    row.optionalLong("chosenDelayMillis"))));
        }
        List<FlowRunResourceSnapshot> resources = new ArrayList<>();
        try (Cursor cursor = database.query("SELECT * FROM flow_run_resources WHERE runId=? ORDER BY id", new Object[]{runId})) {
            Row row = new Row(cursor);
            while (cursor.moveToNext()) resources.add(mapper.toDomain(new FlowRunResourceEntity(
                    row.text("id"), row.text("runId"), row.text("sourceLeaseId"), row.text("resourceId"),
                    row.text("resourceName"), row.integer("capacityAtCreation"), row.integer("units"),
                    row.integer("acquirePosition"), row.integer("releasePosition"), row.text("state"),
                    row.optionalLong("reservedAtEpochMillis"), row.optionalLong("activatedAtEpochMillis"),
                    row.optionalLong("releasedAtEpochMillis"))));
        }
        FlowRunSnapshot legacy = new FlowRunSnapshot(header, steps, resources);
        Map<String, String> snapshotByTemplate = new LinkedHashMap<>();
        for (FlowRunStepSnapshot step : steps)
            if (snapshotByTemplate.put(step.sourceTemplateId, step.id) != null)
                throw new IllegalArgumentException("Ambiguous legacy flow template " + runId);
        Map<String, Long> earned = new LinkedHashMap<>();
        long paid = 0L;
        // A booking is counted once, even when its occurrence step was copied on carry-forward.
        // Its immutable source identifies the earned step; its current reporting assignment
        // identifies which occurrence harvested it. HEAD XP includes a multiplier, not principal.
        String query = "SELECT b.xpDelta,s.sourceTemplateId,"
                + "CASE WHEN EXISTS (SELECT 1 FROM reward_bookings h WHERE h.occurrenceId=report.id "
                + "AND h.target='HEAD' AND h.reversesBookingId IS NULL AND h.kind!='COMBO_DECAY' "
                + "AND NOT EXISTS (SELECT 1 FROM reward_bookings r WHERE r.reversesBookingId=h.id)) "
                + "THEN 1 ELSE 0 END AS paid "
                + "FROM reward_bookings b JOIN occurrences origin ON origin.id=b.occurrenceId "
                + "LEFT JOIN occurrence_steps s ON s.id=b.occurrenceStepId "
                + "LEFT JOIN reward_assignments a ON a.bookingId=b.id "
                + "JOIN occurrences report ON report.id=COALESCE(a.occurrenceId,b.occurrenceId) "
                + "WHERE origin.flowRunId=? AND b.target='VESSEL'";
        try (Cursor cursor = database.query(query, new Object[]{runId})) {
            while (cursor.moveToNext()) {
                String stepId = snapshotByTemplate.get(cursor.getString(1));
                if (stepId == null) throw new IllegalArgumentException("Unattributed legacy flow reward " + runId);
                long xp = cursor.getLong(0);
                earned.put(stepId, Math.addExact(earned.getOrDefault(stepId, 0L), xp));
                if (cursor.getInt(2) == 1) paid = Math.addExact(paid, xp);
            }
        }
        return new Schema24FlowMigrationInput(legacy,
                new MigrateLinearFlowExecution().execute(legacy, earned, paid));
    }

    private static final class Row {
        private final Cursor cursor;
        Row(Cursor cursor) { this.cursor = cursor; }
        String text(String name) { return cursor.getString(cursor.getColumnIndexOrThrow(name)); }
        long number(String name) { return cursor.getLong(cursor.getColumnIndexOrThrow(name)); }
        int integer(String name) { return Math.toIntExact(number(name)); }
        Long optionalLong(String name) {
            int index = cursor.getColumnIndexOrThrow(name);
            return cursor.isNull(index) ? null : cursor.getLong(index);
        }
        Integer optionalInt(String name) {
            Long value = optionalLong(name);
            return value == null ? null : Math.toIntExact(value);
        }
    }
}
