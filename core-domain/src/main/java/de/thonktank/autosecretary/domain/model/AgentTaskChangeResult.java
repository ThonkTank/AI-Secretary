package de.thonktank.autosecretary.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Outcome of applying a confirmed agent proposal. */
public final class AgentTaskChangeResult {
    public enum Status { APPLIED, STALE, REJECTED }

    public final Status status;
    public final List<TaskId> createdTaskIds;
    public final String detail;

    private AgentTaskChangeResult(Status status, List<TaskId> createdTaskIds, String detail) {
        this.status = status;
        this.createdTaskIds = Collections.unmodifiableList(new ArrayList<>(createdTaskIds));
        this.detail = detail == null ? "" : detail;
    }

    public static AgentTaskChangeResult applied(List<TaskId> ids) {
        return new AgentTaskChangeResult(Status.APPLIED, ids, "");
    }

    public static AgentTaskChangeResult stale(String detail) {
        return new AgentTaskChangeResult(Status.STALE, Collections.emptyList(), detail);
    }

    public static AgentTaskChangeResult rejected(String detail) {
        return new AgentTaskChangeResult(Status.REJECTED, Collections.emptyList(), detail);
    }
}
