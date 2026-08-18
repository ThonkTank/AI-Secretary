package de.thonktank.autosecretary;

import java.util.concurrent.atomic.AtomicBoolean;

import de.thonktank.autosecretary.domain.model.RewardReceipt;

public final class UiEvent {
    public enum Type {
        ERROR,
        CONFIRM_DELETE,
        CONFIRM_CLOSE,
        REQUEST_CALENDAR_PERMISSION,
        OPEN_APP_SETTINGS,
        REWARD
    }

    public final Type type;
    public final String message;
    public final String taskId;
    public final String taskTitle;
    public final int rewardXp;
    public final RewardReceipt.Target rewardTarget;
    public final String rewardActionKey;
    private final AtomicBoolean consumed = new AtomicBoolean();

    private UiEvent(Type type, String message, String taskId, String taskTitle) {
        this(type, message, taskId, taskTitle, 0,
                RewardReceipt.Target.NONE, null);
    }

    private UiEvent(Type type, String message, String taskId, String taskTitle,
                    int rewardXp,
                    RewardReceipt.Target rewardTarget,
                    String rewardActionKey) {
        this.type = type;
        this.message = message;
        this.taskId = taskId;
        this.taskTitle = taskTitle;
        this.rewardXp = rewardXp;
        this.rewardTarget = rewardTarget;
        this.rewardActionKey = rewardActionKey;
    }

    public static UiEvent error(String message) {
        return new UiEvent(Type.ERROR, message, null, null);
    }

    public static UiEvent action(Type type) {
        return new UiEvent(type, null, null, null);
    }

    public static UiEvent confirmDelete(TaskSnapshot task) {
        return new UiEvent(Type.CONFIRM_DELETE, null, task.taskId, task.title);
    }

    public static UiEvent confirmClose(String taskId, String title) {
        return new UiEvent(Type.CONFIRM_CLOSE, null, taskId, title);
    }

    public static UiEvent reward(RewardReceipt result, String actionKey) {
        return new UiEvent(Type.REWARD, null, null, null, result.xp,
                result.target, actionKey);
    }

    public boolean consume() {
        return consumed.compareAndSet(false, true);
    }
}
