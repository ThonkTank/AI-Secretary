package de.thonktank.autosecretary.presentation.today;


public final class CalendarEventSnapshot {
    public final String time;
    public final String title;
    public final int minuteOfDay;
    public CalendarEventSnapshot(String time, String title, int minuteOfDay) {
        this.time = time; this.title = title; this.minuteOfDay = minuteOfDay;
    }
}
