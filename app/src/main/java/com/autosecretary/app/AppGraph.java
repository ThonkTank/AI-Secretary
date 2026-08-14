package com.autosecretary.app;

import android.content.Context;

import com.autosecretary.BuildConfig;

import com.autosecretary.ai.OnDeviceBulkEditor;
import com.autosecretary.application.ai.AiProposalGateway;
import com.autosecretary.application.ai.AiConsentPort;
import com.autosecretary.application.MoveWorkItemUseCase;
import com.autosecretary.application.PlanFocusUseCase;
import com.autosecretary.application.WorkItemRepository;
import com.autosecretary.application.PlanningSettingsRepository;
import com.autosecretary.data.FocusDatabase;
import com.autosecretary.data.FocusDatabaseFactory;
import com.autosecretary.data.RoomWorkItemRepository;
import com.autosecretary.domain.FocusPlanner;
import com.autosecretary.platform.DeviceCalendarGateway;
import com.autosecretary.platform.PreferencesPlanningSettingsRepository;
import com.autosecretary.platform.SystemTimeProvider;
import com.autosecretary.platform.AndroidLocationGateway;
import com.autosecretary.platform.PreferencesAiConsentGateway;
import com.autosecretary.application.LocationPort;
import com.autosecretary.application.TimeProvider;
import com.autosecretary.platform.update.AndroidUpdateRepository;
import com.autosecretary.application.update.UpdateRepository;
import com.autosecretary.platform.update.UpdateInstaller;
import com.autosecretary.widget.FocusWidgetProvider;

/** Manual composition root. No inner package imports concrete outer adapters. */
public final class AppGraph {
    private final Context context;
    private final AppExecutors executors;
    private final FocusDatabase database;
    private final WorkItemRepository workItems;
    private final PlanFocusUseCase planFocus;
    private final PlanningSettingsRepository planningSettings;
    private final MoveWorkItemUseCase moveWorkItem;
    private final AiProposalGateway bulkEditor;
    private final AiConsentPort aiConsent;
    private final UpdateRepository updates;
    private final UpdateInstaller updateInstaller;
    private final LocationPort location;
    private final TimeProvider clock;

    public AppGraph(Context context, AppExecutors executors) {
        Context app = context.getApplicationContext();
        this.context = app;
        this.executors = executors;
        database = FocusDatabaseFactory.open(app, executors.database());
        workItems = new RoomWorkItemRepository(database);
        planningSettings = new PreferencesPlanningSettingsRepository(app);
        clock = new SystemTimeProvider();
        planFocus = new PlanFocusUseCase(workItems, new DeviceCalendarGateway(app, clock),
                planningSettings, clock,
                new FocusPlanner());
        moveWorkItem = new MoveWorkItemUseCase(workItems, clock);
        bulkEditor = new OnDeviceBulkEditor(app, executors.io(), executors.ai(), clock);
        aiConsent = new PreferencesAiConsentGateway(app);
        updates = new AndroidUpdateRepository(app, "ThonkTank", "AI-Secretary",
                BuildConfig.APPLICATION_ID, BuildConfig.VERSION_NAME);
        updates.cleanup(BuildConfig.VERSION_CODE);
        updateInstaller = new UpdateInstaller();
        location = new AndroidLocationGateway(app);
    }

    public AppExecutors executors() { return executors; }
    public FocusDatabase database() { return database; }
    public WorkItemRepository workItems() { return workItems; }
    public PlanFocusUseCase planFocus() { return planFocus; }
    public PlanningSettingsRepository planningSettings() { return planningSettings; }
    public MoveWorkItemUseCase moveWorkItem() { return moveWorkItem; }
    public AiProposalGateway bulkEditor() { return bulkEditor; }
    public AiConsentPort aiConsent() { return aiConsent; }
    public UpdateRepository updates() { return updates; }
    public UpdateInstaller updateInstaller() { return updateInstaller; }
    public LocationPort location() { return location; }
    public TimeProvider clock() { return clock; }
    public void refreshWidgets() { FocusWidgetProvider.refreshAll(context); }
    void close() { database.close(); }
}
