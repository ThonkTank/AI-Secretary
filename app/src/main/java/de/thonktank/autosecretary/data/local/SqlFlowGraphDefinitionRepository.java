package de.thonktank.autosecretary.data.local;

import android.database.Cursor;
import androidx.sqlite.db.SupportSQLiteDatabase;
import de.thonktank.autosecretary.domain.model.*;
import de.thonktank.autosecretary.domain.repository.FlowGraphDefinitionRepository;
import de.thonktank.autosecretary.domain.repository.StepRepository;
import java.util.*;
import java.util.function.Supplier;

/** Schema-25 graph capability sharing the catalog's Room connection and transaction. */
public final class SqlFlowGraphDefinitionRepository implements FlowGraphDefinitionRepository {
    private final Supplier<SupportSQLiteDatabase> connection;
    private final StepRepository steps;

    public SqlFlowGraphDefinitionRepository(Supplier<SupportSQLiteDatabase> connection, StepRepository steps) {
        this.connection = connection; this.steps = steps;
    }

    @Override public boolean isFlowTask(TaskId taskId) {
        try (Cursor cursor = database().query("SELECT taskKind FROM tasks WHERE id=?", new Object[]{taskId.value})) {
            return cursor.moveToFirst() && "FLOW".equals(cursor.getString(0));
        }
    }

    @Override public FlowGraphDefinition find(TaskId taskId) {
        SupportSQLiteDatabase db = database();
        if (!isFlowTask(taskId)) return null;
        List<TaskStepTemplate> templates = steps.templates(taskId);
        Map<String, FlowDelayPolicy> waits = new HashMap<>();
        try (Cursor cursor = db.query("SELECT w.stepId,w.mode,w.defaultDelayMillis,w.lastUsedDelayMillis "
                + "FROM flow_step_waits w JOIN task_steps s ON s.id=w.stepId WHERE s.taskId=?", new Object[]{taskId.value})) {
            while (cursor.moveToNext()) waits.put(cursor.getString(0), new FlowDelayPolicy(
                    FlowDelayPolicy.Mode.valueOf(cursor.getString(1)), cursor.getLong(2),
                    cursor.isNull(3) ? null : cursor.getLong(3)));
        }
        List<String> ids = new ArrayList<>();
        List<FlowGraphDefinition.Node> nodes = new ArrayList<>();
        for (TaskStepTemplate step : templates) {
            ids.add(step.id);
            nodes.add(new FlowGraphDefinition.Node(step.id, step.text, step.prescription, step.note,
                    Objects.requireNonNull(waits.get(step.id), "Missing flow step wait")));
        }
        List<FlowTileGraph.Link> links = new ArrayList<>();
        try (Cursor cursor = db.query("SELECT t.sourceStepId,t.targetStepId FROM step_transitions t "
                + "JOIN task_steps s ON s.id=t.sourceStepId WHERE s.taskId=? ORDER BY s.position,t.targetStepId", new Object[]{taskId.value})) {
            while (cursor.moveToNext()) links.add(new FlowTileGraph.Link(cursor.getString(0), cursor.getString(1)));
        }
        List<FlowGraphDefinition.Lease> leases = new ArrayList<>();
        try (Cursor cursor = db.query("SELECT id,resourceId,acquireStepId,releaseStepId,units,releaseAfterWait "
                + "FROM step_resource_leases WHERE taskId=? ORDER BY id", new Object[]{taskId.value})) {
            while (cursor.moveToNext()) leases.add(new FlowGraphDefinition.Lease(cursor.getString(0), cursor.getString(1),
                    cursor.getString(2), cursor.getString(3), cursor.getInt(4), cursor.getInt(5) != 0));
        }
        return new FlowGraphDefinition(taskId, new FlowTileGraph(ids, links), nodes, leases);
    }

    @Override public void replace(FlowGraphDefinition definition) {
        SupportSQLiteDatabase db = database();
        Set<String> templateIds = new HashSet<>();
        for (TaskStepTemplate step : steps.templates(definition.taskId)) templateIds.add(step.id);
        if (!templateIds.equals(definition.nodes.keySet()))
            throw new IllegalArgumentException("Definition does not own exactly these task steps");
        db.execSQL("UPDATE tasks SET taskKind='FLOW' WHERE id=?", new Object[]{definition.taskId.value});
        db.execSQL("DELETE FROM step_resource_leases WHERE taskId=?", new Object[]{definition.taskId.value});
        db.execSQL("DELETE FROM step_transitions WHERE sourceStepId IN (SELECT id FROM task_steps WHERE taskId=?)",
                new Object[]{definition.taskId.value});
        db.execSQL("DELETE FROM flow_step_waits WHERE stepId IN (SELECT id FROM task_steps WHERE taskId=?)",
                new Object[]{definition.taskId.value});
        for (FlowGraphDefinition.Node node : definition.nodes.values())
            db.execSQL("INSERT INTO flow_step_waits(stepId,mode,defaultDelayMillis,lastUsedDelayMillis) VALUES (?,?,?,?)",
                    new Object[]{node.id, node.waitAfter.mode.name(), node.waitAfter.defaultDelayMillis, node.waitAfter.lastUsedDelayMillis});
        for (FlowTileGraph.Link link : definition.graph.links)
            db.execSQL("INSERT INTO step_transitions(sourceStepId,targetStepId) VALUES (?,?)", new Object[]{link.source, link.target});
        for (FlowGraphDefinition.Lease lease : definition.leases)
            db.execSQL("INSERT INTO step_resource_leases(id,taskId,resourceId,acquireStepId,releaseStepId,units,releaseAfterWait) VALUES (?,?,?,?,?,?,?)",
                    new Object[]{lease.id, definition.taskId.value, lease.resourceId, lease.acquireStepId, lease.releaseStepId,
                            lease.units, lease.releaseAfterWait ? 1 : 0});
    }

    @Override public TaskId findSaveResult(String requestKey) {
        try (Cursor cursor = database().query("SELECT taskId FROM flow_editor_saves WHERE requestKey=?", new Object[]{requestKey})) {
            return cursor.moveToFirst() ? TaskId.of(cursor.getString(0)) : null;
        }
    }

    @Override public void recordSaveResult(String requestKey, TaskId taskId) {
        database().execSQL("INSERT INTO flow_editor_saves(requestKey,taskId) VALUES (?,?)", new Object[]{requestKey, taskId.value});
    }

    private SupportSQLiteDatabase database() {
        SupportSQLiteDatabase db = connection.get();
        if (!db.inTransaction()) throw new IllegalStateException("Graph definitions require a consistent Room transaction");
        return db;
    }
}
