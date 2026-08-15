package de.thonktank.autosecretary;

import java.util.concurrent.atomic.AtomicBoolean;

public final class UiEvent {
    public enum Type {
        ERROR,
        CONFIRM_DELETE,
        CONFIRM_CLOSE,
        REQUEST_CALENDAR_PERMISSION,
        OPEN_APP_SETTINGS,
        OPEN_RELEASES
    }

    public final Type type;
    public final String message;
    public final String taskId;
    public final String taskTitle;
    public final int ringWeeks;
    private final AtomicBoolean consumed = new AtomicBoolean();

    private UiEvent(Type type, String message, String taskId, String taskTitle, int ringWeeks) {
        this.type = type;
        this.message = message;
        this.taskId = taskId;
        this.taskTitle = taskTitle;
        this.ringWeeks = ringWeeks;
    }

    public static UiEvent error(String message) {
        return new UiEvent(Type.ERROR, message, null, null, 0);
    }

    public static UiEvent action(Type type) {
        return new UiEvent(type, null, null, null, 0);
    }

    public static UiEvent confirmDelete(TaskSnapshot task) {
        return new UiEvent(Type.CONFIRM_DELETE, null, task.taskId, task.title, task.ringWeeks);
    }

    public static UiEvent confirmClose(String taskId, String title, int ringWeeks) {
        return new UiEvent(Type.CONFIRM_CLOSE, null, taskId, title, ringWeeks);
    }

    public boolean consume() {
        return consumed.compareAndSet(false, true);
    }
}
