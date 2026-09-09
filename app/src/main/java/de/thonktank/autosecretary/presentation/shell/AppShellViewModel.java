package de.thonktank.autosecretary.presentation.shell;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.SavedStateHandleSupport;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.viewmodel.CreationExtras;

import de.thonktank.autosecretary.AppContainer;
import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.DayPalette;
import de.thonktank.autosecretary.NavigationDestination;
import de.thonktank.autosecretary.ZoneIdProvider;
import de.thonktank.autosecretary.data.observable.ClockInvalidationReason;
import de.thonktank.autosecretary.data.observable.ClockSnapshot;
import de.thonktank.autosecretary.data.preferences.DisplayPreferences;
import de.thonktank.autosecretary.data.preferences.UiPreferences;
import de.thonktank.autosecretary.data.preferences.UiThemeMode;
import de.thonktank.autosecretary.infrastructure.AppLogger;
import de.thonktank.autosecretary.presentation.observable.AppShellInvalidationRouting;
import de.thonktank.autosecretary.presentation.observable.LatestReadPipeline;
import de.thonktank.autosecretary.presentation.observable.PresentationInvalidation;
import de.thonktank.autosecretary.presentation.observable.PresentationInvalidationSource;

import java.time.LocalTime;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.StateFlow;
import kotlinx.coroutines.flow.StateFlowKt;

/** Sole owner of the temporary pre-Navigation-3 shell state. */
public final class AppShellViewModel extends ViewModel {
    private static final String SAVED_NAVIGATION = "shell_navigation";

    private final UiPreferences preferences;
    private final Clock clock;
    private final ZoneIdProvider zones;
    private final AppLogger logger;
    private final SavedStateHandle savedState;
    private final ExecutorService worker;
    private final LatestReadPipeline<PresentationInvalidation, Appearance> appearanceReads;
    private final MutableStateFlow<AppShellScreenState> state;
    private final Object lock = new Object();
    private AppShellScreenState current;

    public AppShellViewModel(UiPreferences preferences, Clock clock, ZoneIdProvider zones,
                      AppLogger logger,
                      PresentationInvalidationSource invalidations,
                      SavedStateHandle savedState, ExecutorService worker,
                      @Nullable Executor collectionExecutor) {
        this.preferences = required(preferences);
        this.clock = required(clock);
        this.zones = required(zones);
        this.logger = required(logger);
        this.savedState = required(savedState);
        this.worker = required(worker);
        DisplayPreferences display = preferences.displayPreferences();
        ClockSnapshot initialClock = ClockSnapshot.capture(
                clock, zones, ClockInvalidationReason.INITIAL);
        current = new AppShellScreenState(restoredNavigation(
                savedState.get(SAVED_NAVIGATION)),
                palette(display.themeMode, initialClock.getTime()),
                initialClock.getEpochMillis());
        state = StateFlowKt.MutableStateFlow(current);
        AppShellInvalidationRouting routing = new AppShellInvalidationRouting(invalidations);
        if (collectionExecutor == null) {
            appearanceReads = LatestReadPipeline.reading(routing.getAppearanceChanges(), worker,
                    this::loadAppearance, this::publishAppearance, this::appearanceReadFailed);
        } else {
            appearanceReads = LatestReadPipeline.reading(routing.getAppearanceChanges(), worker,
                    collectionExecutor, this::loadAppearance, this::publishAppearance,
                    this::appearanceReadFailed);
        }
    }

    public StateFlow<AppShellScreenState> state() { return state; }

    public void dispatch(AppShellAction action) {
        if (!(action instanceof AppShellAction.DestinationSelected))
            throw new IllegalArgumentException("Unsupported shell action");
        NavigationDestination destination =
                ((AppShellAction.DestinationSelected) action).destination;
        synchronized (lock) {
            savedState.set(SAVED_NAVIGATION, destination.name());
            publish(current.withNavigation(destination));
        }
    }

    private Appearance loadAppearance(PresentationInvalidation invalidation) {
        DisplayPreferences display = invalidation.getDisplayPreferences();
        if (display == null) display = preferences.displayPreferences();
        ClockSnapshot snapshot = invalidation.getClock();
        if (snapshot == null)
            snapshot = ClockSnapshot.capture(clock, zones, ClockInvalidationReason.INITIAL);
        return new Appearance(
                palette(display.themeMode, snapshot.getTime()), snapshot.getEpochMillis());
    }

    private void publishAppearance(Appearance value) {
        synchronized (lock) {
            publish(current.withAppearance(value.palette, value.nowEpochMillis));
        }
    }

    private void publish(AppShellScreenState value) {
        current = value;
        state.setValue(value);
    }

    private void appearanceReadFailed(Throwable error) {
        logger.error("AppShellViewModel", "Shell appearance projection failed", error);
    }

    @Override public void onCleared() {
        appearanceReads.close();
        worker.shutdown();
    }

    private static DayPalette palette(UiThemeMode mode, LocalTime time) {
        return DayPalette.at(time, DayPalette.Mode.valueOf(mode.name()));
    }

    private static NavigationDestination restoredNavigation(String stored) {
        if (stored == null) return NavigationDestination.TODAY;
        try {
            return NavigationDestination.valueOf(stored);
        } catch (IllegalArgumentException error) {
            return NavigationDestination.TODAY;
        }
    }

    private static <T> T required(T value) {
        if (value == null) throw new IllegalArgumentException("Shell dependency is required");
        return value;
    }

    private static final class Appearance {
        final DayPalette palette;
        final long nowEpochMillis;

        Appearance(DayPalette palette, long nowEpochMillis) {
            this.palette = palette;
            this.nowEpochMillis = nowEpochMillis;
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
            if (!modelClass.isAssignableFrom(AppShellViewModel.class))
                throw new IllegalArgumentException("Unsupported ViewModel " + modelClass);
            return (T) new AppShellViewModel(container.uiPreferences, container.clock,
                    container.zones,
                    container.logger, container.presentationInvalidations,
                    SavedStateHandleSupport.createSavedStateHandle(extras), workers.get(), null);
        }
    }
}
