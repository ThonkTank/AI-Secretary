package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.AgentTaskSnapshot;
import de.thonktank.autosecretary.domain.model.TaskCatalog;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.repository.TaskCatalogQuery;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Loads the canonical normal-task inventory shared with the chat agent. */
public final class LoadAgentTaskCatalog {
    private final TaskCatalogQuery catalog;

    public LoadAgentTaskCatalog(TaskCatalogQuery catalog) {
        if (catalog == null) throw new IllegalArgumentException("Task catalog is required");
        this.catalog = catalog;
    }

    public List<AgentTaskSnapshot> execute() {
        TaskCatalog value = catalog.execute();
        return AgentTaskSnapshot.from(value);
    }

    Map<TaskId, AgentTaskSnapshot> snapshotsById() {
        Map<TaskId, AgentTaskSnapshot> snapshots = new HashMap<>();
        for (AgentTaskSnapshot task : execute()) snapshots.put(task.id, task);
        return snapshots;
    }
}
