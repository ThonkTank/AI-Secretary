package de.thonktank.autosecretary.presentation.today;

import androidx.annotation.Nullable;

import java.util.Objects;

/** Stable, acknowledgeable host work owned by the Today screen. */
public final class TodayRequest {
    public enum Kind {
        ERROR,
        INFO,
        TASK_MENU,
        CHOOSE_MOVE,
        CONFIRM_DELETE,
        CONFIRM_CLOSE,
        REQUEST_TIMER_PERMISSIONS
    }

    public final String id;
    public final Kind kind;
    @Nullable public final String message;
    @Nullable public final TaskActionTarget target;
    @Nullable public final String taskId;
    @Nullable public final String title;
    public final boolean routine;

    private TodayRequest(String id, Kind kind, @Nullable String message,
                         @Nullable TaskActionTarget target, @Nullable String taskId,
                         @Nullable String title, boolean routine) {
        if (id == null || id.trim().isEmpty() || kind == null)
            throw new IllegalArgumentException("Today request identity is required");
        if ((kind == Kind.ERROR || kind == Kind.INFO)
                && (message == null || message.trim().isEmpty()))
            throw new IllegalArgumentException("Today feedback text is required");
        if ((kind == Kind.TASK_MENU || kind == Kind.CHOOSE_MOVE
                || kind == Kind.CONFIRM_DELETE) && target == null)
            throw new IllegalArgumentException("Today task request target is required");
        if (kind == Kind.CONFIRM_CLOSE && (taskId == null || taskId.trim().isEmpty()
                || title == null || title.trim().isEmpty()))
            throw new IllegalArgumentException("Today close request target is required");
        this.id = id;
        this.kind = kind;
        this.message = message;
        this.target = target;
        this.taskId = target == null ? taskId : target.taskId;
        this.title = target == null ? title : target.title;
        this.routine = target == null ? routine : target.routine;
    }

    public static TodayRequest feedback(String id, Kind kind, String message) {
        if (kind != Kind.ERROR && kind != Kind.INFO)
            throw new IllegalArgumentException("Feedback request kind is required");
        return new TodayRequest(id, kind, message, null, null, null, false);
    }

    public static TodayRequest task(String id, Kind kind, TaskActionTarget target) {
        if (kind != Kind.TASK_MENU && kind != Kind.CHOOSE_MOVE
                && kind != Kind.CONFIRM_DELETE)
            throw new IllegalArgumentException("Task request kind is required");
        return new TodayRequest(id, kind, null, target, null, null, false);
    }

    public static TodayRequest close(String id, String taskId, String title) {
        return new TodayRequest(id, Kind.CONFIRM_CLOSE, null, null,
                taskId, title, false);
    }

    public static TodayRequest timerPermissions(String id) {
        return new TodayRequest(id, Kind.REQUEST_TIMER_PERMISSIONS, null, null,
                null, null, false);
    }

    public boolean sameWorkAs(TodayRequest other) {
        if (other == null || kind != other.kind) return false;
        if (taskId != null || other.taskId != null)
            return Objects.equals(taskId, other.taskId);
        return Objects.equals(message, other.message);
    }
}
