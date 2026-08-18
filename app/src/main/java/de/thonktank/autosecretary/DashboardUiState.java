package de.thonktank.autosecretary;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class DashboardUiState {
    public final NavigationDestination navigation;
    public final TodayUiModel dashboard;
    public final CalendarUiState calendar;
    public final DayPalette palette;
    public final CalendarPermissionStatus calendarPermission;
    public final boolean loading;
    public final Set<UiCommand> runningActions;
    public final EditorUiState editor;
    public final SetProgressEditorState setProgressEditor;

    public DashboardUiState(NavigationDestination navigation, TodayUiModel dashboard,
                            CalendarUiState calendar, DayPalette palette,
                            CalendarPermissionStatus calendarPermission, boolean loading,
                            Set<UiCommand> runningActions, EditorUiState editor) {
        this(navigation, dashboard, calendar, palette, calendarPermission, loading,
                runningActions, editor, SetProgressEditorState.closed());
    }

    public DashboardUiState(NavigationDestination navigation, TodayUiModel dashboard,
                            CalendarUiState calendar, DayPalette palette,
                            CalendarPermissionStatus calendarPermission, boolean loading,
                            Set<UiCommand> runningActions, EditorUiState editor,
                            SetProgressEditorState setProgressEditor) {
        this.navigation = navigation;
        this.dashboard = dashboard;
        this.calendar = calendar;
        this.palette = palette;
        this.calendarPermission = calendarPermission;
        this.loading = loading;
        this.runningActions = Collections.unmodifiableSet(new LinkedHashSet<>(runningActions));
        this.editor = editor;
        this.setProgressEditor = setProgressEditor;
    }

    public DashboardUiState withNavigation(NavigationDestination value) {
        return copy(value, dashboard, calendar, palette, calendarPermission, loading,
                runningActions, editor, setProgressEditor);
    }

    public DashboardUiState withEditor(EditorUiState value) {
        return copy(navigation, dashboard, calendar, palette, calendarPermission, loading,
                runningActions, value, setProgressEditor);
    }

    public DashboardUiState withPalette(DayPalette value) {
        return copy(navigation, dashboard, calendar, value, calendarPermission, loading,
                runningActions, editor, setProgressEditor);
    }

    public DashboardUiState withPermission(CalendarPermissionStatus value) {
        return copy(navigation, dashboard, calendar, palette, value, loading,
                runningActions, editor, setProgressEditor);
    }

    public DashboardUiState withLoading(boolean value) {
        return copy(navigation, dashboard, value ? CalendarUiState.loading(calendar) : calendar, palette,
                calendarPermission, value, runningActions, editor, setProgressEditor);
    }

    public DashboardUiState withContent(TodayUiModel dashboardValue,
                                        CalendarUiState calendarValue) {
        return copy(navigation, dashboardValue, calendarValue, palette, calendarPermission,
                false, runningActions, editor,
                setProgressEditor.closeIfMissing(dashboardValue.tasks));
    }

    public DashboardUiState withRunningActions(Set<UiCommand> value) {
        return copy(navigation, dashboard, calendar, palette, calendarPermission, loading,
                value, editor, setProgressEditor);
    }

    public DashboardUiState withSetProgressEditor(SetProgressEditorState value) {
        return copy(navigation, dashboard, calendar, palette, calendarPermission, loading,
                runningActions, editor, value);
    }

    public boolean isRunning(UiCommand key) {
        return runningActions.contains(key);
    }

    private DashboardUiState copy(NavigationDestination navigationValue,
                                  TodayUiModel dashboardValue,
                                  CalendarUiState calendarValue, DayPalette paletteValue,
                                  CalendarPermissionStatus permissionValue, boolean loadingValue,
                                  Set<UiCommand> actionsValue, EditorUiState editorValue,
                                  SetProgressEditorState setProgressEditorValue) {
        return new DashboardUiState(navigationValue, dashboardValue, calendarValue,
                paletteValue, permissionValue, loadingValue, actionsValue, editorValue,
                setProgressEditorValue);
    }
}
