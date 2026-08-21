package de.thonktank.autosecretary;

import de.thonktank.autosecretary.presentation.today.TimelineTaskUiModel;
import de.thonktank.autosecretary.presentation.today.TaskActionTarget;
import de.thonktank.autosecretary.presentation.today.TodayAction;

import de.thonktank.autosecretary.data.preferences.FocusStepLimit;
import de.thonktank.autosecretary.data.preferences.UiThemeMode;

/** Typed user intent emitted by dashboard views. */
public abstract class DashboardEvent {
    private DashboardEvent() { }

    public enum FocusActionKind { COMPLETE, COMPLETE_REMAINING, HARVEST, DEFER }

    /** Temporary root-view transport; all Today semantics live in {@link TodayAction}. */
    public abstract static class Today extends DashboardEvent {
        public final TodayAction action;
        private Today(TodayAction action) { this.action = required(action); }
    }

    public static final class AddTask extends DashboardEvent {
        private AddTask() { }
    }

    public static final class TodayIntent extends Today {
        private TodayIntent(TodayAction action) { super(action); }
    }

    public static final class TimelinePrimary extends Today {
        public final TimelineTaskUiModel task;
        private TimelinePrimary(TimelineTaskUiModel task) {
            super(task.terminalCondition
                    ? TodayAction.requestClose(task.taskId, task.title)
                    : TodayAction.completeOccurrence(task.occurrenceId));
            this.task = required(task);
        }
    }

    public static final class TimelineMenu extends DashboardEvent {
        public final TaskActionTarget target;
        private TimelineMenu(TaskActionTarget target) { this.target = required(target); }
    }

    public static final class FocusAction extends Today {
        public final FocusActionKind kind;
        public final TaskActionTarget target;
        private FocusAction(FocusActionKind kind, TaskActionTarget target) {
            super(focusActionValue(kind, target));
            this.kind = required(kind);
            this.target = required(target);
        }
    }

    public static final class ToggleStep extends Today {
        public final String stepId;
        private ToggleStep(String stepId) {
            super(TodayAction.toggleStep(stepId));
            this.stepId = requiredId(stepId);
        }
    }

    public static final class AdvanceTodayStep extends Today {
        public final String stepId;
        private AdvanceTodayStep(String stepId) {
            super(TodayAction.advanceStep(stepId));
            this.stepId = requiredId(stepId);
        }
    }

    public static final class MoveTodayStep extends Today {
        public final String stepId;
        public final String beforeStepId;
        private MoveTodayStep(String stepId, String beforeStepId) {
            super(TodayAction.moveStep(stepId, beforeStepId));
            this.stepId = requiredId(stepId);
            this.beforeStepId = beforeStepId == null || beforeStepId.isEmpty()
                    ? null : beforeStepId;
        }
    }

    public static final class UndoCompleted extends Today {
        public final String occurrenceId;
        private UndoCompleted(String occurrenceId) {
            super(TodayAction.undoOccurrence(occurrenceId));
            this.occurrenceId = requiredId(occurrenceId);
        }
    }

    public static final class AdjustRepetition extends Today {
        public final String stepId;
        public final int delta;
        private AdjustRepetition(String stepId, int delta) {
            super(TodayAction.adjustRepetition(stepId, delta));
            if (delta == 0) throw new IllegalArgumentException("Adjustment must not be zero");
            this.stepId = requiredId(stepId);
            this.delta = delta;
        }
    }

    public static final class EditRepetition extends Today {
        public final String stepId;
        public final int index;
        private EditRepetition(String stepId, int index) {
            super(TodayAction.editRepetition(stepId, index));
            if (index < 0) throw new IllegalArgumentException("Saved result index is required");
            this.stepId = requiredId(stepId);
            this.index = index;
        }
    }

    public static final class SubmitRepetition extends Today {
        public final String stepId;
        private SubmitRepetition(String stepId) {
            super(TodayAction.submitRepetition(stepId));
            this.stepId = requiredId(stepId);
        }
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
    public static DashboardEvent today(TodayAction action) { return new TodayIntent(action); }
    public static DashboardEvent timelinePrimary(TimelineTaskUiModel task) {
        return new TimelinePrimary(task);
    }
    public static DashboardEvent timelineMenu(TaskActionTarget target) {
        return new TimelineMenu(target);
    }
    public static DashboardEvent focusAction(FocusActionKind kind, TaskActionTarget target) {
        return new FocusAction(kind, target);
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

    private static TodayAction focusActionValue(FocusActionKind kind,
                                                TaskActionTarget target) {
        required(kind);
        required(target);
        switch (kind) {
            case COMPLETE:
                return target.terminalCondition
                        ? TodayAction.requestClose(target.taskId, target.title)
                        : TodayAction.completeOccurrence(target.occurrenceId);
            case COMPLETE_REMAINING:
                return TodayAction.completeRemaining(target.occurrenceId);
            case HARVEST:
                return TodayAction.harvest(target.occurrenceId);
            case DEFER:
                return TodayAction.defer(target.occurrenceId.isEmpty()
                        ? target.taskId : target.occurrenceId);
        }
        throw new AssertionError("Unhandled focus action " + kind);
    }
}
