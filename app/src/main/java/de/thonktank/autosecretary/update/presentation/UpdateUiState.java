package de.thonktank.autosecretary.update.presentation;

import de.thonktank.autosecretary.update.domain.UpdateFailure;
import de.thonktank.autosecretary.update.domain.UpdateInfo;

public final class UpdateUiState {
    public enum Status { IDLE, CHECKING, CURRENT, AVAILABLE, DOWNLOADING, READY, ERROR }

    public final Status status;
    public final UpdateInfo update;
    public final int progress;
    public final String message;
    public final UpdateFailure.Kind errorKind;

    private UpdateUiState(Status status, UpdateInfo update, int progress, String message,
                          UpdateFailure.Kind errorKind) {
        this.status = status;
        this.update = update;
        this.progress = progress;
        this.message = message;
        this.errorKind = errorKind;
    }

    public static UpdateUiState idle() {
        return new UpdateUiState(Status.IDLE, null, 0, null, null);
    }

    public static UpdateUiState checking() {
        return new UpdateUiState(Status.CHECKING, null, 0, null, null);
    }

    public static UpdateUiState current() {
        return new UpdateUiState(Status.CURRENT, null, 0, null, null);
    }

    public static UpdateUiState available(UpdateInfo update) {
        return new UpdateUiState(Status.AVAILABLE, requireUpdate(update), 0, null, null);
    }

    public static UpdateUiState downloading(UpdateInfo update, int progress) {
        int bounded = Math.max(0, Math.min(99, progress));
        return new UpdateUiState(Status.DOWNLOADING, requireUpdate(update), bounded, null, null);
    }

    public static UpdateUiState ready(UpdateInfo update) {
        return new UpdateUiState(Status.READY, requireUpdate(update), 100, null, null);
    }

    public static UpdateUiState error(UpdateFailure.Kind kind, String message) {
        if (kind == null || message == null || message.trim().isEmpty())
            throw new IllegalArgumentException("Typed update error and message are required");
        return new UpdateUiState(Status.ERROR, null, 0, message, kind);
    }

    private static UpdateInfo requireUpdate(UpdateInfo update) {
        if (update == null) throw new IllegalArgumentException("Update is required for this state");
        return update;
    }
}
