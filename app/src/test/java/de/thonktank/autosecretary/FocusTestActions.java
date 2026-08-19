package de.thonktank.autosecretary;

/** Event sink with overridable hooks used only by focused view tests. */
class FocusTestActions implements DashboardEventSink {
    @Override public void emit(DashboardEvent event) {
        if (event instanceof DashboardEvent.FocusAction) {
            DashboardEvent.FocusAction action = (DashboardEvent.FocusAction) event;
            if (action.kind == DashboardEvent.FocusActionKind.COMPLETE) onComplete(action.task);
            else if (action.kind == DashboardEvent.FocusActionKind.COMPLETE_REMAINING)
                onCompleteRemaining(action.task);
            else if (action.kind == DashboardEvent.FocusActionKind.HARVEST)
                onHarvest(action.task);
            else onDefer(action.task);
        } else if (event instanceof DashboardEvent.ToggleStep) {
            onToggleStep(((DashboardEvent.ToggleStep) event).stepId);
        } else if (event instanceof DashboardEvent.AdjustRepetition) {
            DashboardEvent.AdjustRepetition adjustment =
                    (DashboardEvent.AdjustRepetition) event;
            onAdjustRepetition(adjustment.stepId, adjustment.delta);
        } else if (event instanceof DashboardEvent.EditRepetition) {
            DashboardEvent.EditRepetition edit = (DashboardEvent.EditRepetition) event;
            onEditRepetition(edit.stepId, edit.index);
        } else if (event instanceof DashboardEvent.SubmitRepetition) {
            onSubmitRepetition(((DashboardEvent.SubmitRepetition) event).stepId);
        } else if (event instanceof DashboardEvent.AddTask) onAddTask();
        else if (event instanceof DashboardEvent.TimelinePrimary)
            onTaskAction(((DashboardEvent.TimelinePrimary) event).task);
        else if (event instanceof DashboardEvent.TimelineMenu)
            onTaskMenu(((DashboardEvent.TimelineMenu) event).task);
        else if (event instanceof DashboardEvent.ThemeSelected)
            onTheme(((DashboardEvent.ThemeSelected) event).mode);
        else if (event instanceof DashboardEvent.FocusStepLimitSelected)
            onFocusStepLimit(((DashboardEvent.FocusStepLimitSelected) event).limit);
        else if (event instanceof DashboardEvent.CalendarPermission) onCalendarPermission();
        else if (event instanceof DashboardEvent.CheckUpdates) onUpdates();
    }

    public void onComplete(TaskSnapshot task) { }
    public void onCompleteRemaining(TaskSnapshot task) { }
    public void onHarvest(TaskSnapshot task) { }
    public void onDefer(TaskSnapshot task) { }
    public void onToggleStep(String stepId) { }
    public void onAdjustRepetition(String stepId, int delta) { }
    public void onEditRepetition(String stepId, int index) { }
    public void onSubmitRepetition(String stepId) { }
    public void onAddTask() { }
    public void onTaskAction(TimelineTaskUiModel task) { }
    public void onTaskMenu(TimelineTaskUiModel task) { }
    public void onTheme(de.thonktank.autosecretary.data.preferences.UiThemeMode mode) { }
    public void onFocusStepLimit(
            de.thonktank.autosecretary.data.preferences.FocusStepLimit limit) { }
    public void onCalendarPermission() { }
    public void onUpdates() { }
}
