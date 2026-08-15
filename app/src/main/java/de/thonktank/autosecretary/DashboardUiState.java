package de.thonktank.autosecretary;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class DashboardUiState {
    public final NavigationDestination navigation;
    public final DashboardUiModel dashboard;
    public final CalendarUiState calendar;
    public final DayPalette palette;
    public final CalendarPermissionStatus calendarPermission;
    public final boolean loading;
    public final Set<String> runningActions;
    public final EditorUiState editor;

    public DashboardUiState(NavigationDestination navigation, DashboardUiModel dashboard,
                            CalendarUiState calendar, DayPalette palette,
                            CalendarPermissionStatus calendarPermission, boolean loading,
                            Set<String> runningActions, EditorUiState editor) {
        this.navigation = navigation;
        this.dashboard = dashboard;
        this.calendar = calendar;
        this.palette = palette;
        this.calendarPermission = calendarPermission;
        this.loading = loading;
        this.runningActions = Collections.unmodifiableSet(new LinkedHashSet<>(runningActions));
        this.editor = editor;
    }

    public DashboardUiState withNavigation(NavigationDestination value) {
        return copy(value, dashboard, calendar, palette, calendarPermission, loading,
                runningActions, editor);
    }

    public DashboardUiState withEditor(EditorUiState value) {
        return copy(navigation, dashboard, calendar, palette, calendarPermission, loading,
                runningActions, value);
    }

    public DashboardUiState withPalette(DayPalette value) {
        return copy(navigation, dashboard, calendar, value, calendarPermission, loading,
                runningActions, editor);
    }

    public DashboardUiState withPermission(CalendarPermissionStatus value) {
        return copy(navigation, dashboard, calendar, palette, value, loading,
                runningActions, editor);
    }

    public DashboardUiState withLoading(boolean value) {
        return copy(navigation, dashboard, new CalendarUiState(value, calendar.events), palette,
                calendarPermission, value, runningActions, editor);
    }

    public DashboardUiState withContent(DashboardUiModel dashboardValue,
                                        CalendarUiState calendarValue) {
        return copy(navigation, dashboardValue, calendarValue, palette, calendarPermission,
                false, runningActions, editor);
    }

    public DashboardUiState withRunningActions(Set<String> value) {
        return copy(navigation, dashboard, calendar, palette, calendarPermission, loading,
                value, editor);
    }

    public boolean isRunning(String key) {
        return runningActions.contains(key);
    }

    private DashboardUiState copy(NavigationDestination navigationValue,
                                  DashboardUiModel dashboardValue,
                                  CalendarUiState calendarValue, DayPalette paletteValue,
                                  CalendarPermissionStatus permissionValue, boolean loadingValue,
                                  Set<String> actionsValue, EditorUiState editorValue) {
        return new DashboardUiState(navigationValue, dashboardValue, calendarValue,
                paletteValue, permissionValue, loadingValue, actionsValue, editorValue);
    }
}
