package de.thonktank.autosecretary;

import java.util.concurrent.atomic.AtomicBoolean;

public final class UiEvent {
    public enum Type {
        ERROR,
        INFO,
        CONFIRM_DELETE,
        CONFIRM_CLOSE,
        REQUEST_CALENDAR_PERMISSION,
        OPEN_APP_SETTINGS
    }

    public final Type type;
    public final String message;
    public final String taskId;
    public final String taskTitle;
    private final AtomicBoolean consumed = new AtomicBoolean();

    private UiEvent(Type type, String message, String taskId, String taskTitle) {
        this.type = type;
        this.message = message;
        this.taskId = taskId;
        this.taskTitle = taskTitle;
    }

    public static UiEvent error(String message) {
        return new UiEvent(Type.ERROR, message, null, null);
    }

    public static UiEvent info(String message) {
        return new UiEvent(Type.INFO, message, null, null);
    }

    public static UiEvent action(Type type) {
        return new UiEvent(type, null, null, null);
    }

    public static UiEvent confirmDelete(TaskSnapshot task) {
        return new UiEvent(Type.CONFIRM_DELETE, null, task.taskId, task.title);
    }

    public static UiEvent confirmDelete(String taskId, String title) {
        return new UiEvent(Type.CONFIRM_DELETE, null, taskId, title);
    }

    public static UiEvent confirmClose(String taskId, String title) {
        return new UiEvent(Type.CONFIRM_CLOSE, null, taskId, title);
    }

    public boolean consume() {
        return consumed.compareAndSet(false, true);
    }
}
