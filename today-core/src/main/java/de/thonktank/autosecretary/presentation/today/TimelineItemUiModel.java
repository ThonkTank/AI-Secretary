package de.thonktank.autosecretary.presentation.today;


public final class TimelineItemUiModel {
    public final TimelineTaskUiModel task;
    public final CalendarEventSnapshot event;
    public final int minute;
    public final long order;

    private TimelineItemUiModel(TimelineTaskUiModel task, CalendarEventSnapshot event,
                                int minute, long order) {
        this.task = task;
        this.event = event;
        this.minute = minute;
        this.order = order;
    }

    public static TimelineItemUiModel task(TimelineTaskUiModel task) {
        return new TimelineItemUiModel(task, null, task.slot.anchorMinute, task.displayOrder);
    }

    public static TimelineItemUiModel event(CalendarEventSnapshot event) {
        return new TimelineItemUiModel(null, event, event.minuteOfDay, event.minuteOfDay);
    }
}
