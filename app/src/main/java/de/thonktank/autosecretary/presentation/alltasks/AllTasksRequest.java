package de.thonktank.autosecretary.presentation.alltasks;

import androidx.annotation.Nullable;

import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskStepId;

import java.util.Objects;

/** Confirmable host work carried in screen state instead of a consumable event. */
public final class AllTasksRequest {
    public enum Kind { ERROR, INFO, OPEN_EDITOR, CONFIRM_DELETE }

    public final String id;
    public final Kind kind;
    @Nullable public final String message;
    @Nullable public final TaskId taskId;
    @Nullable public final TaskStepId stepId;
    @Nullable public final String title;
    public final boolean addStep;

    private AllTasksRequest(String id, Kind kind, @Nullable String message,
                            @Nullable TaskId taskId, @Nullable TaskStepId stepId,
                            @Nullable String title, boolean addStep) {
        if (id == null || id.isEmpty() || kind == null)
            throw new IllegalArgumentException("Request identity and kind are required");
        this.id = id;
        this.kind = kind;
        this.message = message;
        this.taskId = taskId;
        this.stepId = stepId;
        this.title = title;
        this.addStep = addStep;
    }

    static AllTasksRequest message(String id, Kind kind, String message) {
        if (kind != Kind.ERROR && kind != Kind.INFO)
            throw new IllegalArgumentException("A message request needs a message kind");
        return new AllTasksRequest(id, kind, required(message), null, null, null, false);
    }

    static AllTasksRequest openEditor(String id, TaskId taskId,
                                      @Nullable TaskStepId stepId, boolean addStep) {
        return new AllTasksRequest(id, Kind.OPEN_EDITOR, null, required(taskId), stepId,
                null, addStep);
    }

    static AllTasksRequest confirmDelete(String id, TaskId taskId, String title) {
        return new AllTasksRequest(id, Kind.CONFIRM_DELETE, null, required(taskId), null,
                required(title), false);
    }

    static AllTasksRequest restore(String id, Kind kind, @Nullable String message,
                                   @Nullable String taskId, @Nullable String stepId,
                                   @Nullable String title, boolean addStep) {
        if (kind == Kind.ERROR || kind == Kind.INFO) return message(id, kind, message);
        TaskId restoredTask = TaskId.of(taskId);
        if (kind == Kind.OPEN_EDITOR) return openEditor(id, restoredTask,
                stepId == null ? null : TaskStepId.of(stepId), addStep);
        if (kind == Kind.CONFIRM_DELETE) return confirmDelete(id, restoredTask, title);
        throw new IllegalArgumentException("Unsupported request kind " + kind);
    }

    boolean sameWorkAs(AllTasksRequest other) {
        return other != null && kind == other.kind && Objects.equals(message, other.message)
                && Objects.equals(taskId, other.taskId) && Objects.equals(stepId, other.stepId)
                && Objects.equals(title, other.title) && addStep == other.addStep;
    }

    private static <T> T required(T value) {
        if (value == null) throw new IllegalArgumentException("Request value is required");
        return value;
    }
}
