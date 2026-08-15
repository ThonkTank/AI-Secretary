package de.thonktank.autosecretary;

import java.util.Objects;

public final class EditorUiState {
    public final boolean open;
    public final String taskId;

    private EditorUiState(boolean open, String taskId) {
        this.open = open;
        this.taskId = taskId;
    }

    public static EditorUiState closed() {
        return new EditorUiState(false, null);
    }

    public static EditorUiState create() {
        return new EditorUiState(true, null);
    }

    public static EditorUiState edit(String taskId) {
        return new EditorUiState(true, Objects.requireNonNull(taskId, "taskId"));
    }
}
