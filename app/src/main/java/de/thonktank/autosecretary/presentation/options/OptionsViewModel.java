package de.thonktank.autosecretary.presentation.options;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.SavedStateHandleSupport;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.viewmodel.CreationExtras;

import de.thonktank.autosecretary.AppContainer;
import de.thonktank.autosecretary.CalendarPermissionStatus;
import de.thonktank.autosecretary.CalendarUiState;
import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.DayPalette;
import de.thonktank.autosecretary.R;
import de.thonktank.autosecretary.calendar.CalendarDataSource;
import de.thonktank.autosecretary.calendar.CalendarResult;
import de.thonktank.autosecretary.data.preferences.DisplayPreferences;
import de.thonktank.autosecretary.data.preferences.UiPreferences;
import de.thonktank.autosecretary.infrastructure.AppLogger;
import de.thonktank.autosecretary.presentation.UiTextProvider;
import de.thonktank.autosecretary.presentation.observable.LatestReadPipeline;
import de.thonktank.autosecretary.presentation.observable.OptionsInvalidationRouting;
import de.thonktank.autosecretary.presentation.observable.PresentationInvalidation;
import de.thonktank.autosecretary.presentation.observable.PresentationInvalidationSource;
import de.thonktank.autosecretary.update.application.UpdateClock;
import de.thonktank.autosecretary.update.application.UpdateErrorReporter;
import de.thonktank.autosecretary.update.application.UpdateExecutor;
import de.thonktank.autosecretary.update.application.UpdateExecutorFactory;
import de.thonktank.autosecretary.update.application.UpdatePreferences;
import de.thonktank.autosecretary.update.application.UpdateRepository;
import de.thonktank.autosecretary.update.application.VerifiedUpdate;
import de.thonktank.autosecretary.update.domain.UpdateCheckResult;
import de.thonktank.autosecretary.update.domain.UpdateFailure;
import de.thonktank.autosecretary.update.domain.UpdateInfo;
import de.thonktank.autosecretary.update.presentation.UpdateUiState;

import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.StateFlow;
import kotlinx.coroutines.flow.StateFlowKt;

/** Sole state and action owner for options, permission and updater presentation. */
public final class OptionsViewModel extends ViewModel {
    private static final String SAVED_REQUESTS = "options_requests";
    private static final String SAVED_REQUEST_SEQUENCE = "options_request_sequence";

    private final UiPreferences preferences;
    private final CalendarDataSource calendar;
    private final Clock clock;
    private final AppLogger logger;
    private final UiTextProvider texts;
    private final UpdateRepository updateRepository;
    private final UpdatePreferences updatePreferences;
    private final UpdateErrorReporter updateErrors;
    private final UpdateClock updateClock;
    private final boolean automaticChecks;
    private final Runnable calendarInvalidated;
    private final SavedStateHandle savedState;
    private final ExecutorService worker;
    private final UpdateExecutor updateWorker;
    private final LatestReadPipeline<PresentationInvalidation, Appearance> appearanceReads;
    private final LatestReadPipeline<PresentationInvalidation, CalendarResult> calendarReads;
    private final OptionsRequestSavedStateAdapter requestState =
            new OptionsRequestSavedStateAdapter();
    private final MutableStateFlow<OptionsScreenState> state;
    private final Object lock = new Object();
    private final Object actionLock = new Object();
    private OptionsScreenState current;
    private VerifiedUpdate verified;
    private long requestSequence;
    private long workflowGeneration;
    private boolean cleared;

    public OptionsViewModel(UiPreferences preferences, CalendarDataSource calendar, Clock clock,
                     AppLogger logger, UiTextProvider texts,
                     UpdateRepository updateRepository, UpdatePreferences updatePreferences,
                     UpdateErrorReporter updateErrors, UpdateClock updateClock,
                     boolean automaticChecks, Runnable calendarInvalidated,
                     SavedStateHandle savedState, ExecutorService worker,
                     UpdateExecutor updateWorker, PresentationInvalidationSource invalidations,
                     @Nullable Executor collectionExecutor) {
        this.preferences = required(preferences);
        this.calendar = required(calendar);
        this.clock = required(clock);
        this.logger = required(logger);
        this.texts = required(texts);
        this.updateRepository = required(updateRepository);
        this.updatePreferences = required(updatePreferences);
        this.updateErrors = required(updateErrors);
        this.updateClock = required(updateClock);
        this.automaticChecks = automaticChecks;
        this.calendarInvalidated = required(calendarInvalidated);
        this.savedState = required(savedState);
        this.worker = required(worker);
        this.updateWorker = required(updateWorker);

        DisplayPreferences display = preferences.displayPreferences();
        List<OptionsRequest> restored = requestState.decode(savedState.get(SAVED_REQUESTS));
        Long sequence = savedState.get(SAVED_REQUEST_SEQUENCE);
        requestSequence = sequence == null ? 0L : sequence;
        UpdateUiState update = UpdateUiState.idle();
        for (OptionsRequest request : restored) {
            requestSequence = Math.max(requestSequence, sequenceOf(request.id));
            if (request.kind == OptionsRequest.Kind.INSTALL_UPDATE) {
                verified = request.verified;
                update = UpdateUiState.ready(request.update);
            } else if (request.kind == OptionsRequest.Kind.UPDATE_AVAILABLE) {
                update = UpdateUiState.available(request.update);
            } else if (request.kind == OptionsRequest.Kind.UPDATE_ERROR) {
                update = UpdateUiState.error(request.errorKind, request.message);
            }
        }
        current = new OptionsScreenState(palette(display, clock.time()), display.themeMode,
                display.focusStepLimit, display.restTimerDefaultSeconds,
                CalendarPermissionStatus.UNKNOWN,
                CalendarUiState.empty(), update, restored);
        state = StateFlowKt.MutableStateFlow(current);

        OptionsInvalidationRouting routing = new OptionsInvalidationRouting(invalidations);
        if (collectionExecutor == null) {
            appearanceReads = LatestReadPipeline.reading(routing.getAppearanceChanges(), worker,
                    this::loadAppearance, this::publishAppearance, this::appearanceReadFailed);
            calendarReads = LatestReadPipeline.reading(routing.getCalendarChanges(), worker,
                    ignored -> calendar.loadToday(), this::publishCalendar,
                    this::calendarReadFailed);
        } else {
            appearanceReads = LatestReadPipeline.reading(routing.getAppearanceChanges(), worker,
                    collectionExecutor, this::loadAppearance, this::publishAppearance,
                    this::appearanceReadFailed);
            calendarReads = LatestReadPipeline.reading(routing.getCalendarChanges(), worker,
                    collectionExecutor, ignored -> calendar.loadToday(), this::publishCalendar,
                    this::calendarReadFailed);
        }
    }

    public StateFlow<OptionsScreenState> state() { return state; }

    /** The only options input; reduction is serial even when host and worker race. */
    public void dispatch(OptionsAction action) {
        if (action == null) throw new IllegalArgumentException("Action is required");
        synchronized (actionLock) { reduce(action); }
    }

    private void reduce(OptionsAction action) {
        if (action instanceof OptionsAction.ThemeSelected)
            preferences.setThemeMode(((OptionsAction.ThemeSelected) action).mode);
        else if (action instanceof OptionsAction.FocusStepLimitSelected)
            preferences.setFocusStepLimit(((OptionsAction.FocusStepLimitSelected) action).limit);
        else if (action instanceof OptionsAction.RestTimerDefaultChanged)
            preferences.setRestTimerDefaultSeconds(
                    ((OptionsAction.RestTimerDefaultChanged) action).seconds);
        else if (action instanceof OptionsAction.PermissionObserved)
            observePermission((OptionsAction.PermissionObserved) action);
        else if (action instanceof OptionsAction.CalendarPermissionSelected)
            requestCalendarPermission();
        else if (action instanceof OptionsAction.Resumed) {
            if (automaticChecks) automaticCheck();
        } else if (action instanceof OptionsAction.ManualUpdateSelected)
            manualUpdateAction();
        else if (action instanceof OptionsAction.UpdateAccepted) {
            OptionsAction.UpdateAccepted accepted = (OptionsAction.UpdateAccepted) action;
            UpdateInfo update = consumeOffer(accepted.requestId, accepted.update);
            if (update != null) download(update);
        } else if (action instanceof OptionsAction.UpdatePostponed) {
            OptionsAction.UpdatePostponed postponed = (OptionsAction.UpdatePostponed) action;
            UpdateInfo update = consumeOffer(postponed.requestId, postponed.update);
            if (update != null) updatePreferences.postponeUpdate(update.versionCode,
                    updateClock.nowMillis());
        } else if (action instanceof OptionsAction.InstallPermissionResult)
            completeInstallPermission((OptionsAction.InstallPermissionResult) action);
        else if (action instanceof OptionsAction.InstallerFailed) {
            if (consumeInstall(((OptionsAction.InstallerFailed) action).requestId) != null)
                failInstaller();
        } else if (action instanceof OptionsAction.RequestAcknowledged)
            acknowledge(((OptionsAction.RequestAcknowledged) action).requestId);
        else throw new IllegalArgumentException("Unsupported action " + action.getClass());
    }

    private void observePermission(OptionsAction.PermissionObserved action) {
        CalendarPermissionStatus permission = action.granted
                ? CalendarPermissionStatus.GRANTED
                : !preferences.calendarPermissionAsked() || action.showRationale
                ? CalendarPermissionStatus.REQUESTABLE
                : CalendarPermissionStatus.DENIED_TO_SETTINGS;
        boolean changed;
        synchronized (lock) {
            changed = current.calendarPermission != permission;
            if (changed) publish(current.withPermission(permission));
        }
        if (changed) calendarInvalidated.run();
    }

    private void requestCalendarPermission() {
        CalendarPermissionStatus permission;
        synchronized (lock) { permission = current.calendarPermission; }
        if (permission == CalendarPermissionStatus.GRANTED
                || permission == CalendarPermissionStatus.DENIED_TO_SETTINGS)
            enqueue(OptionsRequest.system(nextRequestId(),
                    OptionsRequest.Kind.OPEN_APP_SETTINGS));
        else {
            preferences.markCalendarPermissionAsked();
            enqueue(OptionsRequest.system(nextRequestId(),
                    OptionsRequest.Kind.REQUEST_CALENDAR_PERMISSION));
        }
    }

    private void automaticCheck() {
        long now = updateClock.nowMillis();
        if (!updatePreferences.shouldCheckUpdates(now)) return;
        updatePreferences.markUpdateCheck(now);
        check(true);
    }

    private void manualUpdateAction() {
        UpdateUiState update;
        synchronized (lock) { update = current.update; }
        if (update.status == UpdateUiState.Status.AVAILABLE) download(update.update);
        else if (update.status == UpdateUiState.Status.READY) requestInstall();
        else if (update.status != UpdateUiState.Status.CHECKING
                && update.status != UpdateUiState.Status.DOWNLOADING) check(false);
    }

    private void check(boolean automatic) {
        final long generation;
        synchronized (lock) {
            if (busy(current.update) || cleared) return;
            generation = ++workflowGeneration;
            publish(current.withUpdate(UpdateUiState.checking()));
        }
        updateWorker.execute(() -> {
            try {
                UpdateCheckResult result = updateRepository.check();
                if (!result.isAvailable()) {
                    publishUpdate(generation, UpdateUiState.current());
                    return;
                }
                UpdateInfo update = result.availableUpdate();
                if (!publishUpdate(generation, UpdateUiState.available(update))) return;
                if (!automatic || updatePreferences.shouldPromptForUpdate(
                        update.versionCode, updateClock.nowMillis()))
                    enqueueIfCurrent(generation, OptionsRequest.available(nextRequestId(), update));
            } catch (UpdateFailure error) {
                handleFailure(generation, error, R.string.error_update_check, automatic);
            }
        });
    }

    private void download(UpdateInfo update) {
        if (update == null) return;
        final long generation;
        synchronized (lock) {
            if (busy(current.update) || cleared) return;
            generation = ++workflowGeneration;
            publish(current.withUpdate(UpdateUiState.downloading(update, 0)));
        }
        updateWorker.execute(() -> {
            try {
                VerifiedUpdate result = updateRepository.download(update,
                        progress -> publishUpdate(generation,
                                UpdateUiState.downloading(update, progress)));
                synchronized (lock) {
                    if (!currentGeneration(generation)) return;
                    verified = result;
                    publish(current.withUpdate(UpdateUiState.ready(update)));
                }
                enqueueIfCurrent(generation,
                        OptionsRequest.install(nextRequestId(), result));
            } catch (UpdateFailure error) {
                handleFailure(generation, error, R.string.error_update_download, false);
            }
        });
    }

    private void requestInstall() {
        VerifiedUpdate value;
        synchronized (lock) { value = verified; }
        if (value != null) enqueue(OptionsRequest.install(nextRequestId(), value));
    }

    private void completeInstallPermission(OptionsAction.InstallPermissionResult action) {
        OptionsRequest request;
        synchronized (lock) {
            request = current.firstRequest();
            if (request == null || request.kind != OptionsRequest.Kind.INSTALL_UPDATE) return;
        }
        acknowledge(request.id);
        if (action.granted && request.verified != null)
            enqueue(OptionsRequest.install(nextRequestId(), request.verified));
    }

    @Nullable private UpdateInfo consumeOffer(String requestId, UpdateInfo expected) {
        synchronized (lock) {
            OptionsRequest request = current.request(requestId);
            if (request == null || request.kind != OptionsRequest.Kind.UPDATE_AVAILABLE
                    || request.update == null || expected == null
                    || request.update.versionCode != expected.versionCode) return null;
            publish(current.acknowledge(requestId));
            persistRequests();
            return request.update;
        }
    }

    @Nullable private VerifiedUpdate consumeInstall(String requestId) {
        synchronized (lock) {
            OptionsRequest request = current.request(requestId);
            if (request == null || request.kind != OptionsRequest.Kind.INSTALL_UPDATE
                    || request.verified == null) return null;
            publish(current.acknowledge(requestId));
            persistRequests();
            return request.verified;
        }
    }

    private void failInstaller() {
        String message = texts.text(R.string.error_update_download);
        synchronized (lock) {
            publish(current.withUpdate(UpdateUiState.error(UpdateFailure.Kind.STORAGE, message)));
        }
        enqueue(OptionsRequest.error(nextRequestId(), UpdateFailure.Kind.STORAGE, message));
    }

    private void handleFailure(long generation, UpdateFailure error, int messageResource,
                               boolean silent) {
        updateErrors.report(error);
        String message = texts.text(messageResource);
        if (!publishUpdate(generation, UpdateUiState.error(error.kind(), message))) return;
        if (!silent) enqueueIfCurrent(generation,
                OptionsRequest.error(nextRequestId(), error.kind(), message));
    }

    private Appearance loadAppearance(PresentationInvalidation invalidation) {
        LocalTime time = invalidation.getClock() == null
                ? clock.time() : invalidation.getClock().getTime();
        return new Appearance(preferences.displayPreferences(), time);
    }

    private void publishAppearance(Appearance appearance) {
        synchronized (lock) {
            publish(current.withAppearance(palette(appearance.preferences, appearance.time),
                    appearance.preferences.themeMode, appearance.preferences.focusStepLimit,
                    appearance.preferences.restTimerDefaultSeconds));
        }
    }

    private void publishCalendar(CalendarResult result) {
        synchronized (lock) { publish(current.withCalendar(CalendarUiState.from(result))); }
    }

    private void appearanceReadFailed(Throwable error) {
        logger.error("OptionsViewModel", "Options appearance projection failed", error);
    }

    private void calendarReadFailed(Throwable error) {
        logger.error("OptionsViewModel", "Options calendar projection failed", error);
        publishCalendar(new CalendarResult.Error(error));
    }

    private boolean publishUpdate(long generation, UpdateUiState value) {
        synchronized (lock) {
            if (!currentGeneration(generation)) return false;
            publish(current.withUpdate(value));
            return true;
        }
    }

    private void enqueueIfCurrent(long generation, OptionsRequest request) {
        synchronized (lock) {
            if (!currentGeneration(generation)) return;
            enqueueLocked(request);
        }
    }

    private void enqueue(OptionsRequest request) {
        synchronized (lock) { enqueueLocked(request); }
    }

    private void enqueueLocked(OptionsRequest request) {
        OptionsScreenState next = current.enqueue(request);
        if (next == current) return;
        publish(next);
        persistRequests();
    }

    private void acknowledge(String requestId) {
        synchronized (lock) {
            OptionsScreenState next = current.acknowledge(requestId);
            if (next == current) return;
            publish(next);
            persistRequests();
        }
    }

    private String nextRequestId() {
        synchronized (lock) {
            requestSequence++;
            savedState.set(SAVED_REQUEST_SEQUENCE, requestSequence);
            return "options:" + requestSequence;
        }
    }

    private void persistRequests() {
        savedState.set(SAVED_REQUESTS, requestState.encode(current.requests));
    }

    private void publish(OptionsScreenState value) {
        current = value;
        state.setValue(value);
    }

    private boolean currentGeneration(long generation) {
        return !cleared && workflowGeneration == generation;
    }

    private static boolean busy(UpdateUiState value) {
        return value.status == UpdateUiState.Status.CHECKING
                || value.status == UpdateUiState.Status.DOWNLOADING;
    }

    private static DayPalette palette(DisplayPreferences preferences, LocalTime time) {
        return DayPalette.at(time, DayPalette.Mode.valueOf(preferences.themeMode.name()));
    }

    private static long sequenceOf(String requestId) {
        if (requestId == null || !requestId.startsWith("options:")) return 0L;
        try {
            return Long.parseLong(requestId.substring("options:".length()));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    @Override protected void onCleared() {
        synchronized (lock) {
            cleared = true;
            workflowGeneration++;
        }
        appearanceReads.close();
        calendarReads.close();
        worker.shutdown();
        updateWorker.close();
    }

    private static <T> T required(T value) {
        if (value == null) throw new IllegalArgumentException("Options dependency is required");
        return value;
    }

    private static final class Appearance {
        final DisplayPreferences preferences;
        final LocalTime time;
        Appearance(DisplayPreferences preferences, LocalTime time) {
            this.preferences = preferences;
            this.time = time;
        }
    }

    public static final class Factory implements ViewModelProvider.Factory {
        private final AppContainer container;
        private final Supplier<ExecutorService> workers;

        public Factory(AppContainer container) {
            this(container, Executors::newSingleThreadExecutor);
        }

        Factory(AppContainer container, Supplier<ExecutorService> workers) {
            this.container = container;
            this.workers = workers;
        }

        @NonNull @Override @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass,
                                              @NonNull CreationExtras extras) {
            if (!modelClass.isAssignableFrom(OptionsViewModel.class))
                throw new IllegalArgumentException("Unsupported ViewModel " + modelClass);
            return (T) new OptionsViewModel(container.uiPreferences, container.calendar,
                    container.clock, container.logger, container.texts, container.updates,
                    container.updatePreferences,
                    failure -> container.logger.error("Updater", failure.getMessage(), failure),
                    container.updateClock, container.updateConfiguration.automaticChecksEnabled,
                    container.calendarInvalidations::materializeExternalChange,
                    SavedStateHandleSupport.createSavedStateHandle(extras), workers.get(),
                    container.updateExecutors.create(), container.presentationInvalidations,
                    null);
        }
    }
}
