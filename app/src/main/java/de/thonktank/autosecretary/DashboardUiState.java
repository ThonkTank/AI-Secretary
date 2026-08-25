package de.thonktank.autosecretary;

import de.thonktank.autosecretary.data.preferences.FocusStepLimit;
import de.thonktank.autosecretary.presentation.today.TodayFeatureState;
import de.thonktank.autosecretary.presentation.today.TodayUiModel;
import de.thonktank.autosecretary.timer.TimerManager;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Atomic state owned only by the dashboard/Today slice. */
public final class DashboardUiState {
    public final NavigationDestination navigation;
    public final TodayUiModel dashboard;
    public final TodayFeatureState todayFeature;
    public final DayPalette palette;
    public final boolean loading;
    public final Set<UiCommand> runningActions;
    public final RepetitionInputState repetitionInput;
    public final FocusStepLimit focusStepLimit;
    public final TimerManager.Snapshot timers;

    public DashboardUiState(NavigationDestination navigation, TodayUiModel dashboard,
                            DayPalette palette, boolean loading,
                            Set<UiCommand> runningActions) {
        this(navigation, dashboard, palette, loading, runningActions,
                RepetitionInputState.idle(), FocusStepLimit.AUTO,
                TimerManager.Snapshot.empty(),
                TodayFeatureState.idle(dashboard));
    }

    public DashboardUiState(NavigationDestination navigation, TodayUiModel dashboard,
                            DayPalette palette, boolean loading,
                            Set<UiCommand> runningActions,
                            RepetitionInputState repetitionInput,
                            FocusStepLimit focusStepLimit) {
        this(navigation, dashboard, palette, loading, runningActions, repetitionInput,
                focusStepLimit, TimerManager.Snapshot.empty(),
                TodayFeatureState.idle(dashboard));
    }

    private DashboardUiState(NavigationDestination navigation, TodayUiModel dashboard,
                             DayPalette palette, boolean loading,
                             Set<UiCommand> runningActions,
                             RepetitionInputState repetitionInput,
                             FocusStepLimit focusStepLimit,
                             TimerManager.Snapshot timers,
                             TodayFeatureState todayFeature) {
        if (navigation == null || dashboard == null || palette == null
                || runningActions == null || repetitionInput == null
                || focusStepLimit == null || timers == null || todayFeature == null)
            throw new IllegalArgumentException("Complete dashboard state is required");
        this.navigation = navigation;
        this.dashboard = dashboard;
        this.todayFeature = todayFeature;
        this.palette = palette;
        this.loading = loading;
        this.runningActions = Collections.unmodifiableSet(new LinkedHashSet<>(runningActions));
        this.repetitionInput = repetitionInput;
        this.focusStepLimit = focusStepLimit;
        this.timers = timers;
    }

    public DashboardUiState withNavigation(NavigationDestination value) {
        return copy(value, dashboard, palette, loading, runningActions, repetitionInput,
                focusStepLimit, todayFeature);
    }

    public DashboardUiState withLoading(boolean value) {
        return copy(navigation, dashboard, palette, value, runningActions, repetitionInput,
                focusStepLimit, todayFeature);
    }

    public DashboardUiState withContent(TodayUiModel value) {
        return copy(navigation, value, palette, false, runningActions,
                repetitionInput.reconcile(value.focus), focusStepLimit,
                TodayFeatureState.idle(value));
    }

    public DashboardUiState withToday(TodayUiModel value) {
        return withTodayFeature(TodayFeatureState.idle(value));
    }

    public DashboardUiState withTodayFeature(TodayFeatureState value) {
        return copy(navigation, value.today, palette, loading, runningActions,
                repetitionInput.reconcile(value.today.focus), focusStepLimit, value);
    }

    public DashboardUiState withRunningActions(Set<UiCommand> value) {
        return copy(navigation, dashboard, palette, loading, value, repetitionInput,
                focusStepLimit, todayFeature);
    }

    public DashboardUiState withRepetitionInput(RepetitionInputState value) {
        return copy(navigation, dashboard, palette, loading, runningActions, value,
                focusStepLimit, todayFeature);
    }

    public DashboardUiState withAppearance(DayPalette paletteValue,
                                           FocusStepLimit focusStepLimitValue) {
        return copy(navigation, dashboard, paletteValue, loading, runningActions,
                repetitionInput, focusStepLimitValue, todayFeature);
    }

    public DashboardUiState withTimers(TimerManager.Snapshot value) {
        return new DashboardUiState(navigation, dashboard, palette, loading, runningActions,
                repetitionInput, focusStepLimit, value, todayFeature);
    }

    public boolean isRunning(UiCommand key) { return runningActions.contains(key); }

    private DashboardUiState copy(NavigationDestination navigation,
                                  TodayUiModel dashboard, DayPalette palette,
                                  boolean loading, Set<UiCommand> runningActions,
                                  RepetitionInputState repetitionInput,
                                  FocusStepLimit focusStepLimit,
                                  TodayFeatureState todayFeature) {
        return new DashboardUiState(navigation, dashboard, palette, loading, runningActions,
                repetitionInput, focusStepLimit, timers, todayFeature);
    }
}
