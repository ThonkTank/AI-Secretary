package de.thonktank.autosecretary.data.local;

import android.database.Cursor;
import androidx.sqlite.db.SupportSQLiteDatabase;
import de.thonktank.autosecretary.domain.model.*;
import de.thonktank.autosecretary.domain.repository.FlowGraphRunRepository;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;

/** Schema-25 execution capability on the same Room connection as occurrences and the ledger. */
public final class SqlFlowGraphRunRepository implements FlowGraphRunRepository {
    private final Supplier<SupportSQLiteDatabase> connection;

    public SqlFlowGraphRunRepository(Supplier<SupportSQLiteDatabase> connection) {
        this.connection = Objects.requireNonNull(connection);
    }

    @Override public FlowGraphRunRecord find(String runId) {
        SupportSQLiteDatabase db = database();
        try (Cursor cursor = db.query("SELECT sourceKey,scheduledOn,slot,queueOrder,nextExecutionSequence,"
                + "createdAtEpochMillis,updatedAtEpochMillis FROM step_flow_runs WHERE id=?", new Object[]{runId})) {
            if (!cursor.moveToFirst()) return null;
            return new FlowGraphRunRecord(new FlowGraphSnapshotReader(db).find(runId), cursor.getString(0),
                    LocalDate.parse(cursor.getString(1)), TaskSlot.fromStorage(cursor.getString(2)),
                    cursor.getLong(3), Math.toIntExact(cursor.getLong(4)), cursor.getLong(5), cursor.getLong(6));
        }
    }

    @Override public FlowGraphRunRecord findBySourceKey(String sourceKey) {
        return findSelected("SELECT id FROM step_flow_runs WHERE sourceKey=?", sourceKey);
    }

    @Override public FlowGraphRunRecord findByStepId(String runtimeStepId) {
        return findSelected("SELECT runId FROM flow_run_steps WHERE id=?", runtimeStepId);
    }

    private FlowGraphRunRecord findSelected(String query, String identity) {
        String id;
        try (Cursor cursor = database().query(query, new Object[]{identity})) {
            if (!cursor.moveToFirst()) return null;
            id = cursor.getString(0);
        }
        return find(id);
    }

    @Override public List<FlowGraphRunRecord> active() { return activeRecords(null); }

    @Override public List<FlowGraphRunRecord> active(TaskId taskId) {
        return activeRecords(Objects.requireNonNull(taskId));
    }

    private List<FlowGraphRunRecord> activeRecords(TaskId taskId) {
        List<String> ids = new ArrayList<>();
        try (Cursor cursor = database().query("SELECT id FROM step_flow_runs WHERE collected=0 AND cancelled=0"
                + (taskId == null ? "" : " AND taskId=?")
                + " ORDER BY queueOrder,createdAtEpochMillis,id",
                taskId == null ? new Object[]{} : new Object[]{taskId.value})) {
            while (cursor.moveToNext()) ids.add(cursor.getString(0));
        }
        List<FlowGraphRunRecord> result = new ArrayList<>();
        for (String id : ids) result.add(Objects.requireNonNull(find(id)));
        return Collections.unmodifiableList(result);
    }

    @Override public Long nextReadyAtEpochMillis() {
        try (Cursor cursor = database().query("SELECT MIN(s.readyAtEpochMillis) FROM flow_run_steps s "
                + "JOIN step_flow_runs r ON r.id=s.runId WHERE s.state='WAITING_TIME' "
                + "AND r.collected=0 AND r.cancelled=0")) {
            return cursor.moveToFirst() && !cursor.isNull(0) ? cursor.getLong(0) : null;
        }
    }

    @Override public Map<String, Long> consumingUnitsExcluding(String runId) {
        requireIdentity(runId);
        Map<String, Long> result = new LinkedHashMap<>();
        // Do not join current definitions or capacities: deleting/editing either must not
        // hide an already reserved resource. The reducer treats a missing total as zero.
        try (Cursor cursor = database().query("SELECT resourceId,SUM(units) FROM flow_run_resources "
                + "WHERE runId<>? AND state IN ('RESERVED','ACTIVE') GROUP BY resourceId ORDER BY resourceId",
                new Object[]{runId})) {
            while (cursor.moveToNext()) result.put(cursor.getString(0), cursor.getLong(1));
        }
        return Collections.unmodifiableMap(result);
    }

    @Override public void insert(FlowCandidate candidate, FlowGraphRun run, long now) {
        new FlowGraphSnapshotWriter(database()).insert(candidate, run, now);
    }

    @Override public void update(FlowGraphRun run, long now) {
        new FlowGraphSnapshotWriter(database()).update(run, now);
    }

    @Override public boolean reorder(String runId, long queueOrder, long now) {
        requireIdentity(runId); requireTime(now);
        if (queueOrder < 0L) throw new IllegalArgumentException("Queue order must not be negative");
        FlowGraphRunRecord before = find(runId);
        if (before == null || before.run.collected || before.run.cancelled || before.queueOrder == queueOrder)
            return false;
        database().execSQL("UPDATE step_flow_runs SET queueOrder=?,updatedAtEpochMillis=? WHERE id=?",
                new Object[]{queueOrder, now, runId});
        return true;
    }

    @Override public int allocateExecutionSequence(String runId, long now) {
        requireIdentity(runId); requireTime(now);
        FlowGraphRunRecord before = find(runId);
        if (before == null || before.run.collected || before.run.cancelled)
            throw new IllegalStateException("No active flow execution to attach an occurrence to");
        int allocated;
        try (Cursor history = database().query("SELECT COALESCE(MAX(flowExecutionSequence),0) FROM occurrences WHERE flowRunId=?",
                new Object[]{runId})) {
            history.moveToFirst();
            allocated = Math.toIntExact(Math.max((long) before.nextExecutionSequence,
                    Math.addExact(history.getLong(0), 1L)));
        }
        // Older counters can lag behind retained history. Also respect the actual unique key
        // if a legacy row's sequence metadata differs; never replace its history or children.
        while (hasExecutionKey(runId, allocated)) allocated = Math.addExact(allocated, 1);
        int next = Math.addExact(allocated, 1);
        database().execSQL("UPDATE step_flow_runs SET nextExecutionSequence=?,updatedAtEpochMillis=? WHERE id=?",
                new Object[]{next, now, runId});
        return allocated;
    }

    private boolean hasExecutionKey(String runId, int sequence) {
        try (Cursor existing = database().query("SELECT 1 FROM occurrences WHERE sourceKey=?",
                new Object[]{"flow-step:" + runId + ':' + sequence})) {
            return existing.moveToFirst();
        }
    }

    private SupportSQLiteDatabase database() {
        SupportSQLiteDatabase db = connection.get();
        if (!db.inTransaction()) throw new IllegalStateException("Flow execution requires a consistent Room transaction");
        return db;
    }

    private static void requireIdentity(String value) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("Run identity is missing");
    }

    private static void requireTime(long now) {
        if (now < 0L) throw new IllegalArgumentException("Timestamp must not be negative");
    }
}
