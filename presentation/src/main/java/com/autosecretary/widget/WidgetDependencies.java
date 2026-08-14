package com.autosecretary.widget;

import android.content.Context;

import com.autosecretary.application.DashboardData;
import com.autosecretary.application.LocationPort;
import com.autosecretary.application.MoveWorkItemUseCase;
import com.autosecretary.application.TimeProvider;
import com.autosecretary.application.TodayTimeline;
import com.autosecretary.application.WorkItemRepository;

/** Injected widget boundary; RemoteViews components never reach into AppGraph. */
public interface WidgetDependencies {
    DashboardData loadDashboard() throws Exception;
    TodayTimeline today(DashboardData dashboard);
    WorkItemRepository workItems();
    MoveWorkItemUseCase moveWorkItem();
    LocationPort location();
    TimeProvider time();
    void executeDatabase(Runnable action);
    void refreshWidgets();

    interface Provider {
        WidgetDependencies widgetDependencies();
    }

    static WidgetDependencies from(Context context) {
        Context application = context.getApplicationContext();
        if (!(application instanceof Provider provider)) {
            throw new IllegalStateException("Widget-Abhängigkeiten fehlen");
        }
        return provider.widgetDependencies();
    }
}
