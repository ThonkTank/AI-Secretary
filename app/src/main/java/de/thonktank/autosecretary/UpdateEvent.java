package de.thonktank.autosecretary;

import de.thonktank.autosecretary.update.UpdateInfo;
import de.thonktank.autosecretary.update.VerifiedUpdate;

import java.util.concurrent.atomic.AtomicBoolean;

public final class UpdateEvent {
    public enum Type { AVAILABLE, INSTALL, ERROR }

    public final Type type;
    public final UpdateInfo update;
    public final VerifiedUpdate verified;
    public final String message;
    private final AtomicBoolean consumed = new AtomicBoolean();

    private UpdateEvent(Type type, UpdateInfo update, VerifiedUpdate verified, String message) {
        this.type = type;
        this.update = update;
        this.verified = verified;
        this.message = message;
    }

    static UpdateEvent available(UpdateInfo update) {
        return new UpdateEvent(Type.AVAILABLE, update, null, null);
    }

    static UpdateEvent install(VerifiedUpdate update) {
        return new UpdateEvent(Type.INSTALL, update.info, update, null);
    }

    static UpdateEvent error(String message) {
        return new UpdateEvent(Type.ERROR, null, null, message);
    }

    public boolean consume() {
        return consumed.compareAndSet(false, true);
    }
}
