package de.thonktank.autosecretary;

import androidx.annotation.NonNull;

public final class CalendarEventSnapshot {
    @NonNull public final String time;
    @NonNull public final String title;
    public final int minuteOfDay;
    CalendarEventSnapshot(@NonNull String time, @NonNull String title, int minuteOfDay) {
        this.time = time; this.title = title; this.minuteOfDay = minuteOfDay;
    }
}
