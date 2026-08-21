package de.thonktank.autosecretary;

import de.thonktank.autosecretary.presentation.today.TimelineTaskUiModel;

import de.thonktank.autosecretary.data.preferences.FocusStepLimit;
import de.thonktank.autosecretary.data.preferences.UiThemeMode;

/** Typed user intent emitted by dashboard views. */
public abstract class DashboardEvent {
    private DashboardEvent() { }

    public enum FocusActionKind { COMPLETE, COMPLETE_REMAINING, HARVEST, DEFER }

    public static final class AddTask extends DashboardEvent {
        private AddTask() { }
    }

    public static final class TimelinePrimary extends DashboardEvent {
        public final TimelineTaskUiModel task;
        private TimelinePrimary(TimelineTaskUiModel task) { this.task = required(task); }
    }

    public static final class TimelineMenu extends DashboardEvent {
        public final TimelineTaskUiModel task;
        private TimelineMenu(TimelineTaskUiModel task) { this.task = required(task); }
    }

    public static final class FocusAction extends DashboardEvent {
        public final FocusActionKind kind;
        public final TaskSnapshot task;
        private FocusAction(FocusActionKind kind, TaskSnapshot task) {
            this.kind = required(kind);
            this.task = required(task);
        }
    }

    public static final class ToggleStep extends DashboardEvent {
        public final String stepId;
        private ToggleStep(String stepId) { this.stepId = requiredId(stepId); }
    }

    public static final class AdvanceTodayStep extends DashboardEvent {
        public final String stepId;
        private AdvanceTodayStep(String stepId) { this.stepId = requiredId(stepId); }
    }

    public static final class MoveTodayStep extends DashboardEvent {
        public final String stepId;
        public final String beforeStepId;
        private MoveTodayStep(String stepId, String beforeStepId) {
            this.stepId = requiredId(stepId);
            this.beforeStepId = beforeStepId == null || beforeStepId.isEmpty()
                    ? null : beforeStepId;
        }
    }

    public static final class UndoCompleted extends DashboardEvent {
        public final String occurrenceId;
        private UndoCompleted(String occurrenceId) {
            this.occurrenceId = requiredId(occurrenceId);
        }
    }

    public static final class AdjustRepetition extends DashboardEvent {
        public final String stepId;
        public final int delta;
        private AdjustRepetition(String stepId, int delta) {
            if (delta == 0) throw new IllegalArgumentException("Adjustment must not be zero");
            this.stepId = requiredId(stepId);
            this.delta = delta;
        }
    }

    public static final class EditRepetition extends DashboardEvent {
        public final String stepId;
        public final int index;
        private EditRepetition(String stepId, int index) {
            if (index < 0) throw new IllegalArgumentException("Saved result index is required");
            this.stepId = requiredId(stepId);
            this.index = index;
        }
    }

    public static final class SubmitRepetition extends DashboardEvent {
        public final String stepId;
        private SubmitRepetition(String stepId) { this.stepId = requiredId(stepId); }
    }

    public static final class ThemeSelected extends DashboardEvent {
        public final UiThemeMode mode;
        private ThemeSelected(UiThemeMode mode) { this.mode = required(mode); }
    }

    public static final class FocusStepLimitSelected extends DashboardEvent {
        public final FocusStepLimit limit;
        private FocusStepLimitSelected(FocusStepLimit limit) {
            this.limit = required(limit);
        }
    }

    public static final class CalendarPermission extends DashboardEvent {
        private CalendarPermission() { }
    }

    public static final class CheckUpdates extends DashboardEvent {
        private CheckUpdates() { }
    }

    public static DashboardEvent addTask() { return new AddTask(); }
    public static DashboardEvent timelinePrimary(TimelineTaskUiModel task) {
        return new TimelinePrimary(task);
    }
    public static DashboardEvent timelineMenu(TimelineTaskUiModel task) {
        return new TimelineMenu(task);
    }
    public static DashboardEvent focusAction(FocusActionKind kind, TaskSnapshot task) {
        return new FocusAction(kind, task);
    }
    public static DashboardEvent toggleStep(String stepId) { return new ToggleStep(stepId); }
    public static DashboardEvent advanceTodayStep(String stepId) {
        return new AdvanceTodayStep(stepId);
    }
    public static DashboardEvent moveTodayStep(String stepId, String beforeStepId) {
        return new MoveTodayStep(stepId, beforeStepId);
    }
    public static DashboardEvent undoCompleted(String occurrenceId) {
        return new UndoCompleted(occurrenceId);
    }
    public static DashboardEvent adjustRepetition(String stepId, int delta) {
        return new AdjustRepetition(stepId, delta);
    }
    public static DashboardEvent editRepetition(String stepId, int index) {
        return new EditRepetition(stepId, index);
    }
    public static DashboardEvent submitRepetition(String stepId) {
        return new SubmitRepetition(stepId);
    }
    public static DashboardEvent themeSelected(UiThemeMode mode) {
        return new ThemeSelected(mode);
    }
    public static DashboardEvent focusStepLimitSelected(FocusStepLimit limit) {
        return new FocusStepLimitSelected(limit);
    }
    public static DashboardEvent calendarPermission() { return new CalendarPermission(); }
    public static DashboardEvent checkUpdates() { return new CheckUpdates(); }

    private static String requiredId(String value) {
        if (value == null || value.trim().isEmpty())
            throw new IllegalArgumentException("Event identity is required");
        return value;
    }

    private static <T> T required(T value) {
        if (value == null) throw new IllegalArgumentException("Event value is required");
        return value;
    }
}
