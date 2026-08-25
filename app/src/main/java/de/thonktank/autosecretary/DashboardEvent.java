package de.thonktank.autosecretary;

import de.thonktank.autosecretary.presentation.today.TaskActionTarget;

/** Root-shell events are limited to dashboard navigation and task menus. */
public abstract class DashboardEvent {
    private DashboardEvent() { }

    public static final class AddTask extends DashboardEvent { private AddTask() { } }

    public static final class TimelineMenu extends DashboardEvent {
        public final TaskActionTarget target;
        private TimelineMenu(TaskActionTarget target) { this.target = required(target); }
    }

    public static DashboardEvent addTask() { return new AddTask(); }
    public static DashboardEvent timelineMenu(TaskActionTarget target) {
        return new TimelineMenu(target);
    }
    private static <T> T required(T value) {
        if (value == null) throw new IllegalArgumentException("Event value is required");
        return value;
    }
}
