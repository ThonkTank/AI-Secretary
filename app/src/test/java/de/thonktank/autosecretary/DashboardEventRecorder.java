package de.thonktank.autosecretary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Small event fixture that keeps view tests coupled to the public dashboard contract. */
final class DashboardEventRecorder implements DashboardEventSink,
        de.thonktank.autosecretary.presentation.today.TodayActionSink {
    private final List<DashboardEvent> events = new ArrayList<>();
    private final List<de.thonktank.autosecretary.presentation.today.TodayAction> todayActions =
            new ArrayList<>();

    @Override public void emit(DashboardEvent event) {
        events.add(event);
    }

    @Override public void emit(
            de.thonktank.autosecretary.presentation.today.TodayAction action) {
        todayActions.add(action);
    }

    List<DashboardEvent> events() {
        return Collections.unmodifiableList(events);
    }

    <T extends DashboardEvent> T last(Class<T> type) {
        for (int index = events.size() - 1; index >= 0; index--)
            if (type.isInstance(events.get(index))) return type.cast(events.get(index));
        return null;
    }

    List<de.thonktank.autosecretary.presentation.today.TodayAction> todayActions() {
        if (!todayActions.isEmpty()) return Collections.unmodifiableList(todayActions);
        List<de.thonktank.autosecretary.presentation.today.TodayAction> wrapped =
                new ArrayList<>();
        for (DashboardEvent event : events)
            if (event instanceof DashboardEvent.Today)
                wrapped.add(((DashboardEvent.Today) event).action);
        return Collections.unmodifiableList(wrapped);
    }

    de.thonktank.autosecretary.presentation.today.TodayAction lastToday(
            de.thonktank.autosecretary.presentation.today.TodayAction.Kind kind) {
        List<de.thonktank.autosecretary.presentation.today.TodayAction> values = todayActions();
        for (int index = values.size() - 1; index >= 0; index--)
            if (values.get(index).kind == kind) return values.get(index);
        return null;
    }
}
