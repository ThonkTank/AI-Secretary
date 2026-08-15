package de.thonktank.autosecretary;

public final class TimelineItemUiModel {
    public final TaskSnapshot task;
    public final CalendarEventSnapshot event;
    final int minute;
    final long order;

    private TimelineItemUiModel(TaskSnapshot task, CalendarEventSnapshot event,
                                int minute, long order) {
        this.task = task;
        this.event = event;
        this.minute = minute;
        this.order = order;
    }

    public static TimelineItemUiModel task(TaskSnapshot task) {
        return new TimelineItemUiModel(task, null, task.slot.anchorMinute, task.displayOrder);
    }

    public static TimelineItemUiModel event(CalendarEventSnapshot event) {
        return new TimelineItemUiModel(null, event, event.minuteOfDay, event.minuteOfDay);
    }
}
