package com.autosecretary.ui.update;

import com.autosecretary.application.update.UpdateFailure;
import com.autosecretary.application.update.UpdateInfo;
import com.autosecretary.application.update.VerifiedUpdate;
import com.autosecretary.application.update.DownloadTicket;
import com.autosecretary.application.update.DownloadProgress;

/** Mutually exclusive states for the complete phone-update interaction. */
public sealed interface UpdateUiState permits UpdateUiState.Idle, UpdateUiState.Checking,
        UpdateUiState.Current, UpdateUiState.Available, UpdateUiState.Downloading,
        UpdateUiState.Verifying, UpdateUiState.Ready, UpdateUiState.Error {
    default boolean busy() {
        return this instanceof Checking || this instanceof Downloading
                || this instanceof Verifying;
    }
    default boolean checked() { return !(this instanceof Idle || this instanceof Checking); }
    default UpdateInfo available() {
        if (this instanceof Available value) return value.update();
        if (this instanceof Downloading value) return value.update();
        if (this instanceof Verifying value) return value.update();
        if (this instanceof Error value) return value.update();
        return null;
    }
    default VerifiedUpdate verified() {
        if (this instanceof Ready value) return value.update();
        if (this instanceof Error value) return value.verified();
        return null;
    }
    default String error() {
        return this instanceof Error value ? value.failure().detail() : null;
    }
    default boolean retryable() {
        return !(this instanceof Error value) || value.failure().retryable();
    }

    record Idle() implements UpdateUiState { }
    record Checking() implements UpdateUiState { }
    record Current() implements UpdateUiState { }
    record Available(UpdateInfo update) implements UpdateUiState {
        public Available { if (update == null) throw new IllegalArgumentException("Update fehlt"); }
    }
    record Downloading(
            UpdateInfo update,
            DownloadTicket ticket,
            DownloadProgress progress) implements UpdateUiState {
        public Downloading {
            if (update == null || ticket == null || progress == null) {
                throw new IllegalArgumentException("Downloadzustand fehlt");
            }
        }
    }
    record Verifying(UpdateInfo update, DownloadTicket ticket) implements UpdateUiState {
        public Verifying {
            if (update == null || ticket == null) {
                throw new IllegalArgumentException("Prüfzustand fehlt");
            }
        }
    }
    record Ready(VerifiedUpdate update) implements UpdateUiState {
        public Ready { if (update == null) throw new IllegalArgumentException("Update fehlt"); }
    }
    record Error(UpdateInfo update, VerifiedUpdate verified, UpdateFailure failure)
            implements UpdateUiState {
        public Error { if (failure == null) throw new IllegalArgumentException("Fehler fehlt"); }
    }

    static UpdateUiState initial() { return new Idle(); }
}
