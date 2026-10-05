package de.thonktank.autosecretary.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** One user-confirmable, all-or-nothing set of normal-task changes. */
public final class AgentTaskChangeSet {
    public static final int MAX_OPERATIONS = 20;

    public final String id;
    public final String summary;
    public final List<AgentTaskOperation> operations;

    public AgentTaskChangeSet(String id, String summary, List<AgentTaskOperation> operations) {
        if (id == null || id.trim().isEmpty())
            throw new IllegalArgumentException("Agent proposal identity is required");
        if (summary == null || summary.trim().isEmpty())
            throw new IllegalArgumentException("Agent proposal summary is required");
        if (operations == null || operations.isEmpty() || operations.size() > MAX_OPERATIONS)
            throw new IllegalArgumentException("Agent proposal needs 1 to 20 operations");
        List<AgentTaskOperation> copied = new ArrayList<>();
        Set<TaskId> referenced = new HashSet<>();
        for (AgentTaskOperation operation : operations) {
            if (operation == null) throw new IllegalArgumentException("Agent operation is required");
            if (operation.taskId != null && !referenced.add(operation.taskId))
                throw new IllegalArgumentException("A task can occur only once per proposal");
            copied.add(operation);
        }
        this.id = id.trim();
        this.summary = summary.trim();
        this.operations = Collections.unmodifiableList(copied);
    }
}
