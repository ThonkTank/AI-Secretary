package de.thonktank.autosecretary;

/** Confirmable editor host work carried in screen state. */
public final class TaskEditorRequest {
    public final String id;
    public final String message;

    TaskEditorRequest(String id, String message) {
        if (id == null || id.isEmpty() || message == null || message.isEmpty())
            throw new IllegalArgumentException("Complete editor request is required");
        this.id = id;
        this.message = message;
    }

    boolean sameWorkAs(TaskEditorRequest other) {
        return other != null && message.equals(other.message);
    }
}
