package de.thonktank.autosecretary;

import de.thonktank.autosecretary.presentation.today.TodayUiModel;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import de.thonktank.autosecretary.data.preferences.DisplayPreferences;
import de.thonktank.autosecretary.data.preferences.FocusStepLimit;
import de.thonktank.autosecretary.data.preferences.UiThemeMode;
import de.thonktank.autosecretary.update.presentation.UpdateUiState;

public final class DashboardUiState {
    public final NavigationDestination navigation;
    public final TodayUiModel dashboard;
    public final CalendarUiState calendar;
    public final DayPalette palette;
    public final CalendarPermissionStatus calendarPermission;
    public final boolean loading;
    public final Set<UiCommand> runningActions;
    public final EditorUiState editor;
    public final RepetitionInputState repetitionInput;
    public final UiThemeMode themeMode;
    public final FocusStepLimit focusStepLimit;
    public final UpdateUiState update;

    public DashboardUiState(NavigationDestination navigation, TodayUiModel dashboard,
                            CalendarUiState calendar, DayPalette palette,
                            CalendarPermissionStatus calendarPermission, boolean loading,
                            Set<UiCommand> runningActions, EditorUiState editor) {
        this(navigation, dashboard, calendar, palette, calendarPermission, loading,
                runningActions, editor, RepetitionInputState.idle(), UiThemeMode.AUTO,
                FocusStepLimit.AUTO, UpdateUiState.idle());
    }

    public DashboardUiState(NavigationDestination navigation, TodayUiModel dashboard,
                            CalendarUiState calendar, DayPalette palette,
                            CalendarPermissionStatus calendarPermission, boolean loading,
                            Set<UiCommand> runningActions, EditorUiState editor,
                            RepetitionInputState repetitionInput) {
        this(navigation, dashboard, calendar, palette, calendarPermission, loading,
                runningActions, editor, repetitionInput, UiThemeMode.AUTO,
                FocusStepLimit.AUTO, UpdateUiState.idle());
    }

    public DashboardUiState(NavigationDestination navigation, TodayUiModel dashboard,
                            CalendarUiState calendar, DayPalette palette,
                            CalendarPermissionStatus calendarPermission, boolean loading,
                            Set<UiCommand> runningActions, EditorUiState editor,
                            RepetitionInputState repetitionInput, UiThemeMode themeMode,
                            FocusStepLimit focusStepLimit, UpdateUiState update) {
        if (themeMode == null || focusStepLimit == null || update == null)
            throw new IllegalArgumentException("Complete render preferences are required");
        this.navigation = navigation;
        this.dashboard = dashboard;
        this.calendar = calendar;
        this.palette = palette;
        this.calendarPermission = calendarPermission;
        this.loading = loading;
        this.runningActions = Collections.unmodifiableSet(new LinkedHashSet<>(runningActions));
        this.editor = editor;
        this.repetitionInput = repetitionInput;
        this.themeMode = themeMode;
        this.focusStepLimit = focusStepLimit;
        this.update = update;
    }

    public DashboardUiState withNavigation(NavigationDestination value) {
        return copy(value, dashboard, calendar, palette, calendarPermission, loading,
                runningActions, editor, repetitionInput);
    }

    public DashboardUiState withEditor(EditorUiState value) {
        return copy(navigation, dashboard, calendar, palette, calendarPermission, loading,
                runningActions, value, repetitionInput);
    }

    public DashboardUiState withPalette(DayPalette value) {
        return copy(navigation, dashboard, calendar, value, calendarPermission, loading,
                runningActions, editor, repetitionInput);
    }

    public DashboardUiState withPermission(CalendarPermissionStatus value) {
        return copy(navigation, dashboard, calendar, palette, value, loading,
                runningActions, editor, repetitionInput);
    }

    public DashboardUiState withLoading(boolean value) {
        return copy(navigation, dashboard,
                value ? CalendarUiState.loading(calendar) : calendar, palette,
                calendarPermission, value, runningActions, editor, repetitionInput);
    }

    public DashboardUiState withContent(TodayUiModel dashboardValue,
                                        CalendarUiState calendarValue) {
        return copy(navigation, dashboardValue, calendarValue, palette, calendarPermission,
                false, runningActions, editor,
                repetitionInput.reconcile(dashboardValue.focus));
    }

    /** Replaces only the Today feature projection and preserves every sibling state. */
    public DashboardUiState withToday(TodayUiModel value) {
        return copy(navigation, value, calendar, palette, calendarPermission, loading,
                runningActions, editor, repetitionInput.reconcile(value.focus));
    }

    public DashboardUiState withRunningActions(Set<UiCommand> value) {
        return copy(navigation, dashboard, calendar, palette, calendarPermission, loading,
                value, editor, repetitionInput);
    }

    public DashboardUiState withRepetitionInput(RepetitionInputState value) {
        return copy(navigation, dashboard, calendar, palette, calendarPermission, loading,
                runningActions, editor, value);
    }

    public DashboardUiState withDisplayPreferences(DisplayPreferences value,
                                                   DayPalette paletteValue) {
        return new DashboardUiState(navigation, dashboard, calendar, paletteValue,
                calendarPermission, loading, runningActions, editor, repetitionInput,
                value.themeMode, value.focusStepLimit, update);
    }

    public DashboardUiState withUpdate(UpdateUiState value) {
        return new DashboardUiState(navigation, dashboard, calendar, palette,
                calendarPermission, loading, runningActions, editor, repetitionInput,
                themeMode, focusStepLimit, value);
    }

    public boolean isRunning(UiCommand key) { return runningActions.contains(key); }

    private DashboardUiState copy(NavigationDestination navigationValue,
                                  TodayUiModel dashboardValue,
                                  CalendarUiState calendarValue, DayPalette paletteValue,
                                  CalendarPermissionStatus permissionValue, boolean loadingValue,
                                  Set<UiCommand> actionsValue, EditorUiState editorValue,
                                  RepetitionInputState repetitionInputValue) {
        return new DashboardUiState(navigationValue, dashboardValue, calendarValue,
                paletteValue, permissionValue, loadingValue, actionsValue, editorValue,
                repetitionInputValue, themeMode, focusStepLimit, update);
    }
}
