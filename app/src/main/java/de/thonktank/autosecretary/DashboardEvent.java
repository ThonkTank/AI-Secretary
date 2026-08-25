package de.thonktank.autosecretary;

import de.thonktank.autosecretary.data.preferences.FocusStepLimit;
import de.thonktank.autosecretary.data.preferences.UiThemeMode;
import de.thonktank.autosecretary.presentation.today.TaskActionTarget;

/** Root-shell events are limited to navigation, dialogs, preferences and system actions. */
public abstract class DashboardEvent {
    private DashboardEvent() { }

    public static final class AddTask extends DashboardEvent { private AddTask() { } }

    public static final class TimelineMenu extends DashboardEvent {
        public final TaskActionTarget target;
        private TimelineMenu(TaskActionTarget target) { this.target = required(target); }
    }

    public static final class ThemeSelected extends DashboardEvent {
        public final UiThemeMode mode;
        private ThemeSelected(UiThemeMode mode) { this.mode = required(mode); }
    }

    public static final class FocusStepLimitSelected extends DashboardEvent {
        public final FocusStepLimit limit;
        private FocusStepLimitSelected(FocusStepLimit limit) { this.limit = required(limit); }
    }

    public static final class RestTimerDefaultChanged extends DashboardEvent {
        public final int seconds;
        private RestTimerDefaultChanged(int seconds) {
            if (seconds < 1) throw new IllegalArgumentException("Rest timer must be positive");
            this.seconds = seconds;
        }
    }

    public static final class CalendarPermission extends DashboardEvent {
        private CalendarPermission() { }
    }

    public static final class CheckUpdates extends DashboardEvent { private CheckUpdates() { } }

    public static DashboardEvent addTask() { return new AddTask(); }
    public static DashboardEvent timelineMenu(TaskActionTarget target) {
        return new TimelineMenu(target);
    }
    public static DashboardEvent themeSelected(UiThemeMode mode) {
        return new ThemeSelected(mode);
    }
    public static DashboardEvent focusStepLimitSelected(FocusStepLimit limit) {
        return new FocusStepLimitSelected(limit);
    }
    public static DashboardEvent restTimerDefaultChanged(int seconds) {
        return new RestTimerDefaultChanged(seconds);
    }
    public static DashboardEvent calendarPermission() { return new CalendarPermission(); }
    public static DashboardEvent checkUpdates() { return new CheckUpdates(); }

    private static <T> T required(T value) {
        if (value == null) throw new IllegalArgumentException("Event value is required");
        return value;
    }
}
