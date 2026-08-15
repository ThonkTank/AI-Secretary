package de.thonktank.autosecretary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CalendarUiState {
    public final boolean loading;
    public final List<CalendarEventSnapshot> events;

    public CalendarUiState(boolean loading, List<CalendarEventSnapshot> events) {
        this.loading = loading;
        this.events = Collections.unmodifiableList(new ArrayList<>(events));
    }

    public static CalendarUiState empty() {
        return new CalendarUiState(false, Collections.emptyList());
    }
}
