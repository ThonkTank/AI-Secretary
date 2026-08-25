package de.thonktank.autosecretary;

import de.thonktank.autosecretary.data.preferences.DisplayPreferences;
import de.thonktank.autosecretary.data.preferences.FocusStepLimit;
import de.thonktank.autosecretary.data.preferences.UiThemeMode;
import de.thonktank.autosecretary.presentation.today.TodayFeatureState;
import de.thonktank.autosecretary.presentation.today.TodayUiModel;
import de.thonktank.autosecretary.update.presentation.UpdateUiState;
import de.thonktank.autosecretary.timer.TimerManager;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Atomic state owned by the dashboard/Today slice; editor state has its own owner. */
public final class DashboardUiState {
    public final NavigationDestination navigation;
    public final TodayUiModel dashboard;
    public final TodayFeatureState todayFeature;
    public final CalendarUiState calendar;
    public final DayPalette palette;
    public final CalendarPermissionStatus calendarPermission;
    public final boolean loading;
    public final Set<UiCommand> runningActions;
    public final RepetitionInputState repetitionInput;
    public final UiThemeMode themeMode;
    public final FocusStepLimit focusStepLimit;
    public final int restTimerDefaultSeconds;
    public final UpdateUiState update;
    public final TimerManager.Snapshot timers;

    public DashboardUiState(NavigationDestination navigation, TodayUiModel dashboard,
                            CalendarUiState calendar, DayPalette palette,
                            CalendarPermissionStatus calendarPermission, boolean loading,
                            Set<UiCommand> runningActions) {
        this(navigation, dashboard, calendar, palette, calendarPermission, loading,
                runningActions, RepetitionInputState.idle(), UiThemeMode.AUTO,
                FocusStepLimit.AUTO, 60, UpdateUiState.idle());
    }

    public DashboardUiState(NavigationDestination navigation, TodayUiModel dashboard,
                            CalendarUiState calendar, DayPalette palette,
                            CalendarPermissionStatus calendarPermission, boolean loading,
                            Set<UiCommand> runningActions,
                            RepetitionInputState repetitionInput) {
        this(navigation, dashboard, calendar, palette, calendarPermission, loading,
                runningActions, repetitionInput, UiThemeMode.AUTO,
                FocusStepLimit.AUTO, 60, UpdateUiState.idle());
    }

    public DashboardUiState(NavigationDestination navigation, TodayUiModel dashboard,
                            CalendarUiState calendar, DayPalette palette,
                            CalendarPermissionStatus calendarPermission, boolean loading,
                            Set<UiCommand> runningActions,
                            RepetitionInputState repetitionInput, UiThemeMode themeMode,
                            FocusStepLimit focusStepLimit, UpdateUiState update) {
        this(navigation, dashboard, calendar, palette, calendarPermission, loading,
                runningActions, repetitionInput, themeMode, focusStepLimit, 60, update);
    }

    public DashboardUiState(NavigationDestination navigation, TodayUiModel dashboard,
                            CalendarUiState calendar, DayPalette palette,
                            CalendarPermissionStatus calendarPermission, boolean loading,
                            Set<UiCommand> runningActions,
                            RepetitionInputState repetitionInput, UiThemeMode themeMode,
                            FocusStepLimit focusStepLimit, int restTimerDefaultSeconds,
                            UpdateUiState update) {
        this(navigation, dashboard, calendar, palette, calendarPermission, loading,
                runningActions, repetitionInput, themeMode, focusStepLimit,
                restTimerDefaultSeconds, update, TimerManager.Snapshot.empty(),
                TodayFeatureState.idle(dashboard));
    }

    private DashboardUiState(NavigationDestination navigation, TodayUiModel dashboard,
                             CalendarUiState calendar, DayPalette palette,
                             CalendarPermissionStatus calendarPermission, boolean loading,
                             Set<UiCommand> runningActions,
                             RepetitionInputState repetitionInput, UiThemeMode themeMode,
                             FocusStepLimit focusStepLimit, int restTimerDefaultSeconds,
                             UpdateUiState update,
                             TimerManager.Snapshot timers,
                             TodayFeatureState todayFeature) {
        if (themeMode == null || focusStepLimit == null || restTimerDefaultSeconds < 1
                || update == null || timers == null)
            throw new IllegalArgumentException("Complete render preferences are required");
        this.navigation = navigation;
        this.dashboard = dashboard;
        this.todayFeature = todayFeature;
        this.calendar = calendar;
        this.palette = palette;
        this.calendarPermission = calendarPermission;
        this.loading = loading;
        this.runningActions = Collections.unmodifiableSet(new LinkedHashSet<>(runningActions));
        this.repetitionInput = repetitionInput;
        this.themeMode = themeMode;
        this.focusStepLimit = focusStepLimit;
        this.restTimerDefaultSeconds = restTimerDefaultSeconds;
        this.update = update;
        this.timers = timers;
    }

    public DashboardUiState withNavigation(NavigationDestination value) {
        return copy(value, dashboard, calendar, palette, calendarPermission, loading,
                runningActions, repetitionInput);
    }

    public DashboardUiState withPalette(DayPalette value) {
        return copy(navigation, dashboard, calendar, value, calendarPermission, loading,
                runningActions, repetitionInput);
    }

    public DashboardUiState withPermission(CalendarPermissionStatus value) {
        return copy(navigation, dashboard, calendar, palette, value, loading,
                runningActions, repetitionInput);
    }

    public DashboardUiState withLoading(boolean value) {
        return copy(navigation, dashboard,
                value ? CalendarUiState.loading(calendar) : calendar, palette,
                calendarPermission, value, runningActions, repetitionInput);
    }

    public DashboardUiState withContent(TodayUiModel dashboardValue,
                                        CalendarUiState calendarValue) {
        return new DashboardUiState(navigation, dashboardValue, calendarValue, palette,
                calendarPermission, false, runningActions,
                repetitionInput.reconcile(dashboardValue.focus), themeMode, focusStepLimit,
                restTimerDefaultSeconds, update, timers,
                TodayFeatureState.idle(dashboardValue));
    }

    public DashboardUiState withToday(TodayUiModel value) {
        return withTodayFeature(TodayFeatureState.idle(value));
    }

    public DashboardUiState withTodayFeature(TodayFeatureState value) {
        return new DashboardUiState(navigation, value.today, calendar, palette,
                calendarPermission, loading, runningActions,
                repetitionInput.reconcile(value.today.focus), themeMode, focusStepLimit,
                restTimerDefaultSeconds, update, timers, value);
    }

    public DashboardUiState withRunningActions(Set<UiCommand> value) {
        return copy(navigation, dashboard, calendar, palette, calendarPermission, loading,
                value, repetitionInput);
    }

    public DashboardUiState withRepetitionInput(RepetitionInputState value) {
        return copy(navigation, dashboard, calendar, palette, calendarPermission, loading,
                runningActions, value);
    }

    public DashboardUiState withDisplayPreferences(DisplayPreferences value,
                                                   DayPalette paletteValue) {
        return new DashboardUiState(navigation, dashboard, calendar, paletteValue,
                calendarPermission, loading, runningActions, repetitionInput,
                value.themeMode, value.focusStepLimit, value.restTimerDefaultSeconds,
                update, timers, todayFeature);
    }

    public DashboardUiState withUpdate(UpdateUiState value) {
        return new DashboardUiState(navigation, dashboard, calendar, palette,
                calendarPermission, loading, runningActions, repetitionInput,
                themeMode, focusStepLimit, restTimerDefaultSeconds, value, timers, todayFeature);
    }

    public DashboardUiState withTimers(TimerManager.Snapshot value) {
        return new DashboardUiState(navigation, dashboard, calendar, palette,
                calendarPermission, loading, runningActions, repetitionInput,
                themeMode, focusStepLimit, restTimerDefaultSeconds, update, value, todayFeature);
    }

    public boolean isRunning(UiCommand key) { return runningActions.contains(key); }

    private DashboardUiState copy(NavigationDestination navigationValue,
                                  TodayUiModel dashboardValue,
                                  CalendarUiState calendarValue, DayPalette paletteValue,
                                  CalendarPermissionStatus permissionValue, boolean loadingValue,
                                  Set<UiCommand> actionsValue,
                                  RepetitionInputState repetitionInputValue) {
        return new DashboardUiState(navigationValue, dashboardValue, calendarValue,
                paletteValue, permissionValue, loadingValue, actionsValue,
                repetitionInputValue, themeMode, focusStepLimit, restTimerDefaultSeconds,
                update, timers, todayFeature);
    }
}
