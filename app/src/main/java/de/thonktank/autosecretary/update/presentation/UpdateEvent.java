package de.thonktank.autosecretary.update.presentation;

import de.thonktank.autosecretary.update.application.VerifiedUpdate;
import de.thonktank.autosecretary.update.domain.UpdateFailure;
import de.thonktank.autosecretary.update.domain.UpdateInfo;

import java.util.concurrent.atomic.AtomicBoolean;

public final class UpdateEvent {
    public enum Type { AVAILABLE, INSTALL, ERROR }

    public final Type type;
    public final UpdateInfo update;
    public final VerifiedUpdate verified;
    public final String message;
    public final UpdateFailure.Kind errorKind;
    private final AtomicBoolean consumed = new AtomicBoolean();

    private UpdateEvent(Type type, UpdateInfo update, VerifiedUpdate verified, String message,
                        UpdateFailure.Kind errorKind) {
        this.type = type;
        this.update = update;
        this.verified = verified;
        this.message = message;
        this.errorKind = errorKind;
    }

    static UpdateEvent available(UpdateInfo update) {
        return new UpdateEvent(Type.AVAILABLE, update, null, null, null);
    }

    static UpdateEvent install(VerifiedUpdate update) {
        return new UpdateEvent(Type.INSTALL, update.info, update, null, null);
    }

    static UpdateEvent error(UpdateFailure.Kind kind, String message) {
        return new UpdateEvent(Type.ERROR, null, null, message, kind);
    }

    public boolean consume() {
        return consumed.compareAndSet(false, true);
    }
}
