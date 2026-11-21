package de.thonktank.autosecretary;

/** Confirmable editor host work carried in screen state. */
public final class TaskEditorRequest {
    public final String id;
    public final String message;
    public final String flowTaskId;
    public final boolean notice;

    TaskEditorRequest(String id, String message) {
        this(id, message, null);
    }

    TaskEditorRequest(String id, String message, String flowTaskId) {
        this(id, message, flowTaskId, false);
    }

    static TaskEditorRequest notice(String id, String message) {
        return new TaskEditorRequest(id, message, null, true);
    }

    TaskEditorRequest(String id, String message, String flowTaskId, boolean notice) {
        if (id == null || id.isEmpty() || message == null || message.isEmpty())
            throw new IllegalArgumentException("Complete editor request is required");
        this.id = id;
        this.message = message;
        this.flowTaskId = flowTaskId;
        this.notice = notice;
    }

    boolean sameWorkAs(TaskEditorRequest other) {
        return other != null && notice == other.notice && message.equals(other.message)
                && java.util.Objects.equals(flowTaskId, other.flowTaskId);
    }
}
