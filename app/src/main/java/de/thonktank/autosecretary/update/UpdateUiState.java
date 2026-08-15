package de.thonktank.autosecretary.update;

public final class UpdateUiState {
    public enum Status { IDLE, CHECKING, CURRENT, AVAILABLE, DOWNLOADING, READY, ERROR }

    public final Status status;
    public final UpdateInfo update;
    public final int progress;
    public final String message;

    private UpdateUiState(Status status, UpdateInfo update, int progress, String message) {
        this.status = status;
        this.update = update;
        this.progress = progress;
        this.message = message;
    }

    public static UpdateUiState idle() {
        return new UpdateUiState(Status.IDLE, null, 0, null);
    }

    public static UpdateUiState checking() {
        return new UpdateUiState(Status.CHECKING, null, 0, null);
    }

    public static UpdateUiState current() {
        return new UpdateUiState(Status.CURRENT, null, 0, null);
    }

    public static UpdateUiState available(UpdateInfo update) {
        return new UpdateUiState(Status.AVAILABLE, update, 0, null);
    }

    public static UpdateUiState downloading(UpdateInfo update, int progress) {
        return new UpdateUiState(Status.DOWNLOADING, update, progress, null);
    }

    public static UpdateUiState ready(UpdateInfo update) {
        return new UpdateUiState(Status.READY, update, 100, null);
    }

    public static UpdateUiState error(String message) {
        return new UpdateUiState(Status.ERROR, null, 0, message);
    }
}
