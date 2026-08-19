package de.thonktank.autosecretary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Small event fixture that keeps view tests coupled to the public dashboard contract. */
final class DashboardEventRecorder implements DashboardEventSink {
    private final List<DashboardEvent> events = new ArrayList<>();

    @Override public void emit(DashboardEvent event) {
        events.add(event);
    }

    List<DashboardEvent> events() {
        return Collections.unmodifiableList(events);
    }

    <T extends DashboardEvent> T last(Class<T> type) {
        for (int index = events.size() - 1; index >= 0; index--)
            if (type.isInstance(events.get(index))) return type.cast(events.get(index));
        return null;
    }
}
