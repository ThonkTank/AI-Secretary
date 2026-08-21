package de.thonktank.autosecretary.calendar;

import de.thonktank.autosecretary.presentation.today.CalendarEventSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class CalendarResult {
    private CalendarResult() { }

    public List<CalendarEventSnapshot> events() {
        return Collections.emptyList();
    }

    public Throwable error() {
        return null;
    }

    public static final class Success extends CalendarResult {
        private final List<CalendarEventSnapshot> events;

        public Success(List<CalendarEventSnapshot> events) {
            this.events = Collections.unmodifiableList(new ArrayList<>(events));
        }

        @Override public List<CalendarEventSnapshot> events() { return events; }
    }

    public static final class PermissionMissing extends CalendarResult { }
    public static final class ProviderUnavailable extends CalendarResult { }

    public static final class Error extends CalendarResult {
        private final Throwable error;

        public Error(Throwable error) {
            this.error = error;
        }

        @Override public Throwable error() { return error; }
    }
}
