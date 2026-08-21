package de.thonktank.autosecretary;


import android.content.Context;

import de.thonktank.autosecretary.calendar.CalendarDataSource;
import de.thonktank.autosecretary.data.local.DatabaseFactory;
import de.thonktank.autosecretary.data.local.RoomTaskRepository;
import de.thonktank.autosecretary.data.preferences.UiPreferences;
import de.thonktank.autosecretary.domain.repository.ApplicationTaskRepository;
import de.thonktank.autosecretary.domain.usecase.IdGenerator;
import de.thonktank.autosecretary.domain.usecase.TaskUseCases;
import de.thonktank.autosecretary.domain.usecase.UuidGenerator;
import de.thonktank.autosecretary.infrastructure.AppLogger;
import de.thonktank.autosecretary.presentation.DashboardPresenter;
import de.thonktank.autosecretary.presentation.DashboardUiMapper;
import de.thonktank.autosecretary.presentation.AndroidUiTextProvider;
import de.thonktank.autosecretary.presentation.UiTextProvider;
import de.thonktank.autosecretary.update.application.UpdateRepository;
import de.thonktank.autosecretary.update.application.UpdateClock;
import de.thonktank.autosecretary.update.application.UpdateConfiguration;
import de.thonktank.autosecretary.update.application.UpdateExecutorFactory;
import de.thonktank.autosecretary.update.application.UpdatePreferences;
import de.thonktank.autosecretary.update.infrastructure.DisabledUpdateRepository;
import de.thonktank.autosecretary.update.infrastructure.GitHubUpdateRepository;
import de.thonktank.autosecretary.update.infrastructure.SerialUpdateExecutor;
import de.thonktank.autosecretary.update.infrastructure.SharedUpdatePreferences;
import de.thonktank.autosecretary.update.infrastructure.UpdateInstaller;
import de.thonktank.autosecretary.update.infrastructure.UrlConnectionHttpTransport;
import de.thonktank.autosecretary.update.domain.UpdateTrustPolicy;

public final class AppContainer {
    public final AppDatabase database;
    public final Clock clock;
    public final ZoneIdProvider zones;
    public final IdGenerator ids;
    public final AppLogger logger;
    public final ApplicationTaskRepository taskRepository;
    public final TaskUseCases tasks;
    public final CalendarDataSource calendar;
    public final UiPreferences uiPreferences;
    public final DashboardPresenter dashboardPresenter;
    public final UiTextProvider texts;
    public final AppExecutors executors;
    public final WidgetUpdateCoordinator widgetUpdates;
    public final UpdateConfiguration updateConfiguration;
    public final UpdateRepository updates;
    public final UpdatePreferences updatePreferences;
    public final UpdateClock updateClock;
    public final UpdateExecutorFactory updateExecutors;
    public final UpdateInstaller updateInstaller;

    public AppContainer(Context context, Clock clock, ZoneIdProvider zones,
                        IdGenerator ids, AppLogger logger, DatabaseFactory databases) {
        Context app = context.getApplicationContext();
        this.clock = clock;
        this.zones = zones;
        this.ids = ids;
        this.logger = logger;
        this.database = databases.create(app);
        this.taskRepository = new RoomTaskRepository(database);
        this.tasks = new TaskUseCases(taskRepository, clock, ids);
        this.uiPreferences = new UiPreferences(app, logger);
        this.texts = new AndroidUiTextProvider(app);
        this.calendar = new CalendarRepository(app, clock, zones,
                uiPreferences::calendarPolicy, logger, texts);
        this.dashboardPresenter = new DashboardPresenter(clock, tasks.loadDashboard,
                tasks.materializeDue, new DashboardUiMapper(texts), tasks.applyComboDecay);
        this.executors = new AppExecutors();
        this.widgetUpdates = WidgetUpdateCoordinator.create(app, this, executors.widgetSerial);
        this.updateConfiguration = BuildConfig.DEBUG
                ? UpdateConfiguration.development(BuildConfig.UPDATE_REPOSITORY_OWNER,
                        BuildConfig.UPDATE_REPOSITORY_NAME, BuildConfig.UPDATE_METADATA_ASSET,
                        BuildConfig.UPDATE_APK_ASSET, BuildConfig.UPDATE_TAG_PREFIX)
                : UpdateConfiguration.production(BuildConfig.UPDATE_REPOSITORY_OWNER,
                        BuildConfig.UPDATE_REPOSITORY_NAME, BuildConfig.UPDATE_METADATA_ASSET,
                        BuildConfig.UPDATE_APK_ASSET, BuildConfig.UPDATE_TAG_PREFIX);
        this.updatePreferences = new SharedUpdatePreferences(app, logger);
        this.updateClock = System::currentTimeMillis;
        this.updateExecutors = SerialUpdateExecutor::new;
        UpdateTrustPolicy updateTrust = UpdateTrustPolicy.github();
        this.updates = updateConfiguration.remoteChecksEnabled
                ? new GitHubUpdateRepository(app, updateConfiguration.repositoryOwner,
                        updateConfiguration.repositoryName, updateConfiguration.metadataAsset,
                        updateConfiguration.apkAsset, updateConfiguration.tagPrefix,
                        new UrlConnectionHttpTransport(updateTrust), updateTrust)
                : new DisabledUpdateRepository();
        this.updateInstaller = new UpdateInstaller();
    }

    public static AppContainer create(Context context, AppLogger logger) {
        ZoneIdProvider zones = new SystemZoneIdProvider();
        return new AppContainer(context, new SystemClock(zones), zones,
                new UuidGenerator(), logger, new DatabaseFactory());
    }
}
