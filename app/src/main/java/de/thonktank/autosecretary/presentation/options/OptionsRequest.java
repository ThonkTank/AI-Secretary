package de.thonktank.autosecretary.presentation.options;

import androidx.annotation.Nullable;

import de.thonktank.autosecretary.update.application.VerifiedUpdate;
import de.thonktank.autosecretary.update.domain.UpdateFailure;
import de.thonktank.autosecretary.update.domain.UpdateInfo;

import java.util.Objects;

/** Confirmable Android host work carried by the options screen state. */
public final class OptionsRequest {
    public enum Kind {
        REQUEST_CALENDAR_PERMISSION, OPEN_APP_SETTINGS, UPDATE_AVAILABLE,
        INSTALL_UPDATE, UPDATE_ERROR, OPEN_FLOW_SETUP, OPEN_FLOW_RUNS
    }

    public final String id;
    public final Kind kind;
    @Nullable public final UpdateInfo update;
    @Nullable public final VerifiedUpdate verified;
    @Nullable public final String message;
    @Nullable public final UpdateFailure.Kind errorKind;

    OptionsRequest(String id, Kind kind, @Nullable UpdateInfo update,
                   @Nullable VerifiedUpdate verified, @Nullable String message,
                   @Nullable UpdateFailure.Kind errorKind) {
        if (id == null || id.isEmpty() || kind == null)
            throw new IllegalArgumentException("Request identity and kind are required");
        this.id = id;
        this.kind = kind;
        this.update = update;
        this.verified = verified;
        this.message = message;
        this.errorKind = errorKind;
    }

    static OptionsRequest system(String id, Kind kind) {
        if (kind != Kind.REQUEST_CALENDAR_PERMISSION && kind != Kind.OPEN_APP_SETTINGS
                && kind != Kind.OPEN_FLOW_SETUP && kind != Kind.OPEN_FLOW_RUNS)
            throw new IllegalArgumentException("Unsupported system request");
        return new OptionsRequest(id, kind, null, null, null, null);
    }

    static OptionsRequest available(String id, UpdateInfo update) {
        return new OptionsRequest(id, Kind.UPDATE_AVAILABLE, required(update), null, null, null);
    }

    static OptionsRequest install(String id, VerifiedUpdate update) {
        return new OptionsRequest(id, Kind.INSTALL_UPDATE, required(update).info, update, null,
                null);
    }

    static OptionsRequest error(String id, UpdateFailure.Kind kind, String message) {
        return new OptionsRequest(id, Kind.UPDATE_ERROR, null, null, required(message),
                required(kind));
    }

    boolean sameWorkAs(OptionsRequest other) {
        return other != null && kind == other.kind
                && version(update) == version(other.update)
                && Objects.equals(path(verified), path(other.verified))
                && Objects.equals(message, other.message) && errorKind == other.errorKind;
    }

    private static long version(@Nullable UpdateInfo value) {
        return value == null ? -1L : value.versionCode;
    }
    @Nullable private static String path(@Nullable VerifiedUpdate value) {
        return value == null ? null : value.apk.getAbsolutePath();
    }
    private static <T> T required(T value) {
        if (value == null) throw new IllegalArgumentException("Request value is required");
        return value;
    }
}
