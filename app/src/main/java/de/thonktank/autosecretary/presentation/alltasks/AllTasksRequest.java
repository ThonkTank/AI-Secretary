package de.thonktank.autosecretary.presentation.alltasks;

import androidx.annotation.Nullable;

import de.thonktank.autosecretary.domain.model.TaskId;
import java.util.Objects;

/** Confirmable host work carried in screen state instead of a consumable event. */
public final class AllTasksRequest {
    public enum Kind { ERROR, INFO, CONFIRM_DELETE }

    public final String id;
    public final Kind kind;
    @Nullable public final String message;
    @Nullable public final TaskId taskId;
    @Nullable public final String title;

    private AllTasksRequest(String id, Kind kind, @Nullable String message,
                            @Nullable TaskId taskId, @Nullable String title) {
        if (id == null || id.isEmpty() || kind == null)
            throw new IllegalArgumentException("Request identity and kind are required");
        this.id = id;
        this.kind = kind;
        this.message = message;
        this.taskId = taskId;
        this.title = title;
    }

    static AllTasksRequest message(String id, Kind kind, String message) {
        if (kind != Kind.ERROR && kind != Kind.INFO)
            throw new IllegalArgumentException("A message request needs a message kind");
        return new AllTasksRequest(id, kind, required(message), null, null);
    }

    static AllTasksRequest confirmDelete(String id, TaskId taskId, String title) {
        return new AllTasksRequest(id, Kind.CONFIRM_DELETE, null, required(taskId),
                required(title));
    }

    static AllTasksRequest restore(String id, Kind kind, @Nullable String message,
                                   @Nullable String taskId, @Nullable String title) {
        if (kind == Kind.ERROR || kind == Kind.INFO) return message(id, kind, message);
        TaskId restoredTask = TaskId.of(taskId);
        if (kind == Kind.CONFIRM_DELETE) return confirmDelete(id, restoredTask, title);
        throw new IllegalArgumentException("Unsupported request kind " + kind);
    }

    boolean sameWorkAs(AllTasksRequest other) {
        return other != null && kind == other.kind && Objects.equals(message, other.message)
                && Objects.equals(taskId, other.taskId) && Objects.equals(title, other.title);
    }

    private static <T> T required(T value) {
        if (value == null) throw new IllegalArgumentException("Request value is required");
        return value;
    }
}
