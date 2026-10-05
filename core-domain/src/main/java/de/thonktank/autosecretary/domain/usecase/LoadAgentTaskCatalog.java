package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.AgentTaskSnapshot;
import de.thonktank.autosecretary.domain.model.TaskCatalog;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.repository.TaskCatalogQuery;

import java.util.List;

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

    AgentTaskSnapshot find(TaskId id) {
        for (AgentTaskSnapshot task : execute()) if (task.id.equals(id)) return task;
        return null;
    }
}
