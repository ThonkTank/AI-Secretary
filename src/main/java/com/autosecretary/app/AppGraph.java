package com.autosecretary.app;

import android.content.Context;

import com.autosecretary.ai.OnDeviceBulkEditor;
import com.autosecretary.application.ai.AiProposalGateway;
import com.autosecretary.application.ai.AiConsentPort;
import com.autosecretary.application.MoveWorkItemUseCase;
import com.autosecretary.application.PlanFocusUseCase;
import com.autosecretary.application.WorkItemRepository;
import com.autosecretary.application.PlanningSettingsRepository;
import com.autosecretary.application.ResolveMigrationCandidateUseCase;
import com.autosecretary.application.WorkItemCommands;
import com.autosecretary.application.PlanningSettingsUseCase;
import com.autosecretary.data.FocusDatabase;
import com.autosecretary.data.FocusDatabaseFactory;
import com.autosecretary.data.RoomWorkItemRepository;
import com.autosecretary.data.LegacyDatabaseBackup;
import com.autosecretary.domain.FocusPlanner;
import com.autosecretary.platform.DeviceCalendarGateway;
import com.autosecretary.platform.PreferencesPlanningSettingsRepository;
import com.autosecretary.platform.SystemAppClock;
import com.autosecretary.platform.AndroidLocationGateway;
import com.autosecretary.platform.PreferencesAiConsentGateway;
import com.autosecretary.application.LocationPort;
import com.autosecretary.platform.update.GitHubReleaseUpdateGateway;
import com.autosecretary.platform.update.UpdateInstaller;
import com.autosecretary.application.update.UpdateGateway;
import com.autosecretary.widget.FocusWidgetProvider;

/** Manual composition root. No inner package imports concrete outer adapters. */
public final class AppGraph {
    private final Context context;
    private final AppExecutors executors;
    private final FocusDatabase database;
    private final WorkItemRepository workItems;
    private final PlanFocusUseCase planFocus;
    private final PlanningSettingsRepository planningSettings;
    private final PlanningSettingsUseCase planningSettingsUseCase;
    private final MoveWorkItemUseCase moveWorkItem;
    private final ResolveMigrationCandidateUseCase resolveMigrationCandidate;
    private final WorkItemCommands workItemCommands;
    private final AiProposalGateway bulkEditor;
    private final AiConsentPort aiConsent;
    private final UpdateGateway updateGateway;
    private final UpdateInstaller updateInstaller;
    private final LocationPort location;
    private final SystemAppClock clock;

    public AppGraph(Context context, AppExecutors executors) {
        Context app = context.getApplicationContext();
        this.context = app;
        this.executors = executors;
        database = FocusDatabaseFactory.open(app, executors.database());
        workItems = new RoomWorkItemRepository(database);
        planningSettings = new PreferencesPlanningSettingsRepository(app);
        planningSettingsUseCase = new PlanningSettingsUseCase(planningSettings);
        clock = new SystemAppClock();
        planFocus = new PlanFocusUseCase(workItems, new DeviceCalendarGateway(app),
                planningSettings, clock,
                new FocusPlanner());
        moveWorkItem = new MoveWorkItemUseCase(workItems, clock);
        resolveMigrationCandidate = new ResolveMigrationCandidateUseCase(workItems, clock);
        workItemCommands = new WorkItemCommands(workItems, clock);
        bulkEditor = new OnDeviceBulkEditor(app, executors.io(), executors.ai(), clock);
        aiConsent = new PreferencesAiConsentGateway(app);
        updateGateway = new GitHubReleaseUpdateGateway(app);
        updateInstaller = new UpdateInstaller();
        location = new AndroidLocationGateway(app);
    }

    public AppExecutors executors() { return executors; }
    public FocusDatabase database() { return database; }
    public WorkItemRepository workItems() { return workItems; }
    public PlanFocusUseCase planFocus() { return planFocus; }
    public PlanningSettingsRepository planningSettings() { return planningSettings; }
    public PlanningSettingsUseCase planningSettingsUseCase() { return planningSettingsUseCase; }
    public MoveWorkItemUseCase moveWorkItem() { return moveWorkItem; }
    public ResolveMigrationCandidateUseCase resolveMigrationCandidate() {
        return resolveMigrationCandidate;
    }
    public WorkItemCommands workItemCommands() { return workItemCommands; }
    public AiProposalGateway bulkEditor() { return bulkEditor; }
    public AiConsentPort aiConsent() { return aiConsent; }
    public UpdateGateway updateGateway() { return updateGateway; }
    public UpdateInstaller updateInstaller() { return updateInstaller; }
    public LocationPort location() { return location; }
    public SystemAppClock clock() { return clock; }
    public java.io.File migrationBackupArchive() {
        return LegacyDatabaseBackup.latestArchive(context);
    }
    public void refreshWidgets() { FocusWidgetProvider.refreshAll(context); }
    void close() { database.close(); }
}
