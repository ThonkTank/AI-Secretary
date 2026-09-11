package de.thonktank.autosecretary;

/** Confirmable editor host work carried in screen state. */
public final class TaskEditorRequest {
    public final String id;
    public final String message;
    public final String flowTaskId;

    TaskEditorRequest(String id, String message) {
        this(id, message, null);
    }

    TaskEditorRequest(String id, String message, String flowTaskId) {
        if (id == null || id.isEmpty() || message == null || message.isEmpty())
            throw new IllegalArgumentException("Complete editor request is required");
        this.id = id;
        this.message = message;
        this.flowTaskId = flowTaskId;
    }

    boolean sameWorkAs(TaskEditorRequest other) {
        return other != null && message.equals(other.message) && java.util.Objects.equals(flowTaskId, other.flowTaskId);
    }
}
