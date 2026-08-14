package com.autosecretary.app;

import com.autosecretary.application.DashboardData;
import com.autosecretary.application.LocationPort;
import com.autosecretary.application.MoveWorkItemUseCase;
import com.autosecretary.application.TimeProvider;
import com.autosecretary.application.TodayTimeline;
import com.autosecretary.application.WorkItemRepository;
import com.autosecretary.widget.FocusWidgetProvider;
import com.autosecretary.widget.WidgetDependencies;

/** App-owned adapter that supplies port-level widget dependencies. */
final class AppWidgetDependencies implements WidgetDependencies {
    private final AppGraph graph;

    AppWidgetDependencies(AppGraph graph) { this.graph = graph; }

    @Override public DashboardData loadDashboard() throws Exception {
        return graph.executors().callDatabase(() -> graph.planFocus().execute(Integer.MAX_VALUE));
    }
    @Override public TodayTimeline today(DashboardData dashboard) {
        return graph.todayTimeline().execute(dashboard);
    }
    @Override public WorkItemRepository workItems() { return graph.workItems(); }
    @Override public MoveWorkItemUseCase moveWorkItem() { return graph.moveWorkItem(); }
    @Override public LocationPort location() { return graph.location(); }
    @Override public TimeProvider time() { return graph.clock(); }
    @Override public void executeDatabase(Runnable action) {
        graph.executors().database().execute(action);
    }
    @Override public void refreshWidgets() { FocusWidgetProvider.refreshAll(graph.context()); }
}
