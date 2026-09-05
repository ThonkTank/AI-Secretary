package de.thonktank.autosecretary;

import androidx.annotation.Nullable;

/** Closed input boundary for every task-editor interaction. */
public abstract class TaskEditorAction {
    private TaskEditorAction() { }

    public static final class Open extends TaskEditorAction {
        @Nullable public final String taskId;
        @Nullable public final String stepId;
        public final boolean addStep;
        private Open(@Nullable String taskId, @Nullable String stepId, boolean addStep) {
            this.taskId = taskId;
            this.stepId = stepId;
            this.addStep = addStep;
        }
    }
    public static final class DraftChanged extends TaskEditorAction {
        public final EditorUiState draft;
        private DraftChanged(EditorUiState draft) { this.draft = required(draft); }
    }
    public static final class Save extends TaskEditorAction {
        public final EditorUiState draft;
        private Save(EditorUiState draft) { this.draft = required(draft); }
    }
    public static final class Delete extends TaskEditorAction {
        public final String taskId;
        private Delete(String taskId) { this.taskId = requiredText(taskId); }
    }
    public static final class Dismiss extends TaskEditorAction {
        private Dismiss() { }
    }
    public static final class RequestAcknowledged extends TaskEditorAction {
        public final String requestId;
        private RequestAcknowledged(String requestId) {
            this.requestId = requiredText(requestId);
        }
    }
    public static final class UndoTrainingAdjustment extends TaskEditorAction {
        public final String stepId;
        private UndoTrainingAdjustment(String stepId) {
            this.stepId = requiredText(stepId);
        }
    }

    public static TaskEditorAction openNew() { return new Open(null, null, false); }
    public static TaskEditorAction open(String taskId) {
        return new Open(requiredText(taskId), null, false);
    }
    public static TaskEditorAction open(String taskId, @Nullable String stepId, boolean addStep) {
        return new Open(requiredText(taskId), stepId, addStep);
    }
    public static TaskEditorAction draftChanged(EditorUiState draft) {
        return new DraftChanged(draft);
    }
    public static TaskEditorAction save(EditorUiState draft) { return new Save(draft); }
    public static TaskEditorAction delete(String taskId) { return new Delete(taskId); }
    public static TaskEditorAction dismiss() { return new Dismiss(); }
    public static TaskEditorAction acknowledgeRequest(String requestId) {
        return new RequestAcknowledged(requestId);
    }
    public static TaskEditorAction undoTrainingAdjustment(String stepId) {
        return new UndoTrainingAdjustment(stepId);
    }

    private static String requiredText(String value) {
        if (value == null || value.isEmpty()) throw new IllegalArgumentException("Text is required");
        return value;
    }

    private static <T> T required(T value) {
        if (value == null) throw new IllegalArgumentException("Action value is required");
        return value;
    }
}
