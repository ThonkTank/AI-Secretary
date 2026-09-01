package de.thonktank.autosecretary;


import android.content.Context;

import de.thonktank.autosecretary.calendar.CalendarDataSource;
import de.thonktank.autosecretary.data.local.DatabaseFactory;
import de.thonktank.autosecretary.data.observable.AndroidMinuteTicker;
import de.thonktank.autosecretary.data.observable.CalendarInvalidationSource;
import de.thonktank.autosecretary.data.observable.ClockInvalidationSource;
import de.thonktank.autosecretary.data.observable.DatabaseInvalidationSource;
import de.thonktank.autosecretary.data.observable.PreferenceInvalidationSource;
import de.thonktank.autosecretary.data.preferences.UiPreferences;
import de.thonktank.autosecretary.domain.usecase.IdGenerator;
import de.thonktank.autosecretary.domain.usecase.CatalogUseCases;
import de.thonktank.autosecretary.domain.usecase.FlowUseCases;
import de.thonktank.autosecretary.domain.usecase.TodayUseCases;
import de.thonktank.autosecretary.domain.usecase.TrainingUseCases;
import de.thonktank.autosecretary.domain.usecase.UuidGenerator;
import de.thonktank.autosecretary.infrastructure.AppLogger;
import de.thonktank.autosecretary.presentation.DashboardPresenter;
import de.thonktank.autosecretary.presentation.DashboardUiMapper;
import de.thonktank.autosecretary.presentation.AndroidUiTextProvider;
import de.thonktank.autosecretary.presentation.UiTextProvider;
import de.thonktank.autosecretary.presentation.observable.PresentationInvalidationSource;
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
import de.thonktank.autosecretary.timer.AndroidTimerClock;
import de.thonktank.autosecretary.timer.AndroidTimerScheduler;
import de.thonktank.autosecretary.timer.RoomTimerSessionStore;
import de.thonktank.autosecretary.timer.TimerManager;
import de.thonktank.autosecretary.timer.TimerNotificationPublisher;

public final class AppContainer {
    public final AppDatabase database;
    public final DatabaseInvalidationSource databaseInvalidations;
    public final Clock clock;
    public final ZoneIdProvider zones;
    public final IdGenerator ids;
    public final AppLogger logger;
    public final CatalogUseCases catalog;
    public final TodayUseCases today;
    public final FlowUseCases flows;
    public final TrainingUseCases training;
    public final CalendarDataSource calendar;
    public final UiPreferences uiPreferences;
    public final CalendarInvalidationSource calendarInvalidations;
    public final PreferenceInvalidationSource preferenceInvalidations;
    public final ClockInvalidationSource clockInvalidations;
    public final PresentationInvalidationSource presentationInvalidations;
    public final DashboardPresenter dashboardPresenter;
    public final UiTextProvider texts;
    public final AppExecutors executors;
    public final WidgetUpdateCoordinator widgetUpdates;
    public final FlowWakeScheduler flowWakeScheduler;
    public final UpdateConfiguration updateConfiguration;
    public final UpdateRepository updates;
    public final UpdatePreferences updatePreferences;
    public final UpdateClock updateClock;
    public final UpdateExecutorFactory updateExecutors;
    public final UpdateInstaller updateInstaller;
    public final TimerManager timers;

    public AppContainer(Context context, Clock clock, ZoneIdProvider zones,
                        IdGenerator ids, AppLogger logger, DatabaseFactory databases) {
        Context app = context.getApplicationContext();
        this.clock = clock;
        this.zones = zones;
        this.ids = ids;
        this.logger = logger;
        this.database = databases.create(app);
        this.databaseInvalidations = new DatabaseInvalidationSource(database);
        this.uiPreferences = new UiPreferences(app, logger);
        ApplicationUseCaseComposition useCases = new ApplicationUseCaseComposition(database,
                clock, ids, uiPreferences);
        this.catalog = useCases.catalog;
        this.today = useCases.today;
        this.flows = useCases.flows;
        this.training = useCases.training;
        this.texts = new AndroidUiTextProvider(app);
        this.calendar = new CalendarRepository(app, clock, zones,
                uiPreferences::calendarPolicy, logger, texts);
        this.calendarInvalidations = new CalendarInvalidationSource(calendar);
        this.preferenceInvalidations = new PreferenceInvalidationSource(uiPreferences);
        this.clockInvalidations = new ClockInvalidationSource(clock, new AndroidMinuteTicker());
        this.presentationInvalidations = new PresentationInvalidationSource(databaseInvalidations,
                calendarInvalidations, preferenceInvalidations, clockInvalidations);
        this.flowWakeScheduler = new FlowWakeScheduler(app, flows.activateReadyFlows, logger);
        this.dashboardPresenter = new DashboardPresenter(clock, today.loadDashboard,
                today.materializeDue, new DashboardUiMapper(texts), today.applyComboDecay,
                today.settlePreviousPartialOccurrences, flows.activateReadyFlows,
                flowWakeScheduler::reschedule);
        this.executors = new AppExecutors();
        this.timers = new TimerManager(new RoomTimerSessionStore(database.timers()),
                new AndroidTimerScheduler(app), new TimerNotificationPublisher(app),
                new AndroidTimerClock(), executors.timerSerial, logger);
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
