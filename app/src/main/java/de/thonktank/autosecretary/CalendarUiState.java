package de.thonktank.autosecretary;

import de.thonktank.autosecretary.calendar.CalendarResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CalendarUiState {
    public enum Status {
        NOT_LOADED,
        SUCCESS,
        PERMISSION_MISSING,
        PROVIDER_UNAVAILABLE,
        ERROR
    }

    public final boolean loading;
    public final Status status;
    public final List<CalendarEventSnapshot> events;
    public final Throwable error;

    private CalendarUiState(boolean loading, Status status,
                            List<CalendarEventSnapshot> events, Throwable error) {
        this.loading = loading;
        this.status = status;
        this.events = Collections.unmodifiableList(new ArrayList<>(events));
        this.error = error;
    }

    public static CalendarUiState empty() {
        return new CalendarUiState(false, Status.NOT_LOADED, Collections.emptyList(), null);
    }

    public static CalendarUiState loading(CalendarUiState previous) {
        return new CalendarUiState(true, previous.status, previous.events, previous.error);
    }

    public static CalendarUiState from(CalendarResult result) {
        if (result instanceof CalendarResult.Success)
            return new CalendarUiState(false, Status.SUCCESS, result.events(), null);
        if (result instanceof CalendarResult.PermissionMissing)
            return new CalendarUiState(false, Status.PERMISSION_MISSING,
                    Collections.emptyList(), null);
        if (result instanceof CalendarResult.ProviderUnavailable)
            return new CalendarUiState(false, Status.PROVIDER_UNAVAILABLE,
                    Collections.emptyList(), null);
        return new CalendarUiState(false, Status.ERROR, Collections.emptyList(), result.error());
    }
}
